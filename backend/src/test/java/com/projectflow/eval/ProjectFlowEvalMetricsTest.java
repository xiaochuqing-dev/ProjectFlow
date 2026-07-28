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

    @Test
    void countsNewlyCitedValidatedToolEvidenceAsSecondStageGain() throws Exception {
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
        EvalClaim supported = new EvalClaim(
            "深读结果补充了回滚边界",
            "FACT",
            "OBSERVED",
            List.of("tool:doc-reader:validated"),
            true,
            false
        );
        StageResult stageOne = new StageResult(
            List.of("source:strange-doc"),
            List.of(supported),
            List.of("DOCUMENT_OVERVIEW")
        );
        StageResult stageTwo = new StageResult(
            List.of("source:strange-doc", "tool:doc-reader:validated"),
            List.of(supported),
            List.of("DOCUMENT_OVERVIEW")
        );

        var summary = ProjectFlowEvalMetrics.calculate(
            groundTruth,
            List.of(observation(testCase, "r1", List.of(supported), stageOne, stageTwo))
        );

        assertThat(summary.secondStageEvidenceGain()).isEqualTo(1.0);
    }

    @Test
    void repeatabilityTreatsParaphrasesWithStableEvidenceAndEpistemicRoleAsEquivalent() throws Exception {
        ProjectFlowEvalGroundTruth full = ProjectFlowEvalGroundTruth.load(
            new ObjectMapper().findAndRegisterModules()
        );
        EvalCase testCase = full.cases().stream()
            .filter(value -> "stale-readme".equals(value.id()))
            .findFirst()
            .orElseThrow();
        ProjectFlowEvalGroundTruth groundTruth = new ProjectFlowEvalGroundTruth(
            full.standard(),
            List.of(testCase)
        );
        EvalClaim first = new EvalClaim(
            "README 的当前性无法确认",
            "CURRENTNESS",
            "POSSIBLY_STALE",
            List.of("source:readme", "source:manifest"),
            true,
            false
        );
        EvalClaim paraphrase = new EvalClaim(
            "现有证据表明 README 可能已经过时",
            "CURRENTNESS",
            "POSSIBLY_STALE",
            List.of("source:manifest", "source:readme"),
            true,
            false
        );

        var summary = ProjectFlowEvalMetrics.calculate(
            groundTruth,
            List.of(
                observation(testCase, "r1", List.of(first), null, null),
                observation(testCase, "r2", List.of(paraphrase), null, null)
            )
        );

        assertThat(summary.repeatability()).isEqualTo(1.0);
    }

    @Test
    void repeatabilityDetectsAChangedCriticalToolDecision() throws Exception {
        ProjectFlowEvalGroundTruth full = ProjectFlowEvalGroundTruth.load(
            new ObjectMapper().findAndRegisterModules()
        );
        EvalCase testCase = full.cases().stream()
            .filter(value -> "small-script".equals(value.id()))
            .findFirst()
            .orElseThrow();
        ProjectFlowEvalGroundTruth groundTruth = new ProjectFlowEvalGroundTruth(
            full.standard(),
            List.of(testCase)
        );
        EvalClaim claim = new EvalClaim(
            "脚本提供 CSV 转换",
            "PURPOSE",
            "CURRENT_STATE",
            List.of("source:script"),
            true,
            false
        );

        var summary = ProjectFlowEvalMetrics.calculate(
            groundTruth,
            List.of(
                observation(testCase, "r1", List.of(claim), null, null, List.of("MANIFEST")),
                observation(testCase, "r2", List.of(claim), null, null, List.of())
            )
        );

        assertThat(summary.repeatability()).isLessThan(1.0);
        assertThat(summary.repeatability()).isGreaterThan(0.5);
    }

    private static ProjectFlowEvalObservation observation(
        EvalCase testCase,
        String runId,
        List<EvalClaim> claims,
        StageResult stageOne,
        StageResult stageTwo
    ) {
        return observation(testCase, runId, claims, stageOne, stageTwo, testCase.expectedTools());
    }

    private static ProjectFlowEvalObservation observation(
        EvalCase testCase,
        String runId,
        List<EvalClaim> claims,
        StageResult stageOne,
        StageResult stageTwo,
        List<String> toolPlan
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
            toolPlan,
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
