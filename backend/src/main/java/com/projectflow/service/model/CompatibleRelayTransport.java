package com.projectflow.service.model;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.entity.AiProvider;
import com.projectflow.entity.AiProviderAuthMode;
import com.projectflow.service.AiProviderHeaderPolicy;
import com.projectflow.entity.ModelProtocol;
import com.projectflow.service.AiProviderUrlGuard;

/**
 * Narrow compatibility path for relays whose authentication cannot be expressed by the official SDKs.
 * Standard OpenAI and Anthropic authentication remains on their official SDK transports.
 */
@Component
public class CompatibleRelayTransport {
    private static final List<String> REASONING_FIELDS = List.of("reasoning_content", "reasoning", "analysis");

    private final ObjectMapper objectMapper;
    private final AiProviderUrlGuard urlGuard;

    public CompatibleRelayTransport(ObjectMapper objectMapper, AiProviderUrlGuard urlGuard) {
        this.objectMapper = objectMapper;
        this.urlGuard = urlGuard;
    }

    public boolean isRequired(AiProvider provider) {
        return switch (provider.getAuthMode()) {
            case API_KEY_HEADER, QUERY_API_KEY, NONE -> true;
            default -> false;
        };
    }

    public CanonicalModelResponse execute(CanonicalModelRequest request) throws IOException {
        AiProvider provider = request.provider();
        URI endpoint = urlGuard.endpointUri(provider.getBaseUrl(), provider.getProtocol(), provider.getEndpointOverride());
        HttpRequest.Builder builder = HttpRequest.newBuilder(authenticatedUri(endpoint, request))
            .timeout(request.requestTimeout())
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload(request)), StandardCharsets.UTF_8));
        applyHeaders(builder, request);

        HttpResponse<String> response;
        try {
            response = HttpClient.newBuilder()
                .connectTimeout(request.connectionTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build()
                .send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Compatible relay request interrupted", exception);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ModelProtocolHttpException(response.statusCode(), null);
        }
        JsonNode root = objectMapper.readTree(response.body());
        return switch (provider.getProtocol()) {
            case OPENAI_RESPONSES -> parseResponses(root, response);
            case OPENAI_CHAT_COMPLETIONS -> parseChat(root, response);
            case ANTHROPIC_MESSAGES -> parseAnthropic(root, response);
        };
    }

    private Map<String, Object> payload(CanonicalModelRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", request.provider().getModelName());
        switch (request.provider().getProtocol()) {
            case OPENAI_RESPONSES -> {
                body.put("instructions", request.systemPrompt());
                body.put("input", request.userPrompt());
                body.put("max_output_tokens", request.maxOutputTokens());
                if (request.jsonMode()) body.put("text", Map.of("format", Map.of("type", "json_object")));
                if (request.reasoningEffort() != null) {
                    body.put("reasoning", Map.of("effort", request.reasoningEffort()));
                }
            }
            case OPENAI_CHAT_COMPLETIONS -> {
                body.put("messages", List.of(
                    Map.of("role", "system", "content", request.systemPrompt()),
                    Map.of("role", "user", "content", request.userPrompt())
                ));
                body.put("max_tokens", request.maxOutputTokens());
                if (request.jsonMode()) body.put("response_format", Map.of("type", "json_object"));
                if (request.reasoningEffort() != null) {
                    body.put("reasoning_effort", request.reasoningEffort());
                }
            }
            case ANTHROPIC_MESSAGES -> {
                body.put("system", request.systemPrompt());
                body.put("messages", List.of(Map.of("role", "user", "content", request.userPrompt())));
                body.put("max_tokens", request.maxOutputTokens());
                if (request.reasoningEffort() != null) {
                    body.put("thinking", Map.of(
                        "type", "enabled",
                        "budget_tokens", AnthropicMessagesAdapter.reasoningBudget(
                            request.reasoningEffort(), request.maxOutputTokens()
                        )
                    ));
                }
            }
        }
        if (request.temperature() != null
            && !(request.provider().getProtocol() == ModelProtocol.ANTHROPIC_MESSAGES
                && request.reasoningEffort() != null)) {
            body.put("temperature", request.temperature());
        }
        return body;
    }

    private URI authenticatedUri(URI endpoint, CanonicalModelRequest request) throws IOException {
        AiProvider provider = request.provider();
        if (provider.getAuthMode() != AiProviderAuthMode.QUERY_API_KEY) return endpoint;
        String name = AiProviderHeaderPolicy.requireCredentialQueryName(
            provider.getQueryKeyName() == null ? "api_key" : provider.getQueryKeyName()
        );
        String separator = endpoint.getRawQuery() == null ? "?" : "&";
        return URI.create(endpoint.toASCIIString() + separator + encode(name) + "=" + encode(requiredCredential(request)));
    }

    private void applyHeaders(HttpRequest.Builder builder, CanonicalModelRequest request) throws IOException {
        AiProvider provider = request.provider();
        if (provider.getAuthMode() == AiProviderAuthMode.API_KEY_HEADER) {
            builder.header(AiProviderHeaderPolicy.requireCredentialHeaderName(
                provider.getAuthHeaderName() == null ? "x-api-key" : provider.getAuthHeaderName()
            ), requiredCredential(request));
        }
        AiProviderHeaderPolicy.requireSafe(provider.getSafeHeaders()).forEach(builder::header);
    }

    private CanonicalModelResponse parseChat(JsonNode root, HttpResponse<String> response) {
        JsonNode choice = root.path("choices").path(0);
        JsonNode message = choice.path("message");
        String reason = text(choice, "finish_reason", "stop");
        int reasoningLength = REASONING_FIELDS.stream().map(message::path).filter(node -> !node.isMissingNode() && !node.isNull())
            .mapToInt(node -> node.asText().length()).sum();
        return canonical(
            text(message, "content", ""), reason, normalizeChat(reason), usage(root.path("usage"), "prompt_tokens", "completion_tokens", "total_tokens"),
            text(root, "id", requestId(response)), reasoningLength
        );
    }

    private CanonicalModelResponse parseResponses(JsonNode root, HttpResponse<String> response) {
        StringBuilder content = new StringBuilder();
        boolean refused = false;
        for (JsonNode item : root.path("output")) {
            for (JsonNode part : item.path("content")) {
                String type = text(part, "type", "");
                if ("output_text".equals(type)) content.append(text(part, "text", ""));
                if ("refusal".equals(type)) refused = true;
            }
        }
        JsonNode usage = root.path("usage");
        int input = integer(usage, "input_tokens");
        int output = integer(usage, "output_tokens");
        int total = integer(usage, "total_tokens");
        int reasoning = integer(usage.path("output_tokens_details"), "reasoning_tokens");
        String reason = root.path("incomplete_details").path("reason").asText(text(root, "status", "unknown"));
        CanonicalModelUsage canonicalUsage = usage.isObject()
            ? new CanonicalModelUsage(input, output, total == 0 ? input + output : total, reasoning, "ACTUAL")
            : CanonicalModelUsage.unavailable();
        return canonical(content.toString(), reason, refused ? NormalizedFinishReason.REFUSAL : normalizeResponses(reason), canonicalUsage,
            text(root, "id", requestId(response)), reasoning * 4);
    }

    private CanonicalModelResponse parseAnthropic(JsonNode root, HttpResponse<String> response) {
        StringBuilder content = new StringBuilder();
        int reasoningLength = 0;
        boolean toolUse = false;
        for (JsonNode block : root.path("content")) {
            String type = text(block, "type", "");
            if ("text".equals(type)) content.append(text(block, "text", ""));
            if ("thinking".equals(type)) reasoningLength += text(block, "thinking", "").length();
            toolUse |= type.endsWith("tool_use");
        }
        String reason = text(root, "stop_reason", "unknown");
        JsonNode usage = root.path("usage");
        int input = integer(usage, "input_tokens");
        int output = integer(usage, "output_tokens");
        CanonicalModelUsage canonicalUsage = usage.isObject()
            ? new CanonicalModelUsage(input, output, input + output, 0, "ACTUAL")
            : CanonicalModelUsage.unavailable();
        return canonical(content.toString(), reason, normalizeAnthropic(reason, toolUse), canonicalUsage,
            text(root, "id", requestId(response)), reasoningLength);
    }

    private CanonicalModelResponse canonical(
        String content, String providerReason, NormalizedFinishReason reason, CanonicalModelUsage usage, String requestId, int reasoningLength
    ) {
        return new CanonicalModelResponse(content, providerReason, reason, usage, requestId, reasoningLength > 0, reasoningLength);
    }

    private CanonicalModelUsage usage(JsonNode usage, String inputField, String outputField, String totalField) {
        if (!usage.isObject()) return CanonicalModelUsage.unavailable();
        int input = integer(usage, inputField);
        int output = integer(usage, outputField);
        int total = integer(usage, totalField);
        return new CanonicalModelUsage(input, output, total == 0 ? input + output : total, 0, "ACTUAL");
    }

    private NormalizedFinishReason normalizeChat(String reason) {
        return switch (reason.toLowerCase()) {
            case "stop" -> NormalizedFinishReason.COMPLETE;
            case "length" -> NormalizedFinishReason.OUTPUT_LIMIT;
            case "content_filter" -> NormalizedFinishReason.CONTENT_FILTERED;
            case "tool_calls", "function_call" -> NormalizedFinishReason.TOOL_USE;
            default -> NormalizedFinishReason.UNKNOWN;
        };
    }

    private NormalizedFinishReason normalizeResponses(String reason) {
        return switch (reason.toLowerCase()) {
            case "completed" -> NormalizedFinishReason.COMPLETE;
            case "max_output_tokens" -> NormalizedFinishReason.OUTPUT_LIMIT;
            case "content_filter" -> NormalizedFinishReason.CONTENT_FILTERED;
            case "failed", "cancelled" -> NormalizedFinishReason.ERROR;
            case "incomplete", "in_progress", "queued" -> NormalizedFinishReason.INCOMPLETE;
            default -> NormalizedFinishReason.UNKNOWN;
        };
    }

    private NormalizedFinishReason normalizeAnthropic(String reason, boolean toolUse) {
        if (toolUse || "tool_use".equalsIgnoreCase(reason)) return NormalizedFinishReason.TOOL_USE;
        return switch (reason.toLowerCase()) {
            case "end_turn", "stop_sequence" -> NormalizedFinishReason.COMPLETE;
            case "max_tokens" -> NormalizedFinishReason.OUTPUT_LIMIT;
            case "refusal" -> NormalizedFinishReason.REFUSAL;
            default -> NormalizedFinishReason.UNKNOWN;
        };
    }

    private String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asText() : fallback;
    }

    private int integer(JsonNode node, String field) {
        return node.path(field).canConvertToInt() ? node.path(field).intValue() : 0;
    }

    private String requestId(HttpResponse<String> response) {
        return response.headers().firstValue("x-request-id").orElse("");
    }

    private String requiredCredential(CanonicalModelRequest request) throws IOException {
        String credential = request.credential();
        if (credential == null || credential.isBlank()) {
            throw new IOException("Provider credential unavailable");
        }
        return credential;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

}
