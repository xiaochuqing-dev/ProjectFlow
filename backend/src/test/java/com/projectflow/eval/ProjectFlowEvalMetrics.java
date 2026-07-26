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
        int degraded = 0;
        long inputTokens = 0;
        long outputTokens = 0;
        long totalTokens = 0;
        long latencyMs = 0;
        int requests = 0;
        int retries = 0;
        List<Double> stageEvidenceGain = new ArrayList<>();
        List<Double> stageUnsupportedReduction = new ArrayList<>();
        List<Double> stageViewGain = new ArrayList<>();

        for (ProjectFlowEvalObservation observation : observations) {
            EvalCase expected = cases.get(observation.caseId());
            if (expected == null) continue;
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
            failures += observation.failed() ? 1 : 0;
            degraded += observation.degraded() ? 1 : 0;
            inputTokens += observation.inputTokens();
            outputTokens += observation.outputTokens();
            totalTokens += observation.totalTokens();
            latencyMs += observation.latencyMs();
            requests += observation.requestCount();
            retries += observation.retries();

            if (observation.stageOne() != null && observation.stageTwo() != null) {
                stageEvidenceGain.add(
                    recall(observation.stageTwo().evidenceIds(), expected.mustFindEvidence())
                        - recall(observation.stageOne().evidenceIds(), expected.mustFindEvidence())
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
            rate(claims.actual, claims.expected),
            recallFrom(evidence),
            precision(evidence),
            f1(shapes),
            exactSetAccuracy(observations, cases, Dimension.SHAPES),
            precision(tools),
            recallFrom(tools),
            rate(tools.falsePositive, Math.max(1, tools.predicted)),
            unavailableToolRequests,
            rejectedDangerousRequests,
            precision(deepReads),
            recallFrom(views),
            precision(views),
            recallFrom(conflicts),
            repeatability(observations),
            average(stageEvidenceGain),
            average(stageUnsupportedReduction),
            average(stageViewGain),
            manualReviewClaims,
            requests,
            inputTokens,
            outputTokens,
            totalTokens,
            observations.isEmpty() ? 0 : (double) latencyMs / observations.size(),
            retries,
            rate(failures, observations.size()),
            failures == 0 ? 1.0 : rate(degraded, failures),
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

    private static double repeatability(List<ProjectFlowEvalObservation> observations) {
        Map<String, List<ProjectFlowEvalObservation>> grouped = new HashMap<>();
        observations.forEach(value -> grouped.computeIfAbsent(value.caseId(), ignored -> new ArrayList<>()).add(value));
        List<Double> similarities = new ArrayList<>();
        for (List<ProjectFlowEvalObservation> group : grouped.values()) {
            for (int left = 0; left < group.size(); left++) {
                for (int right = left + 1; right < group.size(); right++) {
                    ProjectFlowEvalObservation a = group.get(left);
                    ProjectFlowEvalObservation b = group.get(right);
                    similarities.add((
                        jaccard(a.projectShapes(), b.projectShapes())
                            + jaccard(a.evidenceUsed(), b.evidenceUsed())
                            + jaccard(a.toolPlan(), b.toolPlan())
                            + jaccard(
                                safe(a.claims()).stream().map(EvalClaim::text).toList(),
                                safe(b.claims()).stream().map(EvalClaim::text).toList()
                            )
                    ) / 4.0);
                }
            }
        }
        return similarities.isEmpty() ? 1.0 : average(similarities);
    }

    private static double jaccard(List<String> left, List<String> right) {
        Set<String> a = normalizedSet(left);
        Set<String> b = normalizedSet(right);
        if (a.isEmpty() && b.isEmpty()) return 1.0;
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        return rate(intersection.size(), union.size());
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

    private static final class Counter {
        private long predicted;
        private long expected;
        private long truePositive;
        private long falsePositive;
        private long forbidden;
        private long actual;
    }

    private enum Dimension {
        SHAPES
    }

    record EvalSummary(
        int runCount,
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
        int retryCount,
        double failureRate,
        double degradationSuccessRate,
        Double estimatedCost
    ) {
    }
}
