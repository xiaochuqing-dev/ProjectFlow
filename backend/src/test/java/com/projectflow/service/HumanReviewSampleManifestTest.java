package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class HumanReviewSampleManifestTest {
    private static final Set<String> REQUIRED_COVERAGE = Set.of(
        "projectflow", "non-code", "short-history", "long-history", "generic-commit",
        "multi-commit-one-result", "one-commit-multiple-results", "lifecycle-restore", "rename-move",
        "split-merge", "unknown-reason", "conflict", "supporting", "correction"
    );

    @Test
    void frozenHumanReviewSampleIsLargeStratifiedAndStable() throws Exception {
        Path manifest = Path.of("..", "docs", "acceptance-evidence", "v3.8.5",
            "human-review-sample-manifest.json").normalize();
        Assumptions.assumeTrue(Files.isRegularFile(manifest),
            "等待 GLM 与 DeepSeek RC2 真实工件后冻结人工抽样清单");

        JsonNode root = new ObjectMapper().readTree(manifest.toFile());
        assertThat(root.path("status").asText()).isIn("PENDING_HUMAN_REVIEW", "REVIEWED");
        JsonNode stories = root.path("stories");
        JsonNode chapters = root.path("chapters");
        assertThat(stories.isArray()).isTrue();
        assertThat(chapters.isArray()).isTrue();
        assertThat(stories.size()).isGreaterThanOrEqualTo(30);
        assertThat(chapters.size()).isGreaterThanOrEqualTo(8);

        Set<String> sampleIds = new HashSet<>();
        Set<String> providers = new HashSet<>();
        Set<String> coverage = new HashSet<>();
        StreamSupport.stream(stories.spliterator(), false).forEach(sample ->
            validate(sample, "STORY", sampleIds, providers, coverage));
        StreamSupport.stream(chapters.spliterator(), false).forEach(sample ->
            validate(sample, "CHAPTER", sampleIds, providers, coverage));

        assertThat(providers).contains("GLM", "DeepSeek");
        assertThat(coverage).containsAll(REQUIRED_COVERAGE);
    }

    private static void validate(JsonNode sample, String kind, Set<String> sampleIds,
        Set<String> providers, Set<String> coverage) {
        assertThat(sample.path("kind").asText()).isEqualTo(kind);
        assertThat(sample.path("sampleId").asText()).isNotBlank();
        assertThat(sampleIds.add(sample.path("sampleId").asText())).isTrue();
        assertThat(sample.path("entityId").asText()).isNotBlank();
        assertThat(sample.path("artifact").asText()).isNotBlank();
        assertThat(sample.path("contentHash").asText()).matches("sha256:[0-9a-f]{64}");
        assertThat(sample.path("presentationRevision").asText()).isNotBlank();
        providers.add(sample.path("provider").asText());
        sample.path("coverageTags").forEach(tag -> coverage.add(tag.asText()));
    }
}
