package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class CorrectedProjectionConsistencyTest {
    private final ProjectHistoryPresentationInvariantValidator validator =
        new ProjectHistoryPresentationInvariantValidator();

    @Test
    void splitMembershipCountsAndRoleRelationsStayConsistentAcrossDerivedViews() {
        var primary = ProjectHistoryContractFixtures.story("story-a", "PRIMARY", "", List.of("story-b"));
        var splitSupport = ProjectHistoryContractFixtures.story("story-b", "SUPPORTING", "story-a", List.of());
        var stories = new LinkedHashMap<>(Map.of(primary.id(), primary, splitSupport.id(), splitSupport));
        var chapter = ProjectHistoryContractFixtures.chapter("chapter-a", List.of(primary, splitSupport), "presentation:test");
        var thread = ProjectHistoryContractFixtures.thread("thread-a", List.of(primary, splitSupport), "presentation:test");

        assertThatCode(() -> validator.validateCorrectedHistory(
            stories, new LinkedHashMap<>(Map.of(chapter.id(), chapter)), new LinkedHashMap<>(Map.of(thread.id(), thread))
        )).doesNotThrowAnyException();

        var missingSplit = ProjectHistoryContractFixtures.chapter("chapter-a", List.of(primary), "presentation:test");
        assertThatThrownBy(() -> validator.validateCorrectedHistory(
            stories, new LinkedHashMap<>(Map.of(missingSplit.id(), missingSplit)),
            new LinkedHashMap<>(Map.of(thread.id(), thread))
        )).isInstanceOf(ProjectHistoryPresentationInvariantValidator.Violation.class)
            .hasMessageContaining("coverage is incomplete");
    }
}
