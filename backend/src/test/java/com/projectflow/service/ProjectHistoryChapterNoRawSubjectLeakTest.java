package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.projectflow.entity.ProjectHistoryEvent.Transition;

class ProjectHistoryChapterNoRawSubjectLeakTest {
    private final ProjectHistoryLanguageService language = new ProjectHistoryLanguageService();

    @Test
    void chapterFallbackNeverConcatenatesRawOrTruncatedSubjects() {
        List<String> stories = List.of(
            "improve project import and …",
            "v3 文档",
            "v3 2 phase0 embedded mo… 文档",
            "ui design direction 文档"
        );
        String firstLayer = language.chapterTitle(
            stories, List.of(Transition.MODIFIED), Instant.EPOCH, Instant.EPOCH.plusSeconds(1)
        ) + " " + language.chapterSummary(stories, 48, 16);

        assertThat(firstLayer).doesNotContain(
            "improve project import", "v3 2 phase0", "ui design direction", "…", "48 项", "16 项"
        );
    }
}
