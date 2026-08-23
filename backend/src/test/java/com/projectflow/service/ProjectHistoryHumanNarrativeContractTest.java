package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.projectflow.entity.ProjectHistoryEvent.Transition;

class ProjectHistoryHumanNarrativeContractTest {
    private final ProjectHistoryLanguageService language = new ProjectHistoryLanguageService();

    @Test
    void firstLayerReadsLikeAProjectResultInsteadOfAnInternalRecordTranslation() {
        var result = language.fallback(
            Transition.CREATED,
            "research report",
            List.of("research/ResearchReport.md"),
            List.of("docs: establish research report"),
            List.of("CREATED")
        );

        String firstLayer = String.join(" ", result.title(), result.summary(), result.before(), result.change(), result.after());
        assertThat(firstLayer)
            .contains("研究报告")
            .doesNotContain("research report", "ResearchReport.md", "覆盖范围内尚未出现", "变化后");
        assertThat(List.of(result.title(), result.summary(), result.before(), result.change(), result.after()))
            .doesNotHaveDuplicates();
        assertThat(result.change()).isNotEqualTo(result.title());
    }
}
