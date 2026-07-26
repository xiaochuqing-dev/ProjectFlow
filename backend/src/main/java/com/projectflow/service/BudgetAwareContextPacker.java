package com.projectflow.service;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.projectflow.dto.ProjectUnderstandingDtos.ContextPackingDiagnostics;

@Component
public class BudgetAwareContextPacker {
    private static final Map<String, SectionPolicy> POLICIES = Map.of(
        "projectIntake", new SectionPolicy(1_200, 4_000),
        "manifests", new SectionPolicy(1_000, 5_000),
        "documents", new SectionPolicy(2_500, 13_000),
        "structure", new SectionPolicy(2_500, 13_000),
        "git", new SectionPolicy(1_000, 6_000),
        "historicalCoverage", new SectionPolicy(1_000, 5_000),
        "unknownsAndConflicts", new SectionPolicy(800, 4_000),
        "toolResults", new SectionPolicy(1_500, 12_000)
    );

    private final ObjectMapper objectMapper;
    private final SensitiveContentRedactor redactor;

    @Value("${projectflow.understanding.max-model-prompt-chars:48000}")
    private int maxChars;

    public BudgetAwareContextPacker(ObjectMapper objectMapper, SensitiveContentRedactor redactor) {
        this.objectMapper = objectMapper;
        this.redactor = redactor;
    }

    public PackedContext pack(Map<String, ?> sections) {
        return pack(sections, maxChars);
    }

    public PackedContext pack(Map<String, ?> sections, int requestedMaxChars) {
        int limit = Math.max(8_000, requestedMaxChars);
        LinkedHashMap<String, JsonNode> source = new LinkedHashMap<>();
        sections.forEach((name, value) -> {
            JsonNode node = sanitize(objectMapper.valueToTree(value));
            if (!isEmpty(node)) source.put(name, node);
        });

        Map<String, Integer> budgets = allocateBudgets(source, limit);
        ObjectNode packed = JsonNodeFactory.instance.objectNode();
        Map<String, Integer> selected = new LinkedHashMap<>();
        Map<String, Integer> dropped = new LinkedHashMap<>();
        Map<String, Integer> chars = new LinkedHashMap<>();
        List<String> reasons = new ArrayList<>();
        source.forEach((name, node) -> {
            JsonNode fitted = fit(node, budgets.getOrDefault(name, 1_000));
            packed.set(name, fitted);
            int originalCount = itemCount(node);
            int selectedCount = itemCount(fitted);
            selected.put(name, selectedCount);
            dropped.put(name, Math.max(0, originalCount - selectedCount));
            chars.put(name, serializedLength(fitted));
            if (selectedCount < originalCount || hasTruncatedText(node, fitted)) {
                reasons.add(name + " 达到类别预算，按完整 JSON 项目有界收缩");
            }
        });

        shrinkToGlobalLimit(packed, limit, selected, dropped, chars, reasons);
        String json = serialize(packed);
        boolean valid = isValidJson(json);
        return new PackedContext(
            json,
            new ContextPackingDiagnostics(
                limit,
                json.length(),
                Map.copyOf(selected),
                Map.copyOf(dropped),
                Map.copyOf(chars),
                List.copyOf(reasons),
                valid
            )
        );
    }

    private Map<String, Integer> allocateBudgets(Map<String, JsonNode> sections, int limit) {
        int overheadReserve = Math.min(4_096, Math.max(1_024, limit / 10));
        int available = Math.max(4_000, limit - overheadReserve);
        Map<String, Integer> result = new LinkedHashMap<>();
        int minimumTotal = 0;
        for (String name : sections.keySet()) {
            int minimum = policy(name).minimum();
            result.put(name, minimum);
            minimumTotal += minimum;
        }
        if (minimumTotal > available && minimumTotal > 0) {
            double ratio = (double) available / minimumTotal;
            result.replaceAll((name, value) -> Math.max(300, (int) Math.floor(value * ratio)));
        }
        int used = result.values().stream().mapToInt(Integer::intValue).sum();
        int remaining = Math.max(0, available - used);
        boolean changed = true;
        while (remaining > 0 && changed) {
            changed = false;
            for (String name : sections.keySet()) {
                int current = result.get(name);
                int cap = policy(name).maximum();
                if (current >= cap) continue;
                int addition = Math.min(Math.min(512, cap - current), remaining);
                result.put(name, current + addition);
                remaining -= addition;
                changed = true;
                if (remaining == 0) break;
            }
        }
        return result;
    }

