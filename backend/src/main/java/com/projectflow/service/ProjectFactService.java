package com.projectflow.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projectflow.dto.ProjectFactDtos.FactMemoryOverviewResponse;
import com.projectflow.dto.ProjectFactDtos.ProjectFactDetailResponse;
import com.projectflow.dto.ProjectFactDtos.ProjectFactHistoryStateResponse;
import com.projectflow.dto.ProjectFactDtos.ProjectFactPageResponse;
import com.projectflow.dto.ProjectFactDtos.ProjectFactSummaryResponse;
import com.projectflow.dto.ProjectFactDtos.ProjectRecordBatchDetailResponse;
import com.projectflow.dto.ProjectFactDtos.ProjectRecordBatchPageResponse;
import com.projectflow.dto.ProjectFactDtos.ProjectRecordBatchResponse;
import com.projectflow.entity.ChangeBatch;
import com.projectflow.entity.ProjectFact;
import com.projectflow.entity.ProjectFactHistoryState;
import com.projectflow.entity.ProjectFactRecordStatus;
import com.projectflow.repository.ChangeBatchRepository;
import com.projectflow.repository.ProjectFactCommitRefRepository;
import com.projectflow.repository.ProjectFactHistoryStateRepository;
import com.projectflow.repository.ProjectFactOverviewRow;
import com.projectflow.repository.ProjectFactRepository;
import com.projectflow.repository.ProjectFactSummaryRow;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.support.AppException;

@Service
public class ProjectFactService {
    private final ProjectRepository projectRepository;
    private final ProjectFactRepository factRepository;
    private final ProjectFactCommitRefRepository commitRefRepository;
    private final ProjectFactHistoryStateRepository historyStateRepository;
    private final ChangeBatchRepository batchRepository;

    public ProjectFactService(
        ProjectRepository projectRepository,
        ProjectFactRepository factRepository,
        ProjectFactCommitRefRepository commitRefRepository,
        ProjectFactHistoryStateRepository historyStateRepository,
        ChangeBatchRepository batchRepository
    ) {
        this.projectRepository = projectRepository;
        this.factRepository = factRepository;
        this.commitRefRepository = commitRefRepository;
        this.historyStateRepository = historyStateRepository;
        this.batchRepository = batchRepository;
    }

    @Transactional(readOnly = true)
    public ProjectFactPageResponse listFacts(
        UUID userId,
        UUID projectId,
        Instant from,
        Instant to,
        UUID batchId,
        ProjectFactRecordStatus recordStatus,
        int page,
        int size
    ) {
        ownedProject(userId, projectId);
        Page<ProjectFactSummaryRow> result = factRepository.searchSummaries(
            projectId, from, to, batchId, recordStatus, PageRequest.of(Math.max(0, page), clampSize(size))
        );
        return factPage(result);
    }

    @Transactional(readOnly = true)
    public ProjectFactDetailResponse getFact(UUID userId, UUID factId) {
        ProjectFact fact = factRepository.findById(factId)
            .orElseThrow(() -> new AppException("PROJECT_FACT_NOT_FOUND", "项目事实不存在", HttpStatus.NOT_FOUND));
        ownedProject(userId, fact.getProjectId());
        return detail(fact);
    }

    @Transactional(readOnly = true)
    public FactMemoryOverviewResponse overview(UUID userId, UUID projectId) {
        ownedProject(userId, projectId);
        ProjectFactOverviewRow summary = factRepository.summarize(
            projectId, ProjectFactRecordStatus.RECORDED, ProjectFactRecordStatus.NEEDS_ATTENTION
        );
        long covered = commitRefRepository.countDistinctCommitShaByProjectId(projectId);
        ProjectFactHistoryState history = historyStateRepository.findByProjectId(projectId).orElse(null);
        long totalCommits = history == null ? covered : Math.max(covered, history.getTotalCommitCount());
        return new FactMemoryOverviewResponse(
            projectId, summary.safeTotalCount(), summary.safeRecordedCount(), summary.safeAttentionCount(),
            covered, totalCommits, summary.earliestOccurredAt(), summary.latestOccurredAt()
        );
    }

    @Transactional(readOnly = true)
    public ProjectFactHistoryStateResponse historyState(UUID userId, UUID projectId) {
        ownedProject(userId, projectId);
        ProjectFactHistoryState state = historyStateRepository.findByProjectId(projectId).orElse(null);
        if (state == null) {
            int covered = Math.toIntExact(Math.min(Integer.MAX_VALUE, commitRefRepository.countDistinctCommitShaByProjectId(projectId)));
            return new ProjectFactHistoryStateResponse(
                projectId, "NOT_STARTED", "", "", covered, covered, 0, "", 0, 0,
                null, null, null, null, "", ""
            );
        }
        return historyResponse(state);
    }

