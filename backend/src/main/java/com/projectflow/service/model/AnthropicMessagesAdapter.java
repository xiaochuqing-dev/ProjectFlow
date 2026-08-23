package com.projectflow.service.model;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.Timeout;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.projectflow.entity.AiProvider;
import com.projectflow.entity.AiProviderAuthMode;
import com.projectflow.entity.ModelProtocol;
import com.projectflow.service.AiProviderUrlGuard;

@Component
public class AnthropicMessagesAdapter implements ModelProtocolAdapter {
    private final AiProviderUrlGuard urlGuard;
    private final CompatibleRelayTransport compatibleRelayTransport;

    @Autowired
    public AnthropicMessagesAdapter(AiProviderUrlGuard urlGuard, CompatibleRelayTransport compatibleRelayTransport) {
        this.urlGuard = urlGuard;
        this.compatibleRelayTransport = compatibleRelayTransport;
    }

    /** Compatibility constructor for focused unit tests. */
    public AnthropicMessagesAdapter(AiProviderUrlGuard urlGuard) {
        this(urlGuard, new CompatibleRelayTransport(new ObjectMapper(), urlGuard));
    }

    @Override
    public ModelProtocol protocol() { return ModelProtocol.ANTHROPIC_MESSAGES; }

    @Override
    public CanonicalModelResponse execute(CanonicalModelRequest request) throws IOException {
        if (compatibleRelayTransport.isRequired(request.provider())) return compatibleRelayTransport.execute(request);
        MessageCreateParams.Builder params = MessageCreateParams.builder()
            .model(request.provider().getModelName())
            .system(request.systemPrompt())
            .addUserMessage(request.userPrompt())
            .maxTokens(request.maxOutputTokens());
        if (request.reasoningEffort() != null) {
            params.enabledThinking(reasoningBudget(request.reasoningEffort(), request.maxOutputTokens()));
        } else if (request.temperature() != null) {
            params.temperature(request.temperature());
        }
        AnthropicClient client = clientBuilder(request).build();
        try {
            Message response = client.messages().create(params.build());
            StringBuilder content = new StringBuilder();
            int reasoningLength = 0;
            boolean toolUse = false;
            for (var block : response.content()) {
                if (block.text().isPresent()) content.append(block.text().get().text());
                if (block.thinking().isPresent()) reasoningLength += block.thinking().get().thinking().length();
                toolUse |= block.toolUse().isPresent() || block.serverToolUse().isPresent();
            }
            String reason = response.stopReason().map(value -> value.asString()).orElse("unknown");
            long input = response.usage().inputTokens();
            long output = response.usage().outputTokens();
            return new CanonicalModelResponse(
                content.toString(), reason, normalize(reason, toolUse),
                new CanonicalModelUsage(Math.toIntExact(input), Math.toIntExact(output), Math.toIntExact(input + output), 0, "ACTUAL"),
                response.id(), reasoningLength > 0, reasoningLength
            );
        } catch (AnthropicServiceException exception) {
            throw new ModelProtocolHttpException(exception.statusCode(), exception);
        } catch (RuntimeException exception) {
            throw new IOException("Anthropic Messages SDK request failed", exception);
        } finally {
            client.close();
        }
    }

    private AnthropicOkHttpClient.Builder clientBuilder(CanonicalModelRequest request) {
        AiProvider provider = request.provider();
        String key = provider.getApiKey() == null || provider.getApiKey().isBlank() ? "projectflow-no-key" : provider.getApiKey();
        AiProviderAuthMode mode = provider.getAuthMode();
        AnthropicOkHttpClient.Builder builder = AnthropicOkHttpClient.builder()
            .baseUrl(urlGuard.sdkBaseUrl(provider.getBaseUrl(), provider.getProtocol(), provider.getEndpointOverride()))
            .timeout(Timeout.builder()
                .connect(request.connectionTimeout())
                .read(request.requestTimeout())
                .write(request.requestTimeout())
                .request(request.requestTimeout())
                .build())
            .maxRetries(0);
        if (mode == AiProviderAuthMode.BEARER) {
            builder.authToken(key);
        } else builder.apiKey(key);
        provider.getSafeHeaders().forEach(builder::putHeader);
        return builder;
    }

    private NormalizedFinishReason normalize(String reason, boolean toolUse) {
        if (toolUse || "tool_use".equalsIgnoreCase(reason)) return NormalizedFinishReason.TOOL_USE;
        return switch (reason == null ? "" : reason.toLowerCase()) {
            case "end_turn", "stop_sequence" -> NormalizedFinishReason.COMPLETE;
            case "max_tokens" -> NormalizedFinishReason.OUTPUT_LIMIT;
            case "refusal" -> NormalizedFinishReason.REFUSAL;
            default -> NormalizedFinishReason.UNKNOWN;
        };
    }

    static long reasoningBudget(String effort, int maxOutputTokens) {
        long visibleOutputReserve = 1_024L;
        if (maxOutputTokens <= visibleOutputReserve) {
            throw new IllegalArgumentException("Anthropic Messages thinking requires max output tokens above 1024");
        }
        long requested = switch (effort == null ? "" : effort.toLowerCase(java.util.Locale.ROOT)) {
            case "max" -> 31_999L;
            case "high" -> 16_000L;
            default -> throw new IllegalArgumentException("Anthropic Messages supports high or max thinking effort");
        };
        return Math.min(requested, maxOutputTokens - visibleOutputReserve);
    }
}
