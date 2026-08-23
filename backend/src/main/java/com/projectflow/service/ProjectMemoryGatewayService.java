package com.projectflow.service;

import static com.projectflow.dto.ProjectMemoryGatewayDtos.*;

import java.text.Normalizer;
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
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projectflow.dto.ProjectFactDtos.FactMemoryOverviewResponse;
import com.projectflow.dto.ProjectFactDtos.ProjectFactHistoryStateResponse;
import com.projectflow.dto.ProjectFactDtos.ProjectFactPageResponse;
import com.projectflow.dto.ProjectFactDtos.ProjectFactSummaryResponse;
import com.projectflow.dto.ProjectHistoryDtos.EvolutionThreadPageResponse;
import com.projectflow.dto.ProjectHistoryDtos.HistoryChapterPageResponse;
import com.projectflow.dto.ProjectHistoryDtos.HistoryEvidenceResponse;
import com.projectflow.dto.ProjectHistoryDtos.HistoryEventPageResponse;
import com.projectflow.dto.ProjectHistoryDtos.HistoryOverviewContent;
import com.projectflow.dto.ProjectHistoryDtos.HistoryOverviewResponse;
import com.projectflow.dto.ProjectHistoryDtos.HistoryStoryPageResponse;
import com.projectflow.dto.ProjectHistoryDtos.HistoryCorrectionListResponse;
import com.projectflow.dto.ProjectTimelineDtos.TimelineLifecycleResponse;
import com.projectflow.dto.ProjectTimelineDtos.TimelinePeriodDetailResponse;
import com.projectflow.dto.ProjectTimelineDtos.TimelinePeriodPageResponse;
import com.projectflow.dto.ProjectTimelineDtos.TimelineSummaryResponse;
import com.projectflow.dto.ProjectTimelineDtos.TimelineThemeResponse;
import com.projectflow.entity.ChangeBatch;
import com.projectflow.entity.ProjectCapability;
import com.projectflow.entity.ProjectCapabilityAttentionStatus;
import com.projectflow.entity.ProjectCapabilityEvolution;
import com.projectflow.entity.ProjectCapabilityFact;
import com.projectflow.entity.ProjectCapabilityMapState;
import com.projectflow.entity.ProjectCapabilityMapStatus;
import com.projectflow.entity.ProjectCapabilityMaturity;
import com.projectflow.entity.ProjectCapabilityStatus;
import com.projectflow.entity.ProjectFact;
import com.projectflow.entity.ProjectFactRecordStatus;
import com.projectflow.entity.ProjectSpace;
import com.projectflow.entity.ProjectTimelineSummary;
import com.projectflow.entity.ProjectTimelineTheme;
import com.projectflow.entity.TimelineGranularity;
import com.projectflow.repository.ChangeBatchRepository;
import com.projectflow.repository.ProjectCapabilityAttentionRepository;
import com.projectflow.repository.ProjectCapabilityEvolutionRepository;
import com.projectflow.repository.ProjectCapabilityFactRepository;
import com.projectflow.repository.ProjectCapabilityMapStateRepository;
import com.projectflow.repository.ProjectCapabilityRepository;
import com.projectflow.repository.ProjectFactRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.repository.ProjectTimelineSummaryRepository;
import com.projectflow.repository.ProjectTimelineThemeRepository;
import com.projectflow.support.AppException;

@Service
public class ProjectMemoryGatewayService {
    private static final String FACT_TRUTH = "FACTUAL_SOURCE";
    private static final String TIMELINE_TRUTH = "DERIVED_FROM_FACTS_TEMPORAL";
    private static final String CAPABILITY_TRUTH = "DERIVED_FROM_FACTS_CAPABILITY";

    private final ProjectRepository projectRepository;
    private final ProjectFactRepository factRepository;
    private final ChangeBatchRepository batchRepository;
    private final ProjectTimelineSummaryRepository timelineSummaryRepository;
    private final ProjectTimelineThemeRepository timelineThemeRepository;
    private final ProjectCapabilityRepository capabilityRepository;
    private final ProjectCapabilityEvolutionRepository evolutionRepository;
    private final ProjectCapabilityFactRepository capabilityFactRepository;
    private final ProjectCapabilityMapStateRepository capabilityStateRepository;
    private final ProjectCapabilityAttentionRepository capabilityAttentionRepository;
    private final ProjectFactService factService;
    private final ProjectTimelineService timelineService;
    private final ProjectEvidenceTraceService evidenceTraceService;
    private final ProjectMemorySearchService memorySearchService;
    private final ProjectHistoryReadService historyReadService;

