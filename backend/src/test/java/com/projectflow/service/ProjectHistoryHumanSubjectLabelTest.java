package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class ProjectHistoryHumanSubjectLabelTest {
    private final ProjectHistoryLanguageService language = new ProjectHistoryLanguageService();

    @Test
    void replacesBareTechnicalSubjectsWithEvidenceBoundedHumanConcepts() {
        assertThat(language.readableObject("research report", List.of("research/ResearchReport.md"), List.of()))
            .isEqualTo("研究报告");
        assertThat(language.readableObject("project outcome", List.of("outcomes/summary.md"), List.of()))
            .isEqualTo("项目成果");
        assertThat(language.readableObject("core experience", List.of("design/core-experience.md"), List.of()))
            .isEqualTo("核心使用体验");
        assertThat(language.readableObject("env example", List.of(".env.example"), List.of()))
            .isEqualTo("环境配置示例");
        assertThat(language.readableObject("gitignore", List.of(".gitignore"), List.of()))
            .isEqualTo("版本库忽略规则");
        assertThat(language.readableObject("readme", List.of("README.md"), List.of()))
            .isEqualTo("项目使用说明");
        assertThat(language.readableObject("data", List.of("analysis/data.csv"), List.of()))
            .isEqualTo("数据分析结果");
    }

    @Test
    void replacesFixtureAndTruncatedSubjectsInsteadOfDisplayingThem() {
        assertThat(language.readableObject("outcome00000 part000", List.of("outcomes/outcome-00000.md"), List.of()))
            .isEqualTo("项目成果记录");
        assertThat(language.readableObject(
            "项目结果主题00000内容000", List.of("results/项目结果主题00000内容000.java"), List.of()
        )).isEqualTo("项目成果记录");
        assertThat(language.readableObject("improve project import and …", List.of("docs/import.md"), List.of()))
            .doesNotContain("improve", "…");
        assertThat(language.readableObject("v3 2 phase0 embedded mo…", List.of("docs/v3-phase0.md"), List.of()))
            .doesNotContain("v3 2", "phase0", "…");
    }

    @Test
    void doesNotTurnAnUnanchoredSkeletonCommitIntoImplementedLogin() {
        assertThat(language.readableObject(
            "login",
            List.of("frontend/next-env.d.ts", "frontend/next.config.ts", "frontend/package.json"),
            List.of("chore: initialize project skeleton")
        )).isEqualTo("前端项目骨架");
        assertThat(language.readableObject(
            "login",
            List.of("backend/pom.xml", "backend/src/main/java/com/projectflow/HealthController.java", "backend/src/main/resources/application.yml"),
            List.of("chore: initialize project skeleton")
        )).isEqualTo("后端项目骨架");
    }
}
