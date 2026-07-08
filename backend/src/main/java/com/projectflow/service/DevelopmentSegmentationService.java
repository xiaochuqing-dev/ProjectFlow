package com.projectflow.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.projectflow.entity.EvidenceConfidence;

@Service
public class DevelopmentSegmentationService {
    private static final Pattern CONVENTIONAL_SCOPE = Pattern.compile("^[a-zA-Z]+\\(([^)]+)\\):");

    public List<SegmentDraft> group(List<ChangeAtom> atoms) {
        if (atoms == null || atoms.isEmpty()) {
            return List.of();
        }
        if (atoms.size() <= 3 || distinctFiles(atoms) <= 10) {
            return topicGroups(atoms, 1, 3);
        }
        if (atoms.size() <= 30 && distinctFiles(atoms) <= 80) {
            return topicGroups(atoms, 2, 5);
        }
        int target = clamp((int) Math.ceil(atoms.size() / 25.0), 3, 8);
        return partition(atoms, target);
    }

    private List<SegmentDraft> topicGroups(List<ChangeAtom> atoms, int minimum, int maximum) {
        Map<String, List<ChangeAtom>> grouped = new LinkedHashMap<>();
        for (ChangeAtom atom : atoms) {
            grouped.computeIfAbsent(topicKey(atom), ignored -> new ArrayList<>()).add(atom);
        }
        if (grouped.size() >= minimum && grouped.size() <= maximum) {
            return grouped.entrySet().stream().map(entry -> draft(entry.getKey(), entry.getValue())).toList();
        }
        int target = clamp(grouped.size(), minimum, maximum);
        return partition(atoms, target);
    }

    private List<SegmentDraft> partition(List<ChangeAtom> atoms, int target) {
        List<List<ChangeAtom>> buckets = new ArrayList<>();
        for (int index = 0; index < target; index++) {
            buckets.add(new ArrayList<>());
        }
        for (int index = 0; index < atoms.size(); index++) {
            buckets.get(Math.min(target - 1, index * target / atoms.size())).add(atoms.get(index));
        }
        List<SegmentDraft> result = new ArrayList<>();
        for (List<ChangeAtom> bucket : buckets) {
            if (!bucket.isEmpty()) {
                result.add(draft(topicKey(bucket.get(0)), bucket));
            }
        }
        return result;
    }

