package com.projectflow.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Deterministic contract checks for the blind V3.9 reviewer input. */
class IndependentSemanticReviewPackageTest {
    private static final Set<String> SCENARIO_FIELDS = Set.of(
        "id", "oldBoundedHistory", "newDelta", "candidateContinuation",
        "evidenceSummaries", "storyThreadChapterRefs", "conflicts", "unknowns"
    );
    private static final Set<String> FORBIDDEN_REVIEW_INPUT_FIELDS = Set.of(
        "expected", "expectedAnswer", "implementationPass", "testResult", "otherModelJudgement",
        "modelJudgement", "frozenExpected", "acceptanceDecision", "scenario"
    );
    private static final List<String> FORBIDDEN_ANSWER_HINTS = List.of(
        "no-change refresh",
        "one new change continues an existing Story",
        "a new Story continues an existing Thread",
        "an unrelated change remains independent",
        "coherent Chapter continuation and heterogeneous boundary",
        "a correction survives safe additive continuation",
        "rewrite invalidates an unsafe correction target",
        "Current Project State and Agent Context advance together",
        "Agent Result is later refined by Git Evidence without self-promotion",
        "no-Git and non-code project continuity",
        "Provider failure, fallback and resume",
        "Obsidian no-op and affected managed-block update"
    );
    private static final List<String> SCENARIO_IDS = List.of(
        "V39-HUMAN-01", "V39-HUMAN-02", "V39-HUMAN-03", "V39-HUMAN-04",
        "V39-HUMAN-05", "V39-HUMAN-06", "V39-HUMAN-07", "V39-HUMAN-08",
        "V39-HUMAN-09", "V39-HUMAN-10", "V39-HUMAN-11", "V39-HUMAN-12"
    );
    private static final Pattern ABSOLUTE_PATH = Pattern.compile(
        "(?i)(?:[a-z]:[\\\\/]|(?:^|[\\s\"'])/(?:home|users|root|tmp)/)"
    );
    private static final Pattern SECRET_MARKER = Pattern.compile(
        "(?i)(?:bearer\\s+|api[_-]?key\\s*[:=]|authorization\\s*[:=]|-----begin .*private key)"
    );

    @Test
    void packageContainsExactlyTheTwelveHumanScenarioInputs() throws Exception {
        JsonNode root = readJson(packagePath());
        validateStructure(root);
    }

    @Test
    void blindPackageHasNoMachineSecretsAbsolutePathsOrAnswerLabels() throws Exception {
        String content = Files.readString(packagePath());
        JsonNode root = new ObjectMapper().findAndRegisterModules().readTree(content);
        validateSafety(root, content);
    }

    static JsonNode loadAndValidatePackage(ObjectMapper mapper) throws IOException {
        String content = Files.readString(packagePath());
        JsonNode root = mapper.readTree(content);
        validateStructure(root);
        validateSafety(root, content);
        return root;
    }

    private static void validateStructure(JsonNode root) {
        assertThat(root.path("schemaVersion").asText()).isEqualTo(
            "projectflow-v3.9-independent-semantic-review-input-v1"
        );
        assertThat(root.path("packageKind").asText()).isEqualTo("BLIND_SEMANTIC_REVIEW");
        assertThat(root.path("scenarioCount").asInt()).isEqualTo(SCENARIO_IDS.size());
        assertThat(root.path("scenarios").isArray()).isTrue();
        assertThat(root.path("scenarios")).hasSize(SCENARIO_IDS.size());

        List<String> ids = new ArrayList<>();
        for (JsonNode scenario : root.path("scenarios")) {
            Set<String> fields = new LinkedHashSet<>();
            scenario.fieldNames().forEachRemaining(fields::add);
            assertThat(fields).containsExactlyInAnyOrderElementsOf(SCENARIO_FIELDS);
            assertThat(scenario.path("id").asText()).isNotBlank();
            assertThat(scenario.path("oldBoundedHistory").isObject()).isTrue();
            assertThat(scenario.path("newDelta").isObject()).isTrue();
            assertThat(scenario.path("candidateContinuation").isObject()).isTrue();
            assertThat(scenario.path("evidenceSummaries").isArray()).isTrue();
            assertThat(scenario.path("storyThreadChapterRefs").isObject()).isTrue();
            assertThat(scenario.path("conflicts").isArray()).isTrue();
            assertThat(scenario.path("unknowns").isArray()).isTrue();
            assertThat(scenario.toString().length()).as("bounded scenario input")
                .isLessThanOrEqualTo(6_000);
            ids.add(scenario.path("id").asText());
        }
        assertThat(ids).containsExactlyElementsOf(SCENARIO_IDS);
        assertThat(new HashSet<>(ids)).hasSize(SCENARIO_IDS.size());
    }

