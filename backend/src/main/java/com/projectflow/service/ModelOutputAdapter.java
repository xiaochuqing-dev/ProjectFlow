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
        return parse(rawContent, ModelTaskType.LEGACY_STRUCTURED);
    }

    public ParsedOutput parse(String rawContent, ModelTaskType task) throws IOException {
        if (rawContent == null || rawContent.isBlank()) {
            throw new IOException("模型没有返回内容");
        }
        String withoutFence = CODE_FENCE.matcher(rawContent).replaceAll("").trim();
        List<String> candidates = balancedJsonCandidates(withoutFence);
        ParsedCandidate best = null;
        IOException parseFailure = null;
        for (String candidate : candidates) {
            try {
                JsonNode parsed = objectMapper.readTree(candidate);
                JsonNode normalizedRoot = normalizeTargetRoot(task.normalizeRoot(parsed), task);
                int score = task.schemaScore(normalizedRoot, this);
                ParsedCandidate current = new ParsedCandidate(
                    normalizedRoot, score, !withoutFence.equals(candidate) || normalizedRoot != parsed
                );
                if (best == null || current.score() > best.score()) best = current;
            } catch (IOException firstFailure) {
                parseFailure = firstFailure;
                String normalized = TRAILING_COMMA.matcher(candidate).replaceAll("$1");
                if (normalized.equals(candidate)) continue;
                try {
                    JsonNode parsed = objectMapper.readTree(normalized);
                    JsonNode normalizedRoot = normalizeTargetRoot(task.normalizeRoot(parsed), task);
                    int score = task.schemaScore(normalizedRoot, this);
                    ParsedCandidate current = new ParsedCandidate(normalizedRoot, score, true);
                    if (best == null || current.score() > best.score()) best = current;
                } catch (IOException ignored) {
                    // 继续评估其他候选，并在最后尝试目标感知的部分恢复。
                }
            }
        }
        ArrayNode recovered = recoverCompleteArrayItems(withoutFence, task);
        if (!recovered.isEmpty() && likelyTruncated(withoutFence)) {
            return new ParsedOutput(recovered, true, rawContent.length(), true, recovered.size());
        }
        if (best != null) {
            return new ParsedOutput(best.root(), best.repaired(), rawContent.length(), false, 0);
        }
        if (!recovered.isEmpty()) return new ParsedOutput(recovered, true, rawContent.length(), true, recovered.size());
        if (candidates.isEmpty()) throw new IOException("模型返回内容中没有完整 JSON");
        throw parseFailure == null ? new IOException("模型返回的 JSON 无法解析") : parseFailure;
    }

    /** 结合 JSON 根结构闭合情况识别疑似截断，解释文字不会被误判。 */
    public boolean likelyTruncated(String rawContent) {
        if (rawContent == null || rawContent.isBlank()) return false;
        String content = CODE_FENCE.matcher(rawContent).replaceAll("").trim();
        boolean inString = false;
        boolean escaped = false;
        for (int index = 0; index < content.length(); index++) {
            char current = content.charAt(index);
            if (inString) {
                if (escaped) escaped = false;
                else if (current == '\\') escaped = true;
                else if (current == '"') inString = false;
                continue;
            }
            if (current == '"') {
                inString = true;
                continue;
            }
            if (current != '{' && current != '[') continue;
            if (balancedEnd(content, index) < 0) return true;
        }
        return false;
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

    private List<String> balancedJsonCandidates(String content) {
        List<String> candidates = new ArrayList<>();
        boolean inString = false;
        boolean escaped = false;
        for (int start = 0; start < content.length(); start++) {
            char opening = content.charAt(start);
            if (inString) {
                if (escaped) escaped = false;
                else if (opening == '\\') escaped = true;
                else if (opening == '"') inString = false;
                continue;
            }
            if (opening == '"') {
                inString = true;
                continue;
            }
            if (opening != '{' && opening != '[') continue;
            int end = balancedEnd(content, start);
            if (end > start) candidates.add(content.substring(start, end + 1).trim());
        }
        return candidates;
    }

    private int balancedEnd(String content, int start) {
        List<Character> stack = new ArrayList<>();
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
            if (current == '"') inString = true;
            else if (current == '{' || current == '[') stack.add(current);
            else if (current == '}' || current == ']') {
                if (stack.isEmpty()) return -1;
                char expected = current == '}' ? '{' : '[';
                if (stack.get(stack.size() - 1) != expected) return -1;
                stack.remove(stack.size() - 1);
                if (stack.isEmpty()) return index;
            }
        }
        return -1;
    }

    /** 从多个未闭合数组中选择最像目标 Schema 的完整对象集合，不再默认使用第一个数组。 */
    private ArrayNode recoverCompleteArrayItems(String content, ModelTaskType task) {
        ArrayNode best = objectMapper.createArrayNode();
        int bestScore = 0;
        for (int arrayStart = content.indexOf('['); arrayStart >= 0; arrayStart = content.indexOf('[', arrayStart + 1)) {
            ArrayNode recovered = recoverArrayAt(content, arrayStart);
            int score = task.schemaScore(recovered, this);
            if (!recovered.isEmpty() && (score > bestScore || best.isEmpty())) {
                best = recovered;
                bestScore = score;
            }
        }
        return bestScore > 0 || task == ModelTaskType.LEGACY_STRUCTURED ? best : objectMapper.createArrayNode();
    }

    private ArrayNode recoverArrayAt(String content, int arrayStart) {
        ArrayNode recovered = objectMapper.createArrayNode();
        int arrayEnd = balancedEnd(content, arrayStart);
        int limit = arrayEnd < 0 ? content.length() : arrayEnd;
        for (int index = arrayStart + 1; index < limit; index++) {
            if (content.charAt(index) != '{') continue;
            int end = balancedEnd(content, index);
            if (end < 0 || end > limit) continue;
            try {
                JsonNode item = objectMapper.readTree(TRAILING_COMMA.matcher(content.substring(index, end + 1)).replaceAll("$1"));
                if (item.isObject()) recovered.add(item);
            } catch (IOException ignored) {
                // 只保留完整且可解析的对象。
            }
            index = end;
        }
        return recovered;
    }

    private JsonNode normalizeTargetRoot(JsonNode root, ModelTaskType task) {
        if (!task.collectionOutput()) return root;
        JsonNode collection = findTargetCollection(root, task, 0);
        return collection == null ? root : collection;
    }

    private JsonNode findTargetCollection(JsonNode node, ModelTaskType task, int depth) {
        if (node == null || depth > 5) return null;
        if (node.isArray()) {
            for (JsonNode item : node) {
                if (matchesAnyField(item, task.itemFields())) return node;
            }
        }
        if (node.isObject()) {
            if (matchesAnyField(node, task.itemFields())) {
                ArrayNode single = objectMapper.createArrayNode();
                single.add(node);
                return single;
            }
            for (String alias : task.rootAliases()) {
                JsonNode found = findTargetCollection(node.get(alias), task, depth + 1);
                if (found != null) return found;
            }
            var fields = node.fields();
            while (fields.hasNext()) {
                JsonNode found = findTargetCollection(fields.next().getValue(), task, depth + 1);
                if (found != null) return found;
            }
        }
        return null;
    }

    private boolean matchesAnyField(JsonNode node, List<String> fields) {
        return node != null && node.isObject() && fields.stream().anyMatch(node::has);
    }

    private record ParsedCandidate(JsonNode root, int score, boolean repaired) {}

    public record ParsedOutput(JsonNode root, boolean repaired, int rawLength, boolean partial, int recoveredItems) {
        public ParsedOutput(JsonNode root, boolean repaired, int rawLength) {
            this(root, repaired, rawLength, false, 0);
        }
    }
}
