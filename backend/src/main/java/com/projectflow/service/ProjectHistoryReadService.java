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
    private final ProjectRepository projectRepository;
    private final ProjectHistorySnapshotRepository snapshotRepository;
    private final ProjectHistoryEventRepository eventRepository;
    private final ProjectHistoryCorrectionService correctionService;
    private final ProjectEvidenceTraceService evidenceTraceService;
    private final SensitiveContentRedactor redactor;
    private final ObjectMapper objectMapper;

    public ProjectHistoryReadService(
        ProjectRepository projectRepository,
        ProjectHistorySnapshotRepository snapshotRepository,
        ProjectHistoryEventRepository eventRepository,
        ProjectHistoryCorrectionService correctionService,
        ProjectEvidenceTraceService evidenceTraceService,
        SensitiveContentRedactor redactor,
        ObjectMapper objectMapper
    ) {
        this.projectRepository = projectRepository;
        this.snapshotRepository = snapshotRepository;
        this.eventRepository = eventRepository;
        this.correctionService = correctionService;
        this.evidenceTraceService = evidenceTraceService;
        this.redactor = redactor;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public HistoryOverviewResponse overview(UUID userId, UUID projectId) {
        owned(userId, projectId);
        ProjectHistorySnapshot snapshot = snapshotRepository.findByProjectId(projectId).orElse(null);
        if (snapshot == null) {
            return new HistoryOverviewResponse(
                projectId, "NOT_INITIALIZED", "", 0, null, null,
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
        return new HistoryOverviewResponse(
            projectId, snapshot.getStatus().name(), snapshot.getProjectRevision(), snapshot.getSourceEventCount(),
            snapshot.getEarliestEventAt(), snapshot.getLatestEventAt(), snapshot.getStrategyVersion(), snapshot.getPromptVersion(),
            displayOverview,
            value(snapshot.getCoverageJson(), HistoryCoverage.class, emptyCoverage()),
            displayDiagnostics, snapshot.getAnalysisJobId(), snapshot.getGeneratedAt(),
                snapshot.getLatestSuccessfulAt(), snapshot.getUpdatedAt(), outbound(snapshot.getErrorCode()), outbound(snapshot.getErrorSummary())
        );
    }

    @Transactional(readOnly = true)
    public HistoryChapterPageResponse chapters(UUID userId, UUID projectId, int page, int size) {
        List<HistoryChapter> values = corrected(userId, projectId).chapters();
        Slice<HistoryChapter> slice = slice(values, page, size);
        return new HistoryChapterPageResponse(projectId, slice.items(), slice.page(), slice.size(), slice.total(), slice.totalPages());
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
            projectId, chapter, chapter.storyRefs().stream().map(stories::get).filter(java.util.Objects::nonNull)
                .filter(story -> !story.hiddenByDefault()).toList()
        );
    }

    @Transactional(readOnly = true)
    public HistoryStoryPageResponse stories(
        UUID userId, UUID projectId, String subject, boolean attentionOnly, Instant from, Instant to, int page, int size
    ) {
        owned(userId, projectId);
        validateTimeRange(from, to);
        List<ChangeStory> values = corrected(userId, projectId).stories()
            .stream().filter(story -> !story.hiddenByDefault())
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
        return new HistoryStoryPageResponse(projectId, slice.items(), slice.page(), slice.size(), slice.total(), slice.totalPages());
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
        return new HistoryStoryDetailResponse(projectId, story, orderedEvents, threads);
    }

    @Transactional(readOnly = true)
    public EvolutionThreadPageResponse threads(UUID userId, UUID projectId, String subject, int page, int size) {
        List<EvolutionThread> values = corrected(userId, projectId).threads()
            .stream().filter(thread -> subject == null || subject.isBlank()
                || thread.subjectKey().contains(normalize(subject))
                || thread.subjectLabel().toLowerCase(Locale.ROOT).contains(subject.toLowerCase(Locale.ROOT)))
            .toList();
        Slice<EvolutionThread> slice = slice(values, page, size);
        return new EvolutionThreadPageResponse(projectId, slice.items(), slice.page(), slice.size(), slice.total(), slice.totalPages());
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
            projectId, thread, thread.storyRefs().stream().map(stories::get).filter(java.util.Objects::nonNull).toList()
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
        List<String> recent = corrected.stories().stream()
            .filter(story -> !story.hiddenByDefault() && !"MERGED".equals(story.displayStatus()))
            .sorted(Comparator.comparing(ChangeStory::pinned).reversed()
                .thenComparing(ChangeStory::occurredTo, Comparator.nullsLast(Comparator.reverseOrder())))
            .limit(5).map(story -> story.humanTitle() + "（" + date(story.occurredTo()) + "）").toList();
        return new HistoryOverviewContent(
            automatic.earliestConfirmedState(), automatic.currentState(), summaries, recent,
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
            event.getTransition().name(), outbound(event.getSafeSourceLabel()), strings(event.getAffectedPathsJson()),
            strings(event.getSubjectKeysJson()), strings(event.getEvidenceRefsJson()), strings(event.getRelationRefsJson()),
            event.getAuthority().name(), event.getEpistemicStatus().name(), map(event.getCoverageJson()),
            strings(event.getLimitationsJson()), ProjectHistorySourceCollector.safeDeepLink(event.getRawSourceDeepLink()),
            event.getRewriteState().name(), event.getUpdatedAt()
        );
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
