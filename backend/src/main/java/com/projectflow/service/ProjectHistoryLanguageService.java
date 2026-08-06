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
    private static final Pattern TECHNICAL = Pattern.compile(
        "(?i)(controller|repository|service|dto|entity|mapper|handler|adapter|provider|src/main|frontend/src|backend/src|project-area|project-subject|change-)"
    );
    private static final Set<String> SUPPORT_SEGMENTS = Set.of(
        "test", "tests", "__tests__", "docs", "doc", ".github", "ci", "config", "configuration", "migration",
        "migrations", "build", "scripts", "fixtures", "example", "examples"
    );
    private static final Set<String> SUPPORT_EXTENSIONS = Set.of(
        "md", "mdx", "txt", "rst", "adoc", "yml", "yaml", "toml", "json", "xml", "properties", "gradle", "lock"
    );

    public Presentation fallback(
        Transition transition,
        String subject,
        List<String> paths,
        List<String> sourceLabels,
        List<String> transitions
    ) {
        String object = readableObject(subject, paths, sourceLabels);
        String title = switch (transition == null ? Transition.UNKNOWN_TRANSITION : transition) {
            case CREATED -> "建立" + object + "，项目开始具备这项能力";
            case MODIFIED -> "完善" + object + "，当前行为得到更新";
            case REMOVED -> "移除" + object + "，旧能力不再保留";
            case RESTORED -> "恢复" + object + "，此前能力重新可用";
            case RENAMED -> "调整" + object + "名称，历史关系仍可追踪";
            case MOVED -> "调整" + object + "位置，历史关系仍可追踪";
            case REPLACED -> "替换" + object + "，新的实现接替旧实现";
            case SPLIT -> "拆分" + object + "，变化可以分别追踪";
            case MERGED -> "合并" + object + "，把同一项工作的结果放在一起";
            case REVERTED -> "撤销" + object + "，上一项变化被回退";
            case REAPPLIED -> "重新实现" + object + "，此前结果再次出现";
            default -> "记录" + object + "，这段历史保留了它的变化";
        };
        String summary = switch (transition == null ? Transition.UNKNOWN_TRANSITION : transition) {
            case CREATED -> "项目新增了" + object + "，后续来源可以继续核对它的变化。";
            case MODIFIED -> "项目完善了" + object + "，当前状态以这次来源记录为准。";
            case REMOVED, REVERTED -> "项目移除了" + object + "，旧结果在这段历史中被结束或回退。";
            case RESTORED, REAPPLIED -> "项目重新恢复了" + object + "，此前的移除或回退被后续来源改变。";
            default -> "项目围绕" + object + "留下了一项可追溯结果，工程证据仍可继续下钻。";
        };
        String before = switch (transition == null ? Transition.UNKNOWN_TRANSITION : transition) {
            case CREATED -> "此前的已覆盖来源中尚未观察到这项项目结果。";
            case REMOVED -> "此前来源仍显示这项项目结果存在。";
            case RESTORED, REAPPLIED -> "此前来源显示这项项目结果曾被移除或撤销。";
            default -> "此前状态只按更早来源保留，不能从当前代码反推未记录的细节。";
        };
        String after = switch (transition == null ? Transition.UNKNOWN_TRANSITION : transition) {
            case REMOVED, REVERTED -> "变化后，" + object + "不再处于当前可确认状态。";
            case RESTORED, REAPPLIED -> "变化后，" + object + "重新处于项目当前状态。";
            case RENAMED, MOVED -> "变化后，" + object + "以新的名称或位置继续存在。";
            default -> "变化后，" + object + "呈现出这段历史能够确认的状态。";
        };
        return new Presentation(title, summary, before, title, after, object);
    }

    public String readableObject(String subject, List<String> paths, List<String> labels) {
        String subjectSample = subject == null ? "" : subject.toLowerCase(Locale.ROOT);
        if (containsAny(subjectSample, "project-area-backend", "后端区域")) return "后端区域";
        if (containsAny(subjectSample, "project-area-frontend", "前端区域")) return "前端区域";
        if (containsAny(subjectSample, "project-area-docs", "project-area-document", "文档区域")) return "文档区域";
        String sample = ((subject == null ? "" : subject) + " " + String.join(" ", paths == null ? List.of() : paths)
            + " " + String.join(" ", labels == null ? List.of() : labels)).toLowerCase(Locale.ROOT);
        // Area subjects are an intentional semantic fallback for large imports.
        // Resolve them before broad product terms such as "history" so a
        // backend/frontend/docs area does not collapse into a generic label.
        if (containsAny(sample, "project-area-backend", "backend/src", "后端区域")) return "后端区域";
        if (containsAny(sample, "project-area-frontend", "frontend/src", "前端区域")) return "前端区域";
        if (containsAny(sample, "project-area-docs", "project-area-document", "docs/", "文档区域")) return "文档区域";
        if (containsAny(sample, "auth", "login", "sign-in", "邮箱", "email", "oauth")) return "登录与身份验证流程";
        if (containsAny(sample, "export", "pdf", "markdown", "csv")) return "项目成果导出";
        if (containsAny(sample, "history", "timeline", "project-history", "历程")) return "项目历程与时间阅读";
        if (containsAny(sample, "agent", "hermes", "context", "mcp")) return "多个 Agent 的项目上下文共享";
        if (containsAny(sample, "obsidian", "vault", "projection")) return "Obsidian 项目知识投影";
        if (containsAny(sample, "task", "job", "refresh", "checkpoint", "retry")) return "长任务刷新与恢复";
        if (containsAny(sample, "docker", "compose", "gitignore", "environment", "env")) return "项目基础开发环境";
        if (containsAny(sample, "capability", "能力")) return "项目长期能力记录";
        if (containsAny(sample, "timeline", "fact", "memory", "记忆")) return "项目事实与时间记录";
        if (containsAny(sample, "readme", "documentation", "docs", "说明")) return "项目使用说明";
        String candidate = (subject == null ? "" : subject).replace('-', ' ').replace('_', ' ').trim();
        candidate = candidate.replaceAll("(?i)\\b(project area|project subject|change)\\b", "").replaceAll("\\s+", " ").trim();
        if (candidate.isBlank() || TECHNICAL.matcher(candidate).find()) return "项目核心结果";
        if (candidate.length() > 36) candidate = candidate.substring(0, 35) + "…";
        return candidate;
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
        boolean supportLabel = String.join(" ", safeLabels).toLowerCase(Locale.ROOT)
            .matches(".*(test|测试|doc|文档|readme|config|配置|ci|build|构建|lint|format|migration|迁移).*" );
        boolean supportCategory = categories != null && !categories.isEmpty()
            && categories.stream().allMatch(category -> category == Category.VALIDATION
                || category == Category.DOCUMENT_VERSION || category == Category.TAG);
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
        return value.matches("^(docs?|test|tests|chore|ci|build|style|format|lint|bump|backfill|checkpoint|archive|record|freeze|release|update readme|merge pull request)(\\s|:|-|$).*")
            || value.contains("acceptance evidence")
            || value.contains("pull request gates")
            || value.contains("release evidence");
    }

    public String threadLabel(String key) {
        return readableObject(key, List.of(), List.of());
    }

    public String commitSummary(String label, Transition transition, List<String> paths) {
        String object = readableObject(label, paths, List.of(label));
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
        return action + object + "；具体原因没有可靠说明。";
    }

    public String chapterTitle(List<String> storyTitles, List<Transition> transitions, Instant from, Instant to) {
        String joined = String.join(" ", storyTitles == null ? List.of() : storyTitles);
        if (containsAny(joined, "登录", "身份")) return "完善登录流程并统一项目入口";
        if (containsAny(joined, "历程", "时间", "记忆")) return "补齐项目记忆、时间阅读和证据下钻";
        if (containsAny(joined, "Agent", "上下文", "Obsidian")) return "让多个 Agent 共享并持续阅读项目上下文";
        if (transitions != null && transitions.stream().allMatch(value -> value == Transition.CREATED)) return "建立项目基础并形成首批可确认成果";
        if (transitions != null && transitions.stream().anyMatch(value -> value == Transition.RESTORED || value == Transition.REAPPLIED)) return "恢复并继续完善已有项目成果";
        if (from != null && to != null && from.equals(to)) return "完成一组相互关联的项目变化";
        return "持续完善项目成果并保留工程证据";
    }

    public String chapterSummary(List<String> storyTitles, int storyCount, int rawEventCount) {
        List<String> titles = storyTitles == null ? List.of() : storyTitles.stream().limit(3).toList();
        String focus = titles.isEmpty() ? "项目成果" : String.join("、", titles);
        return "这一阶段围绕" + focus + "推进了 " + storyCount + " 项可读成果；测试、文档和配置等支撑工作已归入工程详情。原始事件仍完整保留，可继续核验。";
    }

    public record Presentation(String title, String summary, String before, String change, String after, String object) {
    }

    private boolean supportPath(String path) {
        if (path == null || path.isBlank()) return false;
        String normalized = path.replace('\\', '/').toLowerCase(Locale.ROOT);
        String[] parts = normalized.split("/");
        for (String part : parts) if (SUPPORT_SEGMENTS.contains(part)) return true;
        int dot = normalized.lastIndexOf('.');
        return dot > 0 && SUPPORT_EXTENSIONS.contains(normalized.substring(dot + 1));
    }

    private static boolean containsAny(String value, String... markers) {
        for (String marker : markers) if (value.contains(marker.toLowerCase(Locale.ROOT))) return true;
        return false;
    }
}
