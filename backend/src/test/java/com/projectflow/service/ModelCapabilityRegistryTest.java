package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.projectflow.entity.AiProvider;
import com.projectflow.entity.AiProviderType;
import com.projectflow.entity.ModelProtocol;

class ModelCapabilityRegistryTest {
    private final ModelCapabilityRegistry registry = new ModelCapabilityRegistry();

    @Test
    void deepSeekChatSupportsTemperatureAndJsonMode() {
        var capabilities = registry.resolve(provider(AiProviderType.DEEPSEEK, "deepseek-chat", 32_000));

        assertThat(capabilities.profile()).isEqualTo("DEEPSEEK_CHAT");
        assertThat(capabilities.supportsTemperature()).isTrue();
        assertThat(capabilities.supportsJsonMode()).isTrue();
        assertThat(capabilities.supportsReasoning()).isFalse();
        assertThat(capabilities.maxOutputTokens()).isEqualTo(32_000);
    }

    @Test
    void reasoningModelOmitsUnsupportedTemperatureAndJsonMode() {
        var capabilities = registry.resolve(provider(AiProviderType.DEEPSEEK, "deepseek-reasoner", 64_000));

        assertThat(capabilities.profile()).isEqualTo("DEEPSEEK_REASONING");
        assertThat(capabilities.supportsTemperature()).isFalse();
        assertThat(capabilities.supportsJsonMode()).isFalse();
        assertThat(capabilities.supportsReasoning()).isTrue();
        assertThat(capabilities.reasoningFieldNames()).contains("reasoning_content");
    }

    @Test
    void unknownCompatibleProviderUsesConservativeStandardProfile() {
        var capabilities = registry.resolve(provider(AiProviderType.OPENAI_COMPATIBLE, "vendor-model", 8_192));

        assertThat(capabilities.profile()).isEqualTo("OPENAI_COMPATIBLE_STANDARD");
        assertThat(capabilities.supportsTemperature()).isTrue();
        assertThat(capabilities.supportsJsonMode()).isFalse();
        assertThat(capabilities.supportsStructuredOutput()).isFalse();
    }

    @Test
    void unknownDeepSeekModelDoesNotAssumeJsonMode() {
        var capabilities = registry.resolve(provider(AiProviderType.DEEPSEEK, "deepseek-v4-pro", 100_000));

        assertThat(capabilities.profile()).isEqualTo("DEEPSEEK_STANDARD");
        assertThat(capabilities.supportsTemperature()).isTrue();
        assertThat(capabilities.supportsJsonMode()).isFalse();
    }

    @Test
    void glmFiveResponsesUsesReasoningAwareBudgetAndOmitsTemperatureByDefault() {
        var capabilities = registry.resolve(provider(AiProviderType.OPENAI, "glm-5.2", 16_000));

        assertThat(capabilities.profile()).isEqualTo("OPENAI_RESPONSES_REASONING");
        assertThat(capabilities.supportsReasoning()).isTrue();
        assertThat(capabilities.supportsTemperature()).isFalse();
        assertThat(capabilities.supportsStructuredOutput()).isTrue();
    }

    @Test
    void explicitMessagesReasoningCapabilityOmitsTemperatureUnlessExplicitlyOverridden() {
        AiProvider provider = provider(AiProviderType.ANTHROPIC, "qwen3.7-plus", 65_536);
        provider.configureProtocol(
            ModelProtocol.ANTHROPIC_MESSAGES, null, null, null, null, Map.of(), 600,
            null, false, false, true, true
        );

        var capabilities = registry.resolve(provider);

        assertThat(capabilities.profile()).isEqualTo("ANTHROPIC_MESSAGES_STANDARD");
        assertThat(capabilities.supportsReasoning()).isTrue();
        assertThat(capabilities.supportsReasoningControl()).isTrue();
        assertThat(capabilities.supportsTemperature()).isFalse();
    }

    private AiProvider provider(AiProviderType type, String model, int maxTokens) {
        AiProvider provider = new AiProvider(UUID.randomUUID());
        provider.update("test", "https://api.deepseek.com", "test-key", model, type, 0.9, maxTokens, true, List.of());
        return provider;
    }
}
