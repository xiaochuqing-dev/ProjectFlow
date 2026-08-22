package com.projectflow.service.model;

import java.io.IOException;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.openai.client.OpenAIClient;
import com.openai.errors.OpenAIServiceException;
import com.openai.models.ReasoningEffort;
import com.openai.models.ResponseFormatJsonObject;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.completions.CompletionUsage;
import com.projectflow.entity.ModelProtocol;
import com.projectflow.service.AiProviderUrlGuard;

@Component
public class OpenAiChatCompletionsAdapter implements ModelProtocolAdapter {
    private static final List<String> REASONING_FIELDS = List.of("reasoning_content", "reasoning", "analysis");
    private final AiProviderUrlGuard urlGuard;
    private final CompatibleRelayTransport compatibleRelayTransport;

    @Autowired
    public OpenAiChatCompletionsAdapter(AiProviderUrlGuard urlGuard, CompatibleRelayTransport compatibleRelayTransport) {
        this.urlGuard = urlGuard;
        this.compatibleRelayTransport = compatibleRelayTransport;
    }

    /** Compatibility constructor for focused unit tests. */
    public OpenAiChatCompletionsAdapter(AiProviderUrlGuard urlGuard) {
        this(urlGuard, new CompatibleRelayTransport(new ObjectMapper(), urlGuard));
    }

    @Override
    public ModelProtocol protocol() { return ModelProtocol.OPENAI_CHAT_COMPLETIONS; }

    @Override
    public CanonicalModelResponse execute(CanonicalModelRequest request) throws IOException {
        if (compatibleRelayTransport.isRequired(request.provider())) return compatibleRelayTransport.execute(request);
        ChatCompletionCreateParams.Builder params = ChatCompletionCreateParams.builder()
            .model(request.provider().getModelName())
            .addSystemMessage(request.systemPrompt())
            .addUserMessage(request.userPrompt())
            .maxTokens(request.maxOutputTokens());
        if (request.temperature() != null) params.temperature(request.temperature());
        if (request.jsonMode()) params.responseFormat(ResponseFormatJsonObject.builder().build());
        if (request.reasoningEffort() != null) {
            params.reasoningEffort(ReasoningEffort.of(request.reasoningEffort()));
        }
        OpenAIClient client = OpenAiSdkSupport.clientBuilder(request, urlGuard).build();
        try {
            ChatCompletion response = client.chat().completions().create(params.build());
            if (response.choices().isEmpty()) {
                return new CanonicalModelResponse("", "missing_choice", NormalizedFinishReason.UNKNOWN,
                    usage(response.usage().orElse(null)), response._id().asKnown().orElse(""), false, 0);
            }
            ChatCompletion.Choice choice = response.choices().get(0);
            String content = choice.message().content().orElse("");
            int reasoningLength = REASONING_FIELDS.stream()
                .map(choice.message()._additionalProperties()::get)
                .filter(java.util.Objects::nonNull)
                .mapToInt(value -> value.toString().length()).sum();
            String reason = choice._finishReason().asKnown().map(value -> value.asString()).orElse("stop");
            return new CanonicalModelResponse(
                content, reason, normalize(reason), usage(response.usage().orElse(null)), response._id().asKnown().orElse(""),
                reasoningLength > 0, reasoningLength
            );
        } catch (OpenAIServiceException exception) {
            throw new ModelProtocolHttpException(
                exception.statusCode(), exception.type().orElse(""), exception.code().orElse(""),
                exception.param().orElse(""), exception
            );
        } catch (RuntimeException exception) {
            Throwable root = exception;
            while (root.getCause() != null && root.getCause() != root) root = root.getCause();
            throw new IOException(
                "OpenAI Chat Completions SDK request failed: " + exception.getMessage()
                    + " (" + root.getClass().getSimpleName() + ": " + root.getMessage() + ")",
                exception
            );
        } finally {
            client.close();
        }
    }

    private static CanonicalModelUsage usage(CompletionUsage value) {
        if (value == null) return CanonicalModelUsage.unavailable();
        long prompt = value._promptTokens().asKnown().orElse(0L);
        long completion = value._completionTokens().asKnown().orElse(0L);
        long total = value._totalTokens().asKnown().orElse(prompt + completion);
        boolean actual = !value._promptTokens().isMissing() || !value._completionTokens().isMissing() || !value._totalTokens().isMissing();
        return new CanonicalModelUsage(
            Math.toIntExact(prompt), Math.toIntExact(completion), Math.toIntExact(total), 0, actual ? "ACTUAL" : "UNAVAILABLE"
        );
    }

    private NormalizedFinishReason normalize(String reason) {
        return switch (reason == null ? "" : reason.toLowerCase()) {
            case "stop" -> NormalizedFinishReason.COMPLETE;
            case "length" -> NormalizedFinishReason.OUTPUT_LIMIT;
            case "content_filter" -> NormalizedFinishReason.CONTENT_FILTERED;
            case "tool_calls", "function_call" -> NormalizedFinishReason.TOOL_USE;
            default -> NormalizedFinishReason.UNKNOWN;
        };
    }
}
