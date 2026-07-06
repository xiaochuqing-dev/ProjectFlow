package com.projectflow.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

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

    public ModelSegmentEnricher(
        AiProviderRepository providerRepository,
        ModelGatewayService modelGatewayService,
        SegmentEvidenceValidator evidenceValidator
    ) {
        this.providerRepository = providerRepository;
        this.modelGatewayService = modelGatewayService;
        this.evidenceValidator = evidenceValidator;
    }

    public List<SegmentDraft> enrich(
        UUID userId,
        List<ChangeAtom> atoms,
        List<SegmentDraft> fallback,
        List<String> warnings
    ) {
        AiProvider provider = configuredProvider(userId);
        if (provider == null || atoms.isEmpty()) {
            return fallback;
        }
        try {
            JsonNode json = modelGatewayService.callJson(provider, prompt(atoms, fallback), MAX_OUTPUT_TOKENS);
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
        } catch (Exception exception) {
            warnings.add("模型归并失败，已使用本地规则结果。");
            return fallback;
        }
    }

    private AiProvider configuredProvider(UUID userId) {
        return providerRepository.findByUserIdOrderByDefaultEnabledDescUpdatedAtDesc(userId).stream()
            .filter(provider -> provider.getType() != AiProviderType.MOCK)
            .filter(provider -> provider.getApiKey() != null && !provider.getApiKey().isBlank())
            .findFirst()
            .orElse(null);
    }

    private String prompt(List<ChangeAtom> atoms, List<SegmentDraft> fallback) {
        StringBuilder facts = new StringBuilder();
        if (atoms.size() <= MAX_PROMPT_ATOMS) {
            for (ChangeAtom atom : atoms) {
                facts.append("ATOM ").append(atom.id()).append(" | ").append(atom.title()).append(" | files=")
                    .append(String.join(",", atom.files())).append(" | evidence=")
                    .append(String.join(",", atom.evidenceRefs())).append('\n');
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
            needsUserReview 必须为 true，模型不能替用户确认项目事实。

            事实：
            """ + facts;
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
}
