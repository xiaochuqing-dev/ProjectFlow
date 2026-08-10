package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.projectflow.entity.ProjectFactEpistemicStatus;
import com.projectflow.entity.ProjectHistoryEvent.Authority;
import com.projectflow.entity.ProjectHistoryEvent.Category;
import com.projectflow.entity.ProjectHistoryEvent.Transition;
import com.projectflow.service.ProjectHistoryNarrativeEntailmentValidator.EvidenceProfile;
import com.projectflow.service.ProjectHistoryNarrativeEntailmentValidator.NarrativeViolation;

class ProjectHistoryDogfoodNarrativeEntailmentTest {
    private final ProjectHistoryHumanSubjectLabelService labels = new ProjectHistoryHumanSubjectLabelService();
    private final ProjectHistoryNarrativeEntailmentValidator validator = new ProjectHistoryNarrativeEntailmentValidator();

    @Test
    void projectSkeletonEvidenceCannotBecomeImplementedLoginOrJwt() {
        List<String> paths = List.of(
            "backend/pom.xml",
            "backend/src/main/java/com/projectflow/HealthController.java",
            "backend/src/main/java/com/projectflow/ProjectFlowApplication.java",
            "backend/src/main/resources/application.yml"
        );
        String subject = labels.label("login", paths, List.of("chore: initialize ProjectFlow skeleton"));
        assertThat(subject).isEqualTo("后端项目骨架");
        var envelope = validator.envelope(new EvidenceProfile(
            subject, Transition.CREATED, List.of(Category.FILE_CHANGE), List.of(Authority.FACTUAL_SOURCE),
            List.of(ProjectFactEpistemicStatus.OBSERVED), paths, List.of("初始化项目骨架"), false
        ));

        assertThatThrownBy(() -> validator.validateStory(
            envelope,
            "新增登录流程，让后端具备用户登录功能",
            "项目首次搭建后端登录流程，包含用户认证和 JWT 令牌。",
            "此前还没有后端登录流程。",
            "本次实现登录功能。",
            "用户现在可以登录。",
            "",
            "目前没有足够信息确认为什么做这次调整。"
        )).isInstanceOf(NarrativeViolation.class);
    }
}
