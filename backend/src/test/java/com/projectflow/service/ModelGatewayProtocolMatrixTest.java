package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.entity.AiProvider;
import com.projectflow.entity.AiProviderAuthMode;
import com.projectflow.entity.AiProviderType;
import com.projectflow.entity.ModelProtocol;
import com.projectflow.service.model.AnthropicMessagesAdapter;
import com.projectflow.service.model.CanonicalModelRequest;
import com.projectflow.service.model.ModelProtocolAdapterRegistry;
import com.projectflow.service.model.NormalizedFinishReason;
import com.projectflow.service.model.OpenAiChatCompletionsAdapter;
import com.projectflow.service.model.OpenAiResponsesAdapter;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

class ModelGatewayProtocolMatrixTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;
    private volatile String modelContent = "{}";
    private volatile boolean outputLimited;
    private volatile boolean limitFirstRequest;
    private volatile boolean invalidSchemaFirstRequest;
    private volatile boolean reasoningEmptyFirstRequest;
    private volatile boolean transientFailureFirstRequest;
    private final AtomicInteger providerRequestCount = new AtomicInteger();
    private final AtomicReference<com.sun.net.httpserver.Headers> capturedHeaders = new AtomicReference<>();
    private final AtomicReference<String> capturedQuery = new AtomicReference<>("");
    private final AtomicReference<com.fasterxml.jackson.databind.JsonNode> capturedBody = new AtomicReference<>();
    private ModelGatewayService gateway;
    private ModelProtocolAdapterRegistry registry;
    private String serverBase;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/responses", exchange -> respond(exchange, responsesBody(shouldLimit())));
        server.createContext("/v1/chat/completions", exchange -> respond(exchange, chatBody(shouldLimit())));
        server.createContext("/v1/messages", exchange -> respond(exchange, anthropicBody(shouldLimit())));
        server.start();
        serverBase = "http://127.0.0.1:" + server.getAddress().getPort();
        AiProviderUrlGuard guard = new AiProviderUrlGuard();
        registry = new ModelProtocolAdapterRegistry(List.of(
            new OpenAiResponsesAdapter(guard), new OpenAiChatCompletionsAdapter(guard), new AnthropicMessagesAdapter(guard)
        ));
        gateway = new ModelGatewayService(
            objectMapper, guard, new ModelOutputAdapter(objectMapper), new ModelCapabilityRegistry(), new ModelRequestPolicy(),
            registry, 30
        );
    }

    @Test
    void normalizesOutputLimitAcrossProtocols() throws Exception {
        outputLimited = true;
        for (ModelProtocol protocol : ModelProtocol.values()) {
            var response = registry.require(protocol).execute(new CanonicalModelRequest(
                provider(protocol), "只返回 JSON", "{}", 256, null, false, Duration.ofSeconds(30)
            ));
            assertThat(response.finishReason()).isEqualTo(NormalizedFinishReason.OUTPUT_LIMIT);
            assertThat(response.usage().source()).isEqualTo("ACTUAL");
        }
    }

    @Test
    void everyProtocolUsesTheSameBoundedTruncationRecoveryAndRejectsASecondIncompleteResult() throws Exception {
        for (ModelProtocol protocol : ModelProtocol.values()) {
            modelContent = ModelTaskType.PROVIDER_CONNECTION_TEST.minimalSchema();
            providerRequestCount.set(0);
            limitFirstRequest = true;
            var recovered = gateway.callStructured(provider(protocol), "最小兼容任务", ModelTaskType.PROVIDER_CONNECTION_TEST);
            assertThat(recovered.diagnostics().retryType()).isEqualTo("TRUNCATION_RETRY");
            assertThat(recovered.diagnostics().requestCount()).isEqualTo(2);
            assertThat(recovered.diagnostics().compactRetrySucceeded()).isTrue();

            providerRequestCount.set(0);
            limitFirstRequest = false;
            outputLimited = true;
            org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> gateway.callStructured(provider(protocol), "最小兼容任务", ModelTaskType.PROVIDER_CONNECTION_TEST)
            ).isInstanceOf(ModelGatewayService.ModelOutputTruncatedException.class)
                .satisfies(failure -> {
                    var truncated = (ModelGatewayService.ModelOutputTruncatedException) failure;
                    assertThat(truncated.diagnostics().requestCount()).isEqualTo(2);
                    assertThat(truncated.diagnostics().compactRetryAttempted()).isTrue();
                    assertThat(truncated.diagnostics().compactRetrySucceeded()).isFalse();
                });
            assertThat(providerRequestCount.get()).isEqualTo(2);
            outputLimited = false;
        }
    }

    @Test
    void everyProtocolUsesTheSameSchemaReasoningAndTransportRecoveryContracts() throws Exception {
        for (ModelProtocol protocol : ModelProtocol.values()) {
            AiProvider provider = provider(protocol);
            modelContent = ModelTaskType.PROVIDER_PROJECTFLOW_COMPATIBILITY_TEST.minimalSchema();

            providerRequestCount.set(0);
            invalidSchemaFirstRequest = true;
            var schemaRecovered = gateway.callStructured(
                provider, "最小 ProjectFlow 任务", ModelTaskType.PROVIDER_PROJECTFLOW_COMPATIBILITY_TEST
            );
            assertThat(schemaRecovered.diagnostics().retryType()).isEqualTo("SCHEMA_REPAIR_RETRY");
            assertThat(schemaRecovered.diagnostics().requestCount()).isEqualTo(2);
            invalidSchemaFirstRequest = false;

            providerRequestCount.set(0);
            reasoningEmptyFirstRequest = true;
            var reasoningRecovered = gateway.callStructured(
                provider, "最小 ProjectFlow 任务", ModelTaskType.PROVIDER_PROJECTFLOW_COMPATIBILITY_TEST
            );
            assertThat(reasoningRecovered.diagnostics().retryType()).isEqualTo("EMPTY_AFTER_REASONING_RETRY");
            assertThat(reasoningRecovered.diagnostics().requestCount()).isEqualTo(2);
            reasoningEmptyFirstRequest = false;

            providerRequestCount.set(0);
            transientFailureFirstRequest = true;
            var transportRecovered = gateway.callStructured(
                provider, "最小 ProjectFlow 任务", ModelTaskType.PROVIDER_PROJECTFLOW_COMPATIBILITY_TEST
            );
            assertThat(transportRecovered.diagnostics().retryType()).isEqualTo("TRANSPORT_RETRY");
            assertThat(transportRecovered.diagnostics().requestCount()).isEqualTo(2);
            transientFailureFirstRequest = false;
        }
    }

    @Test
    void supportsBoundedHeaderQueryAndBearerAuthenticationVariants() throws Exception {
        AiProvider headerProvider = provider(ModelProtocol.OPENAI_CHAT_COMPLETIONS);
        headerProvider.configureProtocol(ModelProtocol.OPENAI_CHAT_COMPLETIONS, null, AiProviderAuthMode.API_KEY_HEADER,
            "X-Relay-Key", null, Map.of("X-ProjectFlow-Client", "compat-test"), 30, true, false, false, false, false);
        registry.require(headerProvider.getProtocol()).execute(request(headerProvider));
        assertThat(capturedHeaders.get().getFirst("X-Relay-Key")).isEqualTo("test-key");
        assertThat(capturedHeaders.get().getFirst("X-ProjectFlow-Client")).isEqualTo("compat-test");
        assertThat(capturedHeaders.get().getFirst("Authorization")).isNull();

        AiProvider queryProvider = provider(ModelProtocol.OPENAI_RESPONSES);
        queryProvider.configureProtocol(ModelProtocol.OPENAI_RESPONSES, null, AiProviderAuthMode.QUERY_API_KEY,
            null, "access_key", Map.of(), 30, true, false, false, false, false);
        registry.require(queryProvider.getProtocol()).execute(request(queryProvider));
        assertThat(capturedQuery.get()).contains("access_key=test-key");
        assertThat(capturedHeaders.get().getFirst("Authorization")).isNull();

        AiProvider bearerProvider = provider(ModelProtocol.ANTHROPIC_MESSAGES);
        bearerProvider.configureProtocol(ModelProtocol.ANTHROPIC_MESSAGES, null, AiProviderAuthMode.BEARER,
            null, null, Map.of(), 30, true, false, false, false, false);
        registry.require(bearerProvider.getProtocol()).execute(request(bearerProvider));
        assertThat(capturedHeaders.get().getFirst("Authorization")).isEqualTo("Bearer test-key");
        assertThat(capturedHeaders.get().getFirst("x-api-key")).isNull();

        AiProvider anthropicHeaderProvider = provider(ModelProtocol.ANTHROPIC_MESSAGES);
        anthropicHeaderProvider.configureProtocol(ModelProtocol.ANTHROPIC_MESSAGES, null, AiProviderAuthMode.API_KEY_HEADER,
            "X-Anthropic-Relay-Key", null, Map.of(), 30, true, false, false, false, false);
        registry.require(anthropicHeaderProvider.getProtocol()).execute(request(anthropicHeaderProvider));
        assertThat(capturedHeaders.get().getFirst("X-Anthropic-Relay-Key")).isEqualTo("test-key");
        assertThat(capturedHeaders.get().getFirst("x-api-key")).isNull();

        AiProvider noAuthProvider = provider(ModelProtocol.ANTHROPIC_MESSAGES);
        noAuthProvider.configureProtocol(ModelProtocol.ANTHROPIC_MESSAGES, null, AiProviderAuthMode.NONE,
            null, null, Map.of(), 30, true, false, false, false, false);
        registry.require(noAuthProvider.getProtocol()).execute(request(noAuthProvider));
        assertThat(capturedHeaders.get().getFirst("Authorization")).isNull();
        assertThat(capturedHeaders.get().getFirst("x-api-key")).isNull();
    }

    @Test
    void reasoningControlIsCapabilityGatedAndUsesProtocolSafeEffort() throws Exception {
        modelContent = ModelTaskType.PROVIDER_CONNECTION_TEST.minimalSchema();
        AiProvider unsupported = provider(ModelProtocol.OPENAI_RESPONSES);
        gateway.callStructured(
            unsupported,
            "最小兼容任务",
            ModelTaskType.PROVIDER_CONNECTION_TEST
        );
        assertThat(capturedBody.get().has("reasoning")).isFalse();

        modelContent = ModelTaskType.PROJECT_UNDERSTANDING_SNAPSHOT.minimalSchema();
        AiProvider controlled = provider(ModelProtocol.OPENAI_RESPONSES);
        controlled.configureProtocol(
            ModelProtocol.OPENAI_RESPONSES,
            null,
            AiProviderAuthMode.PROTOCOL_DEFAULT,
            null,
            null,
            Map.of(),
            30,
            false,
            false,
            true,
            true,
            true
        );
        modelContent = ModelTaskType.PROVIDER_CONNECTION_TEST.minimalSchema();
        providerRequestCount.set(0);
        gateway.callStructured(
            controlled,
            "最小兼容任务",
            ModelTaskType.PROVIDER_CONNECTION_TEST
        );
        assertThat(capturedBody.get().path("reasoning").path("effort").asText())
            .isEqualTo("high");
        assertThat(capturedBody.get().path("max_output_tokens").asInt()).isEqualTo(20_000);

        providerRequestCount.set(0);
        modelContent = ModelTaskType.PROJECT_UNDERSTANDING_SNAPSHOT.minimalSchema();
        gateway.callStructured(
            controlled,
            "最小兼容任务",
            ModelTaskType.PROJECT_UNDERSTANDING_SNAPSHOT
        );
        assertThat(capturedBody.get().path("reasoning").path("effort").asText())
            .isEqualTo("high");

        providerRequestCount.set(0);
        limitFirstRequest = true;
        gateway.callStructured(
            controlled,
            "最小兼容任务",
            ModelTaskType.PROJECT_UNDERSTANDING_SNAPSHOT
        );
        assertThat(capturedBody.get().path("reasoning").path("effort").asText())
            .isEqualTo("high");

        modelContent = ModelTaskType.PROJECT_UNDERSTANDING_SNAPSHOT.minimalSchema();
        AiProvider chatControlled = provider(ModelProtocol.OPENAI_CHAT_COMPLETIONS);
        chatControlled.configureProtocol(
            ModelProtocol.OPENAI_CHAT_COMPLETIONS,
            null,
            AiProviderAuthMode.PROTOCOL_DEFAULT,
            null,
            null,
            Map.of(),
            30,
            false,
            false,
            false,
            true,
            true
        );
        providerRequestCount.set(0);
        gateway.callStructured(
            chatControlled,
            "最小兼容任务",
            ModelTaskType.PROJECT_UNDERSTANDING_SNAPSHOT
        );
        assertThat(capturedBody.get().path("reasoning_effort").asText()).isEqualTo("high");
        assertThat(capturedBody.get().has("temperature")).isFalse();

        ModelGatewayService maxGateway = new ModelGatewayService(
            objectMapper, new AiProviderUrlGuard(), new ModelOutputAdapter(objectMapper),
            new ModelCapabilityRegistry(), new ModelRequestPolicy("max"), registry, 30
        );
        providerRequestCount.set(0);
        var maxResponse = maxGateway.callStructured(
            chatControlled,
            "最小兼容任务",
            ModelTaskType.PROJECT_UNDERSTANDING_SNAPSHOT
        );
        assertThat(capturedBody.get().path("reasoning_effort").asText()).isEqualTo("max");
        assertThat(maxResponse.diagnostics().reasoningEffort()).isEqualTo("max");
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void everyActiveTaskUsesTheSameCanonicalContractAcrossAllProtocols() throws Exception {
        for (ModelProtocol protocol : ModelProtocol.values()) {
            AiProvider provider = provider(protocol);
            for (ModelTaskType task : ModelTaskType.values()) {
                modelContent = task.minimalSchema();
                ModelGatewayService.StructuredModelResponse result = gateway.callStructured(provider, "只按目标结构返回", task);
                assertThat(result.diagnostics().protocol()).isEqualTo(protocol.name());
                assertThat(result.diagnostics().normalizedFinishReason()).isEqualTo("COMPLETE");
                assertThat(result.diagnostics().usageSource()).isEqualTo("ACTUAL");
                assertThat(result.diagnostics().schemaMatched()).as(protocol + " / " + task).isTrue();
            }
        }
    }

    private AiProvider provider(ModelProtocol protocol) {
        AiProvider provider = new AiProvider(UUID.randomUUID());
        AiProviderType type = protocol == ModelProtocol.OPENAI_RESPONSES ? AiProviderType.OPENAI
            : protocol == ModelProtocol.ANTHROPIC_MESSAGES ? AiProviderType.ANTHROPIC : AiProviderType.OPENAI_COMPATIBLE;
        String baseUrl = protocol == ModelProtocol.ANTHROPIC_MESSAGES ? serverBase : serverBase + "/v1";
        provider.update("matrix", baseUrl, "test-key", "test-model", type, 0.1, 20_000, true, List.of("TEST"));
        provider.configureProtocol(protocol, null, AiProviderAuthMode.PROTOCOL_DEFAULT, null, null, Map.of(), 30,
            true, protocol == ModelProtocol.OPENAI_CHAT_COMPLETIONS, protocol == ModelProtocol.OPENAI_RESPONSES,
            false, false);
        return provider;
    }

    private void respond(HttpExchange exchange, String body) throws IOException {
        capturedHeaders.set(exchange.getRequestHeaders());
        capturedQuery.set(exchange.getRequestURI().getRawQuery() == null ? "" : exchange.getRequestURI().getRawQuery());
        capturedBody.set(objectMapper.readTree(exchange.getRequestBody().readAllBytes()));
        if (transientFailureFirstRequest && providerRequestCount.get() == 1) {
            byte[] error = "{\"error\":{\"type\":\"overloaded_error\",\"message\":\"busy\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(429, error.length);
            exchange.getResponseBody().write(error);
            exchange.close();
            return;
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private CanonicalModelRequest request(AiProvider provider) {
        return new CanonicalModelRequest(provider, "只返回 JSON", "{}", 256, null, false, Duration.ofSeconds(30));
    }

    private boolean shouldLimit() {
        int request = providerRequestCount.incrementAndGet();
        return outputLimited || (limitFirstRequest && request == 1);
    }

    private String chatBody(boolean limited) throws IOException {
        Map<String, Object> message = new java.util.LinkedHashMap<>();
        message.put("role", "assistant");
        message.put("content", contentForRequest());
        if (reasoningForRequest()) message.put("reasoning_content", "private reasoning metadata");
        return objectMapper.writeValueAsString(Map.of(
            "id", "chatcmpl_test", "object", "chat.completion", "created", 1, "model", "test-model",
            "choices", List.of(Map.of("index", 0, "finish_reason", limited ? "length" : "stop", "message", message)),
            "usage", Map.of("prompt_tokens", 10, "completion_tokens", 20, "total_tokens", 30)
        ));
    }

    private String anthropicBody(boolean limited) throws IOException {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("id", "msg_test");
        body.put("type", "message");
        body.put("role", "assistant");
        body.put("model", "test-model");
        List<Map<String, String>> blocks = new java.util.ArrayList<>();
        if (reasoningForRequest()) blocks.add(Map.of("type", "thinking", "thinking", "private reasoning metadata", "signature", "sig"));
        blocks.add(Map.of("type", "text", "text", contentForRequest()));
        body.put("content", blocks);
        body.put("stop_reason", limited ? "max_tokens" : "end_turn");
        body.put("stop_sequence", null);
        body.put("usage", Map.of("input_tokens", 10, "output_tokens", 20));
        return objectMapper.writeValueAsString(body);
    }

    private String responsesBody(boolean limited) throws IOException {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("id", "resp_test");
        body.put("object", "response");
        body.put("created_at", 1.0);
        body.put("model", "test-model");
        body.put("output", List.of(Map.of(
            "type", "message", "id", "msg_test", "status", "completed", "role", "assistant",
            "content", List.of(Map.of("type", "output_text", "text", contentForRequest(), "annotations", List.of()))
        )));
        body.put("parallel_tool_calls", false);
        body.put("temperature", 0.1);
        body.put("tool_choice", "auto");
        body.put("tools", List.of());
        body.put("top_p", 1.0);
        body.put("status", limited ? "incomplete" : "completed");
        if (limited) body.put("incomplete_details", Map.of("reason", "max_output_tokens"));
        body.put("usage", Map.of(
            "input_tokens", 10, "input_tokens_details", Map.of("cached_tokens", 0),
            "output_tokens", 20, "output_tokens_details", Map.of("reasoning_tokens", reasoningForRequest() ? 20 : 0), "total_tokens", 30
        ));
        return objectMapper.writeValueAsString(body);
    }

    private String contentForRequest() {
        if (invalidSchemaFirstRequest && providerRequestCount.get() == 1) return "{\"wrong\":true}";
        if (reasoningForRequest()) return "";
        return modelContent;
    }

    private boolean reasoningForRequest() {
        return reasoningEmptyFirstRequest && providerRequestCount.get() == 1;
    }
}
