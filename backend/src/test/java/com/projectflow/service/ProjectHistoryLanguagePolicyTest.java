package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.projectflow.entity.ProjectHistoryEvent.Category;
import com.projectflow.entity.ProjectHistoryEvent.Transition;

class ProjectHistoryLanguagePolicyTest {
    private final ProjectHistoryLanguageService language = new ProjectHistoryLanguageService();

    @Test
    void describesSoftwareAndNonCodeArtifactsWithoutProjectSpecificOrInternalLanguage() {
        assertNeutral("quarterly-review", "slides/quarterly-review.pptx", "演示文稿");
        assertNeutral("research-conclusion", "paper/research-conclusion.md", "文档");
        assertNeutral("revenue-summary", "analysis/revenue-summary.csv", "数据分析结果");
        assertNeutral("final-cut", "media/final-cut.mp4", "视频");
        assertNeutral("brand-system", "design/brand-system.fig", "设计稿");
        assertNeutral("campaign", "site/index.html", "页面");
        assertNeutral("business-plan", "proposal/business-plan.docx", "文档");
        assertNeutral("auth", "backend/src/AuthController.java", "登录流程");
        assertNeutral("export", "src/ExportService.java", "成果导出");

        String customerService = language.readableObject(
            "customer-service", List.of("research/customer-service.md"), List.of("customer service findings")
        );
        assertThat(customerService).contains("客户服务研究").doesNotContain("customer service", "项目核心结果");
    }

    @Test
    void treatsMarkdownAndJsonAsPotentialPrimaryResultsWhileKeepingExplicitValidationAsSupport() {
        assertThat(language.supporting(
            List.of(Category.DOCUMENT_VERSION), List.of("report/final-report.md"),
            List.of("complete final report"), List.of(Transition.CREATED), false
        )).isFalse();
        assertThat(language.supporting(
            List.of(Category.FILE_CHANGE), List.of("analysis/results.json"),
            List.of("publish analysis results"), List.of(Transition.CREATED), false
        )).isFalse();
        assertThat(language.supporting(
            List.of(Category.VALIDATION), List.of("tests/report-validation.json"),
            List.of("test report output"), List.of(Transition.MODIFIED), false
        )).isTrue();
        assertThat(language.supportingCommitLabel("fix")).isTrue();
        assertThat(language.supportingCommitLabel("update")).isTrue();
        assertThat(language.supportingCommitLabel("fix checkout redirect")).isFalse();
        assertThat(language.supportingCommitLabel("docs: publish research conclusion")).isFalse();
    }

    @Test
    void chapterLanguageSeparatesPrimaryResultsFromSupportingWork() {
        String summary = language.chapterSummary(
            List.of("新增研究结论，形成首个可确认版本", "更新数据图表，形成新的可确认版本"),
            5, 35
        );
        assertThat(summary).contains("新增研究结论", "更新数据图表", "支撑工作保留在工程详情中")
            .doesNotContain("5 项主要结果", "35 项支撑工作", "40 项可读成果");

        String first = language.chapterTitle(
            List.of("新增季度汇报演示文稿，形成首个可确认版本"),
            List.of(Transition.CREATED), Instant.EPOCH, Instant.EPOCH
        );
        String second = language.chapterTitle(
            List.of("更新收入分析数据结果，形成新的可确认版本"),
            List.of(Transition.MODIFIED), Instant.EPOCH.plusSeconds(60), Instant.EPOCH.plusSeconds(120)
        );
        assertThat(first).contains("新增季度汇报演示文稿");
        assertThat(second).contains("更新收入分析数据结果");
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void firstLayerDoesNotRepeatRawTechnicalLabelsOrUnknownReasonPerCommit() {
        var presentation = language.fallback(
            Transition.MODIFIED, "project-history", List.of("backend/src/ProjectHistoryController.java"),
            List.of("fix: update ProjectHistoryController"), List.of("MODIFIED")
        );
        String firstLayer = String.join(" ", presentation.title(), presentation.summary(), presentation.before(),
            presentation.change(), presentation.after());
        assertThat(firstLayer).doesNotContain(
            "ProjectHistoryController", "backend/src", "Controller", "后端", "能力", "相关来源说明包括"
        );
        assertThat(language.commitSummary(
            "fix: update ProjectHistoryController", Transition.MODIFIED,
            List.of("backend/src/ProjectHistoryController.java")
        )).doesNotContain("ProjectHistoryController", "具体原因没有可靠说明");

        var currentFile = language.fallback(
            Transition.UNKNOWN_TRANSITION, "research-report", List.of("reports/research-report.md"),
            List.of(), List.of("UNKNOWN_TRANSITION")
        );
        assertThat(currentFile.title()).contains("研究报告", "当前能够确认的变化");
        assertThat(currentFile.summary()).contains("现有来源记录", "具体范围")
            .doesNotContain("能力", "后端", "research-report");
    }

    private void assertNeutral(String subject, String path, String expectedObject) {
        var result = language.fallback(Transition.CREATED, subject, List.of(path), List.of(), List.of("CREATED"));
        String firstLayer = String.join(" ", result.title(), result.summary(), result.before(), result.change(), result.after());
        assertThat(firstLayer).contains(expectedObject).doesNotContain(
            "项目开始具备这项能力", "当前行为得到更新", "旧能力", "后端区域", "前端区域",
            "Controller", "Service", "Repository", "DTO", "Entity"
        );
    }
}
