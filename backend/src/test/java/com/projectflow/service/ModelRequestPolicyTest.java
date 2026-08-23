package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.projectflow.entity.AiProvider;
import com.projectflow.entity.AiProviderType;
import com.projectflow.entity.ModelProtocol;

class ModelRequestPolicyTest {
    private final ModelCapabilityRegistry registry = new ModelCapabilityRegistry();
    private final ModelRequestPolicy policy = new ModelRequestPolicy();

    @Test
    void keepsConfiguredTemperatureWithoutGlobalPointThreeCeiling() {
        AiProvider provider = provider("deepseek-chat", 0.9, 32_000);
        var parameters = policy.initial(
            provider, registry.resolve(provider), ModelTaskType.PROJECT_ANALYSIS, "x".repeat(2_000)
        );

        assertThat(parameters.configuredTemperature()).isEqualTo(0.9);
        assertThat(parameters.recommendedTemperature()).isEqualTo(0.2);
        assertThat(parameters.effectiveTemperature()).isEqualTo(0.9);
        assertThat(parameters.temperatureSent()).isTrue();
        assertThat(parameters.temperatureDecision()).contains("不做全局封顶");
    }

    @Test
    void omitsTemperatureForReasoningModel() {
        AiProvider provider = provider("deepseek-reasoner", 0.9, 64_000);
        var parameters = policy.initial(
            provider, registry.resolve(provider), ModelTaskType.PROJECT_CAPABILITY_ANALYSIS, "x".repeat(20_000)
        );

        assertThat(parameters.temperatureSent()).isFalse();
        assertThat(parameters.effectiveTemperature()).isNull();
        assertThat(parameters.taskRequestedMaxTokens()).isGreaterThan(7_000);
    }

    @Test
    void givesReasoningResponsesEnoughSharedReasoningAndVisibleOutputBudget() {
        AiProvider provider = provider(AiProviderType.OPENAI, "glm-5.2", 0.9, 16_000);
        var parameters = policy.initial(
            provider,
            registry.resolve(provider),
            ModelTaskType.PROJECT_UNDERSTANDING_SNAPSHOT,
            "x".repeat(2_000)
        );

        assertThat(parameters.temperatureSent()).isFalse();
        assertThat(parameters.taskRequestedMaxTokens()).isEqualTo(16_000);
        assertThat(parameters.effectiveMaxTokens()).isEqualTo(16_000);
        assertThat(parameters.maxTokenDecision()).contains("reasoning 与可见 JSON 共享");
    }

    @Test
    void calculatesDifferentBudgetsByTaskAndInputScale() {
        AiProvider provider = provider("deepseek-chat", 0.2, 64_000);
        var small = policy.initial(
            provider, registry.resolve(provider), ModelTaskType.CAPABILITY_INTERPRETATION, "x".repeat(500)
        );
        var large = policy.initial(
            provider, registry.resolve(provider), ModelTaskType.DEVELOPMENT_SEGMENT_MERGE, "x".repeat(45_000)
        );

        assertThat(small.effectiveMaxTokens()).isLessThan(4_000);
        assertThat(large.effectiveMaxTokens()).isGreaterThan(8_000);
        assertThat(large.maxTokenDecision()).contains("动态计算");
    }

    @Test
    void providerCeilingIsAppliedAndExplained() {
        AiProvider provider = provider("deepseek-chat", 0.2, 4_096);
        var parameters = policy.initial(
            provider, registry.resolve(provider), ModelTaskType.PROJECT_CAPABILITY_ANALYSIS, "x".repeat(40_000)
        );

        assertThat(parameters.effectiveMaxTokens()).isEqualTo(4_096);
        assertThat(parameters.taskRequestedMaxTokens()).isGreaterThan(4_096);
        assertThat(parameters.maxTokenDecision()).contains("Provider 配置能力上限");
    }

    @Test
    void recoveryBudgetDependsOnFailureTypeInsteadOfFixedTwoThousand() {
        AiProvider provider = provider("deepseek-chat", 0.2, 32_000);
        var capabilities = registry.resolve(provider);
        var initial = policy.initial(provider, capabilities, ModelTaskType.PROJECT_ANALYSIS, "x".repeat(8_000));
        var truncation = policy.recovery(initial, capabilities, ModelTaskType.PROJECT_ANALYSIS, "TRUNCATION_RETRY", 4_000);
        var schema = policy.recovery(initial, capabilities, ModelTaskType.PROJECT_ANALYSIS, "SCHEMA_REPAIR_RETRY", 1_000);

        assertThat(truncation.effectiveMaxTokens()).isGreaterThan(initial.effectiveMaxTokens());
        assertThat(truncation.effectiveMaxTokens()).isNotEqualTo(2_000);
        assertThat(schema.effectiveMaxTokens()).isNotEqualTo(truncation.effectiveMaxTokens());
    }

