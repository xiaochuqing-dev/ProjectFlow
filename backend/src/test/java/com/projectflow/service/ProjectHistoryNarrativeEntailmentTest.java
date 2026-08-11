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
    }

    @Test
    void rejectsGenericChapterWordingEvenWhenTheSubjectIsGrounded() {
        assertThatThrownBy(() -> validator.validateChapter(
            "研究报告相关变化",
            "这一阶段围绕研究报告整理了相关变化。",
            List.of("研究报告已建立并持续更新。")
        )).isInstanceOf(NarrativeViolation.class);
    }
}
