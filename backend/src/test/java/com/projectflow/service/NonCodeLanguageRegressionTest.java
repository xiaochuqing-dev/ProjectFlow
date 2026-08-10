package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class NonCodeLanguageRegressionTest {
    private final ProjectHistoryLanguageService language = new ProjectHistoryLanguageService();

    @Test
    void nonCodeArtifactsUseTheirOwnLanguageInsteadOfSoftwareArchitectureTerms() {
        assertThat(language.readableObject("季度复盘", List.of("slides/review.pptx"), List.of()))
            .contains("演示文稿");
        assertThat(language.readableObject("访谈结论", List.of("research/report.docx"), List.of()))
            .contains("文档");
        assertThat(language.readableObject("销售趋势", List.of("analysis/results.csv"), List.of()))
            .contains("数据分析结果");
        assertThat(language.readableObject("品牌首页", List.of("site/index.html"), List.of()))
            .contains("页面");
        assertThat(String.join(" ",
            language.readableObject("季度复盘", List.of("slides/review.pptx"), List.of()),
            language.readableObject("访谈结论", List.of("research/report.docx"), List.of()),
            language.readableObject("销售趋势", List.of("analysis/results.csv"), List.of())
        )).doesNotContain("Controller", "Service", "Repository", "DTO", "后端");
    }
}
