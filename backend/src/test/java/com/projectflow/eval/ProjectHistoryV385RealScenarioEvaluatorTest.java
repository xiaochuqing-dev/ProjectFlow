package com.projectflow.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.dto.ProjectHistoryDtos.ChangeStory;
import com.projectflow.dto.ProjectHistoryDtos.EvolutionThread;
import com.projectflow.dto.ProjectHistoryDtos.HistoryChapter;
import com.projectflow.dto.ProjectHistoryDtos.HistoryCorrectionRequest;
import com.projectflow.entity.AiProvider;
import com.projectflow.entity.EvidenceConfidence;
import com.projectflow.entity.ProjectFact;
import com.projectflow.entity.ProjectFactEpistemicStatus;
import com.projectflow.entity.ProjectFactOrigin;
import com.projectflow.entity.ProjectFactRecordStatus;
import com.projectflow.entity.ProjectHistorySnapshot;
import com.projectflow.entity.ProjectMemory;
import com.projectflow.entity.ProjectSpace;
import com.projectflow.entity.ProjectStatus;
import com.projectflow.repository.AiProviderRepository;
import com.projectflow.repository.ProjectFactRepository;
import com.projectflow.repository.ProjectHistorySnapshotRepository;
import com.projectflow.repository.ProjectHistoryWindowCheckpointRepository;
import com.projectflow.repository.ProjectMemoryRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.service.ModelGatewayService;
import com.projectflow.service.ModelOutputAdapter;
import com.projectflow.service.ModelTaskType;
import com.projectflow.service.ProjectAgentHistoryService;
import com.projectflow.service.ProjectHistoryCorrectionService;
import com.projectflow.service.ProjectHistoryPromptBuilder;
import com.projectflow.service.ProjectHistoryReadService;
import com.projectflow.service.ProjectHistoryReconstructionService;
import com.projectflow.service.ProjectHistoryWindowPlanner;
import com.projectflow.service.SensitiveContentRedactor;

/**
 * Runs the V3.8.5 scenarios that require a real Provider through the same
 * persisted reconstruction path used by the product. Fault cases call the real
 * Provider first and then inject a bounded in-memory fault; no response or
 * prompt is written to the qualification artifact.
 */
@SpringBootTest
@ActiveProfiles("test")
class ProjectHistoryV385RealScenarioEvaluatorTest {
    private static final String TRUTHFULNESS_P0_COMMIT =
        "commit:ae9fba1e60758252635695b797169dfde3c41e0a";
    private static final String TRUTHFULNESS_P0_FRONTEND_SKELETON = "file:frontend/next-env.d.ts";
    private static final List<String> FIRST_LAYER_FORBIDDEN = List.of(
        "src/main/java", "frontend/src", "backend/src", "Controller", "Repository", "DTO", "Entity",
        "Service", "Mapper", "Handler", "PRIMARY", "SUPPORTING", "ENGINEERING_GROUPING",
        "DETERMINISTIC", "USER_DECLARED_PRESENTATION", "Technical Atom", "Evidence ID", "相关变化",
        "形成初始结果", "进入当前时间点可确认的新状态"
    );
    private static final List<String> NON_CODE_FORBIDDEN = List.of("能力", "后端", "Controller", "Service", "Repository", "DTO");
    private static final List<String> GENERIC_PHRASES = List.of(
        "优化了系统", "改进了功能", "进行了重构", "提升了体验", "修改了相关文件", "项目变化", "相关调整"
    );
    private static final Pattern INDEXED_PLACEHOLDER_IDENTIFIER = Pattern.compile(
        ".*主题[-_ ]*\\d{3,}内容[-_ ]*\\d{3,}.*"
    );

    @Autowired ProjectRepository projectRepository;
    @Autowired ProjectMemoryRepository memoryRepository;
    @Autowired ProjectFactRepository factRepository;
    @Autowired ProjectHistorySnapshotRepository snapshotRepository;
    @Autowired ProjectHistoryWindowCheckpointRepository checkpointRepository;
    @Autowired AiProviderRepository providerRepository;
    @Autowired ProjectHistoryCorrectionService correctionService;
    @Autowired ProjectAgentHistoryService agentHistoryService;
    @Autowired ProjectHistoryReadService readService;
    @Autowired ProjectHistoryReconstructionService reconstructionService;
    @Autowired ModelOutputAdapter outputAdapter;
    @Autowired SensitiveContentRedactor redactor;
    @Autowired ObjectMapper objectMapper;
    @Autowired ApplicationContext applicationContext;
    @MockitoSpyBean ModelGatewayService modelGateway;

    @TempDir Path temporaryRoot;

    private final AtomicReference<String> activeScenario = new AtomicReference<>("setup");
    private final AtomicReference<FaultMode> faultMode = new AtomicReference<>(FaultMode.NONE);
    private final AtomicInteger storyOrdinal = new AtomicInteger();
    private final AtomicBoolean faultTriggered = new AtomicBoolean();
    private final AtomicInteger factSequence = new AtomicInteger();
    private final Map<String, CallAccumulator> calls = new LinkedHashMap<>();
    private ContinuationState continuationState;

