package com.projectflow.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.eval.ProjectFlowEvalGroundTruth.EvalCase;
import com.projectflow.service.ProjectUnderstandingPromptBuilder;

class ProjectFlowEvalPromptParityTest {
    @Test
    void directEvalUsesSharedBuilderAndGroundTruthLabelsCannotChangePrompt() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        EvalCase original = new EvalCase(
            "case-a",
            "fixture://document",
            true,
            "source:doc 是一份需要深读的当前约束文档。",
            "深读正文",
            List.of("DOCUMENT"),
            List.of("source:doc"),
            List.of("database"),
            List.of("DOC_READER"),
            List.of("SCIP"),
            List.of("DOCUMENT_OVERVIEW"),
            List.of("ARCHITECTURE"),
            List.of("implementation"),
            List.of(),
            "UNAVAILABLE",
            List.of("source:doc")
        );
        EvalCase changedLabels = new EvalCase(
            "case-b",
            original.source(),
            false,
            original.context(),
            "完全不同的工具证据",
            List.of("BACKEND"),
            List.of("source:other"),
            List.of("document"),
            List.of("GIT_HISTORY"),
            List.of("DOC_READER"),
            List.of("BACKEND"),
            List.of("DOCUMENT_OVERVIEW"),
            List.of("anything"),
            List.of("FAKE_CONFLICT"),
            "GROUND_TRUTH_ONLY_MODE",
            List.of()
        );
        ProjectUnderstandingPromptBuilder builder = new ProjectUnderstandingPromptBuilder();

        String originalPrompt = ProjectFlowRealModelEvalIT.buildScoutPrompt(mapper, builder, original);
        String changedPrompt = ProjectFlowRealModelEvalIT.buildScoutPrompt(mapper, builder, changedLabels);

        assertThat(changedPrompt).isEqualTo(originalPrompt);
        assertThat(originalPrompt)
            .contains(ProjectUnderstandingPromptBuilder.SCOUT_PROMPT_VERSION)
            .doesNotContain(
                "mustFindEvidence",
                "expectedProjectShapes",
                "expectedTools",
                "forbiddenTools",
                "expectedViews",
                "GROUND_TRUTH_ONLY_MODE",
                "FAKE_CONFLICT"
            );
    }
}
