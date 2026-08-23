package com.projectflow.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.projectflow.dto.ProjectHistoryDtos.ProjectCurrentStateResponse;

public final class ProjectAgentHistoryDtos {
    private ProjectAgentHistoryDtos() {
    }

    public record AgentProjectCatalogItem(
        UUID projectId,
        String name,
        String status,
        String safeSourceId,
        UUID latestSnapshotId,
        String latestRevision,
        Instant lastAnalyzedAt,
        Instant historyFrom,
        Instant historyTo,
        double evidenceCoverage,
        long strongFactCount,
        long candidateCount,
        int unknownCount,
        int conflictCount
    ) {
    }

    public record AgentProjectCatalogResponse(
        List<AgentProjectCatalogItem> items,
        int total,
        Instant generatedAt
    ) {
    }

    public record PortfolioSearchItem(
        UUID projectId,
        String projectName,
        String entityType,
        UUID entityId,
        String title,
        String summary,
        String status,
        String currentness,
        String source,
        Instant occurredAt
    ) {
    }

    public record PortfolioSearchResponse(
        String query,
        List<PortfolioSearchItem> items,
        int searchedProjectCount,
        boolean truncated
    ) {
    }

    public record AgentEvidenceResponse(
        UUID projectId,
        String evidenceId,
        String category,
        String sourceType,
        String locator,
        String semanticRole,
        String importance,
        String currentness,
        String confidence,
        String deepReadStatus,
        String summary,
        List<String> evidenceRefs,
        String sourceRevision
    ) {
    }

    public record AgentKnowledgeItem(
        UUID projectId,
        String itemId,
        String statement,
        String epistemicStatus,
        String currentness,
        String sourceRevision,
        String validationStatus,
        List<String> evidenceRefs,
        List<String> limitations,
        Instant effectiveAt
    ) {
    }

    public record AgentKnowledgeResponse(
        UUID projectId,
        List<AgentKnowledgeItem> items,
        int strongFactCount,
        int declaredCount,
        int inferredCount,
        int conflictCount,
        int unknownCount,
        int processEvidenceCount,
        boolean truncated
    ) {
    }

    public record AgentContextPackageResponse(
        String packageVersion,
        String packageRevision,
        UUID projectId,
        String projectName,
        String projectStatus,
        String sourceRevision,
        Instant generatedAt,
        String taskDescription,
        List<String> requestedScope,
        String revisionPreference,
        String requestedEvidenceDepth,
        List<AgentKnowledgeItem> currentStrongFacts,
        List<AgentKnowledgeItem> declaredMaterial,
        List<AgentKnowledgeItem> inferredCandidates,
        List<AgentKnowledgeItem> conflicts,
        List<AgentKnowledgeItem> unknowns,
        List<AgentEvidenceResponse> keyEvidence,
        List<AgentKnowledgeItem> latestVerifiedChanges,
        List<AgentKnowledgeItem> relevantHistoricalRecords,
        List<AgentSourceRangeResponse> relatedRanges,
        AgentTrustGuidanceResponse trustGuidance,
        String historicalCoverage,
        ProjectCurrentStateResponse currentProjectState,
        AgentCoverageDisclosureResponse coverageDisclosure,
        List<String> unreadScope,
        List<String> limitations,
        List<String> suggestedDeepReadTargets,
        List<String> provenance,
        AgentContextGenerationMetadata generationMetadata,
        int sizeBudget,
        int actualCharacters,
        boolean truncated
    ) {
    }

    public record AgentSourceRangeResponse(
        String evidenceId,
        String locator,
        String rangeKind,
        long startLine,
        long endLine,
        long startByte,
        long endByte,
        String sourceRevision,
        String currentness,
        String validationHint
    ) {
    }

    public record AgentTrustGuidanceResponse(
        List<String> generallyReusableItemIds,
        List<String> quickVerifyItemIds,
        List<String> mustRevalidateItemIds,
        List<String> rules
    ) {
    }

    public record AgentCoverageDisclosureResponse(
        String mode,
        int matchedKnowledgeCount,
        int availableKnowledgeCount,
        int matchedEvidenceCount,
        int availableEvidenceCount,
        int matchedRangeCount,
        boolean partial,
        String semanticContractStatus
    ) {
    }

    public record AgentContextGenerationMetadata(
        String retrievalMode,
        boolean modelCalled,
        String packageRevisionAlgorithm,
        String revisionValidation,
        String evidenceDepth,
        Instant generatedAt
    ) {
    }
}
