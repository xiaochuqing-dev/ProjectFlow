package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.entity.AiProvider;
import com.projectflow.entity.AiProviderAuthMode;
import com.projectflow.entity.AiProviderType;
import com.projectflow.entity.ModelProtocol;
import com.projectflow.service.model.AnthropicMessagesAdapter;
import com.projectflow.service.model.ModelProtocolAdapterRegistry;
import com.projectflow.service.model.OpenAiChatCompletionsAdapter;
import com.projectflow.service.model.OpenAiResponsesAdapter;

class ModelGatewayCredentialBoundaryTest {
    @Test
    void secureGatewayDoesNotFallbackToEntityPlaintextWhenStoreCannotRead() {
        ObjectMapper mapper = new ObjectMapper();
        AiProviderUrlGuard guard = new AiProviderUrlGuard();
        ModelProtocolAdapterRegistry adapters = new ModelProtocolAdapterRegistry(List.of(
            new OpenAiResponsesAdapter(guard), new OpenAiChatCompletionsAdapter(guard), new AnthropicMessagesAdapter(guard)
        ));
        ModelGatewayService gateway = new ModelGatewayService(
            mapper,
            guard,
            new ModelOutputAdapter(mapper),
            new ModelCapabilityRegistry(),
            new ModelRequestPolicy(),
            adapters,
            new UnavailableProviderCredentialStore(),
            30
        );
        AiProvider provider = new AiProvider(UUID.randomUUID());
        provider.update(
            "Legacy Provider", "https://example.invalid", "legacy-sentinel", "model", AiProviderType.OPENAI,
            0.1, 2048, true, List.of("TEST")
        );
        provider.configureProtocol(
            ModelProtocol.OPENAI_CHAT_COMPLETIONS, null, AiProviderAuthMode.PROTOCOL_DEFAULT,
            null, null, Map.of(), 30, true, true, true, false, false
        );

        assertThatThrownBy(() -> gateway.callStructured(provider, "return JSON", ModelTaskType.PROVIDER_CONNECTION_TEST))
            .isInstanceOf(ModelGatewayService.ModelCredentialException.class)
            .hasMessage("SECRET_STORE_UNAVAILABLE")
            .hasMessageNotContaining("legacy-sentinel");
    }
}
