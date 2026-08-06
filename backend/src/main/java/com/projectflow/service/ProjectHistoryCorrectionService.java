package com.projectflow.service;

import static com.projectflow.dto.ProjectHistoryDtos.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.entity.ProjectHistoryCorrection;
import com.projectflow.entity.ProjectHistoryEvent;
import com.projectflow.entity.ProjectHistorySnapshot;
import com.projectflow.repository.ProjectHistoryCorrectionRepository;
import com.projectflow.repository.ProjectHistoryEventRepository;
import com.projectflow.repository.ProjectHistorySnapshotRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.support.AppException;

/**
 * Resolves the durable presentation overlay for Project History.  The service
 * deliberately has no ProjectFact or ProjectHistoryEvent write dependency.
 */
@Service
public class ProjectHistoryCorrectionService {
    public static final String USER_DECLARED_PRESENTATION = "USER_DECLARED_PRESENTATION";
    private static final int MAX_ACTIVE_CORRECTIONS = 2_000;
    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_TARGET_IDS = 100;
    private static final int MAX_TARGET_ID_LENGTH = 180;
    private static final int MAX_TITLE_LENGTH = 8_000;
    private static final int MAX_SUMMARY_LENGTH = 12_000;
    private static final Set<String> TYPES = Set.of(
        "RENAME_STORY", "EDIT_SUMMARY", "MERGE_STORIES", "SPLIT_STORY", "SET_PRIMARY",
        "SET_SUPPORTING", "REATTACH_SUPPORTING", "HIDE_STORY", "PIN_STORY",
        "DECLARE_CHAPTER", "RENAME_CHAPTER", "RESTORE_AUTOMATIC"
    );

    private final ProjectRepository projectRepository;
    private final ProjectHistorySnapshotRepository snapshotRepository;
    private final ProjectHistoryCorrectionRepository correctionRepository;
    private final ProjectHistoryEventRepository eventRepository;
    private final ProjectHistoryPresentationInvariantValidator presentationInvariantValidator;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate conflictTransactionTemplate;

    public ProjectHistoryCorrectionService(
        ProjectRepository projectRepository,
        ProjectHistorySnapshotRepository snapshotRepository,
        ProjectHistoryCorrectionRepository correctionRepository,
        ProjectHistoryEventRepository eventRepository,
        ProjectHistoryPresentationInvariantValidator presentationInvariantValidator,
        ObjectMapper objectMapper,
        PlatformTransactionManager transactionManager
    ) {
        this.projectRepository = projectRepository;
        this.snapshotRepository = snapshotRepository;
        this.correctionRepository = correctionRepository;
        this.eventRepository = eventRepository;
        this.presentationInvariantValidator = presentationInvariantValidator;
        this.objectMapper = objectMapper;
        this.conflictTransactionTemplate = new TransactionTemplate(transactionManager);
        this.conflictTransactionTemplate.setPropagationBehavior(
            org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW
        );
    }

    @Transactional(readOnly = true)
    public HistoryCorrectionListResponse list(UUID userId, UUID projectId) {
        return list(userId, projectId, 0, DEFAULT_PAGE_SIZE);
    }

