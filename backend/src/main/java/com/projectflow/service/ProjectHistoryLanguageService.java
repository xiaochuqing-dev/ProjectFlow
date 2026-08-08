package com.projectflow.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.projectflow.entity.ProjectHistoryEvent.Category;
import com.projectflow.entity.ProjectHistoryEvent.Transition;

/**
 * Small deterministic language boundary for the first presentation layer.
 * It deliberately consumes bounded labels/paths and never treats a class name
 * or commit subject as a user-facing fact on its own.
 */
@Component
public final class ProjectHistoryLanguageService {
    private static final Pattern TECHNICAL_SUFFIX = Pattern.compile(
        "(?i)(controller|repository|dto|entity|mapper|handler|adapter|provider)$"
    );
    private static final Set<String> SUPPORT_SEGMENTS = Set.of(
        "test", "tests", "__tests__", ".github", "ci", "config", "configuration", "migration",
        "migrations", "build", "scripts", "fixtures", "example", "examples"
    );
    private static final Set<String> SLIDE_EXTENSIONS = Set.of("ppt", "pptx", "key", "odp");
    private static final Set<String> DOCUMENT_EXTENSIONS = Set.of("doc", "docx", "pdf", "md", "mdx", "txt", "rst", "adoc", "tex");
    private static final Set<String> DATA_EXTENSIONS = Set.of("csv", "tsv", "xls", "xlsx", "json", "parquet", "arrow");
    private static final Set<String> VIDEO_EXTENSIONS = Set.of("mp4", "mov", "mkv", "webm", "avi");
    private static final Set<String> DESIGN_EXTENSIONS = Set.of("fig", "sketch", "psd", "ai", "xd", "svg", "png", "jpg", "jpeg");

    public Presentation fallback(
        Transition transition,
        String subject,
        List<String> paths,
        List<String> sourceLabels,
        List<String> transitions
    ) {
        String object = readableObject(subject, paths, sourceLabels);
        String title = switch (transition == null ? Transition.UNKNOWN_TRANSITION : transition) {
            case CREATED -> "新增" + object + "，形成首个可确认版本";
            case MODIFIED -> "更新" + object + "，形成新的可确认版本";
            case REMOVED -> "移除" + object + "，当前项目不再保留这项结果";
            case RESTORED -> "恢复" + object + "，此前移除的结果重新出现";
            case RENAMED -> "重命名" + object + "，原有内容继续保留";
            case MOVED -> "调整" + object + "的存放位置，内容继续保留";
            case REPLACED -> "替换" + object + "，当前采用新的版本";
            case SPLIT -> "拆分" + object + "，各部分可以分别查看";
            case MERGED -> "合并" + object + "，相关内容统一呈现";
            case REVERTED -> "撤销" + object + "的上一项变化，恢复此前状态";
            case REAPPLIED -> "重新加入" + object + "，此前撤销的结果再次出现";
            default -> "更新" + object + "，形成当前可核对的材料版本";
        };
        String summary = switch (transition == null ? Transition.UNKNOWN_TRANSITION : transition) {
            case CREATED -> object + "首次出现在已覆盖来源中，并形成可继续核对的版本。";
            case MODIFIED -> object + "已经更新，当前展示以这次可核对的结果为准。";
            case REMOVED, REVERTED -> object + "已从当前结果中移除或回退，详情仍保留原始来源。";
            case RESTORED, REAPPLIED -> object + "已经恢复，此前的移除或撤销可从历史中继续核对。";
            default -> object + "留下了一项当前可核对的变化，具体范围可在详情中继续确认。";
        };
        String before = switch (transition == null ? Transition.UNKNOWN_TRANSITION : transition) {
            case CREATED -> "此前覆盖范围内尚未出现" + object + "。";
            case REMOVED, REVERTED -> "此前来源仍显示" + object + "存在。";
            case RESTORED, REAPPLIED -> "此前来源显示" + object + "曾被移除或撤销。";
            default -> "此前已有" + object + "，但尚未包含这次变化。";
        };
        String after = switch (transition == null ? Transition.UNKNOWN_TRANSITION : transition) {
            case REMOVED, REVERTED -> "变化后，当前项目不再保留" + object + "。";
            case RESTORED, REAPPLIED -> "变化后，" + object + "重新出现在当前项目中。";
            case RENAMED, MOVED -> "变化后，" + object + "以新的名称或位置继续保留。";
            default -> "变化后，" + object + "形成了当前可核对的版本。";
        };
        return new Presentation(title, summary, before, title, after, object);
    }

