package com.projectflow.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.projectflow.dto.ProjectAgentHistoryDtos.AgentContextPackageResponse;
import com.projectflow.dto.ProjectAgentHistoryDtos.AgentEvidenceResponse;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class ProjectAgentRevalidationDtos {
    private ProjectAgentRevalidationDtos() {
    }

    public record AgentRevalidationRequest(
        @NotBlank @Size(max = 40) String action,
        @Size(max = 200) String targetId,
        @Size(max = 200) String evidenceId,
        Long startLine,
        Long endLine,
        Integer maxChars,
        @Size(max = 1_000) String taskDescription,
        @Size(max = 20) List<@Size(max = 200) String> scope,
        @Size(max = 40) String revisionPreference,
        @Size(max = 20) String evidenceDepth,
        Integer sizeBudget
    ) {
    }

    public record RevalidatedRangeResponse(
        String evidenceId,
        String locator,
        String kind,
        long startLine,
        long endLine,
        long startByte,
        long endByte,
        String sourceHash,
        boolean truncated,
        String text
    ) {
    }

    public record AgentRevalidationResponse(
        UUID projectId,
        String action,
        String targetId,
        String status,
        String sourceRevisionBefore,
        String currentSourceRevision,
        String currentness,
        String validationStatus,
        List<String> verifiedEvidenceRefs,
        AgentEvidenceResponse evidence,
        RevalidatedRangeResponse range,
        AgentContextPackageResponse resolvedPackage,
        List<String> limitations,
        Instant checkedAt
    ) {
    }
}
