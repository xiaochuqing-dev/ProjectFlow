package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.projectflow.entity.ProjectFactEpistemicStatus;
import com.projectflow.entity.ProjectHistoryEvent.Authority;
import com.projectflow.entity.ProjectHistoryEvent.Category;
import com.projectflow.entity.ProjectHistoryEvent.Transition;
import com.projectflow.service.ProjectHistoryNarrativeEntailmentValidator.ClaimState;
import com.projectflow.service.ProjectHistoryNarrativeEntailmentValidator.EvidenceProfile;
import com.projectflow.service.ProjectHistoryNarrativeEntailmentValidator.NarrativeViolation;

class ProjectHistoryNarrativeEntailmentTest {
    private final ProjectHistoryNarrativeEntailmentValidator validator = new ProjectHistoryNarrativeEntailmentValidator();

    @Test
    void processDeclarationCanDescribeItsOwnBehaviorWithoutBorrowingImplementationAuthority() {
        var envelope = validator.envelope(new EvidenceProfile("accounting", "账务服务相关代码", Transition.MODIFIED,
            List.of(new ProjectHistoryNarrativeEntailmentValidator.EvidenceAtom("agent-owned", List.of("accounting"),
                Category.AGENT_RESULT, Transition.MODIFIED, Authority.PROCESS_EVIDENCE, ProjectFactEpistemicStatus.PROCESS_EVIDENCE,
                List.of("src/AccountingService.java"), "发票审核区分 OCR/PDF 提取结果与人工确认；批量导入仍有重复项。",
                List.of("agent-result:billing-review"))), false));
        assertThat(envelope.claimState()).isEqualTo(ClaimState.UNKNOWN);
        assertThat(envelope.supportClass()).isEqualTo("PROCESS_DECLARATION");
        assertThat(envelope.directEvidenceRefs()).isEmpty();
        assertThat(envelope.indirectEvidenceRefs()).containsExactly("agent-result:billing-review");
        assertThat(envelope.humanSafeSourceContext()).anySatisfy(value ->
            assertThat(value).startsWith("开发过程声明").contains("OCR/PDF", "批量导入仍有重复项"));
        String title = "工作记录说明发票审核区分提取结果与人工确认";
        String summary = "开发助手报告了审核展示的调整，并记录批量导入仍有重复项；实际效果尚待独立核实。";
        assertThat(validator.semanticallyUseful(title, summary, envelope)).isTrue();
        validator.validateStory(envelope, title, summary, "此前的审核行为没有独立记录。",
            "开发助手在工作记录中描述了提取结果与人工确认的区分，并保留重复项问题。",
            "上述内容属于开发过程声明，未证明审核流程的实际运行效果。", "", "调整原因仍未知。");
        assertThatThrownBy(() -> validator.validateStory(envelope, title, summary, "此前的审核行为没有独立记录。",
            "开发助手已经实现了审核流程并通过验收。", "来源声明已保留。", "", "调整原因仍未知。"))
            .isInstanceOf(NarrativeViolation.class);
        assertThatThrownBy(() -> validator.validateStory(envelope,
            "工作记录说明库存管理区分生产仓与备用仓", "开发助手报告库存管理的调整并保留核对结果。",
            "此前未知。", "开发助手记录库存变化。", "这是未经独立验证的声明。", "", "原因未知。"))
            .isInstanceOf(NarrativeViolation.class).hasMessageContaining("allowed subject");
    }

