package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class ProjectHistoryHumanSubjectLabelTest {
    private final ProjectHistoryLanguageService language = new ProjectHistoryLanguageService();

    @Test
    void namesScriptAndCheckArtifactsWithoutClaimingTheirRuntimeOutcome() {
        assertThat(language.readableObject("files", List.of("backend/app/schemas/files.py"), List.of()))
            .isEqualTo("文件数据结构定义");
        assertThat(language.readableObject("opaque", List.of("backend/app/opaque.py"), List.of()))
            .isEqualTo("后端代码文件");
        assertThat(language.readableObject("browser-collaboration", List.of("scripts/browser_collaboration.mjs"), List.of()))
            .isEqualTo("协作脚本");
        assertThat(language.readableObject("change-documents", List.of("docs/report.md", "docs/implementation-ci.json", "docs/secret-scan-evidence.json"), List.of()))
            .isEqualTo("项目检查记录与文档");
    }

    @Test
    void translatesMixedLanguageProposalScopeWithoutDroppingOneOfItsObjects() {
        assertThat(language.readableObject("pull-request-7", List.of(),
            List.of("Pull Request #7：Example V4.0-C：Thread 与 Provider 迁入 V4 工作区；说明：来源声明")))
            .contains("长期主题", "模型配置", "迁入", "工作区").doesNotContain("Example", "Thread", "Provider");
        assertThat(language.readableObject("pom", List.of("backend/pom.xml"), List.of()))
            .isEqualTo("构建依赖配置");
        assertThat(language.readableObject("api", List.of("frontend/src/lib/api.ts"), List.of()))
            .isEqualTo("接口客户端");
        assertThat(language.readableObject("change-save-validation", List.of("reports/validation.json", "reports/screenshot.png"), List.of()))
            .isEqualTo("项目检查记录与截图");
    }

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
