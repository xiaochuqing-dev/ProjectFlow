package com.projectflow.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projectflow.dto.ProjectCapabilityDtos.CapabilityAttentionPageResponse;
import com.projectflow.dto.ProjectCapabilityDtos.CapabilityAttentionResponse;
import com.projectflow.dto.ProjectCapabilityDtos.CapabilityDetailResponse;
import com.projectflow.dto.ProjectCapabilityDtos.CapabilityEvolutionPageResponse;
import com.projectflow.dto.ProjectCapabilityDtos.CapabilityEvolutionResponse;
import com.projectflow.dto.ProjectCapabilityDtos.CapabilityFactPageResponse;
import com.projectflow.dto.ProjectCapabilityDtos.CapabilityFactResponse;
import com.projectflow.dto.ProjectCapabilityDtos.CapabilityListItemResponse;
import com.projectflow.dto.ProjectCapabilityDtos.CapabilityMapOverviewResponse;
import com.projectflow.dto.ProjectCapabilityDtos.CapabilityPageResponse;
import com.projectflow.dto.ProjectCapabilityDtos.CoverageStatusResponse;
import com.projectflow.entity.ProjectCapability;
import com.projectflow.entity.ProjectCapabilityAttention;
import com.projectflow.entity.ProjectCapabilityAttentionStatus;
import com.projectflow.entity.ProjectCapabilityEvolution;
import com.projectflow.entity.ProjectCapabilityMapState;
import com.projectflow.entity.ProjectCapabilityMapStatus;
import com.projectflow.entity.ProjectCapabilityMaturity;
import com.projectflow.entity.ProjectCapabilityStatus;
import com.projectflow.entity.ProjectFactHistoryState;
import com.projectflow.entity.ProjectTimelineSummary;
import com.projectflow.entity.TimelineGranularity;
import com.projectflow.repository.CapabilityFactRow;
import com.projectflow.repository.ProjectCapabilityAttentionRepository;
import com.projectflow.repository.ProjectCapabilityEvolutionRepository;
import com.projectflow.repository.ProjectCapabilityFactRepository;
import com.projectflow.repository.ProjectCapabilityMapStateRepository;
import com.projectflow.repository.ProjectCapabilityRepository;
import com.projectflow.repository.ProjectFactHistoryStateRepository;
import com.projectflow.repository.ProjectFactRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.repository.ProjectTimelineSummaryRepository;
import com.projectflow.support.AppException;

@Service
public class ProjectCapabilityQueryService {
    private final ProjectRepository projectRepository;
    private final ProjectCapabilityRepository capabilityRepository;
    private final ProjectCapabilityEvolutionRepository evolutionRepository;
    private final ProjectCapabilityFactRepository capabilityFactRepository;
    private final ProjectCapabilityAttentionRepository attentionRepository;
    private final ProjectCapabilityMapStateRepository stateRepository;
    private final ProjectFactRepository factRepository;
    private final ProjectFactHistoryStateRepository historyRepository;
    private final ProjectTimelineSummaryRepository timelineRepository;

    public ProjectCapabilityQueryService(
        ProjectRepository projectRepository,
        ProjectCapabilityRepository capabilityRepository,
        ProjectCapabilityEvolutionRepository evolutionRepository,
        ProjectCapabilityFactRepository capabilityFactRepository,
        ProjectCapabilityAttentionRepository attentionRepository,
        ProjectCapabilityMapStateRepository stateRepository,
        ProjectFactRepository factRepository,
        ProjectFactHistoryStateRepository historyRepository,
        ProjectTimelineSummaryRepository timelineRepository
    ) {
        this.projectRepository = projectRepository;
        this.capabilityRepository = capabilityRepository;
        this.evolutionRepository = evolutionRepository;
        this.capabilityFactRepository = capabilityFactRepository;
        this.attentionRepository = attentionRepository;
        this.stateRepository = stateRepository;
        this.factRepository = factRepository;
        this.historyRepository = historyRepository;
        this.timelineRepository = timelineRepository;
    }

