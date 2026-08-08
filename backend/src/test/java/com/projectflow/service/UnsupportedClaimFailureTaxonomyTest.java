package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class UnsupportedClaimFailureTaxonomyTest {
    private static final Set<String> GLM_CASES = Set.of(
        "cal-small-five-commit-project", "cal-create-modify-delete-restore", "cal-multi-commit-one-change",
        "cal-primary-supporting", "cal-non-code-project", "cal-technical-leakage",
        "holdout-rename-move-split-merge", "holdout-unrelated-commit", "holdout-document-project",
        "holdout-thousand-events", "holdout-generic-message", "holdout-agent-evidence"
    );
    private static final Set<String> DEEPSEEK_CASES = Set.of(
        "cal-small-five-commit-project", "cal-create-modify-delete-restore", "cal-non-code-project",
        "cal-conflict-preservation", "cal-technical-leakage", "holdout-chaotic-history",
        "holdout-rename-move-split-merge", "holdout-unrelated-commit", "holdout-document-project",
        "holdout-thousand-events", "holdout-generic-message", "holdout-agent-evidence"
    );

    @Test
    void preservesAllHistoricalFailuresWithoutInventingMissingSubcauses() throws Exception {
        Path manifest = Path.of("..", "docs", "acceptance-evidence", "v3.8.5", "provider-failure-taxonomy.json").normalize();
        assertThat(Files.isRegularFile(manifest)).isTrue();
        JsonNode root = new ObjectMapper().readTree(manifest.toFile());

        assertThat(root.path("taxonomy").size()).isEqualTo(10);
        assertThat(root.path("taxonomy").fieldNames()).toIterable()
            .containsExactlyInAnyOrder("A", "B", "C", "D", "E", "F", "G", "H", "I", "J");
        JsonNode failures = root.path("historicalUnsupportedClaims");
        assertThat(failures.size()).isEqualTo(24);
        assertThat(caseIds(failures, "GLM")).isEqualTo(GLM_CASES);
        assertThat(caseIds(failures, "DeepSeek")).isEqualTo(DEEPSEEK_CASES);
        StreamSupport.stream(failures.spliterator(), false).forEach(failure -> {
            assertThat(failure.path("count").asInt()).isEqualTo(1);
            assertThat(failure.path("taxonomyCode").asText()).isEqualTo("J");
            assertThat(failure.path("causeStatus").asText()).isEqualTo("UNKNOWN_FROM_SANITIZED_ARTIFACT");
        });

        Set<String> contributors = StreamSupport.stream(root.path("confirmedCrossCuttingContributors").spliterator(), false)
            .map(item -> item.path("taxonomyCode").asText()).collect(Collectors.toSet());
        assertThat(contributors).containsExactlyInAnyOrder("D", "F");
        assertThat(root.path("evidenceLimit").asText()).contains("不得把 D 或 F 倒推");
    }

    private static Set<String> caseIds(JsonNode failures, String provider) {
        return StreamSupport.stream(failures.spliterator(), false)
            .filter(item -> provider.equals(item.path("provider").asText()))
            .map(item -> item.path("caseId").asText())
            .collect(Collectors.toSet());
    }
}
