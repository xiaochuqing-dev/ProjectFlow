package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.entity.AiProvider;
import com.projectflow.entity.AiProviderType;
import com.projectflow.entity.ModelProtocol;
import com.projectflow.repository.AiProviderRepository;

class AiProviderServiceProbeTest {

    @Test
    void oneProjectFlowTaskCoversConnectionProtocolAndStructuredCompatibility() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        AiProviderRepository repository = mock(AiProviderRepository.class);
        ModelGatewayService gateway = mock(ModelGatewayService.class);
        AiProvider provider = provider();
        when(repository.findByIdAndUserId(provider.getId(), provider.getUserId())).thenReturn(Optional.of(provider));

        String content = "{\"summary\":\"ProjectFact 保存已发生的开发事实\"}";
        ModelOutputAdapter.ParsedOutput parsed = new ModelOutputAdapter(mapper).parse(
            content,
            ModelTaskType.PROVIDER_PROJECTFLOW_COMPATIBILITY_TEST
        );
        when(gateway.callStructured(
            eq(provider),
            anyString(),
            eq(ModelTaskType.PROVIDER_PROJECTFLOW_COMPATIBILITY_TEST)
        )).thenReturn(new ModelGatewayService.StructuredModelResponse(content, parsed));

        AiProviderService service = new AiProviderService(
            repository,
            new AiProviderUrlGuard(),
            gateway,
            mock(ApplicationEventPublisher.class),
            new ModelCapabilityRegistry(),
            mapper
        );

        var result = service.test(provider.getUserId(), provider.getId());

        assertThat(result.ok()).isTrue();
        assertThat(result.profile().connection()).isEqualTo("PASSED");
        assertThat(result.profile().projectFlowCompatibility()).isEqualTo("FULL");
        verify(gateway).callStructured(
            eq(provider),
            anyString(),
            eq(ModelTaskType.PROVIDER_PROJECTFLOW_COMPATIBILITY_TEST)
        );
        verify(gateway, never()).callStructured(
            eq(provider),
            anyString(),
            eq(ModelTaskType.PROVIDER_CONNECTION_TEST)
        );
    }

    private AiProvider provider() {
        AiProvider provider = new AiProvider(UUID.randomUUID());
        provider.update(
            "Qwen3.7 Plus OpenCode Go",
            "https://opencode.ai/zen/go",
            "test-key",
            "qwen3.7-plus",
            AiProviderType.ANTHROPIC,
            0.1,
            65_536,
            true,
            List.of("PROBE_TEST")
        );
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
        return provider;
    }
}
