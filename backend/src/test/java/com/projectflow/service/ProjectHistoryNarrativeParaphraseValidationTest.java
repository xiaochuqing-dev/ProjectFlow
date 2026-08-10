package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.projectflow.entity.ProjectFactEpistemicStatus;
import com.projectflow.entity.ProjectHistoryEvent.Authority;
import com.projectflow.entity.ProjectHistoryEvent.Category;
import com.projectflow.entity.ProjectHistoryEvent.Transition;
import com.projectflow.service.ProjectHistoryNarrativeEntailmentValidator.EvidenceProfile;
import com.projectflow.service.ProjectHistoryNarrativeEntailmentValidator.NarrativeViolation;

class ProjectHistoryNarrativeParaphraseValidationTest {
    private final ProjectHistoryNarrativeEntailmentValidator validator = new ProjectHistoryNarrativeEntailmentValidator();

    @Test
    void acceptsDistinctHumanWordingInsideTheSemanticEnvelope() {
        var envelope = validator.envelope(profile());
        assertThatCode(() -> validator.validateStory(
            envelope,
            "建立研究报告，形成可以继续阅读的初始成果",
            "这一阶段首次整理研究报告的主要内容，并将其纳入项目记录。",
            "此前项目中还没有这份研究报告。",
            "本次建立报告并补充了已经收集到的研究内容。",
            "项目中已有研究报告，后续可以继续阅读和完善。",
            "",
            "目前没有足够信息确认为什么做这次调整。"
        )).doesNotThrowAnyException();
    }

    @Test
    void rejectsANewEntityThatDoesNotExistInTheAllowedContext() {
        var envelope = validator.envelope(profile());
        assertThatThrownBy(() -> validator.validateStory(
            envelope,
            "建立研究报告，形成可以继续阅读的初始成果",
            "研究报告同时新增 JWT 登录机制。",
            "此前项目中还没有这份研究报告。",
            "本次建立报告并补充了已经收集到的研究内容。",
            "项目中已有研究报告，后续可以继续阅读和完善。",
            "",
            "目前没有足够信息确认为什么做这次调整。"
        )).isInstanceOf(NarrativeViolation.class);
    }

    private static EvidenceProfile profile() {
        return new EvidenceProfile(
            "研究报告", Transition.CREATED, List.of(Category.DOCUMENT_VERSION), List.of(Authority.FACTUAL_SOURCE),
            List.of(ProjectFactEpistemicStatus.OBSERVED), List.of("research/ResearchReport.md"), List.of(), false
        );
    }
}