    public String readableObject(String subject, List<String> paths, List<String> labels) {
        List<String> safePaths = paths == null ? List.of() : paths.stream()
            .filter(value -> value != null && !value.isBlank()).toList();
        String sample = ((subject == null ? "" : subject) + " " + String.join(" ", safePaths)).toLowerCase(Locale.ROOT);
        if (containsAny(sample, "auth", "login", "sign-in", "oauth", "登录", "邮箱登录")) return "登录流程";
        if (containsAny(sample, "export", "download", "导出")) return "成果导出";
        if (containsAny(sample, "project-history", "timeline", "项目历程")) return "项目历程";
        String subjectSample = subject == null ? "" : subject.toLowerCase(Locale.ROOT);
        if (subjectSample.startsWith("project-area-")) {
            if (containsAny(subjectSample, "frontend", "site", "web")) return "页面内容";
            if (containsAny(subjectSample, "docs", "document", "paper", "report")) return "项目文档";
            return "项目材料";
        }
        String artifact = artifactObject(subject, safePaths);
        if (!artifact.isBlank()) return artifact;
        String candidate = humanize(subject);
        if (candidate.isBlank()) candidate = safePaths.stream().map(ProjectHistoryLanguageService::pathStem)
            .map(ProjectHistoryLanguageService::humanize).filter(value -> !value.isBlank()).findFirst().orElse("");
        if (candidate.isBlank()) return "项目材料";
        return bounded(candidate, 36);
    }

    public boolean supporting(
        List<Category> categories,
        List<String> paths,
        List<String> labels,
        List<Transition> transitions,
        boolean hasIndependentResultSignal
    ) {
        if (hasIndependentResultSignal) return false;
        List<String> safePaths = paths == null ? List.of() : paths;
        List<String> safeLabels = labels == null ? List.of() : labels;
        boolean allSupportPaths = !safePaths.isEmpty() && safePaths.stream().allMatch(this::supportPath);
        boolean supportLabel = String.join(" ", safeLabels).trim().toLowerCase(Locale.ROOT)
            .matches("^(tests?|测试|chore|ci|build|构建|lint|format|config|配置|migration|迁移)(\\s|:|-|$).*" );
        boolean supportCategory = categories != null && !categories.isEmpty()
            && categories.stream().allMatch(category -> category == Category.VALIDATION);
        boolean boundary = transitions != null && transitions.stream().anyMatch(value -> Set.of(
            Transition.REPLACED, Transition.REMOVED, Transition.RESTORED, Transition.SPLIT, Transition.MERGED
        ).contains(value));
        return !boundary && (allSupportPaths || supportLabel || supportCategory);
    }

    /**
     * Commit subjects are evidence hints, not facts. These prefixes are only
     * used to decide whether a candidate should be folded under a nearby
     * result; the raw commit and its paths remain available in drill-down.
     */
    public boolean supportingCommitLabel(String label) {
        String value = label == null ? "" : label.trim().toLowerCase(Locale.ROOT);
        if (value.isBlank()) return true;
        return value.matches("^(fix|fixed|update|updated|change|changes|misc|wip|修复|更新|修改)$")
            || value.matches("^(test|tests|chore|ci|build|style|format|lint|bump|backfill|checkpoint|archive|freeze)(\\s|:|-|$).*")
            || value.contains("acceptance evidence")
            || value.contains("pull request gates");
    }

    public String threadLabel(String key) {
        return readableObject(key, List.of(), List.of());
    }

    public String commitSummary(String label, Transition transition, List<String> paths) {
        boolean genericLabel = supportingCommitLabel(label);
        String object = readableObject(genericLabel ? "" : label, paths, List.of(label));
        if (genericLabel && !containsHan(object)) {
            boolean supportOnly = paths != null && !paths.isEmpty() && paths.stream().allMatch(this::supportPath);
            object = supportOnly ? "验证与配套内容" : "相关功能";
        }
        String action = switch (transition == null ? Transition.UNKNOWN_TRANSITION : transition) {
            case CREATED -> "建立";
            case MODIFIED -> "完善";
            case REMOVED -> "移除";
            case RESTORED -> "恢复";
            case RENAMED, MOVED -> "调整";
            case REPLACED -> "替换";
            case SPLIT -> "拆分";
            case MERGED -> "合并";
            case REVERTED -> "撤销";
            case REAPPLIED -> "重新实现";
            default -> "记录";
        };
        return action + object + "，并保留原始提交信息供核对。";
    }

    public String chapterTitle(List<String> storyTitles, List<Transition> transitions, Instant from, Instant to) {
        List<String> focus = (storyTitles == null ? List.<String>of() : storyTitles).stream()
            .map(ProjectHistoryLanguageService::chapterFocus).filter(value -> !value.isBlank()).distinct().limit(2).toList();
        if (focus.isEmpty()) return "项目材料形成当前阶段的可确认结果";
        String joined = String.join("与", focus);
        if (transitions != null && !transitions.isEmpty()
            && transitions.stream().allMatch(value -> value == Transition.CREATED)) {
            return "首次形成" + joined + "等可确认结果";
        }
        if (transitions != null && transitions.stream().anyMatch(value ->
            value == Transition.RESTORED || value == Transition.REAPPLIED)) {
            return "恢复" + joined + "，相关结果重新可用";
        }
        return joined + "在这一阶段继续完善";
    }

