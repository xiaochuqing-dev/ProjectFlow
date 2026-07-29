package com.projectflow.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.eval.ProjectFlowEvalGroundTruth.EvalCase;
import com.projectflow.service.ProjectUnderstandingPromptBuilder;

class GroundTruthLeakTest {
    @Test
    void expectedLabelsAndCaseIdentityNeverEnterTheProductionPrompt() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        EvalCase caseWithSentinels = new EvalCase(
            "GROUND_TRUTH_SECRET_CASE_ID",
            "fixture://same-source",
            true,
            "同一份真实输入上下文",
            "GROUND_TRUTH_SECRET_TOOL_EVIDENCE",
            List.of("GROUND_TRUTH_SECRET_SHAPE"),
            List.of("GROUND_TRUTH_SECRET_EVIDENCE"),
            List.of("GROUND_TRUTH_SECRET_FORBIDDEN_CLAIM"),
            List.of("GROUND_TRUTH_SECRET_TOOL"),
            List.of("GROUND_TRUTH_SECRET_FORBIDDEN_TOOL"),
            List.of("GROUND_TRUTH_SECRET_VIEW"),
            List.of("GROUND_TRUTH_SECRET_FORBIDDEN_VIEW"),
            List.of("GROUND_TRUTH_SECRET_UNKNOWN"),
            List.of("GROUND_TRUTH_SECRET_CONFLICT"),
            "GROUND_TRUTH_SECRET_HISTORY",
            List.of("GROUND_TRUTH_SECRET_TARGET")
        );

        String prompt = ProjectFlowRealModelEvalIT.buildScoutPrompt(
            mapper, new ProjectUnderstandingPromptBuilder(), caseWithSentinels
        );

        assertThat(prompt)
            .contains("同一份真实输入上下文")
            .doesNotContain("GROUND_TRUTH_SECRET");
    }
}
