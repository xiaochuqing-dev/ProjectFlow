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
                "storyId", "humanTitle", "oneSentenceSummary", "reason", "reasonEvidenceRefs", "unknowns"
            );
        assertThat(ProjectHistoryModelOutputContract.CHAPTER_FIELDS)
            .containsExactlyInAnyOrder("chapterId", "title", "summary");
    }
}
