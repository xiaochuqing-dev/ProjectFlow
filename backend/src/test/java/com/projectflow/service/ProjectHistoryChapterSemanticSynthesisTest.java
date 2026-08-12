package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.projectflow.entity.ProjectHistoryEvent.Transition;

class ProjectHistoryChapterSemanticSynthesisTest {
    private final ProjectHistoryLanguageService language = new ProjectHistoryLanguageService();
    private final ProjectHistoryNarrativeEntailmentValidator validator =
        new ProjectHistoryNarrativeEntailmentValidator();

    @Test
    void chapterLeadsWithThePhaseOutcomeAndKeepsCountsAsMetadata() {
        List<String> stories = List.of(
            "建立环境配置示例，为本地运行提供配置基础",
            "建立前端项目骨架，形成页面开发基础",
            "补充版本库忽略规则，减少无关开发文件",
            "编写项目使用说明，说明项目如何启动"
        );

        String title = language.chapterTitle(stories, List.of(Transition.CREATED), Instant.EPOCH, Instant.EPOCH.plusSeconds(1));
        String summary = language.chapterSummary(stories, 39, 9);

        assertThat(title).contains("建立环境配置示例").doesNotContain("env", "gitignore", "与");
        assertThat(summary)
            .startsWith("这一时期建立环境配置示例")
            .contains("建立前端项目骨架")
            .doesNotContain("39 项主要结果", "9 项支撑工作", "主要包括");
    }

    @Test
    void canonicalPhaseThemeRemainsGroundedWhenItParaphrasesThePrimaryStory() {
        List<String> stories = List.of("形成研究报告并整理研究结论");
        String title = language.chapterTitle(
            stories, List.of(Transition.CREATED), Instant.EPOCH, Instant.EPOCH.plusSeconds(1)
        );
        String summary = language.chapterSummary(stories, 1, 0);

        validator.validateChapter(title, summary, language.chapterGrounding(stories));
    }
}
