package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class ProjectHistoryFinalHumanSignoffEvidenceTest {
    private static final Pattern SENSITIVE = Pattern.compile(
        "(?i)(?:sk-[A-Za-z0-9_-]{20,}|ark-[A-Za-z0-9-]{20,}|Bearer [A-Za-z0-9._-]{24,}|[A-Za-z]:\\\\Users\\\\)"
    );

    @Test
    void recordsOwnerOverrideWithoutInventingScoresOrMutatingFrozenPackages() throws Exception {
        Path root = Path.of("..", "docs", "acceptance-evidence", "v3.8.5").normalize();
        Path signoffPath = root.resolve("final-human-signoff.json");
        JsonNode signoff = new ObjectMapper().readTree(signoffPath.toFile());

        assertThat(signoff.path("status").asText()).isEqualTo("APPROVED_BY_PROJECT_OWNER");
        assertThat(signoff.path("v385FinalAcceptance").asText())
            .isEqualTo("PASS_BY_EXPLICIT_OWNER_OVERRIDE");
        assertThat(signoff.path("v39Entry").asText()).isEqualTo("APPROVED");
        assertThat(signoff.path("reviewerCount").asInt()).isEqualTo(1);
        assertThat(signoff.path("decision").path("explicitMergeApproval").asBoolean()).isTrue();
        assertThat(signoff.path("decision").path("projectOwnerOverride").asBoolean()).isTrue();
        assertThat(signoff.path("decision").path("originalQuantitativeThresholdsEvidenced").asBoolean())
            .isFalse();
        assertThat(signoff.path("scores").path("storyReadabilityAverage").isNull()).isTrue();
        assertThat(signoff.path("scores").path("chapterReadabilityAverage").isNull()).isTrue();
        assertThat(signoff.path("scores").path("chapterRepresentativenessAverage").isNull()).isTrue();
        assertThat(signoff.path("scores").path("coreDimensionMinimum").isNull()).isTrue();
        assertThat(signoff.path("truthfulness").path("humanP0Count").asInt(-1)).isZero();

        JsonNode frozen = signoff.path("frozenPackage");
        assertHash(root.resolve("final-chapter-review-manifest.json"),
            frozen.path("finalChapterManifestCanonicalLfSha256").asText());
        assertHash(root.resolve("final-chapter-review-worksheet.md"),
            frozen.path("finalChapterWorksheetCanonicalLfSha256").asText());
        assertHash(root.resolve("human-review-round3-manifest.json"),
            frozen.path("round3ManifestCanonicalLfSha256").asText());
        assertHash(root.resolve("human-review-round3-worksheet.md"),
            frozen.path("round3WorksheetCanonicalLfSha256").asText());

        JsonNode finalManifest = new ObjectMapper()
            .readTree(root.resolve("final-chapter-review-manifest.json").toFile());
        assertThat(finalManifest.path("reviewerCount").asInt(-1)).isZero();
        assertThat(finalManifest.path("status").asText()).isEqualTo("HUMAN_REVIEW_REQUIRED");
        assertThat(SENSITIVE.matcher(Files.readString(signoffPath, StandardCharsets.UTF_8)).find()).isFalse();
    }

    private static void assertHash(Path path, String expected) throws Exception {
        assertThat(canonicalLfSha256(path)).isEqualTo(expected);
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