    public String chapterSummary(List<String> storyTitles, int primaryCount, int supportingCount) {
        List<String> focus = (storyTitles == null ? List.<String>of() : storyTitles).stream()
            .map(ProjectHistoryLanguageService::chapterFocus).filter(value -> !value.isBlank()).distinct().limit(3).toList();
        String details = focus.isEmpty() ? "" : "，主要包括" + String.join("、", focus);
        String support = supportingCount <= 0 ? "" : "，另有 " + supportingCount + " 项支撑工作可在详情中查看";
        return "这一阶段形成了 " + Math.max(0, primaryCount) + " 项主要结果" + details + support + "。";
    }

    public record Presentation(String title, String summary, String before, String change, String after, String object) {
    }

    private boolean supportPath(String path) {
        if (path == null || path.isBlank()) return false;
        String normalized = path.replace('\\', '/').toLowerCase(Locale.ROOT);
        String[] parts = normalized.split("/");
        for (String part : parts) if (SUPPORT_SEGMENTS.contains(part)) return true;
        return false;
    }

    private static String artifactObject(String subject, List<String> paths) {
        if (paths == null || paths.isEmpty()) return "";
        String path = paths.get(0).replace('\\', '/');
        String extension = extension(path);
        String candidate = humanize(subject);
        if (candidate.isBlank()) candidate = humanize(pathStem(path));
        if (candidate.isBlank() || "index".equalsIgnoreCase(candidate)) candidate = humanize(parentName(path));
        candidate = bounded(candidate, 24);
        if (SLIDE_EXTENSIONS.contains(extension)) return named(candidate, "演示文稿");
        if (DOCUMENT_EXTENSIONS.contains(extension)) return named(candidate, "文档");
        if (DATA_EXTENSIONS.contains(extension)) return named(candidate, "数据结果");
        if (VIDEO_EXTENSIONS.contains(extension)) return named(candidate, "视频");
        if (DESIGN_EXTENSIONS.contains(extension)) return named(candidate, "设计稿");
        if ("html".equals(extension) || "htm".equals(extension)) return named(candidate, "页面");
        return "";
    }

    private static String named(String candidate, String type) {
        if (candidate == null || candidate.isBlank() || "project content".equalsIgnoreCase(candidate)) return type;
        return candidate + " " + type;
    }

    private static String humanize(String value) {
        String safe = value == null ? "" : value.trim();
        if (safe.isBlank()) return "";
        safe = safe.replaceAll("([a-z0-9])([A-Z])", "$1 $2").replace('-', ' ').replace('_', ' ');
        safe = safe.replaceAll("(?i)\\b(project area|project subject|change|unknown subject|project change|project content)\\b", " ");
        safe = safe.replaceAll("(?i)\\b(controller|repository|dto|entity|mapper|handler|adapter|provider)\\b", " ");
        safe = TECHNICAL_SUFFIX.matcher(safe).replaceAll("");
        return safe.replaceAll("\\s+", " ").trim();
    }

    private static String chapterFocus(String title) {
        String value = title == null ? "" : title.trim();
        value = value.replaceFirst("^(新增|建立|完成|更新|完善|移除|恢复|重命名|调整|替换|拆分|合并|撤销|重新加入|记录|推进)", "");
        int comma = value.indexOf('，');
        if (comma > 0) value = value.substring(0, comma);
        return bounded(value.trim(), 28);
    }

    private static String extension(String path) {
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        int dot = path.lastIndexOf('.');
        return dot > slash ? path.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    private static String pathStem(String path) {
        if (path == null || path.isBlank()) return "";
        String normalized = path.replace('\\', '/');
        String file = normalized.substring(normalized.lastIndexOf('/') + 1);
        int dot = file.indexOf('.');
        return dot > 0 ? file.substring(0, dot) : file;
    }

    private static String parentName(String path) {
        if (path == null || path.isBlank()) return "";
        String normalized = path.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        if (slash <= 0) return "";
        String parent = normalized.substring(0, slash);
        int parentSlash = parent.lastIndexOf('/');
        return parent.substring(parentSlash + 1);
    }

    private static String bounded(String value, int max) {
        String safe = value == null ? "" : value.trim();
        return safe.length() <= max ? safe : safe.substring(0, Math.max(1, max - 1)) + "…";
    }

    private static boolean containsAny(String value, String... markers) {
        for (String marker : markers) if (value.contains(marker.toLowerCase(Locale.ROOT))) return true;
        return false;
    }

    private static boolean containsHan(String value) {
        if (value == null) return false;
        return value.codePoints().anyMatch(codePoint -> codePoint >= 0x3400 && codePoint <= 0x9FFF);
    }
}
