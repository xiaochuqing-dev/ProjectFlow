package com.projectflow.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class V33WorkflowDtos {
    private V33WorkflowDtos() {
    }

    public record ChangeBatchResponse(
        UUID id,
        UUID projectId,
        Instant scanStartedAt,
        Instant scanFinishedAt,
        String baseCommitSha,
        String headCommitSha,
        String branchName,
        int newCommitCount,
        int changedFileCount,
        int agentResultCount,
        int segmentCount,
        String status,
        List<String> warnings,
        boolean firstScan
    ) {
    }

    public record DevelopmentSegmentResponse(
        UUID id,
        UUID projectId,
        UUID batchId,
        String title,
        String plainSummary,
        List<String> mainChanges,
        String userVisibleValue,
        List<String> includedCommitRefs,
        List<String> includedAgentResultRefs,
        List<String> affectedFiles,
        List<String> evidenceRefs,
        String confidence,
        String status,
        Instant createdAt,
        Instant updatedAt
    ) {
    }
}
