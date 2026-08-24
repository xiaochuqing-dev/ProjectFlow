package com.projectflow.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.entity.AiProvider;
import com.projectflow.service.ModelGatewayService;
import com.projectflow.service.ModelTaskType;
import com.projectflow.service.SensitiveContentRedactor;

/**
 * Real-provider-only, blind semantic review for the twelve V3.9 continuity
 * inputs. This evaluator is an acceptance diagnostic and has no production
 * write path.
 */
@SpringBootTest
@ActiveProfiles("test")
class ProjectHistoryIndependentSemanticReviewEvaluatorTest {
    private static final String ARTIFACT_VERSION =
        "projectflow-v3.9-independent-semantic-review-artifact-v1";
    private static final String PACKAGE_FILE =
        "docs/acceptance-evidence/v3.9/independent-semantic-review-package.json";
    private static final ModelTaskType REVIEW_TASK =
        ModelTaskType.PROJECT_HISTORY_INDEPENDENT_SEMANTIC_REVIEW;
    private static final int MAX_PROVIDER_OUTPUT_TOKENS = 16_384;
    private static final int MAX_LOGICAL_MODEL_CALLS = 1;
    private static final int MAX_PHYSICAL_REQUESTS = 4;
    private static final int MAX_COMPLETION_TOKENS = 32_768;
    private static final int MAX_TOTAL_TOKENS = 65_536;
    private static final Set<String> YES_NO = Set.of("yes", "no");
    private static final Set<String> YES_NO_UNCERTAIN = Set.of("yes", "no", "uncertain");
    private static final Set<String> CONFIDENCE = Set.of("high", "medium", "low");
    private static final Pattern SAFE_CODE = Pattern.compile("[A-Z0-9_:-]{1,64}");
    private static final Pattern WINDOWS_PATH = Pattern.compile("(?i)[a-z]:[\\\\/]");
    private static final Pattern UNIX_PATH = Pattern.compile("(?i)(?:^|[\\s\"'])/(?:home|users|root|tmp)/");

    @Autowired ModelGatewayService modelGateway;
    @Autowired ObjectMapper objectMapper;
    @Autowired SensitiveContentRedactor redactor;

    @Test
    void reviewsAllTwelveBlindContinuityScenariosThroughTheGateway() throws Exception {
        JsonNode packageRoot = IndependentSemanticReviewPackageTest.loadAndValidatePackage(objectMapper);
        List<String> scenarioIds = scenarioIds(packageRoot);

        ProjectFlowRealModelEvalIT.ProviderConfig config = ProjectFlowRealModelEvalIT.providerConfig();
        Assumptions.assumeTrue(config != null, "未提供真实 Provider 安全配置，独立语义复核跳过");

        List<Map<String, Object>> entries = new ArrayList<>();
        ModelGatewayService.ModelCallDiagnostics diagnostics = null;
        String failureCode = "";
        int fallbackRequestCount = 0;
        boolean callAttempted = false;

        try {
            callAttempted = true;
            ModelGatewayService.StructuredModelResponse response = modelGateway.callStructured(
                provider(config), blindPrompt(packageRoot.path("scenarios")), REVIEW_TASK
            );
            diagnostics = response.diagnostics();
            Map<String, BoundedJudgement> judgements = parseJudgements(
                response.parsed().root(), scenarioIds
            );
            enforceCallBudget(diagnostics);
            for (String scenarioId : scenarioIds) {
                entries.add(successEntry(scenarioId, judgements.get(scenarioId)));
            }
            printSafeDiagnostics(diagnostics);
        } catch (Exception failure) {
            if (diagnostics == null) diagnostics = failureDiagnostics(failure);
            fallbackRequestCount = failureRequestCount(failure);
            failureCode = safeFailureCode(failure, diagnostics);
            entries.clear();
            for (String scenarioId : scenarioIds) {
                entries.add(failureEntry(scenarioId, failureCode));
            }
            System.err.println("V39_INDEPENDENT_REVIEW_FAILURE code=" + failureCode);
        }

        boolean complete = failureCode.isBlank()
            && entries.size() == scenarioIds.size()
            && entries.stream().allMatch(entry -> entry.containsKey("judgement"));
        writeArtifact(
            config, entries, diagnostics, failureCode, fallbackRequestCount, callAttempted, complete
        );

        assertThat(complete).as("十二个原 continuity 场景必须全部得到有界复核判断").isTrue();
        assertThat(failureCode).isBlank();
    }

