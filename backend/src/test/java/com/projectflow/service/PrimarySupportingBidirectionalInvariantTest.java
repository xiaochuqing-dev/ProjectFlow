package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class PrimarySupportingBidirectionalInvariantTest {
    private final ProjectHistoryPresentationInvariantValidator validator =
        new ProjectHistoryPresentationInvariantValidator();

    @Test
    void supportingHasExactlyOneActivePrimaryAndThePrimaryPointsBack() {
        var primary = ProjectHistoryContractFixtures.story("story-primary", "PRIMARY", "", List.of("story-support"));
        var support = ProjectHistoryContractFixtures.story("story-support", "SUPPORTING", "story-primary", List.of());

        assertThatCode(() -> validator.validateRoleGraph(List.of(primary, support))).doesNotThrowAnyException();

        var orphan = ProjectHistoryContractFixtures.story("story-support", "SUPPORTING", "", List.of());
        assertThatThrownBy(() -> validator.validateRoleGraph(List.of(primary, orphan)))
            .isInstanceOf(ProjectHistoryPresentationInvariantValidator.Violation.class)
            .hasMessageContaining("inconsistent");
    }
}
