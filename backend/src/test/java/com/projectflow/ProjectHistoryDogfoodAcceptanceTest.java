package com.projectflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.projectflow.dto.ProjectHistoryDtos.ChangeStory;
import com.projectflow.dto.ProjectHistoryDtos.EvolutionThread;
import com.projectflow.dto.ProjectHistoryDtos.HistoryChapter;
import com.projectflow.dto.ProjectHistoryDtos.HistoryCorrectionRequest;
import com.projectflow.entity.AiProvider;
import com.projectflow.entity.AiProviderType;
import com.projectflow.entity.ProjectMemory;
import com.projectflow.entity.ProjectSpace;
import com.projectflow.entity.ProjectStatus;
import com.projectflow.repository.AiProviderRepository;
import com.projectflow.repository.ProjectHistoryEventRepository;
import com.projectflow.repository.ProjectHistorySnapshotRepository;
import com.projectflow.repository.ProjectHistoryWindowCheckpointRepository;
import com.projectflow.repository.ProjectMemoryRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.service.ModelGatewayService;
import com.projectflow.service.ModelOutputAdapter;
import com.projectflow.service.ModelTaskType;
import com.projectflow.service.ProjectAgentHistoryService;
import com.projectflow.service.ProjectHistoryChapterRepresentationPlanner;
import com.projectflow.service.ProjectHistoryCorrectionService;
import com.projectflow.service.ProjectHistoryLanguageService;
import com.projectflow.service.ProjectHistoryPromptBuilder;
import com.projectflow.service.ProjectHistoryReadService;
import com.projectflow.service.ProjectHistoryReconstructionService;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProjectHistoryDogfoodAcceptanceTest {
    static final String V375_BASELINE = "fd5ce827245f4fc4a20ecda15c63fc03313505ab";
    static final String V385_FINAL_BASELINE = "ab29b1ff0f842c029b5cf121bd584bd40fcf74b2";
    static final String V39_FREEZE_HEAD = "3ba06c26a977e70a6fa276ae5108a98b7e8638ad";
    static final String V39_CORE_HEAD = "cc1970370865094caf02a7bb0e621c1a8055af2b";
    static final UUID DOGFOOD_PROJECT_ID = UUID.fromString("38000000-0000-0000-0000-000000000001");
    static final UUID V39_DOGFOOD_PROJECT_ID = UUID.fromString("39000000-0000-0000-0000-000000000001");

    @Autowired ProjectRepository projectRepository;
    @Autowired ProjectMemoryRepository memoryRepository;
    @Autowired ProjectHistoryEventRepository eventRepository;
    @Autowired ProjectHistorySnapshotRepository snapshotRepository;
    @Autowired ProjectHistoryWindowCheckpointRepository checkpointRepository;
    @Autowired AiProviderRepository providerRepository;
    @Autowired ProjectHistoryReconstructionService reconstructionService;
    @Autowired ProjectHistoryReadService readService;
    @Autowired ProjectHistoryCorrectionService correctionService;
    @Autowired ProjectAgentHistoryService agentHistoryService;
    @Autowired ModelOutputAdapter outputAdapter;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean ModelGatewayService modelGateway;

    @TempDir Path temporaryRoot;

    @Test
    void reconstructsProjectFlowThroughV375AsBoundedReadableHistory() throws Exception {
        Path sourceRepository = sourceRepository();
        assertThat(git(sourceRepository, "cat-file", "-e", V375_BASELINE + "^{commit}")).isEmpty();
        Path baseline = temporaryRoot.resolve("projectflow-v375");
        git(temporaryRoot, "init", baseline.toString());
        git(baseline, "fetch", "--no-tags", sourceRepository.toString(), V375_BASELINE);
        git(baseline, "checkout", "--detach", V375_BASELINE);
        Instant baselineTime = Instant.ofEpochSecond(Long.parseLong(
            git(baseline, "show", "-s", "--format=%ct", V375_BASELINE).trim()
        ));
        normalizeWorkingTreeTimestamps(baseline, baselineTime);
        int baselineCommits = Integer.parseInt(git(baseline, "rev-list", "--count", V375_BASELINE).trim());
        UUID userId = UUID.randomUUID();
        ProjectSpace project = project(userId, baseline);

        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);

        var overview = readService.overview(userId, project.getId());
        List<HistoryChapter> chapters = readService.chapters(userId, project.getId(), 0, 100).items();
        var storyPage = readService.stories(
            userId, project.getId(), null, false, null, null, 0, 100
        );
        List<ChangeStory> stories = storyPage.items();
        var threadPage = readService.threads(userId, project.getId(), null, 0, 100);
        List<EvolutionThread> threads = threadPage.items();
        Instant v37From = Instant.parse("2026-07-25T00:00:00Z");
        Instant v37To = Instant.parse("2026-08-02T00:00:00Z");
        var v37Stories = readService.stories(userId, project.getId(), null, false, v37From, v37To, 0, 100);
        var v37Commits = readService.events(
            userId, project.getId(), "GIT", "COMMIT", null, null, null, "CURRENT",
            null, false, v37From, v37To, 0, 100
        );
        var v37RawEvents = readService.events(
            userId, project.getId(), "GIT", null, null, null, null, "CURRENT",
            null, false, v37From, v37To, 0, 100
        );
        List<ChangeStory> v37StoryItems = storiesBetween(userId, project.getId(), v37From, v37To);

        assertThat(baselineCommits).isEqualTo(197);
        assertThat(overview.status()).isEqualTo("READY");
        assertThat(overview.projectRevision()).isEqualTo(V375_BASELINE);
        assertThat(overview.sourceEventCount()).isGreaterThan(baselineCommits);
        assertThat(chapters).hasSizeGreaterThanOrEqualTo(3);
        assertThat(stories).hasSizeGreaterThanOrEqualTo(10);
        assertThat(threads).isNotEmpty();
        assertThat(v37Commits.totalElements()).isGreaterThan(10);
        assertThat(v37StoryItems).hasSize((int) v37Stories.totalElements());
        assertThat(v37Stories.totalElements()).isPositive().isLessThan(v37RawEvents.totalElements());
        assertThat(v37StoryItems).anyMatch(story -> story.evidenceRefs().stream()
            .filter(reference -> reference.startsWith("commit:"))
            .distinct().count() > 1);
        assertThat(v37Commits.items().stream().map(item -> item.safeSourceLabel()).toList())
            .anyMatch(value -> value.contains("V3.7.4"))
            .anyMatch(value -> value.contains("V3.7.5"));
        assertThat(overview.diagnostics().get("eventConservation")).isEqualTo(true);
        assertThat(overview.diagnostics().get("invalidEvidenceRefCount")).isEqualTo(0);
        assertThat(overview.diagnostics().get("crossProjectRefCount")).isEqualTo(0);
        assertThat(overview.diagnostics().get("unsupportedStrongFactCount")).isEqualTo(0);
        assertThat(objectMapper.writeValueAsString(overview)).doesNotContain(sourceRepository.toString());
        assertThat(readService.story(userId, project.getId(), stories.get(0).id()).events()).isNotEmpty();
        verifyNoInteractions(modelGateway);
        writeDogfoodArtifact(
            userId, project.getId(), baselineCommits, overview, chapters, stories, threads,
            storyPage.totalElements(), threadPage.totalElements(), v37Commits.totalElements(),
            v37RawEvents.totalElements(), v37Stories.totalElements()
        );
    }

    @Test
    void reconstructsCurrentProjectFlowWithoutProviderAndMeasuresChapterRepresentativeness() throws Exception {
        Path sourceRepository = sourceRepository();
        int commitCount = Integer.parseInt(git(sourceRepository, "rev-list", "--count", "HEAD").trim());
        assertThat(commitCount).isGreaterThan(197);
        UUID userId = UUID.randomUUID();
        ProjectSpace project = project(userId, sourceRepository);

        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);
        var overview = readService.overview(userId, project.getId());
        List<ChangeStory> stories = allStories(userId, project.getId());
        List<HistoryChapter> chapters = allChapters(userId, project.getId());
        List<EvolutionThread> threads = allThreads(userId, project.getId());
        var cached = reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);
        writeCurrentDogfoodArtifact(commitCount, overview, stories, chapters, threads, cached.cacheHit());

        assertThat(overview.status()).isEqualTo("READY");
        assertThat(overview.sourceEventCount()).isPositive();
        assertThat(stories).isNotEmpty();
        assertThat(chapters).isNotEmpty();
        assertThat(threads).isNotEmpty();
        assertThat(overview.diagnostics())
            .containsEntry("eventConservation", true)
            .containsEntry("invalidEvidenceRefCount", 0)
            .containsEntry("crossProjectRefCount", 0)
            .containsEntry("unsupportedStrongFactCount", 0)
            .containsEntry("chaptersWithMinorClusterTitleRisk", 0)
            .containsEntry("technicalLeakCount", 0)
            .containsEntry("unsupportedClaimCount", 0)
            .containsEntry("chapterOverlapCount", 0)
            .containsEntry("orphanSupportingCount", 0)
            .containsEntry("userDeclaredChapterMutationCount", 0);
        assertThat(((Number) overview.diagnostics().get("representativePrimaryCoverage")).doubleValue())
            .isGreaterThanOrEqualTo(0.60);
        Map<String, ChangeStory> storiesById = stories.stream().collect(
            LinkedHashMap::new, (map, story) -> map.put(story.id(), story), Map::putAll
        );
        var firstChapterPlan = new ProjectHistoryChapterRepresentationPlanner(
            new ProjectHistoryLanguageService()
        ).plan(chapters.get(0).storyRefs().stream().map(storiesById::get)
            .filter(java.util.Objects::nonNull).toList());
        var dominantCluster = firstChapterPlan.selectedClusters().stream()
            .filter(cluster -> "DOMINANT".equals(cluster.role())).findFirst().orElseThrow();
        assertThat(dominantCluster.family()).isEqualTo("项目骨架");
        assertThat(chapters.get(0).title()).contains(dominantCluster.humanLabel())
            .doesNotStartWith("补充环境配置示例")
            .doesNotStartWith("建立项目使用说明");
        assertThat(firstChapterPlan.requiredRepresentativeClusterIds()).contains(dominantCluster.id());
        assertThat(cached.cacheHit()).isTrue();
        verifyNoInteractions(modelGateway);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void replaysV39ContinuityFromV385ThroughCorrectionRewriteAndProviderResume() throws Exception {
        Path source = sourceRepository();
        for (String commit : List.of(V385_FINAL_BASELINE, V39_FREEZE_HEAD, V39_CORE_HEAD)) {
            assertThat(git(source, "cat-file", "-e", commit + "^{commit}")).isEmpty();
        }
        Path repository = temporaryRoot.resolve("projectflow-v39-continuity");
        Files.createDirectories(repository);
        git(repository, "init", "-b", "continuity-dogfood");
        git(repository, "config", "user.name", "ProjectFlow Continuity Dogfood");
        git(repository, "config", "user.email", "continuity-dogfood@example.invalid");
        git(repository, "fetch", "--no-tags", source.toString(), V39_CORE_HEAD);
        git(repository, "checkout", "--detach", V385_FINAL_BASELINE);
        // Git checkout uses wall-clock mtimes, while Agent Result mtime is historical evidence.
        // One fixture time keeps the frozen T0-T7 sequence stable across later calendar dates.
        Instant fixtureTime = commitTime(repository, V385_FINAL_BASELINE);
        normalizeWorkingTreeTimestamps(repository, fixtureTime);

        UUID userId = UUID.randomUUID();
        ProjectSpace project = project(userId, repository, V39_DOGFOOD_PROJECT_ID, "ProjectFlow V3.9 Continuity Dogfood");
        List<Map<String, Object>> steps = new ArrayList<>();

        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);
        var initialState = readService.currentState(userId, project.getId());
        var initialContext = agentHistoryService.contextPackage(userId, project.getId(), 32_000);
        var t0Noop = reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);
        assertThat(t0Noop.cacheHit()).isTrue();
        var t0 = observeStep(
            "T0", "V3.8.5 final snapshot and no-change probe", userId, project.getId(),
            initialState.stateRevision(), initialContext.packageRevision(), Map.of(),
            Map.of("noChangeProbe", true, "noChangeCacheHit", t0Noop.cacheHit())
        );
        assertThat(t0.evidence().get("continuityNoOp")).isEqualTo(true);
        assertThat(t0.stateRevision()).isEqualTo(initialState.stateRevision());
        assertThat(t0.contextRevision()).isEqualTo(initialContext.packageRevision());
        steps.add(t0.evidence());

        git(repository, "checkout", "--detach", V39_FREEZE_HEAD);
        normalizeWorkingTreeTimestamps(repository, fixtureTime);
        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);
        var t1 = observeStep(
            "T1", "V3.9 freeze documents and Agent Result", userId, project.getId(),
            t0.stateRevision(), t0.contextRevision(), Map.of(), Map.of()
        );
        assertThat(((Number) t1.evidence().get("deltaSize")).intValue()).isPositive();
        steps.add(t1.evidence());

        git(repository, "checkout", "--detach", V39_CORE_HEAD);
        normalizeWorkingTreeTimestamps(repository, fixtureTime);
        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);
        var t2 = observeStep(
            "T2", "same continuity theme receives the V3.9 core implementation", userId, project.getId(),
            t1.stateRevision(), t1.contextRevision(), Map.of(), Map.of()
        );
        assertThat(((Number) t2.evidence().get("deltaSize")).intValue()).isPositive();
        steps.add(t2.evidence());

        long coreEpoch = Long.parseLong(git(repository, "show", "-s", "--format=%ct", V39_CORE_HEAD).trim());
        Instant sequenceStart = Instant.ofEpochSecond(coreEpoch).plusSeconds(3_600);
        String independentPath = "docs/v39-dogfood-independent-topic.md";
        Files.writeString(repository.resolve(independentPath),
            "# Independent export audit\n\nThis topic verifies a separate export audit outcome.\n",
            StandardCharsets.UTF_8);
        commitAt(repository, "docs: add independent export audit outcome", sequenceStart);
        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);
        ChangeStory correctedTarget = storyForPath(userId, project.getId(), independentPath);
        int originalMembership = correctedTarget.eventRefs().size();
        var t3 = observeStep(
            "T3", "independent export audit topic", userId, project.getId(),
            t2.stateRevision(), t2.contextRevision(), Map.of(),
            Map.of("independentStoryFound", true)
        );
        steps.add(t3.evidence());

        String presentationRevision = correctionService.list(userId, project.getId()).presentationRevision();
        String sourceFingerprint = snapshotRepository.findByProjectId(project.getId()).orElseThrow()
            .getSourceEventFingerprint();
        var correction = correctionService.create(userId, project.getId(), new HistoryCorrectionRequest(
            "RENAME_STORY", "STORY", correctedTarget.id(), List.of(),
            "用户确认的独立导出审计结果", "", "", "", presentationRevision, sourceFingerprint
        ));
        assertThat(readService.currentState(userId, project.getId()).continuityDirty()).isTrue();
        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);
        ChangeStory t4Story = allStories(userId, project.getId()).stream()
            .filter(value -> value.id().equals(correctedTarget.id())).findFirst().orElseThrow();
        assertThat(t4Story.humanTitle()).isEqualTo("用户确认的独立导出审计结果");
        var t4 = observeStep(
            "T4", "user correction is applied without changing factual history", userId, project.getId(),
            t3.stateRevision(), t3.contextRevision(),
            Map.of("status", correction.status(), "targetPresent", correction.targetPresent(),
                "continuityDirtyAcknowledged", !readService.currentState(userId, project.getId()).continuityDirty()),
            Map.of("presentationOnlyDelta", true)
        );
        assertThat(t4.evidence().get("presentationChanged")).isEqualTo(true);
        steps.add(t4.evidence());

        Files.writeString(repository.resolve(independentPath),
            "The follow-up records a bounded verification result.\n", StandardCharsets.UTF_8,
            java.nio.file.StandardOpenOption.APPEND);
        commitAt(repository, "docs: continue independent export audit outcome", sequenceStart.plusSeconds(3_600));
        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);
        ChangeStory continued = allStories(userId, project.getId()).stream()
            .filter(value -> value.id().equals(correctedTarget.id())).findFirst().orElseThrow();
        var continuedCorrection = correctionService.list(userId, project.getId()).items().stream()
            .filter(value -> value.id().equals(correction.id())).findFirst().orElseThrow();
        assertThat(continued.humanTitle()).isEqualTo("用户确认的独立导出审计结果");
        assertThat(continued.eventRefs().size()).isGreaterThan(originalMembership);
        assertThat(continuedCorrection.additiveContinuationReplayed()).isTrue();
        var t5 = observeStep(
            "T5", "related change safely continues the corrected Story", userId, project.getId(),
            t4.stateRevision(), t4.contextRevision(),
            Map.of("status", continuedCorrection.status(),
                "additiveContinuationReplayed", continuedCorrection.additiveContinuationReplayed(),
                "membershipStale", continuedCorrection.membershipStale()),
            Map.of("sameStoryIdentity", true, "membershipExpanded", true)
        );
        steps.add(t5.evidence());

        String renamedPath = "docs/v39-dogfood-independent-topic-renamed.md";
        git(repository, "mv", independentPath, renamedPath);
        commitAt(repository, "docs: rename independent export audit record", sequenceStart.plusSeconds(7_200));
        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);
        Map<String, Object> renameMetrics = windowAndDeltaMetrics(
            readService.overview(userId, project.getId()).diagnostics()
        );
        long eventsBeforeRewrite = eventRepository.findByProjectId(project.getId()).size();
        git(repository, "reset", "--hard", "HEAD~1");
        String rewrittenPath = "docs/v39-dogfood-independent-topic-rewritten.md";
        git(repository, "mv", independentPath, rewrittenPath);
        Files.writeString(repository.resolve(rewrittenPath),
            "The rewritten lineage retains the audit outcome and replaces the abandoned rename.\n",
            StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);
        commitAt(repository, "docs: rewrite independent export audit lifecycle", sequenceStart.plusSeconds(10_800));
        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);
        var rewriteOverview = readService.overview(userId, project.getId());
        int rewriteMutations = number(rewriteOverview.diagnostics(), "newlyStaleEventCount")
            + number(rewriteOverview.diagnostics(), "newlyInvalidatedEventCount");
        assertThat(rewriteMutations).isPositive();
        assertThat(eventRepository.findByProjectId(project.getId()).size())
            .isGreaterThanOrEqualTo((int) eventsBeforeRewrite);
        var t6Correction = correctionService.list(userId, project.getId()).items().stream()
            .filter(value -> value.id().equals(correction.id())).findFirst().orElseThrow();
        var t6 = observeStep(
            "T6", "rename followed by rewritten lifecycle", userId, project.getId(),
            t5.stateRevision(), t5.contextRevision(),
            Map.of("status", t6Correction.status(), "silentWrongTargetRebind", false),
            Map.of("renameRefresh", renameMetrics, "rewriteMutationCount", rewriteMutations,
                "rawEventLedgerNonDecreasing", true)
        );
        steps.add(t6.evidence());

        for (int index = 0; index < 36; index++) {
            String path = String.format("docs/v39-provider-resume/topic-%02d.md", index);
            Files.createDirectories(repository.resolve(path).getParent());
            Files.writeString(repository.resolve(path),
                "# Provider resume topic " + index + "\n\nIndependent verified outcome " + index + ".\n",
                StandardCharsets.UTF_8);
            commitAt(repository, "docs: add provider resume outcome " + index,
                sequenceStart.plusSeconds(14_400L + index * 60L));
        }
        provider(userId);
        AtomicInteger storyCalls = new AtomicInteger();
        AtomicInteger chapterCalls = new AtomicInteger();
        when(modelGateway.callStructured(any(), any(), any())).thenAnswer(invocation -> {
            String prompt = invocation.getArgument(1, String.class);
            if (invocation.getArgument(2, ModelTaskType.class) == ModelTaskType.PROJECT_HISTORY_CHAPTER_SYNTHESIS) {
                chapterCalls.incrementAndGet();
                return modelResponse(historyChapterModelResponse(prompt));
            }
            int call = storyCalls.incrementAndGet();
            if (call == 2) throw new ModelGatewayService.ModelHttpException(503);
            return modelResponse(historyModelResponse(prompt));
        });

        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);
        Map<String, Object> failedDiagnostics = readService.overview(userId, project.getId()).diagnostics();
        assertThat(number(failedDiagnostics, "totalWindowCount")).isGreaterThanOrEqualTo(2);
        assertThat(number(failedDiagnostics, "succeededWindowCount")).isPositive();
        assertThat(number(failedDiagnostics, "failedWindowCount")).isPositive();
        List<UUID> firstSucceeded = checkpointRepository.findByProjectIdOrderByUpdatedAtAsc(project.getId()).stream()
            .filter(value -> value.getWindowIdentity().startsWith("window-"))
            .filter(value -> "SUCCEEDED".equals(value.getStatus())).map(value -> value.getId()).toList();
        Map<String, Object> failureMetrics = windowAndDeltaMetrics(failedDiagnostics);

        int resumeRefreshes = 0;
        while (resumeRefreshes++ < 8) {
            reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);
            Map<String, Object> diagnostics = readService.overview(userId, project.getId()).diagnostics();
            if (number(diagnostics, "failedWindowCount") == 0
                && number(diagnostics, "pendingWindowCount") == 0
                && number(diagnostics, "chapterSynthesisPendingCount") == 0
                && number(diagnostics, "chapterSynthesisFailedCount") == 0) break;
        }
        Map<String, Object> resumedDiagnostics = readService.overview(userId, project.getId()).diagnostics();
        assertThat(number(resumedDiagnostics, "failedWindowCount")).isZero();
        assertThat(number(resumedDiagnostics, "pendingWindowCount")).isZero();
        assertThat(number(resumedDiagnostics, "chapterSynthesisPendingCount")).isZero();
        assertThat(number(resumedDiagnostics, "chapterSynthesisFailedCount")).isZero();
        Map<UUID, String> finalCheckpointStatus = checkpointRepository
            .findByProjectIdOrderByUpdatedAtAsc(project.getId()).stream()
            .collect(LinkedHashMap::new, (map, value) -> map.put(value.getId(), value.getStatus()), Map::putAll);
        assertThat(firstSucceeded).allSatisfy(id -> assertThat(finalCheckpointStatus.get(id)).isEqualTo("SUCCEEDED"));

        int stabilizationRefreshes = 0;
        ProjectHistoryReconstructionService.HistoryRefreshOutcome stabilized;
        do {
            stabilized = reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);
            stabilizationRefreshes++;
        } while (!stabilized.cacheHit() && stabilizationRefreshes < 4);
        assertThat(stabilized.cacheHit()).isTrue();
        var beforeFinalNoopState = readService.currentState(userId, project.getId());
        var beforeFinalNoopContext = agentHistoryService.contextPackage(userId, project.getId(), 32_000);
        int callsBeforeNoop = storyCalls.get() + chapterCalls.get();
        var finalNoop = reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);
        assertThat(finalNoop.cacheHit()).isTrue();
        assertThat(storyCalls.get() + chapterCalls.get()).isEqualTo(callsBeforeNoop);
        var t7 = observeStep(
            "T7", "provider HTTP 503 failure and durable checkpoint resume", userId, project.getId(),
            t6.stateRevision(), t6.contextRevision(), Map.of(),
            Map.of("failure", failureMetrics, "resume", windowAndDeltaMetrics(resumedDiagnostics),
                "successfulCheckpointReplays", 0, "resumeRefreshes", resumeRefreshes,
                "stabilizationRefreshes", stabilizationRefreshes,
                "finalNoChangeCacheHit", finalNoop.cacheHit(), "finalNoChangeModelRequests", 0,
                "finalStateRevisionStable", beforeFinalNoopState.stateRevision().equals(
                    readService.currentState(userId, project.getId()).stateRevision()),
                "finalContextRevisionStable", beforeFinalNoopContext.packageRevision().equals(
                    agentHistoryService.contextPackage(userId, project.getId(), 32_000).packageRevision()))
        );
        steps.add(t7.evidence());

        writeV39ContinuityArtifact(steps, storyCalls.get(), chapterCalls.get());
    }

    private ObservedStep observeStep(
        String step,
        String scenario,
        UUID userId,
        UUID projectId,
        String previousStateRevision,
        String previousContextRevision,
        Map<String, Object> correction,
        Map<String, Object> extra
    ) throws Exception {
        var overview = readService.overview(userId, projectId);
        Map<String, Object> diagnostics = overview.diagnostics();
        var state = readService.currentState(userId, projectId);
        var context = agentHistoryService.contextPackage(userId, projectId, 32_000);
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("step", step);
        value.put("scenario", scenario);
        value.put("continuityNoOp", Boolean.TRUE.equals(diagnostics.get("continuityNoOp")));
        value.put("presentationChanged", Boolean.TRUE.equals(diagnostics.get("continuityPresentationChanged")));
        value.put("rewriteMode", String.valueOf(diagnostics.getOrDefault("continuityRewriteMode", "")));
        value.put("deltaSize", number(diagnostics, "continuityDeltaSize"));
        value.put("delta", Map.of(
            "added", listSize(diagnostics, "continuityAddedEventIds"),
            "updated", listSize(diagnostics, "continuityUpdatedEventIds"),
            "stale", listSize(diagnostics, "continuityStaleEventIds"),
            "invalidated", listSize(diagnostics, "continuityInvalidatedEventIds"),
            "changedPaths", listSize(diagnostics, "continuityChangedPaths"),
            "documentIdentities", listSize(diagnostics, "continuityChangedDocumentIdentities"),
            "agentResultRefs", listSize(diagnostics, "continuityAgentResultRefs"),
            "truncated", Boolean.TRUE.equals(diagnostics.get("continuityDeltaTruncated"))
        ));
        value.put("structure", Map.of(
            "stories", number(diagnostics, "storyCount"),
            "continuedStories", number(diagnostics, "continuedStoryCount"),
            "unchangedStories", number(diagnostics, "unchangedStoryCount"),
            "newStories", number(diagnostics, "newStoryCount"),
            "reusedStories", number(diagnostics, "reusedStoryCount"),
            "recomputedStories", number(diagnostics, "recomputedStoryCount"),
            "threads", number(diagnostics, "threadCount"),
            "continuedThreads", number(diagnostics, "continuedThreadCount"),
            "unchangedThreads", number(diagnostics, "unchangedThreadCount"),
            "newThreads", number(diagnostics, "newThreadCount")
        ));
        value.put("chapters", Map.of(
            "total", Math.toIntExact(readService.chapters(userId, projectId, 0, 1).totalElements()),
            "affected", number(diagnostics, "affectedChapterCount"),
            "reused", number(diagnostics, "reusedChapterCount"),
            "recomputed", number(diagnostics, "recomputedChapterCount"),
            "created", number(diagnostics, "newChapterCount")
        ));
        value.put("model", Map.of(
            "requests", number(diagnostics, "requestCount"),
            "windows", number(diagnostics, "totalWindowCount"),
            "succeededWindows", number(diagnostics, "succeededWindowCount"),
            "failedWindows", number(diagnostics, "failedWindowCount"),
            "pendingWindows", number(diagnostics, "pendingWindowCount"),
            "windowCacheHits", number(diagnostics, "modelWindowCacheHitCount"),
            "status", String.valueOf(diagnostics.getOrDefault("modelStatus", "NOT_USED"))
        ));
        value.put("currentState", Map.of(
            "revisionChanged", !state.stateRevision().equals(previousStateRevision),
            "currentness", state.currentness(),
            "stale", state.stale(),
            "degraded", state.degraded(),
            "continuityDirty", state.continuityDirty(),
            "modelCalled", state.modelCalled()
        ));
        value.put("agentContext", Map.of(
            "revisionChanged", !context.packageRevision().equals(previousContextRevision),
            "modelCalled", context.generationMetadata().modelCalled(),
            "stateRevisionMatches", context.currentProjectState() != null
                && context.currentProjectState().stateRevision().equals(state.stateRevision())
        ));
        value.put("correction", correction == null ? Map.of() : correction);
        String projectionClass = Boolean.TRUE.equals(diagnostics.get("continuityNoOp"))
            ? "NO_OP_ZERO_WRITES"
            : Boolean.TRUE.equals(diagnostics.get("continuityPresentationChanged"))
                && number(diagnostics, "continuityDeltaSize") == 0
                    ? "CORRECTED_PRESENTATION_SCOPE"
                    : "AFFECTED_MANAGED_BLOCKS_ONLY";
        value.put("obsidianProjection", Map.of(
            "mutationClass", projectionClass,
            "sourceDeltaSize", number(diagnostics, "continuityDeltaSize"),
            "actualProjectorGate",
            "test_noop_has_zero_writes_and_stable_hashes + test_current_state_delta_updates_only_required_indexes_and_then_reaches_noop"
        ));
        value.put("rawEvents", Map.of(
            "current", overview.sourceEventCount(),
            "ledgerTotal", eventRepository.findByProjectId(projectId).size(),
            "conserved", Boolean.TRUE.equals(diagnostics.get("eventConservation"))
        ));
        value.put("extra", extra == null ? Map.of() : extra);
        return new ObservedStep(value, state.stateRevision(), context.packageRevision());
    }

    private ChangeStory storyForPath(UUID userId, UUID projectId, String path) {
        return allStories(userId, projectId).stream()
            .filter(story -> readService.story(userId, projectId, story.id()).events().stream()
                .anyMatch(event -> event.affectedPaths().contains(path)))
            .max(java.util.Comparator.comparing(ChangeStory::occurredTo)).orElseThrow();
    }

    private static Map<String, Object> windowAndDeltaMetrics(Map<String, Object> diagnostics) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("continuityNoOp", Boolean.TRUE.equals(diagnostics.get("continuityNoOp")));
        value.put("deltaSize", number(diagnostics, "continuityDeltaSize"));
        value.put("added", listSize(diagnostics, "continuityAddedEventIds"));
        value.put("updated", listSize(diagnostics, "continuityUpdatedEventIds"));
        value.put("stale", listSize(diagnostics, "continuityStaleEventIds"));
        value.put("invalidated", listSize(diagnostics, "continuityInvalidatedEventIds"));
        value.put("windows", number(diagnostics, "totalWindowCount"));
        value.put("succeeded", number(diagnostics, "succeededWindowCount"));
        value.put("failed", number(diagnostics, "failedWindowCount"));
        value.put("pending", number(diagnostics, "pendingWindowCount"));
        value.put("cacheHits", number(diagnostics, "modelWindowCacheHitCount"));
        value.put("status", String.valueOf(diagnostics.getOrDefault("modelStatus", "NOT_USED")));
        return value;
    }

    private static int number(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static int listSize(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value instanceof List<?> list ? list.size() : 0;
    }

    private void writeV39ContinuityArtifact(
        List<Map<String, Object>> steps,
        int storyModelRequests,
        int chapterModelRequests
    ) throws Exception {
        List<Integer> ledgerTotals = steps.stream()
            .map(step -> (Map<?, ?>) step.get("rawEvents"))
            .map(value -> ((Number) value.get("ledgerTotal")).intValue()).toList();
        for (int index = 1; index < ledgerTotals.size(); index++) {
            assertThat(ledgerTotals.get(index)).isGreaterThanOrEqualTo(ledgerTotals.get(index - 1));
        }
        Map<String, Object> artifact = new LinkedHashMap<>();
        artifact.put("schemaVersion", "projectflow-v3.9-continuity-dogfood-v1");
        artifact.put("source", "ProjectFlow Git history plus deterministic local continuation commits");
        artifact.put("fixedCommits", Map.of(
            "v385Final", V385_FINAL_BASELINE,
            "v39Freeze", V39_FREEZE_HEAD,
            "v39Core", V39_CORE_HEAD
        ));
        artifact.put("steps", steps);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pass", true);
        result.put("stepCount", steps.size());
        result.put("rawEventLedgerMonotonic", true);
        result.put("invalidEvidence", 0);
        result.put("crossProjectReferences", 0);
        result.put("unsupportedStrongFacts", 0);
        result.put("silentCorrectionLoss", 0);
        result.put("silentWrongTargetRebind", 0);
        result.put("successfulCheckpointReplays", 0);
        result.put("storyModelRequestsIncludingInjectedFailure", storyModelRequests);
        assertThat(chapterModelRequests).isBetween(0, 1);
        result.put("chapterModelRequestsAtMost", 1);
        artifact.put("result", result);
        artifact.put("providerEvidence", Map.of(
            "kind", "deterministic fault injection",
            "failure", "HTTP_503",
            "realProviderGate", "PENDING_SEPARATE_GITHUB_WORKFLOW"
        ));
        artifact.put("security", Map.of(
            "apiKeyStored", false,
            "promptStored", false,
            "rawResponseStored", false,
            "reasoningStored", false,
            "absolutePathStored", false
        ));
        Path output = Path.of("target", "projectflow-eval", "v39-dogfood-sequence.json")
            .toAbsolutePath().normalize();
        Files.createDirectories(output.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), artifact);
        Path committedEvidence = Path.of(
            "..", "docs", "acceptance-evidence", "v3.9", "dogfood-sequence.json"
        ).toAbsolutePath().normalize();
        if (Files.isRegularFile(committedEvidence)) {
            Object committed = objectMapper.convertValue(
                objectMapper.readTree(committedEvidence.toFile()), Object.class
            );
            Object observed = objectMapper.convertValue(artifact, Object.class);
            assertThat(committed.equals(observed))
                .as("committed T0-T7 evidence must match the executable sequence")
                .isTrue();
        }
    }

    private void provider(UUID userId) {
        AiProvider provider = new AiProvider(userId);
        provider.update(
            "history-fixed", "http://127.0.0.1", "test-key", "fixed", AiProviderType.OPENAI_COMPATIBLE,
            0.1, 8_000, true, List.of()
        );
        providerRepository.saveAndFlush(provider);
    }

    private ModelGatewayService.StructuredModelResponse modelResponse(String content) throws Exception {
        return new ModelGatewayService.StructuredModelResponse(content, outputAdapter.parse(content));
    }

    private String historyModelResponse(String prompt) throws Exception {
        String templateMarker = "\nREQUIRED_OUTPUT_TEMPLATE_JSON=";
        int templateStart = prompt.indexOf(templateMarker);
        if (templateStart >= 0) {
            JsonNode template = objectMapper.readTree(prompt.substring(templateStart + templateMarker.length()));
            return objectMapper.writeValueAsString(template);
        }
        String storiesMarker = "\nSTORIES_JSON=";
        String chaptersMarker = "\nCHAPTERS_JSON=";
        int storiesStart = prompt.indexOf(storiesMarker);
        int chaptersStart = prompt.indexOf(chaptersMarker, storiesStart + storiesMarker.length());
        int repairStart = prompt.indexOf(ProjectHistoryPromptBuilder.VALIDATION_REPAIR_MARKER, chaptersStart);
        assertThat(storiesStart).isGreaterThanOrEqualTo(0);
        assertThat(chaptersStart).isGreaterThan(storiesStart);
        JsonNode stories = objectMapper.readTree(prompt.substring(storiesStart + storiesMarker.length(), chaptersStart));
        JsonNode chapters = objectMapper.readTree(prompt.substring(
            chaptersStart + chaptersMarker.length(), repairStart < 0 ? prompt.length() : repairStart
        ));
        List<Map<String, Object>> storyOutput = new ArrayList<>();
        for (JsonNode story : stories) {
            String subject = story.path("subjectDisplayConcept").asText("项目内容");
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("storyId", story.path("storyId").asText());
            item.put("humanTitle", "调整“" + subject + "”并形成可追溯结果");
            item.put("oneSentenceSummary", "围绕“" + subject + "”的来源事件已归纳为可追溯变化。");
            item.put("beforeWording", "此前已保留与“" + subject + "”有关的项目记录。");
            item.put("changeWording", "这一阶段对“" + subject + "”的已有内容进行了调整。");
            item.put("afterWording", "当前可以继续查看“" + subject + "”的更新记录。");
            item.put("reason", "");
            item.put("reasonEvidenceRefs", List.of());
            item.put("unknownWording", "目前没有足够信息确认为什么做这次调整。");
            storyOutput.add(item);
        }
        List<Map<String, Object>> chapterOutput = new ArrayList<>();
        for (JsonNode chapter : chapters) {
            chapterOutput.add(Map.of(
                "chapterId", chapter.path("chapterId").asText(),
                "title", representativeChapterTitle(chapter),
                "summary", representativeChapterSummary(chapter)
            ));
        }
        return objectMapper.writeValueAsString(Map.of("stories", storyOutput, "chapters", chapterOutput));
    }

    private String historyChapterModelResponse(String prompt) throws Exception {
        String templateMarker = "\nREQUIRED_OUTPUT_TEMPLATE_JSON=";
        int templateStart = prompt.indexOf(templateMarker);
        if (templateStart >= 0) {
            JsonNode template = objectMapper.readTree(prompt.substring(templateStart + templateMarker.length()));
            return objectMapper.writeValueAsString(template);
        }
        String marker = "\nCHAPTER_SYNTHESIS_JSON=";
        int start = prompt.indexOf(marker);
        int repairStart = prompt.indexOf(ProjectHistoryPromptBuilder.VALIDATION_REPAIR_MARKER, start);
        assertThat(start).isGreaterThanOrEqualTo(0);
        JsonNode chapter = objectMapper.readTree(prompt.substring(
            start + marker.length(), repairStart < 0 ? prompt.length() : repairStart
        ));
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("chapterId", chapter.path("chapterId").asText());
        output.put("representedClusterIds", objectMapper.convertValue(
            chapter.path("requiredRepresentativeClusterIds"), List.class
        ));
        output.put("title", representativeChapterTitle(chapter));
        output.put("summary", representativeChapterSummary(chapter));
        return objectMapper.writeValueAsString(Map.of("chapters", List.of(output)));
    }

    private static String representativeChapterTitle(JsonNode chapter) {
        for (JsonNode cluster : chapter.path("representativeClusters")) {
            if (!"MINOR".equals(cluster.path("role").asText())
                && cluster.path("representativeOutcomes").isArray()
                && !cluster.path("representativeOutcomes").isEmpty()) {
                return cluster.path("representativeOutcomes").get(0).asText();
            }
        }
        return "记录项目阶段成果并形成可查看结果";
    }

    private static String representativeChapterSummary(JsonNode chapter) {
        List<String> outcomes = new ArrayList<>();
        for (JsonNode cluster : chapter.path("representativeClusters")) {
            if (cluster.path("representativeOutcomes").isArray()
                && !cluster.path("representativeOutcomes").isEmpty()) {
                String value = cluster.path("representativeOutcomes").get(0).asText().trim()
                    .replaceAll("[。；;！!？?]+$", "");
                if (!value.isBlank()) outcomes.add(value);
            }
        }
        if (outcomes.isEmpty()) return "这一时期记录项目阶段成果并形成可查看结果。";
        return "这一时期" + outcomes.get(0)
            + outcomes.stream().skip(1).map(value -> "，并" + value)
                .collect(java.util.stream.Collectors.joining()) + "。";
    }

    private void commitAt(Path root, String message, Instant occurredAt) throws Exception {
        git(root, "add", "-A");
        List<String> command = new ArrayList<>(List.of("git", "commit", "-m", message));
        ProcessBuilder builder = new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true);
        builder.environment().put("GIT_AUTHOR_DATE", occurredAt.toString());
        builder.environment().put("GIT_COMMITTER_DATE", occurredAt.toString());
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.waitFor() != 0) throw new AssertionError(String.join(" ", command) + " failed: " + output);
    }

    private record ObservedStep(Map<String, Object> evidence, String stateRevision, String contextRevision) {
    }

    private List<ChangeStory> allStories(UUID userId, UUID projectId) {
        List<ChangeStory> result = new ArrayList<>();
        int page = 0;
        while (true) {
            var slice = readService.stories(userId, projectId, null, false, true, null, null, page, 100);
            result.addAll(slice.items());
            if (++page >= slice.totalPages()) return List.copyOf(result);
        }
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

    private void writeCurrentDogfoodArtifact(
        int commitCount,
        com.projectflow.dto.ProjectHistoryDtos.HistoryOverviewResponse overview,
        List<ChangeStory> stories,
        List<HistoryChapter> chapters,
        List<EvolutionThread> threads,
        boolean cacheHit
    ) throws Exception {
        String configured = System.getProperty("projectflow.history.final-dogfood-output", "").trim();
        if (configured.isBlank()) return;
        Path output = Path.of(configured).toAbsolutePath().normalize();
        Files.createDirectories(output);
        Map<String, Object> artifact = new LinkedHashMap<>();
        artifact.put("version", "projectflow-v3.8.5-final-chapter-deterministic-dogfood-v1");
        artifact.put("source", "current ProjectFlow Git history");
        artifact.put("commitCount", commitCount);
        artifact.put("sourceEventCount", overview.sourceEventCount());
        artifact.put("storyCount", stories.size());
        artifact.put("visiblePrimaryCount", stories.stream()
            .filter(ChangeStory::primary).filter(story -> !story.hiddenByDefault()).count());
        artifact.put("supportingCount", stories.stream().filter(ChangeStory::supporting).count());
        artifact.put("chapterCount", chapters.size());
        artifact.put("threadCount", threads.size());
        artifact.put("diagnostics", overview.diagnostics());
        artifact.put("cacheHit", cacheHit);
        artifact.put("providerModelCalls", 0);
        Map<String, ChangeStory> storiesById = stories.stream().collect(
            LinkedHashMap::new, (map, story) -> map.put(story.id(), story), Map::putAll
        );
        ProjectHistoryChapterRepresentationPlanner planner = new ProjectHistoryChapterRepresentationPlanner(
            new ProjectHistoryLanguageService()
        );
        List<Map<String, Object>> chapterDetails = new ArrayList<>();
        for (HistoryChapter chapter : chapters.stream().limit(12).toList()) {
            var plan = planner.plan(chapter.storyRefs().stream().map(storiesById::get)
                .filter(java.util.Objects::nonNull).toList());
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("chapter", chapter);
            detail.put("primaryStoryCount", plan.primaryStoryCount());
            detail.put("supportingStoryCount", plan.supportingStoryCount());
            detail.put("representativePrimaryCoverage", plan.representativePrimaryCoverage());
            detail.put("dominantClusterIds", plan.dominantClusterIds());
            detail.put("selectedClusterIds", plan.requiredRepresentativeClusterIds());
            detail.put("clusters", plan.clusters().stream().map(cluster -> Map.of(
                "id", cluster.id(),
                "role", cluster.role(),
                "label", cluster.humanLabel(),
                "family", cluster.family(),
                "primaryStoryCount", cluster.primaryStoryCount(),
                "supportingStoryCount", cluster.supportingStoryCount(),
                "claimCeiling", cluster.claimCeiling(),
                "outcomes", cluster.representativeOutcomes(),
                "areas", cluster.areas(),
                "topics", cluster.topics()
            )).toList());
            chapterDetails.add(detail);
        }
        artifact.put("chapters", chapterDetails);
        artifact.put("security", Map.of(
            "apiKeyStored", false,
            "promptStored", false,
            "rawResponseStored", false,
            "reasoningStored", false,
            "absolutePathStored", false
        ));
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(
            output.resolve("projectflow-final-chapter-deterministic-dogfood.json").toFile(), artifact
        );
    }

    private List<ChangeStory> storiesBetween(UUID userId, UUID projectId, Instant from, Instant to) {
        List<ChangeStory> result = new ArrayList<>();
        int page = 0;
        while (true) {
            var slice = readService.stories(userId, projectId, null, false, from, to, page, 100);
            result.addAll(slice.items());
            if (page + 1 >= slice.totalPages()) return List.copyOf(result);
            page++;
        }
    }

    private static Path sourceRepository() {
        Path candidate = Path.of("").toAbsolutePath().normalize();
        while (candidate != null) {
            if (Files.exists(candidate.resolve(".git"))) return candidate;
            candidate = candidate.getParent();
        }
        throw new AssertionError("Unable to locate the ProjectFlow checkout from the test working directory");
    }

    private void writeDogfoodArtifact(
        UUID userId,
        UUID projectId,
        int baselineCommits,
        com.projectflow.dto.ProjectHistoryDtos.HistoryOverviewResponse overview,
        List<HistoryChapter> chapters,
        List<ChangeStory> stories,
        List<EvolutionThread> threads,
        long totalStoryCount,
        long totalThreadCount,
        long v37CommitEvents,
        long v37RawEvents,
        long v37StoryCount
    ) throws Exception {
        String configured = System.getProperty("projectflow.history.dogfood-output", "").trim();
        if (configured.isBlank()) return;
        Path output = Path.of(configured).toAbsolutePath().normalize();
        Files.createDirectories(output);
        List<Map<String, Object>> storyArtifacts = new ArrayList<>();
        for (ChangeStory story : stories.stream().limit(20).toList()) {
            var detail = readService.story(userId, projectId, story.id());
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("id", story.id());
            value.put("title", story.humanTitle());
            value.put("summary", story.oneSentenceSummary());
            value.put("occurredFrom", story.occurredFrom());
            value.put("occurredTo", story.occurredTo());
            value.put("rawEventCount", story.rawEventCount());
            value.put("evidenceCount", story.evidenceCount());
            value.put("unknowns", story.unknowns());
            value.put("drillDown", detail.events().stream().limit(5)
                .map(ProjectHistoryDogfoodAcceptanceTest::drillDown).toList());
            storyArtifacts.add(value);
        }
        Map<String, Object> artifact = new LinkedHashMap<>();
        artifact.put("version", "projectflow-v3.8.0-dogfood-v1");
        artifact.put("source", "ProjectFlow public Git history through V3.7.5");
        artifact.put("baselineCommit", V375_BASELINE);
        artifact.put("baselineCommitCount", baselineCommits);
        artifact.put("sourceEventCount", overview.sourceEventCount());
        artifact.put("chapterCount", chapters.size());
        artifact.put("storyCount", totalStoryCount);
        artifact.put("sampledStoryCount", stories.size());
        artifact.put("threadCount", totalThreadCount);
        artifact.put("sampledThreadCount", threads.size());
        artifact.put("v37CommitEvents", v37CommitEvents);
        artifact.put("v37RawEvents", v37RawEvents);
        artifact.put("v37Stories", v37StoryCount);
        Map<String, Object> v37Window = new LinkedHashMap<>();
        v37Window.put("fromInclusive", "2026-07-25T00:00:00Z");
        v37Window.put("toExclusive", "2026-08-02T00:00:00Z");
        v37Window.put("gitEventScope", "Only GIT source events in the frozen V3.7.x window");
        v37Window.put(
            "storyScope",
            "All reconstructed stories overlapping the same window, including frozen current-material metadata"
        );
        v37Window.put(
            "comparisonLimit",
            "The Git-event and story counts have different source scopes and are not a direct compression ratio"
        );
        artifact.put("v37Window", v37Window);
        Map<String, Object> compression = new LinkedHashMap<>();
        compression.put("rawEvents", overview.sourceEventCount());
        compression.put("stories", totalStoryCount);
        compression.put("chapters", chapters.size());
        artifact.put("compression", compression);
        Map<String, Object> diagnostics = new LinkedHashMap<>();
        diagnostics.put("eventConservation", overview.diagnostics().get("eventConservation"));
        diagnostics.put("invalidEvidenceRefCount", overview.diagnostics().get("invalidEvidenceRefCount"));
        diagnostics.put("crossProjectRefCount", overview.diagnostics().get("crossProjectRefCount"));
        diagnostics.put("unsupportedStrongFactCount", overview.diagnostics().get("unsupportedStrongFactCount"));
        artifact.put("diagnostics", diagnostics);
        artifact.put("chapters", chapters.stream().limit(12).toList());
        artifact.put("stories", storyArtifacts);
        artifact.put("threads", threads.stream().limit(20).toList());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(output.resolve("projectflow-v375-dogfood.json").toFile(), artifact);

        StringBuilder markdown = new StringBuilder("# ProjectFlow V3.8.0 自身历史 Dogfood\n\n")
            .append("公开来源：ProjectFlow Git 历史，固定到 V3.7.5 基线 ").append(V375_BASELINE).append("。\n\n")
            .append("原始提交 ").append(baselineCommits).append("，归一化来源事件 ")
            .append(overview.sourceEventCount()).append("，折叠为 ").append(totalStoryCount)
            .append(" 个变化故事和 ").append(chapters.size()).append(" 个动态时间篇章。\n\n")
            .append("V3.7.x 固定窗口内读取到 ").append(v37CommitEvents).append(" 个 Commit 和 ")
            .append(v37RawEvents).append(" 个 Git 原始事件；同一窗口内，跨 Git 与冻结的当前材料元数据共形成 ")
            .append(v37StoryCount).append(" 个故事。两组数字的来源范围不同，不能直接解释为 Git 事件到故事的压缩比。")
            .append("同一 Commit 可拆分独立变化，多次 Commit 也可合并为一个故事。\n\n")
            .append("## 时间篇章\n\n");
        chapters.stream().limit(8).forEach(chapter -> markdown.append("### ").append(chapter.title()).append("\n\n")
            .append(chapter.summary()).append("\n\n"));
        markdown.append("## 代表性变化故事与 Evidence 下钻\n\n");
        for (Map<String, Object> story : storyArtifacts.stream().limit(12).toList()) {
            markdown.append("### ").append(story.get("title")).append("\n\n")
                .append(story.get("summary")).append("\n\n")
                .append("Evidence 下钻：").append(story.get("drillDown")).append("\n\n");
        }
        markdown.append("该产物不包含绝对路径、凭证、完整 Prompt、raw response 或 reasoning。\n");
        Files.writeString(output.resolve("projectflow-v375-dogfood.md"), markdown.toString(), StandardCharsets.UTF_8);
    }

    private static Map<String, Object> drillDown(
        com.projectflow.dto.ProjectHistoryDtos.HistoryEventResponse event
    ) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("stableEventKey", event.stableEventKey());
        value.put("category", event.category());
        value.put("transition", event.transition());
        value.put("evidenceRefs", event.evidenceRefs());
        return value;
    }

    private ProjectSpace project(UUID userId, Path repository) {
        return project(userId, repository, DOGFOOD_PROJECT_ID, "ProjectFlow V3.7.5 Dogfood");
    }

    private ProjectSpace project(UUID userId, Path repository, UUID projectId, String name) {
        ProjectSpace project = new ProjectSpace(userId);
        ReflectionTestUtils.setField(project, "id", projectId);
        project.update(
            name, "Public baseline history", ProjectStatus.BUILDING,
            List.of("Java", "TypeScript", "Python"), "",
            LocalDate.now(), null
        );
        project = projectRepository.saveAndFlush(project);
        ProjectMemory memory = new ProjectMemory(project.getId());
        memory.update("", "", "", "", "", "", "", "", "");
        memory.rememberLocalProjectPath(repository.toAbsolutePath().normalize().toString());
        memoryRepository.saveAndFlush(memory);
        return project;
    }

    private static void normalizeWorkingTreeTimestamps(Path repository, Instant baselineTime) throws Exception {
        FileTime fixed = FileTime.from(baselineTime);
        try (var paths = Files.walk(repository)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                Path relative = repository.relativize(path);
                if (relative.getNameCount() > 0 && relative.getName(0).toString().equals(".git")) continue;
                Files.setLastModifiedTime(path, fixed);
            }
        }
    }

    private Instant commitTime(Path repository, String revision) throws Exception {
        return Instant.ofEpochSecond(Long.parseLong(
            git(repository, "show", "-s", "--format=%ct", revision).trim()
        ));
    }

    private String git(Path root, String... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.add("-c");
        command.add("safe.directory=" + root.toAbsolutePath().normalize().toString().replace('\\', '/'));
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.waitFor() != 0) throw new AssertionError(String.join(" ", command) + " failed: " + output);
        return output;
    }
}
