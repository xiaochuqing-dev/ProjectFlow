package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class RealDogfoodRoleGraphConsistencyTest {
    private final ProjectHistoryPresentationInvariantValidator validator =
        new ProjectHistoryPresentationInvariantValidator();

    @Test
    void fixedDogfoodScaleKeepsTwoWayRoleGraphUnderEngineeringOwnership() {
        Map<String, List<String>> refsByPrimary = new LinkedHashMap<>();
        for (int index = 0; index < 227; index++) refsByPrimary.put("primary-" + index, new ArrayList<>());
        for (int index = 0; index < 110; index++) {
            refsByPrimary.get("primary-" + (index % 227)).add("support-" + index);
        }
        List<com.projectflow.dto.ProjectHistoryDtos.ChangeStory> stories = new ArrayList<>();
        refsByPrimary.forEach((id, refs) -> stories.add(ProjectHistoryContractFixtures.story(id, "PRIMARY", "", refs)));
        for (int index = 0; index < 110; index++) {
            stories.add(ProjectHistoryContractFixtures.story(
                "support-" + index, "SUPPORTING", "primary-" + (index % 227), List.of()
            ));
        }

        assertThatCode(() -> validator.validateRoleGraph(stories)).doesNotThrowAnyException();

        List<com.projectflow.dto.ProjectHistoryDtos.ChangeStory> broken = new ArrayList<>(stories);
        broken.set(227, ProjectHistoryContractFixtures.story("support-0", "SUPPORTING", "primary-1", List.of()));
        assertThatThrownBy(() -> validator.validateRoleGraph(broken))
            .isInstanceOf(ProjectHistoryPresentationInvariantValidator.Violation.class)
            .hasMessageContaining("inconsistent");
    }
}
