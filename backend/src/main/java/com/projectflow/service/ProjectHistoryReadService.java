package com.projectflow.service;

import static com.projectflow.dto.ProjectHistoryDtos.*;

import java.time.Instant;
import java.time.ZoneOffset;
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

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.dto.ProjectMemoryGatewayDtos.MemoryFactTraceResponse;
import com.projectflow.entity.ProjectFactEpistemicStatus;
import com.projectflow.entity.ProjectHistoryEvent;
import com.projectflow.entity.ProjectHistoryEvent.Authority;
import com.projectflow.entity.ProjectHistoryEvent.Category;
import com.projectflow.entity.ProjectHistoryEvent.RewriteState;
import com.projectflow.entity.ProjectHistoryEvent.SourceType;
import com.projectflow.entity.ProjectHistoryEvent.Transition;
import com.projectflow.entity.ProjectHistorySnapshot;
import com.projectflow.repository.ProjectHistoryEventRepository;
import com.projectflow.repository.ProjectHistorySnapshotRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.support.AppException;

@Service
public class ProjectHistoryReadService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final int OVERVIEW_CHAPTER_LIMIT = 8;
    private static final int CURRENT_STATE_STORY_LIMIT = 12;
    private static final int CURRENT_STATE_PRIMARY_LIMIT = 4;
    private final ProjectRepository projectRepository;
    private final ProjectHistorySnapshotRepository snapshotRepository;
    private final ProjectHistoryEventRepository eventRepository;
    private final ProjectHistoryCorrectionService correctionService;
    private final ProjectEvidenceTraceService evidenceTraceService;
    private final SensitiveContentRedactor redactor;
    private final ProjectHistoryLanguageService languageService;
    private final ObjectMapper objectMapper;

    public ProjectHistoryReadService(
        ProjectRepository projectRepository,
        ProjectHistorySnapshotRepository snapshotRepository,
        ProjectHistoryEventRepository eventRepository,
        ProjectHistoryCorrectionService correctionService,
        ProjectEvidenceTraceService evidenceTraceService,
        SensitiveContentRedactor redactor,
        ProjectHistoryLanguageService languageService,
        ObjectMapper objectMapper
    ) {
        this.projectRepository = projectRepository;
        this.snapshotRepository = snapshotRepository;
        this.eventRepository = eventRepository;
        this.correctionService = correctionService;
        this.evidenceTraceService = evidenceTraceService;
        this.redactor = redactor;
        this.languageService = languageService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public HistoryOverviewResponse overview(UUID userId, UUID projectId) {
        owned(userId, projectId);
        ProjectHistorySnapshot snapshot = snapshotRepository.findByProjectId(projectId).orElse(null);
        if (snapshot == null) {
            return new HistoryOverviewResponse(
                projectId, "", "NOT_INITIALIZED", "", 0, null, null,
                ProjectHistoryReconstructionService.STRATEGY_VERSION,
                ProjectHistoryReconstructionService.PROMPT_VERSION,
                new HistoryOverviewContent(
                    "尚未刷新项目历程。", "当前没有持久化历程快照。", List.of(), List.of(), List.of(),
                    List.of("请显式调用项目历程刷新；GET 不会运行 Git、文件扫描或模型。 ")
                ), emptyCoverage(), Map.of(), null, null, null, null, "", ""
            );
        }
        ProjectHistoryCorrectionService.CorrectedHistory corrected = correctionService.resolve(projectId, snapshot);
        HistoryOverviewContent automaticOverview = value(snapshot.getOverviewJson(), HistoryOverviewContent.class,
            new HistoryOverviewContent("", "", List.of(), List.of(), List.of(), List.of()));
        HistoryOverviewContent displayOverview = correctedOverview(automaticOverview, corrected);
        Map<String, Object> displayDiagnostics = new LinkedHashMap<>(map(snapshot.getDiagnosticsJson()));
        displayDiagnostics.put("presentationRevision", corrected.presentationRevision());
        displayDiagnostics.put("activeCorrectionCount", corrected.corrections().size());
        displayDiagnostics.put("continuityDirty", !snapshot.getContinuityDirtyRevision().isBlank());
        displayDiagnostics.put("pendingContinuityRevision", snapshot.getContinuityDirtyRevision());
        displayDiagnostics.put("pendingContinuityReason", snapshot.getContinuityDirtyReason());
        return new HistoryOverviewResponse(
            projectId, corrected.presentationRevision(), snapshot.getStatus().name(), snapshot.getProjectRevision(), snapshot.getSourceEventCount(),
            snapshot.getEarliestEventAt(), snapshot.getLatestEventAt(), snapshot.getStrategyVersion(), snapshot.getPromptVersion(),
            displayOverview,
            value(snapshot.getCoverageJson(), HistoryCoverage.class, emptyCoverage()),
            displayDiagnostics, snapshot.getAnalysisJobId(), snapshot.getGeneratedAt(),
                snapshot.getLatestSuccessfulAt(), snapshot.getUpdatedAt(), outbound(snapshot.getErrorCode()), outbound(snapshot.getErrorSummary())
        );
    }

    @Transactional(readOnly = true)
    public ProjectCurrentStateResponse currentState(UUID userId, UUID projectId) {
        owned(userId, projectId);
        ProjectHistorySnapshot snapshot = snapshotRepository.findByProjectId(projectId).orElse(null);
        if (snapshot == null) {
            String revision = "current-state:" + ProjectHistorySourceCollector.sha256(
                "project-current-state-v1|" + projectId + "|NOT_INITIALIZED"
            );
            return new ProjectCurrentStateResponse(
                projectId, revision, "NOT_INITIALIZED", "NOT_INITIALIZED", "", "", "",
                false, "", "", null,
                "当前没有持久化项目历程状态。", List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of("尚未刷新项目历程。"), List.of("请显式刷新；当前读取不会扫描来源或调用模型。"),
                false, false, false, null
            );
        }
        ProjectHistoryCorrectionService.CorrectedHistory corrected = correctionService.resolve(projectId, snapshot);
        HistoryOverviewContent automatic = value(snapshot.getOverviewJson(), HistoryOverviewContent.class,
            new HistoryOverviewContent("", "", List.of(), List.of(), List.of(), List.of()));
        HistoryCoverage coverage = value(snapshot.getCoverageJson(), HistoryCoverage.class, emptyCoverage());
        List<ChangeStory> activeStories = corrected.stories().stream()
            .filter(story -> !"MERGED".equals(story.displayStatus()))
            .toList();
        List<ChangeStory> visibleStories = activeStories.stream()
            .filter(story -> !story.hiddenByDefault())
            .sorted(currentStoryOrder())
            .toList();
        List<ChangeStory> recent = visibleStories.stream().limit(CURRENT_STATE_STORY_LIMIT).toList();
        List<ChangeStory> currentPrimaryStories = currentPrimaryStories(corrected, visibleStories);
        String confirmed = confirmedState(automatic, activeStories, visibleStories, currentPrimaryStories);
        List<String> storyRefs = recent.stream().map(ChangeStory::id).toList();
        Set<String> recentIds = new LinkedHashSet<>(storyRefs);
        List<String> threadRefs = corrected.threads().stream()
            .filter(thread -> thread.storyRefs().stream().anyMatch(recentIds::contains))
            .map(EvolutionThread::id).distinct().limit(20).toList();
        List<String> chapterRefs = corrected.chapters().stream()
            .filter(chapter -> chapter.storyRefs().stream().anyMatch(recentIds::contains))
            .map(HistoryChapter::id).distinct().limit(20).toList();
        List<String> conflicts = boundedDistinct(java.util.stream.Stream.concat(
            automatic.conflicts().stream(), corrected.stories().stream()
                .flatMap(story -> java.util.stream.Stream.concat(story.conflicts().stream(), story.correctionConflicts().stream()))
        ).toList(), 50);
        List<String> unknowns = boundedDistinct(java.util.stream.Stream.concat(
            automatic.unknowns().stream(), corrected.stories().stream().flatMap(story -> story.unknowns().stream())
        ).toList(), 50);
        List<String> limitations = new ArrayList<>(coverage.limitations());
        recent.stream().flatMap(story -> story.limitations().stream()).forEach(limitations::add);
        if (snapshot.getErrorSummary() != null && !snapshot.getErrorSummary().isBlank()) {
            limitations.add(outbound(snapshot.getErrorSummary()));
        }
        boolean continuityDirty = !snapshot.getContinuityDirtyRevision().isBlank();
        if (continuityDirty) {
            limitations.add("存在 ProjectFlow 已知内部写入，等待下一次显式 continuity refresh。 ");
        }
        limitations = new ArrayList<>(boundedDistinct(limitations, 50));
        String status = snapshot.getStatus().name();
        boolean stale = continuityDirty || Set.of("STALE", "RUNNING").contains(status)
            || (coverage.currentness() != null && coverage.currentness().toUpperCase(Locale.ROOT).contains("STALE"));
        boolean degraded = Set.of("DEGRADED", "FAILED").contains(status) || !coverage.complete();
        String currentness = "RUNNING".equals(status) ? "REFRESH_RUNNING"
            : continuityDirty && degraded ? "DEGRADED_STALE"
            : continuityDirty || "STALE".equals(status) ? "STALE"
            : degraded ? "DEGRADED"
            : coverage.currentness();
        List<String> recentChanges = recent.stream()
            .map(story -> outbound(story.humanTitle()) + "（" + date(story.occurredTo()) + "）")
            .limit(8).toList();
        String stateRevision = currentStateRevision(
            projectId, snapshot, corrected.presentationRevision(), currentness, confirmed, recentChanges,
            threadRefs, storyRefs, chapterRefs, conflicts, unknowns, limitations
        );
        return new ProjectCurrentStateResponse(
            projectId, stateRevision, status, currentness, outbound(snapshot.getProjectRevision()),
            outbound(snapshot.getSourceEventFingerprint()), corrected.presentationRevision(), continuityDirty,
            snapshot.getContinuityDirtyRevision(), snapshot.getContinuityDirtyReason(), snapshot.getContinuityDirtyAt(),
            outbound(confirmed),
            recentChanges, threadRefs, storyRefs, chapterRefs, conflicts, unknowns, limitations,
            stale, degraded, false, snapshot.getLatestSuccessfulAt()
        );
    }

    private List<ChangeStory> currentPrimaryStories(
        ProjectHistoryCorrectionService.CorrectedHistory corrected,
        List<ChangeStory> visibleStories
    ) {
        Map<String, ChangeStory> storiesById = visibleStories.stream().collect(
            LinkedHashMap::new, (map, story) -> map.put(story.id(), story), Map::putAll
        );
        HistoryChapter latestChapter = corrected.chapters().stream()
            .filter(chapter -> !chapter.hiddenByDefault())
            .filter(chapter -> chapter.storyRefs().stream()
                .map(storiesById::get).anyMatch(story -> story != null && story.primary()))
            .sorted(currentChapterOrder())
            .findFirst().orElse(null);
        LinkedHashSet<String> selectedIds = new LinkedHashSet<>();
        if (latestChapter != null) {
            latestChapter.storyRefs().stream()
                .map(storiesById::get)
                .filter(story -> story != null && story.primary())
                .sorted(currentStoryOrder())
                .map(ChangeStory::id)
                .forEach(selectedIds::add);
        }
        visibleStories.stream().filter(ChangeStory::primary).map(ChangeStory::id).forEach(selectedIds::add);
        return selectedIds.stream().map(storiesById::get)
            .filter(java.util.Objects::nonNull)
            .sorted(currentStoryOrder())
            .limit(CURRENT_STATE_PRIMARY_LIMIT)
            .toList();
    }

    private String confirmedState(
        HistoryOverviewContent automatic,
        List<ChangeStory> activeStories,
        List<ChangeStory> visibleStories,
        List<ChangeStory> currentPrimaryStories
    ) {
        List<String> outcomes = currentPrimaryStories.stream()
            .filter(story -> story.afterState() != null && !story.afterState().isBlank())
            .map(story -> {
                String title = outbound(story.humanTitle());
                String state = outbound(story.afterState());
                return title.isBlank() ? state : title + "：" + state;
            })
            .filter(value -> !value.isBlank()).distinct().limit(CURRENT_STATE_PRIMARY_LIMIT).toList();
        if (!outcomes.isEmpty()) {
            return outcomes.size() == 1
                ? "当前可确认的结果为：" + outcomes.get(0) + "。"
                : "当前可确认的结果包括：" + String.join("；", outcomes) + "。";
        }
        boolean hasActivePrimary = activeStories.stream().anyMatch(ChangeStory::primary);
        boolean hasVisiblePrimary = visibleStories.stream().anyMatch(ChangeStory::primary);
        if (hasActivePrimary && !hasVisiblePrimary) {
            return "当前 Primary 结果已被展示修正隐藏；读取端不会用隐藏内容替代当前可确认状态。";
        }
        if (hasVisiblePrimary) {
            return "当前存在 Primary 变化，但没有可确认的结果状态；Supporting 变化不会替代 Primary 结果。";
        }
        if (!activeStories.isEmpty() && !hasActivePrimary) {
            return "当前没有可确认的 Primary 结果；Supporting 变化仅作为相关上下文保留。";
        }
        String fallback = automatic.currentState();
        return fallback == null || fallback.isBlank() ? "当前没有可确认的持久化状态。" : fallback;
    }

    private static Comparator<ChangeStory> currentStoryOrder() {
        return Comparator.comparing(ChangeStory::occurredTo, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(ChangeStory::occurredFrom, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(ChangeStory::id, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private static Comparator<HistoryChapter> currentChapterOrder() {
        return Comparator.comparing(HistoryChapter::to, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(HistoryChapter::from, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(HistoryChapter::id, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private String currentStateRevision(
        UUID projectId,
        ProjectHistorySnapshot snapshot,
        String presentationRevision,
        String currentness,
        String confirmedState,
        List<String> recentChanges,
        List<String> threadRefs,
        List<String> storyRefs,
        List<String> chapterRefs,
        List<String> conflicts,
        List<String> unknowns,
        List<String> limitations
    ) {
        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("version", "project-current-state-v1");
        canonical.put("projectId", projectId);
        canonical.put("historyStatus", snapshot.getStatus().name());
        canonical.put("currentness", currentness);
        canonical.put("projectRevision", snapshot.getProjectRevision());
        canonical.put("sourceFingerprint", snapshot.getSourceEventFingerprint());
        canonical.put("presentationRevision", presentationRevision);
        canonical.put("continuityDirtyRevision", snapshot.getContinuityDirtyRevision());
        canonical.put("continuityDirtyReason", snapshot.getContinuityDirtyReason());
        canonical.put("confirmedState", confirmedState);
        canonical.put("recentChanges", recentChanges);
        canonical.put("threadRefs", threadRefs);
        canonical.put("storyRefs", storyRefs);
        canonical.put("chapterRefs", chapterRefs);
        canonical.put("conflicts", conflicts);
        canonical.put("unknowns", unknowns);
        canonical.put("limitations", limitations);
        try {
            return "current-state:" + ProjectHistorySourceCollector.sha256(objectMapper.writeValueAsString(canonical));
        } catch (JsonProcessingException exception) {
            return "current-state:" + ProjectHistorySourceCollector.sha256(canonical.toString());
        }
    }

    private List<String> boundedDistinct(List<String> values, int limit) {
        return (values == null ? List.<String>of() : values).stream()
            .map(this::outbound).filter(value -> !value.isBlank()).distinct().limit(limit).toList();
    }

    @Transactional(readOnly = true)
    public HistoryChapterPageResponse chapters(UUID userId, UUID projectId, int page, int size) {
        ProjectHistoryCorrectionService.CorrectedHistory corrected = corrected(userId, projectId);
        List<HistoryChapter> values = corrected.chapters();
        Slice<HistoryChapter> slice = slice(values, page, size);
        return new HistoryChapterPageResponse(projectId, corrected.presentationRevision(), slice.items(), slice.page(), slice.size(), slice.total(), slice.totalPages());
    }

    @Transactional(readOnly = true)
    public HistoryChapterDetailResponse chapter(UUID userId, UUID projectId, String chapterId) {
        ProjectHistoryCorrectionService.CorrectedHistory corrected = corrected(userId, projectId);
        List<HistoryChapter> chapters = corrected.chapters();
        HistoryChapter chapter = chapters.stream().filter(item -> item.id().equals(chapterId)).findFirst()
            .orElseThrow(() -> new AppException("PROJECT_HISTORY_CHAPTER_NOT_FOUND", "项目历程篇章不存在", HttpStatus.NOT_FOUND));
        Map<String, ChangeStory> stories = corrected.stories()
            .stream().collect(LinkedHashMap::new, (map, item) -> map.put(item.id(), item), Map::putAll);
        return new HistoryChapterDetailResponse(
            projectId, corrected.presentationRevision(), chapter, chapter.storyRefs().stream().map(stories::get).filter(java.util.Objects::nonNull)
                .filter(story -> !story.hiddenByDefault()).toList()
        );
    }

    @Transactional(readOnly = true)
    public HistoryStoryPageResponse stories(
        UUID userId, UUID projectId, String subject, boolean attentionOnly, Instant from, Instant to, int page, int size
    ) {
        return stories(userId, projectId, subject, attentionOnly, false, from, to, page, size);
    }

    @Transactional(readOnly = true)
    public HistoryStoryPageResponse stories(
        UUID userId, UUID projectId, String subject, boolean attentionOnly, boolean includeHidden,
        Instant from, Instant to, int page, int size
    ) {
        owned(userId, projectId);
        validateTimeRange(from, to);
        ProjectHistoryCorrectionService.CorrectedHistory corrected = corrected(userId, projectId);
        List<ChangeStory> values = corrected.stories()
            .stream().filter(story -> includeHidden || !story.hiddenByDefault())
            .filter(story -> subject == null || subject.isBlank()
                || story.primarySubjectKey().contains(normalize(subject))
                || story.affectedAreas().stream().anyMatch(area -> area.toLowerCase(Locale.ROOT).contains(subject.toLowerCase(Locale.ROOT))))
            .filter(story -> !attentionOnly || !story.conflicts().isEmpty() || !story.unknowns().isEmpty()
                || !story.correctionConflicts().isEmpty())
            .filter(story -> from == null || !story.occurredTo().isBefore(from))
            .filter(story -> to == null || !story.occurredFrom().isAfter(to))
            .sorted(Comparator.comparing(ChangeStory::pinned).reversed()
                .thenComparing(ChangeStory::occurredFrom, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ChangeStory::id))
            .toList();
        Slice<ChangeStory> slice = slice(values, page, size);
        return new HistoryStoryPageResponse(projectId, corrected.presentationRevision(), slice.items(), slice.page(), slice.size(), slice.total(), slice.totalPages());
    }

    @Transactional(readOnly = true)
    public HistoryStoryDetailResponse story(UUID userId, UUID projectId, String storyId) {
        ProjectHistoryCorrectionService.CorrectedHistory corrected = corrected(userId, projectId);
        List<ChangeStory> stories = corrected.stories();
        ChangeStory story = stories.stream().filter(item -> item.id().equals(storyId)).findFirst()
            .orElseThrow(() -> new AppException("PROJECT_HISTORY_STORY_NOT_FOUND", "项目变化故事不存在", HttpStatus.NOT_FOUND));
        Map<UUID, ProjectHistoryEvent> events = eventRepository.findAllById(story.eventRefs()).stream()
            .filter(event -> projectId.equals(event.getProjectId()))
            .collect(LinkedHashMap::new, (map, event) -> map.put(event.getId(), event), Map::putAll);
        List<HistoryEventResponse> orderedEvents = story.eventRefs().stream().map(events::get).filter(java.util.Objects::nonNull)
            .map(this::toEvent).toList();
        List<EvolutionThread> threads = corrected.threads()
            .stream().filter(thread -> thread.storyRefs().contains(storyId)).toList();
        return new HistoryStoryDetailResponse(projectId, corrected.presentationRevision(), story, orderedEvents, threads);
    }

    @Transactional(readOnly = true)
    public EvolutionThreadPageResponse threads(UUID userId, UUID projectId, String subject, int page, int size) {
        ProjectHistoryCorrectionService.CorrectedHistory corrected = corrected(userId, projectId);
        List<EvolutionThread> values = corrected.threads()
            .stream().filter(thread -> subject == null || subject.isBlank()
                || thread.subjectKey().contains(normalize(subject))
                || thread.subjectLabel().toLowerCase(Locale.ROOT).contains(subject.toLowerCase(Locale.ROOT)))
            .toList();
        Slice<EvolutionThread> slice = slice(values, page, size);
        return new EvolutionThreadPageResponse(projectId, corrected.presentationRevision(), slice.items(), slice.page(), slice.size(), slice.total(), slice.totalPages());
    }

    @Transactional(readOnly = true)
    public EvolutionThreadDetailResponse thread(UUID userId, UUID projectId, String threadId) {
        ProjectHistoryCorrectionService.CorrectedHistory corrected = corrected(userId, projectId);
        List<EvolutionThread> threads = corrected.threads();
        EvolutionThread thread = threads.stream().filter(item -> item.id().equals(threadId)).findFirst()
            .orElseThrow(() -> new AppException("PROJECT_HISTORY_THREAD_NOT_FOUND", "项目演变链不存在", HttpStatus.NOT_FOUND));
        Map<String, ChangeStory> stories = corrected.stories()
            .stream().collect(LinkedHashMap::new, (map, item) -> map.put(item.id(), item), Map::putAll);
        return new EvolutionThreadDetailResponse(
            projectId, corrected.presentationRevision(), thread,
            thread.storyRefs().stream().map(stories::get).filter(java.util.Objects::nonNull).toList()
        );
    }

    @Transactional(readOnly = true)
    public HistoryEventPageResponse events(
        UUID userId,
        UUID projectId,
        String sourceType,
        String category,
        String transition,
        String authority,
        String epistemicStatus,
        String rewriteState,
        String subject,
        boolean attentionOnly,
        Instant from,
        Instant to,
        int page,
        int size
    ) {
        owned(userId, projectId);
        validateTimeRange(from, to);
        SourceType source = enumValue(SourceType.class, sourceType, "INVALID_HISTORY_SOURCE_TYPE");
        Category eventCategory = enumValue(Category.class, category, "INVALID_HISTORY_CATEGORY");
        Transition eventTransition = enumValue(Transition.class, transition, "INVALID_HISTORY_TRANSITION");
        Authority eventAuthority = enumValue(Authority.class, authority, "INVALID_HISTORY_AUTHORITY");
        ProjectFactEpistemicStatus status = enumValue(ProjectFactEpistemicStatus.class, epistemicStatus, "INVALID_EPISTEMIC_STATUS");
        RewriteState rewrite = enumValue(RewriteState.class, rewriteState, "INVALID_HISTORY_REWRITE_STATE");
        Specification<ProjectHistoryEvent> specification = (root, query, builder) -> builder.equal(root.get("projectId"), projectId);
        if (source != null) specification = specification.and((root, query, builder) -> builder.equal(root.get("sourceType"), source));
        if (eventCategory != null) specification = specification.and((root, query, builder) -> builder.equal(root.get("category"), eventCategory));
        if (eventTransition != null) specification = specification.and((root, query, builder) -> builder.equal(root.get("transition"), eventTransition));
        if (eventAuthority != null) specification = specification.and((root, query, builder) -> builder.equal(root.get("authority"), eventAuthority));
        if (status != null) specification = specification.and((root, query, builder) -> builder.equal(root.get("epistemicStatus"), status));
        if (rewrite != null) specification = specification.and((root, query, builder) -> builder.equal(root.get("rewriteState"), rewrite));
        if (from != null) specification = specification.and((root, query, builder) -> builder.greaterThanOrEqualTo(root.get("occurredAt"), from));
        if (to != null) specification = specification.and((root, query, builder) -> builder.lessThanOrEqualTo(root.get("occurredAt"), to));
        if (subject != null && !subject.isBlank()) {
            String query = "%" + normalize(subject) + "%";
            specification = specification.and((root, ignored, builder) -> builder.like(builder.lower(root.get("subjectKeysJson")), query));
        }
        if (attentionOnly) {
            specification = specification.and((root, query, builder) -> builder.or(
                builder.equal(root.get("epistemicStatus"), ProjectFactEpistemicStatus.CONFLICTED),
                builder.equal(root.get("epistemicStatus"), ProjectFactEpistemicStatus.UNKNOWN),
                builder.equal(root.get("authority"), Authority.UNKNOWN),
                builder.notEqual(root.get("limitationsJson"), "[]")
            ));
        }
        int boundedPage = Math.max(0, page);
        int boundedSize = Math.max(1, Math.min(MAX_PAGE_SIZE, size));
        Page<ProjectHistoryEvent> result = eventRepository.findAll(
            specification,
            PageRequest.of(boundedPage, boundedSize, Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id")))
        );
        return new HistoryEventPageResponse(
            projectId, result.getContent().stream().map(this::toEvent).toList(), boundedPage, boundedSize,
            result.getTotalElements(), result.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public HistoryEventResponse event(UUID userId, UUID projectId, UUID eventId) {
        owned(userId, projectId);
        return toEvent(eventRepository.findByIdAndProjectId(eventId, projectId)
            .orElseThrow(() -> new AppException("PROJECT_HISTORY_EVENT_NOT_FOUND", "项目历程事件不存在", HttpStatus.NOT_FOUND)));
    }

    @Transactional(readOnly = true)
    public HistoryEvidenceResponse evidence(UUID userId, UUID projectId, UUID eventId) {
        owned(userId, projectId);
        ProjectHistoryEvent event = eventRepository.findByIdAndProjectId(eventId, projectId)
            .orElseThrow(() -> new AppException("PROJECT_HISTORY_EVENT_NOT_FOUND", "项目历程事件不存在", HttpStatus.NOT_FOUND));
        List<String> refs = strings(event.getEvidenceRefsJson());
        List<HistoryEvidenceItem> items = new ArrayList<>();
        for (String ref : refs.stream().limit(100).toList()) {
            if (ref.startsWith("fact:")) {
                UUID factId = uuid(ref.substring(5));
                if (factId != null) {
                    MemoryFactTraceResponse trace = evidenceTraceService.trace(userId, projectId, factId, "compact");
                    items.add(new HistoryEvidenceItem(
                        "PROJECT_FACT", ref, trace.title(), "CURRENT", event.getSourceRevision(), trace.recordStatus(),
                        "ProjectFact 证据追踪", trace.detailTruncated() ? List.of("Evidence 详情已按安全上限截断。 ") : List.of(),
                         "/projects/" + projectId + "/facts/" + factId
                    ));
                    continue;
                }
            }
            String type = ref.contains(":") ? ref.substring(0, ref.indexOf(':')).toUpperCase(Locale.ROOT) : "SOURCE";
            items.add(new HistoryEvidenceItem(
                type, outbound(ref), outbound(event.getSafeSourceLabel()), event.getRewriteState().name(), outbound(event.getSourceRevision()),
                event.getEpistemicStatus().name(), safeCoverage(event.getCoverageJson()), strings(event.getLimitationsJson()),
                ProjectHistorySourceCollector.safeDeepLink(event.getRawSourceDeepLink())
            ));
        }
        return new HistoryEvidenceResponse(projectId, eventId, items, refs.size() > items.size());
    }

    public HistoryFiltersResponse filters() {
        return new HistoryFiltersResponse(
            names(SourceType.values()), names(Category.values()), names(Transition.values()), names(Authority.values()),
            names(ProjectFactEpistemicStatus.values()), names(RewriteState.values())
        );
    }

    @Transactional(readOnly = true)
    public HistoryCorrectionListResponse corrections(UUID userId, UUID projectId) {
        return correctionService.list(userId, projectId);
    }

    @Transactional(readOnly = true)
    public HistoryCorrectionListResponse corrections(UUID userId, UUID projectId, int page, int size) {
        return correctionService.list(userId, projectId, page, size);
    }

    private ProjectHistoryCorrectionService.CorrectedHistory corrected(UUID userId, UUID projectId) {
        owned(userId, projectId);
        ProjectHistorySnapshot snapshot = snapshotRepository.findByProjectId(projectId).orElse(null);
        return correctionService.resolve(projectId, snapshot);
    }

    private HistoryOverviewContent correctedOverview(
        HistoryOverviewContent automatic,
        ProjectHistoryCorrectionService.CorrectedHistory corrected
    ) {
        List<HistoryChapterSummary> summaries = representativeChapters(corrected).stream()
            .map(chapter -> new HistoryChapterSummary(
                 chapter.id(), chapter.title(), chapter.summary(), chapter.from(), chapter.to(), chapter.storyCount(),
                 chapter.rawEventCount(), chapter.authority()
            )).toList();
        List<ChangeStory> activeStories = corrected.stories().stream()
            .filter(story -> !"MERGED".equals(story.displayStatus()))
            .toList();
        List<ChangeStory> visibleStories = activeStories.stream()
            .filter(story -> !story.hiddenByDefault())
            .sorted(currentStoryOrder())
            .toList();
        List<String> recent = visibleStories.stream().limit(5)
            .map(story -> outbound(story.humanTitle()) + "（" + date(story.occurredTo()) + "）")
            .toList();
        String confirmed = confirmedState(
            automatic, activeStories, visibleStories, currentPrimaryStories(corrected, visibleStories)
        );
        return new HistoryOverviewContent(
            automatic.earliestConfirmedState(), confirmed, summaries, recent,
            automatic.conflicts(), automatic.unknowns()
        );
    }

    private List<HistoryChapter> representativeChapters(
        ProjectHistoryCorrectionService.CorrectedHistory corrected
    ) {
        List<HistoryChapter> visible = corrected.chapters().stream()
            .filter(chapter -> !chapter.hiddenByDefault())
            .sorted(Comparator.comparing(HistoryChapter::from, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(HistoryChapter::to, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(HistoryChapter::id))
            .toList();
        if (visible.size() <= OVERVIEW_CHAPTER_LIMIT) return visible;

        Map<String, ChangeStory> stories = corrected.stories().stream()
            .collect(LinkedHashMap::new, (map, story) -> map.put(story.id(), story), Map::putAll);
        LinkedHashMap<String, HistoryChapter> selected = new LinkedHashMap<>();
        addRepresentative(selected, visible.get(0));
        addRepresentative(selected, visible.get(visible.size() - 1));
        addLatestMatching(selected, visible, chapter -> chapter.pinned() || chapter.storyRefs().stream()
            .map(stories::get).filter(java.util.Objects::nonNull).anyMatch(ChangeStory::pinned));
        addLatestMatching(selected, visible, chapter -> chapter.userDeclared()
            || ProjectHistoryCorrectionService.USER_DECLARED_PRESENTATION.equals(chapter.presentationAuthority()));
        addLatestMatching(selected, visible, chapter -> chapter.storyRefs().stream().map(stories::get)
            .filter(java.util.Objects::nonNull).anyMatch(story -> !story.conflicts().isEmpty()
                || !story.correctionConflicts().isEmpty()));
        addLatestMatching(selected, visible, chapter -> chapter.storyRefs().stream().map(stories::get)
            .filter(java.util.Objects::nonNull).anyMatch(ProjectHistoryReadService::hasImportantUnknown));

        int spanSlots = OVERVIEW_CHAPTER_LIMIT - selected.size();
        for (int slot = 1; slot <= spanSlots; slot++) {
            int target = (int) Math.round((double) slot * (visible.size() - 1) / (spanSlots + 1));
            addNearestUnselected(selected, visible, target);
        }
        for (HistoryChapter chapter : visible) {
            if (selected.size() >= OVERVIEW_CHAPTER_LIMIT) break;
            addRepresentative(selected, chapter);
        }
        return selected.values().stream()
            .sorted(Comparator.comparing(HistoryChapter::from, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(HistoryChapter::to, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(HistoryChapter::id))
            .limit(OVERVIEW_CHAPTER_LIMIT).toList();
    }

    private static void addLatestMatching(
        Map<String, HistoryChapter> selected,
        List<HistoryChapter> chapters,
        java.util.function.Predicate<HistoryChapter> predicate
    ) {
        if (selected.size() >= OVERVIEW_CHAPTER_LIMIT) return;
        for (int index = chapters.size() - 1; index >= 0; index--) {
            HistoryChapter chapter = chapters.get(index);
            if (predicate.test(chapter)) {
                addRepresentative(selected, chapter);
                return;
            }
        }
    }

    private static void addNearestUnselected(
        Map<String, HistoryChapter> selected,
        List<HistoryChapter> chapters,
        int target
    ) {
        for (int distance = 0; distance < chapters.size(); distance++) {
            int before = target - distance;
            if (before >= 0 && !selected.containsKey(chapters.get(before).id())) {
                addRepresentative(selected, chapters.get(before));
                return;
            }
            int after = target + distance;
            if (after < chapters.size() && !selected.containsKey(chapters.get(after).id())) {
                addRepresentative(selected, chapters.get(after));
                return;
            }
        }
    }

    private static void addRepresentative(Map<String, HistoryChapter> selected, HistoryChapter chapter) {
        if (chapter != null && selected.size() < OVERVIEW_CHAPTER_LIMIT) selected.putIfAbsent(chapter.id(), chapter);
    }

    private static boolean hasImportantUnknown(ChangeStory story) {
        return story.unknowns().stream().anyMatch(value -> {
            String text = value == null ? "" : value.trim();
            return !text.isBlank()
                && !text.contains("未发现可独立验证的变更原因")
                && !text.matches(".*原因保持\\s*UNKNOWN[。.]?.*");
        });
    }

    private void owned(UUID userId, UUID projectId) {
        projectRepository.findByIdAndUserId(projectId, userId)
            .orElseThrow(() -> new AppException("PROJECT_NOT_FOUND", "项目不存在", HttpStatus.NOT_FOUND));
    }

    private <T> List<T> snapshotList(UUID userId, UUID projectId, String field, TypeReference<List<T>> type) {
        owned(userId, projectId);
        ProjectHistorySnapshot snapshot = snapshotRepository.findByProjectId(projectId).orElse(null);
        if (snapshot == null) return List.of();
        String json = switch (field) {
            case "chapters" -> snapshot.getChaptersJson();
            case "stories" -> snapshot.getStoriesJson();
            case "threads" -> snapshot.getThreadsJson();
            default -> "[]";
        };
        try {
            return objectMapper.readValue(safeJson(json, "[]"), type);
        } catch (JsonProcessingException exception) {
            throw new AppException("PROJECT_HISTORY_SNAPSHOT_INVALID", "项目历程快照无法读取，请重新刷新", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private HistoryEventResponse toEvent(ProjectHistoryEvent event) {
        return new HistoryEventResponse(
            event.getId(), event.getProjectId(), event.getStableEventKey(), event.getSourceType().name(),
            outbound(event.getSourceIdentity()), outbound(event.getSourceRevision()), outbound(event.getProjectRevision()), event.getOccurredAt(),
            event.getEffectiveAt(), outbound(event.getActorLabel()), event.getScope().name(), event.getCategory().name(),
            event.getTransition().name(), eventUserSummary(event), outbound(event.getSafeSourceLabel()), strings(event.getAffectedPathsJson()),
            strings(event.getSubjectKeysJson()), strings(event.getEvidenceRefsJson()), strings(event.getRelationRefsJson()),
            event.getAuthority().name(), event.getEpistemicStatus().name(), map(event.getCoverageJson()),
            strings(event.getLimitationsJson()), ProjectHistorySourceCollector.safeDeepLink(event.getRawSourceDeepLink()),
            event.getRewriteState().name(), event.getUpdatedAt()
        );
    }

    private String eventUserSummary(ProjectHistoryEvent event) {
        if (event.getSourceType() == SourceType.GIT
            && Set.of(Category.COMMIT, Category.MERGE).contains(event.getCategory())) {
            return languageService.commitSummary(
                outbound(event.getSafeSourceLabel()), event.getTransition(), strings(event.getAffectedPathsJson())
            );
        }
        return languageService.fallback(
            event.getTransition(), outbound(event.getSafeSourceLabel()), strings(event.getAffectedPathsJson()),
            List.of(outbound(event.getSafeSourceLabel())), List.of(event.getTransition().name())
        ).title();
    }

    private List<String> strings(String json) {
        try {
            return objectMapper.readValue(safeJson(json, "[]"), new TypeReference<List<String>>() {});
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    private Map<String, Object> map(String json) {
        try {
            return objectMapper.readValue(safeJson(json, "{}"), new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException exception) {
            return Map.of();
        }
    }

    private <T> T value(String json, Class<T> type, T fallback) {
        try {
            return objectMapper.readValue(safeJson(json, "{}"), type);
        } catch (JsonProcessingException exception) {
            return fallback;
        }
    }

    private static HistoryCoverage emptyCoverage() {
        return new HistoryCoverage(false, "NOT_INITIALIZED", 0, 0, 0, 0, Map.of(), List.of(), List.of());
    }

    private static <T> Slice<T> slice(List<T> values, int page, int size) {
        int boundedPage = Math.max(0, page);
        int boundedSize = Math.max(1, Math.min(MAX_PAGE_SIZE, size));
        long requestedStart = (long) boundedPage * boundedSize;
        int start = (int) Math.min(values.size(), requestedStart);
        int end = Math.min(values.size(), start + boundedSize);
        int totalPages = values.isEmpty() ? 0 : (int) (((long) values.size() + boundedSize - 1) / boundedSize);
        return new Slice<>(values.subList(start, end), boundedPage, boundedSize, values.size(), totalPages);
    }

    private static void validateTimeRange(Instant from, Instant to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new AppException("INVALID_TIME_RANGE", "起始时间不能晚于结束时间", HttpStatus.BAD_REQUEST);
        }
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value, String errorCode) {
        if (value == null || value.isBlank()) return null;
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new AppException(errorCode, "无效的项目历程筛选值", HttpStatus.BAD_REQUEST);
        }
    }

    private static List<String> names(Enum<?>[] values) {
        return java.util.Arrays.stream(values).map(Enum::name).toList();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "-");
    }

    private static String date(Instant value) {
        return value == null ? "时间未知" : LocalDate.ofInstant(value, ZoneOffset.UTC).toString();
    }

    private String safeCoverage(String json) {
        String safe = redactor.redactOutboundText(json == null || json.isBlank() ? "{}" : json);
        return safe.length() <= 500 ? safe : safe.substring(0, 499) + "…";
    }

    private String safeJson(String value, String fallback) {
        String safe = value == null || value.isBlank() ? fallback : value;
        return redactor.redactOutboundText(safe);
    }

    private String outbound(String value) {
        return redactor.redactOutboundText(value == null ? "" : value).trim();
    }

    private static UUID uuid(String value) { try { return UUID.fromString(value); } catch (RuntimeException exception) { return null; } }

    private record Slice<T>(List<T> items, int page, int size, long total, int totalPages) {
    }
}
