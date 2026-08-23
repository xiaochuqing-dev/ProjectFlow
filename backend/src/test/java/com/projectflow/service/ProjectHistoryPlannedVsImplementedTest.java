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

class ProjectHistoryPlannedVsImplementedTest {
    private final ProjectHistoryNarrativeEntailmentValidator validator = new ProjectHistoryNarrativeEntailmentValidator();

    @Test
    void readmePlanMayDescribeThePlanButCannotBecomeImplementedLogin() {
        var envelope = validator.envelope(new EvidenceProfile(
            "登录方案", Transition.CREATED, List.of(Category.DOCUMENT_VERSION), List.of(Authority.DECLARED),
            List.of(ProjectFactEpistemicStatus.DECLARED), List.of("README.md"), List.of("计划支持邮箱登录"), false
        ));
        assertThat(envelope.claimState()).isEqualTo(ClaimState.PLANNED);

        assertThatThrownBy(() -> validator.validateStory(
            envelope,
            "实现登录方案，让用户可以直接登录",
            "这一阶段完成开发并新增了登录功能。",
            "此前还没有登录方案。",
            "本次实现了登录方案。",
            "用户现在可以使用登录方案。",
            "",
            "目前没有足够信息确认为什么做这次调整。"
        )).isInstanceOf(NarrativeViolation.class);

        validator.validateStory(
            envelope,
            "规划登录方案，明确后续建设方向",
            "现有材料记录了登录方案，但还不能确认已经实现。",
            "此前材料中还没有明确记录登录方案。",
            "这一阶段在项目材料中补充了登录方案。",
            "项目已经记录登录方案的方向，实际状态仍需代码证据确认。",
            "",
            "目前没有足够信息确认为什么做这次调整。"
        );
    }
}
