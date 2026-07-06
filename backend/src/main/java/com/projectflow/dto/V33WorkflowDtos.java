package com.projectflow.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.projectflow.entity.SedimentAction;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

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

    public record SedimentConfirmRequest(
        @NotNull SedimentAction action,
        UUID targetSedimentId
    ) {
    }

    public record ProjectSedimentPatchRequest(
        @Size(max = 20_000) String developerNotes
    ) {
    }

    public record ProjectSedimentResponse(
        UUID id,
        UUID projectId,
        String title,
        String summary,
        String problemSolved,
        String sedimentType,
        String status,
        List<String> sourceSegmentIds,
        List<String> evidenceRefs,
        String developerNotes,
        Instant createdAt,
        Instant updatedAt
    ) {
    }

    public record SedimentConfirmationResponse(
        UUID changeId,
        String changeStatus,
        ProjectSedimentResponse sediment,
        String batchStatus
    ) {
    }

    public record AgentBridgeHealthResponse(
        boolean pathAccessible,
        boolean sameGitRepository,
        boolean protocolExists,
        boolean resultsDirectoryExists,
        boolean agentsFileExists,
        boolean entryRulePresent,
        String protocolVersion,
        List<String> detectedRuleFiles,
        List<String> warnings
    ) {
    }
}
