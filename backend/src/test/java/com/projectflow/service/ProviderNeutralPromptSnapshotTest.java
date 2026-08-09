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

        assertThat(ProjectHistoryPromptBuilder.PROMPT_VERSION).isEqualTo("project-history-synthesis-v8");
        assertThat(instructions).contains(
            "可改字段只有 Story 的 humanTitle、oneSentenceSummary、reason、reasonEvidenceRefs、unknowns",
            "role、primaryStoryId、supportingChangeRefs、storyRefs、时间",
            "输出前做机械核对",
            "requiredStoryIds=[]",
            "OUTPUT_TEMPLATE_JSON={\"stories\":[],\"chapters\":[]}",
            "{\"stories\":[{\"storyId\":\"\",\"humanTitle\":\"\",\"oneSentenceSummary\":\"\",\"reason\":\"\",\"reasonEvidenceRefs\":[],\"unknowns\":[]}],"
        );
        assertThat(instructions).doesNotContain("DeepSeek", "GLM", "roleCandidate", "storyRefs\":[]");

        String repair = builder.validationRepair(prompt, "INVALID_EVIDENCE");
        assertThat(repair).contains(
            ProjectHistoryPromptBuilder.VALIDATION_REPAIR_MARKER + "INVALID_EVIDENCE",
            "只能使用 OUTPUT_TEMPLATE_JSON 中的对象、ID 和字段",
            "没有合格 Evidence 时 reason 留空"
        ).doesNotContain("DeepSeek", "GLM", "上一次输出内容");
    }
}
