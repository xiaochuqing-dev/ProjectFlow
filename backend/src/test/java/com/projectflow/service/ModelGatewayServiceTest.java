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
import com.projectflow.entity.ModelProtocol;
import com.projectflow.service.model.ModelProtocolAdapterRegistry;
import com.projectflow.service.model.OpenAiChatCompletionsAdapter;
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
    void emptyTruncatedContentRaisesBudgetForReasoningRecovery() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger compactMaxTokens = new AtomicInteger();
        HttpServer server = startEmptyTruncatedServer(calls, compactMaxTokens);
        try {
            AiProvider provider = provider("http://127.0.0.1:" + server.getAddress().getPort());
            var response = gateway.callStructured(provider, "返回结构化结果", 4_000);

            assertThat(calls.get()).isEqualTo(2);
            assertThat(compactMaxTokens.get()).isEqualTo(8_000);
            assertThat(response.diagnostics().compactRetryAttempted()).isTrue();
            assertThat(response.diagnostics().compactRetrySucceeded()).isTrue();
            assertThat(response.diagnostics().retryType()).isEqualTo("EMPTY_AFTER_REASONING_RETRY");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void maxReasoningRecoveryRequiresASeparateVisibleJsonResultWithinTwoRequests() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        java.util.concurrent.atomic.AtomicReference<JsonNode> recoveryRequest = new java.util.concurrent.atomic.AtomicReference<>();
        HttpServer server = startMaxReasoningRecoveryServer(calls, recoveryRequest, false);
        try {
            AiProvider provider = flashProvider("http://127.0.0.1:" + server.getAddress().getPort());
            ModelGatewayService maxGateway = maxReasoningGateway();

            var response = maxGateway.callStructured(
                provider,
                "请根据有界证据生成项目理解 JSON",
                ModelTaskType.PROJECT_UNDERSTANDING_SNAPSHOT
            );

            assertThat(calls.get()).isEqualTo(2);
            assertThat(recoveryRequest.get().path("reasoning_effort").asText()).isEqualTo("max");
            assertThat(recoveryRequest.get().path("max_tokens").asInt()).isEqualTo(65_536);
            assertThat(recoveryRequest.get().at("/messages/0/content").asText())
                .contains("充分推理", "结束 reasoning", "可见 content", "不得在 content 为空时结束");
            assertThat(recoveryRequest.get().at("/messages/1/content").asText())
                .contains("同一份有界输入", "完整目标 JSON");
            assertThat(response.diagnostics().reasoningEffort()).isEqualTo("max");
            assertThat(response.diagnostics().retryType()).isEqualTo("EMPTY_AFTER_REASONING_RETRY");
            assertThat(response.diagnostics().requestCount()).isEqualTo(2);
            assertThat(response.diagnostics().compactRetrySucceeded()).isTrue();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void maxReasoningRecoveryStopsAfterTheUniqueSecondReasoningOnlyResponse() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        java.util.concurrent.atomic.AtomicReference<JsonNode> recoveryRequest = new java.util.concurrent.atomic.AtomicReference<>();
        HttpServer server = startMaxReasoningRecoveryServer(calls, recoveryRequest, true);
        try {
            AiProvider provider = flashProvider("http://127.0.0.1:" + server.getAddress().getPort());

            assertThatThrownBy(() -> maxReasoningGateway().callStructured(
                provider,
                "请根据有界证据生成项目理解 JSON",
                ModelTaskType.PROJECT_UNDERSTANDING_SNAPSHOT
            )).isInstanceOf(ModelGatewayService.ModelOutputTruncatedException.class)
                .satisfies(exception -> {
                    var diagnostics = ((ModelGatewayService.ModelOutputTruncatedException) exception).diagnostics();
                    assertThat(diagnostics.requestCount()).isEqualTo(2);
                    assertThat(diagnostics.retryType()).isEqualTo("EMPTY_AFTER_REASONING_RETRY");
                    assertThat(diagnostics.failureCode()).isEqualTo("REASONING_EXHAUSTED_OUTPUT");
                    assertThat(diagnostics.reasoningEffort()).isEqualTo("max");
                    assertThat(diagnostics.compactRetrySucceeded()).isFalse();
                });
            assertThat(calls.get()).isEqualTo(2);
            assertThat(recoveryRequest.get().path("reasoning_effort").asText()).isEqualTo("max");
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

    @Test
    void failedRetryRetainsPreviouslyValidatedPartialScout() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = startPartialScoutRecoveryServer(calls);
        try {
            AiProvider provider = provider("http://127.0.0.1:" + server.getAddress().getPort());
            var response = gateway.callStructured(
                provider,
                "分析项目",
                ModelTaskType.PROJECT_UNDERSTANDING_SNAPSHOT
            );

            assertThat(calls.get()).isEqualTo(2);
            assertThat(response.parsed().partial()).isTrue();
            assertThat(response.diagnostics().compactRetryAttempted()).isTrue();
            assertThat(response.diagnostics().compactRetrySucceeded()).isFalse();
            assertThat(response.diagnostics().failureCode()).isEqualTo("PARTIAL_SCOUT_RETAINED");
            assertThat(response.diagnostics().requestCount()).isEqualTo(2);
            assertThat(ModelTaskType.PROJECT_UNDERSTANDING_SNAPSHOT.schemaMatches(
                response.parsed().root(),
                new ModelOutputAdapter(objectMapper)
            )).isTrue();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void cancellationStopsRecoveryRequestAfterFirstResponse() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        java.util.concurrent.atomic.AtomicBoolean cancelled = new java.util.concurrent.atomic.AtomicBoolean();
        HttpServer server = startCancellingServer(calls, cancelled);
        try (ModelCancellationContext.Scope ignored = ModelCancellationContext.bind(cancelled::get)) {
            AiProvider provider = provider("http://127.0.0.1:" + server.getAddress().getPort());
            assertThatThrownBy(() -> gateway.callStructured(provider, "返回结构化结果", ModelTaskType.PROJECT_ANALYSIS))
                .isInstanceOf(java.util.concurrent.CancellationException.class);
            assertThat(calls.get()).isEqualTo(1);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void diagnosticsNamesTransportRetry() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = startTransportRetryServer(calls);
        try {
            AiProvider provider = provider("http://127.0.0.1:" + server.getAddress().getPort());
            var response = gateway.callStructured(provider, "只返回 {\"ok\":true}", ModelTaskType.PROVIDER_CONNECTION_TEST);

            assertThat(calls.get()).isEqualTo(2);
            assertThat(response.diagnostics().transportRetryCount()).isEqualTo(1);
            assertThat(response.diagnostics().retryType()).isEqualTo("TRANSPORT_RETRY");
            assertThat(response.diagnostics().requestCount()).isEqualTo(2);
            assertThat(response.diagnostics().latencyMs()).isGreaterThanOrEqualTo(100);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void legalJsonWithWrongSchemaUsesTargetedRepairRetry() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = startSchemaServer(calls, false);
        try {
            AiProvider provider = provider("http://127.0.0.1:" + server.getAddress().getPort());
            var response = gateway.callStructured(provider, "分析项目", ModelTaskType.PROJECT_ANALYSIS);

            assertThat(calls.get()).isEqualTo(2);
            assertThat(response.diagnostics().retryType()).isEqualTo("SCHEMA_REPAIR_RETRY");
            assertThat(response.diagnostics().schemaMatched()).isTrue();
            assertThat(response.diagnostics().requestCount()).isEqualTo(2);
            assertThat(response.parsed().root().path("summary").asText()).isEqualTo("已修复");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void schemaRepairFailureIsClassifiedSeparately() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = startSchemaServer(calls, true);
        try {
            AiProvider provider = provider("http://127.0.0.1:" + server.getAddress().getPort());
            assertThatThrownBy(() -> gateway.callStructured(provider, "分析项目", ModelTaskType.PROJECT_ANALYSIS))
                .isInstanceOf(ModelGatewayService.ModelSchemaRepairException.class)
                .satisfies(exception -> {
                    var failure = (ModelGatewayService.ModelSchemaRepairException) exception;
                    assertThat(failure.diagnostics().retryType()).isEqualTo("SCHEMA_REPAIR_RETRY");
                    assertThat(ModelFailureClassifier.classifyException(failure)).isEqualTo(ModelFailureClassifier.SCHEMA_REPAIR_FAILED);
                });
            assertThat(calls.get()).isEqualTo(2);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void reasoningCapabilityOmitsUnsupportedRequestParameters() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        java.util.concurrent.atomic.AtomicReference<JsonNode> captured = new java.util.concurrent.atomic.AtomicReference<>();
        HttpServer server = startCaptureServer(calls, captured);
        try {
            AiProvider provider = provider("http://127.0.0.1:" + server.getAddress().getPort());
            provider.update(
                "DeepSeek", provider.getBaseUrl(), "test-key", "deepseek-reasoner", AiProviderType.DEEPSEEK,
                0.8, 32_000, true, List.of("项目分析")
            );
            var response = gateway.callStructured(provider, "x".repeat(12_000), ModelTaskType.PROJECT_CAPABILITY_ANALYSIS);

            assertThat(captured.get().has("temperature")).isFalse();
            assertThat(captured.get().has("response_format")).isFalse();
            assertThat(captured.get().path("max_tokens").asInt()).isGreaterThan(7_000);
            assertThat(response.diagnostics().temperatureSent()).isFalse();
            assertThat(response.diagnostics().capabilityProfile()).isEqualTo("DEEPSEEK_REASONING");
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

    private AiProvider flashProvider(String baseUrl) {
        AiProvider provider = new AiProvider(UUID.randomUUID());
        provider.update(
            "Compatibility Provider", baseUrl, "test-key", "provider-neutral-flash", AiProviderType.DEEPSEEK,
            0.1, 65_536, true, List.of("项目分析")
        );
        provider.configureProtocol(
            ModelProtocol.OPENAI_CHAT_COMPLETIONS,
            null,
            null,
            null,
            null,
            Map.of(),
            600,
            false,
            true,
            false,
            true,
            true
        );
        return provider;
    }

    private ModelGatewayService maxReasoningGateway() {
        AiProviderUrlGuard urlGuard = new AiProviderUrlGuard();
        return new ModelGatewayService(
            objectMapper,
            urlGuard,
            new ModelOutputAdapter(objectMapper),
            new ModelCapabilityRegistry(),
            new ModelRequestPolicy("max"),
            new ModelProtocolAdapterRegistry(List.of(new OpenAiChatCompletionsAdapter(urlGuard))),
            240
        );
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

    private HttpServer startPartialScoutRecoveryServer(AtomicInteger calls) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            int call = calls.incrementAndGet();
            String content = call == 1
                ? """
                    {
                      "semanticScout":{
                        "projectShapeHypotheses":[{
                          "shape":"BACKEND","confidence":"HIGH",
                          "evidenceRefs":["source:manifest"],"reason":"存在服务入口"
                        }],
                        "evidenceSourceAssessments":[{
                          "evidenceId":"source:manifest","semanticRole":"规范来源",
                          "importance":"HIGH","currentness":"CURRENT",
                          "shouldDeepRead":false,"shouldSkip":false,"reason":"定义依赖",
                          "informationGap":"入口未知","affectedDimensions":["SERVICES"],
                          "confidence":"HIGH"
                        }],
                        "applicableDimensions":["SERVICES"],
                        "capabilityDecisions":[],
                        "unknowns":[]
                      },
                      "dynamicProfile":{"summary":"未闭合
                    """
                : "{\"semanticScout\":";
            String body = objectMapper.writeValueAsString(Map.of(
                "choices", List.of(Map.of(
                    "finish_reason", "length",
                    "message", Map.of("content", content)
                )),
                "usage", Map.of(
                    "prompt_tokens", 100,
                    "completion_tokens", 4_000,
                    "total_tokens", 4_100
                )
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

    private HttpServer startMaxReasoningRecoveryServer(
        AtomicInteger calls,
        java.util.concurrent.atomic.AtomicReference<JsonNode> recoveryRequest,
        boolean alwaysReasoningOnly
    ) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            int call = calls.incrementAndGet();
            JsonNode request = objectMapper.readTree(exchange.getRequestBody());
            if (call == 2) recoveryRequest.set(request);
            String body = call == 1 || alwaysReasoningOnly
                ? "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"\",\"reasoning_content\":\"内部推理不得持久化\"}}],\"usage\":{\"prompt_tokens\":100,\"completion_tokens\":4603,\"total_tokens\":4703}}"
                : "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"{\\\"semanticScout\\\":{\\\"projectShapeHypotheses\\\":[],\\\"evidenceSourceAssessments\\\":[],\\\"applicableDimensions\\\":[],\\\"capabilityDecisions\\\":[],\\\"unknowns\\\":[]},\\\"dynamicProfile\\\":{\\\"summary\\\":\\\"已形成可见结果\\\",\\\"sections\\\":[],\\\"limitations\\\":[]},\\\"analysisPlan\\\":{\\\"requestedCapabilityNames\\\":[],\\\"requestedEvidenceIds\\\":[]}}\"}}],\"usage\":{\"prompt_tokens\":100,\"completion_tokens\":200,\"total_tokens\":300}}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return server;
    }

    private HttpServer startSchemaServer(AtomicInteger calls, boolean alwaysWrong) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            int call = calls.incrementAndGet();
            String content = alwaysWrong || call == 1
                ? "{\"unexpected\":[{\"text\":\"有语义但结构错误\"}]}"
                : "{\"summary\":\"已修复\",\"architecture\":\"前后端分层\",\"modules\":[],\"risks\":[],\"importantFiles\":[],\"evidence\":[],\"limitations\":[],\"confidence\":\"HIGH\"}";
            String body = objectMapper.writeValueAsString(Map.of(
                "choices", List.of(Map.of("finish_reason", "stop", "message", Map.of("content", content))),
                "usage", Map.of("prompt_tokens", 100, "completion_tokens", 200, "total_tokens", 300)
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

    private HttpServer startCaptureServer(
        AtomicInteger calls,
        java.util.concurrent.atomic.AtomicReference<JsonNode> captured
    ) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            calls.incrementAndGet();
            captured.set(objectMapper.readTree(exchange.getRequestBody()));
            String content = "{\"capabilities\":[{\"name\":\"可靠模型链路\",\"summary\":\"按能力与任务计算参数\"}]}";
            String body = objectMapper.writeValueAsString(Map.of(
                "choices", List.of(Map.of("finish_reason", "stop", "message", Map.of("content", content))),
                "usage", Map.of("prompt_tokens", 3000, "completion_tokens", 500, "total_tokens", 3500)
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

    private HttpServer startCancellingServer(
        AtomicInteger calls,
        java.util.concurrent.atomic.AtomicBoolean cancelled
    ) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            calls.incrementAndGet();
            String content = "{\"summary\":\"未完成";
            String body = objectMapper.writeValueAsString(Map.of(
                "choices", List.of(Map.of("finish_reason", "length", "message", Map.of("content", content))),
                "usage", Map.of("prompt_tokens", 100, "completion_tokens", 5000, "total_tokens", 5100)
            ));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            cancelled.set(true);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return server;
    }

    private HttpServer startTransportRetryServer(AtomicInteger calls) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            int call = calls.incrementAndGet();
            if (call == 1) {
                java.util.concurrent.locks.LockSupport.parkNanos(120_000_000L);
                exchange.sendResponseHeaders(503, -1);
                exchange.close();
                return;
            }
            String body = objectMapper.writeValueAsString(Map.of(
                "choices", List.of(Map.of("finish_reason", "stop", "message", Map.of("content", "{\"ok\":true}"))),
                "usage", Map.of("prompt_tokens", 20, "completion_tokens", 10, "total_tokens", 30)
            ));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return server;
    }
}
