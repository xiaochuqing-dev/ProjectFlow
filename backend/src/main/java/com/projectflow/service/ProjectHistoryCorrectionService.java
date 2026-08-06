package com.projectflow.service;

import static com.projectflow.dto.ProjectHistoryDtos.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.entity.ProjectHistoryCorrection;
import com.projectflow.entity.ProjectHistorySnapshot;
import com.projectflow.repository.ProjectHistoryCorrectionRepository;
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
    private static final int MAX_CORRECTIONS = 2_000;
    private static final Set<String> TYPES = Set.of(
        "RENAME_STORY", "EDIT_SUMMARY", "MERGE_STORIES", "SPLIT_STORY", "SET_PRIMARY",
        "SET_SUPPORTING", "REATTACH_SUPPORTING", "HIDE_STORY", "PIN_STORY",
        "DECLARE_CHAPTER", "RENAME_CHAPTER", "RESTORE_AUTOMATIC"
    );

    private final ProjectRepository projectRepository;
    private final ProjectHistorySnapshotRepository snapshotRepository;
    private final ProjectHistoryCorrectionRepository correctionRepository;
    private final ObjectMapper objectMapper;

    public ProjectHistoryCorrectionService(
        ProjectRepository projectRepository,
        ProjectHistorySnapshotRepository snapshotRepository,
        ProjectHistoryCorrectionRepository correctionRepository,
        ObjectMapper objectMapper
    ) {
        this.projectRepository = projectRepository;
        this.snapshotRepository = snapshotRepository;
        this.correctionRepository = correctionRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public HistoryCorrectionListResponse list(UUID userId, UUID projectId) {
        owned(userId, projectId);
        ProjectHistorySnapshot snapshot = snapshotRepository.findByProjectId(projectId).orElse(null);
        List<ProjectHistoryCorrection> corrections = correctionRepository
            .findByProjectIdOrderByCreatedAtAsc(projectId);
        String revision = presentationRevision(snapshot, corrections);
        return new HistoryCorrectionListResponse(
            projectId, corrections.stream().limit(MAX_CORRECTIONS).map(value -> response(value, snapshot, revision, null)).toList(), revision,
            corrections.size() > MAX_CORRECTIONS
        );
    }

    /** Applies active corrections to a persisted snapshot without writing it back. */
    @Transactional(readOnly = true)
    public CorrectedHistory resolve(UUID projectId, ProjectHistorySnapshot snapshot) {
        if (snapshot == null) return new CorrectedHistory(List.of(), List.of(), List.of(), "", List.of());
        List<ChangeStory> stories = read(snapshot.getStoriesJson(), new TypeReference<List<ChangeStory>>() {});
        List<HistoryChapter> chapters = read(snapshot.getChaptersJson(), new TypeReference<List<HistoryChapter>>() {});
        List<EvolutionThread> threads = read(snapshot.getThreadsJson(), new TypeReference<List<EvolutionThread>>() {});
        List<ProjectHistoryCorrection> corrections = correctionRepository
            .findByProjectIdAndStatusOrderByCreatedAtAsc(projectId, ProjectHistoryCorrection.Status.ACTIVE)
            .stream().limit(MAX_CORRECTIONS).toList();
        Map<String, ChangeStory> storyMap = stories.stream().collect(
            LinkedHashMap::new, (map, value) -> map.put(value.id(), value), Map::putAll
        );
        Map<String, HistoryChapter> chapterMap = chapters.stream().collect(
            LinkedHashMap::new, (map, value) -> map.put(value.id(), value), Map::putAll
        );
        Map<String, EvolutionThread> threadMap = threads.stream().collect(
            LinkedHashMap::new, (map, value) -> map.put(value.id(), value), Map::putAll
        );
        List<HistoryCorrectionResponse> applied = new ArrayList<>();
        for (ProjectHistoryCorrection correction : corrections) {
            String type = correction.getCorrectionType().toUpperCase(Locale.ROOT);
            List<String> ids = targetIds(correction);
            if ("RESTORE_AUTOMATIC".equals(type)) {
                applied.add(response(correction, snapshot, presentationRevision(snapshot, corrections), null));
                continue;
            }
            boolean valid = targetsValid(correction, ids, storyMap, chapterMap);
            if (!valid) {
                String reason = "修正目标已不存在或已被历史重建替换";
                for (String id : ids) {
                    ChangeStory story = storyMap.get(id);
                    if (story != null) storyMap.put(id, addConflict(story, correction.getId().toString(), reason));
                    HistoryChapter chapter = chapterMap.get(id);
                    if (chapter != null) chapterMap.put(id, addChapterConflict(chapter, correction.getId().toString(), reason));
                }
                applied.add(response(correction, snapshot, presentationRevision(snapshot, corrections), reason));
                continue;
            }
            applyCorrection(correction, ids, storyMap, chapterMap, threadMap);
            applied.add(response(correction, snapshot, presentationRevision(snapshot, corrections), null));
        }
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
            presentationRevision(snapshot, corrections), List.copyOf(applied)
        );
    }

    /**
     * Returns the current presentation overlay revision for a refresh cache key.
     * The value contains only a stable hash and correction metadata; no declared
     * text is returned or persisted by the history reconstruction layer.
     */
    @Transactional(readOnly = true)
    public String currentPresentationRevision(UUID projectId, String sourceFingerprint) {
        List<ProjectHistoryCorrection> corrections = correctionRepository
            .findByProjectIdAndStatusOrderByCreatedAtAsc(projectId, ProjectHistoryCorrection.Status.ACTIVE);
        StringBuilder value = new StringBuilder(safe(sourceFingerprint));
        corrections.stream().limit(MAX_CORRECTIONS).forEach(item -> value.append('|').append(item.getId())
            .append(':').append(item.getCorrectionType()).append(':').append(item.getUpdatedAt())
            .append(':').append(item.getDeclaredTitle()).append(':').append(item.getDeclaredSummary()));
        return "presentation:" + sha256(value.toString()).substring(0, 32);
    }

    @Transactional
    public HistoryCorrectionResponse create(UUID userId, UUID projectId, HistoryCorrectionRequest request) {
        owned(userId, projectId);
        if (request == null) throw bad("PROJECT_HISTORY_CORRECTION_REQUIRED", "必须提供项目历程修正");
        ProjectHistorySnapshot snapshot = snapshotRepository.findByProjectId(projectId).orElseThrow(() ->
            new AppException("PROJECT_HISTORY_NOT_INITIALIZED", "项目历程尚未刷新，暂时不能修正", HttpStatus.CONFLICT));
        List<ProjectHistoryCorrection> existing = correctionRepository.findByProjectIdOrderByCreatedAtAsc(projectId);
        String currentRevision = presentationRevision(snapshot, existing.stream()
            .filter(value -> value.getStatus() == ProjectHistoryCorrection.Status.ACTIVE).toList());
        String expected = safe(request.expectedPresentationRevision());
        String source = safe(request.sourceFingerprint());
        String currentSource = safe(snapshot.getSourceEventFingerprint());
        String type = safe(request.type()).toUpperCase(Locale.ROOT);
        if (!TYPES.contains(type)) throw bad("INVALID_HISTORY_CORRECTION_TYPE", "不支持的项目历程修正类型");
        String normalizedTargetId = normalizedTargetId(type, request, projectId);
        List<String> normalizedTargetIds = normalizedTargetIds(type, request);
        if (!expected.isBlank() && !expected.equals(currentRevision)) {
            ProjectHistoryCorrection conflict = new ProjectHistoryCorrection(
                projectId, userId, type, safeType(request.targetType()), normalizedTargetId,
                json(normalizedTargetIds), request.effectiveTitle(), request.effectiveSummary(), request.effectiveRole(),
                effectiveChapterId(type, request, normalizedTargetId),
                expected, source.isBlank() ? currentSource : source
            );
            conflict.markConflict("展示版本已变化，请重新读取后再提交修正");
            correctionRepository.saveAndFlush(conflict);
            throw new AppException("PROJECT_HISTORY_CORRECTION_CONFLICT", "项目历程展示版本已变化，请刷新后重试", HttpStatus.CONFLICT);
        }
        if (!source.isBlank() && !source.equals(currentSource)) {
            ProjectHistoryCorrection conflict = new ProjectHistoryCorrection(
                projectId, userId, type, safeType(request.targetType()), normalizedTargetId,
                json(normalizedTargetIds), request.effectiveTitle(), request.effectiveSummary(), request.effectiveRole(),
                effectiveChapterId(type, request, normalizedTargetId),
                currentRevision, source
            );
            conflict.markConflict("来源快照已变化，修正需要重新确认");
            correctionRepository.saveAndFlush(conflict);
            throw new AppException("PROJECT_HISTORY_CORRECTION_STALE", "项目历程来源已变化，请刷新后重试", HttpStatus.CONFLICT);
        }
        List<ChangeStory> stories = read(snapshot.getStoriesJson(), new TypeReference<List<ChangeStory>>() {});
        List<HistoryChapter> chapters = read(snapshot.getChaptersJson(), new TypeReference<List<HistoryChapter>>() {});
        validateRequest(type, request, stories, chapters);
        if ("RESTORE_AUTOMATIC".equals(type)) {
            List<String> targets = correctionTargets(type, request);
            existing.stream().filter(value -> value.getStatus() == ProjectHistoryCorrection.Status.ACTIVE)
                .filter(value -> overlaps(value, targets)).forEach(value -> value.markReverted(null));
            correctionRepository.saveAll(existing);
        }
        ProjectHistoryCorrection correction = new ProjectHistoryCorrection(
            projectId, userId, type, safeType(request.targetType()), normalizedTargetId,
            json(normalizedTargetIds), request.effectiveTitle(), request.effectiveSummary(), request.effectiveRole(),
            effectiveChapterId(type, request, normalizedTargetId),
            currentRevision, currentSource
        );
        correctionRepository.saveAndFlush(correction);
        List<ProjectHistoryCorrection> after = correctionRepository.findByProjectIdOrderByCreatedAtAsc(projectId);
        return response(correction, snapshot, presentationRevision(snapshot, after), null);
    }

    @Transactional
    public HistoryCorrectionResponse revert(UUID userId, UUID projectId, UUID correctionId, String expectedRevision) {
        owned(userId, projectId);
        ProjectHistoryCorrection correction = correctionRepository.findByIdAndProjectId(correctionId, projectId)
            .orElseThrow(() -> new AppException("PROJECT_HISTORY_CORRECTION_NOT_FOUND", "项目历程修正不存在", HttpStatus.NOT_FOUND));
        ProjectHistorySnapshot snapshot = snapshotRepository.findByProjectId(projectId).orElse(null);
        List<ProjectHistoryCorrection> active = correctionRepository.findByProjectIdAndStatusOrderByCreatedAtAsc(
            projectId, ProjectHistoryCorrection.Status.ACTIVE
        );
        String revision = presentationRevision(snapshot, active);
        if (expectedRevision != null && !expectedRevision.isBlank() && !expectedRevision.equals(revision)) {
            throw new AppException("PROJECT_HISTORY_CORRECTION_CONFLICT", "项目历程展示版本已变化，请刷新后重试", HttpStatus.CONFLICT);
        }
        correction.markReverted(null);
        correctionRepository.saveAndFlush(correction);
        List<ProjectHistoryCorrection> after = correctionRepository.findByProjectIdOrderByCreatedAtAsc(projectId);
        return response(correction, snapshot, presentationRevision(snapshot, after), null);
    }

    private void applyCorrection(
        ProjectHistoryCorrection correction,
        List<String> ids,
        Map<String, ChangeStory> stories,
        Map<String, HistoryChapter> chapters,
        Map<String, EvolutionThread> threads
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
            case "MERGE_STORIES" -> merge(ids, stories, chapters, threads, correction);
            case "SPLIT_STORY" -> split(target, eventTargetIds(correction), stories, chapters, threads, correction);
            case "RENAME_CHAPTER", "DECLARE_CHAPTER" -> {
                String chapterId = effectiveChapterId(correction);
                HistoryChapter chapter = chapters.get(chapterId);
                if (chapter == null && "DECLARE_CHAPTER".equals(type)) {
                    chapter = declaredChapter(correction, chapterId, stories);
                }
                if (chapter != null) chapters.put(chapterId, chapterPresentation(chapter, correction));
            }
            default -> { }
        }
    }

    private void merge(
        List<String> ids,
        Map<String, ChangeStory> stories,
        Map<String, HistoryChapter> chapters,
        Map<String, EvolutionThread> threads,
        ProjectHistoryCorrection correction
    ) {
        if (ids.size() < 2) return;
        ChangeStory survivor = stories.get(ids.get(0));
        if (survivor == null) return;
        for (String id : ids.subList(1, ids.size())) {
            ChangeStory other = stories.get(id);
            if (other == null) continue;
            survivor = mergedStory(survivor, other, correction);
            String survivorId = survivor.id();
            stories.put(id, presentation(other, other.humanTitle(), other.oneSentenceSummary(), "",
                other.role(), survivorId, correction, true, other.pinned(), survivorId, "MERGED"));
            chapters.replaceAll((chapterId, chapter) -> replaceChapterRefs(chapter, id, survivorId));
            threads.replaceAll((threadId, thread) -> replaceThreadRefs(thread, id, survivorId));
        }
        stories.put(survivor.id(), survivor);
    }

    private void split(
        String target,
        List<String> ids,
        Map<String, ChangeStory> stories,
        Map<String, HistoryChapter> chapters,
        Map<String, EvolutionThread> threads,
        ProjectHistoryCorrection correction
    ) {
        ChangeStory original = stories.get(target);
        if (original == null || ids.isEmpty()) return;
        Set<UUID> requested = ids.stream().map(this::uuid).filter(java.util.Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<UUID> firstPart = original.eventRefs().stream().filter(requested::contains).collect(Collectors.toCollection(LinkedHashSet::new));
        if (firstPart.isEmpty() || firstPart.size() >= original.eventRefs().size()) return;
        List<UUID> secondPart = original.eventRefs().stream().filter(id -> !firstPart.contains(id)).toList();
        String newId = "story-" + sha256(original.id() + "|split|" + correction.getId()).substring(0, 20);
        ChangeStory first = storyWithEvents(original, target, firstPart, correction);
        ChangeStory second = storyWithEvents(original, newId, new LinkedHashSet<>(secondPart), correction);
        stories.put(target, first);
        stories.put(newId, second);
        chapters.replaceAll((chapterId, chapter) -> appendChapterRef(chapter, newId));
        threads.replaceAll((threadId, thread) -> appendThreadRef(thread, newId));
    }

    private ChangeStory storyWithEvents(ChangeStory original, String id, Set<UUID> eventIds, ProjectHistoryCorrection correction) {
        List<String> evidence = original.evidenceRefs();
        return new ChangeStory(
            id, original.primarySubjectKey(), original.humanTitle(), original.oneSentenceSummary(), original.beforeState(),
            original.change(), original.afterState(), original.affectedAreas(), original.reason(), original.reasonEvidenceRefs(),
            original.laterOutcome(), original.conflicts(), original.unknowns(), original.occurredFrom(), original.occurredTo(),
            evidence.size(), eventIds.size(), original.authority(), original.summaryStatus(), original.coverage(),
            original.limitations(), List.copyOf(eventIds), evidence, original.role(), original.primaryStoryId(),
            original.supportingChangeRefs(), original.technicalAtomRefs(), original.commitSummaries(), original.technicalDetails(),
            USER_DECLARED_PRESENTATION, correction.getId().toString(), original.automaticTitle(), original.automaticSummary(),
            append(original.userCorrectionRefs(), correction.getId().toString()), original.hiddenByDefault(), original.pinned(),
            "", "ACTIVE", original.correctionConflicts()
        );
    }

    private ChangeStory mergedStory(ChangeStory left, ChangeStory right, ProjectHistoryCorrection correction) {
        return new ChangeStory(
            left.id(), left.primarySubjectKey(), safe(correction.getDeclaredTitle()).isBlank() ? left.humanTitle() : correction.getDeclaredTitle(),
            safe(correction.getDeclaredSummary()).isBlank() ? left.oneSentenceSummary() : correction.getDeclaredSummary(),
            left.beforeState(), left.change(), left.afterState(), union(left.affectedAreas(), right.affectedAreas()), left.reason(),
            union(left.reasonEvidenceRefs(), right.reasonEvidenceRefs()), left.laterOutcome(), union(left.conflicts(), right.conflicts()),
            union(left.unknowns(), right.unknowns()), min(left.occurredFrom(), right.occurredFrom()), max(left.occurredTo(), right.occurredTo()),
            left.evidenceCount() + right.evidenceCount(), left.rawEventCount() + right.rawEventCount(), left.authority(),
            "USER_DECLARED_PRESENTATION", left.coverage(), union(left.limitations(), right.limitations()), union(left.eventRefs(), right.eventRefs()),
            union(left.evidenceRefs(), right.evidenceRefs()), "PRIMARY", "", union(left.supportingChangeRefs(), right.supportingChangeRefs()),
            union(left.technicalAtomRefs(), right.technicalAtomRefs()), union(left.commitSummaries(), right.commitSummaries()),
            union(left.technicalDetails(), right.technicalDetails()), USER_DECLARED_PRESENTATION, correction.getId().toString(),
            left.automaticTitle(), left.automaticSummary(), append(left.userCorrectionRefs(), correction.getId().toString()),
            left.hiddenByDefault(), left.pinned(), "", "ACTIVE", union(left.correctionConflicts(), right.correctionConflicts())
        );
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

    private String automaticValue(ProjectHistoryCorrection correction, ProjectHistorySnapshot snapshot) {
        if (snapshot == null) return "";
        String type = correction.getCorrectionType().toUpperCase(Locale.ROOT);
        List<ChangeStory> stories = read(snapshot.getStoriesJson(), new TypeReference<List<ChangeStory>>() {});
        List<HistoryChapter> chapters = read(snapshot.getChaptersJson(), new TypeReference<List<HistoryChapter>>() {});
        if (type.endsWith("CHAPTER")) {
            String chapterId = effectiveChapterId(correction);
            return chapters.stream().filter(item -> item.id().equals(chapterId)).map(HistoryChapter::title).findFirst().orElse("");
        }
        ChangeStory target = stories.stream().filter(item -> item.id().equals(correction.getTargetId())).findFirst().orElse(null);
        if (target == null && "MERGE_STORIES".equals(type)) {
            target = stories.stream().filter(item -> rawTargetIds(correction).contains(item.id())).findFirst().orElse(null);
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

    private boolean targetPresent(ProjectHistoryCorrection correction, ProjectHistorySnapshot snapshot) {
        if (snapshot == null) return false;
        String type = correction.getCorrectionType().toUpperCase(Locale.ROOT);
        List<ChangeStory> stories = read(snapshot.getStoriesJson(), new TypeReference<List<ChangeStory>>() {});
        List<HistoryChapter> chapters = read(snapshot.getChaptersJson(), new TypeReference<List<HistoryChapter>>() {});
        if ("DECLARE_CHAPTER".equals(type)) {
            return rawTargetIds(correction).stream().allMatch(id -> stories.stream().anyMatch(story -> story.id().equals(id)));
        }
        if (type.endsWith("CHAPTER")) return chapters.stream().anyMatch(item -> item.id().equals(effectiveChapterId(correction)));
        if ("SPLIT_STORY".equals(type)) {
            ChangeStory story = stories.stream().filter(item -> item.id().equals(correction.getTargetId())).findFirst().orElse(null);
            if (story == null) return false;
            Set<UUID> requested = rawTargetIds(correction).stream().map(this::uuid).filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
            return story.eventRefs().stream().anyMatch(requested::contains);
        }
        return stories.stream().anyMatch(item -> item.id().equals(correction.getTargetId()));
    }

    private HistoryChapter replaceChapterRefs(HistoryChapter chapter, String oldId, String newId) {
        List<String> refs = chapter.storyRefs().stream().map(id -> id.equals(oldId) ? newId : id).distinct().toList();
        return new HistoryChapter(chapter.id(), chapter.title(), chapter.summary(), chapter.from(), chapter.to(), chapter.boundarySignals(),
            refs, refs.size(), chapter.rawEventCount(), chapter.authority(), chapter.coverage(), chapter.limitations(),
            chapter.presentationAuthority(), chapter.presentationRevision(), chapter.userDeclared(), chapter.userCorrectionRefs(),
            chapter.hiddenByDefault(), chapter.pinned());
    }

    private HistoryChapter appendChapterRef(HistoryChapter chapter, String id) {
        if (chapter.storyRefs().contains(id)) return chapter;
        return replaceChapterRefs(chapter, "__none__", id);
    }

    private EvolutionThread replaceThreadRefs(EvolutionThread thread, String oldId, String newId) {
        List<String> refs = thread.storyRefs().stream().map(id -> id.equals(oldId) ? newId : id).distinct().toList();
        return new EvolutionThread(thread.id(), thread.subjectKey(), thread.subjectLabel(), thread.subjectType(), refs,
            thread.transitions(), thread.currentOutcome(), thread.gaps(), thread.conflicts(), thread.unknowns(), thread.evidenceCount(),
            thread.capabilityId(), thread.presentationAuthority(), thread.presentationRevision(), thread.userCorrectionRefs());
    }

    private EvolutionThread appendThreadRef(EvolutionThread thread, String id) {
        if (thread.storyRefs().contains(id)) return thread;
        return replaceThreadRefs(thread, "__none__", id);
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
            return stories.containsKey(safe(correction.getTargetId()))
                && !primary.isBlank() && stories.containsKey(primary)
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
            if (target.isBlank() || primary.isBlank() || target.equals(primary)
                || stories.stream().noneMatch(story -> story.id().equals(target))
                || stories.stream().noneMatch(story -> story.id().equals(primary))) {
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
        return result.stream().filter(value -> value != null && !value.isBlank()).limit(100).toList();
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
        return result.stream().limit(100).toList();
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
        return result.stream().filter(value -> value != null && !value.isBlank()).limit(100).toList();
    }

    private List<String> rawTargetIds(ProjectHistoryCorrection correction) {
        try {
            return objectMapper.readValue(correction.getTargetIdsJson(), new TypeReference<List<String>>() {})
                .stream().filter(value -> value != null && !value.isBlank()).distinct().limit(100).toList();
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

    private HistoryCorrectionResponse response(ProjectHistoryCorrection value) {
        return response(value, null, "", null);
    }

    private HistoryCorrectionResponse response(ProjectHistoryCorrection value, ProjectHistorySnapshot snapshot,
        String revision, String reason) {
        String automatic = automaticValue(value, snapshot);
        String applied = appliedValue(value, automatic);
        String difference = safe(reason).isBlank()
            ? (automatic.equals(applied) ? "无展示差异" : "用户声明覆盖自动展示") : reason;
        boolean present = targetPresent(value, snapshot);
        String status = safe(reason).isBlank() ? value.getStatus().name() : "CONFLICT";
        return new HistoryCorrectionResponse(value.getId(), value.getProjectId(), value.getCorrectionType(), value.getTargetType(),
            value.getTargetId(), rawTargetIds(value), status, value.getBeforePresentationRevision(), value.getSourceFingerprint(),
            safe(reason).isBlank() ? value.getConflictReason() : reason, value.getCreatedAt(), value.getUpdatedAt(), revision,
            value.getDeclaredTitle(), value.getDeclaredSummary(), value.getDeclaredRole(), value.getDeclaredChapterId(),
            automatic, applied, difference, present);
    }

    private HistoryCorrectionResponse conflictResponse(ProjectHistoryCorrection value, String reason) {
        return response(value, null, "", reason);
    }

    private String presentationRevision(ProjectHistorySnapshot snapshot, List<ProjectHistoryCorrection> corrections) {
        StringBuilder value = new StringBuilder(snapshot == null ? "" : snapshot.getSourceEventFingerprint());
        if (corrections != null) corrections.stream().filter(item -> item.getStatus() == ProjectHistoryCorrection.Status.ACTIVE)
            .forEach(item -> value.append('|').append(item.getId()).append(':').append(item.getCorrectionType()).append(':')
                .append(item.getUpdatedAt()).append(':').append(item.getDeclaredTitle()).append(':').append(item.getDeclaredSummary()));
        return "presentation:" + sha256(value.toString()).substring(0, 32);
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
