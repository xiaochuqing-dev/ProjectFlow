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
        List<String> limitations,
        String presentationAuthority,
        String presentationRevision,
        boolean userDeclared,
        List<String> userCorrectionRefs,
        boolean hiddenByDefault,
        boolean pinned
    ) {
        public HistoryChapter(
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
            this(id, title, summary, from, to, boundarySignals, storyRefs, storyCount, rawEventCount,
                authority, coverage, limitations, "AUTOMATIC", "", false, List.of(), false, false);
        }

        public HistoryChapter {
            boundarySignals = immutable(boundarySignals);
            storyRefs = immutable(storyRefs);
            limitations = immutable(limitations);
            userCorrectionRefs = immutable(userCorrectionRefs);
            presentationAuthority = text(presentationAuthority, "AUTOMATIC");
            presentationRevision = text(presentationRevision, "");
        }

        public boolean declared() { return userDeclared; }

        private static String text(String value, String fallback) {
            return value == null || value.isBlank() ? fallback : value.trim();
        }

        private static List<String> immutable(List<String> values) {
            return values == null ? List.of() : List.copyOf(values);
        }
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
        List<String> evidenceRefs,
        String role,
        String primaryStoryId,
        List<String> supportingChangeRefs,
        List<String> technicalAtomRefs,
        List<String> commitSummaries,
        List<String> technicalDetails,
        String presentationAuthority,
        String presentationRevision,
        String automaticTitle,
        String automaticSummary,
        List<String> userCorrectionRefs,
        boolean hiddenByDefault,
        boolean pinned,
        String mergedIntoStoryId,
        String displayStatus,
        List<String> correctionConflicts
    ) {
        public ChangeStory(
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
            this(id, primarySubjectKey, humanTitle, oneSentenceSummary, beforeState, change, afterState, affectedAreas,
                reason, reasonEvidenceRefs, laterOutcome, conflicts, unknowns, occurredFrom, occurredTo, evidenceCount,
                rawEventCount, authority, summaryStatus, coverage, limitations, eventRefs, evidenceRefs, "PRIMARY", "",
                List.of(), List.of(), List.of(), List.of(), "AUTOMATIC", "", humanTitle, oneSentenceSummary, List.of(),
                false, false, "", "ACTIVE", List.of());
        }

        public ChangeStory {
            affectedAreas = immutable(affectedAreas);
            reasonEvidenceRefs = immutable(reasonEvidenceRefs);
            conflicts = immutable(conflicts);
            unknowns = immutable(unknowns);
            limitations = immutable(limitations);
            eventRefs = eventRefs == null ? List.of() : List.copyOf(eventRefs);
            evidenceRefs = immutable(evidenceRefs);
            supportingChangeRefs = immutable(supportingChangeRefs);
            technicalAtomRefs = immutable(technicalAtomRefs);
            commitSummaries = immutable(commitSummaries);
            technicalDetails = immutable(technicalDetails);
            userCorrectionRefs = immutable(userCorrectionRefs);
            correctionConflicts = immutable(correctionConflicts);
            role = role == null || role.isBlank() ? "PRIMARY" : role.trim().toUpperCase(java.util.Locale.ROOT);
            primaryStoryId = primaryStoryId == null ? "" : primaryStoryId.trim();
            presentationAuthority = text(presentationAuthority, "AUTOMATIC");
            presentationRevision = text(presentationRevision, "");
            automaticTitle = text(automaticTitle, humanTitle);
            automaticSummary = text(automaticSummary, oneSentenceSummary);
            mergedIntoStoryId = mergedIntoStoryId == null ? "" : mergedIntoStoryId.trim();
            displayStatus = text(displayStatus, "ACTIVE");
        }

        public boolean primary() { return "PRIMARY".equals(role); }
        public boolean supporting() { return "SUPPORTING".equals(role); }
        public boolean userDeclared() { return "USER_DECLARED_PRESENTATION".equals(presentationAuthority); }

        private static String text(String value, String fallback) {
            return value == null || value.isBlank() ? fallback : value.trim();
        }

        private static <T> List<T> immutable(List<T> values) {
            return values == null ? List.of() : List.copyOf(values);
        }
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
        UUID capabilityId,
        String presentationAuthority,
        String presentationRevision,
        List<String> userCorrectionRefs
    ) {
        public EvolutionThread(
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
            this(id, subjectKey, subjectLabel, subjectType, storyRefs, transitions, currentOutcome, gaps, conflicts,
                unknowns, evidenceCount, capabilityId, "AUTOMATIC", "", List.of());
        }

        public EvolutionThread {
            storyRefs = storyRefs == null ? List.of() : List.copyOf(storyRefs);
            transitions = transitions == null ? List.of() : List.copyOf(transitions);
            gaps = gaps == null ? List.of() : List.copyOf(gaps);
            conflicts = conflicts == null ? List.of() : List.copyOf(conflicts);
            unknowns = unknowns == null ? List.of() : List.copyOf(unknowns);
            userCorrectionRefs = userCorrectionRefs == null ? List.of() : List.copyOf(userCorrectionRefs);
            presentationAuthority = presentationAuthority == null || presentationAuthority.isBlank()
                ? "AUTOMATIC" : presentationAuthority.trim();
            presentationRevision = presentationRevision == null ? "" : presentationRevision.trim();
        }
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

    /** A presentation-only correction. It never writes ProjectFact, Event, or Evidence. */
    public record HistoryCorrectionRequest(
        String type,
        String targetType,
        String targetId,
        List<String> targetIds,
        String title,
        String summary,
        String role,
        String chapterId,
        String expectedPresentationRevision,
        String sourceFingerprint,
        String declaredTitle,
        String declaredSummary,
        String declaredRole,
        String declaredChapterId
    ) {
        public HistoryCorrectionRequest(
            String type,
            String targetType,
            String targetId,
            List<String> targetIds,
            String title,
            String summary,
            String role,
            String chapterId,
            String expectedPresentationRevision,
            String sourceFingerprint
        ) {
            this(type, targetType, targetId, targetIds, title, summary, role, chapterId,
                expectedPresentationRevision, sourceFingerprint, "", "", "", "");
        }

        public List<String> safeTargetIds() {
            return targetIds == null ? List.of() : targetIds.stream().filter(value -> value != null && !value.isBlank()).distinct().limit(100).toList();
        }

        public String effectiveTitle() { return firstNonBlank(declaredTitle, title); }
        public String effectiveSummary() { return firstNonBlank(declaredSummary, summary); }
        public String effectiveRole() { return firstNonBlank(declaredRole, role); }
        public String effectiveChapterId() { return firstNonBlank(declaredChapterId, chapterId); }

        private static String firstNonBlank(String preferred, String fallback) {
            return preferred != null && !preferred.isBlank() ? preferred.trim() : fallback == null ? "" : fallback.trim();
        }
    }

    public record HistoryCorrectionResponse(
        UUID id,
        UUID projectId,
        String type,
        String targetType,
        String targetId,
        List<String> targetIds,
        String status,
        String beforePresentationRevision,
        String sourceFingerprint,
        String conflictReason,
        Instant createdAt,
        Instant updatedAt,
        String presentationRevision,
        String declaredTitle,
        String declaredSummary,
        String declaredRole,
        String declaredChapterId,
        String automaticValue,
        String appliedValue,
        String difference,
        boolean targetPresent
    ) {
        public HistoryCorrectionResponse(
            UUID id,
            UUID projectId,
            String type,
            String targetType,
            String targetId,
            List<String> targetIds,
            String status,
            String beforePresentationRevision,
            String sourceFingerprint,
            String conflictReason,
            Instant createdAt,
            Instant updatedAt,
            String presentationRevision
        ) {
            this(id, projectId, type, targetType, targetId, targetIds, status, beforePresentationRevision,
                sourceFingerprint, conflictReason, createdAt, updatedAt, presentationRevision, "", "", "", "", "", "", "", false);
        }
    }

    public record HistoryCorrectionListResponse(
        UUID projectId,
        List<HistoryCorrectionResponse> items,
        String presentationRevision,
        boolean truncated
    ) {
    }
}
