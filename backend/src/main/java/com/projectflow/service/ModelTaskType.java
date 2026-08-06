package com.projectflow.service;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 所有真实模型入口的统一注册表。任务定义只描述输出目标和预算特征，
 * Provider 能力与最终请求参数由统一策略计算，业务 Service 不再硬编码参数。
 */
public enum ModelTaskType {
    PROVIDER_CONNECTION_TEST(
        "Provider 连接测试", 128, 256, 0.0, false,
        List.of(), List.of("ok"), List.of(),
        "{\"ok\":true}"
    ),
    PROVIDER_PROJECTFLOW_COMPATIBILITY_TEST(
        "Provider ProjectFlow 最小任务测试", 512, 1_024, 0.0, false,
        List.of(), List.of("summary", "architecture"), List.of(),
        "{\"summary\":\"\",\"architecture\":\"\"}"
    ),
    DEVELOPMENT_SEGMENT_MERGE(
        "分析新变化 / 开发推进段归并", 6_000, 20_000, 0.2, true,
        List.of("segments", "developmentSegments", "development_segments", "developmentProgressSegments", "development_progress_segments", "cards", "items", "results"),
        List.of(), List.of("segmentTitle", "segment_title", "title", "plainSummary", "plain_summary", "summary"),
        "{\"segments\":[{\"segmentTitle\":\"\",\"plainSummary\":\"\",\"sourceIndexes\":[\"S1\"]}]}"
    ),
    PROJECT_ANALYSIS(
        "整体项目分析", 5_000, 16_000, 0.2, false,
        List.of(), List.of("summary", "architecture"), List.of(),
        "{\"summary\":\"\",\"architecture\":\"\",\"modules\":[],\"risks\":[],\"importantFiles\":[],\"evidence\":[],\"limitations\":[],\"confidence\":\"\"}"
    ),
    FILE_ANALYSIS(
        "单文件分析", 3_000, 12_000, 0.2, false,
        List.of(), List.of("summary", "role"), List.of(),
        "{\"path\":\"\",\"fileType\":\"\",\"role\":\"\",\"summary\":\"\",\"importance\":\"\",\"riskLevel\":\"\",\"riskNotes\":\"\",\"evidence\":[],\"relatedFiles\":[],\"limitations\":[],\"confidence\":\"\"}"
    ),
    CAPABILITY_INTERPRETATION(
        "能力解读", 1_800, 6_000, 0.2, false,
        List.of(), List.of("summary", "problem", "value"), List.of(),
        "{\"summary\":\"\",\"problem\":\"\",\"value\":\"\",\"readme\":\"\",\"resume\":\"\",\"interview\":\"\"}"
    ),
    PROJECT_CAPABILITY_ANALYSIS(
        "分析项目能力", 7_000, 20_000, 0.2, true,
        List.of("capabilities", "capabilityCards", "capability_cards", "cards", "items", "results"),
        List.of(), List.of("name", "capabilityName", "capability_name", "title", "summary", "plain_summary", "description"),
        "{\"capabilities\":[{\"name\":\"\",\"summary\":\"\",\"problemSolved\":\"\",\"featureEntry\":\"\",\"sourceIndexes\":[\"S1\"],\"readme\":\"\",\"resume\":\"\",\"interview\":\"\"}]}"
    ),
    PROJECT_TIMELINE_PERIOD_SUMMARY(
        "项目历程时间段摘要", 4_000, 16_000, 0.1, false,
        List.of(), List.of("periodSummary", "themes", "ungroupedFactIds"), List.of(),
        "{\"periodSummary\":\"\",\"themes\":[{\"title\":\"\",\"summary\":\"\",\"factIds\":[\"uuid\"]}],\"ungroupedFactIds\":[]}"
    ),
    PROJECT_TIMELINE_LIFECYCLE_SUMMARY(
        "项目完整历程摘要", 4_000, 16_000, 0.1, false,
        List.of(), List.of("periodSummary", "stages", "ungroupedMonthKeys"), List.of(),
        "{\"periodSummary\":\"\",\"stages\":[{\"title\":\"\",\"summary\":\"\",\"monthKeys\":[\"2026-07\"]}],\"ungroupedMonthKeys\":[]}"
    ),
    PROJECT_CAPABILITY_MAP_BOOTSTRAP(
        "全生命周期能力地图初始化", 7_000, 20_000, 0.1, false,
        List.of(), List.of("operations", "noCapabilityChangeFactIds", "attentionFacts"), List.of(),
        "{\"operations\":[{\"type\":\"NEW_CAPABILITY\",\"temporaryKey\":\"C1\",\"canonicalName\":\"\",\"summary\":\"\",\"problemSolved\":\"\",\"longTermValue\":\"\",\"productAreas\":[],\"factIds\":[\"uuid\"],\"evolutionTitle\":\"\",\"evolutionSummary\":\"\"}],\"noCapabilityChangeFactIds\":[],\"attentionFacts\":[]}"
    ),
    PROJECT_CAPABILITY_MAP_INCREMENTAL(
        "全生命周期能力地图增量维护", 6_000, 20_000, 0.1, false,
        List.of(), List.of("operations", "noCapabilityChangeFactIds", "attentionFacts"), List.of(),
        "{\"operations\":[{\"type\":\"ENHANCE_CAPABILITY\",\"capabilityId\":\"uuid\",\"factIds\":[\"uuid\"],\"summary\":\"\",\"evolutionTitle\":\"\",\"evolutionSummary\":\"\"}],\"noCapabilityChangeFactIds\":[],\"attentionFacts\":[]}"
    ),
    PROJECT_UNDERSTANDING_SNAPSHOT(
        "通用证据 Scout 与动态项目档案", 5_000, 16_000, 0.1, false,
        List.of(), List.of("semanticScout", "dynamicProfile", "unknowns"), List.of(),
        "{\"semanticScout\":{\"projectShapeHypotheses\":[],\"evidenceSourceAssessments\":[],\"applicableDimensions\":[],\"capabilityDecisions\":[{\"capability\":\"\",\"decision\":\"REQUEST|SKIP\",\"skipReason\":\"\",\"informationGap\":\"\",\"expectedEvidenceValue\":\"\",\"targetEvidenceIds\":[],\"whyExistingEvidenceIsInsufficient\":\"\"}],\"recommendedToolCalls\":[],\"unknowns\":[],\"skipCandidates\":[],\"potentialConflicts\":[],\"currentnessWarnings\":[]},\"dynamicProfile\":{\"summary\":\"\",\"sections\":[]},\"unknowns\":[],\"selfCheck\":{}}"
    ),
    PROJECT_UNDERSTANDING_FINAL_SYNTHESIS(
        "通用证据最终归纳", 4_000, 12_000, 0.1, false,
        List.of(), List.of("dynamicProfile", "unknowns"), List.of(),
        "{\"dynamicProfile\":{\"summary\":\"\",\"sections\":[]},\"unknowns\":[],\"conflicts\":[],\"stageTwoChanges\":[],\"selfCheck\":{}}"
    ),
    PROJECT_HISTORY_SYNTHESIS(
        "项目历程变化故事与篇章归纳", 6_000, 20_000, 0.1, false,
        List.of(), List.of("stories", "chapters"), List.of(),
        "{\"stories\":[{\"storyId\":\"\",\"humanTitle\":\"\",\"oneSentenceSummary\":\"\",\"role\":\"PRIMARY\",\"reason\":\"\",\"reasonEvidenceRefs\":[],\"conflicts\":[],\"unknowns\":[]}],\"chapters\":[{\"chapterId\":\"\",\"title\":\"\",\"summary\":\"\",\"storyRefs\":[]}] }"
    ),
    LEGACY_STRUCTURED(
        "兼容结构化调用", 2_048, 20_000, 0.2, false,
        List.of("items", "results"), List.of(), List.of(), "{}"
    );

