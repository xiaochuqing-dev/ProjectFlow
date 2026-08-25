package com.projectflow.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.entity.AiProvider;
import com.projectflow.service.AiProviderUrlGuard;
import com.projectflow.service.InMemoryProviderCredentialStore;
import com.projectflow.service.ModelCapabilityRegistry;
import com.projectflow.service.ModelGatewayService;
import com.projectflow.service.ModelOutputAdapter;
import com.projectflow.service.ModelRequestPolicy;
import com.projectflow.service.ModelTaskType;
import com.projectflow.service.model.AnthropicMessagesAdapter;
import com.projectflow.service.model.ModelProtocolAdapterRegistry;
import com.projectflow.service.model.OpenAiChatCompletionsAdapter;
import com.projectflow.service.model.OpenAiResponsesAdapter;

class ProjectFlowRealProviderProbeIT {

    @Test
    void probesConfiguredProviderThroughModelGateway() throws Exception {
        var config = ProjectFlowRealModelEvalIT.providerConfig();
        Assumptions.assumeTrue(config != null, "未提供真实 Provider 配置，健康检查跳过");

        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        AiProviderUrlGuard urlGuard = new AiProviderUrlGuard();
        InMemoryProviderCredentialStore credentialStore = new InMemoryProviderCredentialStore();
        ModelGatewayService gateway = new ModelGatewayService(
            mapper,
            urlGuard,
            new ModelOutputAdapter(mapper),
            new ModelCapabilityRegistry(),
            new ModelRequestPolicy(config.reasoningEffort()),
            new ModelProtocolAdapterRegistry(List.of(
                new OpenAiResponsesAdapter(urlGuard),
                new OpenAiChatCompletionsAdapter(urlGuard),
                new AnthropicMessagesAdapter(urlGuard)
            )),
            credentialStore,
            config.timeoutSeconds()
        );
        AiProvider provider = new AiProvider(UUID.randomUUID());
        provider.update(
            config.name(),
            config.baseUrl(),
            null,
            config.model(),
            config.type(),
            0.0,
            Math.max(256, config.maxTokens()),
            false,
            List.of("V3.7.5_REAL_PROVIDER_PROBE")
        );
        provider.configureProtocol(
            config.protocol(),
            null,
            null,
            null,
            null,
            java.util.Map.of(),
            config.timeoutSeconds(),
            null,
            config.supportsJsonMode(),
            null,
            config.supportsReasoning(),
            config.supportsReasoningControl()
        );
        provider.setSecretRef(credentialStore.writeAndVerify(provider.getId(), config.apiKey()));

        var response = gateway.callStructured(
            provider,
            "请用一句简短中文概括以下事实：ProjectFlow 使用 ProjectFact 保存已发生的开发结果。"
                + "输出一个 JSON 对象，其中只包含名为 summary 的字符串字段。",
            ModelTaskType.PROVIDER_PROJECTFLOW_COMPATIBILITY_TEST
        );
        var diagnostics = response.diagnostics();

        assertThat(response.parsed().root().path("summary").asText()).isNotBlank();
        assertThat(diagnostics.requestSucceeded()).isTrue();
        assertThat(diagnostics.schemaMatched()).isTrue();
        assertThat(diagnostics.requestCount()).isBetween(1, 2);
        assertThat(diagnostics.protocol()).isEqualTo(config.protocol().name());
        assertThat(diagnostics.modelName()).isEqualTo(config.model());
        assertThat(diagnostics.reasoningEffort()).isEqualTo(
            config.supportsReasoningControl() ? config.reasoningEffort() : ""
        );
        assertThat(diagnostics.requestId()).doesNotContain(config.apiKey());
    }
}