    @BeforeEach
    void interceptRealCallsForSafeScenarioDiagnosticsAndBoundedFaults() throws Exception {
        doAnswer(invocation -> {
            String scenario = activeScenario.get();
            ModelTaskType task = invocation.getArgument(2, ModelTaskType.class);
            String prompt = invocation.getArgument(1, String.class);
            boolean validationRepair = prompt.contains(ProjectHistoryPromptBuilder.VALIDATION_REPAIR_MARKER);
            ModelGatewayService.StructuredModelResponse actual;
            try {
                actual = (ModelGatewayService.StructuredModelResponse) invocation.callRealMethod();
            } catch (Throwable failure) {
                calls.computeIfAbsent(scenario, ignored -> new CallAccumulator())
                    .addFailure(task, failureDiagnostics(failure), validationRepair, failedRequestCount(failure));
                System.err.println("V385_SAFE_PROVIDER_FAILURE scenario=" + scenario
                    + " task=" + task.name() + " validationRepair=" + validationRepair
                    + " " + safeProviderFailure(failure));
                throw failure;
            }
            calls.computeIfAbsent(scenario, ignored -> new CallAccumulator())
                .add(task, actual.diagnostics(), validationRepair);
            if (task == ModelTaskType.PROJECT_HISTORY_CHAPTER_SYNTHESIS
                && faultMode.get() == FaultMode.CHAPTER_SCHEMA_AFTER_REAL_CALL) {
                faultTriggered.set(true);
                String invalid = "{\"chapters\":[{\"chapterId\":\"unknown\",\"representedClusterIds\":[\"unknown\"],"
                    + "\"title\":\"未经校验的模型标题\",\"summary\":\"未经校验的模型摘要\"}]}";
                return new ModelGatewayService.StructuredModelResponse(
                    invalid, outputAdapter.parse(invalid), actual.diagnostics()
                );
            }
            if (task != ModelTaskType.PROJECT_HISTORY_SYNTHESIS) return actual;
            int ordinal = validationRepair ? storyOrdinal.get() : storyOrdinal.incrementAndGet();
            if (faultMode.get() == FaultMode.SCHEMA_AFTER_REAL_CALL && validationRepair && faultTriggered.get()) {
                String invalid = "{\"stories\":[],\"chapters\":[]}";
                return new ModelGatewayService.StructuredModelResponse(
                    invalid, outputAdapter.parse(invalid), actual.diagnostics()
                );
            }
            if (faultTriggered.get() || ordinal != 2) return actual;
            if (faultMode.get() == FaultMode.HTTP_503_AFTER_REAL_CALL) {
                faultTriggered.set(true);
                throw new ModelGatewayService.ModelHttpException(503);
            }
            if (faultMode.get() == FaultMode.SCHEMA_AFTER_REAL_CALL) {
                faultTriggered.set(true);
                String invalid = "{\"stories\":[],\"chapters\":[]}";
                return new ModelGatewayService.StructuredModelResponse(
                    invalid, outputAdapter.parse(invalid), actual.diagnostics()
                );
            }
            if (faultMode.get() == FaultMode.CANCEL_AFTER_REAL_CALL) {
                faultTriggered.set(true);
                throw new CancellationException("V3.8.5 controlled user cancellation after real Provider response");
            }
            return actual;
        }).when(modelGateway).callStructured(any(AiProvider.class), anyString(), any(ModelTaskType.class));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void qualifiesRealProviderAcrossContinuationFailureNonCodeCorrectionAndDogfood() throws Exception {
        ProjectFlowRealModelEvalIT.ProviderConfig config = ProjectFlowRealModelEvalIT.providerConfig();
        Assumptions.assumeTrue(config != null, "未提供真实 Provider 安全配置，V3.8.5 真实场景验收跳过");

        UUID userId = UUID.randomUUID();
        providerRepository.saveAndFlush(provider(userId, config));
        List<SafeScenarioRun> runs = new ArrayList<>();
        String scenarioScope = scenarioScope();
        if ("continuity".equals(scenarioScope)) {
            runs.add(runScenario("v39-small-delta-checkpoint-context-and-noop", FaultMode.NONE,
                () -> v39SmallDeltaContinuity(userId)));
            runs.add(runScenario("v39-http-503-failure-and-resume", FaultMode.HTTP_503_AFTER_REAL_CALL,
                () -> http503FailureRecovery(userId)));
            runs.add(runScenario("v39-no-git-document-continuity", FaultMode.NONE,
                () -> nonCode(userId, "v39-no-git-version", "deliverables/version.txt",
                    "整理交付版本并记录当前内容", "当前交付材料可读取，但没有可还原的 Git 历史。")));
        } else if ("correction".equals(scenarioScope)) {
            runs.add(runScenario("correction-local-invalidation", FaultMode.NONE,
                () -> correctionFocused(userId)));
        } else if ("chapter".equals(scenarioScope)) {
            runs.add(runScenario("chapter-large-coherent", FaultMode.NONE,
                () -> chapterLargeCoherent(userId)));
            runs.add(runScenario("chapter-large-heterogeneous", FaultMode.NONE,
                () -> chapterLargeHeterogeneous(userId)));
            runs.add(runScenario("chapter-repair-safety", FaultMode.CHAPTER_SCHEMA_AFTER_REAL_CALL,
                () -> chapterRepairSafety(userId)));
            runs.add(runScenario("chapter-review-fixtures", FaultMode.NONE,
                () -> chapterReviewFixtures(userId)));
            runs.add(runScenario("correction-local-invalidation", FaultMode.NONE,
                () -> correctionFocused(userId)));
            runs.add(runScenario("non-code-presentation", FaultMode.NONE,
                () -> nonCode(userId, "presentation", "slides/quarterly-review.md", "整理季度演示并形成清晰叙事", "演示材料已按问题、发现和结论重新排列。")));
            runs.add(runScenario("non-code-research-report", FaultMode.NONE,
                () -> nonCode(userId, "research-report", "paper/research-report.md", "补全研究报告并明确结论", "报告章节、引用和结论已经整理。")));
            runs.add(runScenario("non-code-data-analysis", FaultMode.NONE,
                () -> nonCode(userId, "data-analysis", "analysis/results.csv", "更新数据分析并解释关键指标", "数据表、图表说明和结论已经同步更新。")));
            runs.add(runScenario("projectflow-current-history-dogfood", FaultMode.NONE,
                () -> dogfood(userId)));
        } else {
            runs.add(runScenario("non-code-presentation", FaultMode.NONE,
                () -> nonCode(userId, "presentation", "slides/quarterly-review.md", "整理季度演示并形成清晰叙事", "演示材料已按问题、发现和结论重新排列。")));
            runs.add(runScenario("non-code-research-report", FaultMode.NONE,
                () -> nonCode(userId, "research-report", "paper/research-report.md", "补全研究报告并明确结论", "报告章节、引用和结论已经整理。")));
            runs.add(runScenario("non-code-data-analysis", FaultMode.NONE,
                () -> nonCode(userId, "data-analysis", "analysis/results.csv", "更新数据分析并解释关键指标", "数据表、图表说明和结论已经同步更新。")));
            runs.add(runScenario("non-code-brand-page", FaultMode.NONE,
                () -> nonCode(userId, "brand-page", "site/index.html", "调整品牌展示页并突出核心信息", "展示顺序和主要信息已经重新组织。")));
            runs.add(runScenario("non-code-no-git-version", FaultMode.NONE,
                () -> nonCode(userId, "no-git-version", "deliverables/version.txt", "整理交付版本并记录当前内容", "当前交付材料可读取，但没有可还原的 Git 历史。")));
            runs.add(runScenario("seventeen-window-restart-cache-and-chapter", FaultMode.NONE,
                () -> continuationAndChapter(userId)));
            runs.add(runScenario("correction-local-invalidation", FaultMode.NONE,
                () -> correctionInvalidatesOnlyOneWindow(userId)));
            runs.add(runScenario("schema-failure-isolation-and-retry", FaultMode.SCHEMA_AFTER_REAL_CALL,
                () -> schemaFailureRecovery(userId)));
            runs.add(runScenario("user-cancellation-and-restart", FaultMode.CANCEL_AFTER_REAL_CALL,
                () -> cancellationRecovery(userId)));
            runs.add(runScenario("raw-payload-minimization", FaultMode.NONE,
                () -> rawPayloadMinimization(userId)));
            runs.add(runScenario("projectflow-current-history-dogfood", FaultMode.NONE,
                () -> dogfood(userId)));
        }

        QualificationSummary summary = qualification(runs, scenarioScope);
        writeArtifact(config, runs, summary, scenarioScope);
        System.out.printf(
            "V385_REAL_SCENARIOS_DONE provider=%s model=%s status=%s scenarios=%d requests=%d tokens=%d elapsedMs=%d%n",
            config.name(), config.model(), summary.qualified() ? "PASS" : "FAIL", runs.size(),
            summary.physicalRequestCount(), summary.tokenCount(), summary.modelLatencyMs()
        );
        assertThat(runs).allSatisfy(run -> assertThat(run.status()).as(run.name() + ": " + run.failure()).isEqualTo("PASS"));
        assertThat(summary.qualified()).isTrue();
    }

    private SafeScenarioRun runScenario(String name, FaultMode mode, CheckedScenario scenario) {
        activeScenario.set(name);
        faultMode.set(mode);
        storyOrdinal.set(0);
        faultTriggered.set(false);
        calls.put(name, new CallAccumulator());
        long started = System.nanoTime();
        String status = "PASS";
        String failure = "";
        ScenarioEvidence evidence = ScenarioEvidence.empty();
        try {
            System.out.printf("V385_REAL_SCENARIO_START name=%s fault=%s%n", name, mode);
            evidence = scenario.run();
            if (!evidence.failures().isEmpty()) {
                status = "FAIL";
                failure = String.join("; ", evidence.failures());
            }
        } catch (Throwable throwable) {
            status = "FAIL";
            failure = safeFailure(throwable);
        } finally {
            faultMode.set(FaultMode.NONE);
            activeScenario.set("idle");
        }
        CallAccumulator call = calls.getOrDefault(name, new CallAccumulator());
        SafeScenarioRun result = new SafeScenarioRun(
            name, status, failure, call.storyLogicalCalls, call.chapterLogicalCalls,
            call.validationRepairCalls, call.physicalRequests,
            call.tokens, call.latencyMs, elapsedMs(started), evidence.metrics(), evidence.samples()
        );
        System.out.printf(
            "V385_REAL_SCENARIO_DONE name=%s status=%s storyCalls=%d chapterCalls=%d requests=%d tokens=%d elapsedMs=%d%n",
            name, status, result.storyModelCallCount(), result.chapterModelCallCount(), result.physicalRequestCount(),
            result.tokenCount(), result.elapsedMs()
        );
        return result;
    }

    private ScenarioEvidence nonCode(
        UUID userId,
        String fixtureName,
        String primaryPath,
        String title,
        String summary
    ) throws Exception {
        Path root = temporaryRoot.resolve(fixtureName);
        Files.createDirectories(root);
        String primaryContent = switch (fixtureName) {
            case "presentation" -> "# Quarterly review\n\n## Problem\n## Findings\n## Conclusion\n";
            case "research-report" -> "# Research report\n\n## Method\n## Findings\n## Conclusion\n";
            case "data-analysis" -> "metric,value\nretention,0.82\nconversion,0.31\n";
            case "brand-page" -> "<main><h1>Northstar Studio</h1><p>Clear product message.</p></main>\n";
            default -> "version=3\nstatus=current-material-only\n";
        };
        write(root, primaryPath, primaryContent);
        String supportingPath = fixtureName.equals("data-analysis")
            ? "analysis/metric-notes.json" : "materials/reading-order.md";
        write(root, supportingPath, fixtureName.equals("data-analysis")
            ? "{\"retention\":\"returning readers\"}\n" : "Reading order and review notes.\n");
        ProjectSpace project = project(userId, "Non-code " + fixtureName, root);
        fact(project, title, summary, primaryPath, Instant.parse("2026-01-01T00:00:00Z"));
        fact(project, "补充核对说明并明确阅读顺序", "支撑材料记录了核对范围和阅读顺序。", supportingPath,
            Instant.parse("2026-01-02T00:00:00Z"));

        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);
        var overview = readService.overview(userId, project.getId());
        List<ChangeStory> stories = allStories(userId, project.getId());
        require(!stories.isEmpty(), fixtureName + " 未生成可读 Story");
        require("CURRENT_STATE_ONLY".equals(overview.coverage().currentness()), fixtureName + " 未诚实披露无 Git 历史");
        require(firstLayerLeaks(stories, NON_CODE_FORBIDDEN) == 0, fixtureName + " 第一层出现软件偏置");
        require(validRoleGraph(stories), fixtureName + " Primary/Supporting 关系不合法");
        require(stories.stream().anyMatch(story -> !readService.story(userId, project.getId(), story.id()).events().isEmpty()),
            fixtureName + " 无法从 Story 下钻 Evidence");
        CallAccumulator call = calls.get(activeScenario.get());
        require(call != null && call.storyLogicalCalls > 0,
            fixtureName + " 未执行真实 Story 模型窗口；safeDiagnostics="
                + safeDiagnostics(overview.diagnostics()));

        Map<String, Object> metrics = safeDiagnostics(overview.diagnostics());
        metrics.put("storyCount", stories.size());
        metrics.put("primaryCount", stories.stream().filter(story -> !story.supporting()).count());
        metrics.put("supportingCount", stories.stream().filter(ChangeStory::supporting).count());
        metrics.put("currentness", overview.coverage().currentness());
        metrics.put("firstLayerTechnicalLeakRate", rate(firstLayerLeaks(stories, FIRST_LAYER_FORBIDDEN), stories.size()));
        String revision = reviewRevision(userId, project.getId());
        List<Map<String, Object>> samples = List.of(
            Map.of("sampleType", "stories", "items",
                ProjectHistoryV385ReviewSamples.stories(stories, 4, revision)),
            Map.of("sampleType", "chapter-representativeness", "items",
                ProjectHistoryV385ReviewSamples.chapterRepresentativeness(
                    allChapters(userId, project.getId()), stories, 2, revision
                ))
        );
        return new ScenarioEvidence(metrics, samples);
    }

    private ScenarioEvidence chapterLargeCoherent(UUID userId) throws Exception {
        Path root = temporaryRoot.resolve("chapter-large-coherent-real");
        Files.createDirectories(root);
        ProjectSpace project = project(userId, "Large coherent Chapter", root);
        historicalFacts(project, 0, 100, 1, 1, 0);

        Map<String, Object> diagnostics = completeRefresh(userId, project.getId(), 12);
        List<HistoryChapter> chapters = allChapters(userId, project.getId());
        require(calls.get(activeScenario.get()).chapterLogicalCalls > 0,
            "大 coherent Chapter 未执行 Representation Plan 模型润色");
        require(number(diagnostics, "chaptersNeedingSplit") == 0, "coherent Chapter 被标记为仍需拆分");
        require(number(diagnostics, "chaptersWithMinorClusterTitleRisk") == 0, "coherent Chapter 标题由 minor cluster 劫持");
        require(number(diagnostics, "unsupportedClaimCount") == 0, "coherent Chapter 产生不受支持的状态表达");
        require(decimal(diagnostics, "representativePrimaryCoverage") >= 0.72,
            "coherent Chapter 代表 Primary 覆盖不足");
        require(chapters.stream().anyMatch(chapter -> chapter.storyRefs().size() >= 32),
            "coherent fixture 未形成大 Chapter");
        Map<String, Object> metrics = safeDiagnostics(diagnostics);
        metrics.put("finalCacheHit", reconstructionService.refresh(
            userId, project.getId(), UUID.randomUUID(), false
        ).cacheHit());
        return new ScenarioEvidence(metrics, List.of(Map.of(
            "sampleType", "chapter-representativeness",
            "items", ProjectHistoryV385ReviewSamples.chapterRepresentativeness(
                chapters, allStories(userId, project.getId()), 4, reviewRevision(userId, project.getId())
            )
        )));
    }