    @Test
    void englishCommitStatementsAreBoundedOwnedContextAndCannotWhitelistTechnicalTokens() {
        var envelope = validator.envelope(new EvidenceProfile("report", "审阅报告", Transition.MODIFIED,
            List.of(new ProjectHistoryNarrativeEntailmentValidator.EvidenceAtom("commit-owned", List.of("report"),
                Category.COMMIT, Transition.MODIFIED, Authority.SOURCE_BACKED, ProjectFactEpistemicStatus.OBSERVED,
                List.of("review/report.md"), "test: record invoice acceptance and retain duplicate-import blockers",
                List.of("commit:owned")),
                new ProjectHistoryNarrativeEntailmentValidator.EvidenceAtom("commit-unrelated", List.of("warehouse"),
                    Category.COMMIT, Transition.MODIFIED, Authority.SOURCE_BACKED, ProjectFactEpistemicStatus.OBSERVED,
                    List.of("warehouse/report.md"), "feat: warehouse is production ready", List.of("commit:other"))), false));
        assertThat(envelope.humanSafeSourceContext()).anySatisfy(value -> assertThat(value)
            .startsWith("提交者声明").contains("duplicate-import blockers"));
        assertThat(envelope.humanSafeSourceContext()).noneMatch(value -> value.contains("warehouse"));
        assertThat(envelope.claimState()).isEqualTo(ClaimState.OBSERVED);
        assertThat(validator.containsFirstLayerLeak("记录 duplicate-import 的审阅报告", envelope.humanSafeSourceContext())).isTrue();
        assertThatThrownBy(() -> validator.validateStateCeiling(envelope.claimState(), "发票系统已经通过验收"))
            .isInstanceOf(NarrativeViolation.class);
    }

    @Test
    void boundedSourceContextIncludesLatestDocumentContentWithoutRaisingAuthority() {
        var atoms = new java.util.ArrayList<ProjectHistoryNarrativeEntailmentValidator.EvidenceAtom>();
        for (int i = 0; i < 8; i++) atoms.add(new ProjectHistoryNarrativeEntailmentValidator.EvidenceAtom(
            "event-" + i, List.of("readme"), Category.FILE_CHANGE, Transition.MODIFIED, Authority.SOURCE_BACKED,
            ProjectFactEpistemicStatus.OBSERVED, List.of("README.md"), "文档文字变化：加入说明第" + i + "项", List.of("file:README.md")));
        var envelope = validator.envelope(new EvidenceProfile("readme", "项目使用说明", Transition.MODIFIED, atoms, false));
        assertThat(envelope.humanSafeSourceContext()).hasSize(6).contains("文档文字变化：加入说明第7项")
            .doesNotContain("文档文字变化：加入说明第0项");
        assertThat(envelope.claimState()).isEqualTo(ClaimState.OBSERVED);
        assertThatThrownBy(() -> validator.validateStateCeiling(envelope.claimState(), "系统已经通过验收并发布上线"))
            .isInstanceOf(NarrativeViolation.class);
    }

    @Test
    void historicalRemovalCannotClaimTheCurrentWorktreeStillLacksTheArtifact() {
        var language = new ProjectHistoryLanguageService();
        var envelope = validator.envelope(new EvidenceProfile(
            "图片资料", Transition.REMOVED, List.of(Category.FILE_CHANGE), List.of(Authority.FACTUAL_SOURCE),
            List.of(ProjectFactEpistemicStatus.OBSERVED), List.of("assets/overview.png"), List.of(), false
        ));
        var wording = language.fallback(ClaimState.REMOVED, Transition.REMOVED, "overview",
            List.of("assets/overview.png"), List.of(), List.of("REMOVED"));
        validator.validateStory(envelope, wording.title(), wording.summary(), wording.before(), wording.change(),
            wording.after(), "", "后续是否恢复尚未确认。");
        assertThat(wording.change()).contains("对应版本");
        assertThatThrownBy(() -> validator.validateStory(envelope,
            "移除图片资料，当前项目不再保留这项内容", wording.summary(), wording.before(), wording.change(),
            wording.after(), "", "后续是否恢复尚未确认。"))
            .isInstanceOf(NarrativeViolation.class).hasMessageContaining("current worktree");
        assertThatThrownBy(() -> validator.validateChapter(
            "移除图片资料，当前项目不再保留这项内容", wording.summary(), List.of(wording.title())))
            .isInstanceOf(NarrativeViolation.class).hasMessageContaining("current worktree");
    }

