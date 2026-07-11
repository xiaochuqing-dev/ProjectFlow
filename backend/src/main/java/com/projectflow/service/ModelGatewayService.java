package com.projectflow.service;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.entity.AiProvider;
import com.projectflow.service.ModelCapabilityRegistry.ModelCapabilities;
import com.projectflow.service.ModelRequestPolicy.RequestParameters;

@Service
public class ModelGatewayService {
    private static final int MAX_TRANSPORT_ATTEMPTS = 2;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(8);
    private static final Semaphore MODEL_REQUEST_SLOTS = new Semaphore(4, true);

    private final Duration configuredRequestTimeout;
    private final ObjectMapper objectMapper;
    private final AiProviderUrlGuard aiProviderUrlGuard;
    private final ModelOutputAdapter outputAdapter;
    private final ModelCapabilityRegistry capabilityRegistry;
    private final ModelRequestPolicy requestPolicy;
    private final HttpClient httpClient;

    @Autowired
    public ModelGatewayService(
        ObjectMapper objectMapper,
        AiProviderUrlGuard aiProviderUrlGuard,
        ModelOutputAdapter outputAdapter,
        ModelCapabilityRegistry capabilityRegistry,
        ModelRequestPolicy requestPolicy,
        @Value("${projectflow.model.request-timeout-seconds:240}") int requestTimeoutSeconds
    ) {
        this.objectMapper = objectMapper;
        this.aiProviderUrlGuard = aiProviderUrlGuard;
        this.outputAdapter = outputAdapter;
        this.capabilityRegistry = capabilityRegistry;
        this.requestPolicy = requestPolicy;
        this.configuredRequestTimeout = Duration.ofSeconds(Math.max(30, requestTimeoutSeconds));
        this.httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    }

    /** 保留给独立测试和兼容调用；生产入口必须传入明确的 ModelTaskType。 */
    public ModelGatewayService(
        ObjectMapper objectMapper,
        AiProviderUrlGuard aiProviderUrlGuard,
        ModelOutputAdapter outputAdapter,
        int requestTimeoutSeconds
    ) {
        this(objectMapper, aiProviderUrlGuard, outputAdapter, new ModelCapabilityRegistry(), new ModelRequestPolicy(), requestTimeoutSeconds);
    }

    public JsonNode callJson(AiProvider provider, String prompt, int outputTokenLimit) throws IOException, InterruptedException {
        return callStructured(provider, prompt, outputTokenLimit).parsed().root();
    }

    public StructuredModelResponse callStructured(AiProvider provider, String prompt, int outputTokenLimit) throws IOException, InterruptedException {
        ModelCapabilities capabilities = capabilityRegistry.resolve(provider);
        RequestParameters calculated = requestPolicy.initial(provider, capabilities, ModelTaskType.LEGACY_STRUCTURED, prompt);
        int requested = Math.max(256, outputTokenLimit);
        RequestParameters compatibility = new RequestParameters(
            requested, Math.min(capabilities.maxOutputTokens(), requested), calculated.configuredTemperature(),
            calculated.recommendedTemperature(), calculated.effectiveTemperature(), calculated.temperatureSent(),
            calculated.temperatureDecision(), "兼容调用显式申请输出预算；新业务入口应使用任务动态策略",
            calculated.timeoutSeconds(), "NONE"
        );
        return execute(provider, prompt, ModelTaskType.LEGACY_STRUCTURED, capabilities, compatibility);
    }

    public StructuredModelResponse callStructured(AiProvider provider, String prompt, ModelTaskType task)
        throws IOException, InterruptedException {
        ModelCapabilities capabilities = capabilityRegistry.resolve(provider);
        RequestParameters parameters = requestPolicy.initial(provider, capabilities, task, prompt);
        return execute(provider, prompt, task, capabilities, parameters);
    }

    private StructuredModelResponse execute(
        AiProvider provider,
        String prompt,
        ModelTaskType task,
        ModelCapabilities capabilities,
        RequestParameters initialParameters
    ) throws IOException, InterruptedException {
        StructuredModelResponse firstResponse;
        try {
            firstResponse = sendStructuredRequest(provider, prompt, task, capabilities, initialParameters);
        } catch (ModelSchemaMismatchException mismatch) {
            return repairSchema(provider, task, capabilities, initialParameters, mismatch);
        } catch (ModelOutputTruncatedException exhausted) {
            return retryExhaustedOutput(provider, prompt, task, capabilities, initialParameters, exhausted, null);
        }
        if (!firstResponse.diagnostics().truncated() && !firstResponse.parsed().partial()) return firstResponse;
        return retryExhaustedOutput(provider, prompt, task, capabilities, initialParameters, null, firstResponse);
    }