    @Transactional(readOnly = true)
    public HistoryCorrectionListResponse list(UUID userId, UUID projectId, int page, int size) {
        owned(userId, projectId);
        ProjectHistorySnapshot snapshot = snapshotRepository.findByProjectId(projectId).orElse(null);
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(MAX_PAGE_SIZE, size <= 0 ? DEFAULT_PAGE_SIZE : size));
        var pageResult = correctionRepository.findByProjectIdOrderByCreatedAtAscIdAsc(
            projectId, PageRequest.of(safePage, safeSize)
        );
        List<ProjectHistoryCorrection> active = activeCorrections(projectId);
        String revision = presentationRevision(snapshot, active);
        List<ChangeStory> stories = snapshot == null ? List.of()
            : read(snapshot.getStoriesJson(), new TypeReference<List<ChangeStory>>() {});
        List<HistoryChapter> chapters = snapshot == null ? List.of()
            : read(snapshot.getChaptersJson(), new TypeReference<List<HistoryChapter>>() {});
        Map<String, ChangeStory> storyMap = indexStories(stories);
        Map<String, HistoryChapter> chapterMap = indexChapters(chapters);
        return new HistoryCorrectionListResponse(
            projectId, pageResult.getContent().stream()
                .map(value -> indexedResponse(value, snapshot, revision, null, storyMap, chapterMap)).toList(), revision,
            pageResult.hasNext(), safePage, safeSize, pageResult.getTotalElements(), active.size(), MAX_ACTIVE_CORRECTIONS,
            active.size() > MAX_ACTIVE_CORRECTIONS
        );
    }

    /** Applies active corrections to a persisted snapshot without writing it back. */
    @Transactional(readOnly = true)
    public CorrectedHistory resolve(UUID projectId, ProjectHistorySnapshot snapshot) {
        if (snapshot == null) return new CorrectedHistory(List.of(), List.of(), List.of(), "", List.of());
        List<ChangeStory> stories = read(snapshot.getStoriesJson(), new TypeReference<List<ChangeStory>>() {});
        List<HistoryChapter> chapters = read(snapshot.getChaptersJson(), new TypeReference<List<HistoryChapter>>() {});
        List<EvolutionThread> threads = read(snapshot.getThreadsJson(), new TypeReference<List<EvolutionThread>>() {});
        List<ProjectHistoryCorrection> corrections = activeCorrections(projectId);
        Map<String, ChangeStory> automaticStories = stories.stream().collect(
            LinkedHashMap::new, (map, value) -> map.put(value.id(), value), Map::putAll
        );
        Map<String, HistoryChapter> automaticChapters = chapters.stream().collect(
            LinkedHashMap::new, (map, value) -> map.put(value.id(), value), Map::putAll
        );
        Map<String, ChangeStory> storyMap = stories.stream().collect(
            LinkedHashMap::new, (map, value) -> map.put(value.id(), value), Map::putAll
        );
        Map<String, HistoryChapter> chapterMap = chapters.stream().collect(
            LinkedHashMap::new, (map, value) -> map.put(value.id(), value), Map::putAll
        );
        Map<String, EvolutionThread> threadMap = threads.stream().collect(
            LinkedHashMap::new, (map, value) -> map.put(value.id(), value), Map::putAll
        );
        Map<UUID, ProjectHistoryEvent> eventMap = loadEvents(projectId, stories);
        List<HistoryCorrectionResponse> applied = new ArrayList<>();
        String revision = presentationRevision(snapshot, corrections);
        for (ProjectHistoryCorrection correction : corrections) {
            String type = correction.getCorrectionType().toUpperCase(Locale.ROOT);
            List<String> ids = targetIds(correction);
            CorrectionState correctionState = correctionState(correction, snapshot, automaticStories, automaticChapters);
            if ("RESTORE_AUTOMATIC".equals(type)) {
                applied.add(indexedResponse(correction, snapshot, revision, null, automaticStories, automaticChapters));
                continue;
            }
            boolean valid = targetsValid(correction, ids, storyMap, chapterMap);
            if (!valid || correctionState.conflict()) {
                String reason = correctionState.conflict()
                    ? correctionState.reason()
                    : "修正目标已不存在或已被历史重建替换";
                for (String id : ids) {
                    ChangeStory story = storyMap.get(id);
                    if (story != null) storyMap.put(id, addConflict(story, correction.getId().toString(), reason));
                    HistoryChapter chapter = chapterMap.get(id);
                    if (chapter != null) chapterMap.put(id, addChapterConflict(chapter, correction.getId().toString(), reason));
                }
                applied.add(indexedResponse(correction, snapshot, revision, reason, automaticStories, automaticChapters));
                continue;
            }
            applyCorrection(projectId, correction, ids, storyMap, chapterMap, threadMap, eventMap);
            applied.add(indexedResponse(correction, snapshot, revision, null, automaticStories, automaticChapters));
        }
        presentationInvariantValidator.validateCorrectedHistory(storyMap, chapterMap, threadMap);
        List<ChangeStory> resolvedStories = storyMap.values().stream()
            .sorted(java.util.Comparator.comparing(ChangeStory::occurredFrom, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))
                .thenComparing(ChangeStory::id)).toList();
        List<HistoryChapter> resolvedChapters = chapterMap.values().stream()
            .sorted(java.util.Comparator.comparing(HistoryChapter::from, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))
                .thenComparing(HistoryChapter::id)).toList();
        List<EvolutionThread> resolvedThreads = threadMap.values().stream()
            .sorted(java.util.Comparator.comparing(EvolutionThread::subjectLabel).thenComparing(EvolutionThread::id)).toList();
        return new CorrectedHistory(
            resolvedChapters, resolvedStories, resolvedThreads,
            revision, List.copyOf(applied)
        );
    }

    /**
     * Returns the current presentation overlay revision for a refresh cache key.
     * The value contains only a stable hash and correction metadata; no declared
     * text is returned or persisted by the history reconstruction layer.
     */
    @Transactional(readOnly = true)
    public String currentPresentationRevision(UUID projectId, String sourceFingerprint) {
        return presentationRevision(safe(sourceFingerprint), activeCorrections(projectId));
    }

    @Transactional(readOnly = true)
    public List<String> currentWindowPresentationRevisions(UUID projectId, List<WindowRevisionScope> scopes) {
        List<ProjectHistoryCorrection> active = activeCorrections(projectId);
        List<String> revisions = new ArrayList<>();
        for (WindowRevisionScope scope : scopes == null ? List.<WindowRevisionScope>of() : scopes) {
            Set<String> targets = scope == null ? Set.of() : scope.targetIds();
            List<ProjectHistoryCorrection> relevant = active.stream()
                .filter(correction -> targetIds(correction).stream().anyMatch(targets::contains))
                .toList();
            revisions.add(presentationRevision(scope == null ? "" : scope.sourceFingerprint(), relevant));
        }
        return List.copyOf(revisions);
    }

    public record WindowRevisionScope(String sourceFingerprint, Set<String> targetIds) {
        public WindowRevisionScope {
            sourceFingerprint = sourceFingerprint == null ? "" : sourceFingerprint;
            targetIds = Set.copyOf(targetIds == null ? Set.of() : targetIds);
        }
    }

    @Transactional
    public HistoryCorrectionResponse create(UUID userId, UUID projectId, HistoryCorrectionRequest request) {
        owned(userId, projectId);
        if (request == null) throw bad("PROJECT_HISTORY_CORRECTION_REQUIRED", "必须提供项目历程修正");
        validateRequestBounds(request);
        ProjectHistorySnapshot snapshot = snapshotRepository.findLockedByProjectId(projectId).orElseThrow(() ->
            new AppException("PROJECT_HISTORY_NOT_INITIALIZED", "项目历程尚未刷新，暂时不能修正", HttpStatus.CONFLICT));
        List<ProjectHistoryCorrection> existing = activeCorrections(projectId);
        String currentRevision = presentationRevision(snapshot, existing);
        String expected = safe(request.expectedPresentationRevision());
        String source = safe(request.sourceFingerprint());
        String currentSource = safe(snapshot.getSourceEventFingerprint());
        String type = safe(request.type()).toUpperCase(Locale.ROOT);
        if (!TYPES.contains(type)) throw bad("INVALID_HISTORY_CORRECTION_TYPE", "不支持的项目历程修正类型");
        String normalizedTargetId = normalizedTargetId(type, request, projectId);
        List<String> normalizedTargetIds = normalizedTargetIds(type, request);
        List<ChangeStory> stories = read(snapshot.getStoriesJson(), new TypeReference<List<ChangeStory>>() {});
        List<HistoryChapter> chapters = read(snapshot.getChaptersJson(), new TypeReference<List<HistoryChapter>>() {});
        Map<String, ChangeStory> storyMap = stories.stream().collect(
            LinkedHashMap::new, (map, value) -> map.put(value.id(), value), Map::putAll
        );
        Map<String, HistoryChapter> chapterMap = chapters.stream().collect(
            LinkedHashMap::new, (map, value) -> map.put(value.id(), value), Map::putAll
        );
        String targetMembership = targetMembershipFingerprint(
            type, normalizedTargetId, normalizedTargetIds, storyMap, chapterMap
        );
        String automaticPresentation = automaticPresentationFingerprint(
            type, normalizedTargetId, normalizedTargetIds, storyMap, chapterMap
        );
        if (!expected.isBlank() && !expected.equals(currentRevision)) {
            persistConflict(newCorrection(projectId, userId, type, request, normalizedTargetId, normalizedTargetIds,
                expected, source.isBlank() ? currentSource : source, targetMembership, automaticPresentation),
                "展示版本已变化，请重新读取后再提交修正");
            throw new AppException("PROJECT_HISTORY_CORRECTION_CONFLICT", "项目历程展示版本已变化，请刷新后重试", HttpStatus.CONFLICT);
        }
        if (!source.isBlank() && !source.equals(currentSource)) {
            persistConflict(newCorrection(projectId, userId, type, request, normalizedTargetId, normalizedTargetIds,
                currentRevision, source, targetMembership, automaticPresentation),
                "来源快照已变化，修正需要重新确认");
            throw new AppException("PROJECT_HISTORY_CORRECTION_STALE", "项目历程来源已变化，请刷新后重试", HttpStatus.CONFLICT);
        }
        validateRequest(type, request, stories, chapters);
        if (!"RESTORE_AUTOMATIC".equals(type) && existing.size() >= MAX_ACTIVE_CORRECTIONS) {
            throw new AppException("PROJECT_HISTORY_CORRECTION_LIMIT_REACHED",
                "项目历程有效修正已达到 2000 条，请先撤销或恢复部分修正", HttpStatus.CONFLICT);
        }
        if ("RESTORE_AUTOMATIC".equals(type)) {
            List<String> targets = correctionTargets(type, request);
            long revoked = existing.stream().filter(value -> overlaps(value, targets)).count();
            long activeAfterRestore = existing.size() - revoked + 1L;
            if (activeAfterRestore > MAX_ACTIVE_CORRECTIONS) {
                throw new AppException("PROJECT_HISTORY_CORRECTION_LIMIT_REACHED",
                    "project history correction limit reached; restore must revoke at least one active correction", HttpStatus.CONFLICT);
            }
            existing.stream().filter(value -> overlaps(value, targets)).forEach(value -> value.markReverted(null));
            correctionRepository.saveAll(existing);
        }
        ProjectHistoryCorrection correction = newCorrection(projectId, userId, type, request, normalizedTargetId,
            normalizedTargetIds, currentRevision, currentSource, targetMembership, automaticPresentation);
        correctionRepository.saveAndFlush(correction);
        List<ProjectHistoryCorrection> after = activeCorrections(projectId);
        return response(correction, snapshot, presentationRevision(snapshot, after), null);
    }

    @Transactional
    public HistoryCorrectionResponse revert(UUID userId, UUID projectId, UUID correctionId, String expectedRevision) {
        owned(userId, projectId);
        ProjectHistoryCorrection correction = correctionRepository.findByIdAndProjectId(correctionId, projectId)
            .orElseThrow(() -> new AppException("PROJECT_HISTORY_CORRECTION_NOT_FOUND", "项目历程修正不存在", HttpStatus.NOT_FOUND));
        ProjectHistorySnapshot snapshot = snapshotRepository.findLockedByProjectId(projectId).orElse(null);
        List<ProjectHistoryCorrection> active = activeCorrections(projectId);
        String revision = presentationRevision(snapshot, active);
        if (expectedRevision != null && !expectedRevision.isBlank() && !expectedRevision.equals(revision)) {
            throw new AppException("PROJECT_HISTORY_CORRECTION_CONFLICT", "项目历程展示版本已变化，请刷新后重试", HttpStatus.CONFLICT);
        }
        correction.markReverted(null);
        correctionRepository.saveAndFlush(correction);
        List<ProjectHistoryCorrection> after = activeCorrections(projectId);
        return response(correction, snapshot, presentationRevision(snapshot, after), null);
    }

    private List<ProjectHistoryCorrection> activeCorrections(UUID projectId) {
        return correctionRepository.findByProjectIdAndStatusOrderByCreatedAtAscIdAsc(
            projectId, ProjectHistoryCorrection.Status.ACTIVE
        );
    }

    private static Map<String, ChangeStory> indexStories(List<ChangeStory> stories) {
        return (stories == null ? List.<ChangeStory>of() : stories).stream().collect(
            LinkedHashMap::new, (map, value) -> map.put(value.id(), value), Map::putAll
        );
    }

    private static Map<String, HistoryChapter> indexChapters(List<HistoryChapter> chapters) {
        return (chapters == null ? List.<HistoryChapter>of() : chapters).stream().collect(
            LinkedHashMap::new, (map, value) -> map.put(value.id(), value), Map::putAll
        );
    }

    private Map<UUID, ProjectHistoryEvent> loadEvents(UUID projectId, Collection<ChangeStory> stories) {
        LinkedHashSet<UUID> ids = new LinkedHashSet<>();
        if (stories != null) stories.forEach(story -> ids.addAll(story.eventRefs()));
        if (ids.isEmpty()) return Map.of();
        List<UUID> orderedIds = List.copyOf(ids);
        Map<UUID, ProjectHistoryEvent> result = new LinkedHashMap<>();
        for (int offset = 0; offset < orderedIds.size(); offset += 500) {
            int end = Math.min(orderedIds.size(), offset + 500);
            eventRepository.findByProjectIdAndIdIn(projectId, orderedIds.subList(offset, end))
                .forEach(event -> result.put(event.getId(), event));
        }
        return result;
    }

    private ProjectHistoryCorrection newCorrection(
        UUID projectId,
        UUID userId,
        String type,
        HistoryCorrectionRequest request,
        String targetId,
        List<String> targetIds,
        String beforeRevision,
        String sourceFingerprint,
        String targetMembershipFingerprint,
        String automaticPresentationFingerprint
    ) {
        return new ProjectHistoryCorrection(
            projectId, userId, type, safeType(request.targetType()), targetId, json(targetIds),
            request.effectiveTitle(), request.effectiveSummary(), request.effectiveRole(),
            effectiveChapterId(type, request, targetId), beforeRevision, sourceFingerprint,
            targetMembershipFingerprint, automaticPresentationFingerprint,
            request.effectiveSecondaryTitle(), request.effectiveSecondarySummary()
        );
    }

    private void persistConflict(ProjectHistoryCorrection correction, String reason) {
        correction.markConflict(reason);
        conflictTransactionTemplate.executeWithoutResult(ignored -> correctionRepository.saveAndFlush(correction));
    }

    private void validateRequestBounds(HistoryCorrectionRequest request) {
        List<String> rawIds = request.targetIds();
        if (rawIds != null && rawIds.size() > MAX_TARGET_IDS) {
            throw bad("TOO_MANY_HISTORY_CORRECTION_TARGETS", "一次修正最多引用 100 个目标");
        }
        if (rawIds != null) {
            for (String value : rawIds) {
                if (value == null || value.isBlank()) {
                    throw bad("INVALID_HISTORY_CORRECTION_TARGET", "修正目标不能为空");
                }
                requireLength(value, MAX_TARGET_ID_LENGTH, "INVALID_HISTORY_CORRECTION_TARGET", "修正目标标识过长");
            }
        }
        requireLength(request.targetId(), MAX_TARGET_ID_LENGTH, "INVALID_HISTORY_CORRECTION_TARGET", "修正目标标识过长");
        requireLength(request.chapterId(), MAX_TARGET_ID_LENGTH, "INVALID_HISTORY_CORRECTION_TARGET", "篇章标识过长");
        requireLength(request.declaredChapterId(), MAX_TARGET_ID_LENGTH, "INVALID_HISTORY_CORRECTION_TARGET", "篇章标识过长");
        requireLength(request.title(), MAX_TITLE_LENGTH, "INVALID_HISTORY_CORRECTION_CONTENT", "标题不能超过 8000 个字符");
        requireLength(request.declaredTitle(), MAX_TITLE_LENGTH, "INVALID_HISTORY_CORRECTION_CONTENT", "标题不能超过 8000 个字符");
        requireLength(request.secondaryTitle(), MAX_TITLE_LENGTH, "INVALID_HISTORY_CORRECTION_CONTENT", "拆分标题不能超过 8000 个字符");
        requireLength(request.summary(), MAX_SUMMARY_LENGTH, "INVALID_HISTORY_CORRECTION_CONTENT", "摘要不能超过 12000 个字符");
        requireLength(request.declaredSummary(), MAX_SUMMARY_LENGTH, "INVALID_HISTORY_CORRECTION_CONTENT", "摘要不能超过 12000 个字符");
        requireLength(request.secondarySummary(), MAX_SUMMARY_LENGTH, "INVALID_HISTORY_CORRECTION_CONTENT", "拆分摘要不能超过 12000 个字符");
        requireLength(request.role(), 30, "INVALID_HISTORY_CORRECTION_ROLE", "角色标识过长");
        requireLength(request.declaredRole(), 30, "INVALID_HISTORY_CORRECTION_ROLE", "角色标识过长");
        requireLength(request.targetType(), 30, "INVALID_HISTORY_CORRECTION_TARGET", "目标类型过长");
        requireLength(request.expectedPresentationRevision(), 180, "INVALID_HISTORY_CORRECTION_REVISION", "展示版本标识过长");
        requireLength(request.sourceFingerprint(), 64, "INVALID_HISTORY_CORRECTION_REVISION", "来源指纹过长");
    }

    private static void requireLength(String value, int max, String code, String message) {
        if (value != null && value.trim().length() > max) throw bad(code, message);
    }

    private void applyCorrection(
        UUID projectId,
        ProjectHistoryCorrection correction,
        List<String> ids,
        Map<String, ChangeStory> stories,
        Map<String, HistoryChapter> chapters,
        Map<String, EvolutionThread> threads,
        Map<UUID, ProjectHistoryEvent> events
    ) {
        String type = correction.getCorrectionType().toUpperCase(Locale.ROOT);
        String target = safe(correction.getTargetId());
        ChangeStory story = stories.get(target);
        switch (type) {
            case "RENAME_STORY" -> {
                if (story != null) stories.put(target, presentation(story, textOr(story.humanTitle(), correction.getDeclaredTitle()),
                    story.oneSentenceSummary(), "", story.role(), story.primaryStoryId(), correction));
            }
            case "EDIT_SUMMARY" -> {
                if (story != null) stories.put(target, presentation(story, story.humanTitle(),
                    textOr(story.oneSentenceSummary(), correction.getDeclaredSummary()), "", story.role(), story.primaryStoryId(), correction));
            }
            case "SET_PRIMARY", "SET_SUPPORTING" -> {
                if (story != null) {
                    String role = "SET_PRIMARY".equals(type) ? "PRIMARY" : "SUPPORTING";
                    if ("PRIMARY".equals(role)) removeFromSupporting(stories, target);
                    String primary = "PRIMARY".equals(role) ? "" : primaryTarget(correction);
                    if ("SUPPORTING".equals(role)) removeFromSupporting(stories, target);
                    stories.put(target, presentation(story, story.humanTitle(), story.oneSentenceSummary(), "", role, primary, correction));
                    if ("SUPPORTING".equals(role) && !primary.isBlank() && stories.containsKey(primary)) {
                        addSupporting(stories, primary, target, correction);
                    }
                }
            }
            case "REATTACH_SUPPORTING" -> {
                if (story != null) {
                    String primary = primaryTarget(correction);
                    if (!primary.isBlank() && stories.containsKey(primary)) {
                        removeFromSupporting(stories, target);
                        stories.put(target, presentation(story, story.humanTitle(), story.oneSentenceSummary(), "",
                            "SUPPORTING", primary, correction));
                        addSupporting(stories, primary, target, correction);
                    }
                }
            }
            case "HIDE_STORY" -> {
                if (story != null) stories.put(target, presentation(story, story.humanTitle(), story.oneSentenceSummary(),
                    "", story.role(), story.primaryStoryId(), correction, true, story.pinned(), "", "ACTIVE"));
            }
            case "PIN_STORY" -> {
                if (story != null) stories.put(target, presentation(story, story.humanTitle(), story.oneSentenceSummary(),
                    "", story.role(), story.primaryStoryId(), correction, story.hiddenByDefault(), true, "", "ACTIVE"));
            }
            case "MERGE_STORIES" -> merge(projectId, ids, stories, chapters, threads, events, correction);
            case "SPLIT_STORY" -> split(projectId, target, eventTargetIds(correction), stories, chapters, threads, events, correction);
            case "RENAME_CHAPTER", "DECLARE_CHAPTER" -> {
                String chapterId = effectiveChapterId(correction);
                HistoryChapter chapter = chapters.get(chapterId);
                if (chapter == null && "DECLARE_CHAPTER".equals(type)) {
                    chapter = declaredChapter(correction, chapterId, stories);
                }
                if (chapter != null && "DECLARE_CHAPTER".equals(type)) {
                    Set<String> declaredRefs = new LinkedHashSet<>(chapter.storyRefs());
                    chapters.replaceAll((existingId, existing) -> existingId.equals(chapterId)
                        ? existing
                        : recomputeChapter(existing.storyRefs().stream()
                            .filter(ref -> !declaredRefs.contains(ref)).toList(), existing, stories));
                    chapters.entrySet().removeIf(entry -> !entry.getKey().equals(chapterId)
                        && entry.getValue().storyRefs().isEmpty());
                    chapter = recomputeChapter(chapter.storyRefs(), chapter, stories);
                }
                if (chapter != null) chapters.put(chapterId, chapterPresentation(chapter, correction));
            }
            default -> { }
        }
    }

    private void merge(
        UUID projectId,
        List<String> ids,
        Map<String, ChangeStory> stories,
        Map<String, HistoryChapter> chapters,
        Map<String, EvolutionThread> threads,
        Map<UUID, ProjectHistoryEvent> events,
        ProjectHistoryCorrection correction
    ) {
        LinkedHashSet<String> requestedIds = ids.stream().filter(stories::containsKey)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (requestedIds.size() < 2) return;
        String survivorId = requestedIds.iterator().next();
        List<ChangeStory> sources = requestedIds.stream().map(stories::get)
            .filter(java.util.Objects::nonNull).toList();
        ChangeStory survivor = mergedStory(sources, correction);
        stories.put(survivorId, survivor);
        requestedIds.stream().filter(id -> !id.equals(survivorId)).forEach(id -> {
            ChangeStory other = stories.get(id);
            if (other != null) {
                stories.put(id, presentation(other, other.humanTitle(), other.oneSentenceSummary(), "",
                    other.role(), other.primaryStoryId(), correction, true, other.pinned(), survivorId, "MERGED"));
            }
        });
        remapMergedRelations(stories, requestedIds, survivorId, correction);
        chapters.replaceAll((chapterId, chapter) -> recomputeChapter(
            replaceStoryRefs(chapter.storyRefs(), requestedIds, survivorId), chapter, stories
        ));
        threads.replaceAll((threadId, thread) -> recomputeThread(
            replaceStoryRefs(thread.storyRefs(), requestedIds, survivorId), thread, stories, events, correction
        ));
        reconcileRoleGraph(stories, correction);
    }

    private void split(
        UUID projectId,
        String target,
        List<String> ids,
        Map<String, ChangeStory> stories,
        Map<String, HistoryChapter> chapters,
        Map<String, EvolutionThread> threads,
        Map<UUID, ProjectHistoryEvent> events,
        ProjectHistoryCorrection correction
    ) {
        ChangeStory original = stories.get(target);
        if (original == null || ids.isEmpty()) return;
        Set<UUID> requested = ids.stream().map(this::uuid).filter(java.util.Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<UUID> firstPart = original.eventRefs().stream().filter(requested::contains).collect(Collectors.toCollection(LinkedHashSet::new));
        if (firstPart.isEmpty() || firstPart.size() >= original.eventRefs().size()) return;
        List<UUID> secondPart = original.eventRefs().stream().filter(id -> !firstPart.contains(id)).toList();
        String newId = "story-" + sha256(original.id() + "|split|" + correction.getId()).substring(0, 20);
        ChangeStory first = storyWithEvents(original, target, firstPart, events, correction, false);
        ChangeStory second = storyWithEvents(original, newId, new LinkedHashSet<>(secondPart), events, correction, true);
        stories.put(target, first);
        stories.put(newId, second);
        if (original.supporting() && !original.primaryStoryId().isBlank() && stories.containsKey(original.primaryStoryId())) {
            addSupporting(stories, original.primaryStoryId(), newId, correction);
        }
        chapters.replaceAll((chapterId, chapter) -> chapter.storyRefs().contains(target)
            ? recomputeChapter(appendUnique(chapter.storyRefs(), newId), chapter, stories)
            : chapter);
        threads.replaceAll((threadId, thread) -> thread.storyRefs().contains(target)
            ? recomputeThread(appendUnique(thread.storyRefs(), newId), thread, stories, events, correction)
            : thread);
        reconcileRoleGraph(stories, correction);
    }

    private ChangeStory storyWithEvents(
        ChangeStory original,
        String id,
        Set<UUID> eventIds,
        Map<UUID, ProjectHistoryEvent> events,
        ProjectHistoryCorrection correction,
        boolean secondary
    ) {
        EventSlice slice = eventSlice(eventIds, events);
        List<String> reasonEvidence = original.reasonEvidenceRefs().stream()
            .filter(slice.evidenceRefs()::contains).distinct().toList();
        List<String> limitations = union(slice.limitations(), slice.complete()
            ? List.of()
            : List.of("部分来源事件元数据不可用，拆分后的字段仅覆盖可确认范围"));
        String title = splitTitle(original, correction, secondary);
        String summary = splitSummary(original, correction, secondary);
        String role = original.role();
        String primaryStoryId = original.primaryStoryId();
        List<String> supportingRefs = secondary && original.primary() ? List.of() : original.supportingChangeRefs();
        return new ChangeStory(
            id, original.primarySubjectKey(), title, summary,
            "拆分前，这部分与原变化故事一起展示。",
            "用户按来源事件边界将这部分单独展示。",
            "当前只汇总这部分对应的来源事件和证据。",
            slice.affectedAreas(), reasonEvidence.isEmpty() ? "" : original.reason(), reasonEvidence,
            "", slice.conflicts(), slice.unknowns(), slice.occurredFrom(), slice.occurredTo(),
            slice.evidenceRefs().size(), slice.eventRefs().size(), original.authority(), USER_DECLARED_PRESENTATION,
            slice.complete() ? original.coverage() : "PARTIAL", limitations, slice.eventRefs(), slice.evidenceRefs(),
            role, primaryStoryId, supportingRefs, slice.technicalAtomRefs(), slice.commitSummaries(), slice.technicalDetails(),
            USER_DECLARED_PRESENTATION, correction.getId().toString(),
            secondary ? splitAutomaticTitle(original.automaticTitle()) : original.automaticTitle(),
            secondary ? splitAutomaticSummary(original.automaticSummary()) : original.automaticSummary(),
            append(original.userCorrectionRefs(), correction.getId().toString()), original.hiddenByDefault(),
            secondary ? false : original.pinned(),
            "", "ACTIVE", original.correctionConflicts()
        );
    }

    private ChangeStory mergedStory(List<ChangeStory> sources, ProjectHistoryCorrection correction) {
        ChangeStory left = sources.get(0);
        LinkedHashSet<UUID> eventRefs = sources.stream().flatMap(story -> story.eventRefs().stream())
            .collect(Collectors.toCollection(LinkedHashSet::new));
        LinkedHashSet<String> evidenceRefs = sources.stream().flatMap(story -> story.evidenceRefs().stream())
            .collect(Collectors.toCollection(LinkedHashSet::new));
        List<String> supportingRefs = sources.stream().flatMap(story -> story.supportingChangeRefs().stream())
            .filter(id -> sources.stream().noneMatch(story -> story.id().equals(id)))
            .filter(id -> !id.equals(left.id())).distinct().toList();
        List<String> reasonEvidence = sources.stream().flatMap(story -> story.reasonEvidenceRefs().stream())
            .filter(evidenceRefs::contains).distinct().toList();
        String reason = sources.stream().map(ChangeStory::reason).filter(value -> value != null && !value.isBlank())
            .distinct().findFirst().orElse("");
        String coverage = sources.stream().map(ChangeStory::coverage).distinct().count() == 1
            ? left.coverage() : "PARTIAL";
        return new ChangeStory(
            left.id(), left.primarySubjectKey(), safe(correction.getDeclaredTitle()).isBlank() ? left.humanTitle() : correction.getDeclaredTitle(),
            safe(correction.getDeclaredSummary()).isBlank() ? left.oneSentenceSummary() : correction.getDeclaredSummary(),
            sources.stream().map(ChangeStory::beforeState).filter(value -> value != null && !value.isBlank()).findFirst().orElse(""),
            "用户将多个已有变化故事合并为一个展示结果。",
            sources.stream().sorted(Comparator.comparing(ChangeStory::occurredTo, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(ChangeStory::afterState).filter(value -> value != null && !value.isBlank()).reduce((a, b) -> b).orElse(""),
            sources.stream().flatMap(story -> story.affectedAreas().stream()).distinct().toList(),
            reasonEvidence.isEmpty() ? "" : reason, reasonEvidence, "",
            sources.stream().flatMap(story -> story.conflicts().stream()).distinct().toList(),
            sources.stream().flatMap(story -> story.unknowns().stream()).distinct().toList(),
            sources.stream().map(ChangeStory::occurredFrom).filter(java.util.Objects::nonNull).min(Instant::compareTo).orElse(null),
            sources.stream().map(ChangeStory::occurredTo).filter(java.util.Objects::nonNull).max(Instant::compareTo).orElse(null),
            evidenceRefs.size(), eventRefs.size(), left.authority(), USER_DECLARED_PRESENTATION, coverage,
            sources.stream().flatMap(story -> story.limitations().stream()).distinct().toList(),
            List.copyOf(eventRefs), List.copyOf(evidenceRefs), "PRIMARY", "", supportingRefs,
            sources.stream().flatMap(story -> story.technicalAtomRefs().stream()).distinct().toList(),
            sources.stream().flatMap(story -> story.commitSummaries().stream()).distinct().toList(),
            sources.stream().flatMap(story -> story.technicalDetails().stream()).distinct().toList(),
            USER_DECLARED_PRESENTATION, correction.getId().toString(), left.automaticTitle(), left.automaticSummary(),
            append(sources.stream().flatMap(story -> story.userCorrectionRefs().stream()).distinct().toList(), correction.getId().toString()),
            left.hiddenByDefault(), sources.stream().anyMatch(ChangeStory::pinned), "", "ACTIVE",
            sources.stream().flatMap(story -> story.correctionConflicts().stream()).distinct().toList()
        );
    }

    private EventSlice eventSlice(Set<UUID> eventIds, Map<UUID, ProjectHistoryEvent> events) {
        List<UUID> refs = eventIds.stream().distinct().toList();
        List<ProjectHistoryEvent> selected = refs.stream().map(events::get)
            .filter(java.util.Objects::nonNull).toList();
        List<String> evidence = selected.stream().flatMap(event -> strings(event.getEvidenceRefsJson()).stream())
            .distinct().toList();
        List<String> paths = selected.stream().flatMap(event -> strings(event.getAffectedPathsJson()).stream())
            .filter(value -> value != null && !value.isBlank()).distinct().toList();
        List<String> areas = paths.stream().map(ProjectHistoryCorrectionService::pathArea)
            .filter(value -> !value.isBlank()).distinct().toList();
        if (areas.isEmpty()) {
            areas = selected.stream().flatMap(event -> strings(event.getSubjectKeysJson()).stream())
                .filter(value -> value != null && !value.isBlank()).distinct().toList();
        }
        List<String> conflicts = selected.stream()
            .filter(event -> event.getEpistemicStatus() == com.projectflow.entity.ProjectFactEpistemicStatus.CONFLICTED)
            .map(event -> "来源记录存在冲突：" + safe(event.getSafeSourceLabel())).distinct().toList();
        List<String> unknowns = selected.stream()
            .filter(event -> event.getEpistemicStatus() == com.projectflow.entity.ProjectFactEpistemicStatus.UNKNOWN)
            .map(event -> "来源记录状态未知：" + safe(event.getSafeSourceLabel())).distinct().toList();
        return new EventSlice(
            refs, evidence,
            selected.stream().map(ProjectHistoryEvent::getOccurredAt).filter(java.util.Objects::nonNull)
                .min(Instant::compareTo).orElse(null),
            selected.stream().map(ProjectHistoryEvent::getOccurredAt).filter(java.util.Objects::nonNull)
                .max(Instant::compareTo).orElse(null),
            areas,
            selected.stream().map(ProjectHistoryEvent::getStableEventKey).filter(value -> value != null && !value.isBlank())
                .distinct().toList(),
            selected.stream().filter(event -> event.getCategory() == ProjectHistoryEvent.Category.COMMIT
                    || event.getCategory() == ProjectHistoryEvent.Category.MERGE)
                .map(ProjectHistoryEvent::getSafeSourceLabel).filter(value -> value != null && !value.isBlank())
                .distinct().toList(),
            paths,
            selected.stream().flatMap(event -> strings(event.getLimitationsJson()).stream()).distinct().toList(),
            conflicts, unknowns, selected.size() == refs.size()
        );
    }

    private static String splitTitle(ChangeStory original, ProjectHistoryCorrection correction, boolean secondary) {
        String declared = secondary ? correction.getSecondaryDeclaredTitle() : correction.getDeclaredTitle();
        if (!safe(declared).isBlank()) return declared;
        return secondary ? splitAutomaticTitle(original.humanTitle()) : original.humanTitle();
    }

    private static String splitSummary(ChangeStory original, ProjectHistoryCorrection correction, boolean secondary) {
        String declared = secondary ? correction.getSecondaryDeclaredSummary() : correction.getDeclaredSummary();
        if (!safe(declared).isBlank()) return declared;
        return secondary ? splitAutomaticSummary(original.oneSentenceSummary()) : original.oneSentenceSummary();
    }

    private static String splitAutomaticTitle(String value) {
        return safe(value).isBlank() ? "拆分后的另一部分" : value + "（拆分后的另一部分）";
    }

    private static String splitAutomaticSummary(String value) {
        return safe(value).isBlank()
            ? "该部分只汇总拆分后对应的来源事件。"
            : "该部分来自原故事的另一组来源事件；原摘要为：" + value;
    }

    private static List<String> appendUnique(List<String> values, String value) {
        LinkedHashSet<String> result = new LinkedHashSet<>(values == null ? List.of() : values);
        if (value != null && !value.isBlank()) result.add(value);
        return List.copyOf(result);
    }

    private static List<String> replaceStoryRefs(List<String> refs, Set<String> replacedIds, String survivorId) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (refs != null) refs.forEach(ref -> result.add(replacedIds.contains(ref) ? survivorId : ref));
        return List.copyOf(result);
    }

    private HistoryChapter recomputeChapter(
        List<String> refs,
        HistoryChapter chapter,
        Map<String, ChangeStory> stories
    ) {
        List<String> validRefs = refs.stream().filter(stories::containsKey)
            .filter(id -> !"MERGED".equals(stories.get(id).displayStatus())).distinct().toList();
        List<ChangeStory> members = validRefs.stream().map(stories::get).toList();
        LinkedHashSet<UUID> eventRefs = members.stream().flatMap(story -> story.eventRefs().stream())
            .collect(Collectors.toCollection(LinkedHashSet::new));
        Instant from = members.stream().map(ChangeStory::occurredFrom).filter(java.util.Objects::nonNull)
            .min(Instant::compareTo).orElse(null);
        Instant to = members.stream().map(ChangeStory::occurredTo).filter(java.util.Objects::nonNull)
            .max(Instant::compareTo).orElse(from);
        return new HistoryChapter(
            chapter.id(), chapter.title(), chapter.summary(), from, to, chapter.boundarySignals(), validRefs,
            validRefs.size(), eventRefs.size(), chapter.authority(), chapter.coverage(), chapter.limitations(),
            chapter.presentationAuthority(), chapter.presentationRevision(), chapter.userDeclared(),
            chapter.userCorrectionRefs(), chapter.hiddenByDefault(), chapter.pinned()
        );
    }

    private EvolutionThread recomputeThread(
        List<String> refs,
        EvolutionThread thread,
        Map<String, ChangeStory> stories,
        Map<UUID, ProjectHistoryEvent> events,
        ProjectHistoryCorrection correction
    ) {
        List<String> validRefs = refs.stream().filter(stories::containsKey)
            .filter(id -> !"MERGED".equals(stories.get(id).displayStatus())).distinct().toList();
        List<ChangeStory> members = validRefs.stream().map(stories::get)
            .sorted(Comparator.comparing(ChangeStory::occurredFrom, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ChangeStory::id)).toList();
        LinkedHashSet<UUID> eventIds = members.stream().flatMap(story -> story.eventRefs().stream())
            .collect(Collectors.toCollection(LinkedHashSet::new));
        List<String> transitions = eventIds.stream().map(events::get).filter(java.util.Objects::nonNull)
            .sorted(Comparator.comparing(ProjectHistoryEvent::getOccurredAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ProjectHistoryEvent::getId))
            .map(event -> event.getTransition().name()).toList();
        if (transitions.isEmpty() && !eventIds.isEmpty()) transitions = thread.transitions();
        LinkedHashSet<String> evidence = members.stream().flatMap(story -> story.evidenceRefs().stream())
            .collect(Collectors.toCollection(LinkedHashSet::new));
        String currentOutcome = members.stream().max(
            Comparator.comparing(ChangeStory::occurredTo, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ChangeStory::id)
        ).map(ChangeStory::afterState).orElse("");
        return new EvolutionThread(
            thread.id(), thread.subjectKey(), thread.subjectLabel(), thread.subjectType(), validRefs, transitions,
            currentOutcome, thread.gaps(),
            members.stream().flatMap(story -> story.conflicts().stream()).distinct().toList(),
            members.stream().flatMap(story -> story.unknowns().stream()).distinct().toList(), evidence.size(),
            thread.capabilityId(), USER_DECLARED_PRESENTATION, correction.getId().toString(),
            append(thread.userCorrectionRefs(), correction.getId().toString())
        );
    }

    private void remapMergedRelations(
        Map<String, ChangeStory> stories,
        Set<String> mergedIds,
        String survivorId,
        ProjectHistoryCorrection correction
    ) {
        stories.replaceAll((id, story) -> {
            if ("MERGED".equals(story.displayStatus())) return story;
            String primary = mergedIds.contains(story.primaryStoryId()) ? survivorId : story.primaryStoryId();
            List<String> supporting = replaceStoryRefs(story.supportingChangeRefs(), mergedIds, survivorId).stream()
                .filter(ref -> !ref.equals(id)).filter(stories::containsKey).toList();
            if (id.equals(survivorId)) primary = "";
            return relationPresentation(story, id.equals(survivorId) ? "PRIMARY" : story.role(), primary,
                supporting, correction);
        });
    }

    private void reconcileRoleGraph(Map<String, ChangeStory> stories, ProjectHistoryCorrection correction) {
        stories.replaceAll((id, story) -> {
            if ("MERGED".equals(story.displayStatus())) return story;
            if (!story.supporting()) {
                return relationPresentation(story, "PRIMARY", "", List.of(), correction);
            }
            ChangeStory primary = stories.get(story.primaryStoryId());
            if (primary == null || !primary.primary() || "MERGED".equals(primary.displayStatus()) || primary.id().equals(id)) {
                return relationPresentation(story, "PRIMARY", "", List.of(), correction);
            }
            return relationPresentation(story, "SUPPORTING", primary.id(), List.of(), correction);
        });
        Map<String, List<String>> reverse = new LinkedHashMap<>();
        stories.values().stream().filter(ChangeStory::supporting)
            .filter(story -> !"MERGED".equals(story.displayStatus()))
            .forEach(story -> reverse.computeIfAbsent(story.primaryStoryId(), ignored -> new ArrayList<>()).add(story.id()));
        stories.replaceAll((id, story) -> {
            if ("MERGED".equals(story.displayStatus()) || !story.primary()) return story;
            return relationPresentation(story, "PRIMARY", "", reverse.getOrDefault(id, List.of()), correction);
        });
    }

    private ChangeStory relationPresentation(
        ChangeStory story,
        String role,
        String primaryStoryId,
        List<String> supportingChangeRefs,
        ProjectHistoryCorrection correction
    ) {
        List<String> normalizedSupporting = supportingChangeRefs.stream()
            .filter(ref -> !ref.equals(story.id())).distinct().toList();
        if (story.role().equals(role) && story.primaryStoryId().equals(safe(primaryStoryId))
            && story.supportingChangeRefs().equals(normalizedSupporting)) {
            return story;
        }
        return new ChangeStory(
            story.id(), story.primarySubjectKey(), story.humanTitle(), story.oneSentenceSummary(), story.beforeState(),
            story.change(), story.afterState(), story.affectedAreas(), story.reason(), story.reasonEvidenceRefs(),
            story.laterOutcome(), story.conflicts(), story.unknowns(), story.occurredFrom(), story.occurredTo(),
            story.evidenceCount(), story.rawEventCount(), story.authority(), story.summaryStatus(), story.coverage(),
            story.limitations(), story.eventRefs(), story.evidenceRefs(), role, primaryStoryId,
            normalizedSupporting,
            story.technicalAtomRefs(), story.commitSummaries(), story.technicalDetails(), USER_DECLARED_PRESENTATION,
            correction.getId().toString(), story.automaticTitle(), story.automaticSummary(),
            append(story.userCorrectionRefs(), correction.getId().toString()), story.hiddenByDefault(), story.pinned(),
            story.mergedIntoStoryId(), story.displayStatus(), story.correctionConflicts()
        );
    }

    private static String pathArea(String path) {
        String safePath = safe(path).replace('\\', '/');
        if (safePath.isBlank()) return "";
        String[] segments = safePath.split("/");
        return segments.length == 1 ? segments[0] : segments[0] + "/" + segments[1];
    }

    private ChangeStory presentation(ChangeStory story, String title, String summary, String ignored,
        String role, String primary, ProjectHistoryCorrection correction) {
        return presentation(story, title, summary, ignored, role, primary, correction,
            story.hiddenByDefault(), story.pinned(), story.mergedIntoStoryId(), story.displayStatus());
    }

    private ChangeStory presentation(ChangeStory story, String title, String summary, String ignored,
        String role, String primary, ProjectHistoryCorrection correction, boolean hidden, boolean pinned,
        String mergedInto, String status) {
        return new ChangeStory(
            story.id(), story.primarySubjectKey(), safe(title).isBlank() ? story.humanTitle() : title,
            safe(summary).isBlank() ? story.oneSentenceSummary() : summary, story.beforeState(), story.change(), story.afterState(),
            story.affectedAreas(), story.reason(), story.reasonEvidenceRefs(), story.laterOutcome(), story.conflicts(), story.unknowns(),
            story.occurredFrom(), story.occurredTo(), story.evidenceCount(), story.rawEventCount(), story.authority(),
            story.summaryStatus(), story.coverage(), story.limitations(), story.eventRefs(), story.evidenceRefs(), role, primary,
            story.supportingChangeRefs(), story.technicalAtomRefs(), story.commitSummaries(), story.technicalDetails(),
            USER_DECLARED_PRESENTATION, correction.getId().toString(), story.automaticTitle(), story.automaticSummary(),
            append(story.userCorrectionRefs(), correction.getId().toString()), hidden, pinned, mergedInto, status,
            story.correctionConflicts()
        );
    }

    private HistoryChapter declaredChapter(ProjectHistoryCorrection correction, String chapterId,
        Map<String, ChangeStory> stories) {
        List<String> refs = rawTargetIds(correction).stream().filter(stories::containsKey).distinct().toList();
        List<ChangeStory> selected = refs.stream().map(stories::get).filter(java.util.Objects::nonNull).toList();
        Instant from = selected.stream().map(ChangeStory::occurredFrom).filter(java.util.Objects::nonNull).min(Instant::compareTo).orElse(null);
        Instant to = selected.stream().map(ChangeStory::occurredTo).filter(java.util.Objects::nonNull).max(Instant::compareTo).orElse(from);
        int events = selected.stream().mapToInt(ChangeStory::rawEventCount).sum();
        return new HistoryChapter(
            chapterId,
            safe(correction.getDeclaredTitle()).isBlank() ? "用户声明的项目阶段" : correction.getDeclaredTitle(),
            safe(correction.getDeclaredSummary()).isBlank() ? "用户将这些项目结果归为同一阶段。" : correction.getDeclaredSummary(),
            from, to, List.of("USER_DECLARED_BOUNDARY"), refs, refs.size(), events,
            USER_DECLARED_PRESENTATION, "DECLARED_FROM_EXISTING_STORIES",
            List.of("篇章边界由用户声明，仅用于展示，不改变原始事实"), USER_DECLARED_PRESENTATION,
            correction.getId().toString(), true, List.of(correction.getId().toString()), false, false
        );
    }

    private HistoryChapter chapterPresentation(HistoryChapter chapter, ProjectHistoryCorrection correction) {
        String title = safe(correction.getDeclaredTitle()).isBlank() ? chapter.title() : correction.getDeclaredTitle();
        String summary = safe(correction.getDeclaredSummary()).isBlank() ? chapter.summary() : correction.getDeclaredSummary();
        return new HistoryChapter(chapter.id(), title, summary, chapter.from(), chapter.to(), chapter.boundarySignals(),
            chapter.storyRefs(), chapter.storyCount(), chapter.rawEventCount(), chapter.authority(), chapter.coverage(),
            chapter.limitations(), USER_DECLARED_PRESENTATION, correction.getId().toString(), true,
            append(chapter.userCorrectionRefs(), correction.getId().toString()), chapter.hiddenByDefault(), chapter.pinned());
    }

    private String automaticValue(
        ProjectHistoryCorrection correction,
        Map<String, ChangeStory> stories,
        Map<String, HistoryChapter> chapters
    ) {
        String type = correction.getCorrectionType().toUpperCase(Locale.ROOT);
        if (type.endsWith("CHAPTER")) {
            HistoryChapter chapter = chapters.get(effectiveChapterId(correction));
            return chapter == null ? "" : chapter.title();
        }
        ChangeStory target = stories.get(correction.getTargetId());
        if (target == null && "MERGE_STORIES".equals(type)) {
            target = rawTargetIds(correction).stream().map(stories::get)
                .filter(java.util.Objects::nonNull).findFirst().orElse(null);
        }
        if (target == null) return "";
        return switch (type) {
            case "EDIT_SUMMARY" -> target.automaticSummary();
            case "SET_PRIMARY", "SET_SUPPORTING", "REATTACH_SUPPORTING" -> target.role();
            default -> target.automaticTitle();
        };
    }

    private String appliedValue(ProjectHistoryCorrection correction, String automatic) {
        String type = correction.getCorrectionType().toUpperCase(Locale.ROOT);
        return switch (type) {
            case "EDIT_SUMMARY" -> safe(correction.getDeclaredSummary()).isBlank() ? automatic : correction.getDeclaredSummary();
            case "SET_PRIMARY" -> "PRIMARY";
            case "SET_SUPPORTING", "REATTACH_SUPPORTING" -> "SUPPORTING";
            case "RENAME_STORY", "RENAME_CHAPTER", "DECLARE_CHAPTER", "MERGE_STORIES", "SPLIT_STORY" ->
                safe(correction.getDeclaredTitle()).isBlank() ? automatic : correction.getDeclaredTitle();
            default -> automatic;
        };
    }

    private void addSupporting(Map<String, ChangeStory> stories, String primary, String support,
        ProjectHistoryCorrection correction) {
        ChangeStory value = stories.get(primary);
        if (value == null || value.supportingChangeRefs().contains(support)) return;
        stories.put(primary, new ChangeStory(value.id(), value.primarySubjectKey(), value.humanTitle(), value.oneSentenceSummary(),
            value.beforeState(), value.change(), value.afterState(), value.affectedAreas(), value.reason(), value.reasonEvidenceRefs(),
            value.laterOutcome(), value.conflicts(), value.unknowns(), value.occurredFrom(), value.occurredTo(), value.evidenceCount(),
            value.rawEventCount(), value.authority(), value.summaryStatus(), value.coverage(), value.limitations(), value.eventRefs(),
            value.evidenceRefs(), value.role(), value.primaryStoryId(), append(value.supportingChangeRefs(), support), value.technicalAtomRefs(),
            value.commitSummaries(), value.technicalDetails(), USER_DECLARED_PRESENTATION, correction.getId().toString(), value.automaticTitle(),
            value.automaticSummary(), append(value.userCorrectionRefs(), correction.getId().toString()), value.hiddenByDefault(), value.pinned(), value.mergedIntoStoryId(),
            value.displayStatus(), value.correctionConflicts()));
    }

    private void removeFromSupporting(Map<String, ChangeStory> stories, String support) {
        stories.replaceAll((id, value) -> {
            if (!value.supportingChangeRefs().contains(support)) return value;
            List<String> refs = value.supportingChangeRefs().stream().filter(item -> !item.equals(support)).toList();
            return new ChangeStory(value.id(), value.primarySubjectKey(), value.humanTitle(), value.oneSentenceSummary(), value.beforeState(),
                value.change(), value.afterState(), value.affectedAreas(), value.reason(), value.reasonEvidenceRefs(), value.laterOutcome(),
                value.conflicts(), value.unknowns(), value.occurredFrom(), value.occurredTo(), value.evidenceCount(), value.rawEventCount(),
                value.authority(), value.summaryStatus(), value.coverage(), value.limitations(), value.eventRefs(), value.evidenceRefs(),
                value.role(), value.primaryStoryId(), refs, value.technicalAtomRefs(), value.commitSummaries(), value.technicalDetails(),
                value.presentationAuthority(), value.presentationRevision(), value.automaticTitle(), value.automaticSummary(), value.userCorrectionRefs(),
                value.hiddenByDefault(), value.pinned(), value.mergedIntoStoryId(), value.displayStatus(), value.correctionConflicts());
        });
    }

    private boolean targetsValid(ProjectHistoryCorrection correction, List<String> ids,
        Map<String, ChangeStory> stories, Map<String, HistoryChapter> chapters) {
        String type = correction.getCorrectionType().toUpperCase(Locale.ROOT);
        String targetType = correction.getTargetType().toUpperCase(Locale.ROOT);
        if ("DECLARE_CHAPTER".equals(type)) {
            List<String> storyRefs = rawTargetIds(correction);
            return !storyRefs.isEmpty() && storyRefs.stream().allMatch(stories::containsKey);
        }
        if ("RENAME_CHAPTER".equals(type) || "CHAPTER".equals(targetType)) {
            return ids.stream().anyMatch(chapters::containsKey);
        }
        if ("SPLIT_STORY".equals(type)) {
            ChangeStory original = stories.get(safe(correction.getTargetId()));
            List<String> eventIds = rawTargetIds(correction);
            if (original == null || eventIds.isEmpty()) return false;
            Set<UUID> requested = eventIds.stream().map(this::uuid).filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
            long contained = original.eventRefs().stream().filter(requested::contains).count();
            return contained > 0 && contained < original.eventRefs().size();
        }
        if ("SET_SUPPORTING".equals(type) || "REATTACH_SUPPORTING".equals(type)) {
            String primary = primaryTarget(correction);
            ChangeStory primaryStory = stories.get(primary);
            return stories.containsKey(safe(correction.getTargetId()))
                && !primary.isBlank() && primaryStory != null && primaryStory.primary()
                && !"MERGED".equals(primaryStory.displayStatus())
                && !primary.equals(safe(correction.getTargetId()));
        }
        if ("MERGE_STORIES".equals(type)) return ids.size() >= 2 && ids.stream().allMatch(stories::containsKey);
        return !ids.isEmpty() && ids.stream().allMatch(stories::containsKey);
    }

    private void validateRequest(String type, HistoryCorrectionRequest request, List<ChangeStory> stories, List<HistoryChapter> chapters) {
        List<String> raw = request.safeTargetIds();
        String target = safe(request.targetId());
        if ("MERGE_STORIES".equals(type)) {
            List<String> mergeIds = correctionTargets(type, request);
            if (mergeIds.size() < 2) throw bad("INVALID_HISTORY_CORRECTION_TARGET", "合并至少需要两个变化故事");
            if (mergeIds.stream().anyMatch(id -> stories.stream().noneMatch(story -> story.id().equals(id)))) {
                throw bad("PROJECT_HISTORY_CORRECTION_TARGET_NOT_FOUND", "项目历程合并目标不存在");
            }
        } else if ("SPLIT_STORY".equals(type)) {
            ChangeStory original = stories.stream().filter(story -> story.id().equals(target)).findFirst().orElse(null);
            if (original == null) throw bad("PROJECT_HISTORY_CORRECTION_TARGET_NOT_FOUND", "待拆分的变化故事不存在");
            Set<UUID> requested = raw.stream().map(this::uuid).filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
            long contained = original.eventRefs().stream().filter(requested::contains).count();
            if (requested.isEmpty() || contained == 0 || contained == original.eventRefs().size()) {
                throw bad("INVALID_HISTORY_CORRECTION_TARGET", "拆分必须选择原故事中的部分事件");
            }
        } else if ("SET_SUPPORTING".equals(type) || "REATTACH_SUPPORTING".equals(type)) {
            String primary = raw.isEmpty() ? safe(request.effectiveChapterId()) : safe(raw.get(0));
            ChangeStory primaryStory = stories.stream().filter(story -> story.id().equals(primary)).findFirst().orElse(null);
            if (target.isBlank() || primary.isBlank() || target.equals(primary)
                || stories.stream().noneMatch(story -> story.id().equals(target))
                || primaryStory == null || !primaryStory.primary() || "MERGED".equals(primaryStory.displayStatus())) {
                throw bad("PROJECT_HISTORY_CORRECTION_TARGET_NOT_FOUND", "Supporting 关系必须同时指向有效故事和 Primary 故事");
            }
        } else if ("DECLARE_CHAPTER".equals(type)) {
            if (raw.isEmpty() || raw.stream().anyMatch(id -> stories.stream().noneMatch(story -> story.id().equals(id)))) {
                throw bad("PROJECT_HISTORY_CORRECTION_TARGET_NOT_FOUND", "用户声明篇章必须引用已有变化故事");
            }
        } else if ("RENAME_CHAPTER".equals(type)) {
            String chapterId = safe(request.effectiveChapterId());
            if (chapterId.isBlank() || chapters.stream().noneMatch(chapter -> chapter.id().equals(chapterId))) {
                throw bad("PROJECT_HISTORY_CORRECTION_TARGET_NOT_FOUND", "项目历程篇章不存在");
            }
        } else if (!"RESTORE_AUTOMATIC".equals(type)) {
            if (target.isBlank() || stories.stream().noneMatch(story -> story.id().equals(target))) {
                throw bad("PROJECT_HISTORY_CORRECTION_TARGET_NOT_FOUND", "项目历程修正目标不存在");
            }
        }
        if (Set.of("RENAME_STORY", "RENAME_CHAPTER", "DECLARE_CHAPTER").contains(type)
            && safe(request.effectiveTitle()).isBlank()) {
            throw bad("INVALID_HISTORY_CORRECTION_CONTENT", "重命名必须提供标题");
        }
        if ("EDIT_SUMMARY".equals(type) && safe(request.effectiveSummary()).isBlank()) {
            throw bad("INVALID_HISTORY_CORRECTION_CONTENT", "摘要修正必须提供摘要");
        }
        if (Set.of("SET_PRIMARY", "SET_SUPPORTING").contains(type)
            && !request.effectiveRole().isBlank()
            && !Set.of("PRIMARY", "SUPPORTING").contains(request.effectiveRole().trim().toUpperCase(Locale.ROOT))) {
            throw bad("INVALID_HISTORY_CORRECTION_ROLE", "角色只能是 PRIMARY 或 SUPPORTING");
        }
    }

    private boolean overlaps(ProjectHistoryCorrection correction, List<String> targets) {
        List<String> ids = targetIds(correction);
        return ids.stream().anyMatch(targets::contains) || targets.contains(correction.getTargetId());
    }

    private List<String> correctionTargets(String type, HistoryCorrectionRequest request) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        String target = safe(request.targetId());
        List<String> raw = request.safeTargetIds();
        switch (type) {
            case "MERGE_STORIES" -> {
                if (!target.isBlank()) result.add(target);
                result.addAll(raw);
            }
            case "SPLIT_STORY" -> { if (!target.isBlank()) result.add(target); }
            case "SET_SUPPORTING", "REATTACH_SUPPORTING" -> {
                if (!target.isBlank()) result.add(target);
                if (!raw.isEmpty()) result.add(raw.get(0));
                else if (!safe(request.effectiveChapterId()).isBlank()) result.add(request.effectiveChapterId());
            }
            case "DECLARE_CHAPTER", "RENAME_CHAPTER" -> {
                String chapter = safe(request.effectiveChapterId());
                if (!chapter.isBlank()) result.add(chapter);
                else if (!target.isBlank()) result.add(target);
                if ("DECLARE_CHAPTER".equals(type)) result.addAll(raw);
            }
            default -> {
                if (!target.isBlank()) result.add(target);
                if (target.isBlank()) result.addAll(raw);
            }
        }
        return result.stream().filter(value -> value != null && !value.isBlank()).toList();
    }

    private String normalizedTargetId(String type, HistoryCorrectionRequest request, UUID projectId) {
        String target = safe(request.targetId());
        if ("MERGE_STORIES".equals(type) && target.isBlank() && !request.safeTargetIds().isEmpty()) {
            target = request.safeTargetIds().get(0);
        }
        if (("DECLARE_CHAPTER".equals(type) || "RENAME_CHAPTER".equals(type)) && target.isBlank()) {
            target = safe(request.effectiveChapterId());
        }
        if ("DECLARE_CHAPTER".equals(type) && target.isBlank()) {
            target = "chapter-user-" + sha256(projectId + "|" + request.effectiveTitle() + "|" + String.join(",", request.safeTargetIds())).substring(0, 20);
        }
        return target;
    }

    private List<String> normalizedTargetIds(String type, HistoryCorrectionRequest request) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        List<String> raw = request.safeTargetIds();
        if ("MERGE_STORIES".equals(type)) {
            result.addAll(raw);
            if (result.isEmpty() && !safe(request.targetId()).isBlank()) result.add(safe(request.targetId()));
        } else if ("SET_SUPPORTING".equals(type) || "REATTACH_SUPPORTING".equals(type)) {
            if (!raw.isEmpty()) result.add(raw.get(0));
            else if (!safe(request.effectiveChapterId()).isBlank()) result.add(safe(request.effectiveChapterId()));
        } else {
            result.addAll(raw);
        }
        return List.copyOf(result);
    }

    private List<String> targetIds(ProjectHistoryCorrection correction) {
        String type = correction.getCorrectionType().toUpperCase(Locale.ROOT);
        List<String> raw = rawTargetIds(correction);
        LinkedHashSet<String> result = new LinkedHashSet<>();
        String target = safe(correction.getTargetId());
        switch (type) {
            case "SPLIT_STORY" -> { if (!target.isBlank()) result.add(target); }
            case "SET_SUPPORTING", "REATTACH_SUPPORTING" -> {
                if (!target.isBlank()) result.add(target);
                if (!raw.isEmpty()) result.add(raw.get(0));
                else if (!safe(correction.getDeclaredChapterId()).isBlank()) result.add(correction.getDeclaredChapterId());
            }
            case "DECLARE_CHAPTER", "RENAME_CHAPTER" -> {
                String chapter = effectiveChapterId(correction);
                if (!chapter.isBlank()) result.add(chapter);
                if ("DECLARE_CHAPTER".equals(type)) result.addAll(raw);
            }
            default -> {
                if (!target.isBlank()) result.add(target);
                if ("MERGE_STORIES".equals(type) || target.isBlank()) result.addAll(raw);
            }
        }
        return result.stream().filter(value -> value != null && !value.isBlank()).toList();
    }

    private List<String> rawTargetIds(ProjectHistoryCorrection correction) {
        try {
            return objectMapper.readValue(correction.getTargetIdsJson(), new TypeReference<List<String>>() {})
                .stream().filter(value -> value != null && !value.isBlank()).map(String::trim).distinct().toList();
        } catch (JsonProcessingException ignored) {
            return List.of();
        }
    }

    private List<String> eventTargetIds(ProjectHistoryCorrection correction) { return rawTargetIds(correction); }

    private String primaryTarget(ProjectHistoryCorrection correction) {
        List<String> raw = rawTargetIds(correction);
        return raw.isEmpty() ? safe(correction.getDeclaredChapterId()) : safe(raw.get(0));
    }

    private String effectiveChapterId(ProjectHistoryCorrection correction) {
        return safe(correction.getDeclaredChapterId()).isBlank() ? safe(correction.getTargetId()) : safe(correction.getDeclaredChapterId());
    }

    private String effectiveChapterId(String type, HistoryCorrectionRequest request, String normalizedTargetId) {
        String value = safe(request.effectiveChapterId());
        if (value.isBlank() && ("DECLARE_CHAPTER".equals(type) || "RENAME_CHAPTER".equals(type))) value = normalizedTargetId;
        return value;
    }

    private CorrectionState correctionState(
        ProjectHistoryCorrection correction,
        ProjectHistorySnapshot snapshot,
        Map<String, ChangeStory> stories,
        Map<String, HistoryChapter> chapters
    ) {
        if (snapshot == null) return new CorrectionState(false, false, false, false, "项目历程快照不存在");
        String type = safe(correction.getCorrectionType()).toUpperCase(Locale.ROOT);
        if ("RESTORE_AUTOMATIC".equals(type)) return new CorrectionState(true, false, false, false, "");
        List<String> rawIds = rawTargetIds(correction);
        String currentMembership = targetMembershipFingerprint(type, correction.getTargetId(), rawIds, stories, chapters);
        String currentAutomatic = automaticPresentationFingerprint(type, correction.getTargetId(), rawIds, stories, chapters);
        boolean sourceStale = !safe(correction.getSourceFingerprint()).isBlank()
            && !safe(correction.getSourceFingerprint()).equals(safe(snapshot.getSourceEventFingerprint()));
        boolean hasMembershipFingerprint = !safe(correction.getTargetMembershipFingerprint()).isBlank();
        boolean membershipStale = hasMembershipFingerprint
            ? !safe(correction.getTargetMembershipFingerprint()).equals(currentMembership)
            : sourceStale;
        boolean automaticChanged = !safe(correction.getAutomaticPresentationFingerprint()).isBlank()
            && !safe(correction.getAutomaticPresentationFingerprint()).equals(currentAutomatic);
        boolean present = targetsValid(correction, targetIds(correction), stories, chapters);
        String reason = membershipStale
            ? hasMembershipFingerprint
                ? "修正目标包含的原始事件已经变化，旧修正未自动覆盖新成员"
                : "旧修正缺少目标成员指纹，来源变化后无法证明可以安全重放"
            : present ? "" : "修正目标已不存在或已被历史重建替换";
        return new CorrectionState(present, sourceStale, membershipStale, automaticChanged, reason);
    }

    private String targetMembershipFingerprint(
        String type,
        String targetId,
        List<String> rawTargetIds,
        Map<String, ChangeStory> stories,
        Map<String, HistoryChapter> chapters
    ) {
        StringBuilder value = new StringBuilder();
        appendToken(value, safe(type).toUpperCase(Locale.ROOT));
        switch (safe(type).toUpperCase(Locale.ROOT)) {
            case "DECLARE_CHAPTER" -> {
                appendToken(value, targetId);
                rawTargetIds.stream().sorted().forEach(id -> appendStoryMembership(value, id, stories));
            }
            case "RENAME_CHAPTER" -> appendChapterMembership(value, targetId, stories, chapters);
            case "MERGE_STORIES" -> {
                LinkedHashSet<String> ids = new LinkedHashSet<>(rawTargetIds);
                if (ids.isEmpty() && !safe(targetId).isBlank()) ids.add(safe(targetId));
                ids.stream().sorted().forEach(id -> appendStoryMembership(value, id, stories));
            }
            case "SPLIT_STORY" -> {
                appendStoryMembership(value, targetId, stories);
                rawTargetIds.stream().sorted().forEach(id -> appendToken(value, "split-event:" + id));
            }
            case "SET_SUPPORTING", "REATTACH_SUPPORTING" -> {
                appendStoryMembership(value, targetId, stories);
                rawTargetIds.stream().limit(1).forEach(id -> appendStoryMembership(value, id, stories));
            }
            default -> appendStoryMembership(value, targetId, stories);
        }
        return sha256(value.toString());
    }

    private String automaticPresentationFingerprint(
        String type,
        String targetId,
        List<String> rawTargetIds,
        Map<String, ChangeStory> stories,
        Map<String, HistoryChapter> chapters
    ) {
        StringBuilder value = new StringBuilder();
        appendToken(value, safe(type).toUpperCase(Locale.ROOT));
        switch (safe(type).toUpperCase(Locale.ROOT)) {
            case "DECLARE_CHAPTER", "MERGE_STORIES" -> {
                LinkedHashSet<String> ids = new LinkedHashSet<>(rawTargetIds);
                if (ids.isEmpty() && !safe(targetId).isBlank()) ids.add(safe(targetId));
                ids.stream().sorted().forEach(id -> appendStoryPresentation(value, id, stories));
            }
            case "RENAME_CHAPTER" -> appendChapterPresentation(value, targetId, chapters);
            case "SET_SUPPORTING", "REATTACH_SUPPORTING" -> {
                appendStoryPresentation(value, targetId, stories);
                rawTargetIds.stream().limit(1).forEach(id -> appendStoryPresentation(value, id, stories));
            }
            default -> appendStoryPresentation(value, targetId, stories);
        }
        return sha256(value.toString());
    }

    private void appendStoryMembership(StringBuilder value, String id, Map<String, ChangeStory> stories) {
        appendToken(value, "story:" + safe(id));
        ChangeStory story = stories.get(safe(id));
        if (story == null) {
            appendToken(value, "missing");
            return;
        }
        story.eventRefs().stream().map(UUID::toString).sorted().forEach(eventId -> appendToken(value, "event:" + eventId));
    }

    private void appendChapterMembership(
        StringBuilder value,
        String id,
        Map<String, ChangeStory> stories,
        Map<String, HistoryChapter> chapters
    ) {
        appendToken(value, "chapter:" + safe(id));
        HistoryChapter chapter = chapters.get(safe(id));
        if (chapter == null) {
            appendToken(value, "missing");
            return;
        }
        chapter.storyRefs().stream().sorted().forEach(storyId -> appendStoryMembership(value, storyId, stories));
    }

    private void appendStoryPresentation(StringBuilder value, String id, Map<String, ChangeStory> stories) {
        ChangeStory story = stories.get(safe(id));
        appendToken(value, "story:" + safe(id));
        if (story == null) {
            appendToken(value, "missing");
            return;
        }
        appendToken(value, story.humanTitle());
        appendToken(value, story.oneSentenceSummary());
        appendToken(value, story.role());
        appendToken(value, story.primaryStoryId());
        story.supportingChangeRefs().stream().sorted().forEach(ref -> appendToken(value, ref));
    }

    private void appendChapterPresentation(StringBuilder value, String id, Map<String, HistoryChapter> chapters) {
        HistoryChapter chapter = chapters.get(safe(id));
        appendToken(value, "chapter:" + safe(id));
        if (chapter == null) {
            appendToken(value, "missing");
            return;
        }
        appendToken(value, chapter.title());
        appendToken(value, chapter.summary());
        chapter.storyRefs().stream().sorted().forEach(ref -> appendToken(value, ref));
    }

    private static void appendToken(StringBuilder value, String token) {
        String safe = token == null ? "" : token;
        value.append(safe.length()).append(':').append(safe).append('|');
    }

    private HistoryCorrectionResponse indexedResponse(ProjectHistoryCorrection value, ProjectHistorySnapshot snapshot,
        String revision, String reason, Map<String, ChangeStory> storyIndex, Map<String, HistoryChapter> chapterIndex) {
        CorrectionState state = correctionState(value, snapshot, storyIndex, chapterIndex);
        String automatic = automaticValue(value, storyIndex, chapterIndex);
        String applied = appliedValue(value, automatic);
        String effectiveReason = safe(reason).isBlank() ? state.reason() : reason;
        String difference;
        if (!safe(effectiveReason).isBlank()) difference = effectiveReason;
        else if (state.automaticPresentationChanged()) difference = "自动展示已变化，仍保留用户明确修正";
        else difference = automatic.equals(applied) ? "无展示差异" : "用户声明覆盖自动展示";
        String status = safe(effectiveReason).isBlank() ? value.getStatus().name() : "CONFLICT";
        return new HistoryCorrectionResponse(value.getId(), value.getProjectId(), value.getCorrectionType(), value.getTargetType(),
            value.getTargetId(), rawTargetIds(value), status, value.getBeforePresentationRevision(), value.getSourceFingerprint(),
            safe(effectiveReason).isBlank() ? value.getConflictReason() : effectiveReason, value.getCreatedAt(), value.getUpdatedAt(), revision,
            value.getDeclaredTitle(), value.getDeclaredSummary(), value.getDeclaredRole(), value.getDeclaredChapterId(),
            automatic, applied, difference, state.targetPresent(), value.getSecondaryDeclaredTitle(), value.getSecondaryDeclaredSummary(),
            value.getTargetMembershipFingerprint(), value.getAutomaticPresentationFingerprint(), state.sourceStale(),
            state.membershipStale(), state.automaticPresentationChanged());
    }

    private HistoryCorrectionResponse response(ProjectHistoryCorrection value, ProjectHistorySnapshot snapshot,
        String revision, String reason) {
        Map<String, ChangeStory> stories = snapshot == null ? Map.of() : read(snapshot.getStoriesJson(), new TypeReference<List<ChangeStory>>() {})
            .stream().collect(LinkedHashMap::new, (map, story) -> map.put(story.id(), story), Map::putAll);
        Map<String, HistoryChapter> chapters = snapshot == null ? Map.of() : read(snapshot.getChaptersJson(), new TypeReference<List<HistoryChapter>>() {})
            .stream().collect(LinkedHashMap::new, (map, chapter) -> map.put(chapter.id(), chapter), Map::putAll);
        CorrectionState state = correctionState(value, snapshot, stories, chapters);
        String automatic = automaticValue(value, stories, chapters);
        String applied = appliedValue(value, automatic);
        String effectiveReason = safe(reason).isBlank() ? state.reason() : reason;
        String difference;
        if (!safe(effectiveReason).isBlank()) difference = effectiveReason;
        else if (state.automaticPresentationChanged()) difference = "自动展示已变化，仍保留用户明确修正";
        else difference = automatic.equals(applied) ? "无展示差异" : "用户声明覆盖自动展示";
        String status = safe(effectiveReason).isBlank() ? value.getStatus().name() : "CONFLICT";
        return new HistoryCorrectionResponse(value.getId(), value.getProjectId(), value.getCorrectionType(), value.getTargetType(),
            value.getTargetId(), rawTargetIds(value), status, value.getBeforePresentationRevision(), value.getSourceFingerprint(),
            safe(effectiveReason).isBlank() ? value.getConflictReason() : effectiveReason, value.getCreatedAt(), value.getUpdatedAt(), revision,
            value.getDeclaredTitle(), value.getDeclaredSummary(), value.getDeclaredRole(), value.getDeclaredChapterId(),
            automatic, applied, difference, state.targetPresent(), value.getSecondaryDeclaredTitle(), value.getSecondaryDeclaredSummary(),
            value.getTargetMembershipFingerprint(), value.getAutomaticPresentationFingerprint(), state.sourceStale(),
            state.membershipStale(), state.automaticPresentationChanged());
    }

    private String presentationRevision(ProjectHistorySnapshot snapshot, List<ProjectHistoryCorrection> corrections) {
        return presentationRevision(snapshot == null ? "" : snapshot.getSourceEventFingerprint(), corrections);
    }

    private String presentationRevision(String sourceFingerprint, List<ProjectHistoryCorrection> corrections) {
        StringBuilder value = new StringBuilder();
        appendToken(value, sourceFingerprint);
        if (corrections != null) corrections.stream()
            .filter(item -> item.getStatus() == ProjectHistoryCorrection.Status.ACTIVE)
            .sorted(Comparator.comparing(ProjectHistoryCorrection::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ProjectHistoryCorrection::getId))
            .forEach(item -> appendCorrectionRevision(value, item));
        return "presentation:" + sha256(value.toString()).substring(0, 32);
    }

    private void appendCorrectionRevision(StringBuilder value, ProjectHistoryCorrection correction) {
        appendToken(value, correction.getId().toString());
        appendToken(value, correction.getCorrectionType());
        appendToken(value, correction.getTargetType());
        appendToken(value, correction.getTargetId());
        appendToken(value, correction.getTargetIdsJson());
        appendToken(value, correction.getDeclaredRole());
        appendToken(value, correction.getDeclaredChapterId());
        appendToken(value, correction.getDeclaredTitle());
        appendToken(value, correction.getDeclaredSummary());
        appendToken(value, correction.getSecondaryDeclaredTitle());
        appendToken(value, correction.getSecondaryDeclaredSummary());
        appendToken(value, correction.getStatus().name());
        appendToken(value, correction.getBeforePresentationRevision());
        appendToken(value, correction.getSourceFingerprint());
        appendToken(value, correction.getTargetMembershipFingerprint());
        appendToken(value, correction.getAutomaticPresentationFingerprint());
        appendToken(value, correction.getReplacedById() == null ? "" : correction.getReplacedById().toString());
        appendToken(value, correction.getUpdatedAt() == null ? "" : correction.getUpdatedAt().toString());
    }

    private ChangeStory addConflict(ChangeStory story, String correctionId, String reason) {
        return new ChangeStory(story.id(), story.primarySubjectKey(), story.humanTitle(), story.oneSentenceSummary(), story.beforeState(),
            story.change(), story.afterState(), story.affectedAreas(), story.reason(), story.reasonEvidenceRefs(), story.laterOutcome(),
            story.conflicts(), story.unknowns(), story.occurredFrom(), story.occurredTo(), story.evidenceCount(), story.rawEventCount(),
            story.authority(), story.summaryStatus(), story.coverage(), story.limitations(), story.eventRefs(), story.evidenceRefs(), story.role(),
            story.primaryStoryId(), story.supportingChangeRefs(), story.technicalAtomRefs(), story.commitSummaries(), story.technicalDetails(),
            story.presentationAuthority(), story.presentationRevision(), story.automaticTitle(), story.automaticSummary(), story.userCorrectionRefs(),
            story.hiddenByDefault(), story.pinned(), story.mergedIntoStoryId(), "CONFLICT", union(story.correctionConflicts(), List.of(correctionId + ":" + reason)));
    }

    private HistoryChapter addChapterConflict(HistoryChapter chapter, String correctionId, String reason) {
        return new HistoryChapter(chapter.id(), chapter.title(), chapter.summary(), chapter.from(), chapter.to(), chapter.boundarySignals(),
            chapter.storyRefs(), chapter.storyCount(), chapter.rawEventCount(), chapter.authority(), chapter.coverage(),
            union(chapter.limitations(), List.of("展示修正冲突 " + correctionId + "：" + reason)),
            chapter.presentationAuthority(), chapter.presentationRevision(), chapter.userDeclared(), chapter.userCorrectionRefs(),
            chapter.hiddenByDefault(), chapter.pinned());
    }

    private static <T> List<T> union(List<T> left, List<T> right) {
        LinkedHashSet<T> values = new LinkedHashSet<>();
        if (left != null) values.addAll(left);
        if (right != null) values.addAll(right);
        return values.stream().limit(200).toList();
    }

    private static List<String> append(List<String> values, String item) {
        LinkedHashSet<String> result = new LinkedHashSet<>(values == null ? List.of() : values);
        if (item != null && !item.isBlank()) result.add(item);
        return result.stream().limit(100).toList();
    }

    private static String first(List<String> ids, String fallback) {
        return ids == null || ids.isEmpty() ? safe(fallback) : safe(ids.get(0));
    }

    private static String textOr(String current, String declared) {
        return safe(declared).isBlank() ? current : declared;
    }

    private static Instant min(Instant left, Instant right) { return left == null || (right != null && right.isBefore(left)) ? right : left; }
    private static Instant max(Instant left, Instant right) { return left == null || (right != null && right.isAfter(left)) ? right : left; }

    private UUID uuid(String value) { try { return UUID.fromString(value); } catch (RuntimeException exception) { return null; } }

    private <T> List<T> read(String json, TypeReference<List<T>> type) {
        try { return objectMapper.readValue(json == null || json.isBlank() ? "[]" : json, type); }
        catch (JsonProcessingException exception) { return List.of(); }
    }

    private List<String> strings(String json) {
        return read(json, new TypeReference<List<String>>() {});
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("项目历程修正无法序列化", exception); }
    }

    private void owned(UUID userId, UUID projectId) {
        projectRepository.findByIdAndUserId(projectId, userId).orElseThrow(() ->
            new AppException("PROJECT_NOT_FOUND", "项目不存在", HttpStatus.NOT_FOUND));
    }

    private static String safe(String value) { return value == null ? "" : value.trim(); }
    private static String safeType(String value) { return safe(value).isBlank() ? "STORY" : safe(value).toUpperCase(Locale.ROOT); }
    private static AppException bad(String code, String message) { return new AppException(code, message, HttpStatus.BAD_REQUEST); }

    private static String sha256(String value) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 unavailable", exception); }
    }

    private record CorrectionState(
        boolean targetPresent,
        boolean sourceStale,
        boolean membershipStale,
        boolean automaticPresentationChanged,
        String reason
    ) {
        private boolean conflict() {
            return !targetPresent || membershipStale;
        }
    }

    private record EventSlice(
        List<UUID> eventRefs,
        List<String> evidenceRefs,
        Instant occurredFrom,
        Instant occurredTo,
        List<String> affectedAreas,
        List<String> technicalAtomRefs,
        List<String> commitSummaries,
        List<String> technicalDetails,
        List<String> limitations,
        List<String> conflicts,
        List<String> unknowns,
        boolean complete
    ) {
    }

    public record CorrectedHistory(
        List<HistoryChapter> chapters,
        List<ChangeStory> stories,
        List<EvolutionThread> threads,
        String presentationRevision,
        List<HistoryCorrectionResponse> corrections
    ) {
        public CorrectedHistory {
            chapters = chapters == null ? List.of() : List.copyOf(chapters);
            stories = stories == null ? List.of() : List.copyOf(stories);
            threads = threads == null ? List.of() : List.copyOf(threads);
            corrections = corrections == null ? List.of() : List.copyOf(corrections);
            presentationRevision = presentationRevision == null ? "" : presentationRevision;
        }
    }
}
