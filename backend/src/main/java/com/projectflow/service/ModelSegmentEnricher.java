package com.projectflow.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.JsonNode;
import com.projectflow.entity.AiProvider;
import com.projectflow.entity.AiProviderType;
import com.projectflow.entity.EvidenceConfidence;
import com.projectflow.repository.AiProviderRepository;
import com.projectflow.service.DevelopmentSegmentationService.ChangeAtom;
import com.projectflow.service.DevelopmentSegmentationService.SegmentDraft;

@Service
public class ModelSegmentEnricher {
    private static final int MAX_PROMPT_ATOMS = 80;
    private static final int MAX_OUTPUT_TOKENS = 8_000;

    private final AiProviderRepository providerRepository;
    private final ModelGatewayService modelGatewayService;
    private final SegmentEvidenceValidator evidenceValidator;
    private final SegmentQualityGate qualityGate;

    @Autowired
    public ModelSegmentEnricher(
        AiProviderRepository providerRepository,
        ModelGatewayService modelGatewayService,
        SegmentEvidenceValidator evidenceValidator,
        SegmentQualityGate qualityGate
    ) {
        this.providerRepository = providerRepository;
        this.modelGatewayService = modelGatewayService;
        this.evidenceValidator = evidenceValidator;
        this.qualityGate = qualityGate;
    }

    public ModelSegmentEnricher(
        AiProviderRepository providerRepository,
        ModelGatewayService modelGatewayService,
        SegmentEvidenceValidator evidenceValidator
    ) {
        this(providerRepository, modelGatewayService, evidenceValidator, new SegmentQualityGate());
    }

    public List<SegmentDraft> enrich(
        UUID userId,
        List<ChangeAtom> atoms,
        List<SegmentDraft> fallback,
        List<String> warnings
    ) {
        EnrichmentResult result = enrichWithDiagnostics(userId, atoms, fallback);
        if (!result.fallbackReason().isBlank()) {
            warnings.add(result.fallbackReason());
        }
        return result.segments();
    }

