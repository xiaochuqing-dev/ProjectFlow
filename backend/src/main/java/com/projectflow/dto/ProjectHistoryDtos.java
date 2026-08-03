package com.projectflow.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ProjectHistoryDtos {
    private ProjectHistoryDtos() {
    }

    public record HistoryRefreshRequest(Boolean force) {
        public boolean forceRequested() { return Boolean.TRUE.equals(force); }
    }

    public record HistoryCoverage(
        boolean complete,
        String currentness,
        int discoveredEventCount,
        int currentEventCount,
        int staleEventCount,
        int invalidatedEventCount,
        Map<String, Integer> sourceCounts,
        List<String> gaps,
        List<String> limitations
    ) {
    }

    public record HistoryOverviewContent(
        String earliestConfirmedState,
        String currentState,
        List<HistoryChapterSummary> chapters,
        List<String> recentChanges,
        List<String> conflicts,
        List<String> unknowns
    ) {
    }

    public record HistoryChapterSummary(
        String id,
        String title,
        String summary,
        Instant from,
        Instant to,
        int storyCount,
        int rawEventCount,
        String authority
    ) {
    }

    public record HistoryChapter(
        String id,
        String title,
        String summary,
        Instant from,
        Instant to,
        List<String> boundarySignals,
        List<String> storyRefs,
        int storyCount,
        int rawEventCount,
        String authority,
        String coverage,
        List<String> limitations
    ) {
    }

    public record ChangeStory(
        String id,
        String primarySubjectKey,
        String humanTitle,
        String oneSentenceSummary,
        String beforeState,
        String change,
        String afterState,
        List<String> affectedAreas,
        String reason,
        List<String> reasonEvidenceRefs,
        String laterOutcome,
        List<String> conflicts,
        List<String> unknowns,
        Instant occurredFrom,
        Instant occurredTo,
        int evidenceCount,
        int rawEventCount,
        String authority,
        String summaryStatus,
        String coverage,
        List<String> limitations,
        List<UUID> eventRefs,
        List<String> evidenceRefs
    ) {
    }

    public record EvolutionThread(
        String id,
        String subjectKey,
        String subjectLabel,
        String subjectType,
        List<String> storyRefs,
        List<String> transitions,
        String currentOutcome,
        List<String> gaps,
        List<String> conflicts,
        List<String> unknowns,
        int evidenceCount,
        UUID capabilityId
    ) {
    }

    public record HistoryOverviewResponse(
        UUID projectId,
        String status,
        String projectRevision,
        int sourceEventCount,
        Instant earliestEventAt,
        Instant latestEventAt,
        String strategyVersion,
        String promptVersion,
        HistoryOverviewContent overview,
        HistoryCoverage coverage,
        Map<String, Object> diagnostics,
        UUID analysisJobId,
        Instant generatedAt,
        Instant latestSuccessfulAt,
        Instant updatedAt,
        String errorCode,
        String errorSummary
    ) {
    }

    public record HistoryChapterPageResponse(
        UUID projectId,
        List<HistoryChapter> items,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) {
    }

    public record HistoryChapterDetailResponse(
        UUID projectId,
        HistoryChapter chapter,
        List<ChangeStory> stories
    ) {
    }

    public record HistoryStoryPageResponse(
        UUID projectId,
        List<ChangeStory> items,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) {
    }

    public record HistoryStoryDetailResponse(
        UUID projectId,
        ChangeStory story,
        List<HistoryEventResponse> events,
        List<EvolutionThread> threads
    ) {
    }

    public record EvolutionThreadPageResponse(
        UUID projectId,
        List<EvolutionThread> items,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) {
    }

    public record EvolutionThreadDetailResponse(
        UUID projectId,
        EvolutionThread thread,
        List<ChangeStory> stories
    ) {
    }

    public record HistoryEventResponse(
        UUID id,
        UUID projectId,
        String stableEventKey,
        String sourceType,
        String sourceIdentity,
        String sourceRevision,
        String projectRevision,
        Instant occurredAt,
        Instant effectiveAt,
        String actorLabel,
        String scope,
        String category,
        String transition,
        String safeSourceLabel,
        List<String> affectedPaths,
        List<String> subjectKeys,
        List<String> evidenceRefs,
        List<String> relationRefs,
        String authority,
        String epistemicStatus,
        Map<String, Object> coverage,
        List<String> limitations,
        String rawSourceDeepLink,
        String rewriteState,
        Instant updatedAt
    ) {
    }

    public record HistoryEventPageResponse(
        UUID projectId,
        List<HistoryEventResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) {
    }

    public record HistoryEvidenceItem(
        String type,
        String reference,
        String label,
        String currentness,
        String revision,
        String validation,
        String coverage,
        List<String> limitations,
        String deepLink
    ) {
    }

    public record HistoryEvidenceResponse(
        UUID projectId,
        UUID eventId,
        List<HistoryEvidenceItem> items,
        boolean truncated
    ) {
    }

    public record HistoryFiltersResponse(
        List<String> sourceTypes,
        List<String> categories,
        List<String> transitions,
        List<String> authorities,
        List<String> epistemicStatuses,
        List<String> rewriteStates
    ) {
    }
}
