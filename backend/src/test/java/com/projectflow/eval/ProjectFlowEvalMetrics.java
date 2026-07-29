package com.projectflow.eval;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.projectflow.eval.ProjectFlowEvalGroundTruth.EvalCase;
import com.projectflow.eval.ProjectFlowEvalObservation.EvalClaim;
import com.projectflow.eval.ProjectFlowEvalObservation.StageResult;

final class ProjectFlowEvalMetrics {
    private ProjectFlowEvalMetrics() {
    }

    static EvalSummary calculate(
        ProjectFlowEvalGroundTruth groundTruth,
        List<ProjectFlowEvalObservation> observations
    ) {
        Map<String, EvalCase> cases = new HashMap<>();
        groundTruth.cases().forEach(value -> cases.put(value.id(), value));
        Counter claims = new Counter();
        Counter evidence = new Counter();
        Counter shapes = new Counter();
        Counter tools = new Counter();
        Counter deepReads = new Counter();
        Counter views = new Counter();
        Counter conflicts = new Counter();
        int unavailableToolRequests = 0;
        int rejectedDangerousRequests = 0;
        int manualReviewClaims = 0;
        int failures = 0;
        int successes = 0;
        int timeouts = 0;
        int schemaFailures = 0;
        int cancellations = 0;
        int degraded = 0;
        long inputTokens = 0;
        long outputTokens = 0;
        long totalTokens = 0;
        long latencyMs = 0;
        int requests = 0;
        int retries = 0;
        List<Long> latencies = new ArrayList<>();
        List<ProjectFlowEvalObservation> semanticObservations = new ArrayList<>();
        List<Double> stageEvidenceGain = new ArrayList<>();
        List<Double> stageUnsupportedReduction = new ArrayList<>();
        List<Double> stageViewGain = new ArrayList<>();

        for (ProjectFlowEvalObservation observation : observations) {
            EvalCase expected = cases.get(observation.caseId());
            if (expected == null) continue;
            failures += observation.failed() ? 1 : 0;
            successes += observation.failed() ? 0 : 1;
            degraded += observation.degraded() ? 1 : 0;
            String failure = normalized(observation.finishReason());
            if (failure.contains("TIMEOUT")) timeouts++;
            if (failure.contains("SCHEMA")) schemaFailures++;
            if (failure.contains("CANCEL")) cancellations++;
            inputTokens += observation.inputTokens();
            outputTokens += observation.outputTokens();
            totalTokens += observation.totalTokens();
            latencyMs += observation.latencyMs();
            latencies.add(observation.latencyMs());
            requests += observation.requestCount();
            retries += observation.retries();
            if (observation.failed()) continue;
            semanticObservations.add(observation);
            for (EvalClaim claim : safe(observation.claims())) {
                claims.expected++;
                if (unsupported(claim, expected)) claims.actual++;
                if (claim.manualReviewRequired()) manualReviewClaims++;
            }
            addSetCounter(evidence, observation.evidenceUsed(), expected.mustFindEvidence(), List.of());
            addSetCounter(shapes, observation.projectShapes(), expected.expectedProjectShapes(), List.of());
            addSetCounter(tools, observation.toolPlan(), expected.expectedTools(), expected.forbiddenTools());
            addSetCounter(
                deepReads,
                observation.deepReadTargets(),
                expected.expectedDeepReadTargets(),
                List.of()
            );
            addSetCounter(views, observation.applicableViews(), expected.expectedViews(), expected.forbiddenViews());
            addSetCounter(conflicts, observation.conflictsDetected(), expected.expectedConflicts(), List.of());
            unavailableToolRequests += safe(observation.unavailableToolRequests()).size();
            rejectedDangerousRequests += safe(observation.rejectedDangerousToolRequests()).size();
            if (observation.stageOne() != null && observation.stageTwo() != null) {
                stageEvidenceGain.add(
                    Math.max(
                        recall(observation.stageTwo().evidenceIds(), expected.mustFindEvidence())
                            - recall(observation.stageOne().evidenceIds(), expected.mustFindEvidence()),
                        observation.validatedToolEvidenceCited() ? 1.0 : 0.0
                    )
                );
                stageUnsupportedReduction.add(
                    unsupportedRate(observation.stageOne().claims(), expected)
                        - unsupportedRate(observation.stageTwo().claims(), expected)
                );
                stageViewGain.add(
                    recall(observation.stageTwo().applicableViews(), expected.expectedViews())
                        - recall(observation.stageOne().applicableViews(), expected.expectedViews())
                );
            }
        }
        return new EvalSummary(
            observations.size(),
            semanticObservations.size(),
            successes,
            failures,
            timeouts,
            schemaFailures,
            cancellations,
            degraded,
            rate(successes, observations.size()),
            rate(claims.actual, claims.expected),
            recallFrom(evidence),
            precision(evidence),
            f1(shapes),
            exactSetAccuracy(semanticObservations, cases, Dimension.SHAPES),
            precision(tools),
            recallFrom(tools),
            rate(tools.falsePositive, Math.max(1, tools.predicted)),
            unavailableToolRequests,
            rejectedDangerousRequests,
            precision(deepReads),
            recallFrom(deepReads),
            recallFrom(views),
            precision(views),
            recallFrom(conflicts),
            repeatability(semanticObservations, cases),
            average(stageEvidenceGain),
            average(stageUnsupportedReduction),
            average(stageViewGain),
            manualReviewClaims,
            requests,
            inputTokens,
            outputTokens,
            totalTokens,
            observations.isEmpty() ? 0 : (double) latencyMs / observations.size(),
            percentile95(latencies),
            retries,
            rate(failures, observations.size()),
            rate(degraded, observations.size()),
            observations.stream().anyMatch(value -> value.estimatedCost() != null)
                ? observations.stream().filter(value -> value.estimatedCost() != null)
                    .mapToDouble(ProjectFlowEvalObservation::estimatedCost).sum()
                : null
        );
    }

