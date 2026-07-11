package com.projectflow.service;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.entity.AiProvider;

@Service
public class ModelGatewayService {
    // V3.3.4 小阶段修复：复杂模型分析（开发推进段归并 / 能力分析）需要数分钟，
    // 不再固定 35 秒上限。改为可配置，默认 240 秒，覆盖 DeepSeek 多 commit 分析场景。
    private static final int MAX_MODEL_ATTEMPTS = 2;
    private static final int COMPACT_OUTPUT_MAX_TOKENS = 2_000;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(8);
    private static final double MAX_STRUCTURED_TEMPERATURE = 0.3;
    // 单机最多并发 4 个模型 HTTP 请求，避免连接和 Provider 配额被耗尽。
    private static final Semaphore MODEL_REQUEST_SLOTS = new Semaphore(4, true);
    private final Duration modelRequestTimeout;

    private final ObjectMapper objectMapper;
    private final AiProviderUrlGuard aiProviderUrlGuard;
    private final ModelOutputAdapter outputAdapter;
    private final HttpClient httpClient;

    public ModelGatewayService(
        ObjectMapper objectMapper,
        AiProviderUrlGuard aiProviderUrlGuard,
        ModelOutputAdapter outputAdapter,
        @Value("${projectflow.model.request-timeout-seconds:240}") int requestTimeoutSeconds
    ) {
        this.objectMapper = objectMapper;
        this.aiProviderUrlGuard = aiProviderUrlGuard;
        this.outputAdapter = outputAdapter;
        this.modelRequestTimeout = Duration.ofSeconds(Math.max(30, requestTimeoutSeconds));
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();
    }

    public JsonNode callJson(AiProvider provider, String prompt, int outputTokenLimit) throws IOException, InterruptedException {
        return callStructured(provider, prompt, outputTokenLimit).parsed().root();
    }

    public StructuredModelResponse callStructured(AiProvider provider, String prompt, int outputTokenLimit) throws IOException, InterruptedException {
        int effectiveMaxTokens = Math.min(provider.getMaxTokens(), outputTokenLimit);
        double effectiveTemperature = Math.min(provider.getTemperature(), MAX_STRUCTURED_TEMPERATURE);
        StructuredModelResponse firstResponse;
        try {
            firstResponse = sendStructuredRequest(
                provider, prompt, outputTokenLimit, effectiveMaxTokens, effectiveTemperature, false
            );
        } catch (ModelOutputTruncatedException firstFailure) {
            try {
                int compactMaxTokens = Math.min(effectiveMaxTokens, COMPACT_OUTPUT_MAX_TOKENS);
                StructuredModelResponse compact = sendStructuredRequest(
                    provider, compactPrompt(prompt), COMPACT_OUTPUT_MAX_TOKENS, compactMaxTokens, effectiveTemperature, true
                );
                return compact.withCompactRetry(true, !compact.diagnostics().truncated());
            } catch (IOException compactFailure) {
                throw new ModelOutputTruncatedException(
                    "模型输出达到长度上限，紧凑重试后仍未得到可用结构",
                    compactFailure,
                    firstFailure.diagnostics().withCompactRetry(true, false)
                );
            }
        }
        if (!firstResponse.diagnostics().truncated() && !firstResponse.parsed().partial()) {
            return firstResponse;
        }
        try {
            int compactMaxTokens = Math.min(effectiveMaxTokens, COMPACT_OUTPUT_MAX_TOKENS);
            StructuredModelResponse compact = sendStructuredRequest(
                provider, compactPrompt(prompt), COMPACT_OUTPUT_MAX_TOKENS, compactMaxTokens, effectiveTemperature, true
            );
            return compact.withCompactRetry(true, !compact.diagnostics().truncated());
        } catch (IOException compactFailure) {
            // 第一次响应已有完整条目时，紧凑重试失败也保留可用部分，并明确附带警告诊断。
            return firstResponse.withCompactRetry(true, false);
        }
    }

