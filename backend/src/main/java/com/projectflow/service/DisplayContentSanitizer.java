package com.projectflow.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * V3.3.4 小阶段修复：主视图可读性过滤机制。
 *
 * ProjectFlow 主视图是"产品理解层"，不是"证据原文层"。任何不能让普通开发者一眼理解
 * "这次实际改了什么"的内容，都不能直接出现在主视图 title / plainSummary / mainChanges /
 * userVisibleValue / 能力卡片 name / summary / README 等字段。
 *
 * 本清洗器对所有进入主视图的内容统一执行：
 * 1. 去除原始证据标记：commit:hash、file:path、agent-result:path、evidence 编号、raw hash。
 * 2. 去除脏结构：JSON 片段、数组括号、map / DTO 字段堆叠、内部枚举大写串。
 * 3. 去除长路径 / 长 URL 堆叠：单个长路径或 URL 不应成为主内容。
 * 4. 长度限制：title ≤ 60、summary ≤ 180、单条 change ≤ 120，超出截断。
 * 5. 可读性兜底：若清洗后无可读中文人话，用保守兜底"根据提交记录整理的变更"。
 *
 * 原始证据不会被删除，只是不进入主视图；它仍保留在 evidenceDetails / affectedFiles / 证据细节折叠区。
 */
public final class DisplayContentSanitizer {

    // commit hash（7~40 位十六进制）
    private static final Pattern COMMIT_HASH = Pattern.compile("\\b[0-9a-fA-F]{7,40}\\b");
    // evidenceRefs 前缀：commit: / file: / agent-result: / url:
    private static final Pattern EVIDENCE_PREFIX = Pattern.compile(
        "(?i)\\b(commit|file|agent-result|url|segment|evidence):[^\\s,;|}]*"
    );
    // 长 URL（http/https）
    private static final Pattern LONG_URL = Pattern.compile("https?://[^\\s,;|}]*");
    // JSON 片段：{...} / [...] / "key":"value" 堆叠
    private static final Pattern JSON_BLOCK = Pattern.compile("\\{[^{}]*}|\\[[^\\[\\]]*]|\"[A-Za-z_][A-Za-z0-9_]*\"\\s*:");
    // 内部枚举大写串：CALL_FAILED / LOCAL_RULE / NEEDS_REVIEW 等（全大写 + 下划线，长度 ≥4）
    private static final Pattern INTERNAL_ENUM = Pattern.compile("\\b[A-Z][A-Z0-9_]{3,}\\b");
    // 文件路径堆叠：连续的 path/path/path 或逗号分隔的多个路径
    private static final Pattern PATH_LIST = Pattern.compile(
        "([A-Za-z0-9_\\-./]+/[A-Za-z0-9_\\-./]+(?:\\s*[,，]\\s*[A-Za-z0-9_\\-./]+/[A-Za-z0-9_\\-./]+){2,})"
    );
    // 纯数字串（长度 ≥8）
    private static final Pattern LONG_NUMBER = Pattern.compile("\\b\\d{8,}\\b");

    private static final int TITLE_MAX = 60;
    private static final int SUMMARY_MAX = 180;
    private static final int CHANGE_MAX = 120;

    private DisplayContentSanitizer() {
    }

    /**
     * 清洗主标题。去除脏内容后截断到 60 字符；若结果无可读中文则用兜底。
     */
    public static String sanitizeTitle(String raw) {
        return sanitizeField(raw, TITLE_MAX, "根据提交记录整理的变更");
    }

    /**
     * 清洗摘要 / plainSummary。去除脏内容后截断到 180 字符；若结果无可读中文则用兜底。
     */
    public static String sanitizeSummary(String raw) {
        return sanitizeField(raw, SUMMARY_MAX, "整理了一组可追溯的开发变化。");
    }

    /**
     * 清洗单条主要变化。去除脏内容后截断到 120 字符；若结果无可读中文则用兜底。
     */
    public static String sanitizeChange(String raw) {
        return sanitizeField(raw, CHANGE_MAX, "包含可追溯的开发变化");
    }

