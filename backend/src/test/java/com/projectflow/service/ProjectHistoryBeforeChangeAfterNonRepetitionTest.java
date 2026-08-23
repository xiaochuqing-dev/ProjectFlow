package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.projectflow.entity.ProjectHistoryEvent.Transition;

class ProjectHistoryBeforeChangeAfterNonRepetitionTest {
    private final ProjectHistoryLanguageService language = new ProjectHistoryLanguageService();

    @Test
    void titleSummaryAndBeforeChangeAfterHaveSeparateInformationJobs() {
        var result = language.fallback(
            Transition.MODIFIED,
            "core experience",
            List.of("design/core-experience.md"),
            List.of("refine the core experience"),
            List.of("MODIFIED")
        );

        assertThat(List.of(result.title(), result.summary(), result.before(), result.change(), result.after()))
            .doesNotHaveDuplicates();
        assertThat(result.change()).doesNotContain("形成新的可确认版本");
        assertThat(result.after()).doesNotStartWith("变化后");
    }
}
