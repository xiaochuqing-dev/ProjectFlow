package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class ProviderNeutralPromptSnapshotTest {
    @Test
    void frozenInstructionSnapshotKeepsMinimalOutputAndEngineeringOwnership() {
        ProjectHistoryPromptBuilder builder = new ProjectHistoryPromptBuilder(new ObjectMapper());
        String prompt = builder.buildProduction(new ProjectHistoryPromptBuilder.PromptInput(List.of(), List.of())).prompt();
        String instructions = prompt.substring(0, prompt.indexOf("\nSTORIES_JSON="));

        assertThat(ProjectHistoryPromptBuilder.PROMPT_VERSION).isEqualTo("project-history-synthesis-v5");
        assertThat(instructions).contains(
            "可改字段只有 Story 的 humanTitle、oneSentenceSummary、reason、reasonEvidenceRefs、unknowns",
            "role、primaryStoryId、supportingChangeRefs、storyRefs、时间",
            "{\"stories\":[{\"storyId\":\"\",\"humanTitle\":\"\",\"oneSentenceSummary\":\"\",\"reason\":\"\",\"reasonEvidenceRefs\":[],\"unknowns\":[]}],"
        );
        assertThat(instructions).doesNotContain("DeepSeek", "GLM", "roleCandidate", "storyRefs\":[]");
    }
}
