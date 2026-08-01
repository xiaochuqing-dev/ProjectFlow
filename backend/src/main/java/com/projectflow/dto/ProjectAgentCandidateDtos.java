package com.projectflow.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;

public final class ProjectAgentCandidateDtos {
    private ProjectAgentCandidateDtos() {
    }

    public record SubmitAgentCandidateRequest(
        @NotBlank @Size(max = 40) String candidateType,
        @NotBlank @Size(max = 4_000) String assertion,
        @NotBlank @Size(max = 30) String epistemicStatus,
        @Size(max = 50) List<@Size(max = 200) String> evidenceRefs,
        @Size(max = 30) String currentness,
        @Size(max = 180) String sourceRevision,
        @Size(max = 20) List<@Size(max = 300) String> limitations,
        @Size(max = 160) String sourceAgentId
    ) {
    }

    public record AgentCandidateResponse(
        UUID candidateId,
        UUID projectId,
        String candidateType,
        String assertion,
        String epistemicStatus,
        List<String> evidenceRefs,
        String currentness,
        String sourceRevision,
        List<String> limitations,
        String sourceAgentId,
        String validationStatus,
        Instant createdAt
    ) {
    }

    public record AgentCandidatePageResponse(
        List<AgentCandidateResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) {
    }

    public record AgentCandidateInput(
        @NotBlank @Size(max = 40) String candidateType,
        @NotBlank @Size(max = 4_000) String assertion,
        @NotBlank @Size(max = 30) String epistemicStatus,
        @Size(max = 50) List<@Size(max = 200) String> evidenceRefs,
        @Size(max = 20) List<@Size(max = 300) String> limitations
    ) {
    }

    public record SubmitAgentWorkResultRequest(
        @Size(max = 100) List<@Size(max = 300) String> changedFiles,
        @Size(max = 40) List<@Size(max = 1_000) String> claimedBehaviors,
        @Size(max = 60) List<@Size(max = 1_000) String> executedCommands,
        @Size(max = 60) List<@Size(max = 1_000) String> testResults,
        @Size(max = 30) List<@Size(max = 200) String> commitRefs,
        @Size(max = 30) List<@Size(max = 300) String> pullRequestRefs,
        @Size(max = 30) List<@Size(max = 500) String> knownLimitations,
        @Size(max = 30) List<@Size(max = 500) String> unresolvedItems,
        @Size(max = 80) List<@Size(max = 200) String> evidenceRefs,
        @Size(max = 40) List<@Valid AgentCandidateInput> candidateFacts,
        @Size(max = 30) List<@Size(max = 1_000) String> candidateConflicts,
        @Size(max = 30) List<@Size(max = 1_000) String> candidateUnknownResolutions,
        @Size(max = 180) String sourceRevision,
        @Size(max = 160) String sourceAgentId
    ) {
    }

    public record AgentWorkResultResponse(
        UUID projectId,
        UUID workResultCandidateId,
        List<String> changedFiles,
        List<String> rereadEvidenceRefs,
        String sourceRevision,
        String validationStatus,
        List<AgentCandidateResponse> candidates,
        List<String> limitations,
        Instant createdAt
    ) {
    }
}