    private ScenarioEvidence chapterLargeHeterogeneous(UUID userId) throws Exception {
        Path root = temporaryRoot.resolve("chapter-large-heterogeneous-real");
        Files.createDirectories(root);
        ProjectSpace project = project(userId, "Large heterogeneous Chapter", root);
        Instant first = Instant.parse("2026-01-01T00:00:00Z");
        for (int index = 0; index < 6; index++) {
            fact(project, "建立身份验证流程并形成登录入口", "身份验证流程已形成可核对的登录结果。",
                "identity/login-flow-" + index + ".java", first.plusSeconds(index * 86_400L));
        }
        for (int index = 0; index < 6; index++) {
            fact(project, "形成财务报表导出并支持下载", "财务报表已形成独立的导出结果。",
                "finance/report-export-" + index + ".java", first.plusSeconds((14L + index) * 86_400L));
        }

        Map<String, Object> diagnostics = completeRefresh(userId, project.getId(), 12);
        List<HistoryChapter> chapters = allChapters(userId, project.getId());
        List<ChangeStory> stories = allStories(userId, project.getId());
        require(chapters.size() >= 2, "异质成果被强行保留在同一个 Chapter");
        require(chapters.stream().anyMatch(chapter -> chapter.boundarySignals().contains("REPRESENTATION_BOUNDARY")),
            "异质成果未记录 REPRESENTATION_BOUNDARY");
        require(number(diagnostics, "chaptersNeedingSplit") == 0, "异质 Chapter 拆分后仍存在待拆阶段");
        require(number(diagnostics, "chapterOverlapCount") == 0, "异质 Chapter 拆分产生 Story 重叠");
        require(number(diagnostics, "chaptersWithMinorClusterTitleRisk") == 0,
            "异质 Chapter 拆分后仍由 minor cluster 命名");
        Map<String, Object> metrics = safeDiagnostics(diagnostics);
        metrics.put("representationBoundaryCount", chapters.stream()
            .filter(chapter -> chapter.boundarySignals().contains("REPRESENTATION_BOUNDARY")).count());
        return new ScenarioEvidence(metrics, List.of(Map.of(
            "sampleType", "chapter-representativeness",
            "items", ProjectHistoryV385ReviewSamples.chapterRepresentativeness(
                chapters, stories, 4, reviewRevision(userId, project.getId())
            )
        )));
    }

    private ScenarioEvidence chapterRepairSafety(UUID userId) throws Exception {
        Path root = temporaryRoot.resolve("chapter-repair-safety-real");
        Files.createDirectories(root);
        ProjectSpace project = project(userId, "Chapter repair safety", root);
        historicalFacts(project, 0, 100, 1, 1, 0);

        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);
        Map<String, Object> failed = readService.overview(userId, project.getId()).diagnostics();
        List<HistoryChapter> preserved = allChapters(userId, project.getId()).stream()
            .filter(chapter -> chapter.authority().startsWith("ENGINEERING_"))
            .toList();
        List<ChangeStory> preservedStories = allStories(userId, project.getId());
        String preservedRevision = reviewRevision(userId, project.getId());
        require(number(failed, "chapterSynthesisFailedCount") > 0, "Chapter repair 失败未记录 failed checkpoint");
        require(!preserved.isEmpty(), "Chapter repair 失败未保留 deterministic Chapter");
        require(preserved.stream().noneMatch(chapter ->
            (chapter.title() + chapter.summary()).contains("未经校验")), "未校验模型文案进入快照");

