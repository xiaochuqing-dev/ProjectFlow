package com.projectflow.service;

import org.springframework.stereotype.Component;

import com.projectflow.entity.AiProvider;
import com.projectflow.service.ModelCapabilityRegistry.ModelCapabilities;

/** 根据任务、输入规模、Provider 能力和用户配置计算实际请求参数。 */
@Component
public class ModelRequestPolicy {
    public RequestParameters initial(AiProvider provider, ModelCapabilities capabilities, ModelTaskType task, String prompt) {
        int inputChars = prompt == null ? 0 : prompt.length();
        int scaleTokens = Math.max(0, inputChars / (task.collectionOutput() ? 9 : 14));
        int taskRequested = Math.min(task.maximumUsefulOutputTokens(), task.baseOutputTokens() + scaleTokens);
        if (capabilities.supportsReasoning()) {
            // Responses providers commonly count hidden reasoning and visible JSON
            // against one max-output budget. Reserve the task's bounded useful
            // ceiling so valid visible output is not crowded out by reasoning.
            taskRequested = task.maximumUsefulOutputTokens();
        }
        int effective = Math.min(capabilities.maxOutputTokens(), Math.max(256, taskRequested));
        boolean sendTemperature = capabilities.supportsTemperature();
        Double effectiveTemperature = sendTemperature ? provider.getTemperature() : null;
        String temperatureReason = sendTemperature
            ? "Provider 支持 temperature，使用用户配置值；任务推荐值仅用于诊断，不做全局封顶"
            : "当前模型能力档案不支持 temperature，请求中省略该字段";
        String maxTokenReason = effective < taskRequested
            ? "任务按输入规模申请 " + taskRequested + "，受 Provider 配置能力上限 " + capabilities.maxOutputTokens() + " 约束"
            : capabilities.supportsReasoning()
                ? "reasoning 与可见 JSON 共享输出预算，使用任务有界有效上限，未使用固定 4000/2000 上限"
                : "按任务类型、输入规模和预期结构动态计算，未使用固定 4000/2000 上限";
        if (capabilities.supportsReasoningControl()
            && "OPENAI_RESPONSES".equals(capabilities.providerResponseShape())) {
            maxTokenReason += "；Provider 显式支持 reasoning control，首次结构化请求使用 high";
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
            requested = Math.max(initial.taskRequestedMaxTokens() * 2, task.baseOutputTokens() * 2);
        } else {
            requested = capabilities.supportsReasoning()
                ? initial.taskRequestedMaxTokens() * 2
                : initial.taskRequestedMaxTokens()
                    + Math.max(1_024, initial.taskRequestedMaxTokens() / 2);
        }
        if (capabilities.supportsReasoning() && "SCHEMA_REPAIR_RETRY".equals(retryType)) {
            requested = Math.max(requested, task.maximumUsefulOutputTokens());
        }
        int effective = Math.min(capabilities.maxOutputTokens(), requested);
        String reason = switch (retryType) {
            case "SCHEMA_REPAIR_RETRY" -> "仅重编码已有语义为目标 Schema，预算按目标结构和原内容规模计算";
            case "EMPTY_AFTER_REASONING_RETRY" -> "reasoning 疑似占满共享预算，提高可见输出预算后重试";
            default -> capabilities.supportsReasoning()
                ? "首次输出截断且 reasoning 与可见结果共享预算，在唯一恢复请求中使用最多两倍预算"
                : "首次输出截断，按已申请预算递增 50%，不再压缩为固定小预算";
        };
        if (effective < requested) {
            reason += "；最终受 Provider 配置能力上限 " + capabilities.maxOutputTokens() + " 约束";
        }
        if (capabilities.supportsReasoningControl()
            && "OPENAI_RESPONSES".equals(capabilities.providerResponseShape())) {
            reason += "；恢复请求使用 low reasoning effort，避免再次挤占可见 JSON";
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
}