    private String blindPrompt(JsonNode scenarios) throws Exception {
        String serializedScenarios = objectMapper.writeValueAsString(scenarios);
        if (serializedScenarios.length() > 60_000) {
            throw new IllegalArgumentException("BLIND_INPUT_TOO_LARGE");
        }
        return """
            你是 ProjectFlow V3.9 的独立语义复核器。只依据下面十二条有界输入判断每个候选续接是否忠实；不要补充输入之外的事实，不要把不确定性写成确定事实，也不要用其他条目的候选结论替代本条证据。
            只返回一个 JSON 对象，不要 Markdown，不要解释 JSON。根字段只能是 reviews；reviews 必须按输入顺序逐条返回且不遗漏。每条字段必须是：
            scenarioId、attachmentSemanticallySupported、shouldRemainIndependent（值只能是 yes、no、uncertain）、oldHistoryUnexpectedlyChanged、truthfulnessConcern（值只能是 yes、no），
            rationale（不超过 500 个字符的单行理由）和 confidence（值只能是 high、medium、low）。
            attachmentSemanticallySupported 表示候选的语义归属是否被给出的 Evidence 支持；shouldRemainIndependent 表示候选是否应保持独立；
            oldHistoryUnexpectedlyChanged 表示候选是否显示旧历史被意外改变；truthfulnessConcern 表示是否存在需要关注的真实性风险。
            任一分歧或不确定情况都保留为 uncertain，并在 rationale 中说明边界。

            有界输入：
            """ + serializedScenarios;
    }

    Map<String, BoundedJudgement> parseJudgements(JsonNode root, List<String> scenarioIds) {
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("REVIEW_RESULT_NOT_OBJECT");
        }
        JsonNode reviews = root.path("reviews");
        if (!reviews.isArray()) throw new IllegalArgumentException("REVIEW_LIST_MISSING");
        if (reviews.size() != scenarioIds.size()) {
            throw new IllegalArgumentException("REVIEW_SCENARIO_COUNT_INVALID");
        }

