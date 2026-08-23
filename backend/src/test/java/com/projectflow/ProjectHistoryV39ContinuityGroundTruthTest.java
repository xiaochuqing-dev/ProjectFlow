package com.projectflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class ProjectHistoryV39ContinuityGroundTruthTest {
    private static final Path GROUND_TRUTH = Path.of(
        "..", "docs", "acceptance-evidence", "v3.9", "continuity-ground-truth.json"
    ).normalize();
    private static final Pattern SENSITIVE = Pattern.compile(
        "(?i)(?:sk-[A-Za-z0-9_-]{20,}|Bearer [A-Za-z0-9._-]{24,}|[A-Za-z]:\\\\Users\\\\)"
    );

    @Test
    void freezesThirtySeparatedCasesBeforeImplementationWithoutPromptLeakage() throws Exception {
        String raw = Files.readString(GROUND_TRUTH, StandardCharsets.UTF_8);
        JsonNode root = new ObjectMapper().readTree(raw);
        assertThat(root.path("schemaVersion").asText())
            .isEqualTo("projectflow-v3.9-continuity-ground-truth-v1");
        assertThat(root.path("productionPromptExcluded").asBoolean()).isTrue();
        assertThat(root.path("mutationPolicy").asText()).isEqualTo("APPEND_ONLY_AMENDMENT");
        assertThat(root.path("cases")).hasSize(30);
        Set<String> ids = new HashSet<>();
        int calibration = 0;
        int holdout = 0;
        for (JsonNode value : root.path("cases")) {
            assertThat(ids.add(value.path("id").asText())).as("duplicate case id").isTrue();
            assertThat(value.path("scenario").asText()).isNotBlank();
            assertThat(value.path("expected")).isNotEmpty();
            if ("CALIBRATION".equals(value.path("split").asText())) calibration++;
            else if ("HOLDOUT".equals(value.path("split").asText())) holdout++;
            else throw new AssertionError("unknown split: " + value.path("split").asText());
        }
        assertThat(calibration).isEqualTo(15);
        assertThat(holdout).isEqualTo(15);
        assertThat(root.path("hardGates").path("noChangeModelRequests").asInt(-1)).isZero();
        assertThat(root.path("hardGates").path("unaffectedStoryIdentityPercent").asInt()).isEqualTo(100);
        assertThat(root.path("hardGates").path("unaffectedThreadIdentityPercent").asInt()).isEqualTo(100);
        assertThat(root.path("hardGates").path("unaffectedChapterIdentityPercent").asInt()).isEqualTo(100);
        assertThat(SENSITIVE.matcher(raw).find()).isFalse();

        String productionBuilder = Files.readString(
            Path.of("src", "main", "java", "com", "projectflow", "service", "ProjectHistoryPromptBuilder.java"),
            StandardCharsets.UTF_8
        );
        assertThat(productionBuilder).doesNotContain("V39-CAL-", "V39-HO-", "continuity-ground-truth");
    }
}
