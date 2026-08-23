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

class ProjectHistoryDeclaredVsVerifiedTest {
    private final ProjectHistoryNarrativeEntailmentValidator validator = new ProjectHistoryNarrativeEntailmentValidator();

    @Test
    void apiDesignDeclarationCannotClaimTheEndpointWasVerified() {
        var envelope = validator.envelope(new EvidenceProfile(
            "登录接口设计", Transition.CREATED, List.of(Category.DOCUMENT_VERSION), List.of(Authority.DECLARED),
            List.of(ProjectFactEpistemicStatus.DECLARED), List.of("docs/api-design.md"), List.of("设计登录接口"), false
        ));
        assertThat(envelope.claimState()).isEqualTo(ClaimState.DECLARED);

        assertThatThrownBy(() -> validator.validateStory(
            envelope,
            "说明登录接口设计，形成可查阅的方案",
            "登录接口设计已经验证通过。",
            "此前还没有登录接口设计说明。",
            "本次在设计材料中补充了接口说明。",
            "项目已记录登录接口设计，实际实现仍待确认。",
            "",
            "目前没有足够信息确认为什么做这次调整。"
        )).isInstanceOf(NarrativeViolation.class);
    }
}
