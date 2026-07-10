package com.projectflow.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

/**
 * 统一处理结构化模型输出：提取 JSON、修复轻微格式问题、展开常见外层并读取字段别名。
 */
@Component
public class ModelOutputAdapter {
    private static final Pattern CODE_FENCE = Pattern.compile("(?is)```(?:json)?\\s*|```");
    private static final Pattern TRAILING_COMMA = Pattern.compile(",(\\s*[}\\]])");

    private final ObjectMapper objectMapper;

    public ModelOutputAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ParsedOutput parse(String rawContent) throws IOException {
        if (rawContent == null || rawContent.isBlank()) {
            throw new IOException("模型没有返回内容");
        }
        String withoutFence = CODE_FENCE.matcher(rawContent).replaceAll("").trim();
        String extracted = extractJson(withoutFence);
        boolean repaired = !withoutFence.equals(extracted);
        try {
            return new ParsedOutput(objectMapper.readTree(extracted), repaired, rawContent.length());
        } catch (IOException firstFailure) {
            String normalized = TRAILING_COMMA.matcher(extracted).replaceAll("$1");
            if (normalized.equals(extracted)) {
                throw firstFailure;
            }
            return new ParsedOutput(objectMapper.readTree(normalized), true, rawContent.length());
        }
    }

    public List<JsonNode> items(JsonNode root, String... aliases) {
        JsonNode current = root;
        for (int depth = 0; depth < 3 && current != null && current.isObject(); depth++) {
            JsonNode matched = firstPresent(current, aliases);
            if (matched != null) {
                current = matched;
                break;
            }
            if (current.size() != 1) break;
            current = current.elements().next();
        }
        if (current == null || current.isNull() || current.isMissingNode()) return List.of();
        if (current.isArray()) {
            List<JsonNode> result = new ArrayList<>();
            current.forEach(result::add);
            return result;
        }
        return current.isObject() ? List.of(current) : List.of();
    }

    public String text(JsonNode item, String fallback, String... aliases) {
        JsonNode value = firstPresent(item, aliases);
        if (value == null || value.isContainerNode()) return fallback;
        String text = value.asText("").trim();
        return text.isBlank() ? fallback : text;
    }

    public List<String> strings(JsonNode item, String... aliases) {
        JsonNode value = firstPresent(item, aliases);
        if (value == null || value.isNull()) return List.of();
        if (value.isTextual()) {
            return Arrays.stream(value.asText().split("[,，;；]"))
                .map(String::trim).filter(part -> !part.isBlank()).toList();
        }
        if (!value.isArray()) return List.of();
        List<String> result = new ArrayList<>();
        value.forEach(node -> {
            if (node.isValueNode() && !node.asText("").isBlank()) result.add(node.asText().trim());
        });
        return result;
    }

    public boolean bool(JsonNode item, boolean fallback, String... aliases) {
        JsonNode value = firstPresent(item, aliases);
        return value == null ? fallback : value.asBoolean(fallback);
    }

    private JsonNode firstPresent(JsonNode node, String... aliases) {
        if (node == null || !node.isObject()) return null;
        for (String alias : aliases) {
            JsonNode value = node.get(alias);
            if (value != null && !value.isNull() && !value.isMissingNode()) return value;
        }
        return null;
    }

    private String extractJson(String content) throws IOException {
        int objectStart = content.indexOf('{');
        int arrayStart = content.indexOf('[');
        int start;
        char closing;
        if (objectStart < 0 && arrayStart < 0) throw new IOException("模型返回内容中没有 JSON");
        if (arrayStart >= 0 && (objectStart < 0 || arrayStart < objectStart)) {
            start = arrayStart;
            closing = ']';
        } else {
            start = objectStart;
            closing = '}';
        }
        int end = content.lastIndexOf(closing);
        if (end <= start) throw new IOException("模型返回的 JSON 不完整");
        return content.substring(start, end + 1).trim();
    }

    public record ParsedOutput(JsonNode root, boolean repaired, int rawLength) {
    }
}