    @Transactional(readOnly = true)
    public ProjectRecordBatchPageResponse listRecordBatches(UUID userId, UUID projectId, int page, int size) {
        ownedProject(userId, projectId);
        Page<ChangeBatch> result = batchRepository.findByProjectIdOrderByScanStartedAtDesc(
            projectId, PageRequest.of(Math.max(0, page), clampSize(size))
        );
        return new ProjectRecordBatchPageResponse(
            result.getContent().stream().map(this::batchResponse).toList(), result.getNumber(), result.getSize(),
            result.getTotalElements(), result.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public ProjectRecordBatchDetailResponse getRecordBatch(UUID userId, UUID batchId, int page, int size) {
        ChangeBatch batch = batchRepository.findById(batchId)
            .orElseThrow(() -> new AppException("CHANGE_BATCH_NOT_FOUND", "分析批次不存在", HttpStatus.NOT_FOUND));
        ownedProject(userId, batch.getProjectId());
        Page<ProjectFactSummaryRow> facts = factRepository.searchSummaries(
            batch.getProjectId(), null, null, batchId, null, PageRequest.of(Math.max(0, page), clampSize(size))
        );
        return new ProjectRecordBatchDetailResponse(batchResponse(batch), factPage(facts));
    }

    private ProjectFactPageResponse factPage(Page<ProjectFactSummaryRow> page) {
        return new ProjectFactPageResponse(
            page.getContent().stream().map(this::summary).toList(), page.getNumber(), page.getSize(),
            page.getTotalElements(), page.getTotalPages()
        );
    }

    private ProjectFactSummaryResponse summary(ProjectFactSummaryRow fact) {
        return new ProjectFactSummaryResponse(
            fact.id(), fact.projectId(), fact.batchId(), fact.sourceSegmentId(), fact.legacySedimentId(),
            fact.origin().name(), fact.title(), fact.summary(), fact.occurredFrom(), fact.occurredTo(),
            fact.sourceMode(), fact.qualityStatus(), fact.confidence().name(), fact.recordStatus().name(),
            fact.attentionReason(), fact.safeCommitCount(), fact.safeAgentResultCount(),
            fact.safeAffectedFileCount(), fact.safeEvidenceCount(), fact.createdAt(), fact.updatedAt(),
            fact.safeEpistemicStatus().name(), fact.safeCurrentness(), fact.safeRevision(),
            fact.safeValidationStatus(), fact.safeLimitations()
        );
    }

    private ProjectFactDetailResponse detail(ProjectFact fact) {
        ProjectFactSummaryResponse summary = new ProjectFactSummaryResponse(
            fact.getId(), fact.getProjectId(), fact.getBatchId(), fact.getSourceSegmentId(), fact.getLegacySedimentId(),
            fact.getOrigin().name(), fact.getTitle(), fact.getSummary(), fact.getOccurredFrom(), fact.getOccurredTo(),
            fact.getSourceMode(), fact.getQualityStatus(), fact.getConfidence().name(), fact.getRecordStatus().name(),
            fact.getAttentionReason(), fact.getCommitCount(), fact.getAgentResultCount(), fact.getAffectedFileCount(),
            fact.getEvidenceCount(), fact.getCreatedAt(), fact.getUpdatedAt(), fact.getEpistemicStatus().name(),
            fact.getCurrentness(), fact.getRevision(), fact.getValidationStatus(), fact.getLimitations()
        );
        return new ProjectFactDetailResponse(
            summary.id(), summary.projectId(), summary.batchId(), summary.sourceSegmentId(), summary.legacySedimentId(),
            summary.origin(), summary.title(), summary.summary(), summary.occurredFrom(), summary.occurredTo(),
            summary.sourceMode(), summary.qualityStatus(), summary.confidence(), summary.recordStatus(), summary.attentionReason(),
            summary.commitCount(), summary.agentResultCount(), summary.affectedFileCount(), summary.evidenceCount(),
            summary.createdAt(), summary.updatedAt(), fact.getMainChanges(), fact.getUserVisibleValue(), fact.getCommitRefs(),
            fact.getCommitUrls(), fact.getAgentResultRefs(), fact.getAffectedFiles(), fact.getEvidenceRefs(), fact.getFactFingerprint(),
            fact.getStatement(), fact.getEpistemicStatus().name(), fact.getSourceTypes(), fact.getCurrentness(),
            fact.getRevision(), fact.getObservedAt(), fact.getEffectiveAt(), fact.getSupersededBy(),
            fact.getLimitations(), fact.getConflictRefs(), fact.getCreatedBy(), fact.getSourceAgentId(),
            fact.getSourceModelProvider(), fact.getValidationStatus()
        );
    }

    private ProjectRecordBatchResponse batchResponse(ChangeBatch batch) {
        String resultSource = "HISTORY_BACKFILL".equals(batch.getScanType())
            ? "HISTORY_FACTS"
            : batch.getModelStatus().startsWith("SUCCESS") ? "MODEL_RESULT" : "LOCAL_FACT_RESULT";
        return new ProjectRecordBatchResponse(
            batch.getId(), batch.getProjectId(), batch.getScanStartedAt(), batch.getScanFinishedAt(),
            batch.getFactOccurredFrom(), batch.getFactOccurredTo(), batch.getBranchName(), batch.getStatus().name(),
            batch.getScanType(), batch.getNewCommitCount(), batch.getChangedFileCount(), batch.getAgentResultCount(),
            batch.getFactCount(), batch.getAttentionCount(), batch.getModelStatus(), batch.getModelProvider(), resultSource,
            batch.getStatus() == com.projectflow.entity.ChangeBatchStatus.FAILED
        );
    }

    private ProjectFactHistoryStateResponse historyResponse(ProjectFactHistoryState state) {
        return new ProjectFactHistoryStateResponse(
            state.getProjectId(), state.getStatus().name(), state.getHeadSnapshotSha(), state.getHeadSnapshotSha(),
            state.getTotalCommitCount(), state.getCoveredCommitCount(), state.getRemainingCommitCount(),
            state.getLastProcessedCommitSha(), state.getCurrentChunk(), state.getCompletedChunkCount(), state.getLastBatchId(),
            state.getStartedAt(), state.getUpdatedAt(), state.getCompletedAt(), state.getErrorCode(), state.getErrorSummary()
        );
    }

    private void ownedProject(UUID userId, UUID projectId) {
        projectRepository.findByIdAndUserId(projectId, userId)
            .orElseThrow(() -> new AppException("PROJECT_NOT_FOUND", "项目不存在", HttpStatus.NOT_FOUND));
    }

    private int clampSize(int size) { return Math.max(1, Math.min(200, size <= 0 ? 20 : size)); }
}