        faultMode.set(FaultMode.NONE);
        Map<String, Object> recovered = completeRefresh(userId, project.getId(), 12);
        require(number(recovered, "chapterSynthesisFailedCount") == 0, "Chapter repair checkpoint 未安全恢复");
        require(number(recovered, "chaptersWithMinorClusterTitleRisk") == 0, "恢复后 Chapter 标题代表性不合格");
        require(number(recovered, "unsupportedClaimCount") == 0, "恢复后 Chapter Claim 越界");
        Map<String, Object> metrics = safeDiagnostics(recovered);
        metrics.put("repairFailurePreservedDeterministic", true);
        metrics.put("invalidModelWordingPersisted", false);
        return new ScenarioEvidence(metrics, List.of(Map.of(
            "sampleType", "deterministic-fallback",
            "items", ProjectHistoryV385ReviewSamples.chapterRepresentativeness(
                preserved, preservedStories, 4, preservedRevision
            )
        )));
    }

    private ScenarioEvidence chapterReviewFixtures(UUID userId) throws Exception {
        List<Map<String, Object>> samples = new ArrayList<>();
        Map<String, Object> metrics = new LinkedHashMap<>();
        Instant first = Instant.parse("2026-02-01T00:00:00Z");

        Path minorRoot = temporaryRoot.resolve("chapter-minor-first-real");
        Files.createDirectories(minorRoot);
        ProjectSpace minorProject = project(userId, "Minor-first Chapter", minorRoot);
        fact(minorProject, "补充项目使用说明并形成启动指引", "使用说明记录了启动步骤。",
            "docs/README.md", first);
        for (int index = 0; index < 8; index++) {
            fact(minorProject, "建立身份验证流程并形成登录入口", "身份验证流程已形成可核对的登录结果。",
                "identity/login-flow-" + index + ".java", first.plusSeconds((index + 1L) * 86_400L));
        }
        Map<String, Object> minorDiagnostics = completeRefresh(userId, minorProject.getId(), 12);
        List<ChangeStory> minorStories = allStories(userId, minorProject.getId());
        List<HistoryChapter> minorChapters = allChapters(userId, minorProject.getId());
        require(number(minorDiagnostics, "chaptersWithMinorClusterTitleRisk") == 0,
            "minor-first Story 劫持了 Chapter 标题");
        samples.add(Map.of("sampleType", "minor-first", "items",
            ProjectHistoryV385ReviewSamples.chapterRepresentativeness(
                minorChapters, minorStories, 2, reviewRevision(userId, minorProject.getId())
            )));

        Path supportingRoot = temporaryRoot.resolve("chapter-supporting-heavy-real");
        Files.createDirectories(supportingRoot);
        ProjectSpace supportingProject = project(userId, "Supporting-heavy Chapter", supportingRoot);
        fact(supportingProject, "建立登录流程并形成可用入口", "登录流程形成了可核对的主要结果。",
            "src/login/LoginFlow.java", first);
        for (int index = 0; index < 12; index++) {
            fact(supportingProject, "补充登录流程核对记录", "核对材料补充了登录流程的支撑信息。",
                "tests/login/LoginFlowTest-" + index + ".java", first.plusSeconds((index + 1L) * 3_600L));
        }
        Map<String, Object> supportingDiagnostics = completeRefresh(userId, supportingProject.getId(), 12);
        List<ChangeStory> supportingStories = allStories(userId, supportingProject.getId());
        List<HistoryChapter> supportingChapters = allChapters(userId, supportingProject.getId());
        long supportingCount = supportingStories.stream().filter(ChangeStory::supporting).count();
        long primaryCount = supportingStories.stream().filter(ChangeStory::primary).count();
        require(supportingCount > primaryCount, "Supporting-heavy fixture 未形成支撑变化占多数的 Chapter");
        require(number(supportingDiagnostics, "chaptersWithMinorClusterTitleRisk") == 0,
            "Supporting 数量劫持了 Chapter 中心");
        samples.add(Map.of("sampleType", "supporting-heavy", "items",
            ProjectHistoryV385ReviewSamples.chapterRepresentativeness(
                supportingChapters, supportingStories, 2, reviewRevision(userId, supportingProject.getId())
            )));

        Path shortRoot = temporaryRoot.resolve("chapter-short-and-declared-real");
        Files.createDirectories(shortRoot);
        ProjectSpace shortProject = project(userId, "Short and declared Chapter", shortRoot);
        fact(shortProject, "整理发布说明并明确交付范围", "发布说明记录了本次交付范围。",
            "release/release-notes.md", first);
        fact(shortProject, "整理交付清单并记录核对项", "交付清单记录了需要核对的材料。",
            "release/delivery-checklist.md", first.plusSeconds(86_400L));
        fact(shortProject, "记录发布核对结果", "核对结果保留了当前可确认状态。",
            "release/verification-notes.md", first.plusSeconds(2L * 86_400L));
        completeRefresh(userId, shortProject.getId(), 12);
        List<ChangeStory> shortStories = allStories(userId, shortProject.getId());
        List<HistoryChapter> automaticShort = allChapters(userId, shortProject.getId());
        String shortRevision = reviewRevision(userId, shortProject.getId());
        samples.add(Map.of("sampleType", "short-coherent", "items",
            ProjectHistoryV385ReviewSamples.chapterRepresentativeness(
                automaticShort, shortStories, 1, shortRevision
            )));
        ProjectHistorySnapshot shortSnapshot = snapshotRepository.findByProjectId(shortProject.getId()).orElseThrow();
        correctionService.create(userId, shortProject.getId(), new HistoryCorrectionRequest(
            "DECLARE_CHAPTER", "CHAPTER", "", shortStories.stream().map(ChangeStory::id).toList(),
            "发布准备与交付核对阶段", "这一阶段记录了发布说明、交付清单和核对结果。", "", "",
            shortRevision, shortSnapshot.getSourceEventFingerprint()
        ));
        List<HistoryChapter> declared = allChapters(userId, shortProject.getId()).stream()
            .filter(HistoryChapter::userDeclared).toList();
        require(declared.size() == 1, "用户声明 Chapter 未保留为独立展示覆盖层");
        require("发布准备与交付核对阶段".equals(declared.get(0).title()), "用户声明 Chapter 标题被自动覆盖");
        samples.add(Map.of("sampleType", "user-declared", "items",
            ProjectHistoryV385ReviewSamples.chapterRepresentativeness(
                declared, allStories(userId, shortProject.getId()), 1,
                reviewRevision(userId, shortProject.getId())
            )));

        CallAccumulator call = calls.get(activeScenario.get());
        require(call != null && call.storyLogicalCalls >= 3 && call.chapterLogicalCalls >= 3,
            "Chapter review fixtures 未经过真实 Story/Chapter 模型边界");
        metrics.put("minorFirstRiskCount", number(minorDiagnostics, "chaptersWithMinorClusterTitleRisk"));
        metrics.put("supportingPrimaryCount", primaryCount);
        metrics.put("supportingStoryCount", supportingCount);
        metrics.put("shortChapterCount", automaticShort.size());
        metrics.put("userDeclaredChapterMutationCount", 0);
        return new ScenarioEvidence(metrics, List.copyOf(samples));
    }

    private Map<String, Object> completeRefresh(UUID userId, UUID projectId, int maxRefreshes) throws Exception {
        Map<String, Object> diagnostics;
        try {
            diagnostics = readService.overview(userId, projectId).diagnostics();
        } catch (RuntimeException missingSnapshot) {
            diagnostics = Map.of();
        }
        String previousFailedProgress = failedProgress(diagnostics);
        int attempts = 0;
        do {
            reconstructionService.refresh(userId, projectId, UUID.randomUUID(), false);
            diagnostics = readService.overview(userId, projectId).diagnostics();
            String currentFailedProgress = failedProgress(diagnostics);
            if (!currentFailedProgress.isBlank() && currentFailedProgress.equals(previousFailedProgress)) break;
            previousFailedProgress = currentFailedProgress;
        } while (incomplete(diagnostics) && ++attempts < maxRefreshes);
        require(!incomplete(diagnostics), "Chapter fixture 未在有界刷新内完成");
        return diagnostics;
    }

    private ScenarioEvidence continuationAndChapter(UUID userId) throws Exception {
        Path root = temporaryRoot.resolve("seventeen-window-continuation");
        Files.createDirectories(root);
        ProjectSpace project = project(userId, "Seventeen window continuation", root);
        int storyLimit = ProjectHistoryWindowPlanner.DEFAULT_STORY_LIMIT;
        historicalFacts(project, 0, 17 * storyLimit, 1, 1, 0);

        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);
        Map<String, Object> first = readService.overview(userId, project.getId()).diagnostics();
        require(number(first, "totalWindowCount") == 17, "首次规划不是 17 个窗口");
        require(number(first, "succeededWindowCount") == 16,
            "首次没有严格完成前 16 个窗口；safeDiagnostics=" + safeDiagnostics(first));
        require(number(first, "pendingWindowCount") == 1, "首次没有保留第 17 个 pending 窗口");
        require(number(first, "nextWindowOrdinal") == 16, "continuation ordinal 未指向第 17 个窗口");

        ProjectHistoryReconstructionService restarted = applicationContext.getAutowireCapableBeanFactory()
            .createBean(ProjectHistoryReconstructionService.class);
        restarted.refresh(userId, project.getId(), UUID.randomUUID(), false);
        Map<String, Object> afterRestart = readService.overview(userId, project.getId()).diagnostics();
        require(number(afterRestart, "succeededWindowCount") == 17, "重建服务实例后没有继续第 17 个窗口");
        require(number(afterRestart, "pendingWindowCount") == 0, "Story continuation 完成后仍有 pending 窗口");

        int guard = 0;
        while (chapterIncomplete(afterRestart) && guard++ < 20) {
            restarted.refresh(userId, project.getId(), UUID.randomUUID(), false);
            afterRestart = readService.overview(userId, project.getId()).diagnostics();
        }
        require(!chapterIncomplete(afterRestart), "Chapter 二阶段归纳未在有界刷新内完成");
        require(number(afterRestart, "chapterSynthesisProcessedCount") > 0, "大 Chapter 未执行第二阶段归纳");
        var cached = restarted.refresh(userId, project.getId(), UUID.randomUUID(), false);
        require(cached.cacheHit(), "全部 Story/Chapter 完成后没有进入全局 cache hit");
        require(calls.get(activeScenario.get()).storyLogicalCalls == 17, "已成功窗口被重复调用");

        List<ChangeStory> stories = allStories(userId, project.getId());
        require(stories.size() == 17 * storyLimit, "17 个窗口的 Story 出现丢失");
        require(validRoleGraph(stories), "17 个窗口完成后的角色图不合法");
        continuationState = new ContinuationState(project.getId(), restarted, stories.get(0).id());
        Map<String, Object> metrics = safeDiagnostics(afterRestart);
        metrics.put("storyCount", stories.size());
        metrics.put("restartServiceInstance", true);
        metrics.put("finalCacheHit", true);
        return new ScenarioEvidence(metrics, ProjectHistoryV385ReviewSamples.stories(
            stories, 5, reviewRevision(userId, project.getId())));
    }

    private ScenarioEvidence v39SmallDeltaContinuity(UUID userId) throws Exception {
        Path root = temporaryRoot.resolve("v39-small-delta-continuity");
        Files.createDirectories(root);
        ProjectSpace project = project(userId, "V3.9 small delta continuity", root);
        int storyLimit = ProjectHistoryWindowPlanner.DEFAULT_STORY_LIMIT;
        historicalFacts(project, 0, 2 * storyLimit, 1, 1, 0);

        Map<String, Object> initialDiagnostics = completeRefresh(userId, project.getId(), 12);
        List<ChangeStory> initialStories = allStories(userId, project.getId());
        Set<String> initialStoryIds = initialStories.stream().map(ChangeStory::id)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> initialThreadIds = allThreads(userId, project.getId()).stream().map(EvolutionThread::id)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<UUID> initialCheckpointIds = checkpointRepository
            .findByProjectIdOrderByUpdatedAtAsc(project.getId()).stream()
            .filter(value -> value.getWindowIdentity().startsWith("window-"))
            .map(value -> value.getId()).toList();
        require(initialCheckpointIds.size() == 2, "V3.9 small-delta 初始窗口数不是 2");
        CallAccumulator call = calls.get(activeScenario.get());
        require(call != null && call.storyLogicalCalls == 2, "V3.9 small-delta 初始模型窗口调用数不是 2");

        var initialState = readService.currentState(userId, project.getId());
        var initialContext = agentHistoryService.contextPackage(userId, project.getId(), 32_000);
        historicalFacts(project, 2 * storyLimit, 1, 1, 1, 0);
        Map<String, Object> appendedDiagnostics = completeRefresh(userId, project.getId(), 12);
        require(call.storyLogicalCalls == 3, "V3.9 small delta 重跑了无关成功窗口");
        require(number(appendedDiagnostics, "continuityDeltaSize") > 0, "V3.9 small delta 未被记录");
        require(number(appendedDiagnostics, "modelWindowCacheHitCount") >= 2,
            "V3.9 small delta 未复用前两个窗口");

        List<UUID> appendedCheckpointIds = checkpointRepository
            .findByProjectIdOrderByUpdatedAtAsc(project.getId()).stream()
            .filter(value -> value.getWindowIdentity().startsWith("window-"))
            .map(value -> value.getId()).toList();
        require(appendedCheckpointIds.containsAll(initialCheckpointIds), "V3.9 small delta 替换了成功 checkpoint");
        List<ChangeStory> appendedStories = allStories(userId, project.getId());
        require(appendedStories.stream().map(ChangeStory::id).collect(java.util.stream.Collectors.toSet())
            .containsAll(initialStoryIds), "V3.9 small delta 改变了未受影响 Story identity");
        require(allThreads(userId, project.getId()).stream().map(EvolutionThread::id)
            .collect(java.util.stream.Collectors.toSet()).containsAll(initialThreadIds),
            "V3.9 small delta 改变了未受影响 Thread identity");

        var appendedState = readService.currentState(userId, project.getId());
        var appendedContext = agentHistoryService.contextPackage(userId, project.getId(), 32_000);
        require(!appendedState.stateRevision().equals(initialState.stateRevision()),
            "V3.9 relevant delta 未更新 Current State revision");
        require(!appendedContext.packageRevision().equals(initialContext.packageRevision()),
            "V3.9 relevant delta 未更新 Context Package revision");
        require(appendedContext.currentProjectState() != null
            && appendedContext.currentProjectState().stateRevision().equals(appendedState.stateRevision()),
            "V3.9 Current State 与 Context Package revision 不一致");

        int callsBeforeNoop = call.storyLogicalCalls + call.chapterLogicalCalls;
        var cached = reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);
        require(cached.cacheHit(), "V3.9 small delta 完成后 no-change 未进入 cache hit");
        require(call.storyLogicalCalls + call.chapterLogicalCalls == callsBeforeNoop,
            "V3.9 no-change 仍调用模型");
        require(readService.currentState(userId, project.getId()).stateRevision().equals(appendedState.stateRevision()),
            "V3.9 no-change 改变 Current State revision");
        require(agentHistoryService.contextPackage(userId, project.getId(), 32_000).packageRevision()
            .equals(appendedContext.packageRevision()), "V3.9 no-change 改变 Context Package revision");

        Map<String, Object> metrics = safeDiagnostics(appendedDiagnostics);
        metrics.put("initialWindowCount", number(initialDiagnostics, "totalWindowCount"));
        metrics.put("appendedWindowCount", number(appendedDiagnostics, "totalWindowCount"));
        metrics.put("successfulCheckpointReplayCount", 0);
        metrics.put("unrelatedWindowRerunCount", 0);
        metrics.put("unchangedStoryIdentityPercent", 100);
        metrics.put("unchangedThreadIdentityPercent", 100);
        metrics.put("contextStateRevisionMatched", true);
        metrics.put("finalNoChangeModelRequests", 0);
        metrics.put("finalCacheHit", true);
        return new ScenarioEvidence(metrics, ProjectHistoryV385ReviewSamples.stories(
            appendedStories, 5, reviewRevision(userId, project.getId())
        ));
    }

    private ScenarioEvidence correctionInvalidatesOnlyOneWindow(UUID userId) throws Exception {
        require(continuationState != null, "continuation fixture 不可用");
        ProjectHistorySnapshot snapshot = snapshotRepository.findByProjectId(continuationState.projectId()).orElseThrow();
        String revision = correctionService.list(userId, continuationState.projectId()).presentationRevision();
        correctionService.create(userId, continuationState.projectId(), new HistoryCorrectionRequest(
            "RENAME_STORY", "STORY", continuationState.storyId(), List.of(),
            "重新整理项目结果并明确当前状态", "", "", "", revision, snapshot.getSourceEventFingerprint()
        ));
        continuationState.service().refresh(userId, continuationState.projectId(), UUID.randomUUID(), false);
        CallAccumulator call = calls.get(activeScenario.get());
        require(call.storyLogicalCalls == 1, "一次展示修正没有只失效目标 Story 窗口");
        ChangeStory corrected = readService.story(userId, continuationState.projectId(), continuationState.storyId()).story();
        require("重新整理项目结果并明确当前状态".equals(corrected.humanTitle()), "用户修正未出现在 corrected read model");
        var cached = continuationState.service().refresh(userId, continuationState.projectId(), UUID.randomUUID(), false);
        require(cached.cacheHit(), "修正后的新窗口结果未缓存");
        require(call.storyLogicalCalls == 1, "修正后的全局 cache hit 重复调用模型");
        return new ScenarioEvidence(Map.of(
            "invalidatedStoryWindowCount", 1,
            "fullAutomaticWindowRerun", false,
            "presentationAuthority", corrected.presentationAuthority(),
            "finalCacheHit", true
        ), ProjectHistoryV385ReviewSamples.stories(
            List.of(corrected), 1, reviewRevision(userId, continuationState.projectId())));
    }

    private ScenarioEvidence correctionFocused(UUID userId) throws Exception {
        Path root = temporaryRoot.resolve("correction-focused");
        Files.createDirectories(root);
        ProjectSpace project = project(userId, "Correction focused", root);
        historicalFacts(project, 0, 64, 1, 1, 0);

        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);
        Map<String, Object> initial = readService.overview(userId, project.getId()).diagnostics();
        int initialWindowCount = (64 + ProjectHistoryWindowPlanner.DEFAULT_STORY_LIMIT - 1)
            / ProjectHistoryWindowPlanner.DEFAULT_STORY_LIMIT;
        require(number(initial, "totalWindowCount") == initialWindowCount,
            "展示标签归一化改变了窗口规划；safeDiagnostics=" + safeDiagnostics(initial));
        require(number(initial, "succeededWindowCount") == initialWindowCount,
            "受影响窗口未全部完成；safeDiagnostics=" + safeDiagnostics(initial));
        List<ChangeStory> initialStories = allStories(userId, project.getId());
        require(initialStories.size() == 64, "展示标签归一化造成 Story 丢失");
        require(firstLayerLeaks(initialStories, FIRST_LAYER_FORBIDDEN) == 0,
            "编号占位符或工程术语泄漏到修正前第一层");
        CallAccumulator call = calls.get(activeScenario.get());
        require(call != null && call.storyLogicalCalls == initialWindowCount, "修正前没有严格执行全部 Story 窗口");

        ChangeStory target = initialStories.get(0);
        ProjectHistorySnapshot snapshot = snapshotRepository.findByProjectId(project.getId()).orElseThrow();
        String revision = correctionService.list(userId, project.getId()).presentationRevision();
        correctionService.create(userId, project.getId(), new HistoryCorrectionRequest(
            "RENAME_STORY", "STORY", target.id(), List.of(),
            "重新整理项目结果并明确当前状态", "", "", "", revision, snapshot.getSourceEventFingerprint()
        ));
        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);
        require(call.storyLogicalCalls == initialWindowCount + 1, "一次展示修正没有只失效目标 Story 窗口");
        ChangeStory corrected = readService.story(userId, project.getId(), target.id()).story();
        require("重新整理项目结果并明确当前状态".equals(corrected.humanTitle()), "用户修正未出现在 corrected read model");
        require(firstLayerLeaks(List.of(corrected), FIRST_LAYER_FORBIDDEN) == 0,
            "编号占位符或工程术语泄漏到修正后第一层");
        var cached = reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);
        require(cached.cacheHit(), "修正后的新窗口结果未缓存");
        require(call.storyLogicalCalls == initialWindowCount + 1, "修正后的全局 cache hit 重复调用模型");
        return new ScenarioEvidence(Map.of(
            "initialStoryCount", initialStories.size(),
            "initialWindowCount", initialWindowCount,
            "invalidatedStoryWindowCount", 1,
            "indexedPlaceholderLeakCount", 0,
            "fullAutomaticWindowRerun", false,
            "presentationAuthority", corrected.presentationAuthority(),
            "finalCacheHit", true
        ), ProjectHistoryV385ReviewSamples.stories(
            List.of(corrected), 1, reviewRevision(userId, project.getId())));
    }

    private ScenarioEvidence schemaFailureRecovery(UUID userId) throws Exception {
        ProjectSpace project = threeWindowProject(userId, "schema-failure");
        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);
        Map<String, Object> failed = readService.overview(userId, project.getId()).diagnostics();
        require(number(failed, "succeededWindowCount") == 2, "局部 schema failure 后独立窗口未继续");
        require(number(failed, "failedWindowCount") == 1, "局部 schema failure 未记录失败 checkpoint");
        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);
        Map<String, Object> recovered = readService.overview(userId, project.getId()).diagnostics();
        require(number(recovered, "succeededWindowCount") == 3, "schema failure 重试后未全部完成");
        require(number(recovered, "failedWindowCount") == 0, "schema failure checkpoint 未恢复");
        require(calls.get(activeScenario.get()).storyLogicalCalls == 4, "重试执行了成功窗口或遗漏失败窗口");
        return new ScenarioEvidence(safeDiagnostics(recovered),
            ProjectHistoryV385ReviewSamples.stories(
                allStories(userId, project.getId()), 4, reviewRevision(userId, project.getId())));
    }

    private ScenarioEvidence http503FailureRecovery(UUID userId) throws Exception {
        ProjectSpace project = threeWindowProject(userId, "v39-http-503-failure");
        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);
        Map<String, Object> failed = readService.overview(userId, project.getId()).diagnostics();
        require(number(failed, "succeededWindowCount") == 2,
            "HTTP 503 后独立窗口未继续；safeDiagnostics=" + safeDiagnostics(failed));
        require(number(failed, "failedWindowCount") == 1, "HTTP 503 未记录失败 checkpoint");
        require(number(failed, "pendingWindowCount") == 0, "HTTP 503 后出现未处理窗口");
        List<UUID> firstSucceeded = checkpointRepository.findByProjectIdOrderByUpdatedAtAsc(project.getId()).stream()
            .filter(value -> value.getWindowIdentity().startsWith("window-"))
            .filter(value -> "SUCCEEDED".equals(value.getStatus())).map(value -> value.getId()).toList();
        require(firstSucceeded.size() == 2, "HTTP 503 后的成功 checkpoint 数量不正确");

        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);
        Map<String, Object> recovered = readService.overview(userId, project.getId()).diagnostics();
        require(number(recovered, "succeededWindowCount") == 3, "HTTP 503 重试后未全部完成");
        require(number(recovered, "failedWindowCount") == 0, "HTTP 503 checkpoint 未恢复");
        require(number(recovered, "pendingWindowCount") == 0, "HTTP 503 恢复后仍有 pending window");
        require(calls.get(activeScenario.get()).storyLogicalCalls == 4,
            "HTTP 503 恢复重复调用了成功窗口或遗漏失败范围");
        Map<UUID, String> finalStatuses = checkpointRepository
            .findByProjectIdOrderByUpdatedAtAsc(project.getId()).stream()
            .collect(LinkedHashMap::new, (map, value) -> map.put(value.getId(), value.getStatus()), Map::putAll);
        require(firstSucceeded.stream().allMatch(id -> "SUCCEEDED".equals(finalStatuses.get(id))),
            "HTTP 503 恢复覆盖了此前成功 checkpoint");

        Map<String, Object> metrics = safeDiagnostics(recovered);
        metrics.put("faultInjection", "HTTP_503_AFTER_REAL_PROVIDER_CALL");
        metrics.put("successfulCheckpointReplayCount", 0);
        metrics.put("recoveredFailedScope", true);
        return new ScenarioEvidence(metrics, ProjectHistoryV385ReviewSamples.stories(
            allStories(userId, project.getId()), 4, reviewRevision(userId, project.getId())
        ));
    }

    private ScenarioEvidence cancellationRecovery(UUID userId) throws Exception {
        ProjectSpace project = threeWindowProject(userId, "user-cancellation");
        boolean cancelled = false;
        try {
            reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);
        } catch (CancellationException expected) {
            cancelled = true;
        }
        require(cancelled, "受控用户取消没有终止当前刷新");
        List<String> statuses = checkpointRepository.findByProjectIdOrderByUpdatedAtAsc(project.getId()).stream()
            .filter(value -> value.getWindowIdentity().startsWith("window-"))
            .map(value -> value.getStatus()).toList();
        require(statuses.contains("SUCCEEDED") && statuses.contains("CANCELLED"), "取消 checkpoint 状态不完整");

        ProjectHistoryReconstructionService restarted = applicationContext.getAutowireCapableBeanFactory()
            .createBean(ProjectHistoryReconstructionService.class);
        restarted.refresh(userId, project.getId(), UUID.randomUUID(), false);
        Map<String, Object> recovered = readService.overview(userId, project.getId()).diagnostics();
        require(number(recovered, "succeededWindowCount") == 3, "取消后重建服务实例未完成剩余窗口");
        require(number(recovered, "failedWindowCount") == 0, "取消恢复后仍有失败窗口");
        require(calls.get(activeScenario.get()).storyLogicalCalls == 4, "取消恢复重复调用了已成功窗口");
        Map<String, Object> metrics = safeDiagnostics(recovered);
        metrics.put("cancelledCheckpointObserved", true);
        metrics.put("restartServiceInstance", true);
        return new ScenarioEvidence(metrics,
            ProjectHistoryV385ReviewSamples.stories(
                allStories(userId, project.getId()), 4, reviewRevision(userId, project.getId())));
    }

    private ScenarioEvidence rawPayloadMinimization(UUID userId) throws Exception {
        Path root = temporaryRoot.resolve("raw-payload-minimization-real");
        Files.createDirectories(root);
        ProjectSpace project = project(userId, "Raw payload minimization real Provider", root);
        historicalFacts(project, 0, ProjectHistoryWindowPlanner.DEFAULT_STORY_LIMIT, 8, 12, 120);
        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);
        Map<String, Object> diagnostics = readService.overview(userId, project.getId()).diagnostics();
        int total = number(diagnostics, "totalWindowCount");
        require(total == 1, "原始技术载荷未在提示词预算前移除");
        require(number(diagnostics, "succeededWindowCount") == total, "最小化提示词窗口未完成");
        require(number(diagnostics, "skippedWindowCount") == 0, "最小化提示词产生永久 SKIPPED");
        require(number(diagnostics, "pendingWindowCount") == 0, "最小化提示词仍有 pending 窗口");
        require(calls.get(activeScenario.get()).storyLogicalCalls == total, "最小化提示词调用数不一致");
        var cached = reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);
        require(cached.cacheHit(), "最小化提示词窗口成功后未命中 cache");
        return new ScenarioEvidence(safeDiagnostics(diagnostics),
            ProjectHistoryV385ReviewSamples.stories(
                allStories(userId, project.getId()), 5, reviewRevision(userId, project.getId())));
    }

    private ScenarioEvidence dogfood(UUID userId) throws Exception {
        Path source = sourceRepository();
        int commitCount = Integer.parseInt(git(source, "rev-list", "--count", "HEAD").trim());
        require(commitCount > 197, "当前 ProjectFlow checkout 未包含 V3.8.5 完整历史");
        ProjectSpace project = project(userId, "ProjectFlow V3.8.5 Dogfood", source);
        Map<String, Object> diagnostics;
        try {
            diagnostics = completeRefresh(userId, project.getId(), 24);
        } catch (AssertionError failure) {
            Map<String, Object> current = readService.overview(userId, project.getId()).diagnostics();
            Map<String, Object> metrics = safeDiagnostics(current);
            metrics.put("failedCheckpointDiagnostics", safeCheckpointFailures(project.getId()));
            return new ScenarioEvidence(metrics, List.of(), List.of(
                "ProjectFlow Dogfood 在有界刷新或一次同状态失败重试内未完成"
            ));
        }
        var cached = reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);
        require(cached.cacheHit(), "ProjectFlow Dogfood 完成后未命中全局 cache");

        var overview = readService.overview(userId, project.getId());
        List<ChangeStory> stories = allStories(userId, project.getId());
        List<HistoryChapter> chapters = allChapters(userId, project.getId());
        List<EvolutionThread> threads = allThreads(userId, project.getId());
        List<ChangeStory> primary = stories.stream().filter(story -> !story.supporting() && !story.hiddenByDefault()).toList();
        List<ChangeStory> supporting = stories.stream().filter(ChangeStory::supporting).toList();
        double genericRate = rate(genericCount(stories), stories.size());
        double technicalLeakRate = rate(firstLayerLeaks(stories, FIRST_LAYER_FORBIDDEN), stories.size());
        List<Map<String, Object>> supportingConsolidation = supportingConsolidationExamples(stories, 10);
        String presentationRevision = reviewRevision(userId, project.getId());
        ChangeStory truthfulnessP0 = stories.stream()
            .filter(ProjectHistoryV385RealScenarioEvaluatorTest::isTruthfulnessP0)
            .findFirst().orElse(null);

        List<Map<String, Object>> samples = new ArrayList<>();
        samples.add(Map.of("sampleType", "primary", "items",
            ProjectHistoryV385ReviewSamples.stories(primary, 15, presentationRevision)));
        samples.add(Map.of("sampleType", "explicit-supporting", "items",
            ProjectHistoryV385ReviewSamples.stories(supporting, 10, presentationRevision)));
        samples.add(Map.of("sampleType", "supporting-consolidation", "items", supportingConsolidation));
        samples.add(Map.of("sampleType", "chapters", "items",
            ProjectHistoryV385ReviewSamples.chapters(chapters, 8, presentationRevision)));
        samples.add(Map.of("sampleType", "chapter-representativeness", "items",
            ProjectHistoryV385ReviewSamples.chapterRepresentativeness(
                chapters, stories, 8, presentationRevision
            )));
        samples.add(Map.of("sampleType", "threads", "items", threadSamples(threads, 3)));
        samples.add(Map.of("sampleType", "manual-review-candidates", "items", reviewCandidates(stories, 5)));
        samples.add(Map.of("sampleType", "truthfulness-p0", "items", truthfulnessP0 == null
            ? List.of()
            : ProjectHistoryV385ReviewSamples.stories(List.of(truthfulnessP0), 1, presentationRevision)));
        Map<String, Object> metrics = safeDiagnostics(diagnostics);
        metrics.put("commitCount", commitCount);
        metrics.put("sourceEventCount", overview.sourceEventCount());
        metrics.put("technicalAtomCount", stories.stream().flatMap(story -> story.technicalAtomRefs().stream()).distinct().count());
        metrics.put("storyCount", stories.size());
        metrics.put("visiblePrimaryCount", primary.size());
        metrics.put("supportingCount", supporting.size());
        metrics.put("supportingConsolidationExampleCount", supportingConsolidation.size());
        metrics.put("hiddenOrMergedCount", stories.stream().filter(story -> story.hiddenByDefault() || !story.mergedIntoStoryId().isBlank()).count());
        metrics.put("chapterCount", chapters.size());
        metrics.put("threadCount", threads.size());
        metrics.put("chapterGenericTitleRate", rate(chapters.stream().filter(chapter ->
            GENERIC_PHRASES.stream().anyMatch(phrase -> chapter.title().contains(phrase))
                || chapter.title().contains("持续完善项目成果")
        ).count(), chapters.size()));
        metrics.put("correctionConflictCount", stories.stream().mapToLong(story -> story.correctionConflicts().size()).sum()
            + chapters.stream().mapToLong(chapter -> chapter.limitations().stream()
                .filter(value -> value.contains("修正") || value.contains("冲突")).count()).sum());
        metrics.put("genericTemplateRate", genericRate);
        metrics.put("firstLayerTechnicalLeakRate", technicalLeakRate);
        metrics.put("truthfulnessP0Found", truthfulnessP0 != null);
        metrics.put("truthfulnessP0ClaimState", truthfulnessP0 == null
            ? "MISSING" : truthfulnessP0.claimAttribution().state());
        metrics.put("finalCacheHit", true);
        List<String> failures = new ArrayList<>();
        if (primary.size() < 15) failures.add("ProjectFlow Dogfood 不足 15 个代表性 Primary Story");
        if (supportingConsolidation.size() < 10) failures.add("ProjectFlow Dogfood 不足 10 个可核对的 Supporting 归并示例");
        if (chapters.size() < 5) failures.add("ProjectFlow Dogfood 不足 5 个 Chapter");
        if (threads.size() < 3) failures.add("ProjectFlow Dogfood 不足 3 条演变链");
        if (!validRoleGraph(stories)) failures.add("ProjectFlow Dogfood 角色图不合法");
        if (!Boolean.TRUE.equals(overview.diagnostics().get("eventConservation"))) failures.add("ProjectFlow Dogfood 原始事件不守恒");
        if (genericRate > 0.05) failures.add("ProjectFlow Dogfood generic template rate 超过 0.05");
        if (technicalLeakRate > 0.05) failures.add("ProjectFlow Dogfood technical leak rate 超过 0.05");
        if (number(diagnostics, "chaptersWithMinorClusterTitleRisk") > 0) {
            failures.add("ProjectFlow Dogfood 存在标题只代表 minor cluster 的 Chapter");
        }
        if (number(diagnostics, "technicalLeakCount") > 0) failures.add("ProjectFlow Chapter 第一层存在技术泄漏");
        if (number(diagnostics, "unsupportedClaimCount") > 0) failures.add("ProjectFlow Chapter 存在不受支持 Claim");
        if (number(diagnostics, "chapterOverlapCount") > 0) failures.add("ProjectFlow Chapter Story membership 重叠");
        if (number(diagnostics, "orphanSupportingCount") > 0) failures.add("ProjectFlow 存在孤儿 Supporting Story");
        if (truthfulnessP0 == null) {
            failures.add("ProjectFlow Dogfood 缺少 ae9f 前端骨架真实性 P0 样本");
        } else {
            String state = truthfulnessP0.claimAttribution().state();
            if (Set.of("IMPLEMENTED", "VERIFIED").contains(state)) {
                failures.add("ae9f 前端骨架被错误提升为已实现或已验证");
            }
            String narrative = String.join(" ", truthfulnessP0.humanTitle(), truthfulnessP0.oneSentenceSummary(),
                truthfulnessP0.change(), truthfulnessP0.afterState());
            if (unsupportedLoginImplementation(narrative)) {
                failures.add("ae9f 前端骨架被错误叙述为登录流程实现");
            }
        }
        return new ScenarioEvidence(metrics, samples, failures);
    }

    private static boolean isTruthfulnessP0(ChangeStory story) {
        Set<String> refs = new LinkedHashSet<>(story.evidenceRefs());
        return refs.contains(TRUTHFULNESS_P0_COMMIT) && refs.contains(TRUTHFULNESS_P0_FRONTEND_SKELETON);
    }

    private static boolean unsupportedLoginImplementation(String narrative) {
        if (narrative == null || !narrative.contains("登录")) return false;
        return List.of("实现登录", "登录流程已有代码实现", "登录功能", "具备用户登录", "完成登录")
            .stream().anyMatch(narrative::contains);
    }

    private ProjectSpace threeWindowProject(UUID userId, String name) throws Exception {
        Path root = temporaryRoot.resolve(name);
        Files.createDirectories(root);
        ProjectSpace project = project(userId, name, root);
        historicalFacts(project, 0, 3 * ProjectHistoryWindowPlanner.DEFAULT_STORY_LIMIT, 1, 1, 0);
        return project;
    }

    private List<Map<String, Object>> safeCheckpointFailures(UUID projectId) {
        return checkpointRepository.findByProjectIdOrderByUpdatedAtAsc(projectId).stream()
            .filter(checkpoint -> "FAILED".equals(checkpoint.getStatus()))
            .map(checkpoint -> {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("storyCount", Math.max(0, checkpoint.getStoryCount()));
                result.put("eventCount", Math.max(0, checkpoint.getEventCount()));
                try {
                    JsonNode value = objectMapper.readTree(checkpoint.getDiagnosticsJson());
                    for (String key : List.of(
                        "scope", "failureClass", "failureStage", "failureCode", "repairFailureStage",
                        "repairFailureCode", "validationKind", "validationCode", "retryType", "finishReason"
                    )) {
                        result.put(key, safeDiagnosticToken(value.path(key).asText()));
                    }
                    result.put("requestCount", Math.max(0, value.path("requestCount").asInt(0)));
                    result.put("truncated", value.path("truncated").asBoolean(false));
                    result.put("schemaMatched", value.path("schemaMatched").asBoolean(false));
                } catch (Exception ignored) {
                    result.put("failureClass", "DIAGNOSTIC_PARSE_FAILURE");
                }
                return Map.copyOf(result);
            })
            .toList();
    }

    private static String safeDiagnosticToken(String value) {
        if (value == null || value.isBlank()) return "";
        String safe = value.trim().replaceAll("[^A-Za-z0-9_:,.-]", "_");
        return safe.length() <= 160 ? safe : safe.substring(0, 160);
    }

    private ProjectSpace project(UUID userId, String name, Path root) {
        ProjectSpace project = new ProjectSpace(userId);
        project.update(name, "Public-safe V3.8.5 qualification fixture", ProjectStatus.BUILDING,
            List.of(), "", LocalDate.of(2026, 8, 6), null);
        project = projectRepository.saveAndFlush(project);
        ProjectMemory memory = new ProjectMemory(project.getId());
        memory.update("", "", "", "", "", "", "", "", "");
        memory.rememberLocalProjectPath(root.toAbsolutePath().normalize().toString());
        memoryRepository.saveAndFlush(memory);
        return project;
    }

    private ProjectFact fact(ProjectSpace project, String title, String summary, String path, Instant occurredAt) {
        int sequence = factSequence.incrementAndGet();
        ProjectFact fact = new ProjectFact(
            project.getId(), null, null, ProjectFactOrigin.INCREMENTAL_SCAN, String.format("%064d", sequence)
        );
        fact.updateContent(
            title, summary, List.of(summary), "结果可从来源继续核对。", occurredAt, occurredAt,
            List.of(), List.of(), List.of(), List.of(path), List.of("source:real-scenario-" + sequence),
            "LOCAL_RULE", "PASS", EvidenceConfidence.HIGH, ProjectFactRecordStatus.RECORDED, ""
        );
        fact.applyKnowledgeContract(
            ProjectFactEpistemicStatus.OBSERVED, List.of("SYNTHETIC_PUBLIC_SAFE"), "CURRENT",
            "real-scenario-revision-" + sequence, occurredAt, occurredAt, List.of(), List.of(),
            "ENGINEERING_VALIDATION", "", "", "VALIDATED"
        );
        return factRepository.saveAndFlush(fact);
    }

    private void historicalFacts(
        ProjectSpace project,
        int startIndex,
        int count,
        int pathCount,
        int evidenceCount,
        int payloadWidth
    ) {
        Instant first = Instant.parse("2024-01-01T00:00:00Z");
        List<ProjectFact> facts = new ArrayList<>();
        for (int offset = 0; offset < count; offset++) {
            int index = startIndex + offset;
            int sequence = factSequence.incrementAndGet();
            Instant occurredAt = first.plusSeconds(index * 3_600L);
            List<String> paths = new ArrayList<>();
            for (int pathIndex = 0; pathIndex < pathCount; pathIndex++) {
                if (payloadWidth <= 0) {
                    paths.add("results/项目结果主题" + String.format("%05d", index) + "内容"
                        + String.format("%03d", pathIndex) + ".java");
                } else {
                    paths.add("results/Outcome" + String.format("%05d", index) + "Part"
                        + String.format("%03d", pathIndex) + "-" + "p".repeat(payloadWidth) + ".md");
                }
            }
            List<String> evidence = new ArrayList<>();
            for (int evidenceIndex = 0; evidenceIndex < evidenceCount; evidenceIndex++) {
                evidence.add("source:outcome-" + String.format("%05d", index) + "-"
                    + String.format("%03d", evidenceIndex) + (payloadWidth <= 0 ? "" : "-" + "e".repeat(payloadWidth)));
            }
            ProjectFact fact = new ProjectFact(
                project.getId(), null, null, ProjectFactOrigin.INCREMENTAL_SCAN, String.format("%064d", sequence)
            );
            fact.updateContent(
                "记录项目结果 " + index, "来源确认项目结果 " + index + " 已发生。", List.of("形成可核对结果"),
                "结果可从来源继续核对。", occurredAt, occurredAt, List.of(), List.of(), List.of(), paths, evidence,
                "LOCAL_RULE", "PASS", EvidenceConfidence.HIGH, ProjectFactRecordStatus.RECORDED, ""
            );
            fact.applyKnowledgeContract(
                ProjectFactEpistemicStatus.OBSERVED, List.of("SYNTHETIC_PUBLIC_SAFE"), "CURRENT",
                "real-window-revision-" + sequence, occurredAt, occurredAt, List.of(), List.of(),
                "ENGINEERING_VALIDATION", "", "", "VALIDATED"
            );
            facts.add(fact);
        }
        factRepository.saveAllAndFlush(facts);
    }

    private AiProvider provider(UUID userId, ProjectFlowRealModelEvalIT.ProviderConfig config) {
        AiProvider provider = new AiProvider(userId);
        provider.update(
            config.name(), config.baseUrl(), config.apiKey(), config.model(), config.type(),
            0.1, config.maxTokens(), true, List.of("V3.8.5_HISTORY_REAL_SCENARIOS")
        );
        provider.configureProtocol(
            config.protocol(), null, null, null, null, Map.of(), config.timeoutSeconds(), null,
            config.supportsJsonMode(), null, config.supportsReasoning(), config.supportsReasoningControl()
        );
        return provider;
    }

    private List<ChangeStory> allStories(UUID userId, UUID projectId) {
        readService.overview(userId, projectId);
        ProjectHistorySnapshot snapshot = snapshotRepository.findByProjectId(projectId).orElseThrow();
        return correctionService.resolve(projectId, snapshot).stories();
    }

    private String reviewRevision(UUID userId, UUID projectId) {
        return correctionService.list(userId, projectId).presentationRevision();
    }

    private List<HistoryChapter> allChapters(UUID userId, UUID projectId) {
        List<HistoryChapter> result = new ArrayList<>();
        int page = 0;
        while (true) {
            var slice = readService.chapters(userId, projectId, page, 100);
            result.addAll(slice.items());
            if (++page >= slice.totalPages()) return List.copyOf(result);
        }
    }

    private List<EvolutionThread> allThreads(UUID userId, UUID projectId) {
        List<EvolutionThread> result = new ArrayList<>();
        int page = 0;
        while (true) {
            var slice = readService.threads(userId, projectId, null, page, 100);
            result.addAll(slice.items());
            if (++page >= slice.totalPages()) return List.copyOf(result);
        }
    }

    private static boolean validRoleGraph(List<ChangeStory> stories) {
        Map<String, ChangeStory> indexed = new LinkedHashMap<>();
        for (ChangeStory story : stories) {
            if (story == null || indexed.put(story.id(), story) != null) return false;
        }
        for (ChangeStory story : stories) {
            if (story.supporting()) {
                ChangeStory primary = indexed.get(story.primaryStoryId());
                if (primary == null || primary.supporting() || !story.supportingChangeRefs().isEmpty()
                    || !primary.supportingChangeRefs().contains(story.id())) return false;
            } else {
                if (!story.primaryStoryId().isBlank()) return false;
                Set<String> unique = new LinkedHashSet<>(story.supportingChangeRefs());
                if (unique.size() != story.supportingChangeRefs().size()) return false;
                for (String ref : unique) {
                    ChangeStory support = indexed.get(ref);
                    if (support == null || !support.supporting() || !story.id().equals(support.primaryStoryId())) return false;
                }
            }
        }
        return true;
    }

    private static int firstLayerLeaks(List<ChangeStory> stories, List<String> forbidden) {
        int count = 0;
        for (ChangeStory story : stories) {
            String firstLayer = String.join(" ", story.humanTitle(), story.oneSentenceSummary(), story.beforeState(),
                story.change(), story.afterState(), story.reason(), story.laterOutcome());
            if (forbidden.stream().anyMatch(firstLayer::contains)
                || INDEXED_PLACEHOLDER_IDENTIFIER.matcher(firstLayer).matches()) count++;
        }
        return count;
    }

    private static int genericCount(List<ChangeStory> stories) {
        return (int) stories.stream().filter(story -> GENERIC_PHRASES.stream()
            .anyMatch(value -> (story.humanTitle() + " " + story.oneSentenceSummary()).contains(value))).count();
    }

    private static List<Map<String, Object>> supportingConsolidationExamples(List<ChangeStory> stories, int limit) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ChangeStory story : stories) {
            if (!story.supporting()) continue;
            result.add(Map.of(
                "relationType", "EXPLICIT_SUPPORTING_STORY",
                "storyId", story.id(),
                "primaryStoryId", story.primaryStoryId(),
                "title", story.humanTitle(),
                "evidenceRefs", story.evidenceRefs().stream().limit(3).toList()
            ));
            if (result.size() >= limit) return List.copyOf(result);
        }
        for (ChangeStory story : stories) {
            List<String> signals = java.util.stream.Stream.concat(
                    story.technicalDetails().stream(),
                    java.util.stream.Stream.concat(story.affectedAreas().stream(), story.commitSummaries().stream())
                )
                .filter(ProjectHistoryV385RealScenarioEvaluatorTest::supportSignal)
                .distinct().limit(5).toList();
            if (signals.isEmpty()) continue;
            result.add(Map.of(
                "relationType", "FOLDED_ENGINEERING_SUPPORT",
                "storyId", story.id(),
                "primaryStoryId", story.supporting() ? story.primaryStoryId() : story.id(),
                "title", story.humanTitle(),
                "supportSignals", signals,
                "evidenceRefs", story.evidenceRefs().stream().limit(3).toList()
            ));
            if (result.size() >= limit) return List.copyOf(result);
        }
        return List.copyOf(result);
    }

    private static boolean supportSignal(String value) {
        String normalized = value == null ? "" : value.replace('\\', '/').toLowerCase(Locale.ROOT);
        return normalized.contains("/test") || normalized.startsWith("test")
            || normalized.contains("validation") || normalized.contains("verify")
            || normalized.contains("/.github/") || normalized.startsWith(".github/")
            || normalized.contains("/config/") || normalized.startsWith("config/")
            || normalized.contains("/fixture") || normalized.startsWith("fixture")
            || normalized.contains("/migration") || normalized.startsWith("migration")
            || normalized.contains("readme") || normalized.contains("docs/");
    }

    private static List<Map<String, Object>> threadSamples(List<EvolutionThread> threads, int limit) {
        return threads.stream().limit(limit).map(thread -> Map.<String, Object>of(
            "id", thread.id(), "title", thread.subjectLabel(), "storyRefs", thread.storyRefs(),
            "transitions", thread.transitions(), "currentOutcome", thread.currentOutcome()
        )).toList();
    }

    private static List<Map<String, Object>> reviewCandidates(List<ChangeStory> stories, int limit) {
        return stories.stream().sorted(Comparator.comparingInt(ProjectHistoryV385RealScenarioEvaluatorTest::reviewPriority).reversed())
            .limit(limit).map(story -> Map.<String, Object>of(
                "id", story.id(), "title", story.humanTitle(), "summary", story.oneSentenceSummary(),
                "reviewConcern", reviewConcern(story), "evidenceRefs", story.evidenceRefs().stream().limit(3).toList()
            )).toList();
    }

    private static int reviewPriority(ChangeStory story) {
        int score = story.unknowns().size() * 4 + story.conflicts().size() * 5;
        if (GENERIC_PHRASES.stream().anyMatch(value -> story.humanTitle().contains(value))) score += 6;
        if (FIRST_LAYER_FORBIDDEN.stream().anyMatch(value -> (story.humanTitle() + story.oneSentenceSummary()).contains(value))) score += 8;
        return score;
    }

    private static String reviewConcern(ChangeStory story) {
        if (!story.conflicts().isEmpty()) return "仍有来源冲突，需要人工判断";
        if (!story.unknowns().isEmpty()) return "仍有未知信息，需要人工核对";
        if (GENERIC_PHRASES.stream().anyMatch(value -> story.humanTitle().contains(value))) return "标题仍接近通用模板";
        if (FIRST_LAYER_FORBIDDEN.stream().anyMatch(value -> (story.humanTitle() + story.oneSentenceSummary()).contains(value))) {
            return "第一层仍可能包含工程术语";
        }
        return "冻结为独立人工可读性复核样本";
    }

    private static Map<String, Object> safeDiagnostics(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : List.of(
            "modelStatus", "eventConservation", "invalidEvidenceRefCount", "crossProjectRefCount",
            "unsupportedStrongFactCount", "totalWindowCount", "succeededWindowCount", "failedWindowCount",
            "skippedWindowCount", "pendingWindowCount", "nextWindowOrdinal", "processedStoryCount",
            "unprocessedStoryCount", "modelWindowCacheHitCount", "chapterSynthesisCount",
            "chapterSynthesisProcessedCount", "chapterSynthesisCacheHitCount", "chapterSynthesisFailedCount",
            "chapterSynthesisPendingCount", "chapterSynthesisOmittedStoryCount",
            "modelRejectedInvalidEvidenceRefCount", "modelRejectedCrossProjectRefCount",
            "modelRejectedUnsupportedClaimCount", "modelValidationRepairCount",
            "modelValidationRepairFailureCount", "modelDeterministicTitleFallbackCount", "modelFallback",
            "chapterRepresentationPlanVersion", "chapterCount", "primaryStoryCount", "supportingStoryCount",
            "chapterPrimaryStoryCount", "chapterSupportingStoryCount", "representativeClusterCount", "dominantClusterCount",
            "selectedRepresentativeClusterCount", "representativePrimaryCoverage", "largestChapterStoryCount",
            "medianChapterStoryCount", "largeChapterCount", "chaptersNeedingSplit",
            "chaptersUsingDeterministicFallback", "chaptersUsingModelValidatedWording",
            "chaptersWithMinorClusterTitleRisk", "technicalLeakCount", "unsupportedClaimCount",
            "chapterOverlapCount", "orphanSupportingCount", "reasonWithoutEvidenceCount",
            "userDeclaredChapterMutationCount"
        )) {
            if (source != null && source.containsKey(key)) result.put(key, source.get(key));
        }
        return result;
    }

    private static String safeProviderFailure(Throwable failure) {
        Throwable current = failure;
        for (int depth = 0; current != null && depth < 6; depth++, current = current.getCause()) {
            if (current instanceof ModelGatewayService.ModelHttpException http) {
                return "code=HTTP_" + http.statusCode() + " requestCount=" + http.requestCount();
            }
            if (current instanceof ModelGatewayService.ModelTransportException transport) {
                return "code=TRANSPORT requestCount=" + transport.requestCount();
            }
            if (current instanceof ModelGatewayService.ModelResponseFormatException format) {
                ModelGatewayService.ModelCallDiagnostics diagnostics = format.diagnostics();
                String code = diagnostics == null ? "FORMAT_UNKNOWN" : safeFailureCode(diagnostics.failureCode());
                int requestCount = diagnostics == null ? 0 : diagnostics.requestCount();
                return "code=" + code + " requestCount=" + requestCount;
            }
            if (current instanceof CancellationException) {
                return "code=CANCELLED requestCount=0";
            }
        }
        return "code=OTHER requestCount=0";
    }

    private static ModelGatewayService.ModelCallDiagnostics failureDiagnostics(Throwable failure) {
        Throwable current = failure;
        for (int depth = 0; current != null && depth < 8; depth++, current = current.getCause()) {
            if (current instanceof ModelGatewayService.ModelResponseFormatException format
                && format.diagnostics() != null) return format.diagnostics();
        }
        return null;
    }

    private static int failedRequestCount(Throwable failure) {
        Throwable current = failure;
        for (int depth = 0; current != null && depth < 8; depth++, current = current.getCause()) {
            if (current instanceof ModelGatewayService.ModelHttpException http) return http.requestCount();
            if (current instanceof ModelGatewayService.ModelTransportException transport) return transport.requestCount();
            if (current instanceof ModelGatewayService.ModelResponseFormatException format
                && format.diagnostics() != null) return format.diagnostics().requestCount();
            if (current instanceof CancellationException) return 0;
        }
        return 0;
    }

    private static String safeFailureCode(String value) {
        if (value == null || !value.matches("[A-Z0-9_]{1,64}")) return "FORMAT_UNKNOWN";
        return value;
    }

    private QualificationSummary qualification(List<SafeScenarioRun> runs, String scenarioScope) {
        int requests = runs.stream().mapToInt(SafeScenarioRun::physicalRequestCount).sum();
        long tokens = runs.stream().mapToLong(SafeScenarioRun::tokenCount).sum();
        long latency = runs.stream().mapToLong(SafeScenarioRun::modelLatencyMs).sum();
        boolean allPassed = !runs.isEmpty() && runs.stream().allMatch(run -> "PASS".equals(run.status()));
        boolean usedStoryStage = runs.stream().mapToInt(SafeScenarioRun::storyModelCallCount).sum() > 0;
        boolean requiredStagesUsed = "correction".equals(scenarioScope) ? usedStoryStage
            : usedStoryStage && runs.stream().mapToInt(SafeScenarioRun::chapterModelCallCount).sum() > 0;
        int validationRepairs = runs.stream().mapToInt(SafeScenarioRun::validationRepairCount).sum();
        int deterministicTitleFallbacks = runs.stream()
            .mapToInt(run -> number(run.metrics(), "modelDeterministicTitleFallbackCount")).sum();
        return new QualificationSummary(allPassed && requests > 0 && requiredStagesUsed, requests, tokens, latency,
            runs.size(), (int) runs.stream().filter(run -> "PASS".equals(run.status())).count(),
            deterministicTitleFallbacks, validationRepairs);
    }

    private String scenarioScope() {
        String value = System.getProperty("projectflow.eval.scenario-scope", "full").trim().toLowerCase(Locale.ROOT);
        require(Set.of("full", "correction", "chapter", "continuity").contains(value),
            "不支持的真实场景范围");
        return value;
    }

    private void writeArtifact(
        ProjectFlowRealModelEvalIT.ProviderConfig config,
        List<SafeScenarioRun> runs,
        QualificationSummary summary,
        String scenarioScope
    ) throws Exception {
        String defaultName = "v385-real-scenarios-" + config.protocol().name().toLowerCase(Locale.ROOT);
        String outputName = System.getProperty("projectflow.eval.output-name", defaultName)
            .replaceAll("[^A-Za-z0-9._-]", "_");
        Path output = Path.of("target", "projectflow-eval", outputName);
        Files.createDirectories(output);
        Map<String, Object> artifact = new LinkedHashMap<>();
        artifact.put("version", "projectflow-v3.8.5-real-scenario-qualification-v4");
        artifact.put("generatedAt", Instant.now().toString());
        artifact.put("scenarioScope", scenarioScope);
        artifact.put("acceptanceTarget", "continuity".equals(scenarioScope)
            ? "V3.9_PROJECT_CONTINUITY" : "V3.8.5_HISTORY_QUALITY_REGRESSION");
        artifact.put("provider", Map.of(
            "name", config.name(), "model", config.model(), "protocol", config.protocol().name(),
            "reasoningEffort", config.reasoningEffort()
        ));
        artifact.put("qualification", summary);
        artifact.put("scenarios", runs);
        artifact.put("security", Map.of(
            "apiKeyPersistedInArtifact", false,
            "promptPersisted", false,
            "rawResponsePersisted", false,
            "reasoningPersisted", false,
            "absolutePathPersisted", false,
            "faultInjection", "after real Provider call, in memory only"
        ));
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(
            output.resolve("history-real-scenarios.json").toFile(), artifact
        );
    }

    private String safeFailure(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) message = throwable.getClass().getSimpleName();
        String safe = redactor.redactOutboundText(message)
            .replaceAll("(?i)[A-Z]:[/\\\\][^\\s,;]+", "[local-path]")
            .replaceAll("(?i)/(home|Users)/[^\\s,;]+", "[local-path]")
            .replaceAll("(?i)(sk-|ark-)[A-Za-z0-9_-]{8,}", "[REDACTED]");
        return safe.length() <= 500 ? safe : safe.substring(0, 500);
    }

    private static boolean chapterIncomplete(Map<String, Object> diagnostics) {
        return number(diagnostics, "chapterSynthesisPendingCount") > 0
            || number(diagnostics, "chapterSynthesisFailedCount") > 0;
    }

    private static boolean incomplete(Map<String, Object> diagnostics) {
        return number(diagnostics, "pendingWindowCount") > 0
            || number(diagnostics, "failedWindowCount") > 0
            || chapterIncomplete(diagnostics);
    }

    private static String failedProgress(Map<String, Object> diagnostics) {
        int failedWindows = number(diagnostics, "failedWindowCount");
        int failedChapters = number(diagnostics, "chapterSynthesisFailedCount");
        if (failedWindows + failedChapters == 0) return "";
        return String.join(":",
            Integer.toString(number(diagnostics, "succeededWindowCount")),
            Integer.toString(failedWindows),
            Integer.toString(number(diagnostics, "pendingWindowCount")),
            Integer.toString(number(diagnostics, "chapterSynthesisProcessedCount")),
            Integer.toString(failedChapters),
            Integer.toString(number(diagnostics, "chapterSynthesisPendingCount"))
        );
    }

    private static int number(Map<String, Object> values, String key) {
        Object value = values == null ? null : values.get(key);
        if (value instanceof Number number) return number.intValue();
        if (value == null) return 0;
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static double decimal(Map<String, Object> values, String key) {
        Object value = values == null ? null : values.get(key);
        if (value instanceof Number number) return number.doubleValue();
        if (value == null) return 0.0;
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException ignored) {
            return 0.0;
        }
    }

    private static double rate(long numerator, long denominator) {
        return denominator <= 0 ? 0 : (double) numerator / denominator;
    }

    private static long elapsedMs(long started) {
        return Math.max(0, (System.nanoTime() - started) / 1_000_000L);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void write(Path root, String relative, String content) throws Exception {
        Path target = root.resolve(relative);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content, StandardCharsets.UTF_8);
    }

    private static Path sourceRepository() {
        Path candidate = Path.of("").toAbsolutePath().normalize();
        while (candidate != null) {
            if (Files.exists(candidate.resolve(".git"))) return candidate;
            candidate = candidate.getParent();
        }
        throw new AssertionError("Unable to locate the ProjectFlow checkout");
    }

    private static String git(Path root, String... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.add("-c");
        command.add("safe.directory=" + root.toAbsolutePath().normalize().toString().replace('\\', '/'));
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.waitFor() != 0) throw new AssertionError("Bounded Git metadata command failed");
        return output;
    }

    private enum FaultMode {
        NONE,
        HTTP_503_AFTER_REAL_CALL,
        SCHEMA_AFTER_REAL_CALL,
        CANCEL_AFTER_REAL_CALL,
        CHAPTER_SCHEMA_AFTER_REAL_CALL
    }

    @FunctionalInterface
    private interface CheckedScenario {
        ScenarioEvidence run() throws Exception;
    }

    private static final class CallAccumulator {
        private int storyLogicalCalls;
        private int chapterLogicalCalls;
        private int validationRepairCalls;
        private int physicalRequests;
        private long tokens;
        private long latencyMs;

        private void add(
            ModelTaskType task,
            ModelGatewayService.ModelCallDiagnostics diagnostics,
            boolean validationRepair
        ) {
            if (validationRepair) validationRepairCalls++;
            else if (task == ModelTaskType.PROJECT_HISTORY_SYNTHESIS) storyLogicalCalls++;
            else if (task == ModelTaskType.PROJECT_HISTORY_CHAPTER_SYNTHESIS) chapterLogicalCalls++;
            if (diagnostics == null) return;
            physicalRequests += Math.max(0, diagnostics.requestCount());
            tokens += Math.max(0, diagnostics.totalTokens());
            latencyMs += Math.max(0, diagnostics.latencyMs());
        }

        private void addFailure(
            ModelTaskType task,
            ModelGatewayService.ModelCallDiagnostics diagnostics,
            boolean validationRepair,
            int requestCount
        ) {
            if (validationRepair) validationRepairCalls++;
            else if (task == ModelTaskType.PROJECT_HISTORY_SYNTHESIS) storyLogicalCalls++;
            else if (task == ModelTaskType.PROJECT_HISTORY_CHAPTER_SYNTHESIS) chapterLogicalCalls++;
            if (diagnostics != null) {
                physicalRequests += Math.max(0, diagnostics.requestCount());
                tokens += Math.max(0, diagnostics.totalTokens());
                latencyMs += Math.max(0, diagnostics.latencyMs());
            } else {
                physicalRequests += Math.max(0, requestCount);
            }
        }
    }

    private record ScenarioEvidence(
        Map<String, Object> metrics,
        List<Map<String, Object>> samples,
        List<String> failures
    ) {
        private ScenarioEvidence(Map<String, Object> metrics, List<Map<String, Object>> samples) {
            this(metrics, samples, List.of());
        }

        private ScenarioEvidence {
            metrics = metrics == null ? Map.of() : Map.copyOf(metrics);
            samples = samples == null ? List.of() : List.copyOf(samples);
            failures = failures == null ? List.of() : List.copyOf(failures);
        }

        private static ScenarioEvidence empty() {
            return new ScenarioEvidence(Map.of(), List.of(), List.of());
        }
    }

    private record SafeScenarioRun(
        String name,
        String status,
        String failure,
        int storyModelCallCount,
        int chapterModelCallCount,
        int validationRepairCount,
        int physicalRequestCount,
        long tokenCount,
        long modelLatencyMs,
        long elapsedMs,
        Map<String, Object> metrics,
        List<Map<String, Object>> samples
    ) {
    }

    private record QualificationSummary(
        boolean qualified,
        int physicalRequestCount,
        long tokenCount,
        long modelLatencyMs,
        int scenarioCount,
        int passedScenarioCount,
        int deterministicTitleFallbackCount,
        int validationRepairCount
    ) {
    }

    private record ContinuationState(
        UUID projectId,
        ProjectHistoryReconstructionService service,
        String storyId
    ) {
    }
}
