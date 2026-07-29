package com.projectflow.eval;

import java.time.Instant;
import java.util.List;

record ProjectFlowEvalObservation(
    String caseId,
    String runId,
    String promptVersion,
    String codeVersion,
    String projectRevision,
    int runIndex,
    String provider,
    String protocol,
    String model,
    Instant timestamp,
    List<String> projectShapes,
    List<String> evidenceUsed,
    List<String> toolPlan,
    List<String> unavailableToolRequests,
    List<String> rejectedDangerousToolRequests,
    List<String> deepReadTargets,
    List<String> applicableViews,
    List<String> conflictsDetected,
    List<String> unknowns,
    List<EvalClaim> claims,
    List<String> unsupportedClaims,
    List<String> mustNotClaimViolations,
    List<String> expectedViewMatches,
    StageResult stageOne,
    StageResult stageTwo,
    int requestCount,
    int inputTokens,
    int outputTokens,
    int totalTokens,
    int stageOneInputTokens,
    int stageOneOutputTokens,
    int stageTwoInputTokens,
    int stageTwoOutputTokens,
    int toolEvidenceChars,
    boolean validatedToolEvidenceCited,
    long latencyMs,
    int retries,
    boolean failed,
    boolean degraded,
    String finishReason,
    String finalStatus,
    String degradationStatus,
    Double estimatedCost,
    String priceSourceDate
) {
    record EvalClaim(
        String text,
        String claimType,
        String epistemicStatus,
        List<String> evidenceRefs,
        boolean manualReviewRequired,
        boolean manuallyUnsupported
    ) {
    }

    record StageResult(
        List<String> evidenceIds,
        List<EvalClaim> claims,
        List<String> applicableViews
    ) {
    }
}
