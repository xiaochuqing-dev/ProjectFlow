package com.projectflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.entity.AiProvider;
import com.projectflow.entity.AiProviderType;
import com.projectflow.entity.EvidenceConfidence;
import com.projectflow.repository.AiProviderRepository;
import com.projectflow.service.DevelopmentSegmentationService.ChangeAtom;
import com.projectflow.service.DevelopmentSegmentationService.SegmentDraft;
import com.projectflow.service.ModelFailureClassifier;
import com.projectflow.service.ModelGatewayService;
import com.projectflow.service.ModelSegmentEnricher;
import com.projectflow.service.ModelOutputAdapter;
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
        when(modelGatewayService.callStructured(any(), anyString(), any(com.projectflow.service.ModelTaskType.class))).thenReturn(structured("""
            {
              "segments": [{
                "segmentTitle": "新增扫描游标并从确认点读取变化",
                "plainSummary": "新增扫描游标，从最后确认点读取新变化并保留证据。",
                "includedAtomIds": ["real", "invented"],
                "mainChanges": ["新增扫描游标", "从确认点读取提交", "保留提交与文件证据"],
                "userVisibleValue": "避免漏掉跨天提交。",
                "evidenceRefs": ["commit:real", "commit:invented"],
                "affectedFiles": ["backend/Scan.java", "missing.java"],
                "confidence": "HIGH",
                "needsUserReview": true
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
        when(modelGatewayService.callStructured(any(), anyString(), any(com.projectflow.service.ModelTaskType.class))).thenThrow(new IOException("invalid response"));
        ModelSegmentEnricher enricher = new ModelSegmentEnricher(providerRepository, modelGatewayService, new SegmentEvidenceValidator());
        List<String> warnings = new ArrayList<>();

        List<SegmentDraft> result = enricher.enrich(userId, atoms(), fallback(), warnings);

        assertThat(result).isEqualTo(fallback());
        assertThat(warnings).singleElement().asString().contains("网络连接失败");
    }

    @Test
    void singleReviewFlagErrorDoesNotDiscardUsableSegment() throws Exception {
        UUID userId = UUID.randomUUID();
        when(providerRepository.findByUserIdOrderByDefaultEnabledDescUpdatedAtDesc(userId)).thenReturn(List.of(provider(userId)));
        when(modelGatewayService.callStructured(any(), anyString(), any(com.projectflow.service.ModelTaskType.class))).thenReturn(structured("""
            {
              "segments": [{
                "segmentTitle": "扫描游标",
                "plainSummary": "从确认点读取新变化。",
                "includedAtomIds": ["real"],
                "mainChanges": ["新增扫描游标"],
                "userVisibleValue": "避免漏掉跨天提交。",
                "evidenceRefs": ["commit:real"],
                "affectedFiles": ["backend/Scan.java"],
                "confidence": "HIGH",
                "needsUserReview": false
              }]
            }
            """));
        ModelSegmentEnricher enricher = new ModelSegmentEnricher(providerRepository, modelGatewayService, new SegmentEvidenceValidator());
        List<String> warnings = new ArrayList<>();

        assertThat(enricher.enrich(userId, atoms(), fallback(), warnings)).singleElement()
            .satisfies(segment -> assertThat(segment.title()).isEqualTo("扫描游标"));
        assertThat(warnings).singleElement().asString().contains("需要注意");
    }

    @Test
    void lowQualityModelOutputIsRetainedWithReviewFlagNotDiscarded() throws Exception {
        // V3.3.3: 模型返回可解析结构就保留，质量门槛改为标记器，不再整批丢弃模型结果。
        UUID userId = UUID.randomUUID();
        when(providerRepository.findByUserIdOrderByDefaultEnabledDescUpdatedAtDesc(userId)).thenReturn(List.of(provider(userId)));
        when(modelGatewayService.callStructured(any(), anyString(), any(com.projectflow.service.ModelTaskType.class))).thenReturn(structured("""
            {"segments":[{
              "segmentTitle":"backend 开发推进","plainSummary":"这一组变化围绕 backend 展开，共包含 1 条原子变化。",
              "includedAtomIds":["real"],"mainChanges":["修改后端"],"userVisibleValue":"相关能力已归并",
              "evidenceRefs":["commit:real"],"affectedFiles":["backend/Scan.java"],"confidence":"LOW","needsUserReview":true
            }]}
            """));
        ModelSegmentEnricher enricher = new ModelSegmentEnricher(
            providerRepository, modelGatewayService, new SegmentEvidenceValidator(), new com.projectflow.service.SegmentQualityGate()
        );

        var result = enricher.enrichWithDiagnostics(userId, atoms(), fallback());

        // 模型结果被保留（不是 fallback），模式为 MODEL。
        assertThat(result.mode()).isEqualTo("MODEL");
        assertThat(result.modelStatus()).isEqualTo("SUCCESS_WITH_WARNINGS");
        assertThat(result.segments()).singleElement().satisfies(segment -> {
            assertThat(segment.title()).isEqualTo("backend 开发推进");
            assertThat(segment.confidence()).isEqualTo(EvidenceConfidence.LOW);
        });
        // 质量问题转为标记，不再回退本地规则。
        assertThat(result.fallbackReason()).contains("需人工复核");
        // 不应重试两次——保留优先，一次就保留。
        verify(modelGatewayService, times(1)).callStructured(any(), anyString(), any(com.projectflow.service.ModelTaskType.class));
    }

    @Test
    void sourceIndexesRestoreEvidenceAndInvalidItemDoesNotBreakBatch() throws Exception {
        UUID userId = UUID.randomUUID();
        when(providerRepository.findByUserIdOrderByDefaultEnabledDescUpdatedAtDesc(userId)).thenReturn(List.of(provider(userId)));
        when(modelGatewayService.callStructured(any(), anyString(), any(com.projectflow.service.ModelTaskType.class))).thenReturn(structured("""
            {"developmentSegments":[
              {"title":"扫描游标恢复分析范围","summary":"从确认点恢复待整理变化。","sourceIndexes":["S1","S99"],
               "changes":["恢复扫描范围"],"value":"减少重复扫描。","confidence":"HIGH"},
              "无法识别的单项"
            ]}
            """));
        ModelSegmentEnricher enricher = new ModelSegmentEnricher(providerRepository, modelGatewayService, new SegmentEvidenceValidator());

        var result = enricher.enrichWithDiagnostics(userId, atoms(), fallback());

        assertThat(result.mode()).isEqualTo("MODEL");
        assertThat(result.segments()).singleElement().satisfies(segment -> {
            assertThat(segment.includedAtomIds()).containsExactly("real");
            assertThat(segment.evidenceRefs()).containsExactly("commit:real", "file:backend/Scan.java");
        });
    }

    @Test
    void modelFailureDoesNotRetryExternallyAndFallsBack() throws Exception {
        // 模型调用失败时不外层重试，单次失败即回退本地规则，避免 MODEL_ENRICH 阶段长时间卡住。
        UUID userId = UUID.randomUUID();
        when(providerRepository.findByUserIdOrderByDefaultEnabledDescUpdatedAtDesc(userId)).thenReturn(List.of(provider(userId)));
        when(modelGatewayService.callStructured(any(), anyString(), any(com.projectflow.service.ModelTaskType.class))).thenThrow(new IOException("model timeout"));
        ModelSegmentEnricher enricher = new ModelSegmentEnricher(providerRepository, modelGatewayService, new SegmentEvidenceValidator());
        List<String> warnings = new ArrayList<>();

        var result = enricher.enrichWithDiagnostics(userId, atoms(), fallback(), null);

        assertThat(result.mode()).isEqualTo("LOCAL_RULE");
        assertThat(result.segments()).isEqualTo(fallback());
        assertThat(result.modelStatus()).isEqualTo(ModelFailureClassifier.REQUEST_TIMEOUT);
        assertThat(result.fallbackReason()).contains("请求超时");
        verify(modelGatewayService, times(1)).callStructured(any(), anyString(), any(com.projectflow.service.ModelTaskType.class));
    }

    @Test
    void largeAtomListDoesNotProduceUnboundedPrompt() throws Exception {
        // V3.3.4 小阶段修复：验证 prompt 体积防护--大量 atom + 大量文件不应撑爆 prompt。
        UUID userId = UUID.randomUUID();
        when(providerRepository.findByUserIdOrderByDefaultEnabledDescUpdatedAtDesc(userId)).thenReturn(List.of(provider(userId)));
        // 50 个 atom，每个带 30 个文件路径 + commit:hash + 30 个 file: evidenceRefs。
        List<ChangeAtom> bigAtoms = IntStream.range(0, 50).mapToObj(index -> {
            String atomId = "commit" + index;
            List<String> files = IntStream.range(0, 30).mapToObj(f -> "backend/src/pkg/file" + f + "_" + index + ".java").toList();
            List<String> refs = new ArrayList<>();
            refs.add("commit:" + atomId);
            refs.addAll(files.stream().map(f -> "file:" + f).toList());
            return new ChangeAtom(atomId, "feat: change " + index, Instant.parse("2026-07-06T08:00:00Z"),
                List.of("backend"), files, refs);
        }).toList();
        when(modelGatewayService.callStructured(any(), anyString(), any(com.projectflow.service.ModelTaskType.class))).thenReturn(structured("""
            {"segments":[{
              "segmentTitle":"批量变更归并","plainSummary":"整理一批后端开发变化。",
              "includedAtomIds":["commit0"],"mainChanges":["新增功能","修复问题","优化性能"],
              "userVisibleValue":"开发者可追溯批量变更。",
              "evidenceRefs":["commit:commit0"],"affectedFiles":["backend/src/pkg/file0_0.java"],
              "confidence":"HIGH","needsUserReview":true
            }]}
            """));
        ModelSegmentEnricher enricher = new ModelSegmentEnricher(providerRepository, modelGatewayService, new SegmentEvidenceValidator());

        enricher.enrichWithDiagnostics(userId, bigAtoms, fallback(), null);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(modelGatewayService).callStructured(any(), promptCaptor.capture(), any(com.projectflow.service.ModelTaskType.class));
        String prompt = promptCaptor.getValue();
        // prompt 不应超过 45000 字符预算 + 模板文本余量。
        assertThat(prompt.length()).isLessThan(50_000);
        // evidence 只发 commit: 前缀，不发逐个 file: 路径（已在 files= 里列过）。
        assertThat(prompt).doesNotContain("file:backend");
        // files= 每行最多 15 个路径 + +N more 标记（30 文件 - 15 显示 = 15 剩余）。
        assertThat(prompt).contains("+15");
    }

    private AiProvider provider(UUID userId) {
        AiProvider provider = new AiProvider(userId);
        provider.update("DeepSeek", "https://api.example.com", "test-key", "model", AiProviderType.DEEPSEEK, 0.2, 4_000, true, List.of("analysis"));
        return provider;
    }

    private ModelGatewayService.StructuredModelResponse structured(String content) throws IOException {
        return new ModelGatewayService.StructuredModelResponse(content, new ModelOutputAdapter(objectMapper).parse(content));
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
