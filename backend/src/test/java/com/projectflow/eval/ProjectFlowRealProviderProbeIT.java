package com.projectflow.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.entity.AiProvider;
import com.projectflow.service.AiProviderUrlGuard;
import com.projectflow.service.ModelGatewayService;
import com.projectflow.service.ModelOutputAdapter;
import com.projectflow.service.ModelTaskType;

class ProjectFlowRealProviderProbeIT {

    @Test
    void probesConfiguredProviderThroughModelGateway() throws Exception {
        var config = ProjectFlowRealModelEvalIT.providerConfig();
        Assumptions.assumeTrue(config != null, "未提供真实 Provider 配置，健康检查跳过");

        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        ModelGatewayService gateway = new ModelGatewayService(
            mapper,
            new AiProviderUrlGuard(),
            new ModelOutputAdapter(mapper),
            config.timeoutSeconds()
        );
        AiProvider provider = new AiProvider(UUID.randomUUID());
        provider.update(
            config.name(),
            config.baseUrl(),
            config.apiKey(),
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

        var response = gateway.callStructured(
            provider,
            "基于事实‘ProjectFlow 使用 ProjectFact 保存已发生开发结果’，只返回 {\"summary\":\"\"}，填入一句简短中文。",
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
