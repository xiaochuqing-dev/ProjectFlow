package com.projectflow.service;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

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
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(8);
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
            "temperature", Math.min(provider.getTemperature(), 0.3),
            "max_tokens", Math.min(provider.getMaxTokens(), outputTokenLimit)
        );
        HttpRequest request = HttpRequest.newBuilder()
            .uri(aiProviderUrlGuard.chatCompletionsUri(provider.getBaseUrl()))
            .timeout(modelRequestTimeout)
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + provider.getApiKey())
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
            .build();
        for (int attempt = 1; attempt <= MAX_MODEL_ATTEMPTS; attempt++) {
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return parseModelResponse(response.body());
                }
                if (attempt < MAX_MODEL_ATTEMPTS && isTransientModelStatus(response.statusCode())) {
                    pauseBeforeRetry(attempt);
                    continue;
                }
                throw new ModelHttpException(response.statusCode());
            } catch (HttpTimeoutException exception) {
                if (attempt >= MAX_MODEL_ATTEMPTS) {
                    throw exception;
                }
                pauseBeforeRetry(attempt);
            } catch (IOException exception) {
                if (exception instanceof ModelHttpException || exception instanceof ModelResponseFormatException || attempt >= MAX_MODEL_ATTEMPTS) {
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

    private StructuredModelResponse parseModelResponse(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        String content = root.at("/choices/0/message/content").asText("");
        if (content.isBlank()) {
            throw new IOException("empty model content");
        }
        try {
            return new StructuredModelResponse(content, outputAdapter.parse(content));
        } catch (IOException exception) {
            throw new ModelResponseFormatException(exception.getMessage(), exception);
        }
    }

    private boolean isTransientModelStatus(int statusCode) {
        return statusCode == 429 || statusCode == 500 || statusCode == 502 || statusCode == 503 || statusCode == 504;
    }

    private void pauseBeforeRetry(int attempt) throws InterruptedException {
        Thread.sleep(400L * attempt);
    }

    public record StructuredModelResponse(String rawContent, ModelOutputAdapter.ParsedOutput parsed) {
    }

    public static final class ModelResponseFormatException extends IOException {
        public ModelResponseFormatException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