    private JsonNode fit(JsonNode node, int budget) {
        if (node == null || node.isNull()) return JsonNodeFactory.instance.nullNode();
        if (serializedLength(node) <= budget) return node;
        if (node.isTextual()) {
            String value = node.asText("");
            int maxText = Math.max(0, budget - 4);
            return TextNode.valueOf(value.length() <= maxText ? value : value.substring(0, maxText));
        }
        if (node.isArray()) {
            ArrayNode result = JsonNodeFactory.instance.arrayNode();
            for (JsonNode item : node) {
                int remaining = budget - serializedLength(result) - 2;
                if (remaining < 16) break;
                JsonNode fitted = fit(item, remaining);
                result.add(fitted);
                if (serializedLength(result) > budget) {
                    result.remove(result.size() - 1);
                    break;
                }
            }
            return result;
        }
        if (node.isObject()) {
            ObjectNode result = JsonNodeFactory.instance.objectNode();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                int remaining = budget - serializedLength(result) - field.getKey().length() - 6;
                if (remaining < 16) break;
                result.set(field.getKey(), fit(field.getValue(), remaining));
                if (serializedLength(result) > budget) {
                    result.remove(field.getKey());
                    break;
                }
            }
            return result;
        }
        return node;
    }

    private JsonNode sanitize(JsonNode node) {
        if (node == null || node.isNull()) return JsonNodeFactory.instance.nullNode();
        if (node.isTextual()) return TextNode.valueOf(redactor.redact(node.asText("")));
        if (node.isArray()) {
            ArrayNode result = JsonNodeFactory.instance.arrayNode();
            node.forEach(item -> result.add(sanitize(item)));
            return result;
        }
        if (node.isObject()) {
            ObjectNode result = JsonNodeFactory.instance.objectNode();
            node.fields().forEachRemaining(entry -> result.set(entry.getKey(), sanitize(entry.getValue())));
            return result;
        }
        return node.deepCopy();
    }

    private void shrinkToGlobalLimit(
        ObjectNode packed,
        int limit,
        Map<String, Integer> selected,
        Map<String, Integer> dropped,
        Map<String, Integer> chars,
        List<String> reasons
    ) {
        while (serializedLength(packed) > limit) {
            String largest = null;
            int largestChars = 0;
            Iterator<Map.Entry<String, JsonNode>> fields = packed.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                int length = serializedLength(field.getValue());
                if (length > largestChars) {
                    largest = field.getKey();
                    largestChars = length;
                }
            }
            if (largest == null || largestChars <= 32) break;
            JsonNode current = packed.get(largest);
            JsonNode smaller = fit(current, Math.max(16, largestChars - Math.max(128, largestChars / 5)));
            if (serializedLength(smaller) >= largestChars) break;
            packed.set(largest, smaller);
            int removed = Math.max(0, itemCount(current) - itemCount(smaller));
            selected.put(largest, itemCount(smaller));
            dropped.merge(largest, removed, Integer::sum);
            chars.put(largest, serializedLength(smaller));
            String reason = largest + " 为满足全局预算再次按完整 JSON 项目收缩";
            if (!reasons.contains(reason)) reasons.add(reason);
        }
    }

    private boolean isValidJson(String value) {
        try {
            objectMapper.readTree(value);
            return true;
        } catch (JsonProcessingException exception) {
            return false;
        }
    }

    private String serialize(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("模型上下文无法安全序列化", exception);
        }
    }

    private int serializedLength(JsonNode node) {
        return serialize(node).length();
    }

    private static int itemCount(JsonNode node) {
        if (node == null || node.isNull()) return 0;
        if (node.isArray() || node.isObject()) return node.size();
        return 1;
    }

    private static boolean hasTruncatedText(JsonNode original, JsonNode fitted) {
        if (original == null || fitted == null) return false;
        if (original.isTextual() && fitted.isTextual()) return fitted.asText("").length() < original.asText("").length();
        return false;
    }

    private static boolean isEmpty(JsonNode node) {
        return node == null || node.isNull() || (node.isContainerNode() && node.isEmpty());
    }

    private static SectionPolicy policy(String name) {
        return POLICIES.getOrDefault(name, new SectionPolicy(800, 4_000));
    }

    private record SectionPolicy(int minimum, int maximum) {
    }

    public record PackedContext(String json, ContextPackingDiagnostics diagnostics) {
    }
}