    private static void validateSafety(JsonNode root, String content) {
        assertThat(content.length()).isLessThan(60_000);
        assertThat(ABSOLUTE_PATH.matcher(content).find()).isFalse();
        assertThat(SECRET_MARKER.matcher(content).find()).isFalse();
        assertThat(content).doesNotContain("V39-CAL-");
        assertThat(content).doesNotContain("V39-HO-");
        assertThat(content).doesNotContain(FORBIDDEN_ANSWER_HINTS.toArray(String[]::new));

        Set<String> allFields = new LinkedHashSet<>();
        collectObjectFields(root, allFields);
        assertThat(allFields).doesNotContainAnyElementsOf(FORBIDDEN_REVIEW_INPUT_FIELDS);
    }

    @Test
    void humanWorksheetRemainsUnreviewedAndBlank() throws Exception {
        JsonNode worksheet = readJson(worksheetPath());
        assertThat(worksheet.path("status").asText()).isEqualTo("NOT_REVIEWED");
        assertThat(worksheet.path("reviewer").isNull()).isTrue();
        assertThat(worksheet.path("reviewedAt").isNull()).isTrue();
        assertThat(worksheet.path("decision").isNull()).isTrue();
        for (JsonNode scenario : worksheet.path("scenarios")) {
            assertThat(scenario.path("observedResult").isNull()).isTrue();
            assertThat(scenario.path("correctAttachment").isNull()).isTrue();
            assertThat(scenario.path("oldHistoryStable").isNull()).isTrue();
            assertThat(scenario.path("truthfulUnknownConflict").isNull()).isTrue();
            assertThat(scenario.path("comments").isNull()).isTrue();
        }
    }

    private static void collectObjectFields(JsonNode node, Set<String> fields) {
        if (node == null) return;
        if (node.isObject()) {
            node.fieldNames().forEachRemaining(fields::add);
            node.elements().forEachRemaining(child -> collectObjectFields(child, fields));
            return;
        }
        if (node.isArray()) node.elements().forEachRemaining(child -> collectObjectFields(child, fields));
    }

    private static JsonNode readJson(Path path) throws IOException {
        return new ObjectMapper().findAndRegisterModules().readTree(Files.readString(path));
    }

    private static Path packagePath() {
        return existingPath(
            Path.of("..", "docs", "acceptance-evidence", "v3.9", "independent-semantic-review-package.json"),
            Path.of("docs", "acceptance-evidence", "v3.9", "independent-semantic-review-package.json")
        );
    }

    private static Path worksheetPath() {
        return existingPath(
            Path.of("..", "docs", "acceptance-evidence", "v3.9", "human-continuity-review-worksheet.json"),
            Path.of("docs", "acceptance-evidence", "v3.9", "human-continuity-review-worksheet.json")
        );
    }

    private static Path existingPath(Path first, Path second) {
        if (Files.isRegularFile(first)) return first;
        if (Files.isRegularFile(second)) return second;
        throw new IllegalStateException("V3.9 blind review fixture is missing");
    }
}
