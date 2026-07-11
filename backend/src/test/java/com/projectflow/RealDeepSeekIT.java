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
import com.projectflow.service.ModelTaskType;

class RealDeepSeekIT {
    @Test
    @EnabledIfEnvironmentVariable(named = "PROJECTFLOW_RUN_REAL_MODEL", matches = "true")
    void validatesEveryRegisteredRealEntrypointWithSmallInputs() throws Exception {
        String apiKey = System.getenv("DEEPSEEK_API_KEY");
        assertThat(apiKey).as("真实模型测试必须通过安全环境变量提供 Key").isNotBlank();

        ObjectMapper mapper = new ObjectMapper();
        ModelGatewayService gateway = new ModelGatewayService(
            mapper, new AiProviderUrlGuard(), new ModelOutputAdapter(mapper), 60
        );
        AiProvider provider = new AiProvider(UUID.randomUUID());
        String model = System.getenv().getOrDefault("DEEPSEEK_MODEL", "deepseek-chat");
        provider.update("DeepSeek 真实验收", "https://api.deepseek.com", apiKey, model,
            AiProviderType.DEEPSEEK, 0.2, 12_000, false, List.of("REAL_TEST"));

        List<RealCase> cases = List.of(
            new RealCase(ModelTaskType.PROVIDER_CONNECTION_TEST, "只返回这个 JSON：{\"ok\":true}"),
            new RealCase(ModelTaskType.DEVELOPMENT_SEGMENT_MERGE,
                "只返回 JSON：{\"segments\":[{\"segmentTitle\":\"可靠性验证\",\"plainSummary\":\"验证真实模型入口\",\"sourceIndexes\":[\"S1\"]}]}"),
            new RealCase(ModelTaskType.PROJECT_ANALYSIS,
                "只返回 JSON：{\"summary\":\"测试项目\",\"architecture\":\"单体测试结构\",\"modules\":[],\"risks\":[],\"importantFiles\":[],\"evidence\":[],\"limitations\":[],\"confidence\":\"HIGH\"}"),
            new RealCase(ModelTaskType.FILE_ANALYSIS,
                "只返回 JSON：{\"path\":\"README.md\",\"fileType\":\"Markdown\",\"role\":\"项目说明\",\"summary\":\"记录启动方式\",\"importance\":\"MEDIUM\",\"riskLevel\":\"LOW\",\"riskNotes\":\"无\",\"evidence\":[],\"relatedFiles\":[],\"limitations\":[],\"confidence\":\"HIGH\"}"),
            new RealCase(ModelTaskType.CAPABILITY_INTERPRETATION,
                "只返回 JSON：{\"summary\":\"统一模型网关\",\"problem\":\"避免参数漂移\",\"value\":\"提高可靠性\",\"readme\":\"统一模型入口\",\"resume\":\"完成模型网关治理\",\"interview\":\"说明动态预算\"}"),
            new RealCase(ModelTaskType.PROJECT_CAPABILITY_ANALYSIS,
                "只返回 JSON：{\"capabilities\":[{\"name\":\"模型可靠性治理\",\"summary\":\"统一参数与恢复策略\",\"sourceIndexes\":[\"S1\"]}]}")
        );
        for (RealCase testCase : cases) {
            var response = gateway.callStructured(provider, testCase.prompt(), testCase.task());
            assertThat(testCase.task().schemaMatches(response.parsed().root(), new ModelOutputAdapter(mapper)))
                .as(testCase.task().entryPoint()).isTrue();
            assertThat(response.diagnostics().entryPoint()).isEqualTo(testCase.task().entryPoint());
            assertThat(response.diagnostics().requestCount()).isBetween(1, 3);
            assertThat(response.diagnostics().finishReason()).isNotBlank();
            assertThat(response.diagnostics().totalTokens()).isLessThan(20_000);
        }
    }

    private record RealCase(ModelTaskType task, String prompt) {}
}
