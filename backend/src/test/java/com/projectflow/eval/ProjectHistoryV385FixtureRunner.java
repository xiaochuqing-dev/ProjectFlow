package com.projectflow.eval;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.dto.ProjectHistoryDtos.HistoryEventResponse;
import com.projectflow.entity.EvidenceConfidence;
import com.projectflow.entity.ProjectFact;
import com.projectflow.entity.ProjectFactEpistemicStatus;
import com.projectflow.entity.ProjectFactOrigin;
import com.projectflow.entity.ProjectFactRecordStatus;
import com.projectflow.entity.ProjectHistoryEvent.RewriteState;
import com.projectflow.entity.ProjectHistorySnapshot;
import com.projectflow.entity.ProjectMemory;
import com.projectflow.entity.ProjectSpace;
import com.projectflow.entity.ProjectStatus;
import com.projectflow.repository.ProjectFactRepository;
import com.projectflow.repository.ProjectHistoryEventRepository;
import com.projectflow.repository.ProjectHistorySnapshotRepository;
import com.projectflow.repository.ProjectMemoryRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.service.ModelGatewayService;
import com.projectflow.service.ProjectHistoryCorrectionService;
import com.projectflow.service.ProjectHistoryReadService;
import com.projectflow.service.ProjectHistoryReconstructionService;

/** Runs frozen evaluation shapes through the production reconstruction path. */
public final class ProjectHistoryV385FixtureRunner {
    private final ProjectRepository projectRepository;
    private final ProjectMemoryRepository memoryRepository;
    private final ProjectFactRepository factRepository;
    private final ProjectHistoryEventRepository eventRepository;
    private final ProjectHistorySnapshotRepository snapshotRepository;
    private final ProjectHistoryReconstructionService reconstructionService;
    private final ProjectHistoryCorrectionService correctionService;
    private final ProjectHistoryReadService readService;
    private final ObjectMapper objectMapper;
    private final AtomicInteger factSequence = new AtomicInteger();

    public ProjectHistoryV385FixtureRunner(
        ProjectRepository projectRepository,
        ProjectMemoryRepository memoryRepository,
        ProjectFactRepository factRepository,
        ProjectHistoryEventRepository eventRepository,
        ProjectHistorySnapshotRepository snapshotRepository,
        ProjectHistoryReconstructionService reconstructionService,
        ProjectHistoryCorrectionService correctionService,
        ProjectHistoryReadService readService,
        ObjectMapper objectMapper
    ) {
        this.projectRepository = projectRepository;
        this.memoryRepository = memoryRepository;
        this.factRepository = factRepository;
        this.eventRepository = eventRepository;
        this.snapshotRepository = snapshotRepository;
        this.reconstructionService = reconstructionService;
        this.correctionService = correctionService;
        this.readService = readService;
        this.objectMapper = objectMapper;
    }

    public ProjectHistoryV385QualityEvaluator.CaseObservation run(
        UUID userId,
        JsonNode testCase,
        Path caseRoot
    ) throws Exception {
        return execute(userId, testCase, caseRoot).observation();
    }

    public FixtureExecution execute(
        UUID userId,
        JsonNode testCase,
        Path caseRoot
    ) throws Exception {
        Files.createDirectories(caseRoot);
        ProjectSpace project = project(userId, caseRoot);
        AliasRegistry aliases = new AliasRegistry();
        List<ProjectHistoryReconstructionService.HistoryRefreshOutcome> outcomes = new ArrayList<>();
        String fixtureHash = testCase.path("fixtureHash").asText();
        switch (fixtureHash) {
            case "fixture:small-five-v1" -> smallFive(project, caseRoot, aliases);
            case "fixture:lifecycle-v1" -> lifecycle(project, caseRoot, aliases);
            case "fixture:multi-commit-v1" -> multiCommit(project, caseRoot, aliases);
            case "fixture:primary-supporting-v1" -> primarySupporting(project, caseRoot, aliases);
            case "fixture:non-code-v1" -> nonCode(project, caseRoot, aliases);
            case "fixture:reason-unknown-v1" -> reasonUnknown(project, caseRoot, aliases);
            case "fixture:conflict-v1" -> conflict(project, caseRoot, aliases);
            case "fixture:leakage-v1" -> technicalLeakage(project, caseRoot, aliases);
            case "fixture:chaotic-300-v1" -> chaotic(project, caseRoot, aliases);
            case "fixture:identity-boundary-v1" -> identityBoundary(project, caseRoot, aliases);
            case "fixture:unrelated-v1" -> unrelated(project, caseRoot, aliases);
            case "fixture:revert-reapply-v1" -> revertReapply(project, caseRoot, aliases);
            case "fixture:document-only-v1" -> documentOnly(project, caseRoot, aliases);
            case "fixture:shallow-v1" -> shallow(project, caseRoot, aliases);
            case "fixture:path-rebinding-v1" -> pathRebinding(userId, project, caseRoot, aliases, outcomes);
            case "fixture:thousand-events-v1" -> thousandEvents(project, caseRoot, aliases);
            case "fixture:sensitive-v1" -> sensitive(project, caseRoot, aliases);
            case "fixture:generic-message-v1" -> genericMessage(project, caseRoot, aliases);
            case "fixture:agent-evidence-v1" -> agentEvidence(project, caseRoot, aliases);
            default -> throw new IllegalArgumentException("Unknown history fixture: " + fixtureHash);
        }
        outcomes.add(reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false));

