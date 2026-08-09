package com.projectflow.service;

import java.util.Locale;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.projectflow.entity.AiProvider;
import com.projectflow.service.ModelCapabilityRegistry.ModelCapabilities;

/** 根据任务、输入规模、Provider 能力和用户配置计算实际请求参数。 */
@Component
public class ModelRequestPolicy {
    private static final String DEFAULT_REASONING_EFFORT = "high";
    private static final Set<String> REASONING_EFFORTS = Set.of(
        "minimal", "low", "medium", "high", "xhigh", "max"
    );

    private final String configuredReasoningEffort;

    public ModelRequestPolicy() {
        this(DEFAULT_REASONING_EFFORT);
    }

    @Autowired
    public ModelRequestPolicy(
        @Value("${projectflow.model.reasoning-effort:${PROJECTFLOW_MODEL_REASONING_EFFORT:high}}")
        String reasoningEffort
    ) {
        this.configuredReasoningEffort = normalizeReasoningEffort(reasoningEffort);
    }

    static ModelRequestPolicy runtimeConfigured() {
        String value = System.getProperty("projectflow.model.reasoning-effort");
        if (value == null || value.isBlank()) {
            value = System.getenv("PROJECTFLOW_MODEL_REASONING_EFFORT");
        }
        return new ModelRequestPolicy(value);
    }

    public RequestParameters initial(AiProvider provider, ModelCapabilities capabilities, ModelTaskType task, String prompt) {
        int inputChars = prompt == null ? 0 : prompt.length();
        int scaleTokens = Math.max(0, inputChars / (task.collectionOutput() ? 9 : 14));
        int taskRequested = Math.min(task.maximumUsefulOutputTokens(), task.baseOutputTokens() + scaleTokens);
        boolean qualityFirstReasoning = capabilities.supportsReasoning();
        if (qualityFirstReasoning) {
            // max_tokens is a ceiling, not a consumption target. Explicit
            // QUALITY_FIRST reasoning profiles use the user's bounded Provider
            // allowance from the first request instead of crowding thinking and
            // visible JSON into an ordinary-output estimate. Usage and latency
            // remain diagnostics and never trigger a lower reasoning tier.
            taskRequested = capabilities.maxOutputTokens();
        }
        int effective = Math.min(capabilities.maxOutputTokens(), Math.max(256, taskRequested));
        boolean sendTemperature = capabilities.supportsTemperature();
        Double effectiveTemperature = sendTemperature ? provider.getTemperature() : null;
        String temperatureReason = sendTemperature
            ? "Provider 支持 temperature，使用用户配置值；任务推荐值仅用于诊断，不做全局封顶"
            : "当前模型能力档案不支持 temperature，请求中省略该字段";
        String maxTokenReason = effective < taskRequested
            ? "任务按输入规模申请 " + taskRequested + "，受 Provider 配置能力上限 " + capabilities.maxOutputTokens() + " 约束"
            : qualityFirstReasoning
                ? "reasoning 与可见 JSON 共享输出空间；显式 QUALITY_FIRST 任务首次请求即使用用户配置的 Provider 宽松有界上限；上限不是 Token 消耗目标，耗时和 Token 只作诊断"
                : "按任务类型、输入规模和预期结构动态计算，未使用固定 4000/2000 上限";
        String reasoningEffort = reasoningEffort(capabilities);
        if (reasoningEffort != null
            && "OPENAI_RESPONSES".equals(capabilities.providerResponseShape())) {
            maxTokenReason += "；Provider 显式支持 reasoning control，首次结构化请求使用 " + reasoningEffort;
        } else if (reasoningEffort != null
            && "OPENAI_CHAT_COMPLETIONS".equals(capabilities.providerResponseShape())) {
            maxTokenReason += "；Provider 显式支持 Chat reasoning_effort，结构化请求使用 " + reasoningEffort;
        }
        return new RequestParameters(
            taskRequested, effective, provider.getTemperature(), task.recommendedTemperature(), effectiveTemperature,
            sendTemperature, temperatureReason, maxTokenReason, capabilities.recommendedTimeoutSeconds(), "NONE"
        );
    }

