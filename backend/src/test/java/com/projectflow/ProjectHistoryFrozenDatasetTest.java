package com.projectflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class ProjectHistoryFrozenDatasetTest {
    private static final Set<String> REQUIRED_CASES = Set.of(
        "small-five-commit-project",
        "chaotic-300-plus-commits",
        "create-modify-delete-restore-replace",
        "rename-move-split-merge",
        "multi-commit-one-change",
        "one-commit-three-unrelated-changes",
        "revert-then-reimplement",
        "merge-heavy-multi-branch",
        "commit-message-diff-mismatch",
        "blank-fix-update-messages",
        "mixed-chinese-english-commits",
        "pull-request-issue-reason",
        "readme-agent-collaboration-conflict",
        "test-claim-without-independent-verification",
        "document-only-project",
        "presentation-material-project",
        "single-page-frontend",
        "data-analysis-project",
        "no-git-project",
        "incomplete-git-history",
        "force-push-rebase-rewrite",
        "project-path-rebinding",
        "sensitive-files-and-credential-fields",
        "thousand-plus-raw-events"
    );

    @Test
    void freezesAllTwentyFourRequiredShapesAgainstExistingExecutableTests() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        byte[] bytes;
        try (InputStream input = getClass().getResourceAsStream("/projectflow-v380/history-ground-truth.json")) {
            assertThat(input).isNotNull();
            bytes = input.readAllBytes();
        }
        String raw = new String(bytes, StandardCharsets.UTF_8);
        JsonNode root = mapper.readTree(bytes);
        assertThat(root.path("version").asText()).isEqualTo("projectflow-v3.8.0-history-ground-truth-v1");
        JsonNode cases = root.path("cases");
        assertThat(cases.isArray()).isTrue();
        assertThat(cases).hasSize(24);
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (JsonNode testCase : cases) {
            String id = testCase.path("id").asText();
            assertThat(ids.add(id)).as("duplicate frozen case %s", id).isTrue();
            assertThat(testCase.path("shape").asText()).isNotBlank();
            assertThat(testCase.path("expectedCoverage").asText()).isNotBlank();
            assertThat(testCase.path("maxModelCalls").asInt(-1)).isBetween(0, 1);
            assertThat(testCase.path("verificationTests").isArray()).isTrue();
            assertThat(testCase.path("verificationTests")).isNotEmpty();
            for (JsonNode reference : testCase.path("verificationTests")) verifyMethod(reference.asText());
        }
        assertThat(ids).containsExactlyInAnyOrderElementsOf(REQUIRED_CASES);
        JsonNode gates = root.path("hardGates");
        List.of(
            "invalidEvidenceReferenceCountMax",
            "crossProjectReferenceCountMax",
            "unsupportedStrongFactCountMax",
            "knownChronologyErrorCountMax",
            "lifecycleTransitionErrorCountMax",
            "rawEventLossCountMax",
            "userManagedContentOverwriteCountMax",
            "secretOrAbsolutePathLeakCountMax"
        ).forEach(name -> assertThat(gates.path(name).asInt(-1)).as(name).isZero());
        assertThat(raw)
            .doesNotContain("C:\\Users\\", "/home/", "Bearer ", "sk-", "github_pat_")
            .doesNotContain("raw response", "reasoning原文");
    }

    private static void verifyMethod(String reference) throws Exception {
        int separator = reference.lastIndexOf('#');
        assertThat(separator).as(reference).isGreaterThan(0);
        String className = reference.substring(0, separator);
        String methodName = reference.substring(separator + 1);
        Class<?> type = Class.forName(className);
        assertThat(type.getDeclaredMethods()).as(reference).anyMatch(method -> method.getName().equals(methodName));
    }
}
