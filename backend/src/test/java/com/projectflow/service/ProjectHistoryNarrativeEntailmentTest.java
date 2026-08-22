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
