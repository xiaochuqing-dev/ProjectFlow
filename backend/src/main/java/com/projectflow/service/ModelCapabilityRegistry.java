package com.projectflow.service;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.projectflow.entity.AiProvider;
import com.projectflow.entity.AiProviderType;
import com.projectflow.entity.ModelProtocol;

/** Provider/model 能力集中定义；未知兼容服务按最小标准能力安全退化。 */
@Component
public class ModelCapabilityRegistry {
    public ModelCapabilities resolve(AiProvider provider) {
        String model = provider.getModelName() == null ? "" : provider.getModelName().toLowerCase(Locale.ROOT);
        boolean deepSeek = provider.getType() == AiProviderType.DEEPSEEK;
        boolean reasoning = model.contains("reasoner") || model.contains("reasoning") || model.matches(".*(^|[-_])r1($|[-_]).*");
        boolean knownChat = model.contains("chat");
        boolean supportsTemperature = provider.getSupportsTemperature() != null
            ? provider.getSupportsTemperature() : !reasoning;
        boolean protocolJson = provider.getProtocol() == ModelProtocol.OPENAI_CHAT_COMPLETIONS;
        boolean defaultJsonMode = protocolJson && deepSeek && !reasoning && knownChat;
        boolean supportsJsonMode = provider.getSupportsJsonMode() != null
            ? provider.getSupportsJsonMode() : defaultJsonMode;
        boolean supportsStructuredOutput = provider.getSupportsStructuredOutput() != null
            ? provider.getSupportsStructuredOutput() : provider.getProtocol() == ModelProtocol.OPENAI_RESPONSES;
        boolean supportsReasoning = provider.getSupportsReasoning() != null ? provider.getSupportsReasoning() : reasoning;
        boolean supportsReasoningControl = provider.getSupportsReasoningControl() != null
            ? provider.getSupportsReasoningControl() : false;
        String profile = deepSeek
            ? reasoning ? "DEEPSEEK_REASONING" : knownChat ? "DEEPSEEK_CHAT" : "DEEPSEEK_STANDARD"
            : provider.getType() == AiProviderType.OPENAI ? "OPENAI_RESPONSES_STANDARD"
            : provider.getType() == AiProviderType.ANTHROPIC ? "ANTHROPIC_MESSAGES_STANDARD"
            : provider.getType() == AiProviderType.OPENAI_COMPATIBLE ? "OPENAI_COMPATIBLE_STANDARD" : "CUSTOM_STANDARD";
        return new ModelCapabilities(
            profile,
            supportsTemperature,
            supportsJsonMode,
            supportsStructuredOutput,
            supportsReasoning,
            List.of("reasoning_content", "reasoning", "analysis"),
            Math.max(256, provider.getMaxTokens()),
            supportsReasoningControl,
            false,
            provider.getProtocol().name(),
            provider.getRequestTimeoutSeconds() == null ? (reasoning ? 300 : 240) : provider.getRequestTimeoutSeconds()
        );
    }

    public record ModelCapabilities(
        String profile,
        boolean supportsTemperature,
        boolean supportsJsonMode,
        boolean supportsStructuredOutput,
        boolean supportsReasoning,
        List<String> reasoningFieldNames,
        int maxOutputTokens,
        boolean supportsReasoningControl,
        boolean supportsStreaming,
        String providerResponseShape,
        int recommendedTimeoutSeconds
    ) {
    }
}
