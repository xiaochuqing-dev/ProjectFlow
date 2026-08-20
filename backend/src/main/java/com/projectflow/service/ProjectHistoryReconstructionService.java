package com.projectflow.service;

import static com.projectflow.dto.ProjectHistoryDtos.*;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.entity.AiProvider;
import com.projectflow.entity.ProjectFactEpistemicStatus;
import com.projectflow.entity.ProjectHistoryEvent;
import com.projectflow.entity.ProjectHistoryEvent.Authority;
import com.projectflow.entity.ProjectHistoryEvent.Category;
import com.projectflow.entity.ProjectHistoryEvent.RewriteState;
import com.projectflow.entity.ProjectHistoryEvent.SourceType;
import com.projectflow.entity.ProjectHistoryEvent.Transition;
import com.projectflow.entity.ProjectHistorySnapshot;
import com.projectflow.entity.ProjectHistoryWindowCheckpoint;
import com.projectflow.repository.AiProviderRepository;
import com.projectflow.repository.ProjectHistoryEventRepository;
import com.projectflow.repository.ProjectHistorySnapshotRepository;
import com.projectflow.repository.ProjectHistoryWindowCheckpointRepository;
import com.projectflow.service.ProjectHistorySourceCollector.CollectedEvent;
import com.projectflow.service.ProjectHistorySourceCollector.CollectionOutcome;

/**
 * Builds the replaceable level 0-3 project-history read model from complete
 * persisted source events. Model output may improve wording but cannot change
 * membership, chronology, authority or Evidence references.
 */
@Service
public class ProjectHistoryReconstructionService {
    static final String STRATEGY_VERSION = "project-history-v385-chapter-representation-v1";
    static final String PROMPT_VERSION = ProjectHistoryPromptBuilder.PROMPT_VERSION;
    private static final int MODEL_STORY_LIMIT = ProjectHistoryWindowPlanner.DEFAULT_STORY_LIMIT;
    private static final int MODEL_EVENT_LIMIT = ProjectHistoryWindowPlanner.DEFAULT_EVENT_LIMIT;
    private static final int MAX_MODEL_WINDOWS = ProjectHistoryWindowPlanner.MAX_WINDOWS;
    private static final int MAX_CHAPTER_SYNTHESIS_WINDOWS = 4;
    // Keep repetitive histories readable without letting one subject absorb an
    // unbounded stream of events. The smaller bound also leaves room for
    // multiple bounded windows in large repositories.
    private static final int STORY_EVENT_LIMIT = 32;
    private static final Duration STORY_GAP = Duration.ofDays(10);
    private static final Duration CHAPTER_GAP = Duration.ofDays(21);
    private static final Duration INCREMENTAL_OVERLAP = Duration.ofDays(31);
    private static final int OVERVIEW_CHAPTER_LIMIT = 8;
    private static final Set<String> RAW_ONLY_SUBJECTS = Set.of(
        "dependency-metadata", "dependency-build-metadata", "sensitive-material", "projectflow-metadata"
    );
    private static final Set<String> WEAK_TEXT = Set.of(
        "优化了系统", "改进了功能", "进行了重构", "提升了体验", "修改了相关文件", "项目变化", "相关调整"
    );
    private static final Set<String> MODEL_CHAPTER_SYNTHESIS_FIELDS = Set.of(
        "chapterId", "representedClusterIds", "title", "summary"
    );
    private static final Set<Transition> STORY_BOUNDARIES = Set.of(
        Transition.REMOVED, Transition.RESTORED, Transition.REPLACED,
        Transition.REVERTED, Transition.REAPPLIED, Transition.SPLIT, Transition.MERGED
    );
    private static final Set<Transition> FILE_ALIAS_TRANSITIONS = Set.of(
        Transition.RENAMED, Transition.MOVED, Transition.SPLIT, Transition.MERGED, Transition.REPLACED
    );
    private static final Set<String> RETRYABLE_WINDOW_STATUSES = Set.of(
        "FAILED", "CANCELLED", "RUNNING", "SKIPPED"
    );