    private static boolean unsupported(EvalClaim claim, EvalCase expected) {
        if (claim.manuallyUnsupported()) return true;
        String epistemic = normalized(claim.epistemicStatus());
        boolean uncertainty = epistemic.equals("UNKNOWN") || epistemic.equals("INFERRED");
        boolean missingEvidence = safe(claim.evidenceRefs()).isEmpty() && !uncertainty;
        boolean forbidden = expected.mustNotClaim().stream()
            .anyMatch(marker -> ProjectFlowEvalTextRules.containsUnnegatedMarker(claim.text(), marker));
        return missingEvidence || forbidden;
    }

    private static double unsupportedRate(List<EvalClaim> values, EvalCase expected) {
        List<EvalClaim> claims = safe(values);
        if (claims.isEmpty()) return 0;
        return rate(claims.stream().filter(value -> unsupported(value, expected)).count(), claims.size());
    }

    private static void addSetCounter(
        Counter counter,
        List<String> predictedValues,
        List<String> expectedValues,
        List<String> explicitlyForbidden
    ) {
        Set<String> predicted = normalizedSet(predictedValues);
        Set<String> expected = normalizedSet(expectedValues);
        Set<String> forbidden = normalizedSet(explicitlyForbidden);
        counter.predicted += predicted.size();
        counter.expected += expected.size();
        for (String value : predicted) {
            if (expected.contains(value)) counter.truePositive++;
            else counter.falsePositive++;
            if (forbidden.contains(value)) counter.forbidden++;
        }
    }

    private static double exactSetAccuracy(
        List<ProjectFlowEvalObservation> observations,
        Map<String, EvalCase> cases,
        Dimension dimension
    ) {
        if (observations.isEmpty()) return 0;
        long matches = observations.stream().filter(observation -> {
            EvalCase expected = cases.get(observation.caseId());
            return expected != null && switch (dimension) {
                case SHAPES -> normalizedSet(observation.projectShapes())
                    .equals(normalizedSet(expected.expectedProjectShapes()));
            };
        }).count();
        return rate(matches, observations.size());
    }

    private static double repeatability(
        List<ProjectFlowEvalObservation> observations,
        Map<String, EvalCase> cases
    ) {
        Map<String, List<ProjectFlowEvalObservation>> grouped = new HashMap<>();
        observations.forEach(value -> grouped.computeIfAbsent(value.caseId(), ignored -> new ArrayList<>()).add(value));
        List<Double> similarities = new ArrayList<>();
        for (Map.Entry<String, List<ProjectFlowEvalObservation>> entry : grouped.entrySet()) {
            EvalCase expected = cases.get(entry.getKey());
            if (expected == null) continue;
            List<ProjectFlowEvalObservation> group = entry.getValue();
            for (int left = 0; left < group.size(); left++) {
                for (int right = left + 1; right < group.size(); right++) {
                    similarities.add(criticalDecisionAgreement(
                        group.get(left),
                        group.get(right),
                        expected
                    ));
                }
            }
        }
        return similarities.isEmpty() ? 1.0 : average(similarities);
    }

    /**
     * Repeatability measures whether repeated runs make the same critical
     * benchmark decisions. Accuracy remains independent: two consistently
     * wrong runs are repeatable but still fail the recall/safety gates.
     * Free-form wording, harmless extra sections and claim count do not turn a
     * stable decision into a false instability signal.
     */
    private static double criticalDecisionAgreement(
        ProjectFlowEvalObservation left,
        ProjectFlowEvalObservation right,
        EvalCase expected
    ) {
        DecisionAgreement agreement = new DecisionAgreement();
        compareMembership(agreement, left.projectShapes(), right.projectShapes(), expected.expectedProjectShapes());
        compareMembership(agreement, left.evidenceUsed(), right.evidenceUsed(), expected.mustFindEvidence());
        compareMembership(agreement, left.toolPlan(), right.toolPlan(), expected.expectedTools());
        compareMembership(agreement, left.toolPlan(), right.toolPlan(), expected.forbiddenTools());
        compareMembership(agreement, left.applicableViews(), right.applicableViews(), expected.expectedViews());
        compareMembership(agreement, left.applicableViews(), right.applicableViews(), expected.forbiddenViews());
        compareMembership(
            agreement,
            left.conflictsDetected(),
            right.conflictsDetected(),
            expected.expectedConflicts()
        );
        compareMembership(
            agreement,
            left.deepReadTargets(),
            right.deepReadTargets(),
            expected.expectedDeepReadTargets()
        );
        for (String marker : expected.mustNotClaim()) {
            agreement.compare(
                containsForbiddenClaim(left.claims(), marker),
                containsForbiddenClaim(right.claims(), marker)
            );
        }
        agreement.compare(!safe(left.conflictsDetected()).isEmpty(), !safe(right.conflictsDetected()).isEmpty());
        agreement.compare(!safe(left.unknowns()).isEmpty(), !safe(right.unknowns()).isEmpty());
        agreement.compare(hasUnsupportedClaim(left, expected), hasUnsupportedClaim(right, expected));
        return agreement.rate();
    }

