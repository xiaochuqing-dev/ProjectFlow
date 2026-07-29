package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class TwoModelContractTest {
    @Test
    void understandingContractIsProviderNeutralForResponsesAndChatAdapters() {
        ProjectUnderstandingPromptBuilder builder = new ProjectUnderstandingPromptBuilder();
        String prompt = builder.buildFinalPrompt(new ProjectUnderstandingPromptBuilder.FinalPromptInput(
            "{\"stageOneProfile\":{\"summary\":\"provider-neutral\"}}",
            List.of("intake:scan"),
            List.of("CURRENT_STATE"),
            List.of()
        ));

        assertThat(prompt)
            .contains(
                ProjectUnderstandingPromptBuilder.CONTRACT_VERSION,
                "\"epistemicStatus\"",
                "OBSERVED|VERIFIED|DECLARED|INFERRED|CONFLICTED|UNKNOWN|PROCESS_EVIDENCE"
            )
            .doesNotContainIgnoringCase(
                "deepseek", "glm-5.2", "volces", "api.deepseek.com", "ark.cn-beijing"
            );
    }
}