    @Test
    void reasoningSchemaRepairKeepsTaskUsefulCeiling() {
        AiProvider provider = provider(AiProviderType.OPENAI, "glm-5.2", 0.9, 16_000);
        var capabilities = registry.resolve(provider);
        var initial = policy.initial(
            provider,
            capabilities,
            ModelTaskType.PROJECT_UNDERSTANDING_SNAPSHOT,
            "x".repeat(2_000)
        );

        var schema = policy.recovery(
            initial,
            capabilities,
            ModelTaskType.PROJECT_UNDERSTANDING_SNAPSHOT,
            "SCHEMA_REPAIR_RETRY",
            2_000
        );

        assertThat(schema.taskRequestedMaxTokens()).isEqualTo(16_000);
        assertThat(schema.effectiveMaxTokens()).isEqualTo(16_000);
    }

    @Test
    void maxReasoningSchemaRepairKeepsProviderAllowanceForVisibleJson() {
        ModelRequestPolicy maxPolicy = new ModelRequestPolicy("max");
        AiProvider provider = provider(AiProviderType.DEEPSEEK, "provider-neutral-model", 0.1, 65_536);
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
        var capabilities = registry.resolve(provider);
        var initial = maxPolicy.initial(
            provider,
            capabilities,
            ModelTaskType.PROVIDER_PROJECTFLOW_COMPATIBILITY_TEST,
            "只返回一个很小的 JSON"
        );

        var repair = maxPolicy.recovery(
            initial,
            capabilities,
            ModelTaskType.PROVIDER_PROJECTFLOW_COMPATIBILITY_TEST,
            "SCHEMA_REPAIR_RETRY",
            64
        );

        assertThat(repair.taskRequestedMaxTokens()).isEqualTo(65_536);
        assertThat(repair.effectiveMaxTokens()).isEqualTo(65_536);
        assertThat(repair.maxTokenDecision()).contains("Provider 有界上限", "保持 max");
    }

    @Test
    void reasoningUsesBoundedProviderHeadroomFromTheFirstRequestAndRecovery() {
        AiProvider provider = provider(AiProviderType.OPENAI, "glm-5.2", 0.9, 65_536);
        var capabilities = registry.resolve(provider);
        var initial = policy.initial(
            provider,
            capabilities,
            ModelTaskType.PROJECT_UNDERSTANDING_SNAPSHOT,
            "x".repeat(48_000)
        );

        var recovery = policy.recovery(
            initial,
            capabilities,
            ModelTaskType.PROJECT_UNDERSTANDING_SNAPSHOT,
            "TRUNCATION_RETRY",
            0
        );

        assertThat(initial.taskRequestedMaxTokens()).isEqualTo(65_536);
        assertThat(initial.effectiveMaxTokens()).isEqualTo(65_536);
        assertThat(initial.maxTokenDecision()).contains("QUALITY_FIRST");
        assertThat(initial.maxTokenDecision()).contains("耗时和 Token 只作诊断");
        assertThat(recovery.taskRequestedMaxTokens()).isEqualTo(65_536);
        assertThat(recovery.effectiveMaxTokens()).isEqualTo(65_536);
        assertThat(recovery.maxTokenDecision()).contains("Provider");
    }

    @Test
    void chatHighReasoningRecoveryUsesExplicitProviderCeiling() {
        AiProvider provider = provider(AiProviderType.DEEPSEEK, "deepseek-v4-flash", 0.1, 65_536);
        provider.configureProtocol(
            ModelProtocol.OPENAI_CHAT_COMPLETIONS,
            null,
            null,
            null,
            null,
            Map.of(),
            600,
            false,
            false,
            false,
            true,
            true
        );
        var capabilities = registry.resolve(provider);
        var initial = policy.initial(
            provider,
            capabilities,
            ModelTaskType.PROJECT_UNDERSTANDING_SNAPSHOT,
            "x".repeat(48_000)
        );

        var recovery = policy.recovery(
            initial,
            capabilities,
            ModelTaskType.PROJECT_UNDERSTANDING_SNAPSHOT,
            "EMPTY_AFTER_REASONING_RETRY",
            0
        );

        assertThat(initial.effectiveMaxTokens()).isEqualTo(65_536);
        assertThat(recovery.taskRequestedMaxTokens()).isEqualTo(65_536);
        assertThat(recovery.effectiveMaxTokens()).isEqualTo(65_536);
        assertThat(recovery.maxTokenDecision()).contains("Chat high reasoning");
        assertThat(recovery.maxTokenDecision()).contains("保持 high");
    }

