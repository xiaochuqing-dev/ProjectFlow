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

        assertThat(ProjectHistoryPromptBuilder.PROMPT_VERSION).isEqualTo("project-history-synthesis-v14");
        assertThat(instructions).contains(
            "可改字段只有 Story 的 humanTitle、oneSentenceSummary、beforeWording、changeWording、afterWording、reason、reasonEvidenceRefs、unknownWording",
            "role、primaryStoryId、supportingChangeRefs、storyRefs、时间、verified semantic、claimState",
            "PLANNED 不得写成 IMPLEMENTED",
            "directSupportSummary 是与当前 subject/action 直接匹配的有界支持",
            "不得因为同 Commit、相邻时间、相同区域或 Supporting Story 把间接上下文借给当前 Claim",
            "五段不得复读同一句话",
            "OUTPUT_TEMPLATE_JSON 已预填工程层确定性安全草稿",
            "输出前做机械核对",
            "requiredStoryIds=[]",
            "OUTPUT_TEMPLATE_JSON={\"stories\":[],\"chapters\":[]}",
            "{\"stories\":[{\"storyId\":\"\",\"humanTitle\":\"\",\"oneSentenceSummary\":\"\",\"beforeWording\":\"\",\"changeWording\":\"\",\"afterWording\":\"\",\"reason\":\"\",\"reasonEvidenceRefs\":[],\"unknownWording\":\"\"}],"
        );
        assertThat(instructions).doesNotContain("DeepSeek", "GLM", "roleCandidate", "storyRefs\":[]");

        String repair = builder.validationRepair(prompt, "INVALID_EVIDENCE");
        assertThat(repair).contains(
            ProjectHistoryPromptBuilder.VALIDATION_REPAIR_MARKER + "INVALID_EVIDENCE",
            "必须原样返回 REQUIRED_OUTPUT_TEMPLATE_JSON",
            "不再重新分析或改写",
            "已由工程层生成并通过同一 Validator"
        ).doesNotContain("STORIES_JSON=", "CHAPTERS_JSON=", "DeepSeek", "GLM", "上一次输出内容");

        var chapterPrompt = builder.buildChapterProduction(new ProjectHistoryPromptBuilder.ChapterSynthesisPromptInput(
            "chapter-one", "2026-01-01T00:00:00Z", "2026-01-02T00:00:00Z", 0, 0, 0,
            List.of(), "membership-fingerprint", List.of()
        )).prompt();
        String chapterRepair = builder.validationRepair(chapterPrompt, "CONTRACT");
        assertThat(ProjectHistoryPromptBuilder.CHAPTER_PROMPT_VERSION)
            .isEqualTo("project-history-chapter-synthesis-v9");
        assertThat(ModelTaskType.PROJECT_HISTORY_CHAPTER_SYNTHESIS.minimalSchema())
            .contains("representedClusterIds");
        assertThat(chapterRepair).contains(
            "必须原样返回 REQUIRED_OUTPUT_TEMPLATE_JSON",
            "已把工程层 deterministicFallback 包装为唯一篇章",
            "REQUIRED_OUTPUT_TEMPLATE_JSON={\"chapters\":[{\"chapterId\":\"chapter-one\""
        ).doesNotContain("CHAPTER_SYNTHESIS_JSON=", "只能使用 OUTPUT_TEMPLATE_JSON", "DeepSeek", "GLM");
    }
}