    private final String entryPoint;
    private final int baseOutputTokens;
    private final int maximumUsefulOutputTokens;
    private final double recommendedTemperature;
    private final boolean collectionOutput;
    private final List<String> rootAliases;
    private final List<String> requiredObjectFields;
    private final List<String> itemFields;
    private final String minimalSchema;

    ModelTaskType(
        String entryPoint,
        int baseOutputTokens,
        int maximumUsefulOutputTokens,
        double recommendedTemperature,
        boolean collectionOutput,
        List<String> rootAliases,
        List<String> requiredObjectFields,
        List<String> itemFields,
        String minimalSchema
    ) {
        this.entryPoint = entryPoint;
        this.baseOutputTokens = baseOutputTokens;
        this.maximumUsefulOutputTokens = maximumUsefulOutputTokens;
        this.recommendedTemperature = recommendedTemperature;
        this.collectionOutput = collectionOutput;
        this.rootAliases = rootAliases;
        this.requiredObjectFields = requiredObjectFields;
        this.itemFields = itemFields;
        this.minimalSchema = minimalSchema;
    }

    public String entryPoint() { return entryPoint; }
    public int baseOutputTokens() { return baseOutputTokens; }
    public int maximumUsefulOutputTokens() { return maximumUsefulOutputTokens; }
    public double recommendedTemperature() { return recommendedTemperature; }
    public boolean collectionOutput() { return collectionOutput; }
    public List<String> rootAliases() { return rootAliases; }
    public List<String> requiredObjectFields() { return requiredObjectFields; }
    public List<String> itemFields() { return itemFields; }
    public String minimalSchema() { return minimalSchema; }

