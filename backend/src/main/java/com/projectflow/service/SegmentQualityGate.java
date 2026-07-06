package com.projectflow.service;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.projectflow.service.DevelopmentSegmentationService.SegmentDraft;

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

    public QualityResult evaluate(SegmentDraft candidate, List<String> existingTitles) {
        String title = clean(candidate.title());
        String summary = clean(candidate.plainSummary());
        if (title.isBlank() || summary.isBlank() || EMPTY_TITLE.matcher(title).matches() || EMPTY_TITLE.matcher(summary).matches()) {
            return manual("标题或摘要没有说明具体开发结果");
        }
        if (!ACTION.matcher(title + " " + summary).matches()) {
            return manual("摘要缺少新增、修复、接入、验证等实际动作");
        }
        List<String> changes = candidate.mainChanges() == null ? List.of() : candidate.mainChanges().stream()
            .map(this::clean).filter(value -> !value.isBlank()).toList();
        if (changes.size() < 3 || changes.size() > 6 || changes.stream().allMatch(VAGUE_CHANGES::contains)) {
            return manual("主要变化必须包含 3 到 6 条具体结果");
        }
        if (clean(candidate.userVisibleValue()).isBlank()) {
            return manual("缺少用户、开发者或系统行为层面的可感知变化");
        }
        Set<String> normalized = new HashSet<>();
        for (String existing : existingTitles == null ? List.<String>of() : existingTitles) {
            normalized.add(normalize(existing));
        }
        if (normalized.contains(normalize(title))) {
            return manual("同一批次出现重复标题");
        }
        return new QualityResult("PASS", "");
    }

    private QualityResult manual(String reason) {
        return new QualityResult("NEEDS_MANUAL", reason);
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
