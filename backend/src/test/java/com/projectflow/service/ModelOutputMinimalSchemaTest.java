package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class ModelOutputMinimalSchemaTest {
    @Test
    void registeredSchemaContainsOnlyModelOwnedWordingFields() throws Exception {
        JsonNode root = new ObjectMapper().readTree(ModelTaskType.PROJECT_HISTORY_SYNTHESIS.minimalSchema());

        assertThat(fields(root)).isEqualTo(ProjectHistoryModelOutputContract.ROOT_FIELDS);
        assertThat(fields(root.path("stories").get(0))).isEqualTo(ProjectHistoryModelOutputContract.STORY_FIELDS);
        assertThat(fields(root.path("chapters").get(0))).isEqualTo(ProjectHistoryModelOutputContract.CHAPTER_FIELDS);
        assertThat(ProjectHistoryModelOutputContract.STORY_FIELDS)
            .doesNotContainAnyElementsOf(ProjectHistoryModelOutputContract.ENGINEERING_OWNED_FIELDS);
        assertThat(ProjectHistoryModelOutputContract.CHAPTER_FIELDS)
            .doesNotContainAnyElementsOf(ProjectHistoryModelOutputContract.ENGINEERING_OWNED_FIELDS);
    }

    private static Set<String> fields(JsonNode node) {
        Set<String> result = new LinkedHashSet<>();
        node.fieldNames().forEachRemaining(result::add);
        return result;
    }
}