        ProjectHistorySnapshot snapshot = snapshotRepository.findByProjectId(project.getId()).orElseThrow();
        ProjectHistoryCorrectionService.CorrectedHistory corrected = correctionService.resolve(project.getId(), snapshot);
        List<HistoryEventResponse> events = currentEvents(userId, project.getId());
        Map<String, Set<UUID>> resolvedAliases = aliases.resolve(events);
        var overview = readService.overview(userId, project.getId());
        int requestCount = outcomes.stream().flatMap(value -> value.diagnostics().stream())
            .mapToInt(ModelGatewayService.ModelCallDiagnostics::requestCount).sum();
        long tokens = outcomes.stream().flatMap(value -> value.diagnostics().stream())
            .mapToLong(ModelGatewayService.ModelCallDiagnostics::totalTokens).sum();
        long latencyMs = outcomes.stream().flatMap(value -> value.diagnostics().stream())
            .mapToLong(ModelGatewayService.ModelCallDiagnostics::latencyMs).sum();
        ProjectHistoryV385QualityEvaluator.CaseObservation observation =
            new ProjectHistoryV385QualityEvaluator.CaseObservation(
            testCase.path("id").asText(), testCase.path("split").asText(),
            corrected.stories(), corrected.chapters(), corrected.threads(), events, resolvedAliases,
            overview.overview().unknowns(), overview.overview().conflicts(), corrected.presentationRevision(),
            snapshot.getSourceEventCount(),
            Math.toIntExact(eventRepository.countByProjectIdAndRewriteState(project.getId(), RewriteState.CURRENT)),
            requestCount, tokens
        );
        return new FixtureExecution(
            observation,
            outcomes.stream().anyMatch(ProjectHistoryReconstructionService.HistoryRefreshOutcome::modelUsed),
            outcomes.stream().anyMatch(ProjectHistoryReconstructionService.HistoryRefreshOutcome::degraded),
            outcomes.get(outcomes.size() - 1).cacheHit(),
            latencyMs,
            Map.copyOf(overview.diagnostics())
        );
    }

    private ProjectSpace project(UUID userId, Path root) {
        ProjectSpace project = new ProjectSpace(userId);
        project.update(
            "History quality fixture", "Synthetic public-safe quality fixture", ProjectStatus.BUILDING,
            List.of(), "", LocalDate.of(2026, 8, 6), null
        );
        project = projectRepository.saveAndFlush(project);
        bind(project, root);
        return project;
    }

    private void bind(ProjectSpace project, Path root) {
        ProjectMemory memory = memoryRepository.findByProjectId(project.getId()).orElseGet(() -> {
            ProjectMemory value = new ProjectMemory(project.getId());
            value.update("", "", "", "", "", "", "", "", "");
            return value;
        });
        memory.rememberLocalProjectPath(root.toAbsolutePath().normalize().toString());
        memoryRepository.saveAndFlush(memory);
    }

    private void smallFive(ProjectSpace project, Path root, AliasRegistry aliases) throws Exception {
        initGit(root);
        write(root, "src/LoginFlow.java", "final class LoginFlow { boolean emailFallback; }\n");
        String first = commit(root, "establish sign-in entry", "2024-01-01T00:00:00Z");
        aliases.gitPath("auth-result", first, "src/LoginFlow.java");
        write(root, "src/LoginFlow.java", "final class LoginFlow { boolean emailFallback = true; String failureMessage; }\n");
        String second = commit(root, "add email fallback and consistent failure message", "2024-01-02T00:00:00Z");
        aliases.gitPath("auth-result", second, "src/LoginFlow.java");
        write(root, "tests/LoginVerificationTest.java", "final class LoginVerificationTest {}\n");
        String third = commit(root, "test login fallback", "2024-01-03T00:00:00Z");
        aliases.gitPath("auth-test", third, "tests/LoginVerificationTest.java");
        write(root, "tests/LoginVerification.md", "Login fallback verification notes.\n");
        String fourth = commit(root, "document login verification", "2024-01-04T00:00:00Z");
        aliases.gitPath("auth-doc", fourth, "tests/LoginVerification.md");
        write(root, "tests/LoginVerification.md", "Login fallback and consistent failure verification notes.\n");
        String fifth = commit(root, "refine login verification notes", "2024-01-05T00:00:00Z");
        aliases.gitPath("auth-doc", fifth, "tests/LoginVerification.md");
    }

    private void lifecycle(ProjectSpace project, Path root, AliasRegistry aliases) throws Exception {
        initGit(root);
        write(root, "src/LoginFlow.java", "final class LoginFlow {}\n");
        commit(root, "add login entry", "2024-01-01T00:00:00Z");
        write(root, "src/LoginFlow.java", "final class LoginFlow { boolean fallback; }\n");
        commit(root, "improve login entry", "2024-01-02T00:00:00Z");
        Files.delete(root.resolve("src/LoginFlow.java"));
        commit(root, "remove login entry", "2024-01-03T00:00:00Z");
        write(root, "src/LoginFlow.java", "final class LoginFlow { boolean restored; }\n");
        commit(root, "restore login entry", "2024-01-04T00:00:00Z");
        write(root, "tests/LoginValidationTest.java", "final class LoginValidationTest {}\n");
        commit(root, "test restored login entry", "2024-01-05T00:00:00Z");
        aliases.gitPath("login-life", null, "src/LoginFlow.java");
        aliases.gitPath("login-test", null, "tests/LoginValidationTest.java");
    }

    private void multiCommit(ProjectSpace project, Path root, AliasRegistry aliases) throws Exception {
        initGit(root);
        write(root, "src/ProjectExport.java", "final class ProjectExport { String html; }\n");
        String first = commit(root, "start project result export", "2025-01-01T00:00:00Z");
        aliases.gitPath("export-1", first, "src/ProjectExport.java");
        write(root, "src/ProjectExport.java", "final class ProjectExport { String html; String markdown; }\n");
        String second = commit(root, "add markdown result export", "2025-01-03T00:00:00Z");
        aliases.gitPath("export-2", second, "src/ProjectExport.java");
        write(root, "src/ProjectExport.java", "final class ProjectExport { String markdown; String pdf; }\n");
        String third = commit(root, "finish pdf result export", "2025-01-05T00:00:00Z");
        aliases.gitPath("export-3", third, "src/ProjectExport.java");
        write(root, "tests/ProjectExportValidationTest.java", "final class ProjectExportValidationTest {}\n");
        String fourth = commit(root, "test project result export", "2025-01-06T00:00:00Z");
        aliases.gitPath("export-test", fourth, "tests/ProjectExportValidationTest.java");
    }

    private void primarySupporting(ProjectSpace project, Path root, AliasRegistry aliases) throws Exception {
        initGit(root);
        write(root, "src/LoginExperience.java", "final class LoginExperience { String oldMessage; }\n");
        commit(root, "establish login baseline", "2025-02-01T00:00:00Z");
        write(root, "src/LoginExperience.java", "final class LoginExperience { String consistentMessage; boolean easyEntry; }\n");
        String primary = commit(root, "make login easier to use and verify", "2025-02-02T00:00:00Z");
        aliases.gitPath("login-result", primary, "src/LoginExperience.java");
        write(root, "tests/LoginValidationTest.java", "final class LoginValidationTest {}\n");
        String test = commit(root, "test login result", "2025-02-03T00:00:00Z");
        aliases.gitPath("login-test", test, "tests/LoginValidationTest.java");
        write(root, "tests/LoginValidation.md", "Login result verification guide.\n");
        String doc = commit(root, "document login validation", "2025-02-04T00:00:00Z");
        aliases.gitPath("login-doc", doc, "tests/LoginValidation.md");
        write(root, "config/LoginValidation.json", "{\"consistentFailure\":true}\n");
        String config = commit(root, "configure login validation", "2025-02-05T00:00:00Z");
        aliases.gitPath("login-config", config, "config/LoginValidation.json");
    }

    private void nonCode(ProjectSpace project, Path root, AliasRegistry aliases) throws Exception {
        writeAt(root, "reports/ResearchReport.md", "# Research report\n\nConclusion and findings.\n", "2025-03-02T00:00:00Z");
        aliases.currentFile("report-result", "reports/ResearchReport.md");
        ProjectFact created = fact(
            project, "新增研究报告成果", "研究报告首次形成可阅读版本。", "reports/ResearchReport.md",
            "2025-03-01T00:00:00Z", ProjectFactEpistemicStatus.OBSERVED, ProjectFactRecordStatus.RECORDED,
            List.of("没有 Git 历史，只有当前材料和已记录事实。 ")
        );
        ProjectFact updated = fact(
            project, "更新研究报告结构", "报告结构和结论已经整理。", "reports/ResearchReport.md",
            "2025-03-02T00:00:00Z", ProjectFactEpistemicStatus.OBSERVED, ProjectFactRecordStatus.RECORDED,
            List.of("未记录外部评审意见。 ")
        );
        aliases.fact("report-result", created.getId());
        aliases.fact("report-result", updated.getId());
        ProjectFact outline = fact(
            project, "配置研究报告阅读顺序", "报告章节按照背景、分析和结论排列。",
            "config/ResearchOutline.json", "2025-03-02T00:00:00Z", ProjectFactEpistemicStatus.OBSERVED,
            ProjectFactRecordStatus.RECORDED, List.of("无 Git 历史，只有当前材料和已记录事实。 ")
        );
        aliases.fact("report-outline", outline.getId());
    }

    private void reasonUnknown(ProjectSpace project, Path root, AliasRegistry aliases) throws Exception {
        initGit(root);
        write(root, "results/ProjectOutcome.md", "Initial observed outcome.\n");
        commit(root, "initial result", "2025-04-01T00:00:00Z");
        write(root, "results/ProjectOutcome.md", "Changed observed outcome without a recorded rationale.\n");
        String revision = commit(root, "update", "2025-04-02T00:00:00Z");
        aliases.gitPath("unknown-result", revision, "results/ProjectOutcome.md");
    }

    private void conflict(ProjectSpace project, Path root, AliasRegistry aliases) throws Exception {
        Path agentResult = writeAgentResult(
            root, "migration-work", "整理数据迁移结果", List.of("数据迁移工作声明已经完成。"),
            List.of("migration/DataMigration.sql")
        );
        Files.setLastModifiedTime(agentResult, java.nio.file.attribute.FileTime.from(Instant.parse("2025-05-01T00:00:00Z")));
        aliases.agentResult("migration-result", ".projectflow/agent-results/migration-work/result.json");
        ProjectFact verification = fact(
            project, "验证数据迁移仍然失败", "独立验证与完成声明冲突，当前不能确认迁移成功。",
            "tests/MigrationValidation.md", "2025-05-02T00:00:00Z", ProjectFactEpistemicStatus.CONFLICTED,
            ProjectFactRecordStatus.NEEDS_ATTENTION, List.of("没有后续成功验证。 ")
        );
        aliases.fact("migration-test", verification.getId());
    }

    private void technicalLeakage(ProjectSpace project, Path root, AliasRegistry aliases) throws Exception {
        initGit(root);
        write(root, "backend/src/main/java/example/ProjectHistoryReader.java", "final class ProjectHistoryReader {}\n");
        commit(root, "establish readable project history baseline", "2025-05-31T00:00:00Z");
        write(root, "backend/src/main/java/example/ProjectHistoryReader.java", "final class ProjectHistoryReader { boolean readable; }\n");
        String primary = commit(root, "group project history into readable work results", "2025-06-01T00:00:00Z");
        aliases.gitPath("history-result", primary, "backend/src/main/java/example/ProjectHistoryReader.java");
        write(root, "backend/src/test/java/example/HistoryReadabilityValidationTest.java", "final class HistoryReadabilityValidationTest {}\n");
        String test = commit(root, "test readable project history", "2025-06-02T00:00:00Z");
        aliases.gitPath("history-test", test, "backend/src/test/java/example/HistoryReadabilityValidationTest.java");
    }

    private void chaotic(ProjectSpace project, Path root, AliasRegistry aliases) {
        List<ProjectFact> facts = new ArrayList<>();
        Instant first = Instant.parse("2022-01-01T00:00:00Z");
        for (int index = 0; index < 300; index++) {
            String title = index == 0 ? "新增核心使用体验" : switch (index % 4) {
                case 0 -> "更新核心使用体验";
                case 1 -> "fix";
                case 2 -> "继续整理核心项目结果";
                default -> "update";
            };
            facts.add(newFact(
                project, title, "来源记录了核心项目结果的连续变化。", "src/CoreExperience.java",
                first.plusSeconds(index * 86_400L), ProjectFactEpistemicStatus.OBSERVED,
                ProjectFactRecordStatus.RECORDED, List.of(index < 10 ? "早期历史覆盖有限。 " : "")
            ));
        }
        factRepository.saveAllAndFlush(facts);
        facts.forEach(value -> aliases.fact("core-result", value.getId()));
        ProjectFact test = fact(
            project, "测试核心体验结果", "核对核心体验的当前行为。", "tests/CoreValidationTest.java",
            "2022-11-02T00:00:00Z", ProjectFactEpistemicStatus.OBSERVED, ProjectFactRecordStatus.RECORDED,
            List.of("部分尾部仍需核对。 ")
        );
        ProjectFact doc = fact(
            project, "记录核心体验验证说明", "保留验证范围和未处理尾部。", "tests/CoreValidation.md",
            "2022-11-03T00:00:00Z", ProjectFactEpistemicStatus.OBSERVED, ProjectFactRecordStatus.RECORDED,
            List.of("未处理范围已披露。 ")
        );
        aliases.fact("core-test", test.getId());
        aliases.fact("core-doc", doc.getId());
    }

    private void identityBoundary(ProjectSpace project, Path root, AliasRegistry aliases) throws Exception {
        initGit(root);
        write(root, "reports/ResearchReport.md", "Complete research report.\n");
        commit(root, "create research report", "2025-07-01T00:00:00Z");
        Files.delete(root.resolve("reports/ResearchReport.md"));
        write(root, "reports/ResearchReportPartA.md", "Research report part A.\n");
        write(root, "reports/ResearchReportPartB.md", "Research report part B.\n");
        commit(root, "split research report into sections", "2025-07-02T00:00:00Z");
        Files.delete(root.resolve("reports/ResearchReportPartA.md"));
        Files.delete(root.resolve("reports/ResearchReportPartB.md"));
        write(root, "reports/ResearchReport.md", "Merged research report.\n");
        commit(root, "merge research report sections", "2025-07-03T00:00:00Z");
        Files.move(root.resolve("reports/ResearchReport.md"), root.resolve("reports/FinalResearchReport.md"));
        commit(root, "rename research report", "2025-07-04T00:00:00Z");
        write(root, "reports/FinalResearchReport.md", "Reapplied and refined research report.\n");
        commit(root, "reapply research report result", "2025-07-05T00:00:00Z");
        write(root, "tests/ResearchReportValidation.md", "Research report continuity verification.\n");
        commit(root, "test research report continuity", "2025-07-06T00:00:00Z");
        aliases.path("report-life", path -> path.contains("ResearchReport") && !path.startsWith("tests/"));
        aliases.gitPath("report-test", null, "tests/ResearchReportValidation.md");
    }

    private void unrelated(ProjectSpace project, Path root, AliasRegistry aliases) throws Exception {
        initGit(root);
        write(root, "src/LoginExperience.java", "final class LoginExperience { int version; }\n");
        write(root, "src/ProjectExport.java", "final class ProjectExport { int version; }\n");
        write(root, "docs/ProjectGuide.md", "Initial guide.\n");
        commit(root, "establish three project results", "2025-08-01T00:00:00Z");
        write(root, "src/LoginExperience.java", "final class LoginExperience { int version = 2; }\n");
        write(root, "src/ProjectExport.java", "final class ProjectExport { int version = 2; }\n");
        write(root, "docs/ProjectGuide.md", "Updated guide.\n");
        commit(root, "update", "2025-08-02T00:00:00Z");
        aliases.gitPath("auth-result", null, "src/LoginExperience.java");
        aliases.gitPath("export-result", null, "src/ProjectExport.java");
        aliases.gitPath("docs-result", null, "docs/ProjectGuide.md");
        write(root, "tests/LoginExperienceValidationTest.java", "final class LoginExperienceValidationTest {}\n");
        commit(root, "test login result", "2025-08-03T00:00:00Z");
        aliases.gitPath("auth-test", null, "tests/LoginExperienceValidationTest.java");
        write(root, "tests/ProjectExportValidationTest.java", "final class ProjectExportValidationTest {}\n");
        commit(root, "test export result", "2025-08-04T00:00:00Z");
        aliases.gitPath("export-test", null, "tests/ProjectExportValidationTest.java");
        write(root, "tests/ProjectGuideValidation.md", "Project guide verification.\n");
        commit(root, "check project guide", "2025-08-05T00:00:00Z");
        aliases.gitPath("docs-check", null, "tests/ProjectGuideValidation.md");
    }

    private void revertReapply(ProjectSpace project, Path root, AliasRegistry aliases) throws Exception {
        initGit(root);
        write(root, "reports/ResearchReport.md", "Published report result.\n");
        commit(root, "publish research report", "2025-09-01T00:00:00Z");
        write(root, "reports/ResearchReport.md", "Report reverted to earlier state.\n");
        commit(root, "revert research report result", "2025-09-02T00:00:00Z");
        write(root, "reports/ResearchReport.md", "Research report result reimplemented.\n");
        commit(root, "reapply research report result", "2025-09-03T00:00:00Z");
        aliases.gitPath("report-reapply", null, "reports/ResearchReport.md");
    }

    private void documentOnly(ProjectSpace project, Path root, AliasRegistry aliases) throws Exception {
        writeAt(root, "documents/ProjectGuide.md", "# Project guide\n\nReadable sections and explanations.\n", "2025-10-02T00:00:00Z");
        aliases.currentFile("document-result", "documents/ProjectGuide.md");
        ProjectFact created = fact(
            project, "新增项目文档", "项目文档首次形成可阅读版本。", "documents/ProjectGuide.md",
            "2025-10-01T00:00:00Z", ProjectFactEpistemicStatus.OBSERVED, ProjectFactRecordStatus.RECORDED,
            List.of("没有 Git 历史。 ")
        );
        ProjectFact updated = fact(
            project, "更新项目文档结构", "章节已经重新组织并补足说明。", "documents/ProjectGuide.md",
            "2025-10-02T00:00:00Z", ProjectFactEpistemicStatus.OBSERVED, ProjectFactRecordStatus.RECORDED,
            List.of("变化原因未知。 ")
        );
        aliases.fact("document-result", created.getId());
        aliases.fact("document-result", updated.getId());
        ProjectFact outline = fact(
            project, "配置项目文档章节顺序", "章节已经按照主题重新组织。", "config/ProjectGuideOutline.json",
            "2025-10-02T00:00:00Z", ProjectFactEpistemicStatus.OBSERVED, ProjectFactRecordStatus.RECORDED,
            List.of("没有 Git 历史；变化原因未知。 ")
        );
        aliases.fact("document-outline", outline.getId());
    }

    private void shallow(ProjectSpace project, Path root, AliasRegistry aliases) throws Exception {
        Path source = root.resolve("source");
        Path clone = root.resolve("bound");
        Files.createDirectories(source);
        initGit(source);
        write(source, "results/CurrentOutcome.md", "First historical outcome.\n");
        commit(source, "create earlier project result", "2024-01-01T00:00:00Z");
        write(source, "results/CurrentOutcome.md", "Current observable outcome.\n");
        commit(source, "create current project result", "2025-01-01T00:00:00Z");
        run(root, "git", "clone", "--depth", "1", source.toUri().toString(), clone.toString());
        bind(project, clone);
        aliases.gitPath("current-result", null, "results/CurrentOutcome.md");
    }

    private void pathRebinding(
        UUID userId,
        ProjectSpace project,
        Path root,
        AliasRegistry aliases,
        List<ProjectHistoryReconstructionService.HistoryRefreshOutcome> outcomes
    ) throws Exception {
        Path oldRoot = root.resolve("old-source");
        Path newRoot = root.resolve("new-source");
        Files.createDirectories(oldRoot);
        Files.createDirectories(newRoot);
        initGit(oldRoot);
        write(oldRoot, "results/OldOutcome.md", "Old project result.\n");
        commit(oldRoot, "create old project result", "2024-01-01T00:00:00Z");
        bind(project, oldRoot);
        outcomes.add(reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false));
        initGit(newRoot);
        write(newRoot, "results/NewProjectOutcome.md", "New bound project result.\n");
        commit(newRoot, "replace project source with current result", "2025-01-01T00:00:00Z");
        bind(project, newRoot);
        aliases.gitPath("new-project-result", null, "results/NewProjectOutcome.md");
        ProjectFact replacement = fact(
            project, "替换项目来源并采用当前结果", "新绑定来源独立呈现，旧来源保留为失效记录。",
            "results/NewProjectOutcome.md", "2025-01-01T01:00:00Z", ProjectFactEpistemicStatus.OBSERVED,
            ProjectFactRecordStatus.RECORDED, List.of("旧来源后续变化不可用。 ")
        );
        aliases.fact("new-project-result", replacement.getId());
    }

    private void thousandEvents(ProjectSpace project, Path root, AliasRegistry aliases) {
        List<ProjectFact> facts = new ArrayList<>();
        Instant first = Instant.parse("2023-01-01T00:00:00Z");
        for (int index = 0; index < 1_200; index++) {
            facts.add(newFact(
                project, index == 0 ? "新增项目成果" : "更新项目成果", "来源记录了项目成果的连续变化。",
                "results/ProjectOutcome.md", first.plusSeconds(index * 3_600L), ProjectFactEpistemicStatus.OBSERVED,
                ProjectFactRecordStatus.RECORDED, List.of("部分来源只提供技术细节。 ")
            ));
        }
        factRepository.saveAllAndFlush(facts);
        facts.forEach(value -> aliases.fact("area-result", value.getId()));
        ProjectFact test = fact(
            project, "测试项目成果", "核对大批历史压缩后的结果。", "tests/OutcomeValidationTest.java",
            "2023-02-21T01:00:00Z", ProjectFactEpistemicStatus.OBSERVED, ProjectFactRecordStatus.RECORDED, List.of()
        );
        ProjectFact doc = fact(
            project, "记录项目成果验证说明", "保留完整来源下钻说明。", "tests/OutcomeValidation.md",
            "2023-02-21T02:00:00Z", ProjectFactEpistemicStatus.OBSERVED, ProjectFactRecordStatus.RECORDED, List.of()
        );
        aliases.fact("area-test", test.getId());
        aliases.fact("area-doc", doc.getId());
    }

    private void sensitive(ProjectSpace project, Path root, AliasRegistry aliases) throws Exception {
        write(root, ".env", "SECRET_VALUE=fixture-only-sensitive-content\n");
        writeAt(root, "results/SafeSummary.md", "Safe, reviewable project result without sensitive content.\n", "2025-11-10T00:00:00Z");
        aliases.currentFile("safe-result", "results/SafeSummary.md");
        ProjectFact safe = fact(
            project, "更新安全材料处理结果", "只保留安全元数据和可核对结果。", "results/SafeSummary.md",
            "2025-11-10T00:00:00Z", ProjectFactEpistemicStatus.OBSERVED, ProjectFactRecordStatus.RECORDED,
            List.of("敏感材料详情不可读取。 ")
        );
        aliases.fact("safe-result", safe.getId());
    }

    private void genericMessage(ProjectSpace project, Path root, AliasRegistry aliases) throws Exception {
        initGit(root);
        write(root, "src/ProjectOutcome.java", "final class ProjectOutcome { int version; }\n");
        commit(root, "initial result", "2025-11-01T00:00:00Z");
        write(root, "src/ProjectOutcome.java", "final class ProjectOutcome { int version = 2; }\n");
        commit(root, "fix", "2025-11-02T00:00:00Z");
        aliases.gitPath("actual-result", null, "src/ProjectOutcome.java");
        write(root, "tests/ProjectOutcomeValidationTest.java", "final class ProjectOutcomeValidationTest {}\n");
        commit(root, "update", "2025-11-03T00:00:00Z");
        aliases.gitPath("actual-test", null, "tests/ProjectOutcomeValidationTest.java");
    }

    private void agentEvidence(ProjectSpace project, Path root, AliasRegistry aliases) throws Exception {
        ProjectFact observed = fact(
            project, "新增 Agent 工作交接记录", "来源确认交接记录存在，但未证明所有工作完成。",
            "results/AgentHandoff.md", "2025-12-01T00:00:00Z", ProjectFactEpistemicStatus.OBSERVED,
            ProjectFactRecordStatus.RECORDED, List.of("独立验证尚未完成。 ")
        );
        aliases.fact("agent-result", observed.getId());
        writeAgentResult(
            root, "handoff-work", "整理 Agent 工作交接", List.of("Agent 整理了工作结果，未验证部分保持未知。"),
            List.of("results/AgentHandoff.md")
        );
        aliases.agentResult("agent-result", ".projectflow/agent-results/handoff-work/result.json");
        ProjectFact verification = fact(
            project, "测试 Agent 交接结果", "验证边界已经记录，尚未形成独立完成证明。",
            "tests/AgentHandoffValidationTest.java", "2025-12-02T00:00:00Z",
            ProjectFactEpistemicStatus.PROCESS_EVIDENCE, ProjectFactRecordStatus.NEEDS_ATTENTION,
            List.of("独立验证尚未完成。 ")
        );
        aliases.fact("agent-verification", verification.getId());
    }

    private ProjectFact fact(
        ProjectSpace project,
        String title,
        String summary,
        String path,
        String occurredAt,
        ProjectFactEpistemicStatus status,
        ProjectFactRecordStatus recordStatus,
        List<String> limitations
    ) {
        return factRepository.saveAndFlush(newFact(
            project, title, summary, path, Instant.parse(occurredAt), status, recordStatus, limitations
        ));
    }

    private ProjectFact newFact(
        ProjectSpace project,
        String title,
        String summary,
        String path,
        Instant occurredAt,
        ProjectFactEpistemicStatus status,
        ProjectFactRecordStatus recordStatus,
        List<String> limitations
    ) {
        int sequence = factSequence.incrementAndGet();
        ProjectFact fact = new ProjectFact(
            project.getId(), null, null, ProjectFactOrigin.INCREMENTAL_SCAN, String.format("%064d", sequence)
        );
        fact.updateContent(
            title, summary, List.of(summary), "结果可从来源继续核对。", occurredAt, occurredAt,
            List.of(), List.of(), List.of(), List.of(path), List.of("source:fixture-" + sequence),
            "LOCAL_RULE", recordStatus == ProjectFactRecordStatus.RECORDED ? "PASS" : "NEEDS_REVIEW",
            recordStatus == ProjectFactRecordStatus.RECORDED ? EvidenceConfidence.HIGH : EvidenceConfidence.MEDIUM,
            recordStatus, recordStatus == ProjectFactRecordStatus.RECORDED ? "" : "需要保留验证边界"
        );
        fact.applyKnowledgeContract(
            status, List.of("SYNTHETIC_FIXTURE"), "CURRENT", "fixture-revision-" + sequence,
            occurredAt, occurredAt, clean(limitations),
            status == ProjectFactEpistemicStatus.CONFLICTED ? List.of("source:independent-verification") : List.of(),
            "ENGINEERING_VALIDATION", "", "", recordStatus == ProjectFactRecordStatus.RECORDED ? "VALIDATED" : "PENDING_VALIDATION"
        );
        return fact;
    }

    private Path writeAgentResult(
        Path root,
        String directory,
        String taskGoal,
        List<String> actualChanges,
        List<String> keyFiles
    ) throws IOException {
        Path result = root.resolve(".projectflow/agent-results").resolve(directory).resolve("result.json");
        Files.createDirectories(result.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(result.toFile(), Map.of(
            "taskGoal", taskGoal,
            "actualChanges", actualChanges,
            "keyFiles", keyFiles,
            "verification", Map.of("build", "not_run", "tests", "not_run", "manualCheck", "not_run"),
            "unfinished", List.of("独立验证尚未完成"),
            "sedimentCandidates", List.of()
        ));
        return result;
    }

    private List<HistoryEventResponse> currentEvents(UUID userId, UUID projectId) {
        List<HistoryEventResponse> result = new ArrayList<>();
        int page = 0;
        while (true) {
            var response = readService.events(
                userId, projectId, null, null, null, null, null, "CURRENT", null, false,
                null, null, page, 200
            );
            result.addAll(response.items());
            if (++page >= response.totalPages()) break;
        }
        return List.copyOf(result);
    }

    private static List<String> clean(List<String> values) {
        return values == null ? List.of() : values.stream().filter(value -> value != null && !value.isBlank()).toList();
    }

    private static void initGit(Path root) throws Exception {
        Files.createDirectories(root);
        run(root, "git", "init", "-b", "master");
        run(root, "git", "config", "user.email", "history-eval@example.invalid");
        run(root, "git", "config", "user.name", "History Evaluation");
    }

    private static void write(Path root, String relative, String content) throws IOException {
        Path target = root.resolve(relative);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content, StandardCharsets.UTF_8);
    }

    private static void writeAt(Path root, String relative, String content, String modifiedAt) throws IOException {
        write(root, relative, content);
        Files.setLastModifiedTime(
            root.resolve(relative), java.nio.file.attribute.FileTime.from(Instant.parse(modifiedAt))
        );
    }

    private static String commit(Path root, String message, String occurredAt) throws Exception {
        run(root, "git", "add", "-A");
        ProcessBuilder builder = new ProcessBuilder("git", "commit", "-m", message)
            .directory(root.toFile()).redirectErrorStream(true);
        builder.environment().put("GIT_AUTHOR_DATE", occurredAt);
        builder.environment().put("GIT_COMMITTER_DATE", occurredAt);
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.waitFor() != 0) throw new AssertionError("git commit failed: " + output);
        return run(root, "git", "rev-parse", "HEAD").trim();
    }

    private static String run(Path root, String... command) throws Exception {
        Process process = new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.waitFor() != 0) throw new AssertionError(String.join(" ", command) + " failed: " + output);
        return output;
    }

    public record FixtureExecution(
        ProjectHistoryV385QualityEvaluator.CaseObservation observation,
        boolean modelUsed,
        boolean degraded,
        boolean cacheHit,
        long modelLatencyMs,
        Map<String, Object> diagnostics
    ) {
        public FixtureExecution {
            diagnostics = diagnostics == null ? Map.of() : Map.copyOf(diagnostics);
        }
    }

    private static final class AliasRegistry {
        private final Map<String, List<Predicate<HistoryEventResponse>>> selectors = new LinkedHashMap<>();

        void fact(String alias, UUID factId) {
            add(alias, event -> "PROJECT_FACT".equals(event.sourceType()) && factId.toString().equals(event.sourceIdentity()));
        }

        void currentFile(String alias, String relativePath) {
            add(alias, event -> ("current-file:" + relativePath).equals(event.sourceIdentity()));
        }

        void agentResult(String alias, String relativePath) {
            add(alias, event -> "AGENT_RESULT".equals(event.sourceType()) && relativePath.equals(event.sourceIdentity()));
        }

        void gitPath(String alias, String revision, String relativePath) {
            add(alias, event -> "GIT".equals(event.sourceType())
                && "FILE_CHANGE".equals(event.category())
                && (revision == null || revision.equals(event.sourceRevision()))
                && event.affectedPaths().contains(relativePath));
        }

        void path(String alias, Predicate<String> pathPredicate) {
            add(alias, event -> event.affectedPaths().stream().anyMatch(pathPredicate));
        }

        void add(String alias, Predicate<HistoryEventResponse> selector) {
            selectors.computeIfAbsent(alias, ignored -> new ArrayList<>()).add(selector);
        }

        Map<String, Set<UUID>> resolve(List<HistoryEventResponse> events) {
            Map<String, Set<UUID>> result = new LinkedHashMap<>();
            selectors.forEach((alias, matchers) -> {
                Set<UUID> ids = new LinkedHashSet<>();
                for (HistoryEventResponse event : events) {
                    if (matchers.stream().anyMatch(matcher -> matcher.test(event))) ids.add(event.id());
                }
                result.put(alias, Set.copyOf(ids));
            });
            return Map.copyOf(result);
        }
    }
}