    private SegmentDraft draft(String topic, List<ChangeAtom> atoms) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        LinkedHashSet<String> files = new LinkedHashSet<>();
        LinkedHashSet<String> evidence = new LinkedHashSet<>();
        List<String> changes = new ArrayList<>();
        for (ChangeAtom atom : atoms) {
            ids.add(atom.id());
            files.addAll(atom.files());
            evidence.addAll(atom.evidenceRefs());
            if (changes.size() < 5) {
                changes.add(concreteChange(atom));
            }
        }
        ChangeAtom primary = atoms.get(0);
        String result = concreteChange(primary);
        while (changes.size() < 3) {
            if (changes.size() == 1) {
                // V3.3.4 小阶段修复：本地事实摘要不得把原始文件路径塞进 mainChanges。
                changes.add("影响 " + fileSummary(files));
            } else {
                changes.add("保留可追溯证据，可在证据细节查看提交与文件来源");
            }
        }
        // V3.3.4 小阶段修复：本地事实摘要也必须经过 DisplayContentSanitizer 清洗。
        String sanitizedTitle = DisplayContentSanitizer.sanitizeTitle(result);
        String sanitizedSummary = DisplayContentSanitizer.sanitizeSummary(summaryOf(changes));
        List<String> sanitizedChanges = DisplayContentSanitizer.sanitizeChanges(changes);
        return new SegmentDraft(
            sanitizedTitle,
            sanitizedSummary,
            List.copyOf(ids),
            sanitizedChanges,
            DisplayContentSanitizer.sanitizeUserVisibleValue(
                "用户或开发者可直接获得“" + result + "”对应的行为变化，并能从文件与提交证据追溯来源。"
            ),
            List.copyOf(evidence),
            List.copyOf(files),
            atoms.size() <= 3 ? EvidenceConfidence.HIGH : EvidenceConfidence.MEDIUM
        );
    }

    private String concreteChange(ChangeAtom atom) {
        String cleaned = cleanTitle(atom.title());
        cleaned = cleaned.replaceFirst("(?i)^(add|implement|create|introduce|update|improve|fix|repair|refactor|remove|delete|document|explain|test|verify|support|enable|stop|cut|restore|surface|commit|scan|bind|track|persist|split|merge|drop|guard|skip|polish|tune|align|wire|hook)\\s+", "");
        if (cleaned.matches("^(新增|增加|接入|修复|调整|改造|重构|移除|删除|同步|验证|配置|优化|补充|实现|恢复|暴露|提交|扫描|绑定|跟踪|持久化|拆分|合并|丢弃|守护|跳过|打磨|调优|对齐|接通|挂载).+")) {
            return cleaned;
        }
        String lower = atom.title() == null ? "" : atom.title().toLowerCase(Locale.ROOT);
        String action = actionOf(lower);
        // V3.3.4: 如果去掉动作前缀后仍是英文（无中文字符），做一次常见词转写；
        // 仍无法可靠转写则标为"根据提交记录整理的变更"，英文原文留在证据细节里。
        String translated = translateCommonEnglish(cleaned);
        if (containsChinese(translated)) {
            return action + translated;
        }
        return action + "根据提交记录整理的变更";
    }

    // V3.3.4: 常见英文 commit action -> 中文动作。
    private String actionOf(String lower) {
        if (lower.startsWith("fix") || lower.startsWith("repair")) return "修复";
        if (lower.startsWith("refactor")) return "重构";
        if (lower.startsWith("docs") || lower.startsWith("document")) return "补充";
        if (lower.startsWith("test")) return "验证";
        if (lower.startsWith("remove") || lower.startsWith("delete") || lower.startsWith("drop")) return "移除";
        if (lower.startsWith("stop") || lower.startsWith("cut") || lower.startsWith("skip")) return "调整";
        if (lower.startsWith("restore") || lower.startsWith("revert")) return "恢复";
        if (lower.startsWith("update") || lower.startsWith("improve") || lower.startsWith("polish") || lower.startsWith("tune") || lower.startsWith("align")) return "优化";
        if (lower.startsWith("merge")) return "合并";
        if (lower.startsWith("split")) return "拆分";
        if (lower.startsWith("scan")) return "扫描";
        if (lower.startsWith("bind")) return "绑定";
        if (lower.startsWith("track")) return "跟踪";
        if (lower.startsWith("persist") || lower.startsWith("commit")) return "持久化";
        if (lower.startsWith("surface") || lower.startsWith("expose")) return "暴露";
        if (lower.startsWith("guard")) return "守护";
        if (lower.startsWith("wire") || lower.startsWith("hook") || lower.startsWith("enable") || lower.startsWith("support")) return "接通";
        return "新增";
    }

    // V3.3.4: 对去掉动作前缀后的英文标题做常见词转写。无法可靠转写时原样返回（无中文字符）。
    private String translateCommonEnglish(String value) {
        if (value == null || value.isBlank()) return value;
        String result = value;
        // 仅当不含中文时才尝试转写，避免破坏已经是中文的标题。
        if (containsChinese(result)) return result;
        // 常见动名词/技术词 -> 中文，按词边界替换。
        result = result.replaceAll("(?i)\\bauto[- ]?resuming\\b", "自动恢复");
        result = result.replaceAll("(?i)\\banalysis\\s+jobs?\\b", "分析任务");
        result = result.replaceAll("(?i)\\bmodel\\s+enrich(?:ment)?\\b", "模型归并");
        result = result.replaceAll("(?i)\\btimeout\\b", "超时");
        result = result.replaceAll("(?i)\\bpending\\s+changes?\\b", "待整理变更");
        result = result.replaceAll("(?i)\\bsediment(?:ation)?\\b", "沉淀");
        result = result.replaceAll("(?i)\\bconfirmation\\b", "确认");
        result = result.replaceAll("(?i)\\bselected\\s+project\\b", "选中项目");
        result = result.replaceAll("(?i)\\brefresh\\b", "刷新");
        result = result.replaceAll("(?i)\\bpanel\\b", "面板");
        result = result.replaceAll("(?i)\\bcapability\\s+cards?\\b", "能力卡片");
        result = result.replaceAll("(?i)\\bcapabilit(?:y|ies)\\b", "能力");
        result = result.replaceAll("(?i)\\bgithub\\b", "GitHub");
        result = result.replaceAll("(?i)\\bdashboard\\b", "工作台");
        result = result.replaceAll("(?i)\\bscan(?:ning)?\\b", "扫描");
        result = result.replaceAll("(?i)\\bproject\\b", "项目");
        result = result.replaceAll("(?i)\\bchanges?\\b", "变更");
        return result;
    }

    private boolean containsChinese(String value) {
        if (value == null || value.isEmpty()) return false;
        return value.chars().anyMatch(character -> Character.UnicodeScript.of(character) == Character.UnicodeScript.HAN);
    }

    private String summaryOf(List<String> changes) {
        return changes.stream().limit(3).reduce((left, right) -> left + "；" + right).orElse("整理可追溯开发变化") + "。";
    }

    // V3.3.4 小阶段修复：本地事实摘要不得把原始文件路径塞进 mainChanges，只说明影响范围。
    private String fileSummary(LinkedHashSet<String> files) {
        if (files.isEmpty()) return "项目行为与维护流程";
        return files.size() + " 个相关文件（可在证据细节查看）";
    }

    private String topicKey(ChangeAtom atom) {
        Matcher matcher = CONVENTIONAL_SCOPE.matcher(atom.title() == null ? "" : atom.title());
        if (matcher.find()) {
            return matcher.group(1).trim().toLowerCase(Locale.ROOT);
        }
        return atom.modules().stream().filter(value -> value != null && !value.isBlank()).findFirst().orElse("项目");
    }

    private String cleanTitle(String title) {
        if (title == null || title.isBlank()) {
            return "未命名变化";
        }
        return title.replaceFirst("^[a-zA-Z]+(?:\\([^)]+\\))?:\\s*", "").trim();
    }

    private int distinctFiles(List<ChangeAtom> atoms) {
        return (int) atoms.stream().flatMap(atom -> atom.files().stream()).distinct().count();
    }

    private int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public record ChangeAtom(
        String id,
        String title,
        Instant occurredAt,
        List<String> modules,
        List<String> files,
        List<String> evidenceRefs,
        List<String> diffHints,
        String sourceType
    ) {
        public ChangeAtom {
            modules = modules == null ? List.of() : List.copyOf(modules);
            files = files == null ? List.of() : List.copyOf(files);
            evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
            diffHints = diffHints == null ? List.of() : List.copyOf(diffHints);
            sourceType = sourceType == null ? "GIT" : sourceType;
        }

        public ChangeAtom(
            String id, String title, Instant occurredAt, List<String> modules, List<String> files, List<String> evidenceRefs
        ) {
            this(id, title, occurredAt, modules, files, evidenceRefs, List.of(), "GIT");
        }
    }

    public record SegmentDraft(
        String title,
        String plainSummary,
        List<String> includedAtomIds,
        List<String> mainChanges,
        String userVisibleValue,
        List<String> evidenceRefs,
        List<String> affectedFiles,
        EvidenceConfidence confidence
    ) {
    }
}
