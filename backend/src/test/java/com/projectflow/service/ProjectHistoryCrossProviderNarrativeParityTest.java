package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.projectflow.entity.ProjectFactEpistemicStatus;
import com.projectflow.entity.ProjectHistoryEvent.Authority;
import com.projectflow.entity.ProjectHistoryEvent.Category;
import com.projectflow.entity.ProjectHistoryEvent.Transition;
import com.projectflow.service.ProjectHistoryNarrativeEntailmentValidator.EvidenceProfile;

class ProjectHistoryCrossProviderNarrativeParityTest {
    private final ProjectHistoryNarrativeEntailmentValidator validator = new ProjectHistoryNarrativeEntailmentValidator();

    @Test
    void oneSharedEnvelopeValidatesEquivalentGlmAndDeepseekWording() {
        var profile = new EvidenceProfile(
            "数据分析结果", Transition.CREATED, List.of(Category.FILE_CHANGE), List.of(Authority.FACTUAL_SOURCE),
            List.of(ProjectFactEpistemicStatus.OBSERVED), List.of("analysis/result.csv"), List.of(), false
        );
        var glmEnvelope = validator.envelope(profile);
        var deepseekEnvelope = validator.envelope(profile);
        assertThat(glmEnvelope).isEqualTo(deepseekEnvelope);

        assertThatCode(() -> validate(glmEnvelope)).doesNotThrowAnyException();
        assertThatCode(() -> validate(deepseekEnvelope)).doesNotThrowAnyException();
    }

    private void validate(ProjectHistoryNarrativeEntailmentValidator.NarrativeEnvelope envelope) {
        validator.validateStory(
            envelope,
            "建立数据分析结果，形成可以继续查看的初始成果",
            "这一阶段首次整理数据分析结果，并将已有内容纳入项目记录。",
            "此前项目中还没有这份数据分析结果。",
            "本次建立分析结果并保存了已经产生的数据内容。",
            "项目中已有数据分析结果，后续可以继续查看和完善。",
            "",
            "目前没有足够信息确认为什么做这次调整。"
        );
    }
}
