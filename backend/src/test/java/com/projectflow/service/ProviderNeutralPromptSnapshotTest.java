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

        assertThat(ProjectHistoryPromptBuilder.PROMPT_VERSION).isEqualTo("project-history-synthesis-v12");
        assertThat(instructions).contains(
            "可改字段只有 Story 的 humanTitle、oneSentenceSummary、beforeWording、changeWording、afterWording、reason、reasonEvidenceRefs、unknownWording",
            "role、primaryStoryId、supportingChangeRefs、storyRefs、时间、verified semantic、claimState",
            "PLANNED 不得写成 IMPLEMENTED",
            "directSupportSummary 是与当前 subject/action 直接匹配的有界支持",
            "不得因为同 Commit、相邻时间、相同区域或 Supporting Story 把间接上下文借给当前 Claim",
            "五段不得复读同一句话",
            "输出前做机械核对",
            "requiredStoryIds=[]",
            "OUTPUT_TEMPLATE_JSON={\"stories\":[],\"chapters\":[]}",
            "{\"stories\":[{\"storyId\":\"\",\"humanTitle\":\"\",\"oneSentenceSummary\":\"\",\"beforeWording\":\"\",\"changeWording\":\"\",\"afterWording\":\"\",\"reason\":\"\",\"reasonEvidenceRefs\":[],\"unknownWording\":\"\"}],"
        );
        assertThat(instructions).doesNotContain("DeepSeek", "GLM", "roleCandidate", "storyRefs\":[]");

        String repair = builder.validationRepair(prompt, "INVALID_EVIDENCE");
        assertThat(repair).contains(
            ProjectHistoryPromptBuilder.VALIDATION_REPAIR_MARKER + "INVALID_EVIDENCE",
            "只能使用 OUTPUT_TEMPLATE_JSON 中的对象、ID 和字段",
            "没有采用合格 Evidence 时 reason 留空",
            "humanTitle 必须明确写出动作和对象"
        ).doesNotContain("DeepSeek", "GLM", "上一次输出内容");

        var chapterPrompt = builder.buildChapterProduction(new ProjectHistoryPromptBuilder.ChapterSynthesisPromptInput(
            "chapter-one", "2026-01-01T00:00:00Z", "2026-01-02T00:00:00Z", 0, 0, 0,
            List.of(), "membership-fingerprint", List.of()
        )).prompt();
        String chapterRepair = builder.validationRepair(chapterPrompt, "CONTRACT");
        assertThat(ProjectHistoryPromptBuilder.CHAPTER_PROMPT_VERSION)
            .isEqualTo("project-history-chapter-synthesis-v6");
        assertThat(chapterRepair).contains(
            "CHAPTER_SYNTHESIS_JSON=",
            "从原始 CHAPTER_SYNTHESIS_JSON 重新生成",
            "只能包含 chapterId、title、summary",
            "title 必须直接写出至少一个 Primary Story 已支持的具体动作、对象和结果"
        ).doesNotContain("只能使用 OUTPUT_TEMPLATE_JSON", "DeepSeek", "GLM");
    }
}
