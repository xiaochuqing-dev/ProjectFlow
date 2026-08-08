package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class ModelPortabilityContractTest {
    @Test
    void oneProviderNeutralPromptContractServesProductionAndEvaluation() {
        ProjectHistoryPromptBuilder builder = new ProjectHistoryPromptBuilder(new ObjectMapper());
        var input = new ProjectHistoryPromptBuilder.PromptInput(
            List.of(new ProjectHistoryPromptBuilder.StoryPromptInput(
                "story-a", "登录流程", "2026-01-01T00:00:00Z", "2026-01-02T00:00:00Z",
                List.of("CREATED"), List.of("新增登录入口"), List.of("登录体验"), List.of("commit:aaaa"),
                List.of(), "此前没有登录入口。", "新增登录入口。", "登录入口已经存在。"
            )),
            List.of(new ProjectHistoryPromptBuilder.ChapterPromptInput(
                "chapter-a", "2026-01-01T00:00:00Z", "2026-01-02T00:00:00Z",
                List.of("story-a"), List.of("EARLIEST_DISCOVERED_EVENT")
            ))
        );

        var production = builder.buildProduction(input);

        assertThat(builder.buildEvaluation(input)).isEqualTo(production);
        assertThat(production.prompt()).doesNotContain(
            "DeepSeek", "GLM", "OpenAI", "Anthropic", "Ground Truth", "expected answer", "caseId"
        );
        assertThat(production.prompt()).contains("只改文字，不改事实或结构", "禁止返回或改写");
    }
}
