package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.entity.AiProvider;
import com.projectflow.entity.AiProviderType;
import com.sun.net.httpserver.HttpServer;

class ModelGatewayServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ModelGatewayService gateway = new ModelGatewayService(
        objectMapper, new AiProviderUrlGuard(), new ModelOutputAdapter(objectMapper), 240
    );

    @Test
    void exposesFinishReasonUsageAndEffectiveParameters() throws Exception {
        String responseBody = objectMapper.writeValueAsString(Map.of(
            "choices", List.of(Map.of("finish_reason", "length", "message", Map.of("content", "{\"items\":[]}"))),
            "usage", Map.of("prompt_tokens", 1200, "completion_tokens", 3900, "total_tokens", 5100)
        ));
        var response = gateway.parseModelResponse(responseBody, provider(), 4_000, 4_000, 0.3, 1250, 1, false);

        var diagnostics = response.diagnostics();
        assertThat(diagnostics.finishReason()).isEqualTo("length");
        assertThat(diagnostics.promptTokens()).isEqualTo(1200);
        assertThat(diagnostics.completionTokens()).isEqualTo(3900);
        assertThat(diagnostics.providerMaxTokens()).isEqualTo(8_000);
        assertThat(diagnostics.taskPolicyMaxTokens()).isEqualTo(4_000);
        assertThat(diagnostics.effectiveMaxTokens()).isEqualTo(4_000);
        assertThat(diagnostics.providerTemperature()).isEqualTo(0.8);
        assertThat(diagnostics.effectiveTemperature()).isEqualTo(0.3);
        assertThat(diagnostics.timeoutSeconds()).isEqualTo(240);
        assertThat(diagnostics.truncated()).isTrue();
    }

    @Test
    void detectsNearLimitWhenFinishReasonIsMissing() throws Exception {
        String responseBody = objectMapper.writeValueAsString(Map.of(
            "choices", List.of(Map.of("message", Map.of("content", "{\"items\":[]}"))),
            "usage", Map.of("completion_tokens", 3700)
        ));
        var response = gateway.parseModelResponse(responseBody, provider(), 4_000, 4_000, 0.3, 20, 0, false);

        assertThat(response.diagnostics().truncated()).isTrue();
    }

    @Test
    void distinguishesEmptyContentFromJsonSyntaxFailure() {
        assertThatThrownBy(() -> gateway.parseModelResponse(
            "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"\"}}]}",
            provider(), 4_000, 4_000, 0.3, 10, 0, false
        )).isInstanceOf(ModelGatewayService.ModelEmptyContentException.class);
    }

    @Test
    void classifiesEmptyLengthWithReasoningAsExhaustedOutput() {
        assertThatThrownBy(() -> gateway.parseModelResponse(
            "{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":\"\",\"reasoning_content\":\"内部推理不得暴露\"}}],\"usage\":{\"completion_tokens\":4000}}",
            provider(), 4_000, 4_000, 0.3, 10, 0, false
        )).isInstanceOf(ModelGatewayService.ModelOutputTruncatedException.class)
            .satisfies(exception -> {
                var diagnostics = ((ModelGatewayService.ModelOutputTruncatedException) exception).diagnostics();
                assertThat(diagnostics.reasoningPresent()).isTrue();
                assertThat(diagnostics.reasoningLength()).isPositive();
                assertThat(diagnostics.usageSource()).isEqualTo("ACTUAL");
            });
    }

    @Test
    void emptyTruncatedContentTriggersLowerBudgetCompactRetry() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger compactMaxTokens = new AtomicInteger();
        HttpServer server = startEmptyTruncatedServer(calls, compactMaxTokens);
        try {
            AiProvider provider = provider("http://127.0.0.1:" + server.getAddress().getPort());
            var response = gateway.callStructured(provider, "返回结构化结果", 4_000);

            assertThat(calls.get()).isEqualTo(2);
            assertThat(compactMaxTokens.get()).isEqualTo(2_000);
            assertThat(response.diagnostics().compactRetryAttempted()).isTrue();
            assertThat(response.diagnostics().compactRetrySucceeded()).isTrue();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void compactRetryRecoversTruncatedOutput() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = startServer(calls, false);
        try {
            AiProvider provider = provider("http://127.0.0.1:" + server.getAddress().getPort());
            var response = gateway.callStructured(provider, "返回结构化结果", 4_000);

            assertThat(calls.get()).isEqualTo(2);
            assertThat(response.diagnostics().compactRetryAttempted()).isTrue();
            assertThat(response.diagnostics().compactRetrySucceeded()).isTrue();
            assertThat(new ModelOutputAdapter(objectMapper).items(response.parsed().root(), "items")).hasSize(1);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void compactRetryFailureKeepsTruncationClassification() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = startServer(calls, true);
        try {
            AiProvider provider = provider("http://127.0.0.1:" + server.getAddress().getPort());
            assertThatThrownBy(() -> gateway.callStructured(provider, "返回结构化结果", 4_000))
                .isInstanceOf(ModelGatewayService.ModelOutputTruncatedException.class)
                .satisfies(exception -> assertThat(((ModelGatewayService.ModelOutputTruncatedException) exception)
                    .diagnostics().compactRetryAttempted()).isTrue());
            assertThat(calls.get()).isEqualTo(2);
        } finally {
            server.stop(0);
        }
    }

    private AiProvider provider() {
        return provider("https://api.deepseek.com");
    }

    private AiProvider provider(String baseUrl) {
        AiProvider provider = new AiProvider(UUID.randomUUID());
        provider.update(
            "DeepSeek", baseUrl, "test-key", "deepseek-chat", AiProviderType.DEEPSEEK,
            0.8, 8_000, true, List.of("项目分析")
        );
        return provider;
    }

    private HttpServer startServer(AtomicInteger calls, boolean alwaysTruncated) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            int call = calls.incrementAndGet();
            String content = alwaysTruncated || call == 1
                ? "{\"items\":[{\"name\":\"未完成"
                : "{\"items\":[{\"name\":\"恢复成功\"}]}";
            String finishReason = alwaysTruncated || call == 1 ? "length" : "stop";
            int completionTokens = alwaysTruncated || call == 1 ? 4_000 : 300;
            String body = objectMapper.writeValueAsString(Map.of(
                "choices", List.of(Map.of("finish_reason", finishReason, "message", Map.of("content", content))),
                "usage", Map.of("prompt_tokens", 100, "completion_tokens", completionTokens, "total_tokens", 100 + completionTokens)
            ));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return server;
    }

    private HttpServer startEmptyTruncatedServer(AtomicInteger calls, AtomicInteger compactMaxTokens) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            int call = calls.incrementAndGet();
            JsonNode request = objectMapper.readTree(exchange.getRequestBody());
            if (call == 2) compactMaxTokens.set(request.path("max_tokens").asInt());
            String body = call == 1
                ? "{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":\"\",\"reasoning_content\":\"已消耗预算\"}}],\"usage\":{\"completion_tokens\":4000}}"
                : "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"{\\\"items\\\":[{\\\"name\\\":\\\"恢复成功\\\"}]}\"}}],\"usage\":{\"completion_tokens\":200}}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return server;
    }
}
