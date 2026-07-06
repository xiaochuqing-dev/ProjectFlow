package com.projectflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.entity.AiProvider;
import com.projectflow.entity.AiProviderType;
import com.projectflow.entity.EvidenceConfidence;
import com.projectflow.repository.AiProviderRepository;
import com.projectflow.service.DevelopmentSegmentationService.ChangeAtom;
import com.projectflow.service.DevelopmentSegmentationService.SegmentDraft;
import com.projectflow.service.ModelGatewayService;
import com.projectflow.service.ModelSegmentEnricher;
import com.projectflow.service.SegmentEvidenceValidator;

@ExtendWith(MockitoExtension.class)
class ModelSegmentEnricherTest {
    @Mock
    private AiProviderRepository providerRepository;

    @Mock
    private ModelGatewayService modelGatewayService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void acceptsStrictJsonButRemovesInventedReferences() throws Exception {
        UUID userId = UUID.randomUUID();
        AiProvider provider = provider(userId);
        when(providerRepository.findByUserIdOrderByDefaultEnabledDescUpdatedAtDesc(userId)).thenReturn(List.of(provider));
        when(modelGatewayService.callJson(any(), anyString(), anyInt())).thenReturn(objectMapper.readTree("""
            {
              "segments": [{
                "segmentTitle": "扫描游标",
                "plainSummary": "从确认点读取新变化。",
                "includedAtomIds": ["real", "invented"],
                "mainChanges": ["新增扫描游标"],
                "userVisibleValue": "避免漏掉跨天提交。",
                "evidenceRefs": ["commit:real", "commit:invented"],
                "affectedFiles": ["backend/Scan.java", "missing.java"],
                "confidence": "HIGH"
              }]
            }
            """));
        ModelSegmentEnricher enricher = new ModelSegmentEnricher(providerRepository, modelGatewayService, new SegmentEvidenceValidator());
        List<String> warnings = new ArrayList<>();

        List<SegmentDraft> result = enricher.enrich(userId, atoms(), fallback(), warnings);

        assertThat(result).singleElement().satisfies(segment -> {
            assertThat(segment.includedAtomIds()).containsExactly("real");
            assertThat(segment.evidenceRefs()).containsExactly("commit:real", "file:backend/Scan.java");
            assertThat(segment.affectedFiles()).containsExactly("backend/Scan.java");
        });
        assertThat(warnings).isEmpty();
    }

    @Test
    void malformedModelOutputFallsBackToRulesWithWarning() throws Exception {
        UUID userId = UUID.randomUUID();
        when(providerRepository.findByUserIdOrderByDefaultEnabledDescUpdatedAtDesc(userId)).thenReturn(List.of(provider(userId)));
        when(modelGatewayService.callJson(any(), anyString(), anyInt())).thenThrow(new IOException("invalid response"));
        ModelSegmentEnricher enricher = new ModelSegmentEnricher(providerRepository, modelGatewayService, new SegmentEvidenceValidator());
        List<String> warnings = new ArrayList<>();

        List<SegmentDraft> result = enricher.enrich(userId, atoms(), fallback(), warnings);

        assertThat(result).isEqualTo(fallback());
        assertThat(warnings).singleElement().asString().contains("模型归并失败");
    }

    private AiProvider provider(UUID userId) {
        AiProvider provider = new AiProvider(userId);
        provider.update("DeepSeek", "https://api.example.com", "test-key", "model", AiProviderType.DEEPSEEK, 0.2, 4_000, true, List.of("analysis"));
        return provider;
    }

    private List<ChangeAtom> atoms() {
        return List.of(new ChangeAtom(
            "real", "feat(scan): add cursor", Instant.parse("2026-07-06T08:00:00Z"),
            List.of("backend"), List.of("backend/Scan.java"), List.of("commit:real", "file:backend/Scan.java")
        ));
    }

    private List<SegmentDraft> fallback() {
        return List.of(new SegmentDraft(
            "scan 开发推进", "规则摘要", List.of("real"), List.of("新增游标"), "避免漏扫",
            List.of("commit:real", "file:backend/Scan.java"), List.of("backend/Scan.java"), EvidenceConfidence.HIGH
        ));
    }
}
