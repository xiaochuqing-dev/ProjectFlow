package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.projectflow.entity.ProjectHistoryEvent.Transition;

class ProjectHistoryNonCodeHumanLanguageTest {
    private final ProjectHistoryLanguageService language = new ProjectHistoryLanguageService();

    @Test
    void reportDataSlidesDesignAndPagesRemainUserResults() {
        assertResult("research report", "report/ResearchReport.md", "研究报告");
        assertResult("data", "analysis/data.csv", "数据分析结果");
        assertResult("quarterly review", "slides/quarterly-review.pptx", "演示文稿");
        assertResult("brand direction", "design/brand-direction.fig", "设计稿");
        assertResult("campaign", "site/index.html", "页面");
    }

    private void assertResult(String subject, String path, String expected) {
        var result = language.fallback(Transition.CREATED, subject, List.of(path), List.of(), List.of("CREATED"));
        assertThat(result.title() + result.summary()).contains(expected).doesNotContain("Supporting", "Controller", path);
    }
}
