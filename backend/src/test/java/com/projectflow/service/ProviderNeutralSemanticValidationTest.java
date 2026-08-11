package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProviderNeutralSemanticValidationTest {
    @Test
    void modelAndEngineeringResponsibilitiesAreDisjoint() {
        assertThat(ProjectHistoryModelOutputContract.ENGINEERING_OWNED_FIELDS).contains(
            "role", "primaryStoryId", "supportingChangeRefs", "storyRefs", "eventRefs", "evidenceRefs",
            "occurredFrom", "occurredTo", "sourceRevision", "projectFact", "verified"
        );
        assertThat(ProjectHistoryModelOutputContract.STORY_FIELDS)
            .containsExactlyInAnyOrder(
                "storyId", "humanTitle", "oneSentenceSummary", "beforeWording", "changeWording", "afterWording",
                "reason", "reasonEvidenceRefs", "unknownWording"
            );
        assertThat(ProjectHistoryModelOutputContract.STORY_COMPATIBILITY_FIELDS)
            .containsAll(ProjectHistoryModelOutputContract.STORY_FIELDS)
            .contains("unknowns")
            .doesNotContainAnyElementsOf(ProjectHistoryModelOutputContract.ENGINEERING_OWNED_FIELDS);
        assertThat(ProjectHistoryModelOutputContract.NARRATIVE_WORDING_FIELDS)
            .containsExactlyInAnyOrder(
                "humanTitle", "oneSentenceSummary", "beforeWording", "changeWording", "afterWording",
                "reason", "unknownWording"
            )
            .doesNotContainAnyElementsOf(ProjectHistoryModelOutputContract.ENGINEERING_OWNED_FIELDS);
        assertThat(ProjectHistoryModelOutputContract.CHAPTER_FIELDS)
            .containsExactlyInAnyOrder("chapterId", "title", "summary");
    }
}