    @Transactional(readOnly = true)
    public CapabilityMapOverviewResponse overview(UUID userId, UUID projectId) {
        ownedProject(userId, projectId);
        ProjectCapabilityMapState state = stateRepository.findByProjectId(projectId).orElse(null);
        long facts = factRepository.countByProjectId(projectId);
        Map<String, Long> maturity = new LinkedHashMap<>();
        for (ProjectCapabilityMaturity level : ProjectCapabilityMaturity.values()) {
            maturity.put(level.name(), capabilityRepository.countByProjectIdAndMaturityLevel(projectId, level));
        }
        int source = state == null ? Math.toIntExact(facts) : state.getSourceFactCount();
        int covered = state == null ? 0 : state.getCoveredFactCount();
        ProjectFactHistoryState history = historyRepository.findByProjectId(projectId).orElse(null);
        CoverageStatusResponse historyCoverage = history == null
            ? new CoverageStatusResponse("NOT_STARTED", 0, 0, 0)
            : new CoverageStatusResponse(history.getStatus().name(), history.getCoveredCommitCount(), history.getTotalCommitCount(), history.getRemainingCommitCount());
        ProjectTimelineSummary lifecycle = timelineRepository.findByProjectIdAndGranularityAndPeriodKey(
            projectId, TimelineGranularity.LIFECYCLE, "ALL"
        ).orElse(null);
        CoverageStatusResponse timelineCoverage = lifecycle == null
            ? new CoverageStatusResponse("NOT_INITIALIZED", 0, source, source)
            : new CoverageStatusResponse(
                lifecycle.getStatus().name(), lifecycle.getCoveredFactCount(), lifecycle.getSourceFactCount(),
                Math.max(0, lifecycle.getSourceFactCount() - lifecycle.getCoveredFactCount())
            );
        return new CapabilityMapOverviewResponse(
            projectId,
            capabilityRepository.countByProjectId(projectId),
            capabilityRepository.countByProjectIdAndStatus(projectId, ProjectCapabilityStatus.ACTIVE),
            capabilityRepository.countByProjectIdAndStatus(projectId, ProjectCapabilityStatus.MERGED),
            Map.copyOf(maturity), source, covered,
            state == null ? 0 : state.getAssignedFactCount(),
            state == null ? 0 : state.getNoChangeFactCount(),
            Math.max(0, source - covered),
            attentionRepository.countByProjectIdAndStatus(projectId, ProjectCapabilityAttentionStatus.OPEN),
            state == null ? "" : state.getSourceFactFingerprint(),
            state == null ? ProjectCapabilityMapStatus.NOT_INITIALIZED.name() : state.getStatus().name(),
            stale(state), state == null ? null : state.getLatestSuccessfulAt(), state == null ? null : state.getLatestAttemptAt(),
            state == null ? "" : state.getErrorCode(), state == null ? "" : state.getErrorSummary(),
            historyCoverage, timelineCoverage
        );
    }

