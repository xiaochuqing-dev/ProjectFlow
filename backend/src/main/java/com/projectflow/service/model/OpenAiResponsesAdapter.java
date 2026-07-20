package com.projectflow.service.model;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.openai.client.OpenAIClient;
import com.openai.errors.OpenAIServiceException;
import com.openai.models.ResponseFormatJsonObject;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseTextConfig;
import com.openai.models.responses.ResponseUsage;
import com.projectflow.entity.ModelProtocol;
import com.projectflow.service.AiProviderUrlGuard;

@Component
public class OpenAiResponsesAdapter implements ModelProtocolAdapter {
    private final AiProviderUrlGuard urlGuard;
    private final CompatibleRelayTransport compatibleRelayTransport;

    @Autowired
    public OpenAiResponsesAdapter(AiProviderUrlGuard urlGuard, CompatibleRelayTransport compatibleRelayTransport) {
        this.urlGuard = urlGuard;
        this.compatibleRelayTransport = compatibleRelayTransport;
    }

    /** Compatibility constructor for focused unit tests. */
    public OpenAiResponsesAdapter(AiProviderUrlGuard urlGuard) {
        this(urlGuard, new CompatibleRelayTransport(new ObjectMapper(), urlGuard));
    }

    @Override
    public ModelProtocol protocol() { return ModelProtocol.OPENAI_RESPONSES; }

    @Override
    public CanonicalModelResponse execute(CanonicalModelRequest request) throws IOException {
        if (compatibleRelayTransport.isRequired(request.provider())) return compatibleRelayTransport.execute(request);
        ResponseCreateParams.Builder params = ResponseCreateParams.builder()
            .model(request.provider().getModelName())
            .instructions(request.systemPrompt())
            .input(request.userPrompt())
            .maxOutputTokens(request.maxOutputTokens());
        if (request.temperature() != null) params.temperature(request.temperature());
        if (request.jsonMode()) {
            params.text(ResponseTextConfig.builder().format(ResponseFormatJsonObject.builder().build()).build());
        }
        OpenAIClient client = OpenAiSdkSupport.clientBuilder(request, urlGuard).build();
        try {
            Response response = client.responses().create(params.build());
            StringBuilder content = new StringBuilder();
            boolean refused = false;
            for (var item : response.output()) {
                if (item.message().isEmpty()) continue;
                for (var part : item.message().get().content()) {
                    if (part.outputText().isPresent()) content.append(part.outputText().get().text());
                    refused |= part.refusal().isPresent();
                }
            }
            String providerReason = response.incompleteDetails()
                .flatMap(Response.IncompleteDetails::reason).map(reason -> reason.asString())
                .orElseGet(() -> response.status().map(status -> status.asString()).orElse("unknown"));
            NormalizedFinishReason finish = normalize(providerReason, refused);
            CanonicalModelUsage usage = response.usage().map(OpenAiResponsesAdapter::usage)
                .orElse(CanonicalModelUsage.unavailable());
            return new CanonicalModelResponse(
                content.toString(), providerReason, finish, usage, response._id().asKnown().orElse(""), usage.reasoningTokens() > 0,
                usage.reasoningTokens() * 4
            );
        } catch (OpenAIServiceException exception) {
            throw new ModelProtocolHttpException(exception.statusCode(), exception);
        } catch (RuntimeException exception) {
            throw new IOException("OpenAI Responses SDK request failed", exception);
        } finally {
            client.close();
        }
    }

    private static CanonicalModelUsage usage(ResponseUsage value) {
        return new CanonicalModelUsage(
            Math.toIntExact(value.inputTokens()), Math.toIntExact(value.outputTokens()), Math.toIntExact(value.totalTokens()),
            Math.toIntExact(value.outputTokensDetails().reasoningTokens()), "ACTUAL"
        );
    }

    private NormalizedFinishReason normalize(String reason, boolean refused) {
        if (refused) return NormalizedFinishReason.REFUSAL;
        return switch (reason == null ? "" : reason.toLowerCase()) {
            case "completed" -> NormalizedFinishReason.COMPLETE;
            case "max_output_tokens" -> NormalizedFinishReason.OUTPUT_LIMIT;
            case "content_filter" -> NormalizedFinishReason.CONTENT_FILTERED;
            case "failed", "cancelled" -> NormalizedFinishReason.ERROR;
            case "incomplete", "in_progress", "queued" -> NormalizedFinishReason.INCOMPLETE;
            default -> NormalizedFinishReason.UNKNOWN;
        };
    }
}
