package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.projectflow.entity.ProjectFactEpistemicStatus;

class StrongFactStatusTest {
    @Test
    void onlyObservedAndVerifiedAreStrongFacts() {
        assertThat(ProjectFactEpistemicStatus.values()).filteredOn(ProjectFactEpistemicStatus::isStrongFact)
            .containsExactly(ProjectFactEpistemicStatus.OBSERVED, ProjectFactEpistemicStatus.VERIFIED);
    }

    @Test
    void legacyAndModelLabelsNormalizeWithoutCreatingNewAuthority() {
        assertThat(ProjectFactEpistemicStatus.fromAnalysisLabel("USER_ASSERTION"))
            .isEqualTo(ProjectFactEpistemicStatus.DECLARED);
        assertThat(ProjectFactEpistemicStatus.fromAnalysisLabel("MODEL_SUMMARY"))
            .isEqualTo(ProjectFactEpistemicStatus.INFERRED);
        assertThat(ProjectFactEpistemicStatus.fromAnalysisLabel("PROCESS_METADATA"))
            .isEqualTo(ProjectFactEpistemicStatus.PROCESS_EVIDENCE);
        assertThat(ProjectFactEpistemicStatus.fromAnalysisLabel("invented_status").isStrongFact()).isFalse();
    }
}