        Set<String> expectedIds = new LinkedHashSet<>(scenarioIds);
        List<String> returnedIds = new ArrayList<>();
        Map<String, BoundedJudgement> result = new LinkedHashMap<>();
        for (JsonNode review : reviews) {
            String scenarioId = review.path("scenarioId").asText("").trim();
            if (!expectedIds.contains(scenarioId) || result.containsKey(scenarioId)) {
                throw new IllegalArgumentException("REVIEW_SCENARIO_ID_INVALID");
            }
            returnedIds.add(scenarioId);
            result.put(scenarioId, new BoundedJudgement(
                enumValue(review, "attachmentSemanticallySupported", YES_NO_UNCERTAIN),
                enumValue(review, "shouldRemainIndependent", YES_NO_UNCERTAIN),
                enumValue(review, "oldHistoryUnexpectedlyChanged", YES_NO),
                enumValue(review, "truthfulnessConcern", YES_NO),
                safeRationale(review.path("rationale").asText("")),
                enumValue(review, "confidence", CONFIDENCE)
            ));
            if (result.get(scenarioId).rationale().isBlank()) {
                throw new IllegalArgumentException("REVIEW_RATIONALE_EMPTY");
            }
        }
        if (!returnedIds.equals(scenarioIds)) {
            throw new IllegalArgumentException("REVIEW_SCENARIO_ORDER_INVALID");
        }
        return result;
    }

    private String enumValue(JsonNode root, String field, Set<String> allowed) {
        String value = root.path(field).asText("").trim().toLowerCase(Locale.ROOT);
        if (!allowed.contains(value)) {
            throw new IllegalArgumentException("REVIEW_FIELD_INVALID_" + field);
        }
        return value;
    }

    private String safeRationale(String value) {
        String safe = redactor.redactOutboundText(value == null ? "" : value)
            .replaceAll("(?i)api[- ]?key|authorization|prompt|raw[-_ ]?response|reasoning", "[REDACTED]")
            .replaceAll("[\\r\\n\\t]+", " ")
            .trim();
        if (WINDOWS_PATH.matcher(safe).find() || UNIX_PATH.matcher(safe).find()) {
            safe = "[REDACTED_PATH]";
        }
        return safe.length() <= 500 ? safe : safe.substring(0, 500);
    }

    private void enforceCallBudget(ModelGatewayService.ModelCallDiagnostics diagnostics) {
        if (diagnostics == null) throw new IllegalArgumentException("REVIEW_DIAGNOSTICS_MISSING");
        if (diagnostics.effectiveMaxTokens() > MAX_PROVIDER_OUTPUT_TOKENS) {
            throw new IllegalArgumentException("REVIEW_OUTPUT_BUDGET_EXCEEDED");
        }
        if (diagnostics.requestCount() > MAX_PHYSICAL_REQUESTS) {
            throw new IllegalArgumentException("REVIEW_REQUEST_BUDGET_EXCEEDED");
        }
        if (diagnostics.completionTokens() > MAX_COMPLETION_TOKENS
            || diagnostics.totalTokens() > MAX_TOTAL_TOKENS) {
            throw new IllegalArgumentException("REVIEW_TOKEN_BUDGET_EXCEEDED");
        }
    }

    private Map<String, Object> successEntry(String scenarioId, BoundedJudgement judgement) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("scenarioId", safeLabel(scenarioId, 80));
        entry.put("judgement", judgement);
        return entry;
    }

    private Map<String, Object> failureEntry(String scenarioId, String failureCode) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("scenarioId", safeLabel(scenarioId, 80));
        entry.put("failureCode", safeCode(failureCode, "UNKNOWN"));
        return entry;
    }

    private Map<String, Object> safeDiagnostics(
        ModelGatewayService.ModelCallDiagnostics diagnostics,
        int fallbackRequestCount,
        String failureCode
    ) {
        Map<String, Object> safe = new LinkedHashMap<>();
        if (diagnostics == null) {
            safe.put("requestCount", boundedNonNegative(fallbackRequestCount));
            safe.put("inputTokens", 0);
            safe.put("completionTokens", 0);
            safe.put("totalTokens", 0);
            safe.put("latencyMs", 0);
            safe.put("requestSucceeded", false);
            safe.put("failureCode", safeCode(failureCode, "UNKNOWN"));
            return safe;
        }
        safe.put("requestCount", boundedNonNegative(
            Math.max(diagnostics.requestCount(), fallbackRequestCount)
        ));
        safe.put("inputTokens", boundedNonNegative(diagnostics.promptTokens()));
        safe.put("completionTokens", boundedNonNegative(diagnostics.completionTokens()));
        safe.put("totalTokens", boundedNonNegative(diagnostics.totalTokens()));
        safe.put("latencyMs", boundedNonNegative(diagnostics.latencyMs()));
        safe.put("requestSucceeded", diagnostics.requestSucceeded());
        safe.put("effectiveMaxTokens", boundedNonNegative(diagnostics.effectiveMaxTokens()));
        safe.put("truncated", diagnostics.truncated());
        safe.put("partialResult", diagnostics.partialResult());
        safe.put("schemaMatched", diagnostics.schemaMatched());
        safe.put("finishReason", safeCode(diagnostics.finishReason(), "UNKNOWN"));
        safe.put("normalizedFinishReason", safeCode(diagnostics.normalizedFinishReason(), "UNKNOWN"));
        safe.put("retryType", safeCode(diagnostics.retryType(), "NONE"));
        safe.put("failureStage", safeCode(diagnostics.failureStage(), ""));
        safe.put("failureCode", safeCode(
            failureCode == null || failureCode.isBlank() ? diagnostics.failureCode() : failureCode, ""
        ));
        safe.put("protocol", safeCode(diagnostics.protocol(), "UNKNOWN"));
        return safe;
    }

    private void writeArtifact(
        ProjectFlowRealModelEvalIT.ProviderConfig config,
        List<Map<String, Object>> entries,
        ModelGatewayService.ModelCallDiagnostics diagnostics,
        String failureCode,
        int fallbackRequestCount,
        boolean callAttempted,
        boolean complete
    ) throws Exception {
        String outputName = System.getProperty(
            "projectflow.eval.output-name",
            "v39-independent-semantic-review-" + config.protocol().name().toLowerCase(Locale.ROOT)
        ).replaceAll("[^A-Za-z0-9._-]", "_");
        Path output = Path.of("target", "projectflow-eval", outputName);
        Files.createDirectories(output);

        int successful = (int) entries.stream().filter(entry -> entry.containsKey("judgement")).count();
        Map<String, Object> artifact = new LinkedHashMap<>();
        artifact.put("version", ARTIFACT_VERSION);
        artifact.put("status", complete ? "COMPLETE" : "FAILED");
        artifact.put("complete", complete);
        artifact.put("generatedAt", Instant.now().toString());
        artifact.put("scenarioCount", entries.size());
        artifact.put("successfulScenarioCount", successful);
        artifact.put("failedScenarioCount", entries.size() - successful);
        artifact.put("inputPackage", PACKAGE_FILE);
        artifact.put("provider", Map.of(
            "name", safeLabel(config.name(), 120),
            "model", safeLabel(config.model(), 160),
            "protocol", safeCode(config.protocol().name(), "UNKNOWN")
        ));
        artifact.put("decisionBoundary",
            "MODEL_ONLY_DIAGNOSTIC; disagreement_or_uncertainty_stays_UNRESOLVED; no_Strong_Fact_promotion");
        artifact.put("callBudget", Map.of(
            "logicalModelCallLimit", MAX_LOGICAL_MODEL_CALLS,
            "logicalModelCalls", callAttempted ? 1 : 0,
            "physicalRequestLimit", MAX_PHYSICAL_REQUESTS,
            "providerOutputTokenLimitPerRequest", MAX_PROVIDER_OUTPUT_TOKENS,
            "completionTokenLimit", MAX_COMPLETION_TOKENS,
            "totalTokenLimit", MAX_TOTAL_TOKENS
        ));
        artifact.put("sharedCallDiagnostics", safeDiagnostics(
            diagnostics, fallbackRequestCount, failureCode
        ));
        artifact.put("failureCode", complete ? "" : safeCode(failureCode, "UNKNOWN"));
        artifact.put("entries", entries);
        artifact.put("security", Map.of(
            "apiKeyPersistedInArtifact", false,
            "authorizationPersistedInArtifact", false,
            "promptPersisted", false,
            "rawResponsePersisted", false,
            "reasoningPersisted", false,
            "absolutePathPersisted", false
        ));
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(
            output.resolve("independent-semantic-review.json").toFile(), artifact
        );
    }

    private AiProvider provider(ProjectFlowRealModelEvalIT.ProviderConfig config) {
        AiProvider provider = new AiProvider(UUID.randomUUID());
        int boundedMaxTokens = Math.max(256, Math.min(config.maxTokens(), MAX_PROVIDER_OUTPUT_TOKENS));
        provider.update(
            config.name(), config.baseUrl(), config.apiKey(), config.model(), config.type(),
            0.1, boundedMaxTokens, false, List.of("V39_INDEPENDENT_SEMANTIC_REVIEW")
        );
        provider.configureProtocol(
            config.protocol(), null, null, null, null, Map.of(), config.timeoutSeconds(), null,
            config.supportsJsonMode(), null, config.supportsReasoning(), config.supportsReasoningControl()
        );
        return provider;
    }

    private void printSafeDiagnostics(ModelGatewayService.ModelCallDiagnostics diagnostics) {
        if (diagnostics == null) {
            System.out.println("V39_INDEPENDENT_REVIEW_DONE requests=0 tokens=0");
            return;
        }
        System.out.println("V39_INDEPENDENT_REVIEW_DONE scenarios=12"
            + " requests=" + boundedNonNegative(diagnostics.requestCount())
            + " tokens=" + boundedNonNegative(diagnostics.totalTokens())
            + " latencyMs=" + boundedNonNegative(diagnostics.latencyMs())
            + " finish=" + safeCode(diagnostics.normalizedFinishReason(), "UNKNOWN"));
    }

    private ModelGatewayService.ModelCallDiagnostics failureDiagnostics(Throwable failure) {
        Throwable current = failure;
        for (int depth = 0; current != null && depth < 8; depth++, current = current.getCause()) {
            if (current instanceof ModelGatewayService.ModelResponseFormatException format
                && format.diagnostics() != null) {
                return format.diagnostics();
            }
        }
        return null;
    }

    private int failureRequestCount(Throwable failure) {
        Throwable current = failure;
        for (int depth = 0; current != null && depth < 8; depth++, current = current.getCause()) {
            if (current instanceof ModelGatewayService.ModelHttpException http) {
                return http.requestCount();
            }
            if (current instanceof ModelGatewayService.ModelTransportException transport) {
                return transport.requestCount();
            }
        }
        return 0;
    }

    private String safeFailureCode(
        Throwable failure,
        ModelGatewayService.ModelCallDiagnostics diagnostics
    ) {
        if (diagnostics != null && diagnostics.failureCode() != null
            && !diagnostics.failureCode().isBlank()) {
            return safeCode(diagnostics.failureCode(), "FORMAT_UNKNOWN");
        }
        Throwable current = failure;
        for (int depth = 0; current != null && depth < 8; depth++, current = current.getCause()) {
            if (current instanceof IllegalArgumentException
                && current.getMessage() != null
                && (current.getMessage().startsWith("REVIEW_")
                    || current.getMessage().startsWith("BLIND_"))) {
                return safeCode(current.getMessage(), "REVIEW_FORMAT_INVALID");
            }
            if (current instanceof ModelGatewayService.ModelHttpException http) {
                return safeCode("HTTP_" + http.statusCode(), "HTTP_ERROR");
            }
            if (current instanceof ModelGatewayService.ModelTransportException) return "TRANSPORT";
            if (current instanceof InterruptedException) return "INTERRUPTED";
        }
        return "REVIEW_CALL_FAILED";
    }

    private static List<String> scenarioIds(JsonNode packageRoot) {
        List<String> ids = new ArrayList<>();
        packageRoot.path("scenarios").forEach(node -> ids.add(node.path("id").asText("")));
        return List.copyOf(ids);
    }

    private static int boundedNonNegative(int value) {
        return Math.max(0, Math.min(value, 10_000_000));
    }

    private static long boundedNonNegative(long value) {
        return Math.max(0L, Math.min(value, 86_400_000L));
    }

    private static String safeCode(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        String candidate = value.trim().toUpperCase(Locale.ROOT);
        return SAFE_CODE.matcher(candidate).matches() ? candidate : fallback;
    }

    private String safeLabel(String value, int maxLength) {
        String safe = redactor.redactOutboundText(value == null ? "" : value)
            .replaceAll("[^\\p{L}\\p{N}._:-]", "_");
        if (safe.length() > maxLength) safe = safe.substring(0, maxLength);
        return safe;
    }

    record BoundedJudgement(
        String attachmentSemanticallySupported,
        String shouldRemainIndependent,
        String oldHistoryUnexpectedlyChanged,
        String truthfulnessConcern,
        String rationale,
        String confidence
    ) {
    }
}
