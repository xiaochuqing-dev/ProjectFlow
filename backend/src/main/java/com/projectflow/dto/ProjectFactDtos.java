package com.projectflow.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ProjectFactDtos {
    private ProjectFactDtos() {
    }

    public record ProjectFactSummaryResponse(
        UUID id,
        UUID projectId,
        UUID batchId,
        UUID sourceSegmentId,
        UUID legacySedimentId,
        String origin,
        String title,
        String summary,
        Instant occurredFrom,
        Instant occurredTo,
        String sourceMode,
        String qualityStatus,
        String confidence,
        String recordStatus,
        String attentionReason,
        int commitCount,
        int agentResultCount,
        int affectedFileCount,
        int evidenceCount,
        Instant createdAt,
        Instant updatedAt,
        String epistemicStatus,
        String currentness,
        String revision,
        String validationStatus,
        List<String> limitations
    ) {
    }

    public record ProjectFactDetailResponse(
        UUID id,
        UUID projectId,
        UUID batchId,
        UUID sourceSegmentId,
        UUID legacySedimentId,
        String origin,
        String title,
        String summary,
        Instant occurredFrom,
        Instant occurredTo,
        String sourceMode,
        String qualityStatus,
        String confidence,
        String recordStatus,
        String attentionReason,
        int commitCount,
        int agentResultCount,
        int affectedFileCount,
        int evidenceCount,
        Instant createdAt,
        Instant updatedAt,
        List<String> mainChanges,
        String userVisibleValue,
        List<String> commitRefs,
        List<String> commitUrls,
        List<String> agentResultRefs,
        List<String> affectedFiles,
        List<String> evidenceRefs,
        String factFingerprint,
        String statement,
        String epistemicStatus,
        List<String> sourceTypes,
        String currentness,
        String revision,
        Instant observedAt,
        Instant effectiveAt,
        UUID supersededBy,
        List<String> limitations,
        List<String> conflictRefs,
        String createdBy,
        String sourceAgentId,
        String sourceModelProvider,
        String validationStatus
    ) {
    }

    public record ProjectFactPageResponse(
        List<ProjectFactSummaryResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) {
    }

    public record ProjectRecordBatchResponse(
        UUID batchId,
        UUID projectId,
        Instant scanStartedAt,
        Instant scanFinishedAt,
        Instant factOccurredFrom,
        Instant factOccurredTo,
        String branchName,
        String batchStatus,
        String scanType,
        int commitCount,
        int changedFileCount,
        int agentResultCount,
        int factCount,
        int attentionCount,
        String modelStatus,
        String modelProvider,
        String resultSource,
        boolean needsReanalysis
    ) {
    }

    public record ProjectRecordBatchPageResponse(
        List<ProjectRecordBatchResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) {
    }

    public record ProjectRecordBatchDetailResponse(
        ProjectRecordBatchResponse batch,
        ProjectFactPageResponse facts
    ) {
    }

    public record FactMemoryOverviewResponse(
        UUID projectId,
        long totalFactCount,
        long recordedFactCount,
        long attentionFactCount,
        long coveredCommitCount,
        long totalCommitCount,
        Instant earliestOccurredAt,
        Instant latestOccurredAt
    ) {
    }

    public record ProjectFactHistoryStateResponse(
        UUID projectId,
        String status,
        String headSnapshotSha,
        String upperBoundSha,
        int totalCommitCount,
        int coveredCommitCount,
        int remainingCommitCount,
        String lastProcessedCommitSha,
        int currentChunk,
        int completedChunkCount,
        UUID lastBatchId,
        Instant startedAt,
        Instant updatedAt,
        Instant completedAt,
        String errorCode,
        String errorSummary
    ) {
    }
}
