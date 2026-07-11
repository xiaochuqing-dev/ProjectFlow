package com.projectflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.entity.AiProvider;
import com.projectflow.entity.AiProviderType;
import com.projectflow.service.AiProviderUrlGuard;
import com.projectflow.service.ModelGatewayService;
import com.projectflow.service.ModelOutputAdapter;

class RealDeepSeekIT {
    @Test
    @EnabledIfEnvironmentVariable(named = "PROJECTFLOW_RUN_REAL_MODEL", matches = "true")
    void validatesRealStructuredResponseWithinOneSmallTaskBudget() throws Exception {
        String apiKey = System.getenv("DEEPSEEK_API_KEY");
        assertThat(apiKey).as("真实模型测试必须通过安全环境变量提供 Key").isNotBlank();

        ObjectMapper mapper = new ObjectMapper();
        ModelGatewayService gateway = new ModelGatewayService(
            mapper, new AiProviderUrlGuard(), new ModelOutputAdapter(mapper), 60
        );
        AiProvider provider = new AiProvider(UUID.randomUUID());
        provider.update("DeepSeek 真实验收", "https://api.deepseek.com", apiKey, "deepseek-chat",
            AiProviderType.DEEPSEEK, 0.0, 256, false, List.of("REAL_TEST"));

        var response = gateway.callStructured(provider, "只返回这个 JSON：{\"ok\":true}", 128);
        assertThat(response.parsed().root().path("ok").asBoolean()).isTrue();
        assertThat(response.diagnostics().requestCount()).isBetween(1, 3);
        assertThat(response.diagnostics().totalTokens()).isLessThan(1000);
        assertThat(response.diagnostics().finishReason()).isNotBlank();
    }
}
