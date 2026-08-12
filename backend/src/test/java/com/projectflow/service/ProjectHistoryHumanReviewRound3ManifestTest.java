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

class ProjectHistoryHumanReviewRound3ManifestTest {
    private static final String ROUND2_MANIFEST_RAW_SHA256 =
        "e1aca397b469c4d1e4e4b4f6bb856306b2b3340bcb5df97e80d71a286a247349";
    private static final String ROUND2_WORKSHEET_RAW_SHA256 =
        "8e9c04bde787b6bb6c2528f96e5d296dcf66186f66290298cf18ca21f68d73e7";
    private static final Pattern FILLED_SCORE = Pattern.compile("(?m)^人工可读性评分（1-5）：\\s*[1-5]\\s*$");
    private static final Pattern FILLED_RESULT = Pattern.compile("(?m)^结论（PASS/FAIL）：\\s*(?:PASS|FAIL)\\s*$");
    private static final Pattern SENSITIVE = Pattern.compile(
        "(?i)(?:sk-[A-Za-z0-9_-]{20,}|ark-[A-Za-z0-9-]{20,}|Bearer [A-Za-z0-9._-]{24,})"
    );

    @Test
    void freezesRound3WithTruthfulnessP0AndBlankHumanFields() throws Exception {
        Path root = Path.of("..", "docs", "acceptance-evidence", "v3.8.5").normalize();
        assertThat(rawSha256(root.resolve("human-review-round2-manifest.json")))
            .isEqualTo(ROUND2_MANIFEST_RAW_SHA256);
        assertThat(rawSha256(root.resolve("human-review-round2-worksheet.md")))
            .isEqualTo(ROUND2_WORKSHEET_RAW_SHA256);

        Path manifestPath = root.resolve("human-review-round3-manifest.json");
        Path worksheetPath = root.resolve("human-review-round3-worksheet.md");
        assertThat(manifestPath).isRegularFile();
        assertThat(worksheetPath).isRegularFile();
        JsonNode manifest = new ObjectMapper().readTree(manifestPath.toFile());
        assertThat(manifest.path("version").asText()).isEqualTo("projectflow-v385-human-review-sample-v3");
        assertThat(manifest.path("reviewRound").asInt()).isEqualTo(3);
        assertThat(manifest.path("status").asText()).isEqualTo("PENDING_HUMAN_REVIEW");
        assertThat(manifest.path("round1Status").asText()).isEqualTo("NEEDS_REVISION_NOT_APPROVED");
        assertThat(manifest.path("round2Status").asText()).isEqualTo("NEEDS_REVISION_NOT_APPROVED");
        assertThat(manifest.path("stories")).hasSize(30);
        assertThat(manifest.path("chapters")).hasSize(8);

        Map<String, Integer> storyProviders = new LinkedHashMap<>();
        Map<String, Integer> chapterProviders = new LinkedHashMap<>();
        Set<String> coverage = new HashSet<>();
        StreamSupport.stream(manifest.path("stories").spliterator(), false).forEach(sample -> {
                storyProviders.merge(sample.path("provider").asText(), 1, Integer::sum);
                sample.path("coverageTags").forEach(tag -> coverage.add(tag.asText()));
            });
        String p0SampleId = StreamSupport.stream(manifest.path("stories").spliterator(), false)
            .filter(sample -> StreamSupport.stream(sample.path("coverageTags").spliterator(), false)
                .anyMatch(tag -> "truthfulness-p0".equals(tag.asText())))
            .map(sample -> sample.path("sampleId").asText())
            .findFirst().orElseThrow();
        StreamSupport.stream(manifest.path("chapters").spliterator(), false).forEach(sample -> {
            chapterProviders.merge(sample.path("provider").asText(), 1, Integer::sum);
            sample.path("coverageTags").forEach(tag -> coverage.add(tag.asText()));
        });
        assertThat(storyProviders).containsEntry("GLM", 15).containsEntry("DeepSeek", 15);
        assertThat(chapterProviders).containsEntry("GLM", 4).containsEntry("DeepSeek", 4);
        assertThat(coverage).contains("truthfulness-p0", "projectflow", "non-code", "correction", "supporting");

        String worksheet = Files.readString(worksheetPath, StandardCharsets.UTF_8);
        assertThat(worksheet).contains(
            "Round 2 结论：NEEDS_REVISION_NOT_APPROVED",
            "Claim Subject：", "Claim Action：", "Claim State：", "Direct Evidence IDs：",
            "Indirect Context IDs：", "P0 truthfulness failure（是/否）："
        );
        String p0Section = section(worksheet, p0SampleId);
        assertThat(p0Section)
            .doesNotContain("编写登录流程代码并形成实现", "登录流程已有代码实现")
            .containsPattern("Claim State：(PLANNED|DECLARED|CONFIGURED|OBSERVED|UNKNOWN|CONFLICTED)");
        assertThat(worksheet).doesNotContain(
            "围绕项目基础建设推进阶段成果",
            "相关成果逐步形成并得到完善"
        );
        assertThat(FILLED_SCORE.matcher(worksheet).find()).isFalse();
        assertThat(FILLED_RESULT.matcher(worksheet).find()).isFalse();
        assertThat(SENSITIVE.matcher(worksheet).find()).isFalse();
        assertThat(manifest.path("security").path("modelSelfScoring").asBoolean(true)).isFalse();
        assertThat(manifest.path("security").path("credentialsStored").asBoolean(true)).isFalse();
    }

    private static String section(String worksheet, String sampleId) {
        String marker = "## " + sampleId + "  ";
        int start = worksheet.indexOf(marker);
        assertThat(start).isGreaterThanOrEqualTo(0);
        int end = worksheet.indexOf("\n## ", start + marker.length());
        return end < 0 ? worksheet.substring(start) : worksheet.substring(start, end);
    }

    private static String rawSha256(Path path) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
        return java.util.HexFormat.of().formatHex(digest);
    }
}
