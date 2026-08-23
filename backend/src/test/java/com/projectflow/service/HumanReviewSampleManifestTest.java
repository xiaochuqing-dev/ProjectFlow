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
        assertThat(root.path("version").asText()).isEqualTo("projectflow-v385-human-review-sample-v1");
        assertThat(root.path("sourceRunId").asText()).matches("[1-9][0-9]*");
        assertThat(root.path("sourceRunUrl").asText())
            .isEqualTo("https://github.com/xiaochuqing-dev/ProjectFlow/actions/runs/" + root.path("sourceRunId").asText());
        assertThat(root.path("status").asText()).isIn("PENDING_HUMAN_REVIEW", "REVIEWED");
        assertThat(root.path("samplingMethod").asText()).contains("fixed stratified selection");
        assertThat(root.path("security").path("modelSelfScoring").asBoolean(true)).isFalse();
        assertThat(root.path("security").path("rawPromptStored").asBoolean(true)).isFalse();
        assertThat(root.path("security").path("rawResponseStored").asBoolean(true)).isFalse();
        assertThat(root.path("security").path("reasoningStored").asBoolean(true)).isFalse();
        assertThat(root.path("security").path("credentialsStored").asBoolean(true)).isFalse();
        if ("PENDING_HUMAN_REVIEW".equals(root.path("status").asText())) {
            assertThat(root.path("reviewerCount").asInt(-1)).isZero();
            assertThat(root.path("reviewMode").asText()).isEqualTo("PENDING_SINGLE_HUMAN_REVIEWER");
        }
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
        assertThat(sample.path("projectType").asText())
            .isIn("SOFTWARE_FIXTURE", "PROJECTFLOW_SOFTWARE", "NON_CODE");
        String artifact = sample.path("artifact").asText();
        assertThat(artifact).isNotBlank().startsWith("docs/acceptance-evidence/v3.8.5/real-model/");
        assertThat(artifact).doesNotContain("..", "\\").doesNotMatch("^[A-Za-z]:/.*").doesNotStartWith("/");
        assertThat(sample.path("contentHash").asText()).matches("sha256:[0-9a-f]{64}");
        assertThat(sample.path("presentationRevision").asText()).isNotBlank();
        providers.add(sample.path("provider").asText());
        sample.path("coverageTags").forEach(tag -> coverage.add(tag.asText()));
    }
}
