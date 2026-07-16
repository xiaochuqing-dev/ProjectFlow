package com.projectflow.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ProjectCapabilityDtos {
    private ProjectCapabilityDtos() {
    }

    public record CoverageStatusResponse(String status, int coveredCount, int totalCount, int remainingCount) {
    }

    public record CapabilityMapOverviewResponse(
        UUID projectId, long capabilityCount, long activeCount, long mergedCount,
        Map<String, Long> maturityDistribution, int sourceFactCount, int coveredFactCount,
        int assignedFactCount, int noCapabilityChangeFactCount, int unassignedFactCount,
        long attentionCount, String sourceFactFingerprint, String mapStatus, boolean stale,
        Instant latestSuccessfulAt, Instant latestAttemptAt, String errorCode, String errorSummary,
        CoverageStatusResponse historyCoverage, CoverageStatusResponse timelineCoverage
    ) {
    }

    public record CapabilityListItemResponse(
        UUID id, UUID projectId, String name, String summary, String status, String maturity,
        String maturityReason, Instant firstFormedAt, Instant lastEnhancedAt, int factCount,
        int batchCount, int commitCount, int evolutionCount, int attentionCount, boolean stale,
        UUID mergedIntoCapabilityId
    ) {
    }

    public record CapabilityPageResponse(
        List<CapabilityListItemResponse> items, int page, int size, long totalElements, int totalPages
    ) {
    }

    public record CapabilityEvolutionResponse(
        UUID id, UUID capabilityId, String type, int versionBefore, int versionAfter,
        String title, String summary, Instant occurredAt, int sourceFactCount, int sourceBatchCount,
        List<String> sourceTimelinePeriods, UUID analysisJobId, UUID mergedFromCapabilityId
    ) {
    }

    public record CapabilityEvolutionPageResponse(
        List<CapabilityEvolutionResponse> items, int page, int size, long totalElements, int totalPages
    ) {
    }

    public record CapabilityFactResponse(
        UUID factId, UUID projectId, UUID batchId, String title, String summary,
        Instant occurredFrom, Instant occurredTo, String recordStatus, String attentionReason,
        int commitCount, int affectedFileCount, int evidenceCount, String relationRole,
        UUID sourceEvolutionId, Instant linkedAt
    ) {
    }

    public record CapabilityFactPageResponse(
        List<CapabilityFactResponse> items, int page, int size, long totalElements, int totalPages
    ) {
    }

    public record CapabilityDetailResponse(
        UUID id, UUID projectId, String name, List<String> aliases, String summary,
        String problemSolved, String longTermValue, List<String> productAreas, String status,
        String maturity, String maturityReason, Instant firstFormedAt, Instant lastEnhancedAt,
        int factCount, int batchCount, int commitCount, int evidenceCount, int attentionCount,
        int evolutionCount, int currentVersion, String generationMode, UUID mergedIntoCapabilityId,
        String readmeExpression, String resumeExpression, String interviewExpression,
        CapabilityEvolutionPageResponse evolutions, CapabilityFactPageResponse recentFacts,
        List<CapabilityListItemResponse> mergedHistory, boolean stale
    ) {
    }

    public record CapabilityAttentionResponse(
        UUID id, String type, String reason, UUID factId, UUID sourceCapabilityId,
        UUID targetCapabilityId, String status, Instant createdAt
    ) {
    }

    public record CapabilityAttentionPageResponse(
        List<CapabilityAttentionResponse> items, int page, int size, long totalElements, int totalPages
    ) {
    }
}
