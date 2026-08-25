package com.projectflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class ProjectHistoryV39GroundTruthExecutionMapTest {
    private static final Path REPOSITORY = Path.of("..").toAbsolutePath().normalize();
    private static final Path GROUND_TRUTH = REPOSITORY.resolve(
        "docs/acceptance-evidence/v3.9/continuity-ground-truth.json"
    );
    private static final Path EXECUTION_MAP = REPOSITORY.resolve(
        "docs/acceptance-evidence/v3.9/continuity-ground-truth-execution-map.json"
    );
    private static final Pattern SENSITIVE = Pattern.compile(
        "(?i)(?:sk-[A-Za-z0-9_-]{20,}|Bearer [A-Za-z0-9._-]{24,}|[A-Za-z]:\\\\Users\\\\)"
    );

    @Test
    void bindsEveryFrozenCaseToAnExistingExecutableTestWithoutChangingItsSplit() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode groundTruth = mapper.readTree(Files.readString(GROUND_TRUTH, StandardCharsets.UTF_8));
        String mapText = Files.readString(EXECUTION_MAP, StandardCharsets.UTF_8);
        JsonNode executionMap = mapper.readTree(mapText);

        assertThat(executionMap.path("schemaVersion").asText())
            .isEqualTo("projectflow-v3.9-continuity-ground-truth-execution-map-v1");
        assertThat(executionMap.path("groundTruth").asText())
            .isEqualTo("docs/acceptance-evidence/v3.9/continuity-ground-truth.json");
        assertThat(SENSITIVE.matcher(mapText).find()).isFalse();

        Map<String, String> frozenSplits = new HashMap<>();
        for (JsonNode frozenCase : groundTruth.path("cases")) {
            frozenSplits.put(frozenCase.path("id").asText(), frozenCase.path("split").asText());
        }

        Set<String> mappedIds = new HashSet<>();
        for (JsonNode mappedCase : executionMap.path("cases")) {
            String id = mappedCase.path("id").asText();
            assertThat(mappedIds.add(id)).as("duplicate mapped case: %s", id).isTrue();
            assertThat(frozenSplits).as("unknown mapped case: %s", id).containsKey(id);
            assertThat(mappedCase.path("split").asText()).isEqualTo(frozenSplits.get(id));
            assertThat(mappedCase.path("tests")).as("missing tests for %s", id).isNotEmpty();

            for (JsonNode reference : mappedCase.path("tests")) {
                String source = reference.path("source").asText();
                String method = reference.path("method").asText();
                String gate = reference.path("gate").asText();
                assertThat(gate).isIn("maven-test", "python-unittest");
                assertThat(source).doesNotContain("..", "\\");
                Path sourceFile = REPOSITORY.resolve(source).normalize();
                assertThat(sourceFile).as("source for %s", id).startsWith(REPOSITORY).isRegularFile();
                String sourceText = Files.readString(sourceFile, StandardCharsets.UTF_8);
                assertThat(sourceText).as("method %s for %s", method, id).contains(method + "(");
            }
        }

        assertThat(mappedIds).containsExactlyInAnyOrderElementsOf(frozenSplits.keySet());
        assertThat(mappedIds).hasSize(30);
    }
}
