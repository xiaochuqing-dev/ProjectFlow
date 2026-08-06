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
import com.projectflow.service.ProjectHistoryCorrectionService;
import com.projectflow.service.ProjectHistoryReadService;
import com.projectflow.service.ProjectHistoryReconstructionService;
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

    @Autowired ProjectRepository projectRepository;
    @Autowired ProjectMemoryRepository memoryRepository;
    @Autowired ProjectFactRepository factRepository;
    @Autowired ProjectHistorySnapshotRepository snapshotRepository;
    @Autowired ProjectHistoryWindowCheckpointRepository checkpointRepository;
    @Autowired AiProviderRepository providerRepository;
    @Autowired ProjectHistoryCorrectionService correctionService;
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
            ModelGatewayService.StructuredModelResponse actual =
                (ModelGatewayService.StructuredModelResponse) invocation.callRealMethod();
            calls.computeIfAbsent(scenario, ignored -> new CallAccumulator()).add(task, actual.diagnostics());
            if (task != ModelTaskType.PROJECT_HISTORY_SYNTHESIS) return actual;
            int ordinal = storyOrdinal.incrementAndGet();
            if (faultTriggered.get() || ordinal != 2) return actual;
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
        runs.add(runScenario("prompt-overflow-split", FaultMode.NONE,
            () -> promptOverflow(userId)));
        runs.add(runScenario("projectflow-current-history-dogfood", FaultMode.NONE,
            () -> dogfood(userId)));

        QualificationSummary summary = qualification(runs);
        writeArtifact(config, runs, summary);
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
            name, status, failure, call.storyLogicalCalls, call.chapterLogicalCalls, call.physicalRequests,
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
        require(call != null && call.storyLogicalCalls > 0, fixtureName + " 未执行真实 Story 模型窗口");

        Map<String, Object> metrics = safeDiagnostics(overview.diagnostics());
        metrics.put("storyCount", stories.size());
        metrics.put("primaryCount", stories.stream().filter(story -> !story.supporting()).count());
        metrics.put("supportingCount", stories.stream().filter(ChangeStory::supporting).count());
        metrics.put("currentness", overview.coverage().currentness());
        metrics.put("firstLayerTechnicalLeakRate", rate(firstLayerLeaks(stories, FIRST_LAYER_FORBIDDEN), stories.size()));
        return new ScenarioEvidence(metrics, storySamples(stories, 4));
    }

    private ScenarioEvidence continuationAndChapter(UUID userId) throws Exception {
        Path root = temporaryRoot.resolve("seventeen-window-continuation");
        Files.createDirectories(root);
        ProjectSpace project = project(userId, "Seventeen window continuation", root);
        historicalFacts(project, 0, 17 * 32, 1, 1, 0);

        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);
        Map<String, Object> first = readService.overview(userId, project.getId()).diagnostics();
        require(number(first, "totalWindowCount") == 17, "首次规划不是 17 个窗口");
        require(number(first, "succeededWindowCount") == 16, "首次没有严格完成前 16 个窗口");
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
        require(stories.size() == 17 * 32, "17 个窗口的 Story 出现丢失");
        require(validRoleGraph(stories), "17 个窗口完成后的角色图不合法");
        continuationState = new ContinuationState(project.getId(), restarted, stories.get(0).id());
        Map<String, Object> metrics = safeDiagnostics(afterRestart);
        metrics.put("storyCount", stories.size());
        metrics.put("restartServiceInstance", true);
        metrics.put("finalCacheHit", true);
        return new ScenarioEvidence(metrics, storySamples(stories, 5));
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
        ), storySamples(List.of(corrected), 1));
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
        return new ScenarioEvidence(safeDiagnostics(recovered), storySamples(allStories(userId, project.getId()), 4));
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
        return new ScenarioEvidence(metrics, storySamples(allStories(userId, project.getId()), 4));
    }

    private ScenarioEvidence promptOverflow(UUID userId) throws Exception {
        Path root = temporaryRoot.resolve("prompt-overflow-real");
        Files.createDirectories(root);
        ProjectSpace project = project(userId, "Prompt overflow real Provider", root);
        historicalFacts(project, 0, 32, 8, 12, 120);
        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);
        Map<String, Object> diagnostics = readService.overview(userId, project.getId()).diagnostics();
        int total = number(diagnostics, "totalWindowCount");
        require(total > 1 && total <= 16, "Prompt overflow 没有确定性拆成有界子窗口");
        require(number(diagnostics, "succeededWindowCount") == total, "Prompt overflow 子窗口未全部完成");
        require(number(diagnostics, "skippedWindowCount") == 0, "Prompt overflow 产生永久 SKIPPED");
        require(number(diagnostics, "pendingWindowCount") == 0, "Prompt overflow 仍有 pending 子窗口");
        require(calls.get(activeScenario.get()).storyLogicalCalls == total, "Prompt overflow 子窗口调用数不一致");
        var cached = reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);
        require(cached.cacheHit(), "Prompt overflow 子窗口成功后未命中 cache");
        return new ScenarioEvidence(safeDiagnostics(diagnostics), storySamples(allStories(userId, project.getId()), 5));
    }

    private ScenarioEvidence dogfood(UUID userId) throws Exception {
        Path source = sourceRepository();
        int commitCount = Integer.parseInt(git(source, "rev-list", "--count", "HEAD").trim());
        require(commitCount > 197, "当前 ProjectFlow checkout 未包含 V3.8.5 完整历史");
        ProjectSpace project = project(userId, "ProjectFlow V3.8.5 Dogfood", source);
        Map<String, Object> diagnostics = Map.of();
        int guard = 0;
        do {
            reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);
            diagnostics = readService.overview(userId, project.getId()).diagnostics();
        } while (incomplete(diagnostics) && ++guard < 24);
        require(!incomplete(diagnostics), "ProjectFlow Dogfood 在 24 次有界刷新内未完成");
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

        List<Map<String, Object>> samples = new ArrayList<>();
        samples.add(Map.of("sampleType", "primary", "items", storySamples(primary, 15)));
        samples.add(Map.of("sampleType", "explicit-supporting", "items", storySamples(supporting, 10)));
        samples.add(Map.of("sampleType", "supporting-consolidation", "items", supportingConsolidation));
        samples.add(Map.of("sampleType", "chapters", "items", chapterSamples(chapters, 8)));
        samples.add(Map.of("sampleType", "threads", "items", threadSamples(threads, 3)));
        samples.add(Map.of("sampleType", "manual-review-candidates", "items", reviewCandidates(stories, 5)));
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
        metrics.put("genericTemplateRate", genericRate);
        metrics.put("firstLayerTechnicalLeakRate", technicalLeakRate);
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
        return new ScenarioEvidence(metrics, samples, failures);
    }

    private ProjectSpace threeWindowProject(UUID userId, String name) throws Exception {
        Path root = temporaryRoot.resolve(name);
        Files.createDirectories(root);
        ProjectSpace project = project(userId, name, root);
        historicalFacts(project, 0, 3 * 32, 1, 1, 0);
        return project;
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
                paths.add("results/Outcome" + String.format("%05d", index) + "Part"
                    + String.format("%03d", pathIndex) + (payloadWidth <= 0 ? "" : "-" + "p".repeat(payloadWidth)) + ".md");
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
            if (forbidden.stream().anyMatch(firstLayer::contains)) count++;
        }
        return count;
    }

    private static int genericCount(List<ChangeStory> stories) {
        return (int) stories.stream().filter(story -> GENERIC_PHRASES.stream()
            .anyMatch(value -> (story.humanTitle() + " " + story.oneSentenceSummary()).contains(value))).count();
    }

    private static List<Map<String, Object>> storySamples(List<ChangeStory> stories, int limit) {
        return stories.stream().limit(limit).map(story -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("id", story.id());
            value.put("title", story.humanTitle());
            value.put("summary", story.oneSentenceSummary());
            value.put("before", story.beforeState());
            value.put("change", story.change());
            value.put("after", story.afterState());
            value.put("role", story.role());
            value.put("primaryStoryId", story.primaryStoryId());
            value.put("supportingChangeRefs", story.supportingChangeRefs());
            value.put("reasonEvidenceRefs", story.reasonEvidenceRefs());
            value.put("evidenceRefs", story.evidenceRefs().stream().limit(5).toList());
            value.put("unknowns", story.unknowns());
            value.put("conflicts", story.conflicts());
            return Map.copyOf(value);
        }).toList();
    }

    private static List<Map<String, Object>> chapterSamples(List<HistoryChapter> chapters, int limit) {
        return chapters.stream().limit(limit).map(chapter -> Map.<String, Object>of(
            "id", chapter.id(), "title", chapter.title(), "summary", chapter.summary(),
            "storyCount", chapter.storyCount(), "rawEventCount", chapter.rawEventCount()
        )).toList();
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
            "chapterSynthesisPendingCount", "chapterSynthesisOmittedStoryCount"
        )) {
            if (source != null && source.containsKey(key)) result.put(key, source.get(key));
        }
        return result;
    }

    private QualificationSummary qualification(List<SafeScenarioRun> runs) {
        int requests = runs.stream().mapToInt(SafeScenarioRun::physicalRequestCount).sum();
        long tokens = runs.stream().mapToLong(SafeScenarioRun::tokenCount).sum();
        long latency = runs.stream().mapToLong(SafeScenarioRun::modelLatencyMs).sum();
        boolean allPassed = !runs.isEmpty() && runs.stream().allMatch(run -> "PASS".equals(run.status()));
        boolean usedBothStages = runs.stream().mapToInt(SafeScenarioRun::storyModelCallCount).sum() > 0
            && runs.stream().mapToInt(SafeScenarioRun::chapterModelCallCount).sum() > 0;
        return new QualificationSummary(allPassed && requests > 0 && usedBothStages, requests, tokens, latency,
            runs.size(), (int) runs.stream().filter(run -> "PASS".equals(run.status())).count());
    }

    private void writeArtifact(
        ProjectFlowRealModelEvalIT.ProviderConfig config,
        List<SafeScenarioRun> runs,
        QualificationSummary summary
    ) throws Exception {
        String defaultName = "v385-real-scenarios-" + config.protocol().name().toLowerCase(Locale.ROOT);
        String outputName = System.getProperty("projectflow.eval.output-name", defaultName)
            .replaceAll("[^A-Za-z0-9._-]", "_");
        Path output = Path.of("target", "projectflow-eval", outputName);
        Files.createDirectories(output);
        Map<String, Object> artifact = new LinkedHashMap<>();
        artifact.put("version", "projectflow-v3.8.5-real-scenario-qualification-v1");
        artifact.put("generatedAt", Instant.now().toString());
        artifact.put("provider", Map.of(
            "name", config.name(), "model", config.model(), "protocol", config.protocol().name()
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
        SCHEMA_AFTER_REAL_CALL,
        CANCEL_AFTER_REAL_CALL
    }

    @FunctionalInterface
    private interface CheckedScenario {
        ScenarioEvidence run() throws Exception;
    }

    private static final class CallAccumulator {
        private int storyLogicalCalls;
        private int chapterLogicalCalls;
        private int physicalRequests;
        private long tokens;
        private long latencyMs;

        private void add(ModelTaskType task, ModelGatewayService.ModelCallDiagnostics diagnostics) {
            if (task == ModelTaskType.PROJECT_HISTORY_SYNTHESIS) storyLogicalCalls++;
            if (task == ModelTaskType.PROJECT_HISTORY_CHAPTER_SYNTHESIS) chapterLogicalCalls++;
            if (diagnostics == null) return;
            physicalRequests += Math.max(0, diagnostics.requestCount());
            tokens += Math.max(0, diagnostics.totalTokens());
            latencyMs += Math.max(0, diagnostics.latencyMs());
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
        int passedScenarioCount
    ) {
    }

    private record ContinuationState(
        UUID projectId,
        ProjectHistoryReconstructionService service,
        String storyId
    ) {
    }
}
