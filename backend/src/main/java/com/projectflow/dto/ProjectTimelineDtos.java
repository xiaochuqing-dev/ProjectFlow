package com.projectflow.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.projectflow.dto.ProjectFactDtos.ProjectFactPageResponse;

public final class ProjectTimelineDtos {
    private ProjectTimelineDtos() {
    }

    public record CommitCoverageResponse(long coveredCommitCount, long totalCommitCount) {
    }

    public record HistoryCoverageResponse(
        String status,
        int coveredCommitCount,
        int totalCommitCount,
        int remainingCommitCount,
        String notice
    ) {
    }

    public record TimelineOverviewResponse(
        UUID projectId,
        String timelineZone,
        Instant earliestFactAt,
        Instant latestFactAt,
        long factCount,
        long batchCount,
        CommitCoverageResponse commitCoverage,
        HistoryCoverageResponse history,
        long dirtyPeriodCount,
        String latestSummaryStatus
    ) {
    }

    public record TimelineStatsResponse(
        long factCount,
        long batchCount,
        long commitCount,
        long fileCount,
        long agentResultCount,
        long attentionCount,
        Instant earliestEventAt,
        Instant latestEventAt
    ) {
    }

    public record TimelineSummaryResponse(
        UUID id,
        String granularity,
        String periodKey,
        String status,
        String summary,
        int sourceFactCount,
        int coveredFactCount,
        boolean stale,
        int generationVersion,
        UUID analysisJobId,
        String errorCode,
        String errorSummary,
        Instant generatedAt,
        Instant updatedAt
    ) {
    }

    public record TimelineThemeResponse(
        UUID id,
        String title,
        String summary,
        int sortOrder,
        long factCount
    ) {
    }

    public record TimelinePeriodResponse(
        String periodKey,
        Instant periodStart,
        Instant periodEnd,
        TimelineStatsResponse stats,
        String summaryStatus,
        String summaryPreview,
        boolean summaryStale,
        long themeCount
    ) {
    }

    public record TimelinePeriodPageResponse(
        UUID projectId,
        String timelineZone,
        String granularity,
        List<TimelinePeriodResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) {
    }

    public record TimelinePeriodDetailResponse(
        UUID projectId,
        String timelineZone,
        String granularity,
        String periodKey,
        Instant periodStart,
        Instant periodEnd,
        TimelineStatsResponse stats,
        TimelineSummaryResponse currentSummary,
        List<TimelineThemeResponse> themes,
        int sourceFactCount,
        int coveredFactCount,
        ProjectFactPageResponse facts,
        HistoryCoverageResponse history
    ) {
    }

    public record TimelineThemeFactsResponse(
        UUID projectId,
        UUID themeId,
        String title,
        ProjectFactPageResponse facts
    ) {
    }

    public record TimelineLifecycleResponse(
        UUID projectId,
        String timelineZone,
        Instant earliestFactAt,
        Instant latestFactAt,
        TimelineStatsResponse stats,
        TimelineSummaryResponse currentSummary,
        List<TimelineThemeResponse> stages,
        List<TimelinePeriodResponse> months,
        int sourceFactCount,
        int coveredFactCount,
        HistoryCoverageResponse history
    ) {
    }

    public record TimelineRetryRequest(String granularity, String periodKey) {
    }
}
