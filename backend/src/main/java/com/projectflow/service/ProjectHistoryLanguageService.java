package com.projectflow.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
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
    private final ProjectHistoryHumanSubjectLabelService subjectLabels;

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

    public ProjectHistoryLanguageService() {
        this(new ProjectHistoryHumanSubjectLabelService());
    }

    @Autowired
    public ProjectHistoryLanguageService(ProjectHistoryHumanSubjectLabelService subjectLabels) {
        this.subjectLabels = subjectLabels;
    }

    public Presentation fallback(
        Transition transition,
        String subject,
        List<String> paths,
        List<String> sourceLabels,
        List<String> transitions
    ) {
        String object = readableObject(subject, paths, sourceLabels);
        String title = switch (transition == null ? Transition.UNKNOWN_TRANSITION : transition) {
            case CREATED -> "建立" + object + "，形成可继续查看的初始成果";
            case MODIFIED -> "完善" + object + "，更新已有内容";
            case REMOVED -> "移除" + object + "，当前项目不再保留这项内容";
            case RESTORED -> "恢复" + object + "，让此前移除的内容重新出现";
            case RENAMED -> "调整" + object + "的名称，原有内容继续保留";
            case MOVED -> "调整" + object + "的存放位置，原有内容继续保留";
            case REPLACED -> "替换" + object + "，改用新的内容版本";
            case SPLIT -> "拆分" + object + "，让各部分可以分别查看";
            case MERGED -> "合并" + object + "，让相关内容统一呈现";
            case REVERTED -> "撤销" + object + "的上一项变化，恢复此前内容";
            case REAPPLIED -> "重新加入" + object + "，恢复此前撤销的内容";
            default -> "整理" + object + "，记录当前能够确认的变化";
        };
        String summary = switch (transition == null ? Transition.UNKNOWN_TRANSITION : transition) {
            case CREATED -> "这一阶段首次形成" + object + "，主要内容已纳入项目记录。";
            case MODIFIED -> "这次调整补充了" + object + "的内容，原有记录仍可继续核对。";
            case REMOVED, REVERTED -> object + "已退出当前结果，原始来源仍保留在工程详情中。";
            case RESTORED, REAPPLIED -> object + "重新回到当前结果，此前变化仍可从历史中核对。";
            case RENAMED, MOVED -> object + "调整了名称或位置，内容本身继续保留。";
            case REPLACED -> object + "换成了新的内容版本，旧版本仍可从历史中核对。";
            case SPLIT -> object + "被整理为多个部分，方便分别查看。";
            case MERGED -> object + "的相关内容被收拢到一起，方便统一查看。";
            default -> "现有来源记录了" + object + "的一次变化，具体范围可在工程详情中确认。";
        };
        String before = switch (transition == null ? Transition.UNKNOWN_TRANSITION : transition) {
            case CREATED -> "此前项目中还没有" + object + "。";
            case REMOVED, REVERTED -> "在这次变化前，项目中仍保留" + object + "。";
            case RESTORED, REAPPLIED -> "在这次变化前，" + object + "处于已移除或已撤销状态。";
            default -> "项目中原本已有" + object + "。";
        };
        String change = switch (transition == null ? Transition.UNKNOWN_TRANSITION : transition) {
            case CREATED -> "这一阶段首次建立" + object + "，并保存了相关内容。";
            case MODIFIED -> "这一阶段补充或调整了" + object + "的现有内容。";
            case REMOVED -> "这一阶段从当前项目结果中移除了" + object + "。";
            case RESTORED -> "这一阶段把此前移除的" + object + "恢复回来。";
            case RENAMED -> "这一阶段更改了" + object + "的名称。";
            case MOVED -> "这一阶段调整了" + object + "的存放位置。";
            case REPLACED -> "这一阶段用新的内容替换了原有" + object + "。";
            case SPLIT -> "这一阶段将" + object + "拆成了可以分别查看的部分。";
            case MERGED -> "这一阶段将" + object + "的相关内容合并呈现。";
            case REVERTED -> "这一阶段撤销了" + object + "的上一项变化。";
            case REAPPLIED -> "这一阶段重新应用了" + object + "此前被撤销的变化。";
            default -> "这一阶段记录并整理了" + object + "的现有变化。";
        };
        String after = switch (transition == null ? Transition.UNKNOWN_TRANSITION : transition) {
            case CREATED -> "项目中已有" + object + "，后续可以继续查看和完善。";
            case MODIFIED -> object + "已更新为当前记录的内容。";
            case REMOVED, REVERTED -> "当前项目结果中已不再包含" + object + "。";
            case RESTORED, REAPPLIED -> object + "已重新出现在当前项目中。";
            case RENAMED, MOVED -> object + "以新的名称或位置继续保留。";
            case REPLACED -> "当前项目采用" + object + "的新内容版本。";
            case SPLIT -> object + "目前可以按不同部分分别查看。";
            case MERGED -> object + "目前以统一内容呈现。";
            default -> object + "的当前状态已记录，仍可继续核对。";
        };
        return new Presentation(title, summary, before, change, after, object);
    }

    public Presentation fallback(
        ProjectHistoryNarrativeEntailmentValidator.ClaimState claimState,
        Transition transition,
        String subject,
        List<String> paths,
        List<String> sourceLabels,
        List<String> transitions
    ) {
        Presentation observed = fallback(transition, subject, paths, sourceLabels, transitions);
        String object = observed.object();
        ProjectHistoryNarrativeEntailmentValidator.ClaimState state = claimState == null
            ? ProjectHistoryNarrativeEntailmentValidator.ClaimState.UNKNOWN : claimState;
        return switch (state) {
            case PLANNED -> new Presentation(
                "规划" + object + "，明确后续建设方向",
                "现有材料记录了" + object + "的目标和范围，但还不能确认已经实现。",
                "此前材料中还没有明确记录" + object + "的规划。",
                "这一阶段在项目材料中补充了" + object + "的规划。",
                "项目已经记录" + object + "的方向，实际实现状态仍需代码证据确认。",
                object
            );
            case DECLARED -> new Presentation(
                "说明" + object + "的设计，形成可查阅的方案",
                "项目材料中已经说明" + object + "的设计内容，实际实现情况仍待确认。",
                "此前还没有完整记录" + object + "的设计说明。",
                "这一阶段补充了" + object + "的设计和范围说明。",
                "项目中已有" + object + "的方案记录，但不能据此判断功能已经实现。",
                object
            );
            case CONFIGURED -> new Presentation(
                "补充" + object + "，完善项目配置基础",
                "这一阶段增加了" + object + "，为后续本地设置提供参考。",
                "此前项目中还没有这份" + object + "。",
                "本次加入了" + object + "及其可参考的配置内容。",
                "项目中已有" + object + "，实际运行状态仍需其他证据确认。",
                object
            );
            case IMPLEMENTED -> new Presentation(
                transition == Transition.MODIFIED ? "完善" + object + "，更新已有实现" : "实现" + object + "，形成可使用的功能",
                "相关代码已经形成" + object + "的实现，具体范围可在工程详情中核对。",
                transition == Transition.CREATED ? "此前代码中还没有" + object + "的实现。" : "此前代码中已经有" + object + "的基础实现。",
                transition == Transition.CREATED ? "这一阶段加入了实现" + object + "所需的代码。" : "这一阶段补充或调整了" + object + "的实现代码。",
                object + "已有代码实现，但稳定性仍需验证证据支持。",
                object
            );
            case VERIFIED -> new Presentation(
                "验证" + object + "，补充自动化检查依据",
                "现有验证来源覆盖了" + object + "，验证范围可在工程详情中继续核对。",
                "此前还没有当前这组针对" + object + "的验证记录。",
                "这一阶段为" + object + "增加或更新了自动化验证。",
                object + "已有自动化验证记录，但不能据此推断生产环境稳定。",
                object
            );
            case UNKNOWN -> new Presentation(
                "整理" + object + "，记录当前能够确认的变化",
                "现有来源只能确认" + object + "发生过变化，具体动作和结果仍不完整。",
                "此前状态缺少足够信息。",
                "这一阶段留下了与" + object + "有关的变化记录。",
                object + "的当前状态仍需要更多来源确认。",
                object
            );
            case REMOVED, RESTORED, OBSERVED -> observed;
        };
    }

    public String readableObject(String subject, List<String> paths, List<String> labels) {
        return subjectLabels.label(subject, paths, labels);
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
        String theme = chapterTheme(storyTitles);
        if (transitions != null && !transitions.isEmpty()
            && transitions.stream().allMatch(value -> value == Transition.CREATED)) {
            return "建立" + theme + "，形成这一阶段的基础";
        }
        if (transitions != null && transitions.stream().anyMatch(value ->
            value == Transition.RESTORED || value == Transition.REAPPLIED)) {
            return "恢复" + theme + "，让相关成果重新可见";
        }
        return "围绕" + theme + "推进阶段成果";
    }

    public String chapterSummary(List<String> storyTitles, int primaryCount, int supportingCount) {
        String theme = chapterTheme(storyTitles);
        String support = supportingCount <= 0 ? "" : "相关支撑工作保留在工程详情中。";
        return "这一时期主要围绕" + theme + "推进，相关成果逐步形成并得到完善。" + support;
    }

    public String unknownWording(boolean sourceStateUnknown) {
        return sourceStateUnknown
            ? "现有信息还不足以完整确认这项变化的状态和原因。"
            : "目前没有足够信息确认为什么做这次调整。";
    }

    public record Presentation(String title, String summary, String before, String change, String after, String object) {
    }

    private String chapterTheme(List<String> storyTitles) {
        List<String> values = storyTitles == null ? List.of() : storyTitles;
        List<String> themes = List.of(
            "项目基础建设", "项目资料接入与理解", "项目历程与证据化理解", "内容与呈现", "成果内容建设", "质量与安全验证"
        );
        int[] scores = new int[themes.size()];
        for (String title : values) {
            String value = title == null ? "" : title.toLowerCase(Locale.ROOT);
            if (containsAny(value, "环境配置", "项目骨架", "忽略规则", "使用说明", "登录流程", "登录入口", "运行环境")) scores[0]++;
            if (containsAny(value, "项目导入", "资料接入", "上下文", "项目文档", "阶段文档")) scores[1]++;
            if (containsAny(value, "项目历程", "证据", "事实", "多模型", "历史理解", "历程")) scores[2]++;
            if (containsAny(value, "界面", "视觉", "设计", "页面", "品牌", "演示")) scores[3]++;
            if (containsAny(value, "研究报告", "研究结论", "数据分析", "数据图表", "收入分析", "项目成果", "成果记录", "内容")) scores[4]++;
            if (containsAny(value, "测试", "验证", "质量", "安全", "回归")) scores[5]++;
        }
        int best = -1;
        for (int index = 0; index < scores.length; index++) if (best < 0 || scores[index] > scores[best]) best = index;
        if (best >= 0 && scores[best] > 0) return themes.get(best);
        return values.stream().map(subjectLabels::safeFocus)
            .filter(value -> !value.isBlank() && !"项目阶段成果".equals(value)).findFirst().orElse("项目阶段成果");
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