    /**
     * 清洗用户可感知价值描述。去除脏内容后截断到 180 字符；若结果无可读中文则用兜底。
     */
    public static String sanitizeUserVisibleValue(String raw) {
        return sanitizeField(raw, SUMMARY_MAX, "带来可追溯的行为变化，可从证据细节查看来源。");
    }

    /**
     * 批量清洗主要变化列表。跳过空白输入条目，不可读内容用兜底替代。
     */
    public static List<String> sanitizeChanges(List<String> rawChanges) {
        if (rawChanges == null || rawChanges.isEmpty()) return List.of();
        List<String> result = new ArrayList<>();
        for (String change : rawChanges) {
            if (change == null || change.isBlank()) continue;
            String cleaned = sanitizeChange(change);
            if (!cleaned.isBlank()) result.add(cleaned);
        }
        return result;
    }

    /**
     * 清洗能力卡片名称。与标题同规格。
     */
    public static String sanitizeCapabilityName(String raw) {
        return sanitizeField(raw, TITLE_MAX, "根据项目证据整理的能力");
    }

    /**
     * 清洗能力卡片摘要 / README / 简历 / 面试表达。与摘要同规格。
     */
    public static String sanitizeCapabilitySummary(String raw) {
        return sanitizeField(raw, SUMMARY_MAX, "基于可追溯证据整理的能力说明。");
    }

    /**
     * 核心清洗逻辑：去除脏内容 -> 折叠多余空白 -> 截断 -> 可读性兜底。
     */
    private static String sanitizeField(String raw, int maxLength, String fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        String cleaned = raw.trim();
        // 1. 去除证据前缀标记（commit:abc / file:src/... 等）
        cleaned = EVIDENCE_PREFIX.matcher(cleaned).replaceAll("");
        // 2. 去除长 URL（保留可读文字部分）
        cleaned = LONG_URL.matcher(cleaned).replaceAll("");
        // 3. 去除 commit hash
        cleaned = COMMIT_HASH.matcher(cleaned).replaceAll("");
        // 4. 去除长数字串
        cleaned = LONG_NUMBER.matcher(cleaned).replaceAll("");
        // 5. 去除 JSON 片段 / DTO 字段
        cleaned = JSON_BLOCK.matcher(cleaned).replaceAll("");
        // 6. 去除内部枚举大写串
        cleaned = INTERNAL_ENUM.matcher(cleaned).replaceAll("");
        // 7. 折叠路径列表为更短表示
        cleaned = PATH_LIST.matcher(cleaned).replaceAll("相关文件");
        // 8. 折叠多余空白和残留标点
        cleaned = collapseWhitespace(cleaned);
        // 9. 截断到最大长度
        cleaned = truncate(cleaned, maxLength);
        // 10. 可读性兜底：清洗后无可读中文则用兜底
        if (!hasReadableContent(cleaned)) {
            return fallback;
        }
        return cleaned;
    }

    private static String collapseWhitespace(String value) {
        // 去除残留的孤立标点和多余空白
        return value
            .replaceAll("[\\s]+", " ")
            .replaceAll("\\s*[,，;；|]+\\s*[,，;；|]*\\s*", "，")
            .replaceAll("^[,，;；|\\s]+|[,，;；|\\s]+$", "")
            .replaceAll("\\s+", " ")
            .trim();
    }

    private static String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) return value;
        // 按字符截断，避免截断半个中文字符（Java String 按 UTF-16 char，汉字基本在 BMP 内）。
        return value.substring(0, maxLength) + "…";
    }

    /**
     * 可读性判断：清洗后的文本是否包含足够的中文人话，能回答"做成了什么"。
     * 至少包含 2 个汉字才认为可读。
     */
    private static boolean hasReadableContent(String value) {
        if (value == null || value.isBlank()) return false;
        long hanCount = value.chars()
            .filter(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN)
            .count();
        return hanCount >= 2;
    }
}