    public int schemaScore(JsonNode root, ModelOutputAdapter adapter) {
        if (root == null || root.isNull() || root.isMissingNode()) return 0;
        if (this == LEGACY_STRUCTURED) return root.isObject() || root.isArray() ? 1 : 0;
        if (collectionOutput) {
            List<JsonNode> items = adapter.items(root, rootAliases.toArray(String[]::new));
            if (items.isEmpty()) return 0;
            int fieldMatches = items.stream().limit(3).mapToInt(this::matchingItemFields).sum();
            return 20 + Math.min(items.size(), 8) + fieldMatches * 3;
        }
        JsonNode normalized = normalizeObjectRoot(root);
        if (!normalized.isObject()) return 0;
        int matches = 0;
        for (String field : requiredObjectFields) {
            JsonNode value = normalized.get(field);
            if (value != null && !value.isNull() && !value.isMissingNode()) matches++;
        }
        return requiredObjectFields.isEmpty() ? 1 : matches * 10;
    }

    public boolean schemaMatches(JsonNode root, ModelOutputAdapter adapter) {
        if (this == LEGACY_STRUCTURED) return root != null && (root.isObject() || root.isArray());
        if (collectionOutput) {
            List<JsonNode> items = adapter.items(root, rootAliases.toArray(String[]::new));
            return !items.isEmpty() && items.stream().anyMatch(item -> matchingItemFields(item) > 0);
        }
        JsonNode normalized = normalizeObjectRoot(root);
        if (this == PROJECT_UNDERSTANDING_SNAPSHOT) {
            JsonNode scout = normalized.path("semanticScout");
            JsonNode profile = normalized.path("dynamicProfile");
            return normalized.isObject()
                && scout.isObject()
                && scout.path("projectShapeHypotheses").isArray()
                && scout.path("evidenceSourceAssessments").isArray()
                && scout.path("applicableDimensions").isArray()
                && scout.path("toolRequests").isArray()
                && scout.path("unknowns").isArray()
                && profile.isObject()
                && profile.path("sections").isArray()
                && normalized.path("unknowns").isArray()
                && normalized.path("selfCheck").isObject();
        }
        if (this == PROJECT_UNDERSTANDING_FINAL_SYNTHESIS) {
            JsonNode profile = normalized.path("dynamicProfile");
            return normalized.isObject()
                && profile.isObject()
                && profile.path("sections").isArray()
                && normalized.path("unknowns").isArray()
                && normalized.path("conflicts").isArray()
                && normalized.path("stageTwoChanges").isArray()
                && normalized.path("selfCheck").isObject();
        }
        return requiredObjectFields.stream().allMatch(normalized::has);
    }

    public List<String> schemaGaps(JsonNode root) {
        JsonNode normalized = normalizeObjectRoot(root);
        if (this == PROJECT_UNDERSTANDING_SNAPSHOT) {
            return missing(
                normalized,
                List.of(
                    "semanticScout",
                    "semanticScout.projectShapeHypotheses",
                    "semanticScout.evidenceSourceAssessments",
                    "semanticScout.applicableDimensions",
                    "semanticScout.toolRequests",
                    "semanticScout.unknowns",
                    "dynamicProfile",
                    "dynamicProfile.sections",
                    "unknowns",
                    "selfCheck"
                )
            );
        }
        if (this == PROJECT_UNDERSTANDING_FINAL_SYNTHESIS) {
            return missing(
                normalized,
                List.of(
                    "dynamicProfile",
                    "dynamicProfile.sections",
                    "unknowns",
                    "conflicts",
                    "stageTwoChanges",
                    "selfCheck"
                )
            );
        }
        return requiredObjectFields.stream().filter(field -> !normalized.has(field)).toList();
    }

    public JsonNode normalizeRoot(JsonNode root) {
        if (this == LEGACY_STRUCTURED) return root;
        return collectionOutput ? root : normalizeObjectRoot(root);
    }

