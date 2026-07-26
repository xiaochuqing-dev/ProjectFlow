package com.projectflow.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.eval.ProjectFlowEvalGroundTruth.EvalCase;
import com.projectflow.eval.ProjectFlowEvalObservation.EvalClaim;
import com.projectflow.eval.ProjectFlowEvalObservation.StageResult;

class ProjectFlowEvalMetricsTest {
    @Test
    void calculatesUnsupportedRecallToolRepeatabilityAndSecondStageGain() throws Exception {
        ProjectFlowEvalGroundTruth full = ProjectFlowEvalGroundTruth.load(
            new ObjectMapper().findAndRegisterModules()
        );
        EvalCase testCase = full.cases().stream()
            .filter(value -> "strange-important-document".equals(value.id()))
            .findFirst()
            .orElseThrow();
        ProjectFlowEvalGroundTruth groundTruth = new ProjectFlowEvalGroundTruth(
            full.standard(),
            List.of(testCase)
        );
        EvalClaim unsupported = new EvalClaim(
            "source code architecture",
            "FACT",
            "OBSERVED",
            List.of(),
            true,
            false
        );
        EvalClaim supported = new EvalClaim(
            "奇怪命名文档包含高价值约束",
            "FACT",
            "OBSERVED",
            List.of("source:strange-doc"),
            true,
            false
        );
        StageResult stageOne = new StageResult(List.of(), List.of(unsupported), List.of());
        StageResult stageTwo = new StageResult(
            List.of("source:strange-doc"),
            List.of(supported),
            List.of("DOCUMENT_OVERVIEW")
        );
        List<ProjectFlowEvalObservation> observations = List.of(
            observation(testCase, "r1", List.of(unsupported), stageOne, stageTwo),
            observation(testCase, "r2", List.of(supported), stageOne, stageTwo)
        );

        var summary = ProjectFlowEvalMetrics.calculate(groundTruth, observations);

        assertThat(summary.unsupportedClaimRate()).isEqualTo(0.5);
        assertThat(summary.criticalEvidenceRecall()).isEqualTo(1.0);
        assertThat(summary.toolSelectionRecall()).isEqualTo(1.0);
        assertThat(summary.repeatability()).isBetween(0.5, 1.0);
        assertThat(summary.secondStageEvidenceGain()).isPositive();
        assertThat(summary.secondStageUnsupportedClaimReduction()).isPositive();
        assertThat(summary.secondStageViewGain()).isPositive();
    }

    @Test
    void mustNotMetricDoesNotTreatAnExplicitLimitationAsTheForbiddenClaim() {
        assertThat(ProjectFlowEvalTextRules.containsUnnegatedMarker(
            "Complete repository coverage is not available; only bounded signals were sampled.",
            "complete repository coverage"
        )).isFalse();
        assertThat(ProjectFlowEvalTextRules.containsUnnegatedMarker(
            "The analysis provides complete repository coverage.",
            "complete repository coverage"
        )).isTrue();
    }

    private static ProjectFlowEvalObservation observation(
        EvalCase testCase,
        String runId,
        List<EvalClaim> claims,
        StageResult stageOne,
        StageResult stageTwo
    ) {
        return new ProjectFlowEvalObservation(
            testCase.id(),
            runId,
            "semantic-scout-v3+final-synthesis-v3",
            "3.7.2",
            testCase.source(),
            "r1".equals(runId) ? 1 : 2,
            "FIXED",
            "FIXED",
            "fixed",
            Instant.parse("2026-07-26T00:00:00Z"),
            testCase.expectedProjectShapes(),
            testCase.mustFindEvidence(),
            testCase.expectedTools(),
            List.of(),
            List.of(),
            testCase.expectedDeepReadTargets(),
            testCase.expectedViews(),
            testCase.expectedConflicts(),
            testCase.expectedUnknowns(),
            claims,
            claims.stream().filter(value -> value.evidenceRefs().isEmpty()).map(EvalClaim::text).toList(),
            List.of(),
            testCase.expectedViews(),
            stageOne,
            stageTwo,
            2,
            100,
            50,
            150,
            60,
            30,
            40,
            20,
            100,
            20,
            0,
            false,
            false,
            "COMPLETE",
            "SUCCEEDED",
            "NOT_DEGRADED",
            null,
            "UNAVAILABLE"
        );
    }
}
