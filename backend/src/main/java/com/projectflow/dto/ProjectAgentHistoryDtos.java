package com.projectflow.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
        UUID projectId,
        String projectName,
        String projectStatus,
        String sourceRevision,
        Instant generatedAt,
        List<AgentKnowledgeItem> currentStrongFacts,
        List<AgentKnowledgeItem> declaredMaterial,
        List<AgentKnowledgeItem> inferredCandidates,
        List<AgentKnowledgeItem> conflicts,
        List<AgentKnowledgeItem> unknowns,
        List<AgentEvidenceResponse> keyEvidence,
        List<AgentKnowledgeItem> latestVerifiedChanges,
        String historicalCoverage,
        List<String> limitations,
        List<String> provenance,
        int sizeBudget,
        int actualCharacters,
        boolean truncated
    ) {
    }
}
