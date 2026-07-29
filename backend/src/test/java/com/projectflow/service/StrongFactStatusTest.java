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
}