    private final ProjectHistorySourceCollector sourceCollector;
    private final ProjectHistoryEventRepository eventRepository;
    private final ProjectHistorySnapshotRepository snapshotRepository;
    private final ProjectHistoryWindowCheckpointRepository windowCheckpointRepository;
    private final ProjectHistoryWindowCheckpointService windowCheckpointService;
    private final AiProviderRepository providerRepository;
    private final ModelGatewayService modelGateway;
    private final SensitiveContentRedactor redactor;
    private final ProjectHistoryPromptBuilder promptBuilder;
    private final ProjectHistoryLanguageService languageService;
    private final ProjectHistoryNarrativeEntailmentValidator narrativeValidator;
    private final ProjectHistoryChapterRepresentationPlanner chapterRepresentationPlanner;
    private final ProjectHistoryWindowPlanner windowPlanner;
    private final ProjectHistoryCorrectionService correctionService;
    private final ProjectHistoryPresentationInvariantValidator presentationInvariantValidator;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public ProjectHistoryReconstructionService(
        ProjectHistorySourceCollector sourceCollector,
        ProjectHistoryEventRepository eventRepository,
        ProjectHistorySnapshotRepository snapshotRepository,
        ProjectHistoryWindowCheckpointRepository windowCheckpointRepository,
        ProjectHistoryWindowCheckpointService windowCheckpointService,
        AiProviderRepository providerRepository,
        ModelGatewayService modelGateway,
        SensitiveContentRedactor redactor,
        ProjectHistoryPromptBuilder promptBuilder,
        ProjectHistoryLanguageService languageService,
        ProjectHistoryNarrativeEntailmentValidator narrativeValidator,
        ProjectHistoryCorrectionService correctionService,
        ProjectHistoryPresentationInvariantValidator presentationInvariantValidator,
        ObjectMapper objectMapper,
        PlatformTransactionManager transactionManager
    ) {
        this.sourceCollector = sourceCollector;
        this.eventRepository = eventRepository;
        this.snapshotRepository = snapshotRepository;
        this.windowCheckpointRepository = windowCheckpointRepository;
        this.windowCheckpointService = windowCheckpointService;
        this.providerRepository = providerRepository;
        this.modelGateway = modelGateway;
        this.redactor = redactor;
        this.promptBuilder = promptBuilder;
        this.languageService = languageService;
        this.narrativeValidator = narrativeValidator;
        this.chapterRepresentationPlanner = new ProjectHistoryChapterRepresentationPlanner(languageService);
        this.windowPlanner = new ProjectHistoryWindowPlanner();
        this.correctionService = correctionService;
        this.presentationInvariantValidator = presentationInvariantValidator;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public HistoryRefreshOutcome refresh(UUID userId, UUID projectId, UUID jobId, boolean force) throws Exception {
        return refresh(userId, projectId, jobId, force, (stage, message) -> { });
    }

    public HistoryRefreshOutcome refresh(
        UUID userId,
        UUID projectId,
        UUID jobId,
        boolean force,
        HistoryProgress progress
    ) throws Exception {
        HistoryProgress safeProgress = progress == null ? (stage, message) -> { } : progress;
        ProjectHistorySnapshot before = snapshotRepository.findByProjectId(projectId).orElse(null);
        CollectionOutcome collected = null;
        PersistedEvents persisted = null;
        List<ModelGatewayService.ModelCallDiagnostics> modelDiagnostics = new ArrayList<>();
        try {
            safeProgress.update("HISTORY_SOURCE_DISCOVERY", "正在有界读取并保存项目历程来源事件");
            collected = sourceCollector.collect(userId, projectId);
            persisted = upsert(projectId, collected);
            CollectionOutcome completedCollection = collected;
            PersistedEvents completedPersistence = persisted;
            String currentFingerprint = fingerprint(completedPersistence.currentEvents());
            String correctionRevision = correctionService.currentPresentationRevision(projectId, currentFingerprint);
            boolean cacheHit = !force
                && completedCollection.sourceScanComplete()
                && before != null
                && before.getLatestSuccessfulAt() != null
                && currentFingerprint.equals(before.getSourceEventFingerprint())
                && STRATEGY_VERSION.equals(before.getStrategyVersion())
                && PROMPT_VERSION.equals(before.getPromptVersion())
                && correctionRevision.equals(previousPresentationRevision(before))
                && !hasRetryableWindowCheckpoint(projectId)
                && !hasPendingWindowDiagnostics(before);
            if (cacheHit) {
                Map<String, Object> diagnostics = diagnostics(
                    completedCollection, completedPersistence, "UNCHANGED", "CACHE_HIT", 0, true, 0, 0,
                    "CACHE_HIT", previousStories(before).size(), 0,
                    0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0
                );
                carryForwardSnapshotDiagnostics(before, diagnostics);
                diagnostics.put("presentationRevision", correctionRevision);
                transactionTemplate.executeWithoutResult(status -> {
                    ProjectHistorySnapshot snapshot = snapshotRepository.findLockedByProjectId(projectId).orElseThrow();
                    snapshot.recordCacheHit(jobId, json(diagnostics));
                    snapshotRepository.save(snapshot);
                });
                return new HistoryRefreshOutcome(
                    json(Map.of("status", "CACHE_HIT", "sourceEventCount", completedPersistence.currentEvents().size())),
                    List.of(), false, false, true
                );
            }

            beginSnapshot(projectId, jobId, before == null || !currentFingerprint.equals(before.getSourceEventFingerprint()));
            safeProgress.update("HISTORY_ENGINEERING_RECONSTRUCTION", "正在确定性组织变化故事、时间篇章和演变链");
            DeterministicResult deterministic = reconstruct(completedCollection, completedPersistence, before, force);
            ModelResult modelResult = enhanceWithModel(
                userId, projectId, deterministic, completedPersistence.currentEvents(),
                modelDiagnostics, safeProgress
            );
            SnapshotResult finalResult = modelResult.result();
            validate(finalResult, completedPersistence.currentEvents());
            boolean degraded = !completedCollection.complete() || modelResult.failed() || modelResult.incomplete();
            Map<String, Object> diagnostics = diagnostics(
                completedCollection,
                completedPersistence,
                completedPersistence.rewriteMode(),
                modelResult.status(),
                modelDiagnostics.stream().mapToInt(ModelGatewayService.ModelCallDiagnostics::requestCount).sum(),
                false,
                finalResult.stories().size(),
                finalResult.threads().size(),
                deterministic.reconstructionMode(),
                deterministic.reusedStoryCount(),
                deterministic.recomputedStoryIds().size(),
                modelResult.enhancedStoryCount(),
                modelResult.boundedStoryCount(),
                modelResult.rejectedInvalidEvidenceRefCount(),
                modelResult.rejectedCrossProjectRefCount(),
                modelResult.rejectedUnsupportedClaimCount(),
                modelResult.promptCharacterCount(),
                modelResult.promptOmittedStoryCount(),
                modelResult.promptOmittedChapterCount(),
                modelResult.windowCount(),
                modelResult.windowCacheHitCount(),
                modelResult.checkpointCount(),
                modelResult.processedWindowCount(),
                modelResult.unprocessedWindowCount(),
                modelResult.unprocessedStoryCount()
            );
            diagnostics.put("modelDeterministicTitleFallbackCount", (int) finalResult.stories().stream()
                .filter(story -> "MODEL_VALIDATED_WITH_DETERMINISTIC_TITLE".equals(story.summaryStatus()))
                .count());
            diagnostics.put("modelValidationRepairCount", (int) modelDiagnostics.stream()
                .filter(value -> "HISTORY_VALIDATION_RETRY".equals(value.retryType())).count());
            diagnostics.put("modelValidationRepairFailureCount", (int) modelDiagnostics.stream()
                .filter(value -> "HISTORY_VALIDATION_RETRY".equals(value.retryType()))
                .filter(value -> !value.compactRetrySucceeded()).count());
            diagnostics.put("presentationRevision", correctionRevision);
            diagnostics.put("totalWindowCount", modelResult.windowCount());
            diagnostics.put("succeededWindowCount", modelResult.succeededWindowCount());
            diagnostics.put("failedWindowCount", modelResult.failedWindowCount());
            diagnostics.put("skippedWindowCount", modelResult.skippedWindowCount());
            diagnostics.put("skippedStoryCount", modelResult.skippedStoryCount());
            diagnostics.put("pendingWindowCount", modelResult.pendingWindowCount());
            diagnostics.put("nextWindowOrdinal", modelResult.nextWindowOrdinal());
            diagnostics.put("processedStoryCount", modelResult.enhancedStoryCount());
            diagnostics.put("unprocessedStoryCount", modelResult.unprocessedStoryCount());
            diagnostics.put("chapterSynthesisCount", modelResult.chapterSynthesisCount());
            diagnostics.put("chapterSynthesisProcessedCount", modelResult.chapterSynthesisProcessedCount());
            diagnostics.put("chapterSynthesisCacheHitCount", modelResult.chapterSynthesisCacheHitCount());
            diagnostics.put("chapterSynthesisFailedCount", modelResult.chapterSynthesisFailedCount());
            diagnostics.put("chapterSynthesisPendingCount", modelResult.chapterSynthesisPendingCount());
            diagnostics.put("chapterSynthesisPromptCharacterCount", modelResult.chapterSynthesisPromptCharacterCount());
            diagnostics.put("chapterSynthesisOmittedStoryCount", modelResult.chapterSynthesisOmittedStoryCount());
            putChapterRepresentationDiagnostics(finalResult, diagnostics);
            if (modelResult.failureSummary() != null && !modelResult.failureSummary().isBlank()) {
                diagnostics.put("modelFallback", modelResult.failureSummary());
            }
            HistoryCoverage coverage = coverage(completedCollection, completedPersistence);
            HistoryOverviewContent overview = overview(
                finalResult.chapters(), finalResult.stories(), completedPersistence.currentEvents(), coverage
            );
            safeProgress.update("PERSIST_HISTORY_SNAPSHOT", "正在保存已校验的项目历程快照");
            transactionTemplate.executeWithoutResult(status -> {
                ProjectHistorySnapshot snapshot = snapshotRepository.findLockedByProjectId(projectId)
                    .orElseGet(() -> new ProjectHistorySnapshot(projectId));
                snapshot.complete(
                    completedCollection.projectRevision(), currentFingerprint, completedPersistence.currentEvents().size(),
                    earliest(completedPersistence.currentEvents()), latest(completedPersistence.currentEvents()),
                    STRATEGY_VERSION, PROMPT_VERSION,
                    json(overview), json(finalResult.chapters()), json(finalResult.stories()), json(finalResult.threads()),
                    json(coverage), json(diagnostics), jobId, degraded
                );
                snapshotRepository.save(snapshot);
                cleanupObsoleteWindowCheckpoints(projectId, modelResult.activeCheckpointCacheKeys());
            });
            return new HistoryRefreshOutcome(
                json(Map.ofEntries(
                    Map.entry("status", degraded ? "DEGRADED" : "READY"),
                    Map.entry("sourceEventCount", completedPersistence.currentEvents().size()),
                    Map.entry("chapterCount", finalResult.chapters().size()),
                    Map.entry("storyCount", finalResult.stories().size()),
                    Map.entry("threadCount", finalResult.threads().size()),
                    Map.entry("rewriteMode", completedPersistence.rewriteMode()),
                    Map.entry("reconstructionMode", deterministic.reconstructionMode()),
                    Map.entry("reusedStoryCount", deterministic.reusedStoryCount()),
                    Map.entry("modelEnhancedStoryCount", modelResult.enhancedStoryCount()),
                    Map.entry("boundedDeterministicStoryCount", modelResult.boundedStoryCount()),
                    Map.entry("modelWindowCount", modelResult.windowCount()),
                    Map.entry("modelProcessedWindowCount", modelResult.processedWindowCount()),
                    Map.entry("modelUnprocessedWindowCount", modelResult.unprocessedWindowCount()),
                    Map.entry("modelUnprocessedStoryCount", modelResult.unprocessedStoryCount()),
                    Map.entry("chapterSynthesisCount", modelResult.chapterSynthesisCount()),
                    Map.entry("chapterSynthesisProcessedCount", modelResult.chapterSynthesisProcessedCount()),
                    Map.entry("chapterSynthesisPendingCount", modelResult.chapterSynthesisPendingCount()),
                    Map.entry("modelStatus", modelResult.status())
                )),
                List.copyOf(modelDiagnostics), modelResult.used(), degraded, false
            );
        } catch (CancellationException exception) {
            failSnapshot(projectId, jobId, "PROJECT_HISTORY_CANCELLED", "项目历程刷新已取消，保留上一次成功快照。", Map.of());
            throw exception;
        } catch (Exception exception) {
            Map<String, Object> failureDiagnostics = new LinkedHashMap<>();
            if (collected != null) failureDiagnostics.put("discoveredEventCount", collected.events().size());
            if (persisted != null) {
                failureDiagnostics.put("sourceEventCount", persisted.currentEvents().size());
                failureDiagnostics.put("rewriteMode", persisted.rewriteMode());
            }
            failSnapshot(
                projectId, jobId, "PROJECT_HISTORY_REFRESH_FAILED", safeError(exception),
                failureDiagnostics
            );
            throw exception;
        }
    }

    private PersistedEvents upsert(UUID projectId, CollectionOutcome collected) {
        return transactionTemplate.execute(status -> {
            List<ProjectHistoryEvent> existing = eventRepository.findByProjectId(projectId);
            Map<String, ProjectHistoryEvent> byKey = new LinkedHashMap<>();
            existing.forEach(event -> byKey.put(event.getStableEventKey(), event));
            Set<String> incomingKeys = new LinkedHashSet<>();
            int added = 0;
            int updated = 0;
            int reused = 0;
            int preservedUnseen = 0;
            Instant affectedFrom = null;
            List<ProjectHistoryEvent> changed = new ArrayList<>();
            for (CollectedEvent draft : collected.events()) {
                incomingKeys.add(draft.stableEventKey());
                ProjectHistoryEvent event = byKey.get(draft.stableEventKey());
                boolean replace;
                if (event == null) {
                    event = new ProjectHistoryEvent(projectId, draft.stableEventKey());
                    added++;
                    replace = true;
                } else if (!draft.payloadHash().equals(event.getPayloadHash()) || event.getRewriteState() != RewriteState.CURRENT) {
                    updated++;
                    replace = true;
                } else {
                    reused++;
                    replace = false;
                }
                if (replace) {
                    event.replace(
                        draft.sourceType(), draft.sourceIdentity(), draft.sourceRevision(), draft.projectRevision(),
                        draft.occurredAt(), draft.effectiveAt(), draft.actorLabel(), draft.scope(), draft.category(),
                        draft.transition(), draft.safeSourceLabel(), json(draft.affectedPaths()), json(draft.subjectKeys()),
                        json(draft.evidenceRefs()), json(draft.relationRefs()), draft.authority(), draft.epistemicStatus(),
                        json(draft.coverage()), json(draft.limitations()), draft.rawSourceDeepLink(), draft.payloadHash()
                    );
                    changed.add(event);
                    affectedFrom = earlier(affectedFrom, draft.occurredAt());
                }
            }
            int invalidated = 0;
            int stale = 0;
            for (ProjectHistoryEvent old : existing) {
                if (incomingKeys.contains(old.getStableEventKey()) || old.getRewriteState() != RewriteState.CURRENT) continue;
                if (!collected.sourceScanComplete()) {
                    preservedUnseen++;
                    continue;
                }
                RewriteState next = switch (old.getSourceType()) {
                    case GIT, GITHUB, FILESYSTEM, DOCUMENT -> RewriteState.INVALIDATED;
                    default -> RewriteState.STALE;
                };
                old.markRewriteState(next);
                changed.add(old);
                affectedFrom = earlier(affectedFrom, old.getOccurredAt());
                if (next == RewriteState.INVALIDATED) invalidated++; else stale++;
            }
            eventRepository.saveAll(changed);
            eventRepository.flush();
            List<ProjectHistoryEvent> current = eventRepository
                .findByProjectIdAndRewriteStateOrderByOccurredAtAscIdAsc(projectId, RewriteState.CURRENT);
            int staleTotal = boundedCount(eventRepository.countByProjectIdAndRewriteState(projectId, RewriteState.STALE));
            int invalidatedTotal = boundedCount(eventRepository.countByProjectIdAndRewriteState(projectId, RewriteState.INVALIDATED));
            String mode;
            if (existing.isEmpty()) mode = "FULL_REBUILD";
            else if (preservedUnseen > 0 && added == 0 && updated == 0 && invalidated == 0 && stale == 0) {
                mode = "INCOMPLETE_SOURCE_PRESERVED";
            }
            else if (preservedUnseen > 0) mode = "PARTIAL_SOURCE_REFRESH";
            else if (added == 0 && updated == 0 && invalidated == 0 && stale == 0) mode = "UNCHANGED";
            else if (invalidated == 0 && stale == 0 && updated == 0) mode = "APPEND_ONLY";
            else if (invalidated > 0 && added > 0) mode = "PARTIAL_REWRITE";
            else if (invalidated > 0) mode = "HISTORY_REWRITE";
            else mode = "SOURCE_REFRESH";
            return new PersistedEvents(
                current, added, updated, reused, stale, invalidated, staleTotal, invalidatedTotal,
                preservedUnseen, affectedFrom, mode
            );
        });
    }

    private void beginSnapshot(UUID projectId, UUID jobId, boolean changed) {
        transactionTemplate.executeWithoutResult(status -> {
            ProjectHistorySnapshot snapshot = snapshotRepository.findLockedByProjectId(projectId)
                .orElseGet(() -> new ProjectHistorySnapshot(projectId));
            snapshot.begin(jobId, changed);
            snapshotRepository.save(snapshot);
        });
    }

    private void failSnapshot(UUID projectId, UUID jobId, String code, String summary, Map<String, Object> diagnostics) {
        transactionTemplate.executeWithoutResult(status -> {
            ProjectHistorySnapshot snapshot = snapshotRepository.findLockedByProjectId(projectId)
                .orElseGet(() -> new ProjectHistorySnapshot(projectId));
            snapshot.fail(code, summary, json(diagnostics), jobId);
            snapshotRepository.save(snapshot);
        });
    }

    private DeterministicResult reconstruct(
        CollectionOutcome collected,
        PersistedEvents persisted,
        ProjectHistorySnapshot previousSnapshot,
        boolean force
    ) {
        List<EventView> events = persisted.currentEvents().stream().map(this::view).toList();
        List<EventView> semanticEvents = events.stream().filter(ProjectHistoryReconstructionService::semanticEligible).toList();
        Comparator<EventView> storyEventOrder = storyEventOrder(events);
        Map<UUID, EventView> eventsById = new LinkedHashMap<>();
        events.forEach(event -> eventsById.put(event.id(), event));
        Set<UUID> semanticEventIds = semanticEvents.stream().map(EventView::id)
            .collect(LinkedHashSet::new, Set::add, Set::addAll);
        Map<String, String> subjectAliases = canonicalSubjectAliases(semanticEvents);

        List<ChangeStory> retainedStories = new ArrayList<>();
        List<EventView> reconstructionEvents = semanticEvents;
        String reconstructionMode = "FULL_REBUILD";
        if (!force && previousSnapshot != null && previousSnapshot.getLatestSuccessfulAt() != null
            && STRATEGY_VERSION.equals(previousSnapshot.getStrategyVersion())
            && persisted.affectedFrom() != null) {
            Instant cutoff = persisted.affectedFrom().minus(INCREMENTAL_OVERLAP);
            Set<UUID> currentEventIds = eventsById.keySet();
            retainedStories = previousStories(previousSnapshot).stream()
                .filter(story -> story.occurredTo().isBefore(cutoff))
                .filter(story -> currentEventIds.containsAll(story.eventRefs()))
                .filter(story -> semanticEventIds.containsAll(story.eventRefs()))
                .map(story -> remapSubject(story, subjectAliases))
                .toList();
            if (!retainedStories.isEmpty()) {
                Set<UUID> retainedEventIds = retainedStories.stream().flatMap(story -> story.eventRefs().stream())
                    .collect(LinkedHashSet::new, Set::add, Set::addAll);
                reconstructionEvents = semanticEvents.stream().filter(event -> !retainedEventIds.contains(event.id())).toList();
                reconstructionMode = "INCREMENTAL_OVERLAP_WINDOW";
            }
        }

        List<StoryEnvelope> envelopes = new ArrayList<>();
        for (ChangeStory retainedStory : retainedStories) {
            envelopes.add(envelope(retainedStory, eventsById));
        }
        List<StoryEnvelope> recomputed = buildStoryEnvelopes(
            reconstructionEvents, subjectAliases, collected.complete(), storyEventOrder
        );
        envelopes.addAll(recomputed);
        envelopes = new ArrayList<>(classifyPrimaryAndSupporting(envelopes, eventsById));
        envelopes = new ArrayList<>(compressRepetitivePrimaryStories(envelopes, eventsById));
        envelopes.sort(Comparator.comparing((StoryEnvelope envelope) -> envelope.story().occurredFrom())
            .thenComparing(envelope -> envelope.story().id()));
        List<EvolutionThread> threads = threads(envelopes);
        List<ChangeStory> stories = applyLaterOutcomes(envelopes, threads);
        List<HistoryChapter> chapters = chapters(stories, events);
        Set<String> recomputedStoryIds = recomputed.stream().map(item -> item.story().id())
            .collect(LinkedHashSet::new, Set::add, Set::addAll);
        return new DeterministicResult(
            new SnapshotResult(chapters, stories, threads), Set.copyOf(recomputedStoryIds),
            reconstructionMode, retainedStories.size()
        );
    }

    private List<StoryEnvelope> buildStoryEnvelopes(
        List<EventView> events,
        Map<String, String> subjectAliases,
        boolean complete,
        Comparator<EventView> storyEventOrder
    ) {
        Map<String, String> commitGroups = commitGroupingKeys(events);
        Map<String, List<EventView>> bySubject = new LinkedHashMap<>();
        for (EventView event : events) {
            String selected = selectStorySubject(event, commitGroups);
            if (selected.isBlank()) selected = ProjectHistorySourceCollector.subjectFromText(event.label());
            String key = subjectAliases.getOrDefault(selected, selected);
            bySubject.computeIfAbsent(key, ignored -> new ArrayList<>()).add(event);
        }
        List<StoryEnvelope> envelopes = new ArrayList<>();
        for (Map.Entry<String, List<EventView>> entry : bySubject.entrySet()) {
            List<EventView> subjectEvents = entry.getValue().stream()
                .sorted(storyEventOrder).toList();
            List<EventView> group = new ArrayList<>();
            for (EventView event : subjectEvents) {
                if (!group.isEmpty() && newStory(group, event)) {
                    envelopes.add(story(entry.getKey(), group, complete, storyEventOrder));
                    group = new ArrayList<>();
                }
                group.add(event);
            }
            if (!group.isEmpty()) envelopes.add(story(entry.getKey(), group, complete, storyEventOrder));
        }
        return envelopes;
    }

    /**
     * Select one semantic owner for each event. Source collection intentionally
     * keeps several subject hints (commit text, file subject and area); using
     * all of them as story owners duplicates the same change and makes a large
     * history unreadable. The extra hints remain on the raw event/atom.
     */
    private Map<String, String> commitGroupingKeys(List<EventView> events) {
        Map<String, String> result = new LinkedHashMap<>();
        for (EventView event : events) {
            if (!Set.of(Category.COMMIT, Category.MERGE).contains(event.category())) continue;
            List<String> pathSubjects = event.paths().stream()
                .map(ProjectHistorySourceCollector::historySubjectKey)
                .filter(value -> !value.isBlank()).distinct().toList();
            List<String> changeSubjects = event.subjectKeys().stream()
                .filter(value -> value.startsWith("change-")).distinct().toList();
            boolean bulk = event.paths().size() >= 20;
            boolean splitByPath = !bulk && pathSubjects.size() > 1 && pathSubjects.size() <= 3;
            String selected;
            if (bulk) {
                selected = changeSubjects.isEmpty()
                    ? firstNonArea(event.subjectKeys(), pathSubjects)
                    : changeSubjects.get(0);
            } else if (pathSubjects.size() == 1) {
                selected = pathSubjects.get(0);
            } else if (!changeSubjects.isEmpty()) {
                selected = changeSubjects.get(0);
            } else {
                selected = firstNonArea(event.subjectKeys(), pathSubjects);
            }
            if (!selected.isBlank()) {
                String stored = bulk ? "__BULK__" + selected : splitByPath ? "__SPLIT__" + selected : selected;
                commitRefs(event).forEach(ref -> result.putIfAbsent(ref.substring("commit:".length()), stored));
                if (!event.sourceRevision().isBlank()) result.putIfAbsent(event.sourceRevision(), stored);
            }
        }
        return result;
    }

    private String selectStorySubject(EventView event, Map<String, String> commitGroups) {
        if (event.subjectKeys().contains("dependency-metadata")
            || event.subjectKeys().contains("sensitive-material")
            || event.subjectKeys().contains("projectflow-metadata")) {
            return event.subjectKeys().stream().filter(RAW_ONLY_SUBJECTS::contains).findFirst().orElse("");
        }
        String parent = event.relationRefs().stream()
            .filter(value -> value != null && value.startsWith("commit:"))
            .map(value -> value.substring("commit:".length()))
            .map(commitGroups::get)
            .filter(value -> value != null && !value.isBlank())
            .findFirst().orElse("");
        // Bulk imports intentionally retain area-level primary stories so a
        // broad commit does not collapse a whole repository into one label.
        boolean bulkParent = parent.startsWith("__BULK__");
        boolean splitParent = parent.startsWith("__SPLIT__");
        if (bulkParent && !Set.of(Category.COMMIT, Category.MERGE).contains(event.category())) {
            String area = event.subjectKeys().stream().filter(value -> value.startsWith("project-area-")).findFirst().orElse("");
            if (!area.isBlank()) return area;
        }
        if (splitParent && !Set.of(Category.COMMIT, Category.MERGE).contains(event.category())) {
            parent = "";
        }
        if (!parent.isBlank() && !bulkParent && event.paths().size() < 20) return parent;
        if (Set.of(Category.COMMIT, Category.MERGE).contains(event.category()) && !parent.isBlank()) {
            if (bulkParent) return parent.substring("__BULK__".length());
            if (splitParent) return parent.substring("__SPLIT__".length());
            return parent;
        }
        return event.subjectKeys().stream()
            .filter(value -> !value.startsWith("project-area-") || event.paths().size() >= 20)
            .filter(value -> !RAW_ONLY_SUBJECTS.contains(value))
            .findFirst()
            .orElseGet(() -> event.subjectKeys().stream().findFirst().orElse(""));
    }

    private static String firstNonArea(List<String> values, List<String> fallback) {
        return Stream.concat(values == null ? Stream.empty() : values.stream(), fallback == null ? Stream.empty() : fallback.stream())
            .filter(value -> value != null && !value.isBlank() && !value.startsWith("project-area-"))
            .findFirst().orElseGet(() -> values == null || values.isEmpty() ? "" : values.get(0));
    }

    /**
     * Classify deterministic story candidates without dropping their events.
     * Supporting candidates remain addressable but are attached to the nearest
     * primary result when a strong commit/area signal exists.
     */
    private List<StoryEnvelope> classifyPrimaryAndSupporting(
        List<StoryEnvelope> envelopes,
        Map<UUID, EventView> eventsById
    ) {
        if (envelopes.isEmpty()) return envelopes;
        List<StoryEnvelope> ordered = new ArrayList<>(envelopes);
        Set<String> potentialSupportingIds = new LinkedHashSet<>();
        Set<String> supportingIds = new LinkedHashSet<>();
        Map<String, String> attachments = new LinkedHashMap<>();
        for (StoryEnvelope envelope : ordered) {
            ChangeStory story = envelope.story();
            List<EventView> members = story.eventRefs().stream().map(eventsById::get)
                .filter(java.util.Objects::nonNull).toList();
            List<com.projectflow.entity.ProjectHistoryEvent.Category> categories = members.stream()
                .map(EventView::category).toList();
            List<String> paths = members.stream().flatMap(event -> event.paths().stream()).distinct().limit(80).toList();
            List<String> labels = members.stream().map(EventView::label).filter(value -> value != null && !value.isBlank())
                .distinct().limit(20).toList();
            List<Transition> transitions = members.stream().map(EventView::transition).distinct().toList();
            boolean independent = members.size() >= 3 && members.stream().allMatch(event -> event.category() == Category.FILE_CHANGE)
                || members.stream().anyMatch(event ->
                Set.of(Category.COMMIT, Category.MERGE, Category.PULL_REQUEST, Category.ISSUE, Category.PROJECT_FACT, Category.AGENT_RESULT)
                    .contains(event.category()) && !supportPathOnly(event.paths())
                    && !languageService.supportingCommitLabel(event.label())
            ) || transitions.stream().anyMatch(STORY_BOUNDARIES::contains);
            if (!languageService.supporting(categories, paths, labels, transitions, independent)) continue;
            potentialSupportingIds.add(story.id());
        }
        for (StoryEnvelope envelope : ordered) {
            ChangeStory story = envelope.story();
            if (!potentialSupportingIds.contains(story.id())) continue;
            String target = nearestPrimary(story, ordered, eventsById, potentialSupportingIds);
            if (target != null) {
                supportingIds.add(story.id());
                attachments.put(story.id(), target);
            }
        }
        if (supportingIds.isEmpty()) return ordered;
        Map<String, ChangeStory> byId = new LinkedHashMap<>();
        ordered.forEach(item -> byId.put(item.story().id(), item.story()));
        for (Map.Entry<String, String> attachment : attachments.entrySet()) {
            ChangeStory support = byId.get(attachment.getKey());
            ChangeStory primary = byId.get(attachment.getValue());
            if (support == null || primary == null) continue;
            List<String> refs = new ArrayList<>(primary.supportingChangeRefs());
            if (!refs.contains(support.id())) refs.add(support.id());
            byId.put(primary.id(), withPresentation(primary, primary.humanTitle(), primary.oneSentenceSummary(),
                primary.beforeState(), primary.change(), primary.afterState(), "PRIMARY", "", refs,
                primary.presentationAuthority(), primary.presentationRevision(), primary.userCorrectionRefs(),
                primary.hiddenByDefault(), primary.pinned(), primary.mergedIntoStoryId(), primary.displayStatus(),
                primary.correctionConflicts()));
            byId.put(support.id(), withPresentation(support, support.humanTitle(), support.oneSentenceSummary(),
                support.beforeState(), support.change(), support.afterState(), "SUPPORTING", primary.id(),
                support.supportingChangeRefs(), support.presentationAuthority(), support.presentationRevision(),
                support.userCorrectionRefs(), true, support.pinned(), support.mergedIntoStoryId(), support.displayStatus(),
                support.correctionConflicts()));
        }
        return ordered.stream().map(item -> new StoryEnvelope(byId.get(item.story().id()), item.transitions())).toList();
    }

    /**
     * Fold repeated, non-boundary work into a nearby primary result. This is a
     * presentation compression only: every folded story keeps its stable ID,
     * event refs and Evidence and remains available through technical detail.
     * The lower bound is derived from the candidate volume so a long history
     * is never reduced to an arbitrary fixed number of cards.
     */
    private List<StoryEnvelope> compressRepetitivePrimaryStories(
        List<StoryEnvelope> input,
        Map<UUID, EventView> eventsById
    ) {
        int primaryCount = (int) input.stream().filter(value -> value.story().primary()).count();
        if (primaryCount < 80) return input;
        int readableBudget = Math.max(40, Math.min(120, (int) Math.ceil(Math.sqrt(primaryCount) * 4)));
        int remainingPrimary = primaryCount;
        List<StoryEnvelope> result = new ArrayList<>();
        Map<String, Integer> representatives = new LinkedHashMap<>();
        for (StoryEnvelope candidate : input) {
            ChangeStory story = candidate.story();
            if (!story.primary()) {
                result.add(candidate);
                continue;
            }
            remainingPrimary--;
            String family = presentationFamily(story);
            Integer representativeIndex = representatives.get(family);
            StoryEnvelope representative = representativeIndex == null ? null : result.get(representativeIndex);
            boolean boundary = candidate.transitions().stream().anyMatch(STORY_BOUNDARIES::contains);
            boolean canFold = representative != null
                && !boundary
                && story.supportingChangeRefs().isEmpty()
                && representative.transitions().stream().noneMatch(STORY_BOUNDARIES::contains)
                && representative.story().occurredTo() != null
                && story.occurredFrom() != null
                && Duration.between(representative.story().occurredTo(), story.occurredFrom()).compareTo(Duration.ofDays(45)) <= 0
                && Duration.between(representative.story().occurredTo(), story.occurredFrom()).compareTo(Duration.ofDays(-45)) >= 0
                && result.stream().filter(value -> value.story().primary()).count() + remainingPrimary >= readableBudget;
            if (!canFold) {
                representatives.put(family, result.size());
                result.add(candidate);
                continue;
            }
            ChangeStory primary = representative.story();
            List<String> supportingRefs = new ArrayList<>(primary.supportingChangeRefs());
            if (!supportingRefs.contains(story.id())) supportingRefs.add(story.id());
            result.set(representativeIndex, new StoryEnvelope(withPresentation(primary, primary.humanTitle(),
                primary.oneSentenceSummary(), primary.beforeState(), primary.change(), primary.afterState(), "PRIMARY", "",
                supportingRefs, primary.presentationAuthority(), primary.presentationRevision(), primary.userCorrectionRefs(),
                primary.hiddenByDefault(), primary.pinned(), primary.mergedIntoStoryId(), primary.displayStatus(),
                primary.correctionConflicts()), representative.transitions()));
            result.add(new StoryEnvelope(withPresentation(story, story.humanTitle(), story.oneSentenceSummary(),
                story.beforeState(), story.change(), story.afterState(), "SUPPORTING", primary.id(),
                story.supportingChangeRefs(), story.presentationAuthority(), story.presentationRevision(), story.userCorrectionRefs(),
                true, story.pinned(), story.mergedIntoStoryId(), story.displayStatus(), story.correctionConflicts()), candidate.transitions()));
        }
        return result;
    }

    private String presentationFamily(ChangeStory story) {
        String object = languageService.readableObject(
            story.primarySubjectKey(), story.technicalDetails(), story.affectedAreas()
        );
        if (object == null || object.isBlank() || Set.of(
            "项目核心结果", "项目材料", "项目阶段成果", "阶段成果记录", "项目成果", "项目成果记录"
        ).contains(object)) {
            // Internal grouping may retain the stable subject key even when the
            // first presentation layer deliberately collapses an unsafe/raw
            // identifier into a generic Chinese label.
            object = story.primarySubjectKey();
            if (object == null || object.isBlank()) {
                object = story.affectedAreas().stream().findFirst().orElse("project-material");
            }
        }
        return object.trim().toLowerCase(Locale.ROOT);
    }

    private String nearestPrimary(
        ChangeStory support,
        List<StoryEnvelope> candidates,
        Map<UUID, EventView> eventsById,
        Set<String> supportingCandidates
    ) {
        Set<String> supportCommits = support.eventRefs().stream().map(eventsById::get).filter(java.util.Objects::nonNull)
            .flatMap(event -> commitRefs(event).stream()).collect(LinkedHashSet::new, Set::add, Set::addAll);
        Set<String> supportTopics = presentationTopics(support);
        return candidates.stream()
            .map(StoryEnvelope::story)
            .filter(candidate -> !candidate.id().equals(support.id()))
            .filter(candidate -> !supportingCandidates.contains(candidate.id()))
            .filter(candidate -> candidate.primary())
            .filter(candidate -> {
                Set<String> commits = candidate.eventRefs().stream().map(eventsById::get).filter(java.util.Objects::nonNull)
                    .flatMap(event -> commitRefs(event).stream()).collect(LinkedHashSet::new, Set::add, Set::addAll);
                boolean sameCommit = !supportCommits.isEmpty() && commits.stream().anyMatch(supportCommits::contains);
                boolean close = !candidate.occurredTo().isBefore(support.occurredFrom().minus(Duration.ofDays(7)))
                    && !support.occurredTo().isBefore(candidate.occurredFrom().minus(Duration.ofDays(7)));
                boolean sameArea = candidate.affectedAreas().stream().anyMatch(support.affectedAreas()::contains);
                boolean sharedTopic = presentationTopics(candidate).stream().anyMatch(supportTopics::contains);
                return sameCommit || (close && (sameArea || sharedTopic));
            })
            .sorted(Comparator.comparing((ChangeStory value) -> {
                Set<String> commits = value.eventRefs().stream().map(eventsById::get).filter(java.util.Objects::nonNull)
                    .flatMap(event -> commitRefs(event).stream()).collect(LinkedHashSet::new, Set::add, Set::addAll);
                return !supportCommits.isEmpty() && commits.stream().anyMatch(supportCommits::contains) ? 0 : 1;
            }).thenComparing(value -> storyDistanceSeconds(support, value)).thenComparing(ChangeStory::id))
            .map(ChangeStory::id).findFirst().orElse(null);
    }

    private static Set<String> presentationTopics(ChangeStory story) {
        return Stream.concat(
                Stream.of(story.primarySubjectKey()),
                Stream.concat(story.technicalDetails().stream(), story.affectedAreas().stream())
            )
            .filter(value -> value != null && !value.isBlank())
            .flatMap(value -> java.util.Arrays.stream(value.toLowerCase(Locale.ROOT)
                .replace('\\', '/').split("[^\\p{L}\\p{N}]+")))
            .filter(value -> value.length() >= 3)
            .filter(value -> !Set.of(
                "test", "tests", "validation", "validate", "config", "configuration", "fixture", "fixtures",
                "json", "html", "java", "project", "result", "data", "page"
            ).contains(value))
            .collect(LinkedHashSet::new, Set::add, Set::addAll);
    }

    private static long storyDistanceSeconds(ChangeStory left, ChangeStory right) {
        if (left.occurredFrom() == null || right.occurredTo() == null) return Long.MAX_VALUE;
        long seconds = Duration.between(right.occurredTo(), left.occurredFrom()).getSeconds();
        return seconds == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(seconds);
    }

    private static boolean supportPathOnly(List<String> paths) {
        if (paths == null || paths.isEmpty()) return false;
        return paths.stream().allMatch(path -> {
            String lower = path == null ? "" : path.replace('\\', '/').toLowerCase(Locale.ROOT);
            return lower.contains("/test/") || lower.startsWith("test/") || lower.startsWith("tests/")
                || lower.contains("/.github/") || lower.startsWith(".github/") || lower.contains("/fixtures/")
                || lower.startsWith("fixtures/") || lower.contains("/config/") || lower.startsWith("config/")
                || lower.contains("/migration/") || lower.contains("/migrations/");
        });
    }

    private boolean hasRetryableWindowCheckpoint(UUID projectId) {
        return windowCheckpointRepository.findByProjectIdOrderByUpdatedAtAsc(projectId).stream()
            .anyMatch(checkpoint -> RETRYABLE_WINDOW_STATUSES.contains(checkpoint.getStatus()));
    }

    private boolean hasPendingWindowDiagnostics(ProjectHistorySnapshot snapshot) {
        if (snapshot == null || snapshot.getDiagnosticsJson() == null || snapshot.getDiagnosticsJson().isBlank()) return false;
        try {
            JsonNode root = objectMapper.readTree(snapshot.getDiagnosticsJson());
            if (root == null || !root.isObject()) return false;
            return root.path("modelUnprocessedWindowCount").asInt(0) > 0
                || root.path("modelUnprocessedStoryCount").asInt(0) > 0
                || root.path("pendingWindowCount").asInt(0) > 0
                || root.path("chapterSynthesisPendingCount").asInt(0) > 0
                || root.path("chapterSynthesisFailedCount").asInt(0) > 0;
        } catch (Exception ignored) {
            // An unreadable diagnostic must not turn an incomplete snapshot into
            // a false global cache hit.
            return true;
        }
    }

    private void carryForwardSnapshotDiagnostics(ProjectHistorySnapshot snapshot, Map<String, Object> target) {
        if (snapshot == null || target == null || snapshot.getDiagnosticsJson() == null) return;
        try {
            JsonNode previous = objectMapper.readTree(snapshot.getDiagnosticsJson());
            if (previous == null || !previous.isObject()) return;
            for (String field : List.of(
                "storyCount", "threadCount", "reconstructionMode", "reusedStoryCount", "recomputedStoryCount",
                "modelEnhancedStoryCount", "boundedDeterministicStoryCount", "eventConservation",
                "modelDeterministicTitleFallbackCount",
                "modelRejectedInvalidEvidenceRefCount", "modelRejectedCrossProjectRefCount",
                "modelRejectedUnsupportedClaimCount", "modelPromptCharacterCount", "modelPromptOmittedStoryCount",
                "modelPromptOmittedChapterCount", "modelWindowCount", "modelProcessedWindowCount",
                "modelUnprocessedWindowCount", "modelUnprocessedStoryCount", "modelWindowCacheHitCount",
                "modelCheckpointCount", "totalWindowCount", "succeededWindowCount", "failedWindowCount",
                "skippedWindowCount", "skippedStoryCount", "pendingWindowCount", "nextWindowOrdinal",
                "processedStoryCount", "unprocessedStoryCount"
                , "chapterSynthesisCount", "chapterSynthesisProcessedCount", "chapterSynthesisCacheHitCount",
                "chapterSynthesisFailedCount", "chapterSynthesisPendingCount", "chapterSynthesisPromptCharacterCount",
                "chapterSynthesisOmittedStoryCount", "chapterRepresentationPlanVersion", "chapterCount",
                "primaryStoryCount", "supportingStoryCount", "chapterPrimaryStoryCount",
                "chapterSupportingStoryCount", "representativeClusterCount",
                "dominantClusterCount", "selectedRepresentativeClusterCount", "representativePrimaryCoverage",
                "largestChapterStoryCount", "medianChapterStoryCount", "largeChapterCount", "chaptersNeedingSplit",
                "chaptersUsingDeterministicFallback", "chaptersUsingModelValidatedWording",
                "chaptersWithMinorClusterTitleRisk", "technicalLeakCount", "unsupportedClaimCount",
                "chapterOverlapCount", "orphanSupportingCount", "reasonWithoutEvidenceCount",
                "userDeclaredChapterMutationCount"
            )) {
                JsonNode value = previous.get(field);
                if (value != null && !value.isNull()) target.put(field, objectMapper.convertValue(value, Object.class));
            }
            target.put("previousModelStatus", previous.path("modelStatus").asText(""));
        } catch (Exception ignored) {
            target.put("previousDiagnosticsUnreadable", true);
        }
    }

    private String previousPresentationRevision(ProjectHistorySnapshot snapshot) {
        if (snapshot == null || snapshot.getDiagnosticsJson() == null) return "";
        try {
            JsonNode root = objectMapper.readTree(snapshot.getDiagnosticsJson());
            JsonNode value = root == null ? null : root.get("presentationRevision");
            return value == null || value.isNull() ? "" : value.asText("");
        } catch (Exception ignored) {
            return "";
        }
    }

    private void cleanupObsoleteWindowCheckpoints(UUID projectId, Set<String> activeCacheKeys) {
        Set<String> active = activeCacheKeys == null ? Set.of() : activeCacheKeys;
        List<ProjectHistoryWindowCheckpoint> obsolete = windowCheckpointRepository
            .findByProjectIdOrderByUpdatedAtAsc(projectId).stream()
            .filter(checkpoint -> !active.contains(checkpoint.getCacheKey()))
            .toList();
        if (!obsolete.isEmpty()) windowCheckpointRepository.deleteAll(obsolete);
    }

    private List<ChangeStory> previousStories(ProjectHistorySnapshot snapshot) {
        try {
            return objectMapper.readValue(
                snapshot.getStoriesJson() == null || snapshot.getStoriesJson().isBlank() ? "[]" : snapshot.getStoriesJson(),
                new TypeReference<List<ChangeStory>>() {}
            );
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    private StoryEnvelope envelope(ChangeStory story, Map<UUID, EventView> eventsById) {
        List<Transition> transitions = story.eventRefs().stream().map(eventsById::get)
            .filter(java.util.Objects::nonNull).map(EventView::transition).distinct().toList();
        return new StoryEnvelope(story, transitions);
    }

    private ChangeStory remapSubject(ChangeStory story, Map<String, String> aliases) {
        String subject = aliases.getOrDefault(story.primarySubjectKey(), story.primarySubjectKey());
        if (subject.equals(story.primarySubjectKey())) return story;
        return new ChangeStory(
            story.id(), subject, story.humanTitle(), story.oneSentenceSummary(), story.beforeState(), story.change(),
            story.afterState(), story.affectedAreas(), story.reason(), story.reasonEvidenceRefs(), story.laterOutcome(),
            story.conflicts(), story.unknowns(), story.occurredFrom(), story.occurredTo(), story.evidenceCount(),
            story.rawEventCount(), story.authority(), story.summaryStatus(), story.coverage(), story.limitations(),
            story.eventRefs(), story.evidenceRefs(), story.role(), story.primaryStoryId(), story.supportingChangeRefs(),
            story.technicalAtomRefs(), story.commitSummaries(), story.technicalDetails(), story.presentationAuthority(),
            story.presentationRevision(), story.automaticTitle(), story.automaticSummary(), story.userCorrectionRefs(),
            story.hiddenByDefault(), story.pinned(), story.mergedIntoStoryId(), story.displayStatus(),
            story.correctionConflicts(), story.claimAttribution()
        );
    }

    private ChangeStory withPresentation(
        ChangeStory original,
        String title,
        String summary,
        String before,
        String change,
        String after,
        String role,
        String primaryStoryId,
        List<String> supportingChangeRefs,
        String presentationAuthority,
        String presentationRevision,
        List<String> userCorrectionRefs,
        boolean hiddenByDefault,
        boolean pinned,
        String mergedIntoStoryId,
        String displayStatus,
        List<String> correctionConflicts
    ) {
        return new ChangeStory(
            original.id(), original.primarySubjectKey(), title, summary, before, change, after,
            original.affectedAreas(), original.reason(), original.reasonEvidenceRefs(), original.laterOutcome(),
            original.conflicts(), original.unknowns(), original.occurredFrom(), original.occurredTo(),
            original.evidenceCount(), original.rawEventCount(), original.authority(), original.summaryStatus(),
            original.coverage(), original.limitations(), original.eventRefs(), original.evidenceRefs(),
            role, primaryStoryId, supportingChangeRefs, original.technicalAtomRefs(), original.commitSummaries(),
            original.technicalDetails(), presentationAuthority, presentationRevision,
            original.automaticTitle(), original.automaticSummary(), userCorrectionRefs, hiddenByDefault, pinned,
            mergedIntoStoryId, displayStatus, correctionConflicts, original.claimAttribution()
        );
    }

    private Map<String, String> canonicalSubjectAliases(List<EventView> events) {
        Map<String, String> parents = new LinkedHashMap<>();
        events.stream().flatMap(event -> event.subjectKeys().stream()).forEach(key -> parents.putIfAbsent(key, key));
        for (EventView event : events) {
            if (event.category() != Category.FILE_CHANGE || !FILE_ALIAS_TRANSITIONS.contains(event.transition())) continue;
            List<String> keys = event.subjectKeys().stream().distinct().toList();
            for (int index = 1; index < keys.size(); index++) union(parents, keys.get(0), keys.get(index));
        }
        Map<String, String> aliases = new LinkedHashMap<>();
        parents.keySet().forEach(key -> aliases.put(key, find(parents, key)));
        return aliases;
    }

    private static void union(Map<String, String> parents, String left, String right) {
        parents.putIfAbsent(left, left);
        parents.putIfAbsent(right, right);
        String leftRoot = find(parents, left);
        String rightRoot = find(parents, right);
        if (leftRoot.equals(rightRoot)) return;
        String canonical = leftRoot.compareTo(rightRoot) <= 0 ? leftRoot : rightRoot;
        String alias = canonical.equals(leftRoot) ? rightRoot : leftRoot;
        parents.put(alias, canonical);
    }

    private static String find(Map<String, String> parents, String value) {
        String parent = parents.getOrDefault(value, value);
        if (parent.equals(value)) return value;
        String root = find(parents, parent);
        parents.put(value, root);
        return root;
    }

    private boolean newStory(List<EventView> current, EventView next) {
        EventView previous = current.get(current.size() - 1);
        if (sharesCommit(current, next)) return false;
        Duration gap = Duration.between(previous.occurredAt(), next.occurredAt());
        if (gap.compareTo(STORY_GAP) > 0 || current.size() >= STORY_EVENT_LIMIT) return true;
        if (STORY_BOUNDARIES.contains(previous.transition()) || STORY_BOUNDARIES.contains(next.transition())) return true;
        return next.category() == Category.TAG || previous.category() == Category.TAG;
    }

    private StoryEnvelope story(
        String subjectKey,
        List<EventView> input,
        boolean complete,
        Comparator<EventView> storyEventOrder
    ) {
        List<EventView> events = input.stream().distinct()
            .sorted(storyEventOrder).toList();
        Instant from = events.get(0).occurredAt();
        Instant to = events.get(events.size() - 1).occurredAt();
        List<Transition> transitions = storyTransitions(events);
        Transition outcome = primaryTransition(transitions);
        String subjectLabel = languageService.readableObject(
            subjectKey,
            events.stream().flatMap(event -> event.paths().stream()).distinct().limit(40).toList(),
            events.stream().map(EventView::label).filter(value -> value != null && !value.isBlank()).distinct().limit(10).toList()
        );
        List<UUID> eventRefs = events.stream().map(EventView::id).distinct().toList();
        List<String> evidence = events.stream().flatMap(event -> event.evidenceRefs().stream()).distinct().limit(100).toList();
        List<String> affectedAreas = new ArrayList<>();
        affectedAreas.add(subjectLabel);
        events.stream().flatMap(event -> event.paths().stream()).map(ProjectHistoryReconstructionService::area)
            .filter(value -> !value.isBlank()).distinct().limit(5).forEach(affectedAreas::add);
        List<String> labels = events.stream()
            .filter(event -> event.category() != Category.FILE_CHANGE)
            .map(EventView::label).filter(value -> !value.isBlank()).distinct().limit(3).toList();
        ProjectHistoryNarrativeEntailmentValidator.NarrativeEnvelope narrativeEnvelope = narrativeEnvelope(
            subjectKey, subjectLabel, outcome, events, hasReasonEligibleEvidence(events)
        );
        ProjectHistoryLanguageService.Presentation presentation = languageService.fallback(
            narrativeEnvelope.claimState(), outcome, subjectKey,
            events.stream().flatMap(event -> event.paths().stream()).distinct().limit(40).toList(), labels,
            transitions.stream().map(Enum::name).toList()
        );
        String change = presentation.change();
        String before = presentation.before();
        String after = presentation.after();
        List<String> conflicts = events.stream()
            .filter(event -> event.epistemicStatus() == ProjectFactEpistemicStatus.CONFLICTED)
            .map(event -> "来源对这项结果存在冲突，需在详情中核对。 ").distinct().limit(10).toList();
        boolean sourceStateUnknown = events.stream()
            .anyMatch(event -> event.epistemicStatus() == ProjectFactEpistemicStatus.UNKNOWN);
        List<String> unknowns = narrativeValidator.normalizeUnknowns("", sourceStateUnknown);
        List<String> limitations = events.stream().flatMap(event -> event.limitations().stream()).distinct().limit(20).toList();
        String id = "story-" + ProjectHistorySourceCollector.sha256(subjectKey + "|" + events.get(0).stableKey()).substring(0, 20);
        String humanTitle = presentation.title();
        String summary = presentation.summary();
        List<String> commitSummaries = events.stream()
            .filter(event -> event.category() == Category.COMMIT || event.category() == Category.MERGE)
            .map(event -> languageService.commitSummary(event.label(), event.transition(), event.paths()))
            .distinct().limit(12).toList();
        List<String> technicalDetails = events.stream().flatMap(event -> event.paths().stream())
            .filter(value -> value != null && !value.isBlank()).distinct().limit(30).toList();
        List<String> atomRefs = events.stream().map(EventView::stableKey).distinct().limit(100).toList();
        ChangeStory story = new ChangeStory(
            id, subjectKey, humanTitle, summary, before, change, after, List.copyOf(affectedAreas), "", List.of(), "",
            conflicts, List.copyOf(unknowns), from, to, evidence.size(), eventRefs.size(), "ENGINEERING_GROUPING",
            "DETERMINISTIC", complete ? "FULL_WITHIN_DISCOVERED_SOURCES" : "PARTIAL", limitations, eventRefs, evidence,
            "PRIMARY", "", List.of(), atomRefs, commitSummaries, technicalDetails, "AUTOMATIC", "",
            humanTitle, summary, List.of(), false, false, "", "ACTIVE", List.of(),
            claimAttribution(narrativeEnvelope)
        );
        return new StoryEnvelope(story, transitions);
    }

    /**
     * A Git commit's default MODIFIED value is envelope metadata. When the
     * same revision has file-level transitions, those transitions describe
     * the actual lifecycle and must define the readable sequence. Explicit
     * commit transitions such as REVERTED or REAPPLIED remain meaningful.
     */
    private static List<Transition> storyTransitions(List<EventView> events) {
        Set<String> revisionsWithFileEvents = events.stream()
            .filter(event -> event.category() == Category.FILE_CHANGE || event.category() == Category.DOCUMENT_VERSION)
            .map(EventView::sourceRevision).filter(value -> value != null && !value.isBlank())
            .collect(LinkedHashSet::new, Set::add, Set::addAll);
        Set<String> revisionsWithExplicitCommitTransition = events.stream()
            .filter(event -> event.category() == Category.COMMIT || event.category() == Category.MERGE)
            .filter(event -> event.transition() != Transition.MODIFIED
                && event.transition() != Transition.UNKNOWN_TRANSITION)
            .map(EventView::sourceRevision).filter(value -> value != null && !value.isBlank())
            .collect(LinkedHashSet::new, Set::add, Set::addAll);
        LinkedHashSet<Transition> result = new LinkedHashSet<>();
        for (EventView event : events) {
            boolean commitEnvelope = event.category() == Category.COMMIT || event.category() == Category.MERGE;
            if (commitEnvelope && event.transition() == Transition.MODIFIED
                && revisionsWithFileEvents.contains(event.sourceRevision())) {
                continue;
            }
            if (!commitEnvelope && event.transition() == Transition.MODIFIED
                && revisionsWithExplicitCommitTransition.contains(event.sourceRevision())) {
                continue;
            }
            result.add(event.transition());
        }
        return List.copyOf(result);
    }

    private List<EvolutionThread> threads(List<StoryEnvelope> envelopes) {
        Map<String, List<StoryEnvelope>> grouped = new LinkedHashMap<>();
        envelopes.forEach(envelope -> grouped.computeIfAbsent(envelope.story().primarySubjectKey(), ignored -> new ArrayList<>()).add(envelope));
        List<EvolutionThread> result = new ArrayList<>();
        for (Map.Entry<String, List<StoryEnvelope>> entry : grouped.entrySet()) {
            List<StoryEnvelope> ordered = entry.getValue().stream()
                .sorted(Comparator.comparing((StoryEnvelope envelope) -> envelope.story().occurredFrom())
                    .thenComparing(envelope -> envelope.story().id())).toList();
            List<String> transitions = ordered.stream().flatMap(envelope -> envelope.transitions().stream())
                .map(Enum::name).toList();
            List<String> unknowns = ordered.stream().flatMap(envelope -> envelope.story().unknowns().stream()).distinct().limit(20).toList();
            List<String> conflicts = ordered.stream().flatMap(envelope -> envelope.story().conflicts().stream()).distinct().limit(20).toList();
            int evidenceCount = (int) ordered.stream().flatMap(envelope -> envelope.story().evidenceRefs().stream()).distinct().count();
            String key = entry.getKey();
            result.add(new EvolutionThread(
                "thread-" + ProjectHistorySourceCollector.sha256(key).substring(0, 20), key,
                languageService.threadLabel(key), "PROJECT_SUBJECT",
                ordered.stream().map(envelope -> envelope.story().id()).toList(), transitions,
                ordered.get(ordered.size() - 1).story().afterState(),
                gaps(ordered), conflicts, unknowns, evidenceCount, null
            ));
        }
        return result.stream().sorted(Comparator.comparing(EvolutionThread::subjectLabel)).toList();
    }

    private List<ChangeStory> applyLaterOutcomes(List<StoryEnvelope> envelopes, List<EvolutionThread> threads) {
        Map<String, ChangeStory> byId = new LinkedHashMap<>();
        envelopes.forEach(envelope -> {
            ChangeStory story = envelope.story();
            byId.put(story.id(), copyStory(
                story, "", story.authority(), story.summaryStatus(), story.humanTitle(), story.oneSentenceSummary(),
                story.beforeState(), story.change(), story.afterState(), story.reason(), story.reasonEvidenceRefs(),
                story.conflicts(), story.unknowns()
            ));
        });
        for (EvolutionThread thread : threads) {
            for (int index = 0; index < thread.storyRefs().size() - 1; index++) {
                String id = thread.storyRefs().get(index);
                ChangeStory story = byId.get(id);
                ChangeStory later = byId.get(thread.storyRefs().get(index + 1));
                byId.put(id, copyStory(
                    story,
                    "随后于 " + date(later.occurredFrom()) + " 被继续变更：" + later.humanTitle(),
                    story.authority(), story.summaryStatus(), story.humanTitle(), story.oneSentenceSummary(),
                    story.beforeState(), story.change(), story.afterState(), story.reason(), story.reasonEvidenceRefs(),
                    story.conflicts(), story.unknowns()
                ));
            }
        }
        return byId.values().stream().sorted(Comparator.comparing(ChangeStory::occurredFrom).thenComparing(ChangeStory::id)).toList();
    }

    private List<HistoryChapter> chapters(List<ChangeStory> stories, List<EventView> events) {
        if (stories.isEmpty()) return List.of();
        Set<UUID> tagEventIds = events.stream().filter(event -> event.category() == Category.TAG).map(EventView::id)
            .collect(LinkedHashSet::new, Set::add, Set::addAll);
        List<List<ChangeStory>> groups = new ArrayList<>();
        List<ChangeStory> current = new ArrayList<>();
        List<String> currentSignals = new ArrayList<>();
        Map<String, List<String>> signals = new LinkedHashMap<>();
        int primaryCount = (int) stories.stream().filter(ChangeStory::primary).count();
        // Density is adaptive: supporting changes do not consume the same
        // first-layer budget as primary outcomes, while very large histories
        // still get bounded, sequential chapters.
        int storyLimit = Math.max(12, Math.min(48, (int) Math.ceil(Math.sqrt(Math.max(1, primaryCount)) * 4)));
        int eventLimit = Math.max(120, Math.min(720, stories.stream().mapToInt(ChangeStory::rawEventCount).sum() / Math.max(1, Math.min(8, primaryCount))));
        Duration spanLimit = primaryCount > 160 ? Duration.ofDays(45) : primaryCount > 80 ? Duration.ofDays(75) : Duration.ofDays(120);
        int currentEventCount = 0;
        for (ChangeStory story : stories) {
            if (!current.isEmpty()) {
                ChangeStory previous = current.get(current.size() - 1);
                Duration gap = Duration.between(previous.occurredTo(), story.occurredFrom());
                boolean boundary = gap.compareTo(CHAPTER_GAP) > 0
                    || current.stream().filter(ChangeStory::primary).count() >= storyLimit
                    || currentEventCount + story.rawEventCount() > eventLimit
                    || Duration.between(current.get(0).occurredFrom(), story.occurredTo()).compareTo(spanLimit) > 0
                    || storyContainsEvent(story, tagEventIds);
                if (boundary) {
                    groups.add(current);
                    signals.put(current.get(0).id(), List.copyOf(currentSignals));
                    current = new ArrayList<>();
                    currentSignals = new ArrayList<>();
                    if (gap.compareTo(CHAPTER_GAP) > 0) currentSignals.add("TIME_GAP_" + gap.toDays() + "_DAYS");
                    else if (storyContainsEvent(story, tagEventIds)) currentSignals.add("TAG_BOUNDARY");
                    else currentSignals.add("DENSITY_BOUNDARY");
                    currentEventCount = 0;
                }
            }
            if (current.isEmpty() && groups.isEmpty()) currentSignals.add("EARLIEST_DISCOVERED_EVENT");
            current.add(story);
            currentEventCount += story.rawEventCount();
        }
        if (!current.isEmpty()) {
            groups.add(current);
            signals.put(current.get(0).id(), List.copyOf(currentSignals));
        }
        groups = splitIndependentCommitOutcomes(groups, events, signals);
        groups = splitChapterRepresentationBoundaries(groups, signals);
        List<HistoryChapter> result = new ArrayList<>();
        for (List<ChangeStory> group : groups) {
            Instant from = group.get(0).occurredFrom();
            Instant to = group.get(group.size() - 1).occurredTo();
            ProjectHistoryChapterRepresentationPlanner.Plan representation = chapterRepresentationPlanner.plan(group);
            List<String> outcomes = representation.representativeOutcomes();
            if (outcomes.isEmpty()) {
                outcomes = group.stream().filter(ChangeStory::primary).map(ChangeStory::humanTitle)
                    .filter(value -> value != null && !value.isBlank()).distinct().limit(4).toList();
            }
            List<Transition> transitions = group.stream().flatMap(story -> story.eventRefs().stream()
                .map(id -> events.stream().filter(event -> event.id().equals(id)).findFirst().orElse(null)))
                .filter(java.util.Objects::nonNull).map(EventView::transition).distinct().toList();
            Set<UUID> rawEvents = new LinkedHashSet<>();
            group.forEach(story -> rawEvents.addAll(story.eventRefs()));
            String id = "chapter-" + ProjectHistorySourceCollector.sha256(group.get(0).id()).substring(0, 20);
            int titleClusterCount = Math.max(1, representation.dominantClusterCount());
            String title = languageService.chapterTitle(outcomes, transitions, from, to, titleClusterCount);
            int chapterPrimaryCount = (int) group.stream().filter(ChangeStory::primary).count();
            int chapterSupportingCount = Math.max(0, group.size() - chapterPrimaryCount);
            String summary = languageService.chapterSummary(
                outcomes, chapterPrimaryCount, chapterSupportingCount,
                Math.max(1, representation.selectedClusters().size())
            );
            List<String> limitations = new ArrayList<>();
            if (representation.coherenceRisk()) {
                limitations.add("当前来源未提供足够强的阶段边界，已在最小安全粒度内保守保留多个代表成果。");
            }
            if (representation.representativePrimaryCoverage() < 0.60) {
                limitations.add("代表成果未覆盖全部主要变化，完整成员仍可在篇章详情中核对。");
            }
            result.add(new HistoryChapter(
                id, title, summary, from, to, signals.getOrDefault(group.get(0).id(), List.of()),
                group.stream().map(ChangeStory::id).toList(), group.size(), rawEvents.size(),
                "ENGINEERING_REPRESENTATION_PLAN", "FULL_WITHIN_DISCOVERED_SOURCES", List.copyOf(limitations)
            ));
        }
        return result;
    }

    private List<List<ChangeStory>> splitChapterRepresentationBoundaries(
        List<List<ChangeStory>> groups,
        Map<String, List<String>> signals
    ) {
        List<List<ChangeStory>> result = new ArrayList<>();
        for (List<ChangeStory> group : groups) {
            List<List<ChangeStory>> represented = chapterRepresentationPlanner.split(group);
            List<String> originalSignals = signals.getOrDefault(group.get(0).id(), List.of());
            for (int index = 0; index < represented.size(); index++) {
                List<ChangeStory> members = represented.get(index);
                result.add(members);
                if (index == 0) {
                    signals.put(members.get(0).id(), originalSignals);
                } else {
                    signals.put(members.get(0).id(), List.of("REPRESENTATION_BOUNDARY"));
                }
            }
        }
        return List.copyOf(result);
    }

    /**
     * A generic commit can touch several unrelated outcomes. Story ownership is
     * already fixed at this point, so split only when every Primary shares one
     * commit and each Supporting story has an explicit owner.
     */
    private List<List<ChangeStory>> splitIndependentCommitOutcomes(
        List<List<ChangeStory>> groups,
        List<EventView> events,
        Map<String, List<String>> signals
    ) {
        Map<UUID, EventView> eventsById = events.stream().collect(
            LinkedHashMap::new, (map, event) -> map.put(event.id(), event), Map::putAll
        );
        List<List<ChangeStory>> result = new ArrayList<>();
        for (List<ChangeStory> group : groups) {
            List<ChangeStory> primaries = group.stream().filter(ChangeStory::primary).toList();
            if (primaries.size() < 2 || !shareOneCommit(primaries, eventsById)) {
                result.add(group);
                continue;
            }
            Map<String, List<ChangeStory>> bySubject = new LinkedHashMap<>();
            Map<String, String> primarySubjects = new LinkedHashMap<>();
            for (ChangeStory primary : primaries) {
                String subject = primary.primarySubjectKey();
                bySubject.computeIfAbsent(subject, ignored -> new ArrayList<>()).add(primary);
                primarySubjects.put(primary.id(), subject);
            }
            if (bySubject.size() < 2) {
                result.add(group);
                continue;
            }
            boolean completeOwnership = true;
            for (ChangeStory story : group) {
                if (story.primary()) continue;
                String subject = primarySubjects.get(story.primaryStoryId());
                if (subject == null) {
                    subject = primaries.stream().filter(primary -> primary.supportingChangeRefs().contains(story.id()))
                        .map(ChangeStory::primarySubjectKey).findFirst().orElse(null);
                }
                if (subject == null) {
                    completeOwnership = false;
                    break;
                }
                bySubject.get(subject).add(story);
            }
            if (!completeOwnership) {
                result.add(group);
                continue;
            }
            List<String> originalSignals = signals.getOrDefault(group.get(0).id(), List.of());
            int index = 0;
            for (List<ChangeStory> members : bySubject.values()) {
                List<ChangeStory> orderedMembers = members.stream()
                    .sorted(Comparator.comparing(ChangeStory::occurredFrom).thenComparing(ChangeStory::id)).toList();
                result.add(orderedMembers);
                signals.put(
                    orderedMembers.get(0).id(),
                    index++ == 0 ? originalSignals : List.of("INDEPENDENT_OUTCOME_BOUNDARY")
                );
            }
        }
        return result;
    }

    private static boolean shareOneCommit(List<ChangeStory> stories, Map<UUID, EventView> eventsById) {
        Set<String> shared = null;
        for (ChangeStory story : stories) {
            Set<String> refs = story.eventRefs().stream().map(eventsById::get)
                .filter(java.util.Objects::nonNull).flatMap(event -> commitRefs(event).stream())
                .collect(LinkedHashSet::new, Set::add, Set::addAll);
            if (refs.isEmpty()) return false;
            if (shared == null) shared = new LinkedHashSet<>(refs);
            else shared.retainAll(refs);
            if (shared.isEmpty()) return false;
        }
        return shared != null && !shared.isEmpty();
    }

    private ModelResult enhanceWithModel(
        UUID userId,
        UUID projectId,
        DeterministicResult deterministic,
        List<ProjectHistoryEvent> events,
        List<ModelGatewayService.ModelCallDiagnostics> diagnostics,
        HistoryProgress progress
    ) {
        SnapshotResult base = deterministic.result();
        if (base.stories().isEmpty()) return ModelResult.notUsed(base, "NOT_REQUIRED", 0);
        if (events.stream().noneMatch(event -> event.getScope() == ProjectHistoryEvent.Scope.HISTORICAL)) {
            return ModelResult.notUsed(base, "NOT_REQUIRED_CURRENT_STATE_ONLY", base.stories().size());
        }
        Set<String> recomputedStories = deterministic.recomputedStoryIds();
        Set<String> eligibleStories = semanticModelCandidates(base.stories(), recomputedStories);
        int deterministicOnlyStoryCount = Math.max(0, recomputedStories.size() - eligibleStories.size());
        if (eligibleStories.isEmpty()) {
            return ModelResult.notUsed(base, "REUSED_WITHOUT_MODEL", deterministicOnlyStoryCount);
        }
        AiProvider provider = providerRepository.findFirstByUserIdAndDefaultEnabledTrueOrderByUpdatedAtDesc(userId).orElse(null);
        if (provider == null) return ModelResult.notUsed(base, "NOT_CONFIGURED_DETERMINISTIC", recomputedStories.size());
        Map<UUID, EventView> eventsById = new LinkedHashMap<>();
        events.stream().map(this::view).forEach(event -> eventsById.put(event.id(), event));
        Map<String, List<String>> reasonEvidence = reasonEligibleEvidence(base, eventsById, eligibleStories);
        ModelBatchPlan batchPlan = modelBatches(projectId, base, eligibleStories, events, provider, reasonEvidence);
        List<ModelBatch> batches = batchPlan.batches();
        if (batches.isEmpty()) return ModelResult.notUsed(base, "BOUNDED_DETERMINISTIC", eligibleStories.size());

        SnapshotResult current = base;
        Set<String> enhancedStories = new LinkedHashSet<>();
        Set<String> enhancedChapters = new LinkedHashSet<>();
        Set<String> succeededWindows = new LinkedHashSet<>();
        Set<String> terminalSkippedWindows = new LinkedHashSet<>();
        boolean used = false;
        boolean failed = false;
        boolean systemicFailure = false;
        String failureSummary = "";
        int rejectedInvalidEvidence = 0;
        int rejectedCrossProject = 0;
        int rejectedUnsupportedClaim = 0;
        int promptCharacterCount = 0;
        int promptOmittedStories = 0;
        int promptOmittedChapters = 0;
        int windowCacheHits = 0;
        int checkpointCount = 0;
        int processedWindowCount = 0;

        // Restore every valid successful window first. Cache hits do not consume
        // the per-refresh execution budget, which is what makes continuation
        // deterministic when a project has more than MAX_MODEL_WINDOWS windows.
        for (ModelBatch batch : batches) {
            ProjectHistoryWindowCheckpoint checkpoint = windowCheckpointRepository
                .findByProjectIdAndCacheKey(projectId, batch.cacheKey()).orElse(null);
            if (checkpoint == null || !batch.sourceFingerprint().equals(checkpoint.getSourceFingerprint())) continue;
            if ("SUCCEEDED".equals(checkpoint.getStatus())) {
                SnapshotResult cached = readCachedWindow(checkpoint, batch);
                if (cached != null) {
                    current = mergeWindowResult(current, cached, batch);
                    succeededWindows.add(batch.identity());
                    enhancedStories.addAll(batch.storyIds());
                    cached.chapters().forEach(chapter -> enhancedChapters.add(chapter.id()));
                    used = true;
                    windowCacheHits++;
                    checkpointCount++;
                    processedWindowCount++;
                }
            } else if ("SKIPPED_OVERSIZE".equals(checkpoint.getStatus())) {
                terminalSkippedWindows.add(batch.identity());
                checkpointCount++;
            }
        }

        List<ModelBatch> pending = batches.stream()
            .filter(batch -> !succeededWindows.contains(batch.identity())
                && !terminalSkippedWindows.contains(batch.identity()))
            .limit(MAX_MODEL_WINDOWS)
            .toList();
        boolean incomplete = batches.stream().anyMatch(batch ->
            !succeededWindows.contains(batch.identity()) && !terminalSkippedWindows.contains(batch.identity()));
        for (ModelBatch batch : pending) {
            ProjectHistoryWindowCheckpointService.Attempt attempt = windowCheckpointService.begin(
                projectId, batch.identity(), batch.cacheKey(), batch.sourceFingerprint(),
                batch.storyIds().size(), batch.eventIds().size()
            );
            checkpointCount++;
            if (!attempt.claimed()) {
                incomplete = true;
                failureSummary = "语义窗口正在由另一刷新任务处理，本次未重复调用模型。";
                continue;
            }
            try {
                if (batch.promptOverflow()) {
                    String summary = "单个变化故事超过安全 Prompt 记录上限，已保留确定性结果并停止重复重试。";
                    boolean stored = windowCheckpointService.skipOversize(
                        attempt, summary, json(Map.of("status", "SKIPPED_OVERSIZE", "terminal", true))
                    );
                    if (stored) terminalSkippedWindows.add(batch.identity());
                    failed = true;
                    incomplete = true;
                    failureSummary = summary;
                    continue;
                }
                ProjectHistoryPromptBuilder.PromptBuildResult prompt = modelPrompt(
                    current, events, batch.storyIds(), reasonEvidence
                );
                promptCharacterCount += prompt.promptCharacterCount();
                promptOmittedStories += prompt.omittedStoryCount();
                promptOmittedChapters += prompt.omittedChapterCount();
                if (!prompt.includedStoryIds().containsAll(batch.storyIds())) {
                    // This should only be reachable when a provider-independent
                    // input changes between planning and execution. Do not save a
                    // partial semantic result; mark it terminal and continue.
                    String summary = "语义窗口无法完整进入有界 Prompt，已保留确定性结果。";
                    boolean stored = windowCheckpointService.skipOversize(attempt, summary, json(Map.of(
                        "status", "SKIPPED_OVERSIZE", "terminal", true,
                        "includedStoryCount", prompt.includedStoryIds().size(), "windowStoryCount", batch.storyIds().size()
                    )));
                    if (stored) terminalSkippedWindows.add(batch.identity());
                    failed = true;
                    incomplete = true;
                    failureSummary = summary;
                    continue;
                }
                progress.update("HISTORY_MODEL_SYNTHESIS", "正在对已确定成员和 Evidence 的变化故事做有界语义归纳");
                Set<String> includedChapters = prompt.includedChapterIds();
                SnapshotResult promptBase = current;
                ValidatedHistoryResponse<SnapshotResult> validated = callWithValidationRepair(
                    provider, prompt.prompt(), ModelTaskType.PROJECT_HISTORY_SYNTHESIS,
                    root -> parseModel(
                        root, promptBase, prompt.includedStoryIds(), includedChapters, reasonEvidence, eventsById
                    ),
                    diagnostics
                );
                ModelGatewayService.StructuredModelResponse response = validated.response();
                SnapshotResult parsed = validated.value();
                ModelBatch effective = batch.withChapterIds(includedChapters);
                boolean stored = windowCheckpointService.succeed(
                    attempt, writeWindowResult(parsed, effective), response.diagnostics().requestCount(),
                    safeCheckpointDiagnostics(response.diagnostics())
                );
                if (!stored) {
                    incomplete = true;
                    failureSummary = "语义窗口 attempt 已被更新，本次结果未覆盖较新的 checkpoint。";
                    continue;
                }
                current = parsed;
                enhancedStories.addAll(prompt.includedStoryIds());
                enhancedChapters.addAll(includedChapters);
                succeededWindows.add(batch.identity());
                used = true;
                processedWindowCount++;
            } catch (CancellationException exception) {
                incomplete = true;
                windowCheckpointService.cancel(
                    attempt, "窗口语义归纳已取消，保留确定性结果。", "{\"status\":\"CANCELLED\"}"
                );
                throw exception;
            } catch (HistoryValidationException exception) {
                failed = true;
                incomplete = true;
                rejectedInvalidEvidence += exception.kind() == ValidationKind.INVALID_EVIDENCE ? 1 : 0;
                rejectedCrossProject += exception.kind() == ValidationKind.CROSS_PROJECT_REFERENCE ? 1 : 0;
                rejectedUnsupportedClaim += exception.kind() == ValidationKind.UNSUPPORTED_CLAIM ? 1 : 0;
                failureSummary = "模型归纳未通过结构、ID、Evidence 或安全校验，已保留确定性历程：" + safeError(exception);
                windowCheckpointService.fail(
                    attempt, failureSummary, failureCheckpointDiagnostics("WINDOW", exception)
                );
                // A malformed response is local to this bounded window. Keep
                // processing independent windows; a later refresh retries only
                // this failed checkpoint.
            } catch (Exception exception) {
                failed = true;
                incomplete = true;
                failureSummary = "模型归纳调用失败，已保留确定性历程：" + safeError(exception);
                windowCheckpointService.fail(
                    attempt, failureSummary, failureCheckpointDiagnostics("WINDOW", exception)
                );
                if (systemicProviderFailure(exception)) {
                    systemicFailure = true;
                    break;
                }
            }
        }

        boolean storyStageComplete = batches.stream().allMatch(batch ->
            succeededWindows.contains(batch.identity()) || terminalSkippedWindows.contains(batch.identity()));
        ChapterSynthesisResult chapterSynthesis = storyStageComplete
            ? enhanceLargeChapters(
                projectId, current, enhancedStories, enhancedChapters, provider, diagnostics, progress,
                !systemicFailure
            )
            : ChapterSynthesisResult.notRequired(current);
        current = chapterSynthesis.result();
        used = used || chapterSynthesis.used();
        failed = failed || chapterSynthesis.failed();
        systemicFailure = systemicFailure || chapterSynthesis.systemicFailure();
        incomplete = incomplete || chapterSynthesis.incomplete();
        promptCharacterCount += chapterSynthesis.promptCharacterCount();
        promptOmittedStories += chapterSynthesis.omittedStoryCount();
        checkpointCount += chapterSynthesis.checkpointCount();
        if (!chapterSynthesis.failureSummary().isBlank()) failureSummary = chapterSynthesis.failureSummary();

        int succeededWindowCount = succeededWindows.size();
        int skippedWindowCount = terminalSkippedWindows.size();
        Set<String> terminalSkippedStoryIds = batches.stream()
            .filter(batch -> terminalSkippedWindows.contains(batch.identity()))
            .flatMap(batch -> batch.storyIds().stream())
            .collect(LinkedHashSet::new, Set::add, Set::addAll);
        Set<String> retryableStoryIds = batches.stream()
            .filter(batch -> !succeededWindows.contains(batch.identity()) && !terminalSkippedWindows.contains(batch.identity()))
            .flatMap(batch -> batch.storyIds().stream())
            .collect(LinkedHashSet::new, Set::add, Set::addAll);
        Set<String> activeCheckpointCacheKeys = batches.stream()
            .map(ModelBatch::cacheKey)
            .collect(LinkedHashSet::new, Set::add, Set::addAll);
        activeCheckpointCacheKeys.addAll(chapterSynthesis.activeCheckpointCacheKeys());
        int failedWindowCount = windowCheckpointService.summarize(projectId, activeCheckpointCacheKeys).count("FAILED");
        int unprocessedWindowCount = Math.max(0, batches.size() - succeededWindowCount - skippedWindowCount);
        int pendingWindowCount = Math.max(0, unprocessedWindowCount - failedWindowCount);
        int boundedStories = deterministicOnlyStoryCount + Math.max(0, eligibleStories.size() - enhancedStories.size());
        int unprocessedStoryCount = retryableStoryIds.size();
        int nextWindowOrdinal = batches.stream()
            .filter(batch -> !succeededWindows.contains(batch.identity()) && !terminalSkippedWindows.contains(batch.identity()))
            .map(batch -> windowOrdinal(batch.identity()))
            .min(Integer::compareTo).orElse(-1);
        String status;
        if (failed && used) status = systemicFailure ? "MODEL_PARTIAL_FALLBACK_PROVIDER_STOPPED" : "MODEL_PARTIAL_FALLBACK_DETERMINISTIC";
        else if (failed) status = systemicFailure ? "MODEL_FALLBACK_PROVIDER_STOPPED" : "MODEL_FALLBACK_DETERMINISTIC";
        else if (!used && unprocessedWindowCount > 0) status = "BOUNDED_DETERMINISTIC_PARTIAL";
        else if (!used && skippedWindowCount > 0) status = "BOUNDED_DETERMINISTIC_SKIPPED_OVERSIZE";
        else if (!used) status = "BOUNDED_DETERMINISTIC";
        else if (unprocessedWindowCount > 0 || skippedWindowCount > 0 || chapterSynthesis.incomplete()) {
            status = "MODEL_VALIDATED_PARTIAL_BOUNDED";
        }
        else status = deterministic.reusedStoryCount() > 0 ? "MODEL_VALIDATED_INCREMENTAL" : "MODEL_VALIDATED";
        return new ModelResult(
            current, used, failed, status, failureSummary, enhancedStories.size(), boundedStories,
            rejectedInvalidEvidence, rejectedCrossProject, rejectedUnsupportedClaim,
            promptCharacterCount, promptOmittedStories, promptOmittedChapters,
            batches.size(), processedWindowCount, unprocessedWindowCount, unprocessedStoryCount,
            windowCacheHits, checkpointCount, unprocessedWindowCount > 0 || skippedWindowCount > 0,
            succeededWindowCount, failedWindowCount, skippedWindowCount, terminalSkippedStoryIds.size(),
            pendingWindowCount, nextWindowOrdinal,
            activeCheckpointCacheKeys,
            chapterSynthesis.candidateCount(), chapterSynthesis.processedCount(), chapterSynthesis.cacheHitCount(),
            chapterSynthesis.failedCount(), chapterSynthesis.pendingCount(), chapterSynthesis.promptCharacterCount(),
            chapterSynthesis.omittedStoryCount()
        );
    }

    private ChapterSynthesisResult enhanceLargeChapters(
        UUID projectId,
        SnapshotResult input,
        Set<String> enhancedStoryIds,
        Set<String> enhancedChapterIds,
        AiProvider provider,
        List<ModelGatewayService.ModelCallDiagnostics> diagnostics,
        HistoryProgress progress,
        boolean allowCalls
    ) {
        Set<String> primaryStoryIds = input.stories().stream().filter(ChangeStory::primary).map(ChangeStory::id)
            .collect(LinkedHashSet::new, Set::add, Set::addAll);
        List<ChapterBatch> batches = input.chapters().stream()
            .filter(chapter -> !chapter.hiddenByDefault() && !chapter.userDeclared())
            .filter(chapter -> !ProjectHistoryCorrectionService.USER_DECLARED_PRESENTATION.equals(chapter.presentationAuthority()))
            .filter(chapter -> !enhancedChapterIds.contains(chapter.id()))
            .filter(chapter -> chapter.storyRefs().size() > 1)
            .filter(chapter -> {
                Set<String> chapterPrimaryIds = chapter.storyRefs().stream().filter(primaryStoryIds::contains)
                    .collect(LinkedHashSet::new, Set::add, Set::addAll);
                return !chapterPrimaryIds.isEmpty() && enhancedStoryIds.containsAll(chapterPrimaryIds);
            })
            .map(chapter -> chapterBatch(input, chapter))
            .toList();
        if (batches.isEmpty()) return ChapterSynthesisResult.notRequired(input);

        SnapshotResult current = input;
        Set<String> succeeded = new LinkedHashSet<>();
        Set<String> terminalSkipped = new LinkedHashSet<>();
        Set<String> activeKeys = batches.stream().map(ChapterBatch::cacheKey)
            .collect(LinkedHashSet::new, Set::add, Set::addAll);
        boolean used = false;
        boolean systemicFailure = false;
        String failureSummary = "";
        int cacheHits = 0;
        int checkpointCount = 0;
        int promptCharacters = 0;
        int omittedStories = 0;

        for (ChapterBatch batch : batches) {
            ProjectHistoryWindowCheckpoint checkpoint = windowCheckpointRepository
                .findByProjectIdAndCacheKey(projectId, batch.cacheKey()).orElse(null);
            if (checkpoint == null || !batch.sourceFingerprint().equals(checkpoint.getSourceFingerprint())) continue;
            if ("SUCCEEDED".equals(checkpoint.getStatus())) {
                HistoryChapter cached = readCachedChapter(checkpoint, current, batch);
                if (cached != null) {
                    current = replaceChapter(current, cached);
                    succeeded.add(batch.identity());
                    used = true;
                    cacheHits++;
                    checkpointCount++;
                }
            } else if ("SKIPPED_OVERSIZE".equals(checkpoint.getStatus())) {
                terminalSkipped.add(batch.identity());
                checkpointCount++;
            }
        }

        if (allowCalls) {
            List<ChapterBatch> pending = batches.stream()
                .filter(batch -> !succeeded.contains(batch.identity()) && !terminalSkipped.contains(batch.identity()))
                .limit(MAX_CHAPTER_SYNTHESIS_WINDOWS).toList();
            for (ChapterBatch batch : pending) {
                ProjectHistoryWindowCheckpointService.Attempt attempt = windowCheckpointService.begin(
                    projectId, batch.identity(), batch.cacheKey(), batch.sourceFingerprint(), batch.storyIds().size(), 0
                );
                checkpointCount++;
                if (!attempt.claimed()) {
                    failureSummary = "篇章二阶段归纳正在由另一刷新任务处理，本次未重复调用模型。";
                    continue;
                }
                try {
                    HistoryChapter chapter = current.chapters().stream()
                        .filter(value -> value.id().equals(batch.chapterId())).findFirst().orElseThrow();
                    ProjectHistoryChapterRepresentationPlanner.Plan representation = chapterRepresentationPlan(current, chapter);
                    ProjectHistoryPromptBuilder.ChapterSynthesisBuildResult prompt = chapterPrompt(current, chapter);
                    promptCharacters += prompt.promptCharacterCount();
                    omittedStories += prompt.omittedStoryCount();
                    progress.update("HISTORY_CHAPTER_SYNTHESIS", "正在使用已校验的变化故事摘要归纳大篇章");
                    ValidatedHistoryResponse<HistoryChapter> validated = callWithValidationRepair(
                        provider, prompt.prompt(), ModelTaskType.PROJECT_HISTORY_CHAPTER_SYNTHESIS,
                        root -> parseChapterSynthesis(root, chapter, representation), diagnostics
                    );
                    ModelGatewayService.StructuredModelResponse response = validated.response();
                    HistoryChapter enhanced = validated.value();
                    boolean stored = windowCheckpointService.succeed(
                        attempt, writeChapterResult(enhanced), response.diagnostics().requestCount(),
                        safeCheckpointDiagnostics(response.diagnostics())
                    );
                    if (!stored) {
                        failureSummary = "篇章归纳 attempt 已被更新，本次结果未覆盖较新的 checkpoint。";
                        continue;
                    }
                    current = replaceChapter(current, enhanced);
                    succeeded.add(batch.identity());
                    used = true;
                } catch (CancellationException exception) {
                    windowCheckpointService.cancel(
                        attempt, "篇章二阶段归纳已取消，保留确定性标题。", "{\"status\":\"CANCELLED\",\"scope\":\"CHAPTER\"}"
                    );
                    throw exception;
                } catch (HistoryValidationException exception) {
                    failureSummary = "篇章二阶段归纳未通过结构、ID 或安全校验，已保留确定性标题：" + safeError(exception);
                    windowCheckpointService.fail(
                        attempt, failureSummary, failureCheckpointDiagnostics("CHAPTER", exception)
                    );
                } catch (Exception exception) {
                    failureSummary = "篇章二阶段归纳调用失败，已保留确定性标题：" + safeError(exception);
                    windowCheckpointService.fail(
                        attempt, failureSummary, failureCheckpointDiagnostics("CHAPTER", exception)
                    );
                    if (systemicProviderFailure(exception)) {
                        systemicFailure = true;
                        break;
                    }
                }
            }
        }

        int failedCount = windowCheckpointService.summarize(projectId, activeKeys).count("FAILED");
        int unresolved = Math.max(0, batches.size() - succeeded.size() - terminalSkipped.size());
        int pendingCount = Math.max(0, unresolved - failedCount);
        return new ChapterSynthesisResult(
            current, used, failedCount > 0, systemicFailure, unresolved > 0 || !terminalSkipped.isEmpty(),
            failureSummary, batches.size(), succeeded.size(), cacheHits, failedCount, pendingCount,
            checkpointCount, promptCharacters, omittedStories, activeKeys
        );
    }

    /** Restore only validated story/chapter presentation for a cache hit. */
    private SnapshotResult readCachedWindow(ProjectHistoryWindowCheckpoint checkpoint, ModelBatch batch) {
        try {
            JsonNode root = objectMapper.readTree(checkpoint.getValidatedResultJson());
            if (root == null || !root.isObject()) return null;
            List<ChangeStory> stories = objectMapper.convertValue(root.path("stories"), new TypeReference<List<ChangeStory>>() { });
            List<HistoryChapter> chapters = objectMapper.convertValue(root.path("chapters"), new TypeReference<List<HistoryChapter>>() { });
            if (stories.stream().anyMatch(value -> value == null || value.id() == null)
                || chapters.stream().anyMatch(value -> value == null || value.id() == null)) return null;
            Set<String> cachedStoryIds = stories.stream().map(ChangeStory::id)
                .collect(LinkedHashSet::new, Set::add, Set::addAll);
            Set<String> cachedChapterIds = chapters.stream().map(HistoryChapter::id)
                .collect(LinkedHashSet::new, Set::add, Set::addAll);
            // A checkpoint is reusable only when it covers the complete planned
            // window. Partial prompt packing or a hand-edited row must never be
            // mistaken for a successful semantic pass.
            if (cachedStoryIds.size() != stories.size() || cachedChapterIds.size() != chapters.size()) return null;
            if (!cachedStoryIds.equals(batch.storyIds())) return null;
            List<HistoryChapter> reusableChapters = chapters.stream()
                .filter(chapter -> batch.chapterIds().contains(chapter.id()))
                .toList();
            return new SnapshotResult(reusableChapters, stories, List.of());
        } catch (Exception ignored) {
            return null;
        }
    }

    private SnapshotResult mergeWindowResult(SnapshotResult current, SnapshotResult cached, ModelBatch batch) {
        Map<String, ChangeStory> stories = new LinkedHashMap<>();
        current.stories().forEach(value -> stories.put(value.id(), value));
        cached.stories().forEach(value -> {
            ChangeStory currentStory = stories.get(value.id());
            if (batch.storyIds().contains(value.id()) && currentStory != null) {
                // Checkpoints cache validated wording, not structural truth. A later
                // bounded refresh may rebuild the same stable story ID with newer
                // time, role, membership or Evidence fields, so restore only the
                // presentation fields the model is allowed to produce.
                stories.put(value.id(), copyStory(
                    currentStory,
                    currentStory.laterOutcome(),
                    "INFERRED_NON_AUTHORITATIVE",
                    value.summaryStatus() == null || value.summaryStatus().isBlank()
                        ? "MODEL_VALIDATED" : value.summaryStatus(),
                    value.humanTitle(),
                    value.oneSentenceSummary(),
                    value.beforeState(),
                    value.change(),
                    value.afterState(),
                    value.reason(),
                    value.reasonEvidenceRefs(),
                    currentStory.conflicts(),
                    value.unknowns()
                ));
            }
        });
        Map<String, HistoryChapter> chapters = new LinkedHashMap<>();
        current.chapters().forEach(value -> chapters.put(value.id(), value));
        cached.chapters().forEach(value -> {
            HistoryChapter currentChapter = chapters.get(value.id());
            if (batch.chapterIds().contains(value.id()) && currentChapter != null
                && new LinkedHashSet<>(currentChapter.storyRefs()).equals(new LinkedHashSet<>(value.storyRefs()))) {
                chapters.put(value.id(), new HistoryChapter(
                    currentChapter.id(), value.title(), value.summary(), currentChapter.from(), currentChapter.to(),
                    currentChapter.boundarySignals(), currentChapter.storyRefs(), currentChapter.storyCount(),
                    currentChapter.rawEventCount(), "INFERRED_NON_AUTHORITATIVE", currentChapter.coverage(),
                    currentChapter.limitations(), currentChapter.presentationAuthority(),
                    currentChapter.presentationRevision(), currentChapter.userDeclared(),
                    currentChapter.userCorrectionRefs(), currentChapter.hiddenByDefault(), currentChapter.pinned()
                ));
            }
        });
        return new SnapshotResult(
            current.chapters().stream().map(value -> chapters.getOrDefault(value.id(), value)).toList(),
            current.stories().stream().map(value -> stories.getOrDefault(value.id(), value)).toList(),
            current.threads()
        );
    }

    private String writeWindowResult(SnapshotResult current, ModelBatch batch) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("stories", current.stories().stream().filter(value -> batch.storyIds().contains(value.id())).toList());
            payload.put("chapters", current.chapters().stream().filter(value -> batch.chapterIds().contains(value.id())).toList());
            return redactor.redactOutboundText(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    private ChapterBatch chapterBatch(SnapshotResult snapshot, HistoryChapter chapter) {
        Map<String, ChangeStory> stories = snapshot.stories().stream().collect(
            LinkedHashMap::new, (map, story) -> map.put(story.id(), story), Map::putAll
        );
        ProjectHistoryChapterRepresentationPlanner.Plan representation = chapterRepresentationPlan(snapshot, chapter);
        StringBuilder fingerprintInput = new StringBuilder(chapter.id()).append('|')
            .append(ProjectHistoryChapterRepresentationPlanner.PLAN_VERSION).append('|')
            .append(representation.fingerprint()).append('|')
            .append(String.join("|", chapter.storyRefs()));
        chapter.storyRefs().stream().map(stories::get).filter(java.util.Objects::nonNull).forEach(story ->
            fingerprintInput.append('|').append(story.id()).append('|').append(story.humanTitle())
                .append('|').append(story.oneSentenceSummary()).append('|').append(story.role())
                .append('|').append(story.presentationAuthority()).append('|').append(story.presentationRevision())
        );
        String sourceFingerprint = ProjectHistorySourceCollector.sha256(fingerprintInput.toString());
        String identity = "chapter-summary-" + ProjectHistorySourceCollector.sha256(chapter.id()).substring(0, 20);
        String cacheKey = ProjectHistorySourceCollector.sha256(String.join("|",
            sourceFingerprint, STRATEGY_VERSION, ProjectHistoryPromptBuilder.CHAPTER_PROMPT_VERSION, identity
        ));
        return new ChapterBatch(
            identity, cacheKey, sourceFingerprint, chapter.id(), ordered(new LinkedHashSet<>(chapter.storyRefs()))
        );
    }

    private ProjectHistoryPromptBuilder.ChapterSynthesisBuildResult chapterPrompt(
        SnapshotResult snapshot,
        HistoryChapter chapter
    ) {
        Map<String, ChangeStory> stories = snapshot.stories().stream().collect(
            LinkedHashMap::new, (map, story) -> map.put(story.id(), story), Map::putAll
        );
        List<ChangeStory> members = chapter.storyRefs().stream().map(stories::get)
            .filter(java.util.Objects::nonNull).toList();
        ProjectHistoryChapterRepresentationPlanner.Plan representation = chapterRepresentationPlanner.plan(members);
        Set<String> representativeStoryIds = representation.selectedClusters().stream()
            .flatMap(cluster -> cluster.representativeStoryIds().stream())
            .collect(LinkedHashSet::new, Set::add, Set::addAll);
        List<ProjectHistoryPromptBuilder.ChapterStorySummaryInput> summaries = members.stream()
            .filter(ChangeStory::primary).filter(story -> representativeStoryIds.contains(story.id()))
            .map(story -> new ProjectHistoryPromptBuilder.ChapterStorySummaryInput(
                story.id(), boundedPromptText(story.humanTitle(), 240), boundedPromptText(story.oneSentenceSummary(), 700),
                story.role(), story.occurredFrom() == null ? "" : story.occurredFrom().toString(),
                story.occurredTo() == null ? "" : story.occurredTo().toString()
            )).toList();
        int primaryCount = (int) members.stream().filter(ChangeStory::primary).count();
        ProjectHistoryPromptBuilder.ChapterSynthesisPromptInput input =
            new ProjectHistoryPromptBuilder.ChapterSynthesisPromptInput(
                chapter.id(), chapter.from() == null ? "" : chapter.from().toString(),
                chapter.to() == null ? "" : chapter.to().toString(), chapter.storyRefs().size(), primaryCount,
                Math.max(0, chapter.storyRefs().size() - primaryCount), chapter.boundarySignals(),
                ProjectHistorySourceCollector.sha256(String.join("|", chapter.storyRefs())),
                chapterRepresentativeClusterInputs(representation), representation.requiredRepresentativeClusterIds(),
                representation.dominantClusterIds(), representation.representativePrimaryCoverage(),
                representation.unknowns(), representation.conflicts(), chapterForbiddenOverclaims(representation), summaries,
                boundedPromptText(chapter.title(), 240), boundedPromptText(chapter.summary(), 1_500)
            );
        return promptBuilder.buildChapterProduction(input);
    }

    private ProjectHistoryChapterRepresentationPlanner.Plan chapterRepresentationPlan(
        SnapshotResult snapshot,
        HistoryChapter chapter
    ) {
        Map<String, ChangeStory> stories = snapshot.stories().stream().collect(
            LinkedHashMap::new, (map, story) -> map.put(story.id(), story), Map::putAll
        );
        return chapterRepresentationPlanner.plan(
            chapter.storyRefs().stream().map(stories::get).filter(java.util.Objects::nonNull).toList()
        );
    }

    private List<ProjectHistoryPromptBuilder.ChapterRepresentativeClusterInput> chapterRepresentativeClusterInputs(
        ProjectHistoryChapterRepresentationPlanner.Plan representation
    ) {
        return representation.selectedClusters().stream().map(cluster ->
            new ProjectHistoryPromptBuilder.ChapterRepresentativeClusterInput(
                cluster.id(), boundedPromptText(cluster.humanLabel(), 180), cluster.role(), cluster.weight(),
                cluster.primaryStoryCount(), cluster.supportingStoryCount(), cluster.representativeStoryIds(),
                cluster.representativeOutcomes().stream().map(value -> boundedPromptText(value, 300)).toList(),
                cluster.allowedClaimStates(), cluster.claimCeiling(),
                cluster.unknowns().stream().map(value -> boundedPromptText(value, 300)).limit(8).toList(),
                cluster.conflicts().stream().map(value -> boundedPromptText(value, 300)).limit(8).toList()
            )
        ).toList();
    }

    private static List<String> chapterForbiddenOverclaims(
        ProjectHistoryChapterRepresentationPlanner.Plan representation
    ) {
        LinkedHashSet<String> result = new LinkedHashSet<>(List.of(
            "不得表达生产可用、正式上线、稳定运行或项目成功",
            "不得把 Supporting、测试、配置或文件数量写成用户成果"
        ));
        boolean allVerified = representation.selectedClusters().stream()
            .allMatch(cluster -> "VERIFIED".equals(cluster.claimCeiling()));
        boolean anyImplemented = representation.selectedClusters().stream().anyMatch(cluster ->
            Set.of("IMPLEMENTED", "VERIFIED", "REMOVED", "RESTORED").contains(cluster.claimCeiling())
        );
        if (!allVerified) result.add("没有对应验证状态的成果簇不得写成验证通过");
        if (!anyImplemented) result.add("不得把规划、声明、配置或观察状态写成已经实现");
        return List.copyOf(result);
    }

    private void validateChapterRepresentation(
        String title,
        String summary,
        ProjectHistoryChapterRepresentationPlanner.Plan representation
    ) {
        if (representation == null || representation.selectedClusters().isEmpty()) {
            throw new HistoryValidationException(
                ValidationKind.CONTRACT, "History chapter has no deterministic representative cluster plan"
            );
        }
        List<String> allGrounding = representation.selectedClusters().stream()
            .flatMap(cluster -> cluster.grounding().stream()).distinct().toList();
        try {
            narrativeValidator.validateChapter(title, summary, languageService.chapterGrounding(allGrounding));
        } catch (ProjectHistoryNarrativeEntailmentValidator.NarrativeViolation violation) {
            throw new HistoryValidationException(ValidationKind.UNSUPPORTED_CLAIM, violation.getMessage());
        }
        boolean dominantTitle = representation.selectedClusters().stream()
            .filter(cluster -> representation.dominantClusterIds().contains(cluster.id()))
            .anyMatch(cluster -> narrativeValidator.representsChapterOutcome(title, cluster.grounding()));
        if (!dominantTitle) {
            throw new HistoryValidationException(
                ValidationKind.UNSUPPORTED_CLAIM, "History chapter title represents only a minor or unknown cluster"
            );
        }
        for (ProjectHistoryChapterRepresentationPlanner.Cluster cluster : representation.selectedClusters()) {
            if (!narrativeValidator.representsChapterOutcome(summary, cluster.grounding())) {
                throw new HistoryValidationException(
                    ValidationKind.UNSUPPORTED_CLAIM,
                    "History chapter summary omitted representative cluster " + cluster.id()
                );
            }
            String clusterWording = matchingChapterClauses(title, summary, cluster);
            try {
                narrativeValidator.validateStateCeiling(chapterClaimState(cluster.claimCeiling()), clusterWording);
            } catch (ProjectHistoryNarrativeEntailmentValidator.NarrativeViolation violation) {
                throw new HistoryValidationException(ValidationKind.UNSUPPORTED_CLAIM, violation.getMessage());
            }
        }
    }

    private String matchingChapterClauses(
        String title,
        String summary,
        ProjectHistoryChapterRepresentationPlanner.Cluster cluster
    ) {
        List<String> matches = Stream.of(title, summary)
            .flatMap(value -> Stream.of((value == null ? "" : value).split("[。；;！!?，,]")))
            .map(String::trim).filter(value -> !value.isBlank())
            .filter(value -> narrativeValidator.representsChapterOutcome(value, cluster.grounding()))
            .toList();
        return matches.isEmpty() ? summary : String.join("。", matches);
    }

    private static ProjectHistoryNarrativeEntailmentValidator.ClaimState chapterClaimState(String value) {
        try {
            return ProjectHistoryNarrativeEntailmentValidator.ClaimState.valueOf(
                value == null ? "UNKNOWN" : value.toUpperCase(Locale.ROOT)
            );
        } catch (IllegalArgumentException ignored) {
            return ProjectHistoryNarrativeEntailmentValidator.ClaimState.UNKNOWN;
        }
    }

    private HistoryChapter parseChapterSynthesis(
        JsonNode root,
        HistoryChapter original,
        ProjectHistoryChapterRepresentationPlanner.Plan representation
    ) {
        if (root == null || !root.isObject()) {
            throw new HistoryValidationException(ValidationKind.CONTRACT, "History chapter model output is not an object");
        }
        requireAllowedFields(root, Set.of("chapters"), "History chapter model root contains unsupported fields");
        JsonNode chapters = root.path("chapters");
        if (!chapters.isArray() || chapters.size() != 1) {
            throw new HistoryValidationException(ValidationKind.CONTRACT, "History chapter model must return one chapter");
        }
        JsonNode node = chapters.get(0);
        requireAllowedFields(node, MODEL_CHAPTER_SYNTHESIS_FIELDS, "History chapter model contains forbidden fields");
        if (!original.id().equals(text(node, "chapterId"))) {
            throw new HistoryValidationException(ValidationKind.CROSS_PROJECT_REFERENCE, "Unknown chapter ID");
        }
        List<String> representedClusterIds = stringList(node.path("representedClusterIds"), 12);
        if (!representedClusterIds.equals(representation.requiredRepresentativeClusterIds())) {
            throw new HistoryValidationException(
                ValidationKind.CROSS_PROJECT_REFERENCE,
                "History chapter model changed required representative cluster IDs"
            );
        }
        String title = modelText(node, "title", 240);
        String summary = modelText(node, "summary", 1_500);
        if (weak(title) || weak(summary) || prohibitedAuthorityClaim(title + " " + summary)) {
            throw new HistoryValidationException(ValidationKind.UNSUPPORTED_CLAIM,
                "History chapter model returned an unsupported claim");
        }
        validateChapterRepresentation(title, summary, representation);
        return new HistoryChapter(
            original.id(), title, summary, original.from(), original.to(), original.boundarySignals(),
            original.storyRefs(), original.storyCount(), original.rawEventCount(), "INFERRED_NON_AUTHORITATIVE",
            original.coverage(), original.limitations(), original.presentationAuthority(), original.presentationRevision(),
            original.userDeclared(), original.userCorrectionRefs(), original.hiddenByDefault(), original.pinned()
        );
    }

    private HistoryChapter readCachedChapter(
        ProjectHistoryWindowCheckpoint checkpoint,
        SnapshotResult current,
        ChapterBatch batch
    ) {
        try {
            JsonNode root = objectMapper.readTree(checkpoint.getValidatedResultJson());
            HistoryChapter cached = objectMapper.convertValue(root.path("chapter"), HistoryChapter.class);
            HistoryChapter original = current.chapters().stream()
                .filter(chapter -> chapter.id().equals(batch.chapterId())).findFirst().orElse(null);
            if (cached == null || original == null || !original.id().equals(cached.id())) return null;
            if (!new LinkedHashSet<>(original.storyRefs()).equals(new LinkedHashSet<>(cached.storyRefs()))) return null;
            return cached;
        } catch (Exception ignored) {
            return null;
        }
    }

    private SnapshotResult replaceChapter(SnapshotResult current, HistoryChapter replacement) {
        return new SnapshotResult(
            current.chapters().stream().map(chapter -> chapter.id().equals(replacement.id()) ? replacement : chapter).toList(),
            current.stories(), current.threads()
        );
    }

    private String writeChapterResult(HistoryChapter chapter) {
        try {
            return redactor.redactOutboundText(objectMapper.writeValueAsString(Map.of("chapter", chapter)));
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    private String safeCheckpointDiagnostics(ModelGatewayService.ModelCallDiagnostics diagnostics) {
        if (diagnostics == null) return "{}";
        return json(Map.of(
            "requestCount", diagnostics.requestCount(),
            "totalTokens", diagnostics.totalTokens(),
            "latencyMs", diagnostics.latencyMs(),
            "finishReason", diagnostics.normalizedFinishReason(),
            "truncated", diagnostics.truncated(),
            "schemaMatched", diagnostics.schemaMatched(),
            "retryType", diagnostics.retryType(),
            "retrySucceeded", diagnostics.compactRetrySucceeded()
        ));
    }

    private boolean systemicProviderFailure(Exception exception) {
        if (exception instanceof ModelGatewayService.ModelHttpException http) {
            return http.statusCode() == 401 || http.statusCode() == 403 || http.statusCode() == 402
                || http.statusCode() == 404;
        }
        String message = exception.getMessage() == null ? "" : exception.getMessage().toLowerCase(Locale.ROOT);
        return message.contains("configuration") || message.contains("未配置") || message.contains("provider")
            && (message.contains("disabled") || message.contains("unavailable") || message.contains("额度"));
    }

    private static int windowOrdinal(String identity) {
        if (identity == null) return Integer.MAX_VALUE;
        int start = identity.indexOf("window-");
        if (start < 0) return Integer.MAX_VALUE;
        int from = start + "window-".length();
        int end = from;
        while (end < identity.length() && Character.isDigit(identity.charAt(end))) end++;
        try {
            return end == from ? Integer.MAX_VALUE : Integer.parseInt(identity.substring(from, end));
        } catch (NumberFormatException ignored) {
            return Integer.MAX_VALUE;
        }
    }

    private ModelBatchPlan modelBatches(
        UUID projectId,
        SnapshotResult base,
        Set<String> eligibleStoryIds,
        List<ProjectHistoryEvent> events,
        AiProvider provider,
        Map<String, List<String>> reasonEvidence
    ) {
        String sourceFingerprint = fingerprint(events);
        List<ProjectHistoryWindowPlanner.Window> allWindows = windowPlanner.planAll(
            base.stories(), eligibleStoryIds, sourceFingerprint, STRATEGY_VERSION, PROMPT_VERSION,
            ""
        );
        List<ModelBatch> planned = new ArrayList<>();
        Map<String, ChangeStory> byId = base.stories().stream().collect(
            LinkedHashMap::new, (map, story) -> map.put(story.id(), story), Map::putAll
        );
        Map<UUID, ProjectHistoryEvent> eventsById = events.stream().collect(
            LinkedHashMap::new, (map, event) -> map.put(event.getId(), event), Map::putAll
        );
        for (ProjectHistoryWindowPlanner.Window window : allWindows) {
            Set<String> storyIds = new LinkedHashSet<>(window.storyIds());
            Set<UUID> eventIds = storyIds.stream().map(byId::get).filter(java.util.Objects::nonNull)
                .flatMap(story -> story.eventRefs().stream()).collect(LinkedHashSet::new, Set::add, Set::addAll);
            String windowFingerprint = fingerprint(eventIds.stream().map(eventsById::get)
                .filter(java.util.Objects::nonNull).toList());
            planned.add(modelBatch(base, storyIds, eventIds, window.identity(), "", windowFingerprint));
        }
        List<ProjectHistoryCorrectionService.WindowRevisionScope> scopes = planned.stream().map(batch -> {
            Set<String> targets = new LinkedHashSet<>(batch.storyIds());
            targets.addAll(batch.chapterIds());
            return new ProjectHistoryCorrectionService.WindowRevisionScope(batch.sourceFingerprint(), targets);
        }).toList();
        List<String> windowRevisions = correctionService.currentWindowPresentationRevisions(projectId, scopes);
        List<ModelBatch> result = new ArrayList<>();
        for (int index = 0; index < planned.size(); index++) {
            ModelBatch item = planned.get(index);
            String revision = index < windowRevisions.size() ? windowRevisions.get(index) : "";
            String cacheKey = windowPlanner.cacheKey(
                item.identity(), item.sourceFingerprint(), STRATEGY_VERSION, PROMPT_VERSION, revision
            );
            ModelBatch batch = new ModelBatch(
                item.identity(), cacheKey, item.sourceFingerprint(), item.storyIds(), item.chapterIds(), item.eventIds(), false
            );
            result.addAll(splitPromptOverflow(base, events, batch, reasonEvidence));
        }
        return new ModelBatchPlan(result, result.size());
    }

    /** Split a window deterministically until every Story fits the real prompt budget. */
    private List<ModelBatch> splitPromptOverflow(
        SnapshotResult base,
        List<ProjectHistoryEvent> events,
        ModelBatch batch,
        Map<String, List<String>> reasonEvidence
    ) {
        ProjectHistoryPromptBuilder.PromptBuildResult prompt = modelPrompt(
            base, events, batch.storyIds(), reasonEvidence
        );
        if (prompt.includedStoryIds().containsAll(batch.storyIds())) return List.of(batch);
        if (batch.storyIds().size() <= 1) {
            // A single record can still be too large when user material contains
            // long text. modelPrompt applies bounded field projections; retaining
            // the stable batch here makes the unresolved condition explicit rather
            // than retrying an identical SKIPPED row forever.
            return List.of(batch.withPromptOverflow(true));
        }
        List<String> ids = new ArrayList<>(batch.storyIds());
        int midpoint = Math.max(1, ids.size() / 2);
        Set<String> left = new LinkedHashSet<>(ids.subList(0, midpoint));
        Set<String> right = new LinkedHashSet<>(ids.subList(midpoint, ids.size()));
        return Stream.concat(Stream.of(childBatch(base, batch, left, "a"), childBatch(base, batch, right, "b")), Stream.empty())
            .flatMap(child -> splitPromptOverflow(base, events, child, reasonEvidence).stream())
            .toList();
    }

    private ModelBatch childBatch(SnapshotResult base, ModelBatch parent, Set<String> storyIds, String suffix) {
        Set<UUID> eventIds = base.stories().stream().filter(story -> storyIds.contains(story.id()))
            .flatMap(story -> story.eventRefs().stream())
            .collect(LinkedHashSet::new, Set::add, Set::addAll);
        String identity = parent.identity() + "-" + suffix;
        String cacheKey = ProjectHistorySourceCollector.sha256(String.join("|", parent.cacheKey(), identity));
        return modelBatch(base, storyIds, eventIds, identity, cacheKey, parent.sourceFingerprint());
    }

    private ModelBatch modelBatch(
        SnapshotResult base,
        Set<String> storyIds,
        Set<UUID> eventIds,
        String identity,
        String cacheKey,
        String sourceFingerprint
    ) {
        Set<String> chapters = base.chapters().stream().filter(chapter -> {
            Set<String> refs = new LinkedHashSet<>(chapter.storyRefs());
            return !refs.isEmpty() && storyIds.containsAll(refs);
        }).map(HistoryChapter::id).collect(LinkedHashSet::new, Set::add, Set::addAll);
        return new ModelBatch(identity, cacheKey, sourceFingerprint,
            ordered(storyIds), ordered(chapters), ordered(eventIds), false);
    }

    private static <T> Set<T> ordered(Set<T> values) {
        return java.util.Collections.unmodifiableSet(new LinkedHashSet<>(values == null ? Set.of() : values));
    }

    private ProjectHistoryPromptBuilder.PromptBuildResult modelPrompt(
        SnapshotResult base,
        List<ProjectHistoryEvent> events,
        Set<String> eligibleStories,
        Map<String, List<String>> reasonEvidenceByStory
    ) {
        Map<UUID, EventView> views = new LinkedHashMap<>();
        events.stream().map(this::view).forEach(event -> views.put(event.id(), event));
        List<ProjectHistoryPromptBuilder.StoryPromptInput> stories = base.stories().stream()
            .filter(story -> eligibleStories.contains(story.id())).map(story -> {
            List<EventView> members = story.eventRefs().stream().map(views::get).filter(java.util.Objects::nonNull).toList();
            String subjectLabel = languageService.readableObject(
                story.primarySubjectKey(),
                members.stream().flatMap(event -> event.paths().stream()).distinct().limit(40).toList(),
                members.stream().filter(event -> event.category() != Category.FILE_CHANGE)
                    .map(EventView::label).filter(value -> value != null && !value.isBlank()).distinct().limit(8).toList()
            );
            ProjectHistoryNarrativeEntailmentValidator.NarrativeEnvelope envelope = narrativeEnvelope(
                story.primarySubjectKey(), subjectLabel,
                primaryTransition(storyTransitions(members)),
                members,
                !reasonEvidenceByStory.getOrDefault(story.id(), List.of()).isEmpty()
            );
            return new ProjectHistoryPromptBuilder.StoryPromptInput(
                story.id(), boundedPromptText(envelope.subjectLabel(), 180),
                story.occurredFrom().toString(), story.occurredTo().toString(),
                members.stream().map(event -> event.transition().name()).distinct().toList(),
                members.stream().filter(event -> event.category() != Category.FILE_CHANGE)
                    .map(EventView::label).map(value -> boundedPromptText(value, 240)).distinct().limit(5).toList(),
                story.affectedAreas().stream().limit(10).map(value -> boundedPromptText(value, 180)).toList(),
                story.evidenceRefs().stream().limit(12).map(value -> boundedPromptText(value, 180)).toList(),
                reasonEvidenceByStory.getOrDefault(story.id(), List.of()).stream().limit(30).toList(),
                boundedPromptText(story.beforeState(), 700), boundedPromptText(story.change(), 900),
                boundedPromptText(story.afterState(), 700), envelope.claimState().name(),
                envelope.claimAction(), boundedPromptText(envelope.supportedOutcome(), 300),
                envelope.directSupportSummary(), envelope.indirectContextSummary(), envelope.supportClass(),
                boundedPromptText(envelope.downgradeReason(), 300),
                envelope.allowedClaims(), envelope.forbiddenClaims(), envelope.humanSafeSourceContext(), story.role(),
                story.primaryStoryId(),
                story.supportingChangeRefs().stream().limit(40).toList(),
                boundedPromptText(story.humanTitle(), 240), boundedPromptText(story.oneSentenceSummary(), 1_000)
            );
        }).toList();
        // Chapter wording is finalized only by the second-stage Representation
        // Plan prompt, where exact representative cluster IDs are validated.
        return promptBuilder.buildProduction(new ProjectHistoryPromptBuilder.PromptInput(stories, List.of()));
    }

    private static String boundedPromptText(String value, int limit) {
        String safe = value == null ? "" : value.trim();
        if (safe.length() <= limit) return safe;
        return safe.substring(0, Math.max(0, limit - 1)) + "…";
    }

    private <T> ValidatedHistoryResponse<T> callWithValidationRepair(
        AiProvider provider,
        String prompt,
        ModelTaskType task,
        HistoryResponseParser<T> parser,
        List<ModelGatewayService.ModelCallDiagnostics> diagnostics
    ) throws java.io.IOException, InterruptedException {
        ModelGatewayService.StructuredModelResponse first;
        try {
            first = requireStructuredResponse(modelGateway.callStructured(provider, prompt, task));
        } catch (ModelGatewayService.ModelResponseFormatException failure) {
            if (failure.diagnostics() != null) diagnostics.add(failure.diagnostics());
            throw failure;
        }
        try {
            T value = parser.parse(first.parsed().root());
            diagnostics.add(first.diagnostics());
            return new ValidatedHistoryResponse<>(first, value);
        } catch (HistoryValidationException firstFailure) {
            String repairPrompt = promptBuilder.validationRepair(prompt, firstFailure.kind().name());
            ModelGatewayService.StructuredModelResponse repaired;
            try {
                repaired = requireStructuredResponse(modelGateway.callStructured(provider, repairPrompt, task));
            } catch (java.io.IOException | InterruptedException repairFailure) {
                diagnostics.add(failedValidationRepairDiagnostics(first.diagnostics(), repairFailure));
                throw repairFailure;
            } catch (RuntimeException repairFailure) {
                diagnostics.add(failedValidationRepairDiagnostics(first.diagnostics(), repairFailure));
                throw repairFailure;
            }
            try {
                T value = parser.parse(repaired.parsed().root());
                ModelGatewayService.StructuredModelResponse combined = repaired.withRecovery(
                    first.diagnostics(), "HISTORY_VALIDATION_RETRY", true
                );
                diagnostics.add(combined.diagnostics());
                return new ValidatedHistoryResponse<>(combined, value);
            } catch (HistoryValidationException finalFailure) {
                ModelGatewayService.StructuredModelResponse combined = repaired.withRecovery(
                    first.diagnostics(), "HISTORY_VALIDATION_RETRY", false
                );
                diagnostics.add(combined.diagnostics().withFailure(
                    "HISTORY_VALIDATION", "HISTORY_VALIDATION_REPAIR_FAILED"
                ));
                throw finalFailure;
            }
        }
    }

    private ModelGatewayService.StructuredModelResponse requireStructuredResponse(
        ModelGatewayService.StructuredModelResponse response
    ) {
        if (response == null || response.parsed() == null || response.diagnostics() == null) {
            throw new IllegalStateException("模型未返回可诊断的结构化结果");
        }
        return response;
    }

    private ModelGatewayService.ModelCallDiagnostics failedValidationRepairDiagnostics(
        ModelGatewayService.ModelCallDiagnostics first,
        Throwable failure
    ) {
        ModelGatewayService.ModelCallDiagnostics combined;
        if (failure instanceof ModelGatewayService.ModelResponseFormatException format
            && format.diagnostics() != null) {
            combined = format.diagnostics().combine(first, "HISTORY_VALIDATION_RETRY", false);
        } else {
            combined = first.withRecovery(
                "HISTORY_VALIDATION_RETRY", false, failedModelRequestCount(failure)
            );
        }
        return combined.withFailure("HISTORY_VALIDATION", "HISTORY_VALIDATION_REPAIR_FAILED");
    }

    private static int failedModelRequestCount(Throwable failure) {
        if (failure instanceof ModelGatewayService.ModelHttpException http) return http.requestCount();
        if (failure instanceof ModelGatewayService.ModelTransportException transport) return transport.requestCount();
        return 1;
    }

    private SnapshotResult parseModel(
        JsonNode root,
        SnapshotResult base,
        Set<String> eligibleStoryIds,
        Set<String> eligibleChapterIds,
        Map<String, List<String>> reasonEvidenceByStory,
        Map<UUID, EventView> eventsById
    ) {
        if (root == null || !root.isObject()) {
            throw new HistoryValidationException(ValidationKind.CONTRACT, "History model output is not an object");
        }
        requireAllowedFields(root, ProjectHistoryModelOutputContract.ROOT_FIELDS,
            "History model root contains unsupported fields");
        Map<String, ChangeStory> stories = new LinkedHashMap<>();
        base.stories().forEach(story -> stories.put(story.id(), story));
        Set<String> seenStories = new LinkedHashSet<>();
        JsonNode storyNodes = root.path("stories");
        if (!storyNodes.isArray()) throw new HistoryValidationException(ValidationKind.CONTRACT, "History model stories are missing");
        for (JsonNode node : storyNodes) {
            requireAllowedFields(node, ProjectHistoryModelOutputContract.STORY_COMPATIBILITY_FIELDS,
                "History model story contains forbidden fields");
            String id = text(node, "storyId");
            ChangeStory original = stories.get(id);
            if (original == null || !eligibleStoryIds.contains(id)) {
                throw new HistoryValidationException(ValidationKind.CROSS_PROJECT_REFERENCE, "Unknown story ID");
            }
            if (!seenStories.add(id)) throw new HistoryValidationException(ValidationKind.CONTRACT, "Duplicate story ID");
            String title = modelText(node, "humanTitle", 240);
            String summary = modelText(node, "oneSentenceSummary", 1_000);
            if (weak(title) || weak(summary) || prohibitedAuthorityClaim(title + " " + summary)) {
                throw new HistoryValidationException(ValidationKind.UNSUPPORTED_CLAIM, "History model returned vague wording");
            }
            boolean deterministicTitleFallback = false;
            if (!narrativeValidator.hasActionObjectResult(title, summary)) {
                title = original.humanTitle();
                summary = original.oneSentenceSummary();
                deterministicTitleFallback = true;
                if (!narrativeValidator.hasActionObjectResult(title, summary)) {
                    throw new HistoryValidationException(
                        ValidationKind.UNSUPPORTED_CLAIM,
                        "History title does not state an action, object and supported result"
                    );
                }
            }
            String before = modelText(node, "beforeWording", 1_000);
            String change = modelText(node, "changeWording", 1_200);
            String after = modelText(node, "afterWording", 1_000);
            if (before.isBlank()) before = original.beforeState();
            if (change.isBlank()) change = original.change();
            if (after.isBlank()) after = original.afterState();
            List<String> reasonEvidence = stringList(node.path("reasonEvidenceRefs"), 30);
            if (!reasonEvidenceByStory.getOrDefault(id, List.of()).containsAll(reasonEvidence)) {
                throw new HistoryValidationException(ValidationKind.INVALID_EVIDENCE, "History model returned ineligible reason Evidence");
            }
            String reason = modelText(node, "reason", 1_000);
            if (!reason.isBlank() && reasonEvidence.isEmpty()) {
                throw new HistoryValidationException(ValidationKind.INVALID_EVIDENCE, "History reason has no Evidence");
            }
            String unknownWording = modelText(node, "unknownWording", 500);
            if (unknownWording.isBlank()) {
                unknownWording = stringList(node.path("unknowns"), 20).stream().findFirst().orElse("");
            }
            List<EventView> members = original.eventRefs().stream().map(eventsById::get)
                .filter(java.util.Objects::nonNull).toList();
            String subjectLabel = languageService.readableObject(
                original.primarySubjectKey(),
                members.stream().flatMap(event -> event.paths().stream()).distinct().limit(40).toList(),
                members.stream().filter(event -> event.category() != Category.FILE_CHANGE)
                    .map(EventView::label).filter(value -> value != null && !value.isBlank()).distinct().limit(8).toList()
            );
            ProjectHistoryNarrativeEntailmentValidator.NarrativeEnvelope envelope = narrativeEnvelope(
                original.primarySubjectKey(), subjectLabel,
                primaryTransition(storyTransitions(members)),
                members,
                !reasonEvidenceByStory.getOrDefault(id, List.of()).isEmpty()
            );
            boolean sourceStateUnknown = members.stream()
                .anyMatch(event -> event.epistemicStatus() == ProjectFactEpistemicStatus.UNKNOWN);
            List<String> unknowns = unknownWording.isBlank() && !reason.isBlank() && !sourceStateUnknown
                ? List.of()
                : narrativeValidator.normalizeUnknowns(unknownWording, sourceStateUnknown);
            try {
                narrativeValidator.validateStory(
                    envelope, title, summary, before, change, after, reason,
                    unknowns.stream().findFirst().orElse("")
                );
            } catch (ProjectHistoryNarrativeEntailmentValidator.NarrativeViolation violation) {
                throw new HistoryValidationException(ValidationKind.UNSUPPORTED_CLAIM, violation.getMessage());
            }
            stories.put(id, copyStory(
                original, original.laterOutcome(), "INFERRED_NON_AUTHORITATIVE",
                deterministicTitleFallback ? "MODEL_VALIDATED_WITH_DETERMINISTIC_TITLE" : "MODEL_VALIDATED",
                title, summary, before, change, after, reason, reasonEvidence,
                original.conflicts(), unknowns
            ));
        }
        if (!seenStories.equals(eligibleStoryIds)) {
            throw new HistoryValidationException(ValidationKind.CONTRACT, "History model omitted stories");
        }
        validateRoleGraph(stories.values());

        Map<String, HistoryChapter> chapters = new LinkedHashMap<>();
        base.chapters().forEach(chapter -> chapters.put(chapter.id(), chapter));
        Set<String> seenChapters = new LinkedHashSet<>();
        JsonNode chapterNodes = root.path("chapters");
        if (!chapterNodes.isArray()) throw new HistoryValidationException(ValidationKind.CONTRACT, "History model chapters are missing");
        for (JsonNode node : chapterNodes) {
            requireAllowedFields(node, ProjectHistoryModelOutputContract.CHAPTER_FIELDS,
                "History model chapter contains forbidden fields");
            String id = text(node, "chapterId");
            HistoryChapter original = chapters.get(id);
            if (original == null || !eligibleChapterIds.contains(id)) {
                throw new HistoryValidationException(ValidationKind.CROSS_PROJECT_REFERENCE, "Unknown chapter ID");
            }
            if (!seenChapters.add(id)) throw new HistoryValidationException(ValidationKind.CONTRACT, "Duplicate chapter ID");
            String title = modelText(node, "title", 240);
            String summary = modelText(node, "summary", 1_500);
            if (weak(title) || weak(summary) || prohibitedAuthorityClaim(title + " " + summary)) {
                throw new HistoryValidationException(ValidationKind.UNSUPPORTED_CLAIM, "History model returned an unsupported chapter claim");
            }
            ProjectHistoryChapterRepresentationPlanner.Plan representation = chapterRepresentationPlan(base, original);
            validateChapterRepresentation(title, summary, representation);
            chapters.put(id, new HistoryChapter(
                original.id(), title, summary, original.from(), original.to(), original.boundarySignals(),
                original.storyRefs(), original.storyCount(), original.rawEventCount(), "INFERRED_NON_AUTHORITATIVE",
                original.coverage(), original.limitations(), original.presentationAuthority(),
                original.presentationRevision(), original.userDeclared(), original.userCorrectionRefs(),
                original.hiddenByDefault(), original.pinned()
            ));
        }
        if (!seenChapters.equals(eligibleChapterIds)) {
            throw new HistoryValidationException(ValidationKind.CONTRACT, "History model omitted chapters");
        }
        List<ChangeStory> orderedStories = base.stories().stream().map(story -> stories.get(story.id())).toList();
        List<HistoryChapter> orderedChapters = base.chapters().stream().map(chapter -> chapters.get(chapter.id())).toList();
        return new SnapshotResult(orderedChapters, orderedStories, base.threads());
    }

    private Map<String, List<String>> reasonEligibleEvidence(
        SnapshotResult snapshot,
        Map<UUID, EventView> eventsById,
        Set<String> eligibleStoryIds
    ) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        snapshot.stories().stream().filter(story -> eligibleStoryIds.contains(story.id())).forEach(story ->
            result.put(story.id(), reasonEligibleEvidence(story, eventsById))
        );
        return Map.copyOf(result);
    }

    private ProjectHistoryNarrativeEntailmentValidator.NarrativeEnvelope narrativeEnvelope(
        String subjectKey,
        String subjectLabel,
        Transition transition,
        List<EventView> events,
        boolean reasonEligible
    ) {
        List<EventView> safeEvents = events == null ? List.of() : events;
        return narrativeValidator.envelope(new ProjectHistoryNarrativeEntailmentValidator.EvidenceProfile(
            subjectKey,
            subjectLabel,
            transition,
            safeEvents.stream().map(event -> new ProjectHistoryNarrativeEntailmentValidator.EvidenceAtom(
                event.stableKey(), event.subjectKeys(), event.category(), event.transition(), event.authority(),
                event.epistemicStatus(), event.paths(), event.label(), event.evidenceRefs()
            )).toList(),
            reasonEligible
        ));
    }

    /** Persist only bounded enum-like failure facts; never retain model text or request content. */
    private String failureCheckpointDiagnostics(String scope, Exception failure) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("status", "FAILED");
        values.put("scope", "CHAPTER".equals(scope) ? "CHAPTER" : "WINDOW");
        if (failure instanceof HistoryValidationException validation) {
            values.put("failureClass", "HISTORY_VALIDATION");
            values.put("validationKind", validation.kind().name());
            return json(values);
        }
        if (failure instanceof ModelGatewayService.ModelResponseFormatException format) {
            values.put("failureClass", "MODEL_RESPONSE_FORMAT");
            ModelGatewayService.ModelCallDiagnostics diagnostics = format.diagnostics();
            if (diagnostics != null) {
                values.put("failureStage", safeDiagnosticToken(diagnostics.failureStage()));
                values.put("failureCode", safeDiagnosticToken(diagnostics.failureCode()));
                values.put("retryType", safeDiagnosticToken(diagnostics.retryType()));
                values.put("requestCount", Math.max(0, diagnostics.requestCount()));
                values.put("finishReason", safeDiagnosticToken(diagnostics.normalizedFinishReason()));
                values.put("truncated", diagnostics.truncated());
                values.put("schemaMatched", diagnostics.schemaMatched());
            }
            return json(values);
        }
        if (failure instanceof ModelGatewayService.ModelHttpException http) {
            values.put("failureClass", "MODEL_HTTP");
            values.put("failureCode", "HTTP_" + http.statusCode());
            values.put("requestCount", http.requestCount());
            return json(values);
        }
        if (failure instanceof ModelGatewayService.ModelTransportException transport) {
            values.put("failureClass", "MODEL_TRANSPORT");
            values.put("failureCode", "TRANSPORT");
            values.put("requestCount", transport.requestCount());
            return json(values);
        }
        values.put("failureClass", "OTHER");
        values.put("failureCode", "OTHER");
        return json(values);
    }

    private static String safeDiagnosticToken(String value) {
        if (value == null || value.isBlank()) return "";
        String safe = value.trim().replaceAll("[^A-Za-z0-9_:,.-]", "_");
        return safe.length() <= 160 ? safe : safe.substring(0, 160);
    }

    private static boolean hasReasonEligibleEvidence(List<EventView> events) {
        return (events == null ? List.<EventView>of() : events).stream().anyMatch(event ->
            (event.authority() == Authority.FACTUAL_SOURCE && event.epistemicStatus().isStrongFact()
                || event.authority() == Authority.DECLARED
                    && event.epistemicStatus() == ProjectFactEpistemicStatus.DECLARED
                    && Set.of(Category.PULL_REQUEST, Category.ISSUE, Category.USER_DECLARATION).contains(event.category()))
                && event.evidenceRefs().stream().anyMatch(reference -> reference.startsWith("fact:")
                    || reference.startsWith("github-pr:") || reference.startsWith("github-issue:")
                    || reference.startsWith("declaration:"))
        );
    }

    private static ClaimAttribution claimAttribution(
        ProjectHistoryNarrativeEntailmentValidator.NarrativeEnvelope envelope
    ) {
        return new ClaimAttribution(
            envelope.subjectLabel(), envelope.claimAction(), envelope.claimState().name(), envelope.supportedOutcome(),
            envelope.directEvidenceRefs(), envelope.indirectEvidenceRefs(), envelope.sourceAuthorities(),
            envelope.supportClass(), envelope.downgradeReason()
        );
    }

    private static List<String> reasonEligibleEvidence(ChangeStory story, Map<UUID, EventView> eventsById) {
        Set<String> storyEvidence = new LinkedHashSet<>(story.evidenceRefs());
        return story.eventRefs().stream().map(eventsById::get).filter(java.util.Objects::nonNull)
            .filter(event ->
                event.authority() == Authority.FACTUAL_SOURCE && event.epistemicStatus().isStrongFact()
                    || event.authority() == Authority.DECLARED
                        && event.epistemicStatus() == ProjectFactEpistemicStatus.DECLARED
                        && Set.of(Category.PULL_REQUEST, Category.ISSUE, Category.USER_DECLARATION)
                            .contains(event.category())
            )
            .flatMap(event -> event.evidenceRefs().stream())
            .filter(reference -> (
                reference.startsWith("fact:")
                    || reference.startsWith("github-pr:")
                    || reference.startsWith("github-issue:")
                    || reference.startsWith("declaration:")
            ) && storyEvidence.contains(reference))
            .distinct().toList();
    }

    private void validate(SnapshotResult result, List<ProjectHistoryEvent> events) {
        Map<UUID, EventView> eventsById = new LinkedHashMap<>();
        events.stream().map(this::view).forEach(event -> eventsById.put(event.id(), event));
        Set<UUID> allowedEvents = eventsById.keySet();
        Set<UUID> semanticEvents = eventsById.values().stream()
            .filter(ProjectHistoryReconstructionService::semanticEligible)
            .map(EventView::id)
            .collect(LinkedHashSet::new, Set::add, Set::addAll);
        Set<String> allowedEvidence = eventsById.values().stream().flatMap(event -> event.evidenceRefs().stream())
            .collect(LinkedHashSet::new, Set::add, Set::addAll);
        Set<UUID> coveredEvents = new LinkedHashSet<>();
        Set<String> storyIds = new LinkedHashSet<>();
        Instant previous = null;
        for (ChangeStory story : result.stories()) {
            if (!storyIds.add(story.id())) throw new HistoryValidationException(ValidationKind.CONTRACT, "Duplicate story ID");
            if (previous != null && story.occurredFrom().isBefore(previous)) {
                throw new HistoryValidationException(ValidationKind.CONTRACT, "Story chronology is invalid");
            }
            previous = story.occurredFrom();
            if (!allowedEvents.containsAll(story.eventRefs())) {
                throw new HistoryValidationException(ValidationKind.CROSS_PROJECT_REFERENCE, "Cross-project or unknown event reference");
            }
            if (!allowedEvidence.containsAll(story.evidenceRefs())) {
                throw new HistoryValidationException(ValidationKind.INVALID_EVIDENCE, "Unknown Evidence reference");
            }
            if (!story.evidenceRefs().containsAll(story.reasonEvidenceRefs())) {
                throw new HistoryValidationException(ValidationKind.INVALID_EVIDENCE, "Reason Evidence is invalid");
            }
            if (!reasonEligibleEvidence(story, eventsById).containsAll(story.reasonEvidenceRefs())) {
                throw new HistoryValidationException(ValidationKind.INVALID_EVIDENCE, "Reason Evidence is not authoritative enough");
            }
            if (!story.reason().isBlank() && story.reasonEvidenceRefs().isEmpty()) {
                throw new HistoryValidationException(ValidationKind.INVALID_EVIDENCE, "Reason has no Evidence");
            }
            if (!ProjectHistoryCorrectionService.USER_DECLARED_PRESENTATION.equals(story.presentationAuthority())) {
                List<EventView> members = story.eventRefs().stream().map(eventsById::get)
                    .filter(java.util.Objects::nonNull).toList();
                String subjectLabel = languageService.readableObject(
                    story.primarySubjectKey(),
                    members.stream().flatMap(event -> event.paths().stream()).distinct().limit(40).toList(),
                    members.stream().filter(event -> event.category() != Category.FILE_CHANGE)
                        .map(EventView::label).filter(value -> value != null && !value.isBlank()).distinct().limit(8).toList()
                );
                ProjectHistoryNarrativeEntailmentValidator.NarrativeEnvelope envelope = narrativeEnvelope(
                    story.primarySubjectKey(), subjectLabel, primaryTransition(storyTransitions(members)), members,
                    !reasonEligibleEvidence(story, eventsById).isEmpty()
                );
                try {
                    narrativeValidator.validateStory(
                        envelope, story.humanTitle(), story.oneSentenceSummary(), story.beforeState(), story.change(),
                        story.afterState(), story.reason(), story.unknowns().stream().findFirst().orElse("")
                    );
                } catch (ProjectHistoryNarrativeEntailmentValidator.NarrativeViolation violation) {
                    throw new HistoryValidationException(
                        ValidationKind.UNSUPPORTED_CLAIM,
                        "Story " + story.id() + " [" + envelope.claimState().name() + ", title="
                            + boundedPromptText(story.humanTitle(), 120) + "]: " + violation.getMessage()
                    );
                }
            }
            coveredEvents.addAll(story.eventRefs());
        }
        validateRoleGraph(result.stories());
        if (!coveredEvents.containsAll(semanticEvents)) {
            throw new HistoryValidationException(ValidationKind.CONTRACT, "Semantic event coverage failed");
        }
        Map<String, ChangeStory> storiesById = result.stories().stream().collect(
            LinkedHashMap::new, (map, story) -> map.put(story.id(), story), Map::putAll
        );
        Set<String> chapterStoryIds = new LinkedHashSet<>();
        for (HistoryChapter chapter : result.chapters()) {
            for (String storyRef : chapter.storyRefs()) {
                if (!storyIds.contains(storyRef) || !chapterStoryIds.add(storyRef)) {
                    throw new HistoryValidationException(ValidationKind.CROSS_PROJECT_REFERENCE, "Chapter membership is invalid");
                }
            }
            if (!ProjectHistoryCorrectionService.USER_DECLARED_PRESENTATION.equals(chapter.authority())) {
                List<String> primaryWording = chapter.storyRefs().stream().map(storiesById::get)
                    .filter(java.util.Objects::nonNull).filter(ChangeStory::primary)
                    .flatMap(story -> Stream.of(story.humanTitle(), story.oneSentenceSummary())).toList();
                try {
                    narrativeValidator.validateChapter(
                        chapter.title(), chapter.summary(), languageService.chapterGrounding(primaryWording)
                    );
                } catch (ProjectHistoryNarrativeEntailmentValidator.NarrativeViolation violation) {
                    throw new HistoryValidationException(ValidationKind.UNSUPPORTED_CLAIM, violation.getMessage());
                }
            }
        }
        if (!chapterStoryIds.equals(storyIds)) {
            throw new HistoryValidationException(ValidationKind.CONTRACT, "Chapter coverage is incomplete");
        }
        for (EvolutionThread thread : result.threads()) {
            if (!storyIds.containsAll(thread.storyRefs())) {
                throw new HistoryValidationException(ValidationKind.CROSS_PROJECT_REFERENCE, "Thread contains unknown story");
            }
        }
    }

    private HistoryCoverage coverage(CollectionOutcome collected, PersistedEvents persisted) {
        List<String> gaps = new ArrayList<>();
        if (!collected.gitAvailable()) gaps.add("没有可确认的 Git 历史，只能展示当前材料和已有事实。 ");
        if (collected.shallowGitHistory()) {
            gaps.add("Git 仓库是浅克隆；当前读取窗口不能代表完整项目历史。 ");
        }
        if (collected.gitAvailable() && !collected.gitCommitCountKnown()) {
            gaps.add("Git 提交总数无法确认；不能把已读取范围描述为完整历史。 ");
        } else if (collected.totalGitCommits() > collected.readGitCommits()) {
            gaps.add("Git 历史未完全读取：已读取 " + collected.readGitCommits() + " / " + collected.totalGitCommits() + " 个提交。 ");
        }
        List<ProjectHistoryEvent> current = persisted.currentEvents();
        Map<String, Integer> sourceCounts = new LinkedHashMap<>();
        current.forEach(event -> sourceCounts.merge(event.getSourceType().name(), 1, Integer::sum));
        for (int index = 1; index < current.size(); index++) {
            long days = ChronoUnit.DAYS.between(current.get(index - 1).getOccurredAt(), current.get(index).getOccurredAt());
            if (days >= 180) {
                gaps.add("在 " + date(current.get(index - 1).getOccurredAt()) + " 至 " + date(current.get(index).getOccurredAt())
                    + " 之间存在 " + days + " 天来源空档；不能据此推断项目停滞。 ");
                if (gaps.size() >= 10) break;
            }
        }
        String currentness = collected.complete() ? "CURRENT"
            : !collected.gitAvailable() && collected.projectRoot() != null ? "CURRENT_STATE_ONLY"
            : collected.projectRoot() == null ? "FACTS_ONLY"
            : "PARTIAL";
        return new HistoryCoverage(
            collected.complete(), currentness,
            collected.events().size(), persisted.currentEvents().size(), persisted.staleTotal(), persisted.invalidatedTotal(),
            Map.copyOf(sourceCounts), List.copyOf(gaps), collected.limitations()
        );
    }

    private HistoryOverviewContent overview(
        List<HistoryChapter> chapters,
        List<ChangeStory> stories,
        List<ProjectHistoryEvent> events,
        HistoryCoverage coverage
    ) {
        if (events.isEmpty()) {
            return new HistoryOverviewContent(
                "尚无可确认的历史来源。", "当前只能确认项目已存在，无法重建变化过程。", List.of(), List.of(),
                List.of(), merge(List.of("缺少可用于重建的来源事件。 "), coverage.gaps(), 20)
            );
        }
        List<HistoryChapterSummary> summaries = representativeChapters(chapters, OVERVIEW_CHAPTER_LIMIT).stream()
            .map(chapter -> new HistoryChapterSummary(
            chapter.id(), chapter.title(), chapter.summary(), chapter.from(), chapter.to(), chapter.storyCount(),
            chapter.rawEventCount(), chapter.authority()
        )).toList();
        List<String> recent = stories.stream().sorted(Comparator.comparing(ChangeStory::occurredTo).reversed()).limit(5)
            .map(story -> story.humanTitle() + "（" + date(story.occurredTo()) + "）").toList();
        List<String> conflicts = stories.stream().flatMap(story -> story.conflicts().stream()).distinct().limit(20).toList();
        List<String> unknowns = merge(
            stories.stream().flatMap(story -> story.unknowns().stream()).distinct().limit(20).toList(), coverage.gaps(), 30
        );
        ChangeStory earliestStory = stories.isEmpty() ? null : stories.get(0);
        ChangeStory latestStory = stories.isEmpty() ? null : stories.get(stories.size() - 1);
        String earliestState = earliestStory == null
            ? "已发现来源事件，但现有证据不足以形成可读的最早变化故事。"
            : "最早可确认的变化出现在 " + date(earliestStory.occurredFrom()) + "："
                + earliestStory.humanTitle() + "。" + earliestStory.afterState();
        String currentState = latestStory == null
            ? "当前存在可追溯来源，但尚不能确认项目状态如何变化。"
            : "最近可确认的变化出现在 " + date(latestStory.occurredTo()) + "："
                + latestStory.humanTitle() + "。" + latestStory.afterState()
                + "这不是对项目成功、成熟度或完成度的判断。";
        return new HistoryOverviewContent(
            earliestState,
            currentState,
            summaries, recent, conflicts, unknowns
        );
    }

    private static List<HistoryChapter> representativeChapters(List<HistoryChapter> chapters, int limit) {
        if (chapters == null || chapters.size() <= limit) return chapters == null ? List.of() : List.copyOf(chapters);
        LinkedHashSet<Integer> indices = new LinkedHashSet<>();
        for (int index = 0; index < limit; index++) {
            indices.add((int) Math.round((double) index * (chapters.size() - 1) / (limit - 1)));
        }
        return indices.stream().sorted().map(chapters::get).toList();
    }

    private void putChapterRepresentationDiagnostics(SnapshotResult snapshot, Map<String, Object> diagnostics) {
        Map<String, ChangeStory> stories = snapshot.stories().stream().collect(
            LinkedHashMap::new, (map, story) -> map.put(story.id(), story), Map::putAll
        );
        int clusterCount = 0;
        int dominantCount = 0;
        int selectedCount = 0;
        int primaryCount = 0;
        int supportingCount = 0;
        int needsSplit = 0;
        int deterministicFallback = 0;
        int modelValidated = 0;
        int minorTitleRisk = 0;
        int technicalLeaks = 0;
        int unsupportedClaims = 0;
        double weightedCoverage = 0.0;
        List<Integer> chapterSizes = new ArrayList<>();
        Set<String> seenStoryIds = new LinkedHashSet<>();
        int overlapCount = 0;
        for (HistoryChapter chapter : snapshot.chapters()) {
            List<ChangeStory> members = chapter.storyRefs().stream().map(stories::get)
                .filter(java.util.Objects::nonNull).toList();
            ProjectHistoryChapterRepresentationPlanner.Plan plan = chapterRepresentationPlanner.plan(members);
            clusterCount += plan.clusters().size();
            dominantCount += plan.dominantClusterCount();
            selectedCount += plan.selectedClusters().size();
            primaryCount += plan.primaryStoryCount();
            supportingCount += plan.supportingStoryCount();
            weightedCoverage += plan.representativePrimaryCoverage() * plan.primaryStoryCount();
            if (plan.needsSplit()) needsSplit++;
            if ("INFERRED_NON_AUTHORITATIVE".equals(chapter.authority())) modelValidated++;
            else deterministicFallback++;
            chapterSizes.add(chapter.storyRefs().size());
            for (String storyId : chapter.storyRefs()) if (!seenStoryIds.add(storyId)) overlapCount++;

            boolean representsDominant = plan.selectedClusters().stream()
                .filter(cluster -> plan.dominantClusterIds().contains(cluster.id()))
                .anyMatch(cluster -> narrativeValidator.representsChapterOutcome(chapter.title(), cluster.grounding()));
            boolean representsMinor = plan.selectedClusters().stream()
                .filter(cluster -> !plan.dominantClusterIds().contains(cluster.id()))
                .anyMatch(cluster -> narrativeValidator.representsChapterOutcome(chapter.title(), cluster.grounding()));
            if (!representsDominant && representsMinor) minorTitleRisk++;
            List<String> grounding = plan.selectedClusters().stream().flatMap(cluster -> cluster.grounding().stream())
                .distinct().toList();
            if (narrativeValidator.containsFirstLayerLeak(
                chapter.title() + " " + chapter.summary(), languageService.chapterGrounding(grounding)
            )) technicalLeaks++;
            try {
                validateChapterRepresentation(chapter.title(), chapter.summary(), plan);
            } catch (HistoryValidationException ignored) {
                unsupportedClaims++;
            }
        }
        List<Integer> sortedSizes = chapterSizes.stream().sorted().toList();
        double medianSize = sortedSizes.isEmpty() ? 0.0
            : sortedSizes.size() % 2 == 1 ? sortedSizes.get(sortedSizes.size() / 2)
            : (sortedSizes.get(sortedSizes.size() / 2 - 1) + sortedSizes.get(sortedSizes.size() / 2)) / 2.0;
        Set<String> primaryIds = snapshot.stories().stream().filter(ChangeStory::primary).map(ChangeStory::id)
            .collect(LinkedHashSet::new, Set::add, Set::addAll);
        int orphanSupporting = (int) snapshot.stories().stream().filter(ChangeStory::supporting)
            .filter(story -> story.primaryStoryId().isBlank() || !primaryIds.contains(story.primaryStoryId()))
            .count();
        int reasonWithoutEvidence = (int) snapshot.stories().stream()
            .filter(story -> story.reason() != null && !story.reason().isBlank() && story.reasonEvidenceRefs().isEmpty())
            .count();

        diagnostics.put("chapterRepresentationPlanVersion", ProjectHistoryChapterRepresentationPlanner.PLAN_VERSION);
        diagnostics.put("chapterCount", snapshot.chapters().size());
        diagnostics.put("primaryStoryCount", primaryCount);
        diagnostics.put("supportingStoryCount", supportingCount);
        diagnostics.put("chapterPrimaryStoryCount", primaryCount);
        diagnostics.put("chapterSupportingStoryCount", supportingCount);
        diagnostics.put("representativeClusterCount", clusterCount);
        diagnostics.put("dominantClusterCount", dominantCount);
        diagnostics.put("selectedRepresentativeClusterCount", selectedCount);
        diagnostics.put("representativePrimaryCoverage", primaryCount == 0 ? 0.0 : weightedCoverage / primaryCount);
        diagnostics.put("largestChapterStoryCount", sortedSizes.stream().mapToInt(Integer::intValue).max().orElse(0));
        diagnostics.put("medianChapterStoryCount", medianSize);
        diagnostics.put("largeChapterCount", sortedSizes.stream().filter(value -> value >= 12).count());
        diagnostics.put("chaptersNeedingSplit", needsSplit);
        diagnostics.put("chaptersUsingDeterministicFallback", deterministicFallback);
        diagnostics.put("chaptersUsingModelValidatedWording", modelValidated);
        diagnostics.put("chaptersWithMinorClusterTitleRisk", minorTitleRisk);
        diagnostics.put("technicalLeakCount", technicalLeaks);
        diagnostics.put("unsupportedClaimCount", unsupportedClaims);
        diagnostics.put("chapterOverlapCount", overlapCount);
        diagnostics.put("orphanSupportingCount", orphanSupporting);
        diagnostics.put("reasonWithoutEvidenceCount", reasonWithoutEvidence);
        diagnostics.put("userDeclaredChapterMutationCount", 0);
    }

    private Map<String, Object> diagnostics(
        CollectionOutcome collected,
        PersistedEvents persisted,
        String rewriteMode,
        String modelStatus,
        int requestCount,
        boolean cacheHit,
        int storyCount,
        int threadCount,
        String reconstructionMode,
        int reusedStoryCount,
        int recomputedStoryCount,
        int modelEnhancedStoryCount,
        int boundedDeterministicStoryCount,
        int rejectedInvalidEvidenceRefCount,
        int rejectedCrossProjectRefCount,
        int rejectedUnsupportedClaimCount,
        int promptCharacterCount,
        int promptOmittedStoryCount,
        int promptOmittedChapterCount,
        int windowCount,
        int windowCacheHitCount,
        int checkpointCount,
        int processedWindowCount,
        int unprocessedWindowCount,
        int unprocessedStoryCount
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("strategyVersion", STRATEGY_VERSION);
        result.put("promptVersion", PROMPT_VERSION);
        result.put("rewriteMode", rewriteMode);
        result.put("cacheHit", cacheHit);
        result.put("modelStatus", modelStatus);
        result.put("requestCount", requestCount);
        result.put("sourceEventCount", persisted.currentEvents().size());
        long rawOnlyEventCount = persisted.currentEvents().stream().map(this::view)
            .filter(event -> !semanticEligible(event)).count();
        result.put("semanticEventCount", persisted.currentEvents().size() - rawOnlyEventCount);
        result.put("rawOnlyEventCount", rawOnlyEventCount);
        result.put("addedEventCount", persisted.added());
        result.put("updatedEventCount", persisted.updated());
        result.put("reusedEventCount", persisted.reused());
        result.put("staleEventCount", persisted.staleTotal());
        result.put("invalidatedEventCount", persisted.invalidatedTotal());
        result.put("newlyStaleEventCount", persisted.stale());
        result.put("newlyInvalidatedEventCount", persisted.invalidated());
        result.put("preservedUnseenEventCount", persisted.preservedUnseen());
        result.put("storyCount", storyCount);
        result.put("threadCount", threadCount);
        result.put("reconstructionMode", reconstructionMode);
        result.put("reusedStoryCount", reusedStoryCount);
        result.put("recomputedStoryCount", recomputedStoryCount);
        result.put("modelEnhancedStoryCount", modelEnhancedStoryCount);
        result.put("boundedDeterministicStoryCount", boundedDeterministicStoryCount);
        result.put("eventConservation", true);
        result.put("invalidEvidenceRefCount", 0);
        result.put("crossProjectRefCount", 0);
        result.put("unsupportedStrongFactCount", 0);
        result.put("gitCommitCountKnown", collected.gitCommitCountKnown());
        result.put("reachableGitCommitCount", collected.totalGitCommits());
        result.put("readGitCommitCount", collected.readGitCommits());
        result.put("gitCommitReadLimit", ProjectHistorySourceCollector.MAX_COMMITS);
        result.put("sourceEventLimit", ProjectHistorySourceCollector.MAX_EVENTS);
        result.put("modelRejectedInvalidEvidenceRefCount", rejectedInvalidEvidenceRefCount);
        result.put("modelRejectedCrossProjectRefCount", rejectedCrossProjectRefCount);
        result.put("modelRejectedUnsupportedClaimCount", rejectedUnsupportedClaimCount);
        result.put("modelPromptCharacterCount", promptCharacterCount);
        result.put("modelPromptOmittedStoryCount", promptOmittedStoryCount);
        result.put("modelPromptOmittedChapterCount", promptOmittedChapterCount);
        result.put("modelWindowCount", windowCount);
        result.put("modelProcessedWindowCount", processedWindowCount);
        result.put("modelUnprocessedWindowCount", unprocessedWindowCount);
        result.put("modelUnprocessedStoryCount", unprocessedStoryCount);
        result.put("modelWindowCacheHitCount", windowCacheHitCount);
        result.put("modelCheckpointCount", checkpointCount);
        result.put("complete", collected.complete());
        return result;
    }

    private EventView view(ProjectHistoryEvent event) {
        return new EventView(
            event.getId(), event.getStableEventKey(), event.getSourceType(), event.getSourceIdentity(),
            event.getSourceRevision(), event.getOccurredAt(), event.getCategory(), event.getTransition(),
            event.getSafeSourceLabel(), strings(event.getAffectedPathsJson()), strings(event.getSubjectKeysJson()),
            strings(event.getEvidenceRefsJson()), strings(event.getRelationRefsJson()), event.getAuthority(),
            event.getEpistemicStatus(), strings(event.getLimitationsJson())
        );
    }

    private static boolean semanticEligible(EventView event) {
        return event.subjectKeys().isEmpty()
            || event.subjectKeys().stream().anyMatch(subject -> !RAW_ONLY_SUBJECTS.contains(subject));
    }

    private static Transition primaryTransition(List<Transition> transitions) {
        List<Transition> priority = List.of(
            Transition.RESTORED, Transition.REAPPLIED, Transition.REPLACED, Transition.REMOVED,
            Transition.REVERTED, Transition.SPLIT, Transition.MERGED, Transition.MOVED, Transition.RENAMED,
            Transition.CREATED, Transition.MODIFIED, Transition.UNKNOWN_TRANSITION
        );
        return priority.stream().filter(transitions::contains).findFirst().orElse(Transition.UNKNOWN_TRANSITION);
    }

    private static String title(Transition transition, String subject) {
        return switch (transition) {
            case CREATED -> "建立“" + subject + "”，项目开始具备这项能力";
            case MODIFIED -> "完善“" + subject + "”，当前行为得到更新";
            case REMOVED -> "移除“" + subject + "”并结束当前实现";
            case RESTORED -> "恢复“" + subject + "”并重新纳入项目";
            case RENAMED -> "重命名“" + subject + "”并保留历史关联";
            case MOVED -> "迁移“" + subject + "”并保留历史关联";
            case REPLACED -> "替换“" + subject + "”并启用新结果";
            case SPLIT -> "拆分“" + subject + "”并形成多个结果";
            case MERGED -> "合并“" + subject + "”并形成统一结果";
            case REVERTED -> "撤销“" + subject + "”并回退已有变化";
            case REAPPLIED -> "重新实现“" + subject + "”并恢复变化";
            default -> "记录“" + subject + "”的可确认变化";
        };
    }

    private static String beforeState(Transition transition) {
        return switch (transition) {
            case CREATED -> "此前在已覆盖来源中尚未观察到该项目要素。";
            case RESTORED, REAPPLIED -> "更早的历史表明，这项项目结果曾被移除、撤销或暂时失效。";
            case REMOVED -> "此前该项目要素仍存在于已覆盖项目状态中。";
            default -> "此前状态只按更早的来源事件保留，未从当前代码反推历史。";
        };
    }

    private static String afterState(Transition transition, String subject) {
        return switch (transition) {
            case REMOVED, REVERTED -> "变化后，“" + subject + "”在该时间点被移除或撤销。";
            case RESTORED, REAPPLIED -> "变化后，“" + subject + "”重新出现在项目中。";
            case CREATED -> "变化后，项目开始具备“" + subject + "”这项结果。";
            case RENAMED, MOVED -> "变化后，“" + subject + "”以新位置或名称继续存在。";
            case REPLACED -> "变化后，“" + subject + "”由新的结果接替。";
            default -> "变化后，“" + subject + "”呈现出当前历史能够确认的状态。";
        };
    }

    private static String transitionName(Transition transition) {
        return switch (transition) {
            case CREATED -> "新增";
            case MODIFIED -> "修改";
            case REMOVED -> "删除";
            case RESTORED -> "恢复";
            case RENAMED -> "重命名";
            case MOVED -> "移动";
            case REPLACED -> "替换";
            case SPLIT -> "拆分";
            case MERGED -> "合并";
            case REVERTED -> "撤销";
            case REAPPLIED -> "重新实现";
            default -> "未知转换";
        };
    }

    private static List<String> gaps(List<StoryEnvelope> stories) {
        List<String> gaps = new ArrayList<>();
        for (int index = 1; index < stories.size(); index++) {
            long days = ChronoUnit.DAYS.between(stories.get(index - 1).story().occurredTo(), stories.get(index).story().occurredFrom());
            if (days >= 90) gaps.add("相邻变化故事之间存在 " + days + " 天来源空档。 ");
        }
        return gaps.stream().distinct().limit(10).toList();
    }

    private static boolean storyContainsEvent(ChangeStory story, Set<UUID> eventIds) {
        return story.eventRefs().stream().anyMatch(eventIds::contains);
    }

    private static boolean sharesCommit(List<EventView> current, EventView next) {
        Set<String> nextCommits = commitRefs(next);
        if (nextCommits.isEmpty()) return false;
        return current.stream().map(ProjectHistoryReconstructionService::commitRefs)
            .anyMatch(refs -> refs.stream().anyMatch(nextCommits::contains));
    }

    private static Comparator<EventView> storyEventOrder(List<EventView> events) {
        Map<String, Integer> gitRevisionOrder = gitRevisionOrder(events);
        return Comparator.comparing(EventView::occurredAt)
            .thenComparingInt(event -> gitRevisionOrder.getOrDefault(event.sourceRevision(), Integer.MAX_VALUE))
            .thenComparing(EventView::sourceRevision)
            .thenComparingInt(event -> ProjectHistorySourceCollector.eventCategoryOrder(event.category()))
            .thenComparing(event -> event.sourceType().name())
            .thenComparing(EventView::sourceIdentity)
            .thenComparing(event -> event.transition().name())
            .thenComparing(event -> String.join("\u0000", event.paths()))
            .thenComparing(event -> String.join("\u0000", event.evidenceRefs()))
            .thenComparing(EventView::stableKey);
    }

    private static Map<String, Integer> gitRevisionOrder(List<EventView> events) {
        Map<String, EventView> commits = new LinkedHashMap<>();
        for (EventView event : events) {
            if (event.sourceType() == SourceType.GIT
                && Set.of(Category.COMMIT, Category.MERGE).contains(event.category())
                && !event.sourceRevision().isBlank()) {
                commits.putIfAbsent(event.sourceRevision(), event);
            }
        }
        Map<String, Integer> indegree = new LinkedHashMap<>();
        Map<String, Set<String>> children = new LinkedHashMap<>();
        commits.keySet().forEach(revision -> indegree.put(revision, 0));
        for (Map.Entry<String, EventView> entry : commits.entrySet()) {
            Set<String> parents = entry.getValue().relationRefs().stream()
                .filter(value -> value.startsWith("parent:"))
                .map(value -> value.substring("parent:".length()))
                .filter(commits::containsKey)
                .collect(LinkedHashSet::new, Set::add, Set::addAll);
            indegree.put(entry.getKey(), parents.size());
            parents.forEach(parent -> children.computeIfAbsent(parent, ignored -> new LinkedHashSet<>()).add(entry.getKey()));
        }
        Comparator<String> revisionOrder = Comparator
            .comparing((String revision) -> commits.get(revision).occurredAt())
            .thenComparing(revision -> commits.get(revision).sourceIdentity())
            .thenComparing(revision -> revision);
        PriorityQueue<String> ready = new PriorityQueue<>(revisionOrder);
        indegree.forEach((revision, count) -> {
            if (count == 0) ready.add(revision);
        });
        Map<String, Integer> result = new LinkedHashMap<>();
        while (!ready.isEmpty()) {
            String revision = ready.remove();
            result.put(revision, result.size());
            for (String child : children.getOrDefault(revision, Set.of())) {
                int remaining = indegree.computeIfPresent(child, (ignored, count) -> count - 1);
                if (remaining == 0) ready.add(child);
            }
        }
        commits.keySet().stream().filter(revision -> !result.containsKey(revision)).sorted(revisionOrder)
            .forEach(revision -> result.put(revision, result.size()));
        return result;
    }

    private static Set<String> commitRefs(EventView event) {
        return Stream.concat(event.evidenceRefs().stream(), event.relationRefs().stream())
            .filter(value -> value != null && value.startsWith("commit:"))
            .collect(LinkedHashSet::new, Set::add, Set::addAll);
    }

    private static String area(String path) {
        if (path == null || path.isBlank()) return "";
        String[] segments = path.replace('\\', '/').split("/");
        if (segments.length == 1) return "项目根目录";
        if (segments.length >= 2 && Set.of("backend", "frontend", "docs", "integrations", "scripts", "tests").contains(segments[0])) {
            return segments[0] + (segments.length >= 3 ? "/" + segments[1] : "");
        }
        return segments[0];
    }

    private static String date(Instant value) {
        if (value == null || value.equals(Instant.EPOCH)) return "时间未知";
        return LocalDate.ofInstant(value, ZoneOffset.UTC).toString();
    }

    private static Instant earliest(List<ProjectHistoryEvent> events) {
        return events.stream().map(ProjectHistoryEvent::getOccurredAt).min(Instant::compareTo).orElse(null);
    }

    private static Instant latest(List<ProjectHistoryEvent> events) {
        return events.stream().map(ProjectHistoryEvent::getOccurredAt).max(Instant::compareTo).orElse(null);
    }

    private static Instant earlier(Instant left, Instant right) {
        if (left == null) return right;
        if (right == null) return left;
        return left.isBefore(right) ? left : right;
    }

    private static int boundedCount(long value) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, value));
    }

    private static String fingerprint(List<ProjectHistoryEvent> events) {
        StringBuilder value = new StringBuilder("project-history-source-v1\n");
        events.stream().sorted(Comparator.comparing(ProjectHistoryEvent::getStableEventKey)).forEach(event ->
            value.append(event.getStableEventKey()).append(':').append(event.getPayloadHash()).append('\n')
        );
        return ProjectHistorySourceCollector.sha256(value.toString());
    }

    private ChangeStory copyStory(
        ChangeStory original,
        String laterOutcome,
        String authority,
        String summaryStatus,
        String title,
        String summary,
        String before,
        String change,
        String after,
        String reason,
        List<String> reasonEvidence,
        List<String> conflicts,
        List<String> unknowns
    ) {
        return new ChangeStory(
            original.id(), original.primarySubjectKey(), title, summary, before, change, after, original.affectedAreas(),
            reason, reasonEvidence, laterOutcome, conflicts, unknowns, original.occurredFrom(), original.occurredTo(),
            original.evidenceCount(), original.rawEventCount(), authority, summaryStatus, original.coverage(),
            original.limitations(), original.eventRefs(), original.evidenceRefs(), original.role(),
            original.primaryStoryId(), original.supportingChangeRefs(), original.technicalAtomRefs(),
            original.commitSummaries(), original.technicalDetails(), original.presentationAuthority(),
            original.presentationRevision(), original.automaticTitle(), original.automaticSummary(),
            original.userCorrectionRefs(), original.hiddenByDefault(), original.pinned(), original.mergedIntoStoryId(),
            original.displayStatus(), original.correctionConflicts(), original.claimAttribution()
        );
    }

    private Set<String> semanticModelCandidates(List<ChangeStory> stories, Set<String> recomputedStoryIds) {
        List<ChangeStory> candidates = stories.stream()
            .filter(ChangeStory::primary)
            .filter(story -> recomputedStoryIds.contains(story.id()))
            .toList();
        Map<String, List<ChangeStory>> byFamily = new LinkedHashMap<>();
        candidates.forEach(story -> byFamily.computeIfAbsent(presentationFamily(story), ignored -> new ArrayList<>()).add(story));
        if (byFamily.values().stream().noneMatch(group -> group.size() > 1)) {
            return candidates.stream().map(ChangeStory::id)
                .collect(LinkedHashSet::new, Set::add, Set::addAll);
        }

        long eventCount = candidates.stream().mapToLong(story -> story.eventRefs().size()).sum();
        int averageEvents = Math.max(1, (int) Math.ceil((double) eventCount / Math.max(1, candidates.size())));
        int target = Math.max(byFamily.size(), Math.min(
            ProjectHistoryWindowPlanner.DEFAULT_STORY_LIMIT,
            Math.max(1, ProjectHistoryWindowPlanner.DEFAULT_EVENT_LIMIT / averageEvents)
        ));
        if (candidates.size() <= target) {
            return candidates.stream().map(ChangeStory::id)
                .collect(LinkedHashSet::new, Set::add, Set::addAll);
        }

        Set<String> selected = new LinkedHashSet<>();
        for (int depth = 0; selected.size() < target; depth++) {
            boolean added = false;
            for (List<ChangeStory> family : byFamily.values()) {
                int index = depth % 2 == 0 ? depth / 2 : family.size() - 1 - depth / 2;
                if (index < 0 || index >= family.size()) continue;
                added |= selected.add(family.get(index).id());
                if (selected.size() >= target) break;
            }
            if (!added) break;
        }
        return candidates.stream().map(ChangeStory::id).filter(selected::contains)
            .collect(LinkedHashSet::new, Set::add, Set::addAll);
    }

    private String modelText(JsonNode node, String field, int max) {
        String value = node.path(field).isTextual() ? node.path(field).asText().trim() : "";
        value = redactor.redactOutboundText(value);
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }

    private List<String> stringList(JsonNode node, int limit) {
        if (!node.isArray()) return List.of();
        List<String> result = new ArrayList<>();
        node.forEach(value -> {
            if (value.isTextual() && result.size() < limit) {
                String safe = redactor.redactOutboundText(value.asText().trim());
                if (!safe.isBlank()) result.add(safe.length() <= 500 ? safe : safe.substring(0, 499) + "…");
            }
        });
        return result.stream().distinct().toList();
    }

    private List<String> strings(String json) {
        try {
            return objectMapper.readValue(json == null || json.isBlank() ? "[]" : json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("项目历程 JSON 无法序列化", exception);
        }
    }

    private static String text(JsonNode node, String field) {
        return node.path(field).isTextual() ? node.path(field).asText().trim() : "";
    }

    private void validateRoleGraph(Collection<ChangeStory> values) {
        try {
            presentationInvariantValidator.validateRoleGraph(values);
        } catch (ProjectHistoryPresentationInvariantValidator.Violation exception) {
            ValidationKind kind = switch (exception.kind()) {
                case CROSS_PROJECT_REFERENCE -> ValidationKind.CROSS_PROJECT_REFERENCE;
                case UNSUPPORTED_CLAIM -> ValidationKind.UNSUPPORTED_CLAIM;
                case CONTRACT -> ValidationKind.CONTRACT;
            };
            throw new HistoryValidationException(kind, exception.getMessage());
        }
    }

    private static void requireAllowedFields(JsonNode node, Set<String> allowed, String message) {
        if (node == null || !node.isObject()) {
            throw new HistoryValidationException(ValidationKind.CONTRACT, message);
        }
        node.fieldNames().forEachRemaining(field -> {
            if (!allowed.contains(field)) {
                throw new HistoryValidationException(ValidationKind.UNSUPPORTED_CLAIM, message + ": " + field);
            }
        });
    }

    private static boolean weak(String value) {
        String safe = value == null ? "" : value.trim();
        return safe.length() < 4 || WEAK_TEXT.stream().anyMatch(item -> safe.equals(item) || safe.startsWith(item + "。"));
    }

    private static boolean prohibitedAuthorityClaim(String value) {
        String safe = value == null ? "" : value;
        return StreamSupport.containsAny(safe, List.of(
            "成熟阶段", "成熟度", "成功阶段", "关键里程碑", "达到里程碑", "项目成功", "已成功", "成功完成",
            "进入稳定阶段", "进入完成阶段", "下一步", "路线图", "未来计划"
        ));
    }

    private String safeError(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) message = exception.getClass().getSimpleName();
        message = redactor.redactOutboundText(message);
        return message.length() <= 500 ? message : message.substring(0, 499) + "…";
    }

    private static <T> List<T> merge(Collection<T> left, Collection<T> right, int limit) {
        LinkedHashSet<T> values = new LinkedHashSet<>();
        if (left != null) values.addAll(left);
        if (right != null) values.addAll(right);
        return values.stream().limit(limit).toList();
    }

    private static <T> List<T> append(List<T> values, T item, int limit) {
        List<T> result = new ArrayList<>(values == null ? List.of() : values);
        if (!result.contains(item)) result.add(item);
        return result.stream().limit(limit).toList();
    }

    public record HistoryRefreshOutcome(
        String resultJson,
        List<ModelGatewayService.ModelCallDiagnostics> diagnostics,
        boolean modelUsed,
        boolean degraded,
        boolean cacheHit
    ) {
    }

    @FunctionalInterface
    public interface HistoryProgress {
        void update(String stage, String message);
    }

    private record PersistedEvents(
        List<ProjectHistoryEvent> currentEvents,
        int added,
        int updated,
        int reused,
        int stale,
        int invalidated,
        int staleTotal,
        int invalidatedTotal,
        int preservedUnseen,
        Instant affectedFrom,
        String rewriteMode
    ) {
    }

    private record EventView(
        UUID id,
        String stableKey,
        SourceType sourceType,
        String sourceIdentity,
        String sourceRevision,
        Instant occurredAt,
        Category category,
        Transition transition,
        String label,
        List<String> paths,
        List<String> subjectKeys,
        List<String> evidenceRefs,
        List<String> relationRefs,
        Authority authority,
        ProjectFactEpistemicStatus epistemicStatus,
        List<String> limitations
    ) {
    }

    private record StoryEnvelope(ChangeStory story, List<Transition> transitions) {
    }

    private record SnapshotResult(
        List<HistoryChapter> chapters,
        List<ChangeStory> stories,
        List<EvolutionThread> threads
    ) {
    }

    private record DeterministicResult(
        SnapshotResult result,
        Set<String> recomputedStoryIds,
        String reconstructionMode,
        int reusedStoryCount
    ) {
    }

    private record ModelResult(
        SnapshotResult result,
        boolean used,
        boolean failed,
        String status,
        String failureSummary,
        int enhancedStoryCount,
        int boundedStoryCount,
        int rejectedInvalidEvidenceRefCount,
        int rejectedCrossProjectRefCount,
        int rejectedUnsupportedClaimCount,
        int promptCharacterCount,
        int promptOmittedStoryCount,
        int promptOmittedChapterCount,
        int windowCount,
        int processedWindowCount,
        int unprocessedWindowCount,
        int unprocessedStoryCount,
        int windowCacheHitCount,
        int checkpointCount,
        boolean incomplete,
        int succeededWindowCount,
        int failedWindowCount,
        int skippedWindowCount,
        int skippedStoryCount,
        int pendingWindowCount,
        int nextWindowOrdinal,
        Set<String> activeCheckpointCacheKeys,
        int chapterSynthesisCount,
        int chapterSynthesisProcessedCount,
        int chapterSynthesisCacheHitCount,
        int chapterSynthesisFailedCount,
        int chapterSynthesisPendingCount,
        int chapterSynthesisPromptCharacterCount,
        int chapterSynthesisOmittedStoryCount
    ) {
        private ModelResult {
            activeCheckpointCacheKeys = java.util.Collections.unmodifiableSet(new LinkedHashSet<>(
                activeCheckpointCacheKeys == null ? Set.of() : activeCheckpointCacheKeys
            ));
        }

        private static ModelResult notUsed(SnapshotResult result, String status, int boundedStoryCount) {
            return new ModelResult(result, false, false, status, "", 0, boundedStoryCount, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, false, 0, 0, 0, 0, 0, -1, Set.of(),
                0, 0, 0, 0, 0, 0, 0);
        }
    }

    private record ChapterSynthesisResult(
        SnapshotResult result,
        boolean used,
        boolean failed,
        boolean systemicFailure,
        boolean incomplete,
        String failureSummary,
        int candidateCount,
        int processedCount,
        int cacheHitCount,
        int failedCount,
        int pendingCount,
        int checkpointCount,
        int promptCharacterCount,
        int omittedStoryCount,
        Set<String> activeCheckpointCacheKeys
    ) {
        private ChapterSynthesisResult {
            failureSummary = failureSummary == null ? "" : failureSummary;
            activeCheckpointCacheKeys = java.util.Collections.unmodifiableSet(new LinkedHashSet<>(
                activeCheckpointCacheKeys == null ? Set.of() : activeCheckpointCacheKeys
            ));
        }

        private static ChapterSynthesisResult notRequired(SnapshotResult result) {
            return new ChapterSynthesisResult(
                result, false, false, false, false, "", 0, 0, 0, 0, 0, 0, 0, 0, Set.of()
            );
        }
    }

    private record ModelBatchPlan(List<ModelBatch> batches, int totalWindowCount) {
        private ModelBatchPlan {
            batches = batches == null ? List.of() : List.copyOf(batches);
            totalWindowCount = Math.max(0, totalWindowCount);
        }
    }

    private record ModelBatch(
        String identity,
        String cacheKey,
        String sourceFingerprint,
        Set<String> storyIds,
        Set<String> chapterIds,
        Set<UUID> eventIds,
        boolean promptOverflow
    ) {
        private ModelBatch {
            storyIds = java.util.Collections.unmodifiableSet(new LinkedHashSet<>(storyIds == null ? Set.of() : storyIds));
            chapterIds = java.util.Collections.unmodifiableSet(new LinkedHashSet<>(chapterIds == null ? Set.of() : chapterIds));
            eventIds = java.util.Collections.unmodifiableSet(new LinkedHashSet<>(eventIds == null ? Set.of() : eventIds));
        }

        private ModelBatch withPromptOverflow(boolean value) {
            return new ModelBatch(identity, cacheKey, sourceFingerprint, storyIds, chapterIds, eventIds, value);
        }

        private ModelBatch withChapterIds(Set<String> values) {
            return new ModelBatch(identity, cacheKey, sourceFingerprint, storyIds, values, eventIds, promptOverflow);
        }
    }

    private record ChapterBatch(
        String identity,
        String cacheKey,
        String sourceFingerprint,
        String chapterId,
        Set<String> storyIds
    ) {
        private ChapterBatch {
            storyIds = java.util.Collections.unmodifiableSet(new LinkedHashSet<>(storyIds == null ? Set.of() : storyIds));
        }
    }

    @FunctionalInterface
    private interface HistoryResponseParser<T> {
        T parse(JsonNode root);
    }

    private record ValidatedHistoryResponse<T>(
        ModelGatewayService.StructuredModelResponse response,
        T value
    ) {
    }

    private enum ValidationKind {
        INVALID_EVIDENCE,
        CROSS_PROJECT_REFERENCE,
        UNSUPPORTED_CLAIM,
        CONTRACT
    }

    static final class HistoryValidationException extends RuntimeException {
        private final ValidationKind kind;

        HistoryValidationException(ValidationKind kind, String message) {
            super(message);
            this.kind = kind == null ? ValidationKind.CONTRACT : kind;
        }

        ValidationKind kind() { return kind; }
    }

    /** Avoids regex-driven semantic parsing; this is only a bounded prohibited-phrase check. */
    private static final class StreamSupport {
        private StreamSupport() {
        }

        static boolean containsAny(String value, List<String> phrases) {
            return phrases.stream().anyMatch(value::contains);
        }
    }
}