    private JsonNode normalizeObjectRoot(JsonNode root) {
        JsonNode current = root;
        for (int depth = 0; depth < 3 && current != null && current.isObject(); depth++) {
            boolean matched = requiredObjectFields.stream().anyMatch(current::has);
            if (matched || current.size() != 1) return normalizeDiagnosticDefaults(current);
            current = current.elements().next();
        }
        return normalizeDiagnosticDefaults(current == null ? root : current);
    }

    /**
     * Self-check is non-authoritative diagnostics: engineering validation still
     * owns every reference and eligibility decision. Older/compatible models
     * may omit this empty object, so adding it locally avoids a paid semantic
     * rewrite without inventing a project claim. V8 capabilityDecisions REQUEST
     * is an explicit model decision equivalent to toolRequests; an empty
     * compatibility array lets the shared normalizer consume those decisions
     * without engineering code choosing a capability.
     */
    private JsonNode normalizeDiagnosticDefaults(JsonNode root) {
        if (root == null || !root.isObject()) return root;
        if (this != PROJECT_UNDERSTANDING_SNAPSHOT
            && this != PROJECT_UNDERSTANDING_FINAL_SYNTHESIS) {
            return root;
        }
        ObjectNode candidate = (ObjectNode) root;
        boolean changed = false;
        if (this == PROJECT_UNDERSTANDING_SNAPSHOT
            && !root.path("semanticScout").isObject()
            && root.path("projectShapeHypotheses").isArray()
            && root.path("evidenceSourceAssessments").isArray()
            && root.path("applicableDimensions").isArray()) {
            ObjectNode flattenedScout = candidate.deepCopy();
            JsonNode existingProfile = flattenedScout.remove("dynamicProfile");
            JsonNode existingSelfCheck = flattenedScout.remove("selfCheck");
            JsonNode existingUnknowns = flattenedScout.path("unknowns").deepCopy();
            ObjectNode wrapped = JsonNodeFactory.instance.objectNode();
            wrapped.set("semanticScout", flattenedScout);
            if (existingProfile != null && existingProfile.isObject()) {
                wrapped.set("dynamicProfile", existingProfile);
            } else {
                ObjectNode emptyProfile = wrapped.putObject("dynamicProfile");
                emptyProfile.put("summary", "");
                emptyProfile.putArray("sections");
            }
            if (existingUnknowns.isArray()) wrapped.set("unknowns", existingUnknowns);
            else wrapped.putArray("unknowns");
            if (existingSelfCheck != null && existingSelfCheck.isObject()) {
                wrapped.set("selfCheck", existingSelfCheck);
            } else {
                wrapped.putObject("selfCheck");
            }
            candidate = wrapped;
            changed = true;
        }
        boolean missingSelfCheck = !candidate.path("selfCheck").isObject();
        boolean canReuseScoutUnknowns = this == PROJECT_UNDERSTANDING_SNAPSHOT
            && !candidate.path("unknowns").isArray()
            && candidate.path("semanticScout").path("unknowns").isArray();
        boolean canUseCapabilityDecisionEncoding = this == PROJECT_UNDERSTANDING_SNAPSHOT
            && candidate.path("semanticScout").isObject()
            && !candidate.path("semanticScout").path("toolRequests").isArray()
            && candidate.path("semanticScout").path("capabilityDecisions").isArray();
        if (!missingSelfCheck && !canReuseScoutUnknowns && !canUseCapabilityDecisionEncoding) {
            return candidate;
        }
        ObjectNode normalized = changed ? candidate : candidate.deepCopy();
        if (missingSelfCheck) normalized.putObject("selfCheck");
        if (canReuseScoutUnknowns) {
            normalized.set("unknowns", normalized.path("semanticScout").path("unknowns").deepCopy());
        }
        if (canUseCapabilityDecisionEncoding) {
            ((ObjectNode) normalized.path("semanticScout")).putArray("toolRequests");
        }
        return normalized;
    }

    private int matchingItemFields(JsonNode item) {
        if (item == null || !item.isObject()) return 0;
        int matches = 0;
        for (String field : itemFields) if (item.has(field)) matches++;
        return matches;
    }

    private static List<String> missing(JsonNode root, List<String> paths) {
        return paths.stream().filter(path -> {
            JsonNode value = root;
            for (String segment : path.split("\\.")) value = value.path(segment);
            if (path.endsWith("semanticScout") || path.endsWith("dynamicProfile") || path.endsWith("selfCheck")) {
                return !value.isObject();
            }
            return !value.isArray();
        }).toList();
    }
}
