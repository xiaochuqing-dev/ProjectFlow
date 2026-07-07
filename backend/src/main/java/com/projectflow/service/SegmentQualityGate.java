package com.projectflow.service;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.projectflow.service.DevelopmentSegmentationService.SegmentDraft;

/**
 * V3.3.3: 质量门槛改为"标记器"——不再把整批模型结果替换成本地摘要。
 * 每个开发推进段单独评估，返回细粒度状态，由前端显示"需复核/需中文修正/需补证据"。
 * 只有模型完全不可用时才回退本地规则（见 ModelSegmentEnricher）。
 */
@Component
public class SegmentQualityGate {
    private static final Pattern EMPTY_TITLE = Pattern.compile(
        "^(backend|frontend|docs?|config|test|项目|模块)(?:\\s+开发推进)?$|.*(?:围绕.+展开|相关能力已归并|\\d+\\s*条原子变化).*$",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern ACTION = Pattern.compile(
        ".*(新增|增加|接入|修复|调整|改造|重构|移除|删除|同步|验证|降级|配置|复用|支持|限制|优化|生成|展示|持久化|分析|合并|补充|实现|add|fix|update|remove|refactor|integrat|persist|validate|reuse|support).*",
        Pattern.CASE_INSENSITIVE
    );
    private static final Set<String> VAGUE_CHANGES = Set.of("修改后端", "优化前端", "整理证据", "相关能力已归并");
    // 用户可见主内容中允许保留的英文片段：技术名、文件路径、代码标识符等。其余应中文。
    private static final Pattern ALLOWED_ENGLISH = Pattern.compile(
        "[A-Za-z][A-Za-z0-9_+\\-./]*"
    );

    public QualityResult evaluate(SegmentDraft candidate, List<String> existingTitles) {
        String title = clean(candidate.title());
        String summary = clean(candidate.plainSummary());
        // 1. 中文质量：用户可见主内容必须以中文人话为主。若整句没有汉字，标记需中文改写。
        if (title.isBlank() || !hasChinese(title)) {
            return result("NEEDS_CHINESE_REWRITE", "标题缺少中文人话描述，需改写为简体中文");
        }
        if (summary.isBlank() || !hasChinese(summary)) {
            return result("NEEDS_CHINESE_REWRITE", "摘要缺少中文人话描述，需改写为简体中文");
        }
        // 2. 模板化/目录级摘要：标记需复核（保留模型结果，不丢弃）。
        if (EMPTY_TITLE.matcher(title).matches() || EMPTY_TITLE.matcher(summary).matches()) {
            return result("NEEDS_REVIEW", "标题或摘要没有说明具体开发结果，需人工复核");
        }
        if (!ACTION.matcher(title + " " + summary).matches()) {
            return result("NEEDS_REVIEW", "摘要缺少新增、修复、接入、验证等实际动作");
        }
        // 3. 主要变化条数与质量。
        List<String> changes = candidate.mainChanges() == null ? List.of() : candidate.mainChanges().stream()
            .map(this::clean).filter(value -> !value.isBlank()).toList();
        if (changes.size() < 3 || changes.size() > 6) {
            return result("NEEDS_REVIEW", "主要变化需 3 到 6 条具体结果，当前 " + changes.size() + " 条");
        }
        if (changes.stream().allMatch(VAGUE_CHANGES::contains)) {
            return result("NEEDS_REVIEW", "主要变化过于泛化，需补具体结果");
        }
        // 4. 用户可感知变化。
        if (clean(candidate.userVisibleValue()).isBlank()) {
            return result("NEEDS_REVIEW", "缺少用户、开发者或系统行为层面的可感知变化");
        }
        // 5. 证据完整性：证据引用是否齐全。
        List<String> evidence = candidate.evidenceRefs() == null ? List.of() : candidate.evidenceRefs();
        if (evidence.isEmpty()) {
            return result("NEEDS_EVIDENCE", "缺少证据引用，需补充来源");
        }
        // 6. 重复标题。
        Set<String> normalized = new HashSet<>();
        for (String existing : existingTitles == null ? List.<String>of() : existingTitles) {
            normalized.add(normalize(existing));
        }
        if (normalized.contains(normalize(title))) {
            return result("NEEDS_REVIEW", "同一批次出现重复标题，需人工区分");
        }
        // 7. 置信度低：保留但标记。
        if (candidate.confidence() != null && "LOW".equals(candidate.confidence().name())) {
            return result("LOW_CONFIDENCE", "模型置信度较低，建议人工复核");
        }
        return new QualityResult("PASS", "");
    }

    // 判断字符串是否包含汉字。用户可见主内容必须以中文人话为主。
    private boolean hasChinese(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        return value.chars().anyMatch(character -> Character.UnicodeScript.of(character) == Character.UnicodeScript.HAN);
    }

    // 检测用户可见主内容是否混入了大段未翻译英文（用于本地摘要兜底时辅助判断）。
    public boolean looksLikeRawEnglishCommit(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String withoutAllowed = ALLOWED_ENGLISH.matcher(value).replaceAll(" ");
        String trimmed = withoutAllowed.replaceAll("[\\s\\p{Punct}]", "");
        // 去掉允许的英文标识后几乎没有汉字 → 大概率是英文 commit message 原文。
        return trimmed.chars().noneMatch(character -> Character.UnicodeScript.of(character) == Character.UnicodeScript.HAN);
    }

    private QualityResult result(String status, String reason) {
        return new QualityResult(status, reason);
    }

    // 兼容旧调用：是否通过（PASS）。
    public boolean isPass(QualityResult result) {
        return "PASS".equals(result.status());
    }

    // 兼容旧调用：是否应标记为 NEEDS_REVIEW 状态展示给用户。
    public boolean needsReviewFlag(QualityResult result) {
        String status = result.status();
        return !"PASS".equals(status);
    }

    private String normalize(String value) {
        return clean(value).toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    public record QualityResult(String status, String reason) {
    }
}
