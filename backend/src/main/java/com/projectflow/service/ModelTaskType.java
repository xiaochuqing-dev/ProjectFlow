package com.projectflow.service;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

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
        "{\"semanticScout\":{\"projectShapeHypotheses\":[],\"evidenceSourceAssessments\":[],\"applicableDimensions\":[],\"recommendedToolCalls\":[],\"unknowns\":[],\"skipCandidates\":[],\"potentialConflicts\":[],\"currentnessWarnings\":[]},\"dynamicProfile\":{\"summary\":\"\",\"sections\":[]},\"unknowns\":[]}"
    ),
    PROJECT_UNDERSTANDING_FINAL_SYNTHESIS(
        "通用证据最终归纳", 4_000, 12_000, 0.1, false,
        List.of(), List.of("dynamicProfile", "unknowns"), List.of(),
        "{\"dynamicProfile\":{\"summary\":\"\",\"sections\":[]},\"unknowns\":[]}"
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
        return requiredObjectFields.stream().allMatch(normalized::has);
    }

    public JsonNode normalizeRoot(JsonNode root) {
        if (this == LEGACY_STRUCTURED) return root;
        return collectionOutput ? root : normalizeObjectRoot(root);
    }

    private JsonNode normalizeObjectRoot(JsonNode root) {
        JsonNode current = root;
        for (int depth = 0; depth < 3 && current != null && current.isObject(); depth++) {
            boolean matched = requiredObjectFields.stream().anyMatch(current::has);
            if (matched || current.size() != 1) return current;
            current = current.elements().next();
        }
        return current == null ? root : current;
    }

    private int matchingItemFields(JsonNode item) {
        if (item == null || !item.isObject()) return 0;
        int matches = 0;
        for (String field : itemFields) if (item.has(field)) matches++;
        return matches;
    }
}