    public ProjectMemoryGatewayService(
        ProjectRepository projectRepository,
        ProjectFactRepository factRepository,
        ChangeBatchRepository batchRepository,
        ProjectTimelineSummaryRepository timelineSummaryRepository,
        ProjectTimelineThemeRepository timelineThemeRepository,
        ProjectCapabilityRepository capabilityRepository,
        ProjectCapabilityEvolutionRepository evolutionRepository,
        ProjectCapabilityFactRepository capabilityFactRepository,
        ProjectCapabilityMapStateRepository capabilityStateRepository,
        ProjectCapabilityAttentionRepository capabilityAttentionRepository,
        ProjectFactService factService,
        ProjectTimelineService timelineService,
        ProjectEvidenceTraceService evidenceTraceService,
        ProjectMemorySearchService memorySearchService,
        ProjectHistoryReadService historyReadService
    ) {
        this.projectRepository = projectRepository;
        this.factRepository = factRepository;
        this.batchRepository = batchRepository;
        this.timelineSummaryRepository = timelineSummaryRepository;
        this.timelineThemeRepository = timelineThemeRepository;
        this.capabilityRepository = capabilityRepository;
        this.evolutionRepository = evolutionRepository;
        this.capabilityFactRepository = capabilityFactRepository;
        this.capabilityStateRepository = capabilityStateRepository;
        this.capabilityAttentionRepository = capabilityAttentionRepository;
        this.factService = factService;
        this.timelineService = timelineService;
        this.evidenceTraceService = evidenceTraceService;
        this.memorySearchService = memorySearchService;
        this.historyReadService = historyReadService;
    }

    @Transactional(readOnly = true)
    public MemoryProjectListResponse listProjects(UUID userId) {
        List<MemoryProjectResponse> items = projectRepository.findByUserIdOrderByUpdatedAtDesc(userId)
            .stream().map(this::project).toList();
        return new MemoryProjectListResponse(items, items.size());
    }