    public RequestParameters recovery(
        RequestParameters initial,
        ModelCapabilities capabilities,
        ModelTaskType task,
        String retryType,
        int previousContentLength
    ) {
        int requested;
        if ("SCHEMA_REPAIR_RETRY".equals(retryType)) {
            requested = Math.max(1_024, task.baseOutputTokens() / 2 + Math.max(0, previousContentLength) / 12);
        } else if ("EMPTY_AFTER_REASONING_RETRY".equals(retryType)) {
            requested = capabilities.supportsReasoningControl()
                && "OPENAI_CHAT_COMPLETIONS".equals(capabilities.providerResponseShape())
                    ? capabilities.maxOutputTokens()
                    : Math.max(initial.taskRequestedMaxTokens() * 2, task.baseOutputTokens() * 2);
        } else {
            requested = capabilities.supportsReasoning()
                ? capabilities.maxOutputTokens()
                : initial.taskRequestedMaxTokens()
                    + Math.max(1_024, initial.taskRequestedMaxTokens() / 2);
        }
        if (capabilities.supportsReasoning() && "SCHEMA_REPAIR_RETRY".equals(retryType)) {
            requested = Math.max(requested, task.maximumUsefulOutputTokens());
        }
        int effective = Math.min(capabilities.maxOutputTokens(), requested);
        String reason = switch (retryType) {
            case "SCHEMA_REPAIR_RETRY" -> "仅重编码已有语义为目标 Schema，预算按目标结构和原内容规模计算";
            case "EMPTY_AFTER_REASONING_RETRY" -> capabilities.supportsReasoningControl()
                && "OPENAI_CHAT_COMPLETIONS".equals(capabilities.providerResponseShape())
                    ? "Chat " + configuredReasoningEffort + " reasoning 疑似占满共享预算，唯一恢复请求使用 Provider 明确上限"
                    : "reasoning 疑似占满共享预算，提高可见输出预算后重试";
            default -> capabilities.supportsReasoning()
                ? "首次输出截断且 reasoning 与可见结果共享预算，在唯一恢复请求中使用 Provider 显式配置上限"
                : "首次输出截断，按已申请预算递增 50%，不再压缩为固定小预算";
        };
        if (effective < requested) {
            reason += "；最终受 Provider 配置能力上限 " + capabilities.maxOutputTokens() + " 约束";
        }
        String reasoningEffort = reasoningEffort(capabilities);
        if (reasoningEffort != null
            && "OPENAI_RESPONSES".equals(capabilities.providerResponseShape())) {
            reason += "；语义恢复请求保持 " + reasoningEffort + "，不以耗时或 Token 为由降低思考档次";
        } else if (reasoningEffort != null
            && "OPENAI_CHAT_COMPLETIONS".equals(capabilities.providerResponseShape())) {
            reason += "；Chat reasoning_effort 保持 " + reasoningEffort + "，优先完整语义判断";
        }
        return new RequestParameters(
            requested, effective, initial.configuredTemperature(), initial.recommendedTemperature(),
            initial.effectiveTemperature(), initial.temperatureSent(), initial.temperatureDecision(), reason,
            initial.timeoutSeconds(), retryType
        );
    }

    public record RequestParameters(
        int taskRequestedMaxTokens,
        int effectiveMaxTokens,
        double configuredTemperature,
        double recommendedTemperature,
        Double effectiveTemperature,
        boolean temperatureSent,
        String temperatureDecision,
        String maxTokenDecision,
        int timeoutSeconds,
        String retryType
    ) {
    }

    String reasoningEffort(ModelCapabilities capabilities) {
        if (!capabilities.supportsReasoningControl()) {
            return null;
        }
        return switch (capabilities.providerResponseShape()) {
            case "OPENAI_RESPONSES", "OPENAI_CHAT_COMPLETIONS" -> configuredReasoningEffort;
            default -> null;
        };
    }

    private static String normalizeReasoningEffort(String value) {
        String normalized = value == null || value.isBlank()
            ? DEFAULT_REASONING_EFFORT
            : value.strip().toLowerCase(Locale.ROOT);
        if (!REASONING_EFFORTS.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported model reasoning effort: " + normalized);
        }
        return normalized;
    }
}
