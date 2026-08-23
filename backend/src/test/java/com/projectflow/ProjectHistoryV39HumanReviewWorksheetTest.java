package com.projectflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class ProjectHistoryV39HumanReviewWorksheetTest {
    @Test
    void freezesTwelveScenariosWithoutFabricatingHumanAnswers() throws Exception {
        Path worksheet = Path.of(
            "..", "docs", "acceptance-evidence", "v3.9", "human-continuity-review-worksheet.json"
        ).normalize();
        JsonNode root = new ObjectMapper().readTree(Files.readString(worksheet, StandardCharsets.UTF_8));
        assertThat(root.path("schemaVersion").asText())
            .isEqualTo("projectflow-v3.9-human-continuity-review-v1");
        assertThat(root.path("status").asText()).isEqualTo("NOT_REVIEWED");
        assertThat(root.path("reviewer").isNull()).isTrue();
        assertThat(root.path("reviewedAt").isNull()).isTrue();
        assertThat(root.path("decision").isNull()).isTrue();
        assertThat(root.path("p0TruthfulnessCount").isNull()).isTrue();
        assertThat(root.path("scenarios")).hasSize(12);

        Set<String> ids = new HashSet<>();
        for (JsonNode scenario : root.path("scenarios")) {
            assertThat(ids.add(scenario.path("id").asText())).isTrue();
            assertThat(scenario.path("scenario").asText()).isNotBlank();
            for (String field : Set.of(
                "observedResult", "correctAttachment", "oldHistoryStable", "truthfulUnknownConflict", "comments"
            )) {
                assertThat(scenario.path(field).isNull()).as("%s %s", scenario.path("id").asText(), field).isTrue();
            }
        }
    }
}
