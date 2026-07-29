package com.projectflow.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.projectflow.dto.ProjectTimelineDtos.HistoryCoverageResponse;
import com.projectflow.dto.ProjectTimelineDtos.TimelineStatsResponse;

public final class ProjectMemoryGatewayDtos {
    private ProjectMemoryGatewayDtos() {
    }

    public record MemoryProjectResponse(
        UUID projectId, String name, String summary, String status, List<String> techStack,
        Instant updatedAt
    ) {
    }

    public record MemoryProjectListResponse(List<MemoryProjectResponse> items, int total) {
    }

    public record MemoryTimeResponse(
        Instant occurredFrom, Instant occurredTo, Instant eventAt,
        Instant recordedAt, Instant analyzedAt, Instant syncedAt
    ) {
    }

    public record MemoryFactResponse(
        UUID factId, String title, String summary, MemoryTimeResponse time, UUID batchId,
        int commitCount, int fileCount, int agentResultCount, int evidenceCount,
        String recordStatus, String qualityStatus, String attentionReason,
        List<UUID> relatedCapabilityIds, String truthLayer, String traceHint,
        String epistemicStatus, String currentness, String revision,
        String validationStatus, List<String> limitations
    ) {
    }

    public record MemoryFactPageResponse(
        List<MemoryFactResponse> items, int page, int size, long totalElements,
        int totalPages, boolean hasMore
    ) {
    }

    public record MemoryCapabilityResponse(
        UUID capabilityId, String canonicalName, List<String> aliases, String summary,
        String problemSolved, String longTermValue, List<String> productAreas,
        String status, String maturity, String maturityReason, Instant firstFormedAt,
        Instant lastEnhancedAt, int factCount, int batchCount, int commitCount,
        int evidenceCount, int evolutionCount, int attentionCount, int currentVersion,
        UUID mergedIntoCapabilityId, boolean stale, Instant sourceUpdatedAt,
        String readmeExpression, String resumeExpression, String interviewExpression,
        String truthLayer
    ) {
    }

    public record MemoryCapabilityPageResponse(
        List<MemoryCapabilityResponse> items, int page, int size, long totalElements,
        int totalPages, boolean hasMore
    ) {
    }

    public record MemoryEvolutionResponse(
        UUID evolutionId, UUID capabilityId, String capabilityName, String type,
        int versionBefore, int versionAfter, String title, String summary,
        Instant occurredAt, int sourceFactCount, int sourceBatchCount,
        List<String> sourcePeriods, List<UUID> sourceFactIds,
        UUID mergedFromCapabilityId, String truthLayer
    ) {
    }

    public record MemoryEvolutionPageResponse(
        List<MemoryEvolutionResponse> items, int page, int size, long totalElements,
        int totalPages, boolean hasMore
    ) {
    }

    public record MemoryTimelineSummaryResponse(
        String status, String summary, int sourceFactCount, int coveredFactCount,
        boolean stale, int generationVersion, Instant generatedAt, String notice
    ) {
    }

    public record MemoryTimelineThemeResponse(
        UUID themeId, String title, String summary, long factCount
    ) {
    }

    public record MemoryTimelinePeriodResponse(
        String periodKey, Instant periodStart, Instant periodEnd,
        TimelineStatsResponse stats, MemoryTimelineSummaryResponse summary,
        long themeCount
    ) {
    }

    public record MemoryTimelinePeriodPageResponse(
        List<MemoryTimelinePeriodResponse> items, int page, int size,
        long totalElements, int totalPages, boolean hasMore
    ) {
    }

    public record MemoryTimelineDetailResponse(
        String periodKey, Instant periodStart, Instant periodEnd,
        TimelineStatsResponse stats, MemoryTimelineSummaryResponse summary,
        List<MemoryTimelineThemeResponse> themes, int sourceFactCount,
        int coveredFactCount, MemoryFactPageResponse facts,
        HistoryCoverageResponse history
    ) {
    }

    public record MemoryTimelineLifecycleResponse(
        Instant earliestFactAt, Instant latestFactAt, TimelineStatsResponse stats,
        MemoryTimelineSummaryResponse summary, List<MemoryTimelineThemeResponse> stages,
        List<MemoryTimelinePeriodResponse> months, int sourceFactCount,
        int coveredFactCount, HistoryCoverageResponse history
    ) {
    }

    public record MemoryTimelineQueryResponse(
        UUID projectId, String timelineZone, String granularity, String mode,
        MemoryTimelinePeriodPageResponse periods, MemoryTimelineDetailResponse period,
        MemoryTimelineLifecycleResponse lifecycle
    ) {
    }

    public record MemorySearchResultResponse(
        String entityType, UUID entityId, String title, String summary,
        Instant occurredAt, Instant periodStart, Instant periodEnd,
        String relevanceReason, List<String> matchedFields, List<UUID> relatedIds,
        String truthLayer, String traceHint
    ) {
    }

    public record MemorySearchResponse(
        UUID projectId, List<String> entityTypes,
        List<MemorySearchResultResponse> items, int page, int size,
        long totalElements, int totalPages, boolean hasMore
    ) {
    }

    public record MemoryFactTraceResponse(
        UUID projectId, UUID factId, String title, String summary,
        MemoryTimeResponse time, String recordStatus, String attentionReason,
        UUID batchId, String branch, String batchType,
        List<String> commits, List<String> files, List<String> agentResults,
        List<String> evidence, List<UUID> relatedCapabilityIds,
        boolean detailTruncated, String truthLayer
    ) {
    }

    public record MemoryHealthResponse(
        String historyStatus, String timelineStatus, String capabilityMapStatus,
        boolean capabilityMapStale, Instant latestRealChangeAt,
        Instant latestAnalysisAt, Instant generatedAt, List<String> warnings
    ) {
    }

    public record ProjectSnapshotResponse(
        MemoryProjectResponse project, String branch, String currentVersion,
        long factCount, long recordedFactCount, long attentionFactCount,
        long coveredCommitCount, long totalCommitCount,
        Instant earliestFactAt, Instant latestFactAt, String recentChangeSummary,
        MemoryFactPageResponse recentChanges, MemoryTimelinePeriodResponse latestPeriod,
        MemoryTimelineSummaryResponse lifecycleSummary, long activeCapabilityCount,
        List<MemoryCapabilityResponse> representativeCapabilities,
        List<MemoryEvolutionResponse> recentEvolutions,
        long capabilityAttentionCount, MemoryHealthResponse health
    ) {
    }

    public record MemoryBriefResponse(
        UUID projectId, String contextText, int sizeBudget, int actualCharacters,
        boolean truncated, List<String> warnings, Instant generatedAt
    ) {
    }

    public record MemoryTraceabilityHint(String endpoint, Map<String, String> parameters) {
    }
}
