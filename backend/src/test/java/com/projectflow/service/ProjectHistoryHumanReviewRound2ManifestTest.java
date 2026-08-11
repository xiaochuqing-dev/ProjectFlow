package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class ProjectHistoryHumanReviewRound2ManifestTest {
    private static final String ROUND1_MANIFEST_CANONICAL_LF_SHA256 =
        "524391f2137a7b72d2920efbefaee1190177bbeb588594ef47fa9099d92554d9";
    private static final String ROUND1_WORKSHEET_CANONICAL_LF_SHA256 =
        "dbe05a47548ba72cd2c379c05b987e5915e68c32bb935e5bbd3fcf173c420408";
    private static final Set<String> REQUIRED_COVERAGE = Set.of(
        "projectflow", "non-code", "short-history", "long-history", "generic-commit",
        "multi-commit-one-result", "one-commit-multiple-results", "lifecycle-restore", "rename-move",
        "split-merge", "unknown-reason", "conflict", "supporting", "correction"
    );
    private static final Pattern FILLED_SCORE = Pattern.compile("(?m)^人工可读性评分（1-5）：\\s*[1-5]\\s*$");
    private static final Pattern FILLED_RESULT = Pattern.compile("(?m)^结论（PASS/FAIL）：\\s*(?:PASS|FAIL)\\s*$");
    private static final Pattern SENSITIVE = Pattern.compile(
        "(?i)(?:sk-[A-Za-z0-9_-]{20,}|ark-[A-Za-z0-9-]{20,}|Bearer [A-Za-z0-9._-]{24,})"
    );

    @Test
    void freezesRound2WithoutOverwritingRound1OrSelfScoringHumanReview() throws Exception {
        Path root = Path.of("..", "docs", "acceptance-evidence", "v3.8.5").normalize();
        Path round1Manifest = root.resolve("human-review-sample-manifest.json");
        Path round1Worksheet = root.resolve("human-review-worksheet.md");
        Path manifest = root.resolve("human-review-round2-manifest.json");
        Path worksheet = root.resolve("human-review-round2-worksheet.md");

        assertThat(canonicalLfSha256(round1Manifest)).isEqualTo(ROUND1_MANIFEST_CANONICAL_LF_SHA256);
        assertThat(canonicalLfSha256(round1Worksheet)).isEqualTo(ROUND1_WORKSHEET_CANONICAL_LF_SHA256);
        assertThat(manifest).isRegularFile();
        assertThat(worksheet).isRegularFile();

        JsonNode value = new ObjectMapper().readTree(manifest.toFile());
        assertThat(value.path("version").asText()).isEqualTo("projectflow-v385-human-review-sample-v2");
        assertThat(value.path("reviewRound").asInt()).isEqualTo(2);
        assertThat(value.path("round1Status").asText()).isEqualTo("NEEDS_REVISION_NOT_APPROVED");
        assertThat(value.path("status").asText()).isIn("PENDING_HUMAN_REVIEW", "REVIEWED");
        assertThat(value.path("sourceRunId").asText()).matches("[1-9][0-9]*");
        for (String provider : Set.of("glm", "deepseek")) {
            JsonNode source = value.path("providerSourceRuns").path(provider);
            assertThat(source.path("runId").asText()).matches("[1-9][0-9]*");
            assertThat(source.path("runUrl").asText()).isEqualTo(
                "https://github.com/xiaochuqing-dev/ProjectFlow/actions/runs/" + source.path("runId").asText()
            );
            assertThat(source.path("affectedRunId").asText()).matches("[1-9][0-9]*");
            assertThat(source.path("affectedRunUrl").asText()).isEqualTo(
                "https://github.com/xiaochuqing-dev/ProjectFlow/actions/runs/" + source.path("affectedRunId").asText()
            );
        }
        assertThat(value.path("samplingMethod").asText()).contains("fixed stratified selection", "Round 2");
        assertThat(value.path("security").path("modelSelfScoring").asBoolean(true)).isFalse();
        assertThat(value.path("security").path("rawPromptStored").asBoolean(true)).isFalse();
        assertThat(value.path("security").path("rawResponseStored").asBoolean(true)).isFalse();
        assertThat(value.path("security").path("reasoningStored").asBoolean(true)).isFalse();
        assertThat(value.path("security").path("credentialsStored").asBoolean(true)).isFalse();

        JsonNode stories = value.path("stories");
        JsonNode chapters = value.path("chapters");
        assertThat(stories).hasSize(30);
        assertThat(chapters).hasSize(8);
        Set<String> sampleIds = new HashSet<>();
        Set<String> coverage = new HashSet<>();
        Map<String, Integer> storyProviders = new LinkedHashMap<>();
        Map<String, Integer> chapterProviders = new LinkedHashMap<>();
        StreamSupport.stream(stories.spliterator(), false).forEach(sample ->
            validateSample(sample, "STORY", sampleIds, coverage, storyProviders));
        StreamSupport.stream(chapters.spliterator(), false).forEach(sample ->
            validateSample(sample, "CHAPTER", sampleIds, coverage, chapterProviders));
        assertThat(storyProviders).containsEntry("GLM", 15).containsEntry("DeepSeek", 15);
        assertThat(chapterProviders).containsEntry("GLM", 4).containsEntry("DeepSeek", 4);
        assertThat(coverage).containsAll(REQUIRED_COVERAGE);

        String worksheetText = Files.readString(worksheet, StandardCharsets.UTF_8);
        assertThat(worksheetText).contains(
            "Round 1 结论：NEEDS_REVISION / NOT_APPROVED",
            "受影响纠正链路来源：",
            "第一眼能否理解（是/否）：", "Before 是否自然（是/否）：",
            "Evidence 是否支撑标题（是/否）：", "是否把 planned 当 implemented（是/否）：",
            "是否把 declared 当 verified（是/否）：", "是否像项目阶段而非文件列表（是/否）：",
            "是否出现 raw subject（是/否）：", "是否过度统计口吻（是/否）："
        );
        assertThat(FILLED_SCORE.matcher(worksheetText).find()).isFalse();
        assertThat(FILLED_RESULT.matcher(worksheetText).find()).isFalse();
        assertThat(SENSITIVE.matcher(worksheetText).find()).isFalse();
    }

    private static void validateSample(
        JsonNode sample,
        String kind,
        Set<String> sampleIds,
        Set<String> coverage,
        Map<String, Integer> providers
    ) {
        assertThat(sample.path("kind").asText()).isEqualTo(kind);
        assertThat(sampleIds.add(sample.path("sampleId").asText())).isTrue();
        assertThat(sample.path("entityId").asText()).isNotBlank();
        assertThat(sample.path("artifact").asText())
            .startsWith("docs/acceptance-evidence/v3.8.5/real-model/")
            .doesNotContain("..", "\\").doesNotStartWith("/");
        assertThat(sample.path("contentHash").asText()).matches("sha256:[0-9a-f]{64}");
        assertThat(sample.path("presentationRevision").asText()).isNotBlank();
        boolean correction = StreamSupport.stream(sample.path("coverageTags").spliterator(), false)
            .anyMatch(tag -> "correction".equals(tag.asText()));
        if (correction) {
            assertThat(sample.path("artifact").asText()).endsWith("history-real-scenarios-affected.json");
        }
        providers.merge(sample.path("provider").asText(), 1, Integer::sum);
        sample.path("coverageTags").forEach(tag -> coverage.add(tag.asText()));
    }

    private static String canonicalLfSha256(Path path) throws Exception {
        String normalized = Files.readString(path, StandardCharsets.UTF_8)
            .replace("\r\n", "\n")
            .replace('\r', '\n');
        byte[] digest = MessageDigest.getInstance("SHA-256")
            .digest(normalized.getBytes(StandardCharsets.UTF_8));
        return java.util.HexFormat.of().formatHex(digest);
    }
}