    @Transactional(readOnly = true)
    public ProjectSnapshotResponse snapshot(UUID userId, UUID projectId) {
        ProjectSpace owned = ownedProject(userId, projectId);
        FactMemoryOverviewResponse facts = factService.overview(userId, projectId);
        ProjectFactHistoryStateResponse history = factService.historyState(userId, projectId);
        HistoryOverviewResponse projectHistory = compactHistory(historyReadService.overview(userId, projectId));
        var timelineOverview = timelineService.overview(userId, projectId);
        ProjectCapabilityMapState capabilityState = capabilityStateRepository.findByProjectId(projectId).orElse(null);
        long activeCapabilityCount = capabilityRepository.countByProjectIdAndStatus(projectId, ProjectCapabilityStatus.ACTIVE);
        long capabilityAttentionCount = capabilityAttentionRepository.countByProjectIdAndStatus(
            projectId, ProjectCapabilityAttentionStatus.OPEN
        );
        ChangeBatch latestBatch = batchRepository.findFirstByProjectIdOrderByScanStartedAtDesc(projectId).orElse(null);

        MemoryFactPageResponse recent = recentChanges(userId, projectId, null, null, true, 0, 5, "compact");
        MemoryTimelinePeriodResponse latestPeriod = null;
        MemoryTimelineSummaryResponse lifecycleSummary = null;
        if (facts.totalFactCount() > 0) {
            TimelinePeriodPageResponse months = timelineService.periods(
                userId, projectId, TimelineGranularity.MONTH, null, null, 0, 1
            );
            if (!months.items().isEmpty()) latestPeriod = period(months.items().get(0));
            lifecycleSummary = summary(timelineSummaryRepository.findByProjectIdAndGranularityAndPeriodKey(
                projectId, TimelineGranularity.LIFECYCLE, "ALL"
            ).orElse(null));
        }

        List<ProjectCapability> representative = capabilityRepository
            .findByProjectIdAndStatusOrderByFirstFormedAtAsc(projectId, ProjectCapabilityStatus.ACTIVE)
            .stream().sorted(Comparator.comparingInt(ProjectCapability::getSourceFactCount).reversed()
                .thenComparing(ProjectCapability::getLastEnhancedAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .limit(8).toList();
        boolean stale = stale(capabilityState);
        List<MemoryCapabilityResponse> capabilityItems = representative.stream()
            .map(value -> capability(value, stale, false)).toList();
        List<MemoryEvolutionResponse> evolutions = evolutionPage(projectId, null, 0, 5, false).items();

        List<String> warnings = new ArrayList<>();
        if (history.remainingCommitCount() > 0) warnings.add("Git 历史仍有未覆盖提交");
        switch (projectHistory.status()) {
            case "NOT_INITIALIZED" -> warnings.add("项目历程尚未显式刷新");
            case "RUNNING" -> warnings.add("项目历程正在刷新，当前读取结果可能尚未生成");
            case "STALE" -> warnings.add("项目历程来源已变化，当前返回上次成功快照");
            case "DEGRADED" -> warnings.add("项目历程存在来源缺口、失败保留或模型降级，当前结果仍可追溯");
            case "FAILED" -> warnings.add("项目历程刷新失败且没有可用成功快照");
            default -> { }
        }
        if (facts.attentionFactCount() > 0) warnings.add("部分事实需要关注");
        if (timelineOverview.dirtyPeriodCount() > 0) warnings.add("部分时间段摘要正在刷新或暂不可用，事实统计仍可读取");
        if (stale) warnings.add("能力地图正在刷新或最近刷新失败，当前返回上次成功结果");
        if (capabilityAttentionCount > 0) warnings.add("能力地图存在需要关注的分类或合并问题");
        String recentSummary = recent.items().stream().limit(3).map(MemoryFactResponse::title)
            .collect(Collectors.joining("；"));
        Instant analyzedAt = latestBatch == null ? null
            : firstNonNull(latestBatch.getScanFinishedAt(), latestBatch.getScanStartedAt());
        MemoryHealthResponse health = new MemoryHealthResponse(
            history.status(), projectHistory.status(), timelineOverview.latestSummaryStatus(),
            capabilityState == null ? ProjectCapabilityMapStatus.NOT_INITIALIZED.name() : capabilityState.getStatus().name(),
            stale, facts.latestOccurredAt(), analyzedAt, Instant.now(), List.copyOf(warnings)
        );
        return new ProjectSnapshotResponse(
            project(owned), latestBatch == null ? "" : latestBatch.getBranchName(), "", projectHistory,
            facts.totalFactCount(), facts.recordedFactCount(), facts.attentionFactCount(),
            facts.coveredCommitCount(), facts.totalCommitCount(), facts.earliestOccurredAt(), facts.latestOccurredAt(),
            recentSummary, recent, latestPeriod, lifecycleSummary, activeCapabilityCount,
            capabilityItems, evolutions, capabilityAttentionCount, health
        );
    }

    @Transactional(readOnly = true)
    public MemoryFactPageResponse recentChanges(
        UUID userId, UUID projectId, Instant from, Instant to, boolean includeAttention,
        int page, int size, String detailLevel
    ) {
        ownedProject(userId, projectId);
        if (from != null && to != null && from.isAfter(to)) {
            throw new AppException("INVALID_TIME_RANGE", "起始时间不能晚于结束时间", HttpStatus.BAD_REQUEST);
        }
        int bounded = clamp(size, detailed(detailLevel) ? 100 : 20, 10);
        ProjectFactPageResponse facts = factService.listFacts(
            userId, projectId, from, to, null,
            includeAttention ? null : ProjectFactRecordStatus.RECORDED,
            Math.max(0, page), bounded
        );
        return factPage(projectId, facts, detailed(detailLevel));
    }

    @Transactional(readOnly = true)
    public MemorySearchResponse search(
        UUID userId, UUID projectId, String query, Instant from, Instant to,
        String entityTypes, int page, int size, String detailLevel
    ) {
        return memorySearchService.search(userId, projectId, query, from, to, entityTypes, page, size, detailLevel);
    }

    @Transactional(readOnly = true)
    public MemoryTimelineQueryResponse timeline(
        UUID userId, UUID projectId, String granularityValue, String periodKey,
        String from, String to, int page, int size, String detailLevel
    ) {
        ownedProject(userId, projectId);
        TimelineGranularity granularity = granularity(granularityValue);
        int bounded = clamp(size, detailed(detailLevel) ? 100 : 20, 10);
        if (granularity == TimelineGranularity.LIFECYCLE) {
            TimelineLifecycleResponse source = timelineService.lifecycle(userId, projectId);
            return new MemoryTimelineQueryResponse(
                projectId, source.timelineZone(), granularity.name(), "LIFECYCLE",
                null, null, lifecycle(source)
            );
        }
        if (periodKey == null || periodKey.isBlank()) {
            TimelinePeriodPageResponse source = timelineService.periods(
                userId, projectId, granularity, from, to, Math.max(0, page), bounded
            );
            List<MemoryTimelinePeriodResponse> items = source.items().stream().map(this::period).toList();
            return new MemoryTimelineQueryResponse(
                projectId, source.timelineZone(), granularity.name(), "PERIOD_LIST",
                new MemoryTimelinePeriodPageResponse(
                    items, source.page(), source.size(), source.totalElements(), source.totalPages(),
                    source.page() + 1 < source.totalPages()
                ), null, null
            );
        }
        TimelinePeriodDetailResponse source = timelineService.period(
            userId, projectId, granularity, periodKey, Math.max(0, page), bounded
        );
        return new MemoryTimelineQueryResponse(
            projectId, source.timelineZone(), granularity.name(), "PERIOD_DETAIL", null, detail(source), null
        );
    }

    @Transactional(readOnly = true)
    public MemoryCapabilityPageResponse capabilities(
        UUID userId, UUID projectId, boolean activeOnly, String maturity,
        String search, int page, int size, String detailLevel
    ) {
        ownedProject(userId, projectId);
        ProjectCapabilityMaturity maturityFilter = maturity(maturity);
        String query = normalize(search);
        List<ProjectCapability> filtered = capabilityRepository.findByProjectIdOrderByCreatedAtAsc(projectId).stream()
            .filter(value -> !activeOnly || value.getStatus() == ProjectCapabilityStatus.ACTIVE)
            .filter(value -> maturityFilter == null || value.getMaturityLevel() == maturityFilter)
            .filter(value -> query.isBlank() || capabilitySearchText(value).contains(query))
            .sorted(Comparator.comparing(ProjectCapability::getLastEnhancedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(ProjectCapability::getCanonicalName).thenComparing(ProjectCapability::getId))
            .toList();
        int bounded = clamp(size, detailed(detailLevel) ? 100 : 30, 10);
        int safePage = Math.max(0, page);
        int start = Math.min(filtered.size(), safePage * bounded);
        int end = Math.min(filtered.size(), start + bounded);
        int totalPages = filtered.isEmpty() ? 0 : (filtered.size() + bounded - 1) / bounded;
        boolean stale = stale(capabilityStateRepository.findByProjectId(projectId).orElse(null));
        List<MemoryCapabilityResponse> items = filtered.subList(start, end).stream()
            .map(value -> capability(value, stale, detailed(detailLevel))).toList();
        return new MemoryCapabilityPageResponse(
            items, safePage, bounded, filtered.size(), totalPages, safePage + 1 < totalPages
        );
    }

    @Transactional(readOnly = true)
    public MemoryEvolutionPageResponse capabilityEvolution(
        UUID userId, UUID projectId, UUID capabilityId, int page, int size, String detailLevel
    ) {
        ownedProject(userId, projectId);
        ProjectCapability capability = capabilityRepository.findByIdAndProjectId(capabilityId, projectId)
            .orElseThrow(() -> new AppException(
                "PROJECT_CAPABILITY_NOT_FOUND", "项目能力不存在", HttpStatus.NOT_FOUND
            ));
        return evolutionPage(projectId, capability, page, clamp(size, detailed(detailLevel) ? 100 : 30, 10), detailed(detailLevel));
    }

    @Transactional(readOnly = true)
    public MemoryFactTraceResponse traceFact(
        UUID userId, UUID projectId, UUID factId, String detailLevel
    ) {
        return evidenceTraceService.trace(userId, projectId, factId, detailLevel);
    }

    @Transactional(readOnly = true)
    public HistoryOverviewResponse historyOverview(UUID userId, UUID projectId) {
        return historyReadService.overview(userId, projectId);
    }

    @Transactional(readOnly = true)
    public HistoryCorrectionListResponse historyCorrections(UUID userId, UUID projectId) {
        return historyReadService.corrections(userId, projectId);
    }

    @Transactional(readOnly = true)
    public HistoryCorrectionListResponse historyCorrections(UUID userId, UUID projectId, int page, int size) {
        return historyReadService.corrections(userId, projectId, page, size);
    }

    @Transactional(readOnly = true)
    public HistoryChapterPageResponse historyChapters(UUID userId, UUID projectId, int page, int size) {
        return historyReadService.chapters(userId, projectId, page, size);
    }

    @Transactional(readOnly = true)
    public HistoryStoryPageResponse historyStories(
        UUID userId, UUID projectId, String subject, boolean attentionOnly,
        Instant from, Instant to, int page, int size
    ) {
        return historyStories(userId, projectId, subject, attentionOnly, false, from, to, page, size);
    }

    @Transactional(readOnly = true)
    public HistoryStoryPageResponse historyStories(
        UUID userId, UUID projectId, String subject, boolean attentionOnly, boolean includeHidden,
        Instant from, Instant to, int page, int size
    ) {
        return historyReadService.stories(
            userId, projectId, subject, attentionOnly, includeHidden, from, to, page, size
        );
    }

    @Transactional(readOnly = true)
    public EvolutionThreadPageResponse historyThreads(
        UUID userId, UUID projectId, String subject, int page, int size
    ) {
        return historyReadService.threads(userId, projectId, subject, page, size);
    }

    @Transactional(readOnly = true)
    public HistoryEventPageResponse historyEvents(
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
        return historyReadService.events(
            userId, projectId, sourceType, category, transition, authority, epistemicStatus, rewriteState,
            subject, attentionOnly, from, to, page, size
        );
    }

    @Transactional(readOnly = true)
    public HistoryEvidenceResponse historyEvidence(UUID userId, UUID projectId, UUID eventId) {
        return historyReadService.evidence(userId, projectId, eventId);
    }

    @Transactional(readOnly = true)
    public MemoryBriefResponse brief(UUID userId, UUID projectId, int sizeBudget) {
        ProjectSnapshotResponse snapshot = snapshot(userId, projectId);
        int budget = Math.max(2_000, Math.min(12_000, sizeBudget <= 0 ? 6_000 : sizeBudget));
        List<MemoryTimelinePeriodResponse> periods = List.of();
        if (snapshot.factCount() > 0) {
            periods = timeline(userId, projectId, "MONTH", "", null, null, 0, 5, "compact")
                .periods().items();
        }
        StringBuilder text = new StringBuilder();
        text.append("项目：").append(snapshot.project().name()).append('\n');
        if (!snapshot.project().summary().isBlank()) text.append("定位：").append(snapshot.project().summary()).append('\n');
        HistoryOverviewResponse projectHistory = snapshot.projectHistory();
        if (projectHistory != null && projectHistory.overview() != null) {
            HistoryOverviewContent overview = projectHistory.overview();
            text.append("项目历程：").append(projectHistory.status()).append('，')
                .append(projectHistory.sourceEventCount()).append(" 条来源事件").append('\n');
            if (!overview.earliestConfirmedState().isBlank()) {
                text.append("最早可确认状态：").append(overview.earliestConfirmedState()).append('\n');
            }
            if (!overview.currentState().isBlank()) {
                text.append("当前状态：").append(overview.currentState()).append('\n');
            }
            if (!overview.recentChanges().isEmpty()) {
                text.append("近期发生：").append(String.join("；", overview.recentChanges())).append('\n');
            }
            if (!overview.chapters().isEmpty()) {
                text.append("时间篇章：").append(overview.chapters().stream()
                    .map(item -> item.title() + "（" + item.storyCount() + " 个故事）")
                    .collect(Collectors.joining("；"))).append('\n');
            }
        }
        text.append("事实：").append(snapshot.factCount()).append(" 条，真实变化范围 ")
            .append(snapshot.earliestFactAt()).append(" 至 ").append(snapshot.latestFactAt()).append('\n');
        if (snapshot.lifecycleSummary() != null && !snapshot.lifecycleSummary().summary().isBlank()) {
            text.append("生命周期：").append(snapshot.lifecycleSummary().summary()).append('\n');
        }
        if (!snapshot.recentChanges().items().isEmpty()) {
            text.append("最近变化：").append(snapshot.recentChanges().items().stream()
                .map(item -> item.title() + "（" + item.time().eventAt() + "）")
                .collect(Collectors.joining("；"))).append('\n');
        }
        if (!snapshot.representativeCapabilities().isEmpty()) {
            text.append("主要能力：").append(snapshot.representativeCapabilities().stream()
                .map(item -> item.canonicalName() + "[" + item.maturity() + "]")
                .collect(Collectors.joining("；"))).append('\n');
        }
        if (!snapshot.recentEvolutions().isEmpty()) {
            text.append("近期能力演进：").append(snapshot.recentEvolutions().stream()
                .map(item -> item.capabilityName() + "：" + item.title())
                .collect(Collectors.joining("；"))).append('\n');
        }
        if (!periods.isEmpty()) {
            text.append("关键时间段：").append(periods.stream().map(MemoryTimelinePeriodResponse::periodKey)
                .collect(Collectors.joining("、"))).append('\n');
        }
        if (!snapshot.health().warnings().isEmpty()) {
            text.append("需要关注：").append(String.join("；", snapshot.health().warnings())).append('\n');
        }
        String context = text.toString();
        boolean truncated = context.length() > budget;
        if (truncated) context = context.substring(0, Math.max(0, budget - 1)) + "…";
        return new MemoryBriefResponse(
            projectId, context, budget, context.length(), truncated, snapshot.health().warnings(), Instant.now()
        );
    }

    private HistoryOverviewResponse compactHistory(HistoryOverviewResponse source) {
        if (source == null || source.overview() == null) return source;
        HistoryOverviewContent overview = source.overview();
        HistoryOverviewContent compact = new HistoryOverviewContent(
            text(overview.earliestConfirmedState(), false), text(overview.currentState(), false),
            limit(overview.chapters(), 8), limit(overview.recentChanges(), 5),
            limit(overview.conflicts(), 5), limit(overview.unknowns(), 10)
        );
        return new HistoryOverviewResponse(
            source.projectId(), source.presentationRevision(), source.status(), source.projectRevision(), source.sourceEventCount(),
            source.earliestEventAt(), source.latestEventAt(), source.strategyVersion(), source.promptVersion(),
            compact, source.coverage(), Map.of(), source.analysisJobId(), source.generatedAt(),
            source.latestSuccessfulAt(), source.updatedAt(), source.errorCode(), source.errorSummary()
        );
    }

    private MemoryProjectResponse project(ProjectSpace value) {
        return new MemoryProjectResponse(
            value.getId(), value.getName(), safe(value.getDescription()), value.getStatus().name(),
            value.getTechStack() == null ? List.of() : List.copyOf(value.getTechStack()), value.getUpdatedAt()
        );
    }

    private MemoryFactPageResponse factPage(UUID projectId, ProjectFactPageResponse source, boolean detailed) {
        List<UUID> factIds = source.items().stream().map(ProjectFactSummaryResponse::id).toList();
        Map<UUID, List<UUID>> related = relatedCapabilities(projectId, factIds);
        Set<UUID> batchIds = source.items().stream().map(ProjectFactSummaryResponse::batchId)
            .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, ChangeBatch> batches = batchIds.isEmpty() ? Map.of() : batchRepository.findAllById(batchIds).stream()
            .collect(Collectors.toMap(ChangeBatch::getId, Function.identity()));
        List<MemoryFactResponse> items = source.items().stream().map(fact -> {
            ChangeBatch batch = fact.batchId() == null ? null : batches.get(fact.batchId());
            return new MemoryFactResponse(
                fact.id(), text(fact.title(), detailed), text(fact.summary(), detailed),
                new MemoryTimeResponse(
                    fact.occurredFrom(), fact.occurredTo(), eventAt(fact.occurredFrom(), fact.occurredTo()),
                    fact.createdAt(), batch == null ? null : firstNonNull(batch.getScanFinishedAt(), batch.getScanStartedAt()), null
                ), fact.batchId(), fact.commitCount(), fact.affectedFileCount(), fact.agentResultCount(), fact.evidenceCount(),
                fact.recordStatus(), fact.qualityStatus(), text(fact.attentionReason(), detailed),
                related.getOrDefault(fact.id(), List.of()), FACT_TRUTH,
                "/api/projects/" + projectId + "/project-memory/facts/" + fact.id() + "/trace",
                fact.epistemicStatus(), fact.currentness(), fact.revision(), fact.validationStatus(),
                limit(fact.limitations(), detailed ? 20 : 5)
            );
        }).toList();
        return new MemoryFactPageResponse(
            items, source.page(), source.size(), source.totalElements(), source.totalPages(),
            source.page() + 1 < source.totalPages()
        );
    }

    private Map<UUID, List<UUID>> relatedCapabilities(UUID projectId, List<UUID> factIds) {
        if (factIds == null || factIds.isEmpty()) return Map.of();
        Map<UUID, LinkedHashSet<UUID>> grouped = new LinkedHashMap<>();
        for (ProjectCapabilityFact link : capabilityFactRepository.findByProjectIdAndFactIdIn(projectId, factIds)) {
            grouped.computeIfAbsent(link.getFactId(), ignored -> new LinkedHashSet<>()).add(link.getCapabilityId());
        }
        return grouped.entrySet().stream().collect(Collectors.toMap(
            Map.Entry::getKey, entry -> List.copyOf(entry.getValue())
        ));
    }

    private MemoryCapabilityResponse capability(ProjectCapability item, boolean stale, boolean detailed) {
        return new MemoryCapabilityResponse(
            item.getId(), item.getCanonicalName(), item.getAliases(), text(item.getCurrentSummary(), detailed),
            text(item.getProblemSolved(), detailed), text(item.getLongTermValue(), detailed), item.getProductAreas(),
            item.getStatus().name(), item.getMaturityLevel().name(), text(item.getMaturityReason(), detailed),
            item.getFirstFormedAt(), item.getLastEnhancedAt(), item.getSourceFactCount(), item.getSourceBatchCount(),
            item.getDistinctCommitCount(), item.getEvidenceCount(), item.getEvolutionCount(), item.getAttentionFactCount(),
            item.getCurrentVersion(), item.getMergedIntoCapabilityId(), stale, item.getUpdatedAt(),
            detailed ? text(item.getReadmeExpression(), true) : "",
            detailed ? text(item.getResumeExpression(), true) : "",
            detailed ? text(item.getInterviewExpression(), true) : "", CAPABILITY_TRUTH
        );
    }

    private MemoryEvolutionPageResponse evolutionPage(
        UUID projectId, ProjectCapability capability, int page, int size, boolean detailed
    ) {
        int safePage = Math.max(0, page);
        Page<ProjectCapabilityEvolution> source = capability == null
            ? evolutionRepository.findByProjectIdOrderByOccurredAtDescCreatedAtDesc(projectId, PageRequest.of(safePage, size))
            : evolutionRepository.findByProjectIdAndCapabilityIdOrderByOccurredAtAscCreatedAtAsc(
                projectId, capability.getId(), PageRequest.of(safePage, size)
            );
        List<MemoryEvolutionResponse> items = evolutions(projectId, source.getContent(), detailed);
        return new MemoryEvolutionPageResponse(
            items, source.getNumber(), source.getSize(), source.getTotalElements(), source.getTotalPages(),
            source.getNumber() + 1 < source.getTotalPages()
        );
    }

    private List<MemoryEvolutionResponse> evolutions(
        UUID projectId, List<ProjectCapabilityEvolution> source, boolean detailed
    ) {
        if (source.isEmpty()) return List.of();
        Set<UUID> capabilityIds = source.stream().map(ProjectCapabilityEvolution::getCapabilityId).collect(Collectors.toSet());
        Map<UUID, String> names = capabilityRepository.findAllById(capabilityIds).stream()
            .collect(Collectors.toMap(ProjectCapability::getId, ProjectCapability::getCanonicalName));
        List<UUID> evolutionIds = source.stream().map(ProjectCapabilityEvolution::getId).toList();
        Map<UUID, List<UUID>> facts = capabilityFactRepository
            .findByProjectIdAndSourceEvolutionIdIn(projectId, evolutionIds).stream()
            .collect(Collectors.groupingBy(
                ProjectCapabilityFact::getSourceEvolutionId, LinkedHashMap::new,
                Collectors.mapping(ProjectCapabilityFact::getFactId,
                    Collectors.collectingAndThen(Collectors.toCollection(LinkedHashSet::new), List::copyOf))
            ));
        int factLimit = detailed ? 100 : 20;
        return source.stream().map(item -> new MemoryEvolutionResponse(
            item.getId(), item.getCapabilityId(), names.getOrDefault(item.getCapabilityId(), ""),
            item.getEvolutionType().name(), item.getVersionBefore(), item.getVersionAfter(),
            text(item.getTitle(), detailed), text(item.getSummary(), detailed), item.getOccurredAt(),
            item.getSourceFactCount(), item.getSourceBatchCount(), item.getSourceTimelinePeriods(),
            limit(facts.getOrDefault(item.getId(), List.of()), factLimit), item.getMergedFromCapabilityId(),
            CAPABILITY_TRUTH
        )).toList();
    }

    private MemoryTimelineDetailResponse detail(TimelinePeriodDetailResponse source) {
        return new MemoryTimelineDetailResponse(
            source.periodKey(), source.periodStart(), source.periodEnd(), source.stats(), summary(source.currentSummary()),
            source.themes().stream().map(this::theme).toList(), source.sourceFactCount(), source.coveredFactCount(),
            factPage(source.projectId(), source.facts(), false), source.history()
        );
    }

    private MemoryTimelineLifecycleResponse lifecycle(TimelineLifecycleResponse source) {
        return new MemoryTimelineLifecycleResponse(
            source.earliestFactAt(), source.latestFactAt(), source.stats(), summary(source.currentSummary()),
            source.stages().stream().map(this::theme).toList(), source.months().stream().map(this::period).toList(),
            source.sourceFactCount(), source.coveredFactCount(), source.history()
        );
    }

    private MemoryTimelinePeriodResponse period(com.projectflow.dto.ProjectTimelineDtos.TimelinePeriodResponse source) {
        MemoryTimelineSummaryResponse summary = new MemoryTimelineSummaryResponse(
            source.summaryStatus(), text(source.summaryPreview(), false), Math.toIntExact(source.stats().factCount()),
            "READY".equals(source.summaryStatus()) ? Math.toIntExact(source.stats().factCount()) : 0,
            source.summaryStale(), 0, null, summaryNotice(source.summaryStatus(), source.summaryPreview()),
            "INFERRED", "NON_AUTHORITATIVE"
        );
        return new MemoryTimelinePeriodResponse(
            source.periodKey(), source.periodStart(), source.periodEnd(), source.stats(), summary, source.themeCount()
        );
    }

    private MemoryTimelineSummaryResponse summary(TimelineSummaryResponse source) {
        if (source == null) {
            return new MemoryTimelineSummaryResponse(
                "NOT_GENERATED", "", 0, 0, false, 0, null,
                "尚无自动摘要，事实和确定性统计仍可读取", "INFERRED", "NON_AUTHORITATIVE"
            );
        }
        return new MemoryTimelineSummaryResponse(
            source.status(), text(source.summary(), true), source.sourceFactCount(), source.coveredFactCount(),
            source.stale(), source.generationVersion(), source.generatedAt(),
            summaryNotice(source.status(), source.summary()), source.epistemicStatus(), source.authority()
        );
    }

    private MemoryTimelineSummaryResponse summary(ProjectTimelineSummary source) {
        if (source == null) {
            return new MemoryTimelineSummaryResponse(
                "NOT_GENERATED", "", 0, 0, false, 0, null,
                "尚无自动摘要，事实和确定性统计仍可读取", "INFERRED", "NON_AUTHORITATIVE"
            );
        }
        boolean stale = source.hasGeneratedContent() && source.getStatus() != com.projectflow.entity.ProjectTimelineSummaryStatus.READY;
        return new MemoryTimelineSummaryResponse(
            source.getStatus().name(), text(source.getSummary(), true), source.getSourceFactCount(), source.getCoveredFactCount(),
            stale, source.getGenerationVersion(), source.getGeneratedAt(),
            summaryNotice(source.getStatus().name(), source.getSummary()), "INFERRED", "NON_AUTHORITATIVE"
        );
    }

    private String summaryNotice(String status, String content) {
        if (status == null) return "";
        if ("FAILED".equals(status)) return "摘要生成失败，当前返回事实和确定性统计";
        if ("WAITING_FOR_MODEL".equals(status)) return "等待模型配置，当前返回事实和确定性统计";
        if (!"READY".equals(status) && content != null && !content.isBlank()) return "摘要可能较旧，事实和确定性统计为当前数据";
        return "";
    }

    private MemoryTimelineThemeResponse theme(TimelineThemeResponse source) {
        return new MemoryTimelineThemeResponse(source.id(), source.title(), text(source.summary(), false), source.factCount());
    }

    private ProjectSpace ownedProject(UUID userId, UUID projectId) {
        return projectRepository.findByIdAndUserId(projectId, userId)
            .orElseThrow(() -> new AppException("PROJECT_NOT_FOUND", "项目不存在", HttpStatus.NOT_FOUND));
    }

    private TimelineGranularity granularity(String value) {
        try { return TimelineGranularity.valueOf(safe(value).toUpperCase(Locale.ROOT)); }
        catch (RuntimeException exception) {
            throw new AppException("INVALID_TIMELINE_GRANULARITY", "无效的项目历程粒度", HttpStatus.BAD_REQUEST);
        }
    }

    private ProjectCapabilityMaturity maturity(String value) {
        if (value == null || value.isBlank()) return null;
        try { return ProjectCapabilityMaturity.valueOf(value.trim().toUpperCase(Locale.ROOT)); }
        catch (RuntimeException exception) {
            throw new AppException("INVALID_CAPABILITY_MATURITY", "无效的能力成熟阶段", HttpStatus.BAD_REQUEST);
        }
    }

    private boolean stale(ProjectCapabilityMapState state) {
        return state != null && state.getLatestSuccessfulAt() != null && state.getStatus() != ProjectCapabilityMapStatus.READY;
    }

    private String capabilitySearchText(ProjectCapability value) {
        return normalize(String.join(" ", List.of(
            value.getCanonicalName(), String.join(" ", value.getAliases()), value.getCurrentSummary(),
            value.getProblemSolved(), value.getLongTermValue(), String.join(" ", value.getProductAreas())
        )));
    }

    private String text(String value, boolean detailed) {
        String safe = safe(value);
        int max = detailed ? 4_000 : 600;
        return safe.length() <= max ? safe : safe.substring(0, max - 1) + "…";
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFKC).trim().toLowerCase(Locale.ROOT)
            .replaceAll("\\s+", " ");
    }

    private static String safe(String value) { return value == null ? "" : value.trim(); }
    private static boolean detailed(String value) { return "detailed".equalsIgnoreCase(safe(value)); }
    private static int clamp(int value, int maximum, int fallback) {
        return Math.max(1, Math.min(maximum, value <= 0 ? fallback : value));
    }
    private static Instant eventAt(ProjectFact fact) { return eventAt(fact.getOccurredFrom(), fact.getOccurredTo()); }
    private static Instant eventAt(Instant from, Instant to) { return to == null ? from : to; }
    private static Instant firstNonNull(Instant first, Instant second) { return first == null ? second : first; }
    private static <T> List<T> limit(Collection<T> values, int limit) {
        if (values == null || values.isEmpty()) return List.of();
        return values.stream().limit(limit).toList();
    }
}