    @Test
    void configurationEvidenceCannotClaimDeploymentOrProductionReadiness() {
        var envelope = validator.envelope(new EvidenceProfile(
            "环境配置示例", Transition.CREATED, List.of(Category.FILE_CHANGE), List.of(Authority.FACTUAL_SOURCE),
            List.of(ProjectFactEpistemicStatus.OBSERVED), List.of(".env.example"), List.of(), false
        ));

        assertThat(envelope.claimState()).isEqualTo(ClaimState.CONFIGURED);
        assertThatThrownBy(() -> validator.validateStory(
            envelope,
            "补充环境配置示例，完善本地配置基础",
            "这一阶段增加了环境配置示例，项目已经完成生产部署。",
            "此前还没有环境配置示例。",
            "本次加入了可参考的配置项。",
            "环境配置示例现在可供本地设置参考。",
            "",
            "目前没有足够信息确认为什么做这次调整。"
        )).isInstanceOf(NarrativeViolation.class);
    }

    @Test
    void observedArtifactMayDescribeItsCreationWithoutClaimingVerification() {
        var envelope = validator.envelope(new EvidenceProfile(
            "研究报告", Transition.CREATED, List.of(Category.DOCUMENT_VERSION), List.of(Authority.FACTUAL_SOURCE),
            List.of(ProjectFactEpistemicStatus.OBSERVED), List.of("research/ResearchReport.md"), List.of(), false
        ));

        assertThat(envelope.claimState()).isEqualTo(ClaimState.OBSERVED);
    }

    @Test
    void rawReasonContextCannotWhitelistFixtureTokensInTheFirstLayer() {
        assertThat(validator.containsFirstLayerLeak(
            "围绕 phase0 embedded model 整理项目成果记录",
            List.of("项目成果记录", "原始 phase0 embedded model 说明")
        )).isTrue();
        assertThat(validator.containsFirstLayerLeak(
            "围绕项目结果主题00000内容000整理当前成果",
            List.of("项目结果主题00000内容000")
        )).isTrue();
    }

    @Test
    void rejectsGenericChapterWordingEvenWhenTheSubjectIsGrounded() {
        assertThatThrownBy(() -> validator.validateChapter(
            "研究报告相关变化",
            "这一阶段围绕研究报告整理了相关变化。",
            List.of("研究报告已建立并持续更新。")
        )).isInstanceOf(NarrativeViolation.class);
    }

    @Test
    void rejectsVagueChapterThatOnlySaysAThemeWasAdvancedAndImproved() {
        assertThatThrownBy(() -> validator.validateChapter(
            "围绕项目基础建设推进阶段成果",
            "这一时期主要围绕项目基础建设推进，相关成果逐步形成并得到完善。",
            List.of("建立前端项目骨架，补充本地启动配置。")
        )).isInstanceOf(NarrativeViolation.class);
    }

    @Test
    void acceptsChapterThatNamesAConcretePrimaryOutcome() {
        validator.validateChapter(
            "建立前端项目骨架并补充启动配置",
            "这一时期建立了前端项目骨架，并补充本地启动所需的配置记录。",
            List.of("建立前端项目骨架，补充本地启动配置。")
        );
    }

    @Test
    void detectsProviderTitlesThatOmitAnExplicitSupportedResult() {
        assertThat(validator.hasActionObjectResult(
            "对研究报告执行了创建、修改、拆分与移除等操作并留下变化记录",
            "涉及研究报告在7月初的多项变动，具体效果尚需更多来源确认"
        )).isFalse();
        assertThat(validator.hasActionObjectResult(
            "编写成果导出功能的代码",
            "涵盖成果导出功能的代码创建与修改"
        )).isFalse();
        assertThat(validator.hasActionObjectResult(
            "调整研究报告并形成可核对的变更记录",
            "研究报告的结构变化已保留供后续核对"
        )).isTrue();
    }

    @Test
    void acceptsConservativeDeterministicOutcomesForPlannedAndConfiguredStories() {
        assertThat(validator.hasActionObjectResult(
            "规划项目交付节奏，明确后续建设方向",
            "现有材料记录了项目交付节奏的目标和范围，但还不能确认已经实现。"
        )).isTrue();
        assertThat(validator.hasActionObjectResult(
            "补充环境配置示例，完善项目配置基础",
            "这一阶段增加了环境配置示例，为后续本地设置提供参考。"
        )).isTrue();
    }
}
