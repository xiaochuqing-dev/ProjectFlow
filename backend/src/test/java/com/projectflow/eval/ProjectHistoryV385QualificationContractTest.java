package com.projectflow.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProjectHistoryV385QualificationContractTest {

    @Test
    void separatesSourceCoverageLimitsFromModelExecutionDegradation() {
        assertThat(ProjectHistoryV385RealOutputEvaluatorTest.modelExecutionDegraded(
            "MODEL_VALIDATED", 0, 0, 0, 0, 0
        )).isFalse();
        assertThat(ProjectHistoryV385RealOutputEvaluatorTest.modelExecutionDegraded(
            "MODEL_VALIDATED_INCREMENTAL", 0, 0, 0, 0, 0
        )).isFalse();
    }

    @Test
    void preservesEveryModelFailureAndIncompleteWindowAsAQualificationFailure() {
        assertThat(ProjectHistoryV385RealOutputEvaluatorTest.modelExecutionDegraded(
            "MODEL_PARTIAL_FALLBACK_DETERMINISTIC", 0, 0, 0, 0, 0
        )).isTrue();
        assertThat(ProjectHistoryV385RealOutputEvaluatorTest.modelExecutionDegraded(
            "MODEL_VALIDATED", 1, 0, 0, 0, 0
        )).isTrue();
        assertThat(ProjectHistoryV385RealOutputEvaluatorTest.modelExecutionDegraded(
            "MODEL_VALIDATED", 0, 1, 0, 0, 0
        )).isTrue();
        assertThat(ProjectHistoryV385RealOutputEvaluatorTest.modelExecutionDegraded(
            "MODEL_VALIDATED", 0, 0, 1, 0, 0
        )).isTrue();
        assertThat(ProjectHistoryV385RealOutputEvaluatorTest.modelExecutionDegraded(
            "MODEL_VALIDATED", 0, 0, 0, 1, 0
        )).isTrue();
        assertThat(ProjectHistoryV385RealOutputEvaluatorTest.modelExecutionDegraded(
            "MODEL_VALIDATED", 0, 0, 0, 0, 1
        )).isTrue();
    }
}