    private StructuredModelResponse sendStructuredRequest(
        AiProvider provider,
        String prompt,
        int taskPolicyMaxTokens,
        int effectiveMaxTokens,
        double effectiveTemperature,
        boolean compactRetry
    ) throws IOException, InterruptedException {
        Map<String, Object> body = Map.of(
            "model", provider.getModelName(),
            "messages", List.of(
                Map.of(
                    "role",
                    "system",
                    "content",
                    "只返回合法 JSON，不要 Markdown 代码块。所有自然语言字段必须使用简体中文；技术名、文件路径和代码标识符保留原文。"
                ),
                Map.of("role", "user", "content", prompt)
            ),
            "temperature", effectiveTemperature,
            "max_tokens", effectiveMaxTokens
        );
        HttpRequest request = HttpRequest.newBuilder()
            .uri(aiProviderUrlGuard.chatCompletionsUri(provider.getBaseUrl()))
            .timeout(modelRequestTimeout)
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + provider.getApiKey())
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
            .build();
        // 紧凑重试不再叠加网络重试，单个结构化任务最多发送 3 次请求。
        int allowedAttempts = compactRetry ? 1 : MAX_MODEL_ATTEMPTS;
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
                        response.body(), provider, taskPolicyMaxTokens, effectiveMaxTokens, effectiveTemperature,
                        elapsedMs(startedAt), attempt - 1, compactRetry
                    );
                }
                if (attempt < allowedAttempts && isTransientModelStatus(response.statusCode())) {
                    pauseBeforeRetry(attempt);
                    continue;
                }
                throw new ModelHttpException(response.statusCode());
            } catch (HttpTimeoutException exception) {
                if (attempt >= allowedAttempts) {
                    throw exception;
                }
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

    public String failureMessage(Exception exception) {
        String code = ModelFailureClassifier.classifyException(exception);
        return ModelFailureClassifier.humanReason(code, "");
    }

    /**
     * V3.3.4 小阶段修复：把不可重试的 HTTP 错误码封装为可分类的异常，
     * 让上层能区分 401/403、429、5xx 等具体原因。
     */
    public static final class ModelHttpException extends IOException {
        private final int statusCode;

        public ModelHttpException(int statusCode) {
            super("model HTTP " + statusCode);
            this.statusCode = statusCode;
        }

        public int statusCode() {
            return statusCode;
        }
    }

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
        int reasoningLength = reasoningLength(message);
        boolean nearLimit = completionTokens > 0 && completionTokens >= Math.ceil(effectiveMaxTokens * 0.92);
        boolean truncated = "length".equalsIgnoreCase(finishReason) || nearLimit || outputAdapter.likelyTruncated(content);
        ModelCallDiagnostics diagnostics = new ModelCallDiagnostics(
            provider.getName(), provider.getModelName(), finishReason, promptTokens, completionTokens, totalTokens,
            provider.getMaxTokens(), taskPolicyMaxTokens, effectiveMaxTokens, provider.getTemperature(), effectiveTemperature,
            modelRequestTimeout.toSeconds(), latencyMs, true, !content.isBlank(), truncated, compactRetry, false,
            transportRetryCount, false, false, 0, usageSource, reasoningLength > 0, reasoningLength, 1 + transportRetryCount
        );
        if (content.isBlank()) {
            if (truncated || reasoningLength > 0) {
                throw new ModelOutputTruncatedException("模型输出预算已耗尽，尚未生成可见内容", null, diagnostics);
            }
            throw new ModelEmptyContentException("模型服务已响应，但没有返回内容", diagnostics);
        }
        try {
            ModelOutputAdapter.ParsedOutput parsed = outputAdapter.parse(content);
            ModelCallDiagnostics completed = diagnostics.withParsed(parsed.repaired(), parsed.partial(), parsed.recoveredItems());
            return new StructuredModelResponse(content, parsed, completed);
        } catch (IOException exception) {
            if (truncated) {
                throw new ModelOutputTruncatedException("模型输出达到长度上限且结构不完整", exception, diagnostics);
            }
            throw new ModelResponseFormatException("模型已返回内容，但 JSON 语法无法解析", exception, diagnostics);
        }
    }

    private boolean isTransientModelStatus(int statusCode) {
        return statusCode == 429 || statusCode == 500 || statusCode == 502 || statusCode == 503 || statusCode == 504;
    }

    private void pauseBeforeRetry(int attempt) throws InterruptedException {
        Thread.sleep(400L * attempt);
    }

    private String compactPrompt(String prompt) {
        return prompt + """

            【紧凑重试】上次输出疑似达到长度上限。只返回必要 JSON，最多 4 项；每个自然语言字段不超过 80 个汉字；
            数组最多 4 个值；不要返回解释、内部 ID、文件清单或可由来源编号恢复的证据字段。
            """;
    }

    private int reasoningLength(JsonNode message) {
        if (message == null || !message.isObject()) return 0;
        int length = 0;
        for (String field : List.of("reasoning_content", "reasoning", "analysis")) {
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

    private long elapsedMs(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000);
    }

    public record StructuredModelResponse(
        String rawContent,
        ModelOutputAdapter.ParsedOutput parsed,
        ModelCallDiagnostics diagnostics
    ) {
        public StructuredModelResponse(String rawContent, ModelOutputAdapter.ParsedOutput parsed) {
            this(rawContent, parsed, ModelCallDiagnostics.unknown(parsed));
        }

        StructuredModelResponse withCompactRetry(boolean attempted, boolean succeeded) {
            return new StructuredModelResponse(rawContent, parsed, diagnostics.withCompactRetry(attempted, succeeded));
        }
    }

    public record ModelCallDiagnostics(
        String providerName,
        String modelName,
        String finishReason,
        int promptTokens,
        int completionTokens,
        int totalTokens,
        int providerMaxTokens,
        int taskPolicyMaxTokens,
        int effectiveMaxTokens,
        double providerTemperature,
        double effectiveTemperature,
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
        int requestCount
    ) {
        static ModelCallDiagnostics unknown(ModelOutputAdapter.ParsedOutput parsed) {
            return new ModelCallDiagnostics(
                "", "", "", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                true, true, parsed.partial(), false, false, 0, parsed.repaired(), parsed.partial(), parsed.recoveredItems(),
                "UNAVAILABLE", false, 0, 0
            );
        }

        ModelCallDiagnostics withParsed(boolean repaired, boolean partial, int recovered) {
            return new ModelCallDiagnostics(
                providerName, modelName, finishReason, promptTokens, completionTokens, totalTokens,
                providerMaxTokens, taskPolicyMaxTokens, effectiveMaxTokens, providerTemperature, effectiveTemperature,
                timeoutSeconds, latencyMs, requestSucceeded, contentPresent, truncated || partial,
                compactRetryAttempted, compactRetrySucceeded, transportRetryCount, repaired, partial, recovered,
                usageSource, reasoningPresent, reasoningLength, requestCount
            );
        }

        ModelCallDiagnostics withCompactRetry(boolean attempted, boolean succeeded) {
            return new ModelCallDiagnostics(
                providerName, modelName, finishReason, promptTokens, completionTokens, totalTokens,
                providerMaxTokens, taskPolicyMaxTokens, effectiveMaxTokens, providerTemperature, effectiveTemperature,
                timeoutSeconds, latencyMs, requestSucceeded, contentPresent, truncated || attempted,
                attempted, succeeded, transportRetryCount, jsonRepaired, partialResult, recoveredItems,
                usageSource, reasoningPresent, reasoningLength, requestCount + (attempted ? 1 : 0)
            );
        }
    }

    public static class ModelResponseFormatException extends IOException {
        private final ModelCallDiagnostics diagnostics;

        public ModelResponseFormatException(String message, Throwable cause) {
            this(message, cause, null);
        }

        public ModelResponseFormatException(String message, Throwable cause, ModelCallDiagnostics diagnostics) {
            super(message, cause);
            this.diagnostics = diagnostics;
        }

        public ModelCallDiagnostics diagnostics() {
            return diagnostics;
        }
    }

    public static final class ModelOutputTruncatedException extends ModelResponseFormatException {
        public ModelOutputTruncatedException(String message, Throwable cause, ModelCallDiagnostics diagnostics) {
            super(message, cause, diagnostics);
        }
    }

    public static final class ModelEmptyContentException extends ModelResponseFormatException {
        public ModelEmptyContentException(String message, ModelCallDiagnostics diagnostics) {
            super(message, null, diagnostics);
        }
    }
}
