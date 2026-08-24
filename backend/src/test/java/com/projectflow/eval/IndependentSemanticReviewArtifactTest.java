package com.projectflow.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Verifies that a real-provider review produced a complete, bounded artifact. */
class IndependentSemanticReviewArtifactTest {
    private static final Pattern ABSOLUTE_PATH = Pattern.compile(
        "(?i)(?:[a-z]:[\\\\/]|(?:^|[\\s\"'])/(?:home|users|root|tmp)/)"
    );
    private static final Pattern SECRET_MARKER = Pattern.compile(
        "(?i)(?:bearer\\s+|sk-[a-z0-9_-]{20,}|api[_-]?key\\s*[:=]|authorization\\s*[:=])"
    );
    private static final Set<String> FORBIDDEN_FIELDS = Set.of(
        "apiKey", "authorization", "prompt", "rawResponse", "reasoning",
        "requestId", "baseUrl", "absolutePath"
    );
    private static final Set<String> JUDGEMENT_FIELDS = Set.of(
        "attachmentSemanticallySupported", "shouldRemainIndependent",
        "oldHistoryUnexpectedlyChanged", "truthfulnessConcern", "rationale", "confidence"
    );
    private static final Set<String> ROOT_FIELDS = Set.of(
        "version", "status", "complete", "generatedAt", "scenarioCount",
        "successfulScenarioCount", "failedScenarioCount", "inputPackage", "provider",
        "decisionBoundary", "callBudget", "sharedCallDiagnostics", "failureCode", "entries",
        "security"
    );
    private static final Set<String> PROVIDER_FIELDS = Set.of("name", "model", "protocol");
    private static final Set<String> CALL_BUDGET_FIELDS = Set.of(
        "logicalModelCallLimit", "logicalModelCalls", "physicalRequestLimit",
        "providerOutputTokenLimitPerRequest", "completionTokenLimit", "totalTokenLimit"
    );
    private static final Set<String> DIAGNOSTIC_FIELDS = Set.of(
        "requestCount", "inputTokens", "completionTokens", "totalTokens", "latencyMs",
        "requestSucceeded", "effectiveMaxTokens", "truncated", "partialResult",
        "schemaMatched", "finishReason", "normalizedFinishReason", "retryType",
        "failureStage", "failureCode", "protocol"
    );
    private static final Set<String> SECURITY_FIELDS = Set.of(
        "apiKeyPersistedInArtifact", "authorizationPersistedInArtifact", "promptPersisted",
        "rawResponsePersisted", "reasoningPersisted", "absolutePathPersisted"
    );