    private StructuredModelResponse repairSchema(
        AiProvider provider,
        ModelTaskType task,
        ModelCapabilities capabilities,
        RequestParameters initialParameters,
        ModelSchemaMismatchException firstFailure
    ) throws IOException, InterruptedException {
        RequestParameters retryParameters = requestPolicy.recovery(
            initialParameters, capabilities, task, "SCHEMA_REPAIR_RETRY", firstFailure.rawContent().length()
        );
        String repairPrompt = """
            上一次模型结果是可读取的 JSON，但不符合目标业务 Schema。不要重新分析事实，只把已有结果转换为下面结构。
            只返回转换后的 JSON；缺失字段使用空字符串或空数组，不要补造证据。
            目标 Schema：%s
            待转换结果：%s
            """.formatted(task.minimalSchema(), bounded(firstFailure.rawContent(), 30_000));
        try {
            StructuredModelResponse repaired = sendStructuredRequest(provider, repairPrompt, task, capabilities, retryParameters);
            return repaired.withRecovery(firstFailure.diagnostics(), "SCHEMA_REPAIR_RETRY", true);
        } catch (IOException retryFailure) {
            ModelCallDiagnostics retryDiagnostics = retryFailure instanceof ModelResponseFormatException format
                ? format.diagnostics() : null;
            ModelCallDiagnostics failedDiagnostics = retryDiagnostics == null
                ? firstFailure.diagnostics().withRecovery("SCHEMA_REPAIR_RETRY", false)
                : retryDiagnostics.combine(firstFailure.diagnostics(), "SCHEMA_REPAIR_RETRY", false);
            throw new ModelSchemaRepairException(
                "模型返回结构偏离目标 Schema，定向修复仍未成功",
                retryFailure,
                failedDiagnostics.withFailure("SCHEMA_REPAIR", "SCHEMA_REPAIR_FAILED")
            );
        }
    }

    private StructuredModelResponse retryExhaustedOutput(
        AiProvider provider,
        String prompt,
        ModelTaskType task,
        ModelCapabilities capabilities,
        RequestParameters initialParameters,
        ModelOutputTruncatedException firstFailure,
        StructuredModelResponse partialResponse
    ) throws IOException, InterruptedException {
        ModelCallDiagnostics firstDiagnostics = firstFailure == null ? partialResponse.diagnostics() : firstFailure.diagnostics();
        boolean reasoningExhausted = firstDiagnostics != null && firstDiagnostics.reasoningBudgetExhausted();
        String retryType = reasoningExhausted ? "EMPTY_AFTER_REASONING_RETRY" : "TRUNCATION_RETRY";
        int visibleLength = partialResponse == null ? 0 : partialResponse.rawContent().length();
        RequestParameters retryParameters = requestPolicy.recovery(
            initialParameters, capabilities, task, retryType, visibleLength
        );
        String retryPrompt = prompt + (reasoningExhausted ? """

            【可见输出恢复】上次 reasoning 疑似占满共享预算且没有形成完整可见结果。请直接生成最终 JSON，
            不要输出推理过程；保留所有有证据支持的重要条目。
            """ : """

            【截断恢复】上次最终 JSON 未完整输出。本次预算已按首次结果提高，请直接返回完整目标 JSON，
            不要输出解释；不要无依据删减已有重要结论。
            """);
        try {
            StructuredModelResponse recovered = sendStructuredRequest(provider, retryPrompt, task, capabilities, retryParameters);
            return recovered.withRecovery(firstDiagnostics, retryType, true);
        } catch (IOException retryFailure) {
            if (partialResponse != null && partialResponse.parsed().partial() && partialResponse.parsed().recoveredItems() > 0) {
                return partialResponse.withFailedRecovery(retryType);
            }
            ModelCallDiagnostics retryDiagnostics = retryFailure instanceof ModelResponseFormatException format
                ? format.diagnostics() : null;
            ModelCallDiagnostics failedDiagnostics = retryDiagnostics == null
                ? firstDiagnostics.withRecovery(retryType, false)
                : retryDiagnostics.combine(firstDiagnostics, retryType, false);
            throw new ModelOutputTruncatedException(
                "模型输出预算不足，调整预算后仍未得到可用结构",
                retryFailure,
                failedDiagnostics.withFailure(
                    "OUTPUT_RECOVERY",
                    reasoningExhausted ? "REASONING_EXHAUSTED_OUTPUT" : "OUTPUT_BUDGET_EXHAUSTED"
                )
            );
        }
    }