    @Test
    void explicitMaxReasoningIsPreservedAcrossInitialAndRecoveryRequests() {
        ModelRequestPolicy maxPolicy = new ModelRequestPolicy("max");
        AiProvider provider = provider(AiProviderType.DEEPSEEK, "provider-neutral-model", 0.1, 65_536);
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
        var capabilities = registry.resolve(provider);
        var initial = maxPolicy.initial(
            provider,
            capabilities,
            ModelTaskType.PROJECT_UNDERSTANDING_SNAPSHOT,
            "x".repeat(48_000)
        );
        var recovery = maxPolicy.recovery(
            initial,
            capabilities,
            ModelTaskType.PROJECT_UNDERSTANDING_SNAPSHOT,
            "EMPTY_AFTER_REASONING_RETRY",
            0
        );

        assertThat(maxPolicy.reasoningEffort(capabilities)).isEqualTo("max");
        assertThat(initial.maxTokenDecision()).contains("reasoning_effort", "max");
        assertThat(recovery.maxTokenDecision()).contains("保持 max");
    }

    @Test
    void messagesMaxReasoningIsPreservedAcrossInitialAndRecoveryRequests() {
        ModelRequestPolicy maxPolicy = new ModelRequestPolicy("max");
        AiProvider provider = provider(AiProviderType.ANTHROPIC, "qwen3.7-plus", 0.1, 65_536);
        provider.configureProtocol(
            ModelProtocol.ANTHROPIC_MESSAGES,
            null,
            null,
            null,
            null,
            Map.of(),
            600,
            false,
            false,
            false,
            true,
            true
        );
        var capabilities = registry.resolve(provider);
        var initial = maxPolicy.initial(
            provider,
            capabilities,
            ModelTaskType.PROJECT_UNDERSTANDING_SNAPSHOT,
            "x".repeat(48_000)
        );
        var recovery = maxPolicy.recovery(
            initial,
            capabilities,
            ModelTaskType.PROJECT_UNDERSTANDING_SNAPSHOT,
            "EMPTY_AFTER_REASONING_RETRY",
            0
        );

        assertThat(maxPolicy.reasoningEffort(capabilities)).isEqualTo("max");
        assertThat(initial.maxTokenDecision()).contains("Messages thinking budget", "max");
        assertThat(recovery.maxTokenDecision()).contains("Messages thinking budget", "保持 max");
    }

    @Test
    void nonReasoningTruncationRetainsBoundedFiftyPercentIncrease() {
        AiProvider provider = provider("deepseek-chat", 0.2, 32_000);
        var capabilities = registry.resolve(provider);
        var initial = policy.initial(
            provider,
            capabilities,
            ModelTaskType.PROJECT_UNDERSTANDING_SNAPSHOT,
            "x".repeat(48_000)
        );

        var recovery = policy.recovery(
            initial,
            capabilities,
            ModelTaskType.PROJECT_UNDERSTANDING_SNAPSHOT,
            "TRUNCATION_RETRY",
            0
        );

        assertThat(initial.effectiveMaxTokens()).isEqualTo(8_428);
        assertThat(recovery.taskRequestedMaxTokens()).isEqualTo(12_642);
        assertThat(recovery.effectiveMaxTokens()).isEqualTo(12_642);
        assertThat(recovery.maxTokenDecision()).contains("递增 50%");
    }

    private AiProvider provider(String model, double temperature, int maxTokens) {
        return provider(AiProviderType.DEEPSEEK, model, temperature, maxTokens);
    }

    private AiProvider provider(
        AiProviderType type,
        String model,
        double temperature,
        int maxTokens
    ) {
        AiProvider provider = new AiProvider(UUID.randomUUID());
        provider.update("test", "https://api.deepseek.com", "test-key", model, type,
            temperature, maxTokens, true, List.of());
        return provider;
    }
}
