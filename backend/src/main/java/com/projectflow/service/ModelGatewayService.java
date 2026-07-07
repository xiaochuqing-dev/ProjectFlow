package com.projectflow.service;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.entity.AiProvider;

@Service
public class ModelGatewayService {
    private static final int MAX_MODEL_ATTEMPTS = 2;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(8);
    private static final Duration MODEL_REQUEST_TIMEOUT = Duration.ofSeconds(35);

    private final ObjectMapper objectMapper;
    private final AiProviderUrlGuard aiProviderUrlGuard;
    private final HttpClient httpClient;

    public ModelGatewayService(ObjectMapper objectMapper, AiProviderUrlGuard aiProviderUrlGuard) {
        this.objectMapper = objectMapper;
        this.aiProviderUrlGuard = aiProviderUrlGuard;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();
    }

    public JsonNode callJson(AiProvider provider, String prompt, int outputTokenLimit) throws IOException, InterruptedException {
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
            .timeout(MODEL_REQUEST_TIMEOUT)
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + provider.getApiKey())
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
            .build();
        for (int attempt = 1; attempt <= MAX_MODEL_ATTEMPTS; attempt++) {
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return parseModelJson(response.body());
                }
                if (attempt < MAX_MODEL_ATTEMPTS && isTransientModelStatus(response.statusCode())) {
                    pauseBeforeRetry(attempt);
                    continue;
                }
                throw new NonRetryableModelException("model HTTP " + response.statusCode());
            } catch (HttpTimeoutException exception) {
                if (attempt >= MAX_MODEL_ATTEMPTS) {
                    throw exception;
                }
                pauseBeforeRetry(attempt);
            } catch (IOException exception) {
                if (exception instanceof NonRetryableModelException || attempt >= MAX_MODEL_ATTEMPTS) {
                    throw exception;
                }
                pauseBeforeRetry(attempt);
            }
        }
        throw new IOException("model request failed");
    }

    public String failureMessage(Exception exception) {
        if (exception instanceof HttpTimeoutException) {
            return "模型请求在 " + MODEL_REQUEST_TIMEOUT.toSeconds() + " 秒内未完成。";
        }
        String message = exception.getMessage();
        return message == null || message.isBlank()
            ? "失败类型：" + exception.getClass().getSimpleName()
            : "原因：" + truncate(message, 180);
    }

    private JsonNode parseModelJson(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        String content = root.at("/choices/0/message/content").asText("");
        if (content.isBlank()) {
            throw new IOException("empty model content");
        }
        return objectMapper.readTree(extractJsonObject(content));
    }

    private boolean isTransientModelStatus(int statusCode) {
        return statusCode == 429 || statusCode == 500 || statusCode == 502 || statusCode == 503 || statusCode == 504;
    }

    private void pauseBeforeRetry(int attempt) throws InterruptedException {
        Thread.sleep(400L * attempt);
    }

    private String extractJsonObject(String content) throws IOException {
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IOException("model content is not JSON");
        }
        return content.substring(start, end + 1);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "...";
    }

    private static final class NonRetryableModelException extends IOException {
        private NonRetryableModelException(String message) {
            super(message);
        }
    }
}