    private StructuredModelResponse sendStructuredRequest(
        AiProvider provider,
        String prompt,
        ModelTaskType task,
        ModelCapabilities capabilities,
        RequestParameters parameters
    ) throws IOException, InterruptedException {
        ModelCancellationContext.throwIfCancelled();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", provider.getModelName());
        body.put("messages", List.of(
            Map.of("role", "system", "content", "只返回合法 JSON，不要 Markdown 代码块。所有自然语言字段必须使用简体中文；技术名、文件路径和代码标识符保留原文。"),
            Map.of("role", "user", "content", prompt)
        ));
        if (parameters.temperatureSent() && parameters.effectiveTemperature() != null) {
            body.put("temperature", parameters.effectiveTemperature());
        }
        body.put("max_tokens", parameters.effectiveMaxTokens());
        if (capabilities.supportsJsonMode()) body.put("response_format", Map.of("type", "json_object"));

        Duration timeout = Duration.ofSeconds(Math.max(configuredRequestTimeout.toSeconds(), parameters.timeoutSeconds()));
        HttpRequest request = HttpRequest.newBuilder()
            .uri(aiProviderUrlGuard.chatCompletionsUri(provider.getBaseUrl()))
            .timeout(timeout)
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + provider.getApiKey())
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
            .build();
        int allowedAttempts = "NONE".equals(parameters.retryType()) ? MAX_TRANSPORT_ATTEMPTS : 1;
        for (int attempt = 1; attempt <= allowedAttempts; attempt++) {
            long startedAt = System.nanoTime();
            try {
                MODEL_REQUEST_SLOTS.acquire();
                HttpResponse<String> response;
                try {
                    response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                } finally {
                    MODEL_REQUEST_SLOTS.release();
                }
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return parseModelResponse(
                        response.body(), provider, task, capabilities, parameters, prompt == null ? 0 : prompt.length(), timeout.toSeconds(),
                        elapsedMs(startedAt), attempt - 1
                    );
                }
                if (attempt < allowedAttempts && isTransientModelStatus(response.statusCode())) {
                    pauseBeforeRetry(attempt);
                    continue;
                }
                throw new ModelHttpException(response.statusCode());
            } catch (HttpTimeoutException exception) {
                if (attempt >= allowedAttempts) throw exception;
                pauseBeforeRetry(attempt);
            } catch (IOException exception) {
                if (exception instanceof ModelHttpException || exception instanceof ModelResponseFormatException || attempt >= allowedAttempts) {
                    throw exception;
                }
                pauseBeforeRetry(attempt);
            }
        }
        throw new IOException("model request failed");
    }

    StructuredModelResponse parseModelResponse(
        String responseBody,
        AiProvider provider,
        ModelTaskType task,
        ModelCapabilities capabilities,
        RequestParameters parameters,
        int promptSize,
        long timeoutSeconds,
        long latencyMs,
        int transportRetryCount
    ) throws IOException {
        JsonNode root;
        try {
            root = objectMapper.readTree(responseBody);
        } catch (IOException exception) {
            throw new ModelResponseFormatException("模型服务返回体无法读取", exception, null);
        }
        JsonNode message = root.at("/choices/0/message");
        String content = message.path("content").asText("");
        String finishReason = root.at("/choices/0/finish_reason").asText("");
        JsonNode usage = root.path("usage");
        boolean actualUsage = usage.has("prompt_tokens") || usage.has("completion_tokens") || usage.has("total_tokens");
        int promptTokens = usage.path("prompt_tokens").asInt(0);
        int completionTokens = actualUsage ? usage.path("completion_tokens").asInt(0) : estimateTokens(content);
        int totalTokens = actualUsage ? usage.path("total_tokens").asInt(promptTokens + completionTokens) : completionTokens;
        String usageSource = actualUsage ? "ACTUAL" : content.isBlank() ? "UNAVAILABLE" : "ESTIMATED";
        int reasoningLength = reasoningLength(message, capabilities.reasoningFieldNames());
        boolean nearLimit = completionTokens > 0 && completionTokens >= Math.ceil(parameters.effectiveMaxTokens() * 0.92);
        boolean truncated = "length".equalsIgnoreCase(finishReason) || nearLimit || outputAdapter.likelyTruncated(content);
        boolean reasoningExhausted = reasoningLength > 0 && (content.isBlank() || nearLimit);
        String diagnosticRetryType = transportRetryCount > 0 && "NONE".equals(parameters.retryType())
            ? "TRANSPORT_RETRY" : parameters.retryType();
        ModelCallDiagnostics diagnostics = new ModelCallDiagnostics(
            task.entryPoint(), task.name(), provider.getName(), provider.getModelName(), capabilities.profile(),
            promptSize, promptSize,
            finishReason, promptTokens, completionTokens, totalTokens, provider.getMaxTokens(),
            parameters.taskRequestedMaxTokens(), parameters.effectiveMaxTokens(), parameters.configuredTemperature(),
            parameters.recommendedTemperature(), parameters.effectiveTemperature() == null ? 0 : parameters.effectiveTemperature(),
            parameters.temperatureSent(), parameters.temperatureDecision(), parameters.maxTokenDecision(), timeoutSeconds, latencyMs,
            true, !content.isBlank(), truncated, !"NONE".equals(parameters.retryType()), false,
            transportRetryCount, false, false, 0, usageSource, reasoningLength > 0, reasoningLength,
            reasoningExhausted, 1 + transportRetryCount, diagnosticRetryType, false, "RESPONSE_PARSE", ""
        );
        if (content.isBlank()) {
            if (truncated || reasoningLength > 0) {
                throw new ModelOutputTruncatedException("模型输出预算已耗尽，尚未生成可见内容", null, diagnostics);
            }
            throw new ModelEmptyContentException("模型服务已响应，但没有返回内容", diagnostics.withFailure("RESPONSE_EXTRACT", "EMPTY_CONTENT"));
        }
        try {
            ModelOutputAdapter.ParsedOutput parsed = outputAdapter.parse(content, task);
            boolean schemaMatched = task.schemaMatches(parsed.root(), outputAdapter);
            ModelCallDiagnostics completed = diagnostics.withParsed(parsed.repaired(), parsed.partial(), parsed.recoveredItems(), schemaMatched);
            if (!schemaMatched) {
                throw new ModelSchemaMismatchException("JSON 可读取，但不符合目标业务 Schema", content, completed.withFailure("SCHEMA_MATCH", "SCHEMA_MISMATCH"));
            }
            return new StructuredModelResponse(content, parsed, completed);
        } catch (ModelSchemaMismatchException exception) {
            throw exception;
        } catch (IOException exception) {
            if (truncated) {
                throw new ModelOutputTruncatedException("模型输出达到长度上限且结构不完整", exception, diagnostics.withFailure("JSON_PARSE", "OUTPUT_BUDGET_EXHAUSTED"));
            }
            throw new ModelResponseFormatException("模型已返回内容，但 JSON 语法无法解析", exception, diagnostics.withFailure("JSON_PARSE", "JSON_PARSE_FAILED"));
        }
    }

    /** 兼容旧定向测试签名。 */
    StructuredModelResponse parseModelResponse(
        String responseBody,
        AiProvider provider,
        int taskPolicyMaxTokens,
        int effectiveMaxTokens,
        double effectiveTemperature,
        long latencyMs,
        int transportRetryCount,
        boolean compactRetry
    ) throws IOException {
        ModelCapabilities capabilities = capabilityRegistry.resolve(provider);
        RequestParameters parameters = new RequestParameters(
            taskPolicyMaxTokens, effectiveMaxTokens, provider.getTemperature(), 0.2, effectiveTemperature, true,
            "兼容测试参数", "兼容测试参数", (int) configuredRequestTimeout.toSeconds(), compactRetry ? "TRUNCATION_RETRY" : "NONE"
        );
        return parseModelResponse(
            responseBody, provider, ModelTaskType.LEGACY_STRUCTURED, capabilities, parameters,
            0, configuredRequestTimeout.toSeconds(), latencyMs, transportRetryCount
        );
    }

    public String failureMessage(Exception exception) {
        String code = ModelFailureClassifier.classifyException(exception);
        return ModelFailureClassifier.humanReason(code, "");
    }

    private boolean isTransientModelStatus(int statusCode) {
        return statusCode == 429 || statusCode == 500 || statusCode == 502 || statusCode == 503 || statusCode == 504;
    }

    private void pauseBeforeRetry(int attempt) throws InterruptedException { Thread.sleep(400L * attempt); }

    private int reasoningLength(JsonNode message, List<String> fieldNames) {
        if (message == null || !message.isObject()) return 0;
        int length = 0;
        for (String field : fieldNames) {
            JsonNode value = message.path(field);
            if (!value.isMissingNode() && !value.isNull()) {
                length += value.isTextual() ? value.asText("").length() : value.toString().length();
            }
        }
        return length;
    }

    private int estimateTokens(String content) {
        return content == null || content.isBlank() ? 0 : Math.max(1, (int) Math.ceil(content.length() / 4.0));
    }

    private long elapsedMs(long startedAt) { return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000); }
    private String bounded(String value, int max) { return value.length() <= max ? value : value.substring(0, max); }

    public record StructuredModelResponse(String rawContent, ModelOutputAdapter.ParsedOutput parsed, ModelCallDiagnostics diagnostics) {
        public StructuredModelResponse(String rawContent, ModelOutputAdapter.ParsedOutput parsed) {
            this(rawContent, parsed, ModelCallDiagnostics.unknown(parsed));
        }

        StructuredModelResponse withRecovery(ModelCallDiagnostics previous, String retryType, boolean succeeded) {
            return new StructuredModelResponse(rawContent, parsed, diagnostics.combine(previous, retryType, succeeded));
        }

        StructuredModelResponse withFailedRecovery(String retryType) {
            return new StructuredModelResponse(rawContent, parsed, diagnostics.withRecovery(retryType, false));
        }
    }

    public record ModelCallDiagnostics(
        String entryPoint,
        String taskType,
        String providerName,
        String modelName,
        String capabilityProfile,
        int inputSize,
        int promptSize,
        String finishReason,
        int promptTokens,
        int completionTokens,
        int totalTokens,
        int providerMaxTokens,
        int taskPolicyMaxTokens,
        int effectiveMaxTokens,
        double providerTemperature,
        double recommendedTemperature,
        double effectiveTemperature,
        boolean temperatureSent,
        String temperatureDecision,
        String maxTokenDecision,
        long timeoutSeconds,
        long latencyMs,
        boolean requestSucceeded,
        boolean contentPresent,
        boolean truncated,
        boolean compactRetryAttempted,
        boolean compactRetrySucceeded,
        int transportRetryCount,
        boolean jsonRepaired,
        boolean partialResult,
        int recoveredItems,
        String usageSource,
        boolean reasoningPresent,
        int reasoningLength,
        boolean reasoningBudgetExhausted,
        int requestCount,
        String retryType,
        boolean schemaMatched,
        String failureStage,
        String failureCode
    ) {
        static ModelCallDiagnostics unknown(ModelOutputAdapter.ParsedOutput parsed) {
            return new ModelCallDiagnostics(
                "", "", "", "", "UNKNOWN", 0, 0, "", 0, 0, 0, 0, 0, 0,
                0, 0, 0, false, "", "", 0, 0, true, true, parsed.partial(), false, false,
                0, parsed.repaired(), parsed.partial(), parsed.recoveredItems(), "UNAVAILABLE", false, 0, false,
                0, "NONE", true, "", ""
            );
        }

        ModelCallDiagnostics withParsed(boolean repaired, boolean partial, int recovered, boolean matched) {
            return copy(
                promptTokens, completionTokens, totalTokens, latencyMs, requestCount, retryType,
                truncated || partial, compactRetryAttempted, compactRetrySucceeded, repaired, partial, recovered,
                matched, failureStage, failureCode
            );
        }

        ModelCallDiagnostics withFailure(String stage, String code) {
            return copy(
                promptTokens, completionTokens, totalTokens, latencyMs, requestCount, retryType,
                truncated, compactRetryAttempted, compactRetrySucceeded, jsonRepaired, partialResult, recoveredItems,
                schemaMatched, stage, code
            );
        }

        ModelCallDiagnostics withRecovery(String type, boolean succeeded) {
            boolean outputRecovery = !"SCHEMA_REPAIR_RETRY".equals(type);
            return copy(
                promptTokens, completionTokens, totalTokens, latencyMs, requestCount + 1, type,
                truncated, outputRecovery, outputRecovery && succeeded, jsonRepaired, partialResult, recoveredItems,
                schemaMatched, succeeded ? "" : failureStage, succeeded ? "" : failureCode
            );
        }

        ModelCallDiagnostics combine(ModelCallDiagnostics previous, String type, boolean succeeded) {
            if (previous == null) return withRecovery(type, succeeded);
            boolean outputRecovery = !"SCHEMA_REPAIR_RETRY".equals(type);
            return copy(
                previous.promptTokens + promptTokens,
                previous.completionTokens + completionTokens,
                previous.totalTokens + totalTokens,
                previous.latencyMs + latencyMs,
                previous.requestCount + requestCount,
                type,
                truncated,
                outputRecovery,
                outputRecovery && succeeded,
                jsonRepaired,
                partialResult,
                recoveredItems,
                schemaMatched,
                succeeded ? "" : failureStage,
                succeeded ? "" : failureCode
            );
        }

        private ModelCallDiagnostics copy(
            int newPromptTokens, int newCompletionTokens, int newTotalTokens, long newLatency, int newRequestCount,
            String newRetryType, boolean newTruncated, boolean retryAttempted, boolean retrySucceeded,
            boolean repaired, boolean partial, int recovered, boolean matched, String stage, String code
        ) {
            return new ModelCallDiagnostics(
                entryPoint, taskType, providerName, modelName, capabilityProfile, inputSize, promptSize, finishReason,
                newPromptTokens, newCompletionTokens, newTotalTokens, providerMaxTokens, taskPolicyMaxTokens,
                effectiveMaxTokens, providerTemperature, recommendedTemperature, effectiveTemperature, temperatureSent,
                temperatureDecision, maxTokenDecision, timeoutSeconds, newLatency, requestSucceeded, contentPresent,
                newTruncated, retryAttempted, retrySucceeded, transportRetryCount, repaired, partial, recovered,
                usageSource, reasoningPresent, reasoningLength, reasoningBudgetExhausted, newRequestCount, newRetryType,
                matched, stage, code
            );
        }
    }

    public static final class ModelHttpException extends IOException {
        private final int statusCode;
        public ModelHttpException(int statusCode) { super("model HTTP " + statusCode); this.statusCode = statusCode; }
        public int statusCode() { return statusCode; }
    }

    public static class ModelResponseFormatException extends IOException {
        private final ModelCallDiagnostics diagnostics;
        public ModelResponseFormatException(String message, Throwable cause) { this(message, cause, null); }
        public ModelResponseFormatException(String message, Throwable cause, ModelCallDiagnostics diagnostics) {
            super(message, cause); this.diagnostics = diagnostics;
        }
        public ModelCallDiagnostics diagnostics() { return diagnostics; }
    }

    public static final class ModelOutputTruncatedException extends ModelResponseFormatException {
        public ModelOutputTruncatedException(String message, Throwable cause, ModelCallDiagnostics diagnostics) {
            super(message, cause, diagnostics);
        }
    }

    public static final class ModelEmptyContentException extends ModelResponseFormatException {
        public ModelEmptyContentException(String message, ModelCallDiagnostics diagnostics) { super(message, null, diagnostics); }
    }

    public static final class ModelSchemaMismatchException extends ModelResponseFormatException {
        private final String rawContent;
        public ModelSchemaMismatchException(String message, String rawContent, ModelCallDiagnostics diagnostics) {
            super(message, null, diagnostics); this.rawContent = rawContent == null ? "" : rawContent;
        }
        String rawContent() { return rawContent; }
    }

    public static final class ModelSchemaRepairException extends ModelResponseFormatException {
        public ModelSchemaRepairException(String message, Throwable cause, ModelCallDiagnostics diagnostics) {
            super(message, cause, diagnostics);
        }
    }
}
