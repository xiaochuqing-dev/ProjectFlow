package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class ProjectHistoryFinalChapterReviewManifestTest {
    private static final String SOURCE_RUN_ID = "32609107531";
    private static final String SOURCE_CODE_HEAD = "e1b67f28428e73f39fc23aa6f85961155a20ffd8";
    private static final String ROUND3_MANIFEST_CANONICAL_LF_SHA256 =
        "f316b71a6bec24f7ba40c2da81ef210b101b3ca238c688793fa32d48be877c1b";
    private static final String ROUND3_WORKSHEET_CANONICAL_LF_SHA256 =
        "4d57d7d1fa5bb975465db9be413f70cf943ca7c9c70d8174ba0d4dcdd7d85ca6";
    private static final Pattern FILLED_SCORE = Pattern.compile("(?m)^.*评分（1-5）：\\s*[1-5]\\s*$");
    private static final Pattern FILLED_RESULT = Pattern.compile("(?m)^结论（PASS/FAIL）：\\s*(?:PASS|FAIL)\\s*$");
    private static final Pattern FILLED_BOOLEAN = Pattern.compile("(?m)^.*（是/否）：\\s*(?:是|否)\\s*$");
    private static final Pattern FILLED_REVIEWER = Pattern.compile("(?m)^评审人：[ \\t]*\\S+.*$");
    private static final Pattern FILLED_NOTE = Pattern.compile("(?m)^备注：[ \\t]*\\S+.*$");
    private static final Pattern SENSITIVE = Pattern.compile(
        "(?i)(?:sk-[A-Za-z0-9_-]{20,}|ark-[A-Za-z0-9-]{20,}|Bearer [A-Za-z0-9._-]{24,}|[A-Za-z]:\\\\Users\\\\)"
    );

    @Test
    void freezesBalancedChapterPackageWithoutClaimingHumanAcceptance() throws Exception {
        Path root = Path.of("..", "docs", "acceptance-evidence", "v3.8.5").normalize();
        Path round3Manifest = root.resolve("human-review-round3-manifest.json");
        Path round3Worksheet = root.resolve("human-review-round3-worksheet.md");
        assertThat(canonicalLfSha256(round3Manifest)).isEqualTo(ROUND3_MANIFEST_CANONICAL_LF_SHA256);
        assertThat(canonicalLfSha256(round3Worksheet)).isEqualTo(ROUND3_WORKSHEET_CANONICAL_LF_SHA256);

        Path manifestPath = root.resolve("final-chapter-review-manifest.json");
        Path worksheetPath = root.resolve("final-chapter-review-worksheet.md");
        assertThat(manifestPath).isRegularFile();
        assertThat(worksheetPath).isRegularFile();

        JsonNode manifest = new ObjectMapper().readTree(manifestPath.toFile());
        assertThat(manifest.path("version").asText()).isEqualTo("projectflow-v385-final-chapter-review-v1");
        assertThat(manifest.path("status").asText()).isEqualTo("HUMAN_REVIEW_REQUIRED");
        assertThat(manifest.path("v385FinalAcceptance").asText()).isEqualTo("NOT_PASS");
        assertThat(manifest.path("chapterRepresentativenessAutomatedGate").asText()).isEqualTo("PASS");
        assertThat(manifest.path("chapterRepresentativenessHumanGate").asText()).isEqualTo("NOT_RUN");
        assertThat(manifest.path("sourceRunId").asText()).isEqualTo(SOURCE_RUN_ID);
        assertThat(manifest.path("sourceCodeHead").asText()).isEqualTo(SOURCE_CODE_HEAD);
        assertThat(manifest.path("reviewerCount").asInt(-1)).isZero();
        assertThat(manifest.path("reviewMode").asText()).isEqualTo("PENDING_SINGLE_HUMAN_REVIEWER");

        assertProvider(manifest.path("providerProfiles").path("luna"),
            "gpt-5.6-luna", "OPENAI_RESPONSES");
        assertProvider(manifest.path("providerProfiles").path("deepseek"),
            "deepseek-v4-flash", "OPENAI_CHAT_COMPLETIONS");
        assertProvider(manifest.path("providerProfiles").path("qwen"),
            "qwen3.7-plus", "ANTHROPIC_MESSAGES");

        JsonNode round3 = manifest.path("round3");
        assertThat(round3.path("immutable").asBoolean()).isTrue();
        assertThat(round3.path("reviewerCount").asInt(-1)).isZero();
        assertThat(round3.path("storyCount").asInt()).isEqualTo(30);
        assertThat(round3.path("manifestCanonicalLfSha256").asText())
            .isEqualTo(ROUND3_MANIFEST_CANONICAL_LF_SHA256);
        assertThat(round3.path("worksheetCanonicalLfSha256").asText())
            .isEqualTo(ROUND3_WORKSHEET_CANONICAL_LF_SHA256);
        assertThat(manifest.path("storyRegression").path("round3FrozenStoryCount").asInt()).isEqualTo(30);
        assertThat(manifest.path("storyRegression").path("truthSemanticUnchangedCount").asInt()).isEqualTo(30);
        assertThat(manifest.path("storyRegression").path("truthSemanticChangedCount").asInt(-1)).isZero();
        manifest.path("changedStories").forEach(sample -> {
            assertThat(sample.path("truthSemanticUnchanged").asBoolean()).isTrue();
            assertThat(sample.path("round3TruthSemanticHash").asText()).matches("sha256:[0-9a-f]{64}");
            assertThat(sample.path("finalTruthSemanticHash").asText())
                .isEqualTo(sample.path("round3TruthSemanticHash").asText());
        });

        Map<String, Integer> providerCounts = new LinkedHashMap<>();
        Set<String> coverage = new LinkedHashSet<>();
        assertThat(manifest.path("chapters")).hasSize(12);
        StreamSupport.stream(manifest.path("chapters").spliterator(), false).forEach(sample -> {
            providerCounts.merge(sample.path("provider").asText(), 1, Integer::sum);
            sample.path("coverageTags").forEach(tag -> coverage.add(tag.asText()));
            assertThat(sample.path("contentHash").asText()).matches("sha256:[0-9a-f]{64}");
            assertThat(sample.path("artifact").asText())
                .startsWith("docs/acceptance-evidence/v3.8.5/final-chapter-real-model/")
                .endsWith("/history-real-scenarios.json");
        });
        assertThat(providerCounts).containsExactlyInAnyOrderEntriesOf(Map.of(
            "GPT 5.6 Luna", 4,
            "DeepSeek V4 Flash", 4,
            "Qwen3.7 Plus", 4
        ));
        assertThat(coverage).contains(
            "projectflow", "large-coherent", "large-heterogeneous", "representation-boundary",
            "minor-first", "supporting-heavy", "short-coherent", "user-declared",
            "deterministic-fallback", "presentation", "report-document", "data-analysis"
        );

        JsonNode hashes = manifest.path("sourceArtifactCanonicalLfSha256");
        assertThat(hashes).hasSize(6);
        hashes.fields().forEachRemaining(entry -> {
            assertThat(entry.getKey())
                .startsWith("docs/acceptance-evidence/v3.8.5/final-chapter-real-model/")
                .endsWith(".json");
            assertThat(entry.getValue().asText()).matches("[0-9a-f]{64}");
            try {
                assertThat(canonicalLfSha256(Path.of("..").resolve(entry.getKey()).normalize()))
                    .isEqualTo(entry.getValue().asText());
            } catch (Exception exception) {
                throw new AssertionError("无法校验 Final Chapter 来源工件哈希", exception);
            }
        });

        JsonNode security = manifest.path("security");
        assertThat(security.path("modelSelfScoring").asBoolean(true)).isFalse();
        assertThat(security.path("rawPromptStored").asBoolean(true)).isFalse();
        assertThat(security.path("rawResponseStored").asBoolean(true)).isFalse();
        assertThat(security.path("reasoningStored").asBoolean(true)).isFalse();
        assertThat(security.path("credentialsStored").asBoolean(true)).isFalse();
        assertThat(security.path("absolutePathStored").asBoolean(true)).isFalse();

        String worksheet = Files.readString(worksheetPath, StandardCharsets.UTF_8);
        assertThat(worksheet).contains(
            "状态：HUMAN_REVIEW_REQUIRED / V3.8.5 NOT PASS。",
            "Final Chapter 样本：12 个，GPT 5.6 Luna 4 个、DeepSeek V4 Flash 4 个、Qwen3.7 Plus 4 个。",
            "评审人："
        );
        assertThat(FILLED_SCORE.matcher(worksheet).find()).isFalse();
        assertThat(FILLED_RESULT.matcher(worksheet).find()).isFalse();
        assertThat(FILLED_BOOLEAN.matcher(worksheet).find()).isFalse();
        assertThat(FILLED_REVIEWER.matcher(worksheet).find()).isFalse();
        assertThat(FILLED_NOTE.matcher(worksheet).find()).isFalse();
        assertThat(SENSITIVE.matcher(worksheet).find()).isFalse();
        assertThat(SENSITIVE.matcher(Files.readString(manifestPath, StandardCharsets.UTF_8)).find()).isFalse();
    }

    private static void assertProvider(JsonNode profile, String model, String protocol) {
        assertThat(profile.path("model").asText()).isEqualTo(model);
        assertThat(profile.path("protocol").asText()).isEqualTo(protocol);
        assertThat(profile.path("reasoningEffort").asText()).isEqualTo("max");
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
