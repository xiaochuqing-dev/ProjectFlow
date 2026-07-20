package com.projectflow.service;

import static com.projectflow.dto.ProjectMemoryGatewayDtos.*;

import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
import com.projectflow.entity.ProjectFactAgentResultRef;
import com.projectflow.entity.ProjectFactCommitRef;
import com.projectflow.entity.ProjectFactFileRef;
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
import com.projectflow.repository.ProjectFactAgentResultRefRepository;
import com.projectflow.repository.ProjectFactCommitRefRepository;
import com.projectflow.repository.ProjectFactFileRefRepository;
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
    private static final Pattern SEARCH_TOKEN = Pattern.compile("[\\p{IsHan}]{2,}|[a-z0-9_\\-]{2,}");
    private static final Pattern WINDOWS_ABSOLUTE = Pattern.compile("(?i)^[a-z]:[\\\\/].*");

    private final ProjectRepository projectRepository;
    private final ProjectFactRepository factRepository;
    private final ProjectFactCommitRefRepository commitRepository;
    private final ProjectFactFileRefRepository fileRepository;
    private final ProjectFactAgentResultRefRepository agentRepository;
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

    public ProjectMemoryGatewayService(
        ProjectRepository projectRepository,
        ProjectFactRepository factRepository,
        ProjectFactCommitRefRepository commitRepository,
        ProjectFactFileRefRepository fileRepository,
        ProjectFactAgentResultRefRepository agentRepository,
        ChangeBatchRepository batchRepository,
        ProjectTimelineSummaryRepository timelineSummaryRepository,
        ProjectTimelineThemeRepository timelineThemeRepository,
        ProjectCapabilityRepository capabilityRepository,
        ProjectCapabilityEvolutionRepository evolutionRepository,
        ProjectCapabilityFactRepository capabilityFactRepository,
        ProjectCapabilityMapStateRepository capabilityStateRepository,
        ProjectCapabilityAttentionRepository capabilityAttentionRepository,
        ProjectFactService factService,
        ProjectTimelineService timelineService
    ) {
        this.projectRepository = projectRepository;
        this.factRepository = factRepository;
        this.commitRepository = commitRepository;
        this.fileRepository = fileRepository;
        this.agentRepository = agentRepository;
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
        if (facts.attentionFactCount() > 0) warnings.add("部分事实需要关注");
        if (timelineOverview.dirtyPeriodCount() > 0) warnings.add("部分时间段摘要正在刷新或暂不可用，事实统计仍可读取");
        if (stale) warnings.add("能力地图正在刷新或最近刷新失败，当前返回上次成功结果");
        if (capabilityAttentionCount > 0) warnings.add("能力地图存在需要关注的分类或合并问题");
        String recentSummary = recent.items().stream().limit(3).map(MemoryFactResponse::title)
            .collect(Collectors.joining("；"));
        Instant analyzedAt = latestBatch == null ? null
            : firstNonNull(latestBatch.getScanFinishedAt(), latestBatch.getScanStartedAt());
        MemoryHealthResponse health = new MemoryHealthResponse(
            history.status(), timelineOverview.latestSummaryStatus(),
            capabilityState == null ? ProjectCapabilityMapStatus.NOT_INITIALIZED.name() : capabilityState.getStatus().name(),
            stale, facts.latestOccurredAt(), analyzedAt, Instant.now(), List.copyOf(warnings)
        );
        return new ProjectSnapshotResponse(
            project(owned), latestBatch == null ? "" : latestBatch.getBranchName(), "",
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
        ownedProject(userId, projectId);
        String normalized = normalize(query);
        if (normalized.isBlank()) {
            throw new AppException("MEMORY_QUERY_REQUIRED", "请输入要查询的项目记忆", HttpStatus.BAD_REQUEST);
        }
        if (normalized.length() > 500) {
            throw new AppException("MEMORY_QUERY_TOO_LONG", "查询内容不能超过 500 个字符", HttpStatus.BAD_REQUEST);
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new AppException("INVALID_TIME_RANGE", "起始时间不能晚于结束时间", HttpStatus.BAD_REQUEST);
        }
        Set<SearchType> types = searchTypes(entityTypes);
        List<String> tokens = searchTokens(normalized);
        List<SearchCandidate> candidates = new ArrayList<>();
        if (types.contains(SearchType.FACT)) addFactCandidates(projectId, normalized, tokens, from, to, candidates);
        if (types.contains(SearchType.TIMELINE)) addTimelineCandidates(projectId, normalized, tokens, from, to, candidates);
        if (types.contains(SearchType.CAPABILITY)) addCapabilityCandidates(projectId, normalized, tokens, from, to, candidates);
        if (types.contains(SearchType.EVOLUTION)) addEvolutionCandidates(projectId, normalized, tokens, from, to, candidates);

        Map<String, SearchCandidate> unique = new LinkedHashMap<>();
        candidates.stream().sorted(SearchCandidate.ORDER).forEach(candidate ->
            unique.putIfAbsent(candidate.result().entityType() + ":" + candidate.result().entityId(), candidate)
        );
        List<MemorySearchResultResponse> sorted = unique.values().stream().sorted(SearchCandidate.ORDER)
            .map(SearchCandidate::result).toList();
        int bounded = clamp(size, detailed(detailLevel) ? 50 : 20, 10);
        int safePage = Math.max(0, page);
        int start = Math.min(sorted.size(), safePage * bounded);
        int end = Math.min(sorted.size(), start + bounded);
        int totalPages = sorted.isEmpty() ? 0 : (sorted.size() + bounded - 1) / bounded;
        return new MemorySearchResponse(
            projectId, types.stream().map(Enum::name).toList(), sorted.subList(start, end), safePage,
            bounded, sorted.size(), totalPages, safePage + 1 < totalPages
        );
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
        ownedProject(userId, projectId);
        ProjectFact fact = factRepository.findByIdAndProjectId(factId, projectId)
            .orElseThrow(() -> new AppException("PROJECT_FACT_NOT_FOUND", "项目事实不存在", HttpStatus.NOT_FOUND));
        boolean detailed = detailed(detailLevel);
        int limit = detailed ? 100 : 20;
        ChangeBatch batch = fact.getBatchId() == null ? null : batchRepository.findById(fact.getBatchId()).orElse(null);
        List<String> commits = limit(commitRepository.findByFactId(factId).stream()
            .map(ProjectFactCommitRef::getCommitSha).toList(), limit);
        List<String> files = limit(fileRepository.findByFactId(factId).stream()
            .map(ProjectFactFileRef::getFilePath).map(this::safeReference).filter(value -> !value.isBlank()).toList(), limit);
        List<String> agents = limit(agentRepository.findByFactId(factId).stream()
            .map(ProjectFactAgentResultRef::getAgentResultRef).map(this::safeReference)
            .filter(value -> !value.isBlank()).toList(), limit);
        List<String> evidence = limit(fact.getEvidenceRefs().stream().map(this::safeEvidence)
            .filter(value -> !value.isBlank()).toList(), limit);
        boolean truncated = commitRepository.findByFactId(factId).size() > commits.size()
            || fileRepository.findByFactId(factId).size() > files.size()
            || agentRepository.findByFactId(factId).size() > agents.size()
            || fact.getEvidenceRefs().size() > evidence.size();
        List<UUID> related = relatedCapabilities(projectId, List.of(factId)).getOrDefault(factId, List.of());
        return new MemoryFactTraceResponse(
            projectId, factId, text(fact.getTitle(), detailed), text(fact.getSummary(), detailed),
            new MemoryTimeResponse(
                fact.getOccurredFrom(), fact.getOccurredTo(), eventAt(fact), fact.getCreatedAt(),
                batch == null ? null : firstNonNull(batch.getScanFinishedAt(), batch.getScanStartedAt()), null
            ), fact.getRecordStatus().name(), text(fact.getAttentionReason(), detailed), fact.getBatchId(),
            batch == null ? "" : batch.getBranchName(), batch == null ? "" : batch.getScanType(),
            commits, files, agents, evidence, related, truncated, FACT_TRUTH
        );
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
                "/api/projects/" + projectId + "/project-memory/facts/" + fact.id() + "/trace"
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
            source.summaryStale(), 0, null, summaryNotice(source.summaryStatus(), source.summaryPreview())
        );
        return new MemoryTimelinePeriodResponse(
            source.periodKey(), source.periodStart(), source.periodEnd(), source.stats(), summary, source.themeCount()
        );
    }

    private MemoryTimelineSummaryResponse summary(TimelineSummaryResponse source) {
        if (source == null) {
            return new MemoryTimelineSummaryResponse(
                "NOT_GENERATED", "", 0, 0, false, 0, null,
                "尚无自动摘要，事实和确定性统计仍可读取"
            );
        }
        return new MemoryTimelineSummaryResponse(
            source.status(), text(source.summary(), true), source.sourceFactCount(), source.coveredFactCount(),
            source.stale(), source.generationVersion(), source.generatedAt(),
            summaryNotice(source.status(), source.summary())
        );
    }

    private MemoryTimelineSummaryResponse summary(ProjectTimelineSummary source) {
        if (source == null) {
            return new MemoryTimelineSummaryResponse(
                "NOT_GENERATED", "", 0, 0, false, 0, null,
                "尚无自动摘要，事实和确定性统计仍可读取"
            );
        }
        boolean stale = source.hasGeneratedContent() && source.getStatus() != com.projectflow.entity.ProjectTimelineSummaryStatus.READY;
        return new MemoryTimelineSummaryResponse(
            source.getStatus().name(), text(source.getSummary(), true), source.getSourceFactCount(), source.getCoveredFactCount(),
            stale, source.getGenerationVersion(), source.getGeneratedAt(),
            summaryNotice(source.getStatus().name(), source.getSummary())
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

    private void addFactCandidates(
        UUID projectId, String query, List<String> tokens, Instant from, Instant to, List<SearchCandidate> target
    ) {
        LinkedHashMap<UUID, ProjectFact> facts = new LinkedHashMap<>();
        parseUuid(query).flatMap(id -> factRepository.findByIdAndProjectId(id, projectId)).ifPresent(value -> facts.put(value.getId(), value));
        for (String token : tokens.stream().limit(4).toList()) {
            factRepository.searchMemoryCandidates(projectId, token, from, to, PageRequest.of(0, 100))
                .forEach(value -> facts.putIfAbsent(value.getId(), value));
        }
        Map<UUID, List<UUID>> related = relatedCapabilities(projectId, new ArrayList<>(facts.keySet()));
        for (ProjectFact fact : facts.values()) {
            Instant eventAt = eventAt(fact);
            if (!within(eventAt, from, to)) continue;
            Match match = match(query, tokens, Map.of(
                "stable id", fact.getId().toString(), "标题", fact.getTitle(), "摘要", fact.getSummary(),
                "主要变化", String.join(" ", fact.getMainChanges()), "用户价值", fact.getUserVisibleValue(),
                "关注原因", fact.getAttentionReason()
            ));
            if (match.score() <= 0) continue;
            target.add(new SearchCandidate(match.score(), new MemorySearchResultResponse(
                "FACT", fact.getId(), text(fact.getTitle(), false), text(fact.getSummary(), false),
                eventAt, fact.getOccurredFrom(), fact.getOccurredTo(), match.reason(), match.fields(),
                related.getOrDefault(fact.getId(), List.of()), FACT_TRUTH,
                "/api/projects/" + projectId + "/project-memory/facts/" + fact.getId() + "/trace"
            )));
        }
    }

    private void addTimelineCandidates(
        UUID projectId, String query, List<String> tokens, Instant from, Instant to, List<SearchCandidate> target
    ) {
        List<ProjectTimelineSummary> summaries = timelineSummaryRepository.findByProjectIdOrderByUpdatedAtDesc(projectId);
        Map<UUID, ProjectTimelineSummary> byId = summaries.stream()
            .collect(Collectors.toMap(ProjectTimelineSummary::getId, Function.identity()));
        for (ProjectTimelineSummary item : summaries) {
            Instant occurred = item.getPeriodEnd() == null ? item.getPeriodStart() : item.getPeriodEnd();
            if (!overlaps(item.getPeriodStart(), item.getPeriodEnd(), from, to)) continue;
            String title = item.getGranularity().name() + " " + item.getPeriodKey();
            Match match = match(query, tokens, Map.of(
                "stable id", item.getId().toString(), "时间段", title, "摘要", item.getSummary()
            ));
            if (match.score() <= 0) continue;
            target.add(new SearchCandidate(match.score(), new MemorySearchResultResponse(
                "TIMELINE_PERIOD", item.getId(), title, text(item.getSummary(), false), occurred,
                item.getPeriodStart(), item.getPeriodEnd(), match.reason(), match.fields(), List.of(),
                TIMELINE_TRUTH, "/api/projects/" + projectId + "/project-memory/timeline?granularity="
                    + item.getGranularity().name() + "&periodKey=" + item.getPeriodKey()
            )));
        }
        for (ProjectTimelineTheme item : timelineThemeRepository.findByProjectIdOrderByUpdatedAtDesc(projectId)) {
            ProjectTimelineSummary summary = byId.get(item.getSummaryId());
            Instant start = summary == null ? null : summary.getPeriodStart();
            Instant end = summary == null ? null : summary.getPeriodEnd();
            if (!overlaps(start, end, from, to)) continue;
            Match match = match(query, tokens, Map.of(
                "stable id", item.getId().toString(), "主题", item.getTitle(), "摘要", item.getSummary()
            ));
            if (match.score() <= 0) continue;
            target.add(new SearchCandidate(match.score(), new MemorySearchResultResponse(
                "TIMELINE_THEME", item.getId(), item.getTitle(), text(item.getSummary(), false), end,
                start, end, match.reason(), match.fields(), List.of(item.getSummaryId()), TIMELINE_TRUTH,
                summary == null ? "" : "/api/projects/" + projectId + "/project-memory/timeline?granularity="
                    + summary.getGranularity().name() + "&periodKey=" + summary.getPeriodKey()
            )));
        }
    }

    private void addCapabilityCandidates(
        UUID projectId, String query, List<String> tokens, Instant from, Instant to, List<SearchCandidate> target
    ) {
        for (ProjectCapability item : capabilityRepository.findByProjectIdOrderByCreatedAtAsc(projectId)) {
            Instant occurred = firstNonNull(item.getLastEnhancedAt(), item.getFirstFormedAt());
            if (!within(occurred, from, to)) continue;
            Match match = match(query, tokens, Map.of(
                "stable id", item.getId().toString(), "能力名称", item.getCanonicalName(),
                "别名", String.join(" ", item.getAliases()), "摘要", item.getCurrentSummary(),
                "解决问题", item.getProblemSolved(), "长期价值", item.getLongTermValue(),
                "产品区域", String.join(" ", item.getProductAreas())
            ));
            if (match.score() <= 0) continue;
            target.add(new SearchCandidate(match.score(), new MemorySearchResultResponse(
                "CAPABILITY", item.getId(), item.getCanonicalName(), text(item.getCurrentSummary(), false),
                occurred, item.getFirstFormedAt(), item.getLastEnhancedAt(), match.reason(), match.fields(),
                item.getMergedIntoCapabilityId() == null ? List.of() : List.of(item.getMergedIntoCapabilityId()),
                CAPABILITY_TRUTH, "/api/projects/" + projectId + "/project-memory/capabilities/" + item.getId() + "/evolution"
            )));
        }
    }

    private void addEvolutionCandidates(
        UUID projectId, String query, List<String> tokens, Instant from, Instant to, List<SearchCandidate> target
    ) {
        List<ProjectCapabilityEvolution> items = evolutionRepository
            .findByProjectIdOrderByOccurredAtDescCreatedAtDesc(projectId, PageRequest.of(0, 2_000)).getContent();
        Set<UUID> capabilityIds = items.stream().map(ProjectCapabilityEvolution::getCapabilityId).collect(Collectors.toSet());
        Map<UUID, String> names = capabilityRepository.findAllById(capabilityIds).stream()
            .collect(Collectors.toMap(ProjectCapability::getId, ProjectCapability::getCanonicalName));
        for (ProjectCapabilityEvolution item : items) {
            if (!within(item.getOccurredAt(), from, to)) continue;
            Match match = match(query, tokens, Map.of(
                "stable id", item.getId().toString(), "能力名称", names.getOrDefault(item.getCapabilityId(), ""),
                "演进标题", item.getTitle(), "演进摘要", item.getSummary(), "来源时间段", String.join(" ", item.getSourceTimelinePeriods())
            ));
            if (match.score() <= 0) continue;
            target.add(new SearchCandidate(match.score(), new MemorySearchResultResponse(
                "EVOLUTION", item.getId(), item.getTitle(), text(item.getSummary(), false), item.getOccurredAt(),
                item.getOccurredAt(), item.getOccurredAt(), match.reason(), match.fields(), List.of(item.getCapabilityId()),
                CAPABILITY_TRUTH, "/api/projects/" + projectId + "/project-memory/capabilities/" + item.getCapabilityId() + "/evolution"
            )));
        }
    }

    private Match match(String query, List<String> tokens, Map<String, String> fields) {
        int score = 0;
        LinkedHashSet<String> matched = new LinkedHashSet<>();
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            String value = normalize(entry.getValue());
            if (value.isBlank()) continue;
            int weight = "stable id".equals(entry.getKey()) ? 100 : nameField(entry.getKey()) ? 45 : 20;
            if (value.equals(query)) {
                score += weight + 50;
                matched.add(entry.getKey());
                continue;
            }
            if (value.contains(query)) {
                score += weight;
                matched.add(entry.getKey());
            }
            for (String token : tokens) {
                if (value.contains(token)) {
                    score += Math.max(2, weight / 4);
                    matched.add(entry.getKey());
                }
            }
        }
        List<String> fieldsMatched = List.copyOf(matched);
        String reason = fieldsMatched.isEmpty() ? "" : "匹配" + String.join("、", fieldsMatched);
        return new Match(score, fieldsMatched, reason);
    }

    private Set<SearchType> searchTypes(String value) {
        if (value == null || value.isBlank()) return EnumSet.allOf(SearchType.class);
        EnumSet<SearchType> types = EnumSet.noneOf(SearchType.class);
        for (String raw : value.split(",")) {
            try { types.add(SearchType.valueOf(raw.trim().toUpperCase(Locale.ROOT))); }
            catch (RuntimeException exception) {
                throw new AppException("INVALID_MEMORY_ENTITY_TYPE", "无效的项目记忆实体类型", HttpStatus.BAD_REQUEST);
            }
        }
        return types.isEmpty() ? EnumSet.allOf(SearchType.class) : types;
    }

    private List<String> searchTokens(String normalized) {
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        Matcher matcher = SEARCH_TOKEN.matcher(normalized);
        while (matcher.find() && tokens.size() < 8) tokens.add(matcher.group());
        if (tokens.isEmpty()) tokens.add(normalized);
        return tokens.stream().sorted(Comparator.comparingInt(String::length).reversed()).toList();
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

    private String safeReference(String value) {
        String normalized = safe(value).replace('\\', '/');
        if (normalized.isBlank()) return "";
        if (WINDOWS_ABSOLUTE.matcher(normalized).matches() || normalized.startsWith("/") || normalized.startsWith("//")) {
            String[] parts = normalized.split("/");
            String leaf = parts.length == 0 ? "" : parts[parts.length - 1];
            return leaf.isBlank() ? "[路径已隐藏]" : "[路径已隐藏]/" + leaf;
        }
        if (normalized.split("/").length > 0 && List.of(normalized.split("/")).contains("..")) return "[越界路径已隐藏]";
        return text(normalized, false);
    }

    private String safeEvidence(String value) {
        String safe = safe(value);
        int separator = safe.indexOf(':');
        if (separator <= 0) return safeReference(safe);
        String prefix = safe.substring(0, separator + 1);
        return prefix + safeReference(safe.substring(separator + 1));
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
    private static boolean within(Instant value, Instant from, Instant to) {
        if (from == null && to == null) return true;
        if (value == null) return false;
        return (from == null || !value.isBefore(from)) && (to == null || !value.isAfter(to));
    }
    private static boolean overlaps(Instant start, Instant end, Instant from, Instant to) {
        if (from == null && to == null) return true;
        Instant effectiveStart = start == null ? end : start;
        Instant effectiveEnd = end == null ? start : end;
        if (effectiveStart == null && effectiveEnd == null) return false;
        return (to == null || !effectiveStart.isAfter(to)) && (from == null || !effectiveEnd.isBefore(from));
    }
    private static boolean nameField(String field) {
        return field.contains("标题") || field.contains("名称") || field.contains("主题") || field.contains("时间段") || field.contains("别名");
    }
    private static <T> List<T> limit(Collection<T> values, int limit) {
        if (values == null || values.isEmpty()) return List.of();
        return values.stream().limit(limit).toList();
    }
    private static java.util.Optional<UUID> parseUuid(String value) {
        try { return java.util.Optional.of(UUID.fromString(safe(value))); }
        catch (RuntimeException exception) { return java.util.Optional.empty(); }
    }

    private enum SearchType { FACT, TIMELINE, CAPABILITY, EVOLUTION }
    private record Match(int score, List<String> fields, String reason) { }
    private record SearchCandidate(int score, MemorySearchResultResponse result) {
        private static final Comparator<SearchCandidate> ORDER = Comparator
            .comparingInt(SearchCandidate::score).reversed()
            .thenComparing(candidate -> candidate.result().occurredAt(), Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(candidate -> candidate.result().entityId());
    }
}
