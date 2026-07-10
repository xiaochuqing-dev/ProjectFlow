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
        String extracted;
        try {
            extracted = extractJson(withoutFence);
        } catch (IOException exception) {
            ArrayNode recovered = recoverCompleteArrayItems(withoutFence);
            if (!recovered.isEmpty()) {
                return new ParsedOutput(recovered, true, rawContent.length(), true, recovered.size());
            }
            throw exception;
        }
        boolean repaired = !withoutFence.equals(extracted);
        try {
            return new ParsedOutput(objectMapper.readTree(extracted), repaired, rawContent.length(), false, 0);
        } catch (IOException firstFailure) {
            String normalized = TRAILING_COMMA.matcher(extracted).replaceAll("$1");
            if (!normalized.equals(extracted)) {
                try {
                    return new ParsedOutput(objectMapper.readTree(normalized), true, rawContent.length(), false, 0);
                } catch (IOException ignored) {
                    // 继续尝试保留截断根结构中已经完整的条目。
                }
            }
            ArrayNode recovered = recoverCompleteArrayItems(withoutFence);
            if (!recovered.isEmpty()) {
                return new ParsedOutput(recovered, true, rawContent.length(), true, recovered.size());
            }
            throw firstFailure;
        }
    }

    /** 结合 JSON 根结构闭合情况识别疑似截断，解释文字不会被误判。 */
    public boolean likelyTruncated(String rawContent) {
        if (rawContent == null || rawContent.isBlank()) return false;
        String content = CODE_FENCE.matcher(rawContent).replaceAll("").trim();
        int objectStart = content.indexOf('{');
        int arrayStart = content.indexOf('[');
        int start = arrayStart >= 0 && (objectStart < 0 || arrayStart < objectStart) ? arrayStart : objectStart;
        if (start < 0) return false;
        char opening = content.charAt(start);
        char closing = opening == '{' ? '}' : ']';
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int index = start; index < content.length(); index++) {
            char current = content.charAt(index);
            if (inString) {
                if (escaped) escaped = false;
                else if (current == '\\') escaped = true;
                else if (current == '"') inString = false;
                continue;
            }
            if (current == '"') {
                inString = true;
            } else if (current == opening) {
                depth++;
            } else if (current == closing && --depth == 0) {
                return false;
            }
        }
        return true;
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

    /** 从未闭合的根数组中保留已经闭合且可解析的对象，不猜测残缺条目。 */
    private ArrayNode recoverCompleteArrayItems(String content) {
        ArrayNode recovered = objectMapper.createArrayNode();
        int arrayStart = content.indexOf('[');
        if (arrayStart < 0) return recovered;
        boolean inString = false;
        boolean escaped = false;
        int objectDepth = 0;
        int objectStart = -1;
        for (int index = arrayStart + 1; index < content.length(); index++) {
            char current = content.charAt(index);
            if (inString) {
                if (escaped) escaped = false;
                else if (current == '\\') escaped = true;
                else if (current == '"') inString = false;
                continue;
            }
            if (current == '"') {
                inString = true;
            } else if (current == '{') {
                if (objectDepth == 0) objectStart = index;
                objectDepth++;
            } else if (current == '}' && objectDepth > 0) {
                objectDepth--;
                if (objectDepth == 0 && objectStart >= 0) {
                    String candidate = content.substring(objectStart, index + 1);
                    try {
                        JsonNode item = objectMapper.readTree(TRAILING_COMMA.matcher(candidate).replaceAll("$1"));
                        if (item.isObject()) recovered.add(item);
                    } catch (IOException ignored) {
                        // 只保留完整可解析条目。
                    }
                    objectStart = -1;
                }
            }
        }
        return recovered;
    }

    public record ParsedOutput(JsonNode root, boolean repaired, int rawLength, boolean partial, int recoveredItems) {
        public ParsedOutput(JsonNode root, boolean repaired, int rawLength) {
            this(root, repaired, rawLength, false, 0);
        }
    }
}
