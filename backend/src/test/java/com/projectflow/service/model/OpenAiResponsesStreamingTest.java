package com.projectflow.service.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.entity.AiProvider;
import com.projectflow.entity.AiProviderType;
import com.projectflow.entity.ModelProtocol;
import com.projectflow.service.AiProviderUrlGuard;
import com.sun.net.httpserver.HttpServer;

class OpenAiResponsesStreamingTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void longReasoningUsesStreamingAndAcceptsOnlyTheCompletedEnvelope() throws Exception {
        AtomicReference<JsonNode> sent = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/responses", exchange -> {
            sent.set(mapper.readTree(exchange.getRequestBody()));
            String body = "data: {\"type\":\"response.output_text.delta\",\"delta\":\"partial unvalidated text\"}\n\n"
                + "data: {\"type\":\"response.completed\",\"response\":{\"id\":\"resp_test\",\"object\":\"response\",\"created_at\":1,"
                + "\"status\":\"completed\",\"model\":\"synthetic-reasoning\",\"output\":[{\"type\":\"message\",\"id\":\"msg_test\",\"role\":\"assistant\",\"status\":\"completed\","
                + "\"content\":[{\"type\":\"output_text\",\"text\":\"{\\\"ok\\\":true}\",\"annotations\":[]}]}],"
                + "\"usage\":{\"input_tokens\":11,\"output_tokens\":7,\"total_tokens\":18,\"output_tokens_details\":{\"reasoning_tokens\":3}}}}\n\n";
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write(body.getBytes(StandardCharsets.UTF_8));
            exchange.close();
        });
        server.start();
        try {
            var response = new OpenAiResponsesAdapter(new AiProviderUrlGuard()).execute(request(server));
            assertThat(sent.get().path("stream").asBoolean()).isTrue();
            assertThat(sent.get().path("store").asBoolean(true)).isFalse();
            assertThat(sent.get().path("reasoning").path("effort").asText()).isEqualTo("xhigh");
            assertThat(response.content()).isEqualTo("{\"ok\":true}");
            assertThat(response.finishReason()).isEqualTo(NormalizedFinishReason.COMPLETE);
            assertThat(response.usage().totalTokens()).isEqualTo(18);
        } finally { server.stop(0); }
    }

    @Test
    void closedStreamWithoutTerminalEnvelopeCannotBecomeSuccessfulPartialJson() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/responses", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write("data: {\"type\":\"response.output_text.delta\",\"delta\":\"{}\"}\n\n".getBytes(StandardCharsets.UTF_8));
            exchange.close();
        });
        server.start();
        try {
            assertThatThrownBy(() -> new OpenAiResponsesAdapter(new AiProviderUrlGuard()).execute(request(server)))
                .isInstanceOf(java.io.IOException.class);
        } finally { server.stop(0); }
    }

    private CanonicalModelRequest request(HttpServer server) {
        AiProvider provider = new AiProvider(UUID.randomUUID());
        provider.update("synthetic", "http://127.0.0.1:" + server.getAddress().getPort() + "/v1", "test-key",
            "synthetic-reasoning", AiProviderType.OPENAI, 0.0, 1024, true, java.util.List.of());
        return new CanonicalModelRequest(provider, "test-key", "Return JSON", "test", 1024, null, true,
            "xhigh", Duration.ofSeconds(2), Duration.ofSeconds(901));
    }
}