    @Test
    void realProviderArtifactIsCompleteBoundedAndSafe() throws Exception {
        Assumptions.assumeTrue(
            ProjectFlowRealModelEvalIT.providerConfig() != null,
            "未提供真实 Provider 安全配置，独立语义复核工件校验跳过"
        );

        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        JsonNode packageRoot = IndependentSemanticReviewPackageTest.loadAndValidatePackage(mapper);
        List<String> expectedIds = new ArrayList<>();
        packageRoot.path("scenarios").forEach(node -> expectedIds.add(node.path("id").asText("")));

        Path artifactPath = artifactPath();
        assertThat(artifactPath).isRegularFile();
        String content = Files.readString(artifactPath);
        assertThat(content.length()).isLessThan(120_000);
        assertThat(ABSOLUTE_PATH.matcher(content).find()).isFalse();
        assertThat(SECRET_MARKER.matcher(content).find()).isFalse();

        JsonNode root = mapper.readTree(content);
        assertExactObjectFields(root, ROOT_FIELDS);
        assertThat(root.path("version").asText()).isEqualTo(
            "projectflow-v3.9-independent-semantic-review-artifact-v1"
        );
        assertThat(root.path("status").asText()).isEqualTo("COMPLETE");
        assertThat(root.path("complete").isBoolean()).isTrue();
        assertThat(root.path("complete").asBoolean()).isTrue();
        assertThat(root.path("scenarioCount").isIntegralNumber()).isTrue();
        assertThat(root.path("successfulScenarioCount").isIntegralNumber()).isTrue();
        assertThat(root.path("failedScenarioCount").isIntegralNumber()).isTrue();
        assertThat(root.path("scenarioCount").asInt()).isEqualTo(12);
        assertThat(root.path("successfulScenarioCount").asInt()).isEqualTo(12);
        assertThat(root.path("failedScenarioCount").asInt()).isZero();
        assertThat(root.path("failureCode").isTextual()).isTrue();
        assertThat(root.path("failureCode").asText()).isBlank();
        assertThat(root.path("generatedAt").isTextual()).isTrue();
        Instant.parse(root.path("generatedAt").asText());
        assertThat(root.path("inputPackage").asText())
            .isEqualTo("docs/acceptance-evidence/v3.9/independent-semantic-review-package.json");
        assertThat(root.path("decisionBoundary").asText())
            .isEqualTo(
                "MODEL_ONLY_DIAGNOSTIC; disagreement_or_uncertainty_stays_UNRESOLVED; "
                    + "no_Strong_Fact_promotion"
            );

        JsonNode provider = root.path("provider");
        assertExactObjectFields(provider, PROVIDER_FIELDS);
        PROVIDER_FIELDS.forEach(field -> {
            assertThat(provider.path(field).isTextual()).as(field).isTrue();
            assertThat(provider.path(field).asText()).as(field).isNotBlank();
        });

        JsonNode budget = root.path("callBudget");
        JsonNode diagnostics = root.path("sharedCallDiagnostics");
        assertExactObjectFields(budget, CALL_BUDGET_FIELDS);
        assertExactObjectFields(diagnostics, DIAGNOSTIC_FIELDS);
        CALL_BUDGET_FIELDS.forEach(field ->
            assertThat(budget.path(field).isIntegralNumber()).as(field).isTrue()
        );
        Set.of("requestCount", "inputTokens", "completionTokens", "totalTokens", "latencyMs",
            "effectiveMaxTokens").forEach(field ->
                assertThat(diagnostics.path(field).isIntegralNumber()).as(field).isTrue()
            );
        Set.of("requestSucceeded", "truncated", "partialResult", "schemaMatched")
            .forEach(field -> assertThat(diagnostics.path(field).isBoolean()).as(field).isTrue());
        Set.of("finishReason", "normalizedFinishReason", "retryType", "failureStage",
            "failureCode", "protocol").forEach(field ->
                assertThat(diagnostics.path(field).isTextual()).as(field).isTrue()
            );
        assertThat(budget.path("logicalModelCalls").asInt()).isEqualTo(1);
        assertThat(budget.path("logicalModelCallLimit").asInt()).isEqualTo(1);
        assertThat(budget.path("physicalRequestLimit").asInt()).isEqualTo(4);
        assertThat(budget.path("providerOutputTokenLimitPerRequest").asInt()).isEqualTo(16_384);
        assertThat(budget.path("completionTokenLimit").asInt()).isEqualTo(32_768);
        assertThat(budget.path("totalTokenLimit").asInt()).isEqualTo(65_536);
        assertThat(diagnostics.path("requestCount").asInt())
            .isBetween(1, budget.path("physicalRequestLimit").asInt());
        assertThat(diagnostics.path("effectiveMaxTokens").asInt())
            .isLessThanOrEqualTo(budget.path("providerOutputTokenLimitPerRequest").asInt());
        assertThat(diagnostics.path("completionTokens").asInt())
            .isLessThanOrEqualTo(budget.path("completionTokenLimit").asInt());
        assertThat(diagnostics.path("totalTokens").asInt())
            .isLessThanOrEqualTo(budget.path("totalTokenLimit").asInt());
        assertThat(diagnostics.path("requestSucceeded").asBoolean()).isTrue();
        assertThat(diagnostics.path("schemaMatched").asBoolean()).isTrue();
        assertThat(diagnostics.path("failureCode").asText()).isBlank();

        List<String> actualIds = new ArrayList<>();
        assertThat(root.path("entries").isArray()).isTrue();
        for (JsonNode entry : root.path("entries")) {
            assertExactObjectFields(entry, Set.of("scenarioId", "judgement"));
            assertThat(entry.path("scenarioId").isTextual()).isTrue();
            actualIds.add(entry.path("scenarioId").asText(""));
            JsonNode judgement = entry.path("judgement");
            Set<String> fields = new LinkedHashSet<>();
            judgement.fieldNames().forEachRemaining(fields::add);
            assertThat(fields).containsExactlyInAnyOrderElementsOf(JUDGEMENT_FIELDS);
            JUDGEMENT_FIELDS.forEach(field ->
                assertThat(judgement.path(field).isTextual()).as(field).isTrue()
            );
            assertThat(judgement.path("attachmentSemanticallySupported").asText())
                .isIn("yes", "no", "uncertain");
            assertThat(judgement.path("shouldRemainIndependent").asText())
                .isIn("yes", "no", "uncertain");
            assertThat(judgement.path("oldHistoryUnexpectedlyChanged").asText()).isIn("yes", "no");
            assertThat(judgement.path("truthfulnessConcern").asText()).isIn("yes", "no");
            assertThat(judgement.path("confidence").asText()).isIn("high", "medium", "low");
            assertThat(judgement.path("rationale").asText()).isNotBlank().hasSizeLessThanOrEqualTo(500);
        }
        assertThat(actualIds).containsExactlyElementsOf(expectedIds);

        JsonNode security = root.path("security");
        assertExactObjectFields(security, SECURITY_FIELDS);
        SECURITY_FIELDS.forEach(field -> {
            assertThat(security.path(field).isBoolean()).isTrue();
            assertThat(security.path(field).asBoolean()).isFalse();
        });
        Set<String> allFields = new LinkedHashSet<>();
        collectObjectFields(root, allFields);
        assertThat(allFields).doesNotContainAnyElementsOf(FORBIDDEN_FIELDS);
    }

    private static Path artifactPath() {
        String outputName = System.getProperty("projectflow.eval.output-name", "")
            .replaceAll("[^A-Za-z0-9._-]", "_");
        if (outputName.isBlank()) {
            throw new IllegalStateException("projectflow.eval.output-name is required");
        }
        return Path.of(
            "target", "projectflow-eval", outputName, "independent-semantic-review.json"
        );
    }

    private static void assertExactObjectFields(JsonNode object, Set<String> expected) {
        assertThat(object.isObject()).isTrue();
        Set<String> actual = new LinkedHashSet<>();
        object.fieldNames().forEachRemaining(actual::add);
        assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
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
}
