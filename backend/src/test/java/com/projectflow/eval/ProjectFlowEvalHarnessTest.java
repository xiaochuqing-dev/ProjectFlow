package com.projectflow.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.eval.ProjectFlowEvalGroundTruth.EvalCase;
import com.projectflow.eval.ProjectFlowEvalObservation.EvalClaim;
import com.projectflow.eval.ProjectFlowEvalObservation.StageResult;

class ProjectFlowEvalHarnessTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void loadsEighteenCasesComputesMetricsAndWritesSanitizedArtifacts() throws Exception {
        ProjectFlowEvalGroundTruth groundTruth = ProjectFlowEvalGroundTruth.load(mapper);
        List<ProjectFlowEvalObservation> observations = new ArrayList<>();
        for (EvalCase value : groundTruth.cases()) {
            int runs = value.important() ? 3 : 1;
            for (int index = 1; index <= runs; index++) {
                observations.add(perfectObservation(value, index));
            }
        }

        ProjectFlowEvalHarness harness = new ProjectFlowEvalHarness(mapper);
        var run = harness.evaluate(groundTruth, observations);
        Path output = Path.of("target", "projectflow-eval", "fixed");
        var artifacts = harness.writeArtifacts(run, output);

        assertThat(groundTruth.cases()).hasSize(18);
        assertThat(groundTruth.cases())
            .filteredOn(value -> !value.expectedDeepReadTargets().isEmpty())
            .allSatisfy(value -> assertThat(value.toolEvidence()).isNotBlank());
        assertThat(groundTruth.cases()).filteredOn(EvalCase::important)
            .allSatisfy(value -> assertThat(
                observations.stream().filter(item -> item.caseId().equals(value.id())).count()
            ).isGreaterThanOrEqualTo(3));
        assertThat(run.summary().unsupportedClaimRate()).isZero();
        assertThat(run.summary().criticalEvidenceRecall()).isEqualTo(1.0);
        assertThat(run.summary().evidencePrecision()).isEqualTo(1.0);
        assertThat(run.summary().projectShapeF1()).isEqualTo(1.0);
        assertThat(run.summary().toolSelectionPrecision()).isEqualTo(1.0);
        assertThat(run.summary().toolSelectionRecall()).isEqualTo(1.0);
        assertThat(run.summary().deepReadSufficiency()).isEqualTo(1.0);
        assertThat(run.summary().repeatability()).isEqualTo(1.0);
        assertThat(artifacts.json()).exists();
        assertThat(artifacts.markdown()).exists();
        String json = Files.readString(artifacts.json());
        assertThat(json)
            .doesNotContain("rawResponse", "rawModelResponse", "reasoning", "apiKey", "Authorization")
            .contains(ProjectFlowEvalHarness.SCOPE_NOTICE);
    }

    private static ProjectFlowEvalObservation perfectObservation(EvalCase value, int run) {
        List<String> refs = value.mustFindEvidence().isEmpty()
            ? List.of()
            : List.of(value.mustFindEvidence().get(0));
        EvalClaim claim = new EvalClaim(
            value.mustFindEvidence().isEmpty() ? "当前没有可支持的事实性结论" : "结论由关键证据支持",
            "PROJECT_UNDERSTANDING",
            value.mustFindEvidence().isEmpty() ? "UNKNOWN" : "OBSERVED",
            refs,
            false,
            false
        );
        StageResult stageOne = value.mustFindEvidence().isEmpty()
            ? null
            : new StageResult(
                value.mustFindEvidence().subList(0, Math.max(0, value.mustFindEvidence().size() - 1)),
                List.of(claim),
                value.expectedViews().subList(0, Math.max(0, value.expectedViews().size() - 1))
            );
        StageResult stageTwo = value.mustFindEvidence().isEmpty()
            ? null
            : new StageResult(value.mustFindEvidence(), List.of(claim), value.expectedViews());
        return new ProjectFlowEvalObservation(
            value.id(),
            value.id() + "-fixed-" + run,
            "semantic-scout-v3+final-synthesis-v3",
            "3.7.2",
            value.source(),
            run,
            "FIXED_TEST_PROVIDER",
            "FIXED",
            "fixed-contract-model",
            Instant.parse("2026-07-26T00:00:00Z"),
            value.expectedProjectShapes(),
            value.mustFindEvidence(),
            value.expectedTools(),
            List.of(),
            value.forbiddenTools(),
            value.expectedDeepReadTargets(),
            value.expectedViews(),
            value.expectedConflicts(),
            value.expectedUnknowns(),
            List.of(claim),
            List.of(),
            List.of(),
            value.expectedViews(),
            stageOne,
            stageTwo,
            value.expectedTools().isEmpty() ? 0 : 1,
            100,
            50,
            150,
            60,
            30,
            40,
            20,
            value.toolEvidence() == null ? 0 : value.toolEvidence().length(),
            !value.expectedTools().isEmpty(),
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