    public EnrichmentResult enrichWithDiagnostics(UUID userId, List<ChangeAtom> atoms, List<SegmentDraft> fallback) {
        AiProvider provider = configuredProvider(userId);
        if (provider == null) {
            return new EnrichmentResult(fallback, "LOCAL_RULE", "NOT_CONFIGURED", "", "未配置可用模型，已使用增强本地摘要。");
        }
        if (atoms.isEmpty()) {
            return new EnrichmentResult(fallback, "LOCAL_RULE", "NO_CHANGES", provider.getName(), "没有可供模型分析的新变化。");
        }
        Exception lastFailure = null;
        boolean qualityRejected = false;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                JsonNode json = modelGatewayService.callJson(provider, prompt(atoms, fallback, attempt > 0), MAX_OUTPUT_TOKENS);
                List<SegmentDraft> validated = parse(json, atoms);
                List<String> titles = new ArrayList<>();
                for (SegmentDraft candidate : validated) {
                    var quality = qualityGate.evaluate(candidate, titles);
                    if (!"PASS".equals(quality.status())) {
                        qualityRejected = true;
                        throw new IllegalArgumentException("segment quality rejected: " + quality.reason());
                    }
                    titles.add(candidate.title());
                }
                return new EnrichmentResult(validated, "MODEL", "SUCCESS", provider.getName(), "");
            } catch (Exception exception) {
                lastFailure = exception;
            }
        }
        String status = qualityRejected ? "QUALITY_REJECTED" : modelFailureStatus(lastFailure);
        String reason = qualityRejected ? "模型输出质量不合格，重试后已使用增强本地摘要。"
            : "模型归并失败，已使用增强本地摘要。";
        return new EnrichmentResult(fallback, "LOCAL_RULE", status, provider.getName(), reason);
    }

    private List<SegmentDraft> parse(JsonNode json, List<ChangeAtom> atoms) {
            JsonNode segments = json.path("segments");
            if (!segments.isArray()) {
                throw new IllegalArgumentException("segments must be an array");
            }
            List<SegmentDraft> validated = new ArrayList<>();
            for (JsonNode item : segments) {
                if (!item.path("needsUserReview").asBoolean(false)) {
                    throw new IllegalArgumentException("model segments must require user review");
                }
                SegmentDraft candidate = new SegmentDraft(
                    requiredText(item, "segmentTitle"),
                    requiredText(item, "plainSummary"),
                    textArray(item, "includedAtomIds"),
                    textArray(item, "mainChanges"),
                    requiredText(item, "userVisibleValue"),
                    textArray(item, "evidenceRefs"),
                    textArray(item, "affectedFiles"),
                    confidence(item.path("confidence").asText())
                );
                evidenceValidator.validate(candidate, atoms).ifPresent(validated::add);
            }
            if (validated.isEmpty() || validated.size() > 8) {
                throw new IllegalArgumentException("model segments did not pass evidence validation");
            }
            return validated;
    }

    private AiProvider configuredProvider(UUID userId) {
        return providerRepository.findByUserIdOrderByDefaultEnabledDescUpdatedAtDesc(userId).stream()
            .filter(provider -> provider.getType() != AiProviderType.MOCK)
            .filter(provider -> provider.getApiKey() != null && !provider.getApiKey().isBlank())
            .findFirst()
            .orElse(null);
    }

    private String prompt(List<ChangeAtom> atoms, List<SegmentDraft> fallback, boolean retry) {
        StringBuilder facts = new StringBuilder();
        if (atoms.size() <= MAX_PROMPT_ATOMS) {
            for (ChangeAtom atom : atoms) {
                facts.append("ATOM ").append(atom.id()).append(" | ").append(atom.title()).append(" | files=")
                    .append(String.join(",", atom.files())).append(" | evidence=")
                    .append(String.join(",", atom.evidenceRefs())).append(" | source=").append(atom.sourceType())
                    .append(" | diffHints=").append(String.join(";", atom.diffHints())).append('\n');
            }
        } else {
            for (SegmentDraft segment : fallback) {
                facts.append("RULE_GROUP ").append(segment.title()).append(" | atoms=")
                    .append(String.join(",", segment.includedAtomIds())).append(" | evidence=")
                    .append(String.join(",", segment.evidenceRefs())).append('\n');
            }
        }
        return """
            你是 ProjectFlow V3.3 的开发推进段归并器。只依据给定事实返回严格 JSON：
            {"segments":[{"segmentTitle":"","plainSummary":"","includedAtomIds":[],"mainChanges":[],"userVisibleValue":"","evidenceRefs":[],"affectedFiles":[],"confidence":"HIGH|MEDIUM|LOW","needsUserReview":true}]}
            不能发明 atom、commit 或文件；文档和测试若服务于同一功能，应与功能归为一段；最多返回 8 段。
            标题和摘要必须描述实际开发结果，禁止目录名加“开发推进”或仅报告数量。mainChanges 必须为 3 到 6 条具体变化。
            needsUserReview 必须为 true，模型不能替用户确认项目事实。

            %s

            事实：
            """.formatted(retry ? "上一次输出未通过质量门槛，请改写为具体结果并避免重复标题。" : "") + facts;
    }

    public String configurationKey(UUID userId) {
        AiProvider provider = configuredProvider(userId);
        return provider == null ? "none" : provider.getId() + ":" + provider.getModelName();
    }

    private String modelFailureStatus(Exception failure) {
        if (failure == null) return "CALL_FAILED";
        String message = failure.getMessage() == null ? "" : failure.getMessage().toLowerCase();
        if (message.contains("json") || message.contains("array")) return "JSON_PARSE_FAILED";
        if (message.contains("evidence")) return "EVIDENCE_REJECTED";
        return "CALL_FAILED";
    }

    private String requiredText(JsonNode node, String field) {
        String value = node.path(field).asText("").trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private List<String> textArray(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isArray()) {
            throw new IllegalArgumentException(field + " must be an array");
        }
        List<String> values = new ArrayList<>();
        value.forEach(item -> {
            if (item.isTextual() && !item.asText().isBlank()) {
                values.add(item.asText().trim());
            }
        });
        return values;
    }

    private EvidenceConfidence confidence(String value) {
        try {
            return EvidenceConfidence.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return EvidenceConfidence.LOW;
        }
    }

    public record EnrichmentResult(
        List<SegmentDraft> segments,
        String mode,
        String modelStatus,
        String providerName,
        String fallbackReason
    ) {
    }
}