    private static void compareMembership(
        DecisionAgreement agreement,
        List<String> left,
        List<String> right,
        List<String> decisionValues
    ) {
        Set<String> leftSet = normalizedSet(left);
        Set<String> rightSet = normalizedSet(right);
        for (String value : safe(decisionValues)) {
            String normalized = normalized(value);
            agreement.compare(leftSet.contains(normalized), rightSet.contains(normalized));
        }
    }

    private static boolean containsForbiddenClaim(List<EvalClaim> claims, String marker) {
        return safe(claims).stream()
            .anyMatch(claim -> ProjectFlowEvalTextRules.containsUnnegatedMarker(claim.text(), marker));
    }

    private static boolean hasUnsupportedClaim(ProjectFlowEvalObservation observation, EvalCase expected) {
        return safe(observation.claims()).stream().anyMatch(claim -> unsupported(claim, expected));
    }

    private static double recall(List<String> actual, List<String> expected) {
        Set<String> expectedSet = normalizedSet(expected);
        if (expectedSet.isEmpty()) return 1.0;
        Set<String> actualSet = normalizedSet(actual);
        actualSet.retainAll(expectedSet);
        return rate(actualSet.size(), expectedSet.size());
    }

    private static Set<String> normalizedSet(List<String> values) {
        Set<String> result = new HashSet<>();
        safe(values).stream().map(ProjectFlowEvalMetrics::normalized)
            .filter(value -> !value.isBlank()).forEach(result::add);
        return result;
    }

    private static String normalized(String value) {
        return value == null ? "" : value.strip().toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static double precision(Counter value) {
        return value.predicted == 0 ? (value.expected == 0 ? 1.0 : 0.0)
            : rate(value.truePositive, value.predicted);
    }

    private static double recallFrom(Counter value) {
        return value.expected == 0 ? 1.0 : rate(value.truePositive, value.expected);
    }

    private static double f1(Counter value) {
        double precision = precision(value);
        double recall = recallFrom(value);
        return precision + recall == 0 ? 0 : 2 * precision * recall / (precision + recall);
    }

    private static double rate(long numerator, long denominator) {
        return denominator <= 0 ? 0 : (double) numerator / denominator;
    }

    private static double average(List<Double> values) {
        return values.isEmpty() ? 0 : values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    private static double percentile95(List<Long> values) {
        if (values.isEmpty()) return 0;
        List<Long> sorted = values.stream().sorted().toList();
        int index = Math.max(0, (int) Math.ceil(sorted.size() * 0.95) - 1);
        return sorted.get(index);
    }

    private static final class Counter {
        private long predicted;
        private long expected;
        private long truePositive;
        private long falsePositive;
        private long forbidden;
        private long actual;
    }

    private static final class DecisionAgreement {
        private long total;
        private long matches;

        void compare(boolean left, boolean right) {
            total++;
            if (left == right) matches++;
        }

        double rate() {
            return total == 0 ? 1.0 : ProjectFlowEvalMetrics.rate(matches, total);
        }
    }

    private enum Dimension {
        SHAPES
    }

    record EvalSummary(
        int runCount,
        int conditionalSemanticRunCount,
        int successfulRunCount,
        int failureCount,
        int timeoutCount,
        int schemaFailureCount,
        int cancellationCount,
        int degradedRunCount,
        double endToEndCompletionRate,
        double unsupportedClaimRate,
        double criticalEvidenceRecall,
        double evidencePrecision,
        double projectShapeF1,
        double projectShapeExactAccuracy,
        double toolSelectionPrecision,
        double toolSelectionRecall,
        double unnecessaryToolRate,
        int unavailableToolRequestCount,
        int rejectedDangerousToolRequestCount,
        double deepReadTargetAccuracy,
        double deepReadSufficiency,
        double dynamicViewRecall,
        double dynamicViewPrecision,
        double conflictDetectionRate,
        double repeatability,
        double secondStageEvidenceGain,
        double secondStageUnsupportedClaimReduction,
        double secondStageViewGain,
        int manualReviewClaimCount,
        int modelRequestCount,
        long inputTokens,
        long outputTokens,
        long totalTokens,
        double averageLatencyMs,
        double p95LatencyMs,
        int retryCount,
        double failureRate,
        double degradationRate,
        Double estimatedCost
    ) {
    }
}