    @Transactional(readOnly = true)
    public CapabilityPageResponse list(
        UUID userId, UUID projectId, String status, String maturity, String search, String sort,
        int page, int size
    ) {
        ownedProject(userId, projectId);
        ProjectCapabilityStatus statusFilter = parseStatus(status);
        ProjectCapabilityMaturity maturityFilter = parseMaturity(maturity);
        String sortProperty = switch (safe(sort)) {
            case "name" -> "canonicalName";
            case "firstFormedAt" -> "firstFormedAt";
            case "maturity" -> "maturityLevel";
            case "factCount" -> "sourceFactCount";
            default -> "lastEnhancedAt";
        };
        Sort.Direction direction = "name".equals(safe(sort)) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Page<ProjectCapability> result = capabilityRepository.search(
            projectId, statusFilter, maturityFilter, safe(search),
            PageRequest.of(Math.max(0, page), clamp(size), Sort.by(direction, sortProperty).and(Sort.by("id")))
        );
        boolean stale = stale(stateRepository.findByProjectId(projectId).orElse(null));
        return new CapabilityPageResponse(
            result.getContent().stream().map(item -> listItem(item, stale)).toList(),
            result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public CapabilityDetailResponse detail(UUID userId, UUID capabilityId) {
        ProjectCapability capability = capabilityRepository.findById(capabilityId)
            .orElseThrow(() -> new AppException("PROJECT_CAPABILITY_NOT_FOUND", "项目能力不存在", HttpStatus.NOT_FOUND));
        ownedProject(userId, capability.getProjectId());
        boolean stale = stale(stateRepository.findByProjectId(capability.getProjectId()).orElse(null));
        List<CapabilityListItemResponse> merged = capabilityRepository
            .findByProjectIdAndMergedIntoCapabilityIdOrderByUpdatedAtDesc(capability.getProjectId(), capability.getId())
            .stream().map(item -> listItem(item, stale)).toList();
        return new CapabilityDetailResponse(
            capability.getId(), capability.getProjectId(), capability.getCanonicalName(), capability.getAliases(),
            capability.getCurrentSummary(), capability.getProblemSolved(), capability.getLongTermValue(), capability.getProductAreas(),
            capability.getStatus().name(), capability.getMaturityLevel().name(), capability.getMaturityReason(),
            capability.getFirstFormedAt(), capability.getLastEnhancedAt(), capability.getSourceFactCount(),
            capability.getSourceBatchCount(), capability.getDistinctCommitCount(), capability.getEvidenceCount(),
            capability.getAttentionFactCount(), capability.getEvolutionCount(), capability.getCurrentVersion(),
            capability.getGenerationMode(), capability.getMergedIntoCapabilityId(), capability.getReadmeExpression(),
            capability.getResumeExpression(), capability.getInterviewExpression(),
            evolutionsInternal(capability.getProjectId(), capability.getId(), 0, 20),
            factsInternal(capability.getProjectId(), capability.getId(), 0, 20), merged, stale
        );
    }

    @Transactional(readOnly = true)
    public CapabilityEvolutionPageResponse evolutions(UUID userId, UUID capabilityId, int page, int size) {
        ProjectCapability capability = ownedCapability(userId, capabilityId);
        return evolutionsInternal(capability.getProjectId(), capabilityId, page, size);
    }

    @Transactional(readOnly = true)
    public CapabilityFactPageResponse facts(UUID userId, UUID capabilityId, int page, int size) {
        ProjectCapability capability = ownedCapability(userId, capabilityId);
        return factsInternal(capability.getProjectId(), capabilityId, page, size);
    }

    @Transactional(readOnly = true)
    public CapabilityEvolutionPageResponse changes(UUID userId, UUID projectId, int page, int size) {
        ownedProject(userId, projectId);
        Page<ProjectCapabilityEvolution> result = evolutionRepository.findByProjectIdOrderByOccurredAtDescCreatedAtDesc(
            projectId, PageRequest.of(Math.max(0, page), clamp(size))
        );
        return evolutionPage(result);
    }

    @Transactional(readOnly = true)
    public CapabilityAttentionPageResponse attention(UUID userId, UUID projectId, int page, int size) {
        ownedProject(userId, projectId);
        Page<ProjectCapabilityAttention> result = attentionRepository.findByProjectIdAndStatusOrderByCreatedAtDesc(
            projectId, ProjectCapabilityAttentionStatus.OPEN, PageRequest.of(Math.max(0, page), clamp(size))
        );
        return new CapabilityAttentionPageResponse(
            result.getContent().stream().map(item -> new CapabilityAttentionResponse(
                item.getId(), item.getAttentionType(), item.getReason(), item.getFactId(), item.getSourceCapabilityId(),
                item.getTargetCapabilityId(), item.getStatus().name(), item.getCreatedAt()
            )).toList(), result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages()
        );
    }

    private CapabilityEvolutionPageResponse evolutionsInternal(UUID projectId, UUID capabilityId, int page, int size) {
        return evolutionPage(evolutionRepository.findByProjectIdAndCapabilityIdOrderByOccurredAtDescCreatedAtDesc(
            projectId, capabilityId, PageRequest.of(Math.max(0, page), clamp(size))
        ));
    }

    private CapabilityEvolutionPageResponse evolutionPage(Page<ProjectCapabilityEvolution> result) {
        return new CapabilityEvolutionPageResponse(
            result.getContent().stream().map(this::evolution).toList(), result.getNumber(), result.getSize(),
            result.getTotalElements(), result.getTotalPages()
        );
    }

    private CapabilityFactPageResponse factsInternal(UUID projectId, UUID capabilityId, int page, int size) {
        Page<CapabilityFactRow> result = capabilityFactRepository.pageFacts(
            projectId, capabilityId, PageRequest.of(Math.max(0, page), clamp(size))
        );
        return new CapabilityFactPageResponse(
            result.getContent().stream().map(row -> new CapabilityFactResponse(
                row.factId(), row.projectId(), row.batchId(), row.title(), row.summary(), row.occurredFrom(), row.occurredTo(),
                row.recordStatus().name(), row.attentionReason(), row.commitCount(), row.affectedFileCount(), row.evidenceCount(),
                row.relationRole().name(), row.sourceEvolutionId(), row.linkedAt()
            )).toList(), result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages()
        );
    }

    private CapabilityEvolutionResponse evolution(ProjectCapabilityEvolution item) {
        return new CapabilityEvolutionResponse(
            item.getId(), item.getCapabilityId(), item.getEvolutionType().name(), item.getVersionBefore(), item.getVersionAfter(),
            item.getTitle(), item.getSummary(), item.getOccurredAt(), item.getSourceFactCount(), item.getSourceBatchCount(),
            item.getSourceTimelinePeriods(), item.getAnalysisJobId(), item.getMergedFromCapabilityId()
        );
    }

    private CapabilityListItemResponse listItem(ProjectCapability item, boolean stale) {
        return new CapabilityListItemResponse(
            item.getId(), item.getProjectId(), item.getCanonicalName(), item.getCurrentSummary(), item.getStatus().name(),
            item.getMaturityLevel().name(), item.getMaturityReason(), item.getFirstFormedAt(), item.getLastEnhancedAt(),
            item.getSourceFactCount(), item.getSourceBatchCount(), item.getDistinctCommitCount(), item.getEvolutionCount(),
            item.getAttentionFactCount(), stale, item.getMergedIntoCapabilityId()
        );
    }

    private ProjectCapability ownedCapability(UUID userId, UUID capabilityId) {
        ProjectCapability capability = capabilityRepository.findById(capabilityId)
            .orElseThrow(() -> new AppException("PROJECT_CAPABILITY_NOT_FOUND", "项目能力不存在", HttpStatus.NOT_FOUND));
        ownedProject(userId, capability.getProjectId());
        return capability;
    }

    private void ownedProject(UUID userId, UUID projectId) {
        projectRepository.findByIdAndUserId(projectId, userId)
            .orElseThrow(() -> new AppException("PROJECT_NOT_FOUND", "项目不存在", HttpStatus.NOT_FOUND));
    }

    private static ProjectCapabilityStatus parseStatus(String value) {
        if (safe(value).isBlank()) return null;
        try { return ProjectCapabilityStatus.valueOf(value.trim().toUpperCase(Locale.ROOT)); }
        catch (RuntimeException exception) { throw new AppException("INVALID_CAPABILITY_STATUS", "无效的能力状态", HttpStatus.BAD_REQUEST); }
    }
    private static ProjectCapabilityMaturity parseMaturity(String value) {
        if (safe(value).isBlank()) return null;
        try { return ProjectCapabilityMaturity.valueOf(value.trim().toUpperCase(Locale.ROOT)); }
        catch (RuntimeException exception) { throw new AppException("INVALID_CAPABILITY_MATURITY", "无效的能力成熟阶段", HttpStatus.BAD_REQUEST); }
    }
    private static boolean stale(ProjectCapabilityMapState state) {
        return state != null && state.getLatestSuccessfulAt() != null && state.getStatus() != ProjectCapabilityMapStatus.READY;
    }
    private static int clamp(int size) { return Math.max(1, Math.min(100, size <= 0 ? 20 : size)); }
    private static String safe(String value) { return value == null ? "" : value.trim(); }
}
