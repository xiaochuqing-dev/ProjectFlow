package com.projectflow.service;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Converts bounded evidence labels into a Chinese-first first-layer concept.
 * Raw paths and identifiers remain available in engineering detail, never as
 * the default subject label.
 */
@Component
public final class ProjectHistoryHumanSubjectLabelService {
    private static final Set<String> AUTH_SUBJECT_KEYS = Set.of(
        "auth", "authentication", "login", "sign-in", "signin", "oauth"
    );
    private static final Set<String> SLIDE_EXTENSIONS = Set.of("ppt", "pptx", "key", "odp");
    private static final Set<String> DOCUMENT_EXTENSIONS = Set.of(
        "doc", "docx", "pdf", "md", "mdx", "txt", "rst", "adoc", "tex"
    );
    private static final Set<String> DATA_EXTENSIONS = Set.of("csv", "tsv", "xls", "xlsx", "json", "parquet", "arrow");
    private static final Set<String> VIDEO_EXTENSIONS = Set.of("mp4", "mov", "mkv", "webm", "avi");
    private static final Set<String> DESIGN_EXTENSIONS = Set.of("fig", "sketch", "psd", "ai", "xd", "svg", "png", "jpg", "jpeg");
    private static final Set<String> CODE_EXTENSIONS = Set.of(
        "java", "kt", "kts", "go", "rs", "py", "js", "jsx", "ts", "tsx", "vue", "svelte", "cs", "cpp", "c", "h"
    );
    private static final Pattern FIXTURE_IDENTIFIER = Pattern.compile(
        "(?i).*(?:outcome|part|fixture|phase|embedded|segment)[-_ ]*\\d+.*"
    );
    private static final Pattern INDEXED_PLACEHOLDER_IDENTIFIER = Pattern.compile(
        ".*主题[-_ ]*\\d{3,}内容[-_ ]*\\d{3,}.*"
    );
    private static final Pattern VERSION_DOCUMENT_IDENTIFIER = Pattern.compile(
        "(?i)^v?\\d+(?:[ ._-]+\\d+)+(?:[ ._-]+.*)?$"
    );
    private static final Pattern TECHNICAL_TYPE = Pattern.compile(
        "(?i).*(?:controller|repository|dto|entity|mapper|handler|adapter|provider|service)$"
    );

    public String label(String subject, List<String> paths, List<String> sourceLabels) {
        List<String> safePaths = values(paths);
        List<String> safeLabels = values(sourceLabels);
        String rawSubject = text(subject);
        String subjectSample = rawSubject.toLowerCase(Locale.ROOT);
        String pathSample = String.join(" ", safePaths).toLowerCase(Locale.ROOT);
        String labelSample = String.join(" ", safeLabels).toLowerCase(Locale.ROOT);
        String combined = subjectSample + " " + pathSample + " " + labelSample;
        String semanticText = subjectSample + " " + labelSample;
        if (subjectSample.matches("(?:pull-request|issue)-\\d+")) {
            String number = subjectSample.substring(subjectSample.lastIndexOf('-') + 1);
            String type = subjectSample.startsWith("pull-request-") ? "合并请求" : "议题";
            List<String> titles = safeLabels.stream().map(value -> value.split("；说明：", 2)[0]
                .replaceFirst("(?i)^(?:Pull Request|Issue)\\s*#\\d+[：:]?\\s*", "")).toList();
            String topic = sourceObject(titles);
            if (topic.isBlank()) topic = concreteObjects(List.of(), String.join(" ", titles));
            return type + number + (topic.isBlank() ? "的变更说明" : "（" + topic + "）");
        }
        String skeleton = skeletonLabel(safePaths);
        Set<String> pathSubjectKeys = safePaths.stream()
            .map(ProjectHistorySourceCollector::historySubjectKey)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        boolean subjectPathAnchored = pathSubjectKeys.contains(subjectSample);
        boolean authPathAnchored = safePaths.stream().anyMatch(path -> CODE_EXTENSIONS.contains(extension(path)))
            && pathSubjectKeys.stream().anyMatch(AUTH_SUBJECT_KEYS::contains);

        // Name examples within the area, never rename the area's ownership or
        // infer that an entire feature was implemented from a filename.
        List<String> objectPaths = subjectSample.startsWith("project-area-") ? safePaths
            : safePaths.stream().filter(path -> ProjectHistorySourceCollector.historySubjectKey(path).equals(subjectSample)).toList();
        String concrete = objectPaths.isEmpty() ? "" : concreteObjects(objectPaths, rawSubject);
        if (!concrete.isBlank() && !skeleton.isBlank()) {
            String area = skeleton.replace("项目骨架", "代码");
            return subjectSample.startsWith("project-area-")
                ? area + "（含" + concrete + "相关文件）" : concrete + "相关代码";
        }
        if (subjectSample.startsWith("project-area-") && !skeleton.isBlank()) return skeleton;

        // A commit-scoped change owns its changed-path inventory. Name examples
        // from that inventory without claiming a particular feature is complete.
        if (subjectSample.startsWith("change-")
            && safePaths.stream().anyMatch(path -> CODE_EXTENSIONS.contains(extension(path)))) {
            String changedObjects = concreteObjects(safePaths, "");
            if (!changedObjects.isBlank()) return "相关代码（含" + changedObjects + "文件）";
            return "一次提交的代码与配套文件";
        }

        String sourceFile = subjectPathAnchored && objectPaths.size() == 1 ? objectPaths.get(0)
            : safePaths.size() == 1 ? safePaths.get(0) : "";
        String fileName = sourceFile.replace('\\', '/');
        fileName = fileName.substring(fileName.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
        if (fileName.equals("third_party_notices.md")) return "第三方组件声明";
        if (fileName.equals(".gitattributes")) return "版本库文本与文件属性规则";
        if (fileName.equals("version")) return "项目版本记录";
        if (fileName.matches("(?:docker-)?compose\\.ya?ml")) return "容器服务编排配置";
        if (sourceFile.replace('\\', '/').startsWith(".github/workflows/")) return "持续集成工作流配置";
        if (documentOnly(safePaths) && !sourceFile.isBlank()
            && (fileName.contains("report") || fileName.contains("validation"))) {
            String topic = fileName.contains("reliability") ? "运行可靠性"
                : fileName.contains("closeout") || fileName.contains("closure") ? "阶段收尾"
                : fileName.contains("validation") ? "验证记录" : "";
            java.util.regex.Matcher task = Pattern.compile("(?i)task[-_ ]?(\\d+)").matcher(fileName);
            java.util.regex.Matcher version = Pattern.compile("(?i)(?:^|[-_])v(\\d+(?:\\.\\d+)*)").matcher(fileName);
            String taskLabel = task.find() ? "任务" + task.group(1) : "";
            String versionLabel = version.find() ? "版本" + version.group(1) : "";
            String title = topic + (topic.isBlank() ? taskLabel : "")
                + (fileName.contains("report") ? "报告" : "文档");
            List<String> qualifiers = new java.util.ArrayList<>();
            if (!versionLabel.isBlank()) qualifiers.add(versionLabel);
            if (!topic.isBlank() && !taskLabel.isBlank()) qualifiers.add(taskLabel);
            if (!topic.isBlank() || !taskLabel.isBlank())
                return title + (qualifiers.isEmpty() ? "" : "（" + String.join(" · ", qualifiers) + "）");
        }

        if (isEnvironmentExample(subjectSample) || isEnvironmentExample(sourceFile)) return "环境配置示例";
        if (containsAny(combined, ".gitignore", "gitignore", "ignore rules", "忽略规则")) return "版本库忽略规则";
        if (containsAny(semanticText, "readme", "getting started", "使用说明")) return "项目使用说明";
        if (containsAny(semanticText, "research report", "research-report", "researchreport", "研究报告")) return "研究报告";
        if (containsAny(semanticText, "core experience", "core-experience", "核心体验")) return "核心使用体验";
        if (containsAny(semanticText, "customer service", "customer-service", "客户服务")) return "客户服务研究";
        if (containsAny(semanticText, "project outcome", "project-outcome", "项目成果")) return "项目成果";
        if (FIXTURE_IDENTIFIER.matcher(subjectSample).matches()
            || INDEXED_PLACEHOLDER_IDENTIFIER.matcher(rawSubject).matches()
            || subjectSample.matches(".*outcome\\d+.*")) {
            return "项目成果记录";
        }
        if (containsAny(semanticText, "project import", "project-import", "项目导入")) return "项目导入与资料接入";
        if (containsAny(semanticText, "ui design direction", "design direction", "界面设计方向")) return "界面设计方向";
        if (containsAny(semanticText, "project-history", "project history", "timeline", "项目历程")) return "项目历程";
        if (containsAny(semanticText, "export", "download", "导出")) return "成果导出";
        if (authPathAnchored && (AUTH_SUBJECT_KEYS.contains(subjectSample) || genericSubject(subjectSample))) {
            return "登录流程";
        }

        if (!subjectPathAnchored && !skeleton.isBlank()) return skeleton;

        if (!skeleton.isBlank() && genericSubject(subjectSample)) return skeleton;

        if (VERSION_DOCUMENT_IDENTIFIER.matcher(subjectSample).matches()
            || containsAny(subjectSample, "phase0", "phase1", "phase2", "phase3")
            || rawSubject.contains("…")) {
            return documentOnly(safePaths) ? "项目阶段文档" : "阶段成果记录";
        }
        if (("data".equals(subjectSample) || containsAny(subjectSample, "data result", "analysis data"))
            && safePaths.stream().anyMatch(path -> DATA_EXTENSIONS.contains(extension(path)))) {
            return "数据分析结果";
        }

        String sourceObject = sourceObject(safeLabels);
        if (documentOnly(safePaths) && !sourceObject.isBlank()
            && !Set.of("项目文档", "项目材料", "项目阶段文档", "报告文档").contains(sourceObject))
            return sourceObject.endsWith("文档") || sourceObject.endsWith("记录") ? sourceObject : sourceObject + "的文档记录";
        if (!concrete.isBlank() && documentOnly(safePaths)) return concrete + "文档";
        if (!concrete.isBlank() && safePaths.stream().anyMatch(path -> CODE_EXTENSIONS.contains(extension(path))))
            return concrete + "相关代码";
        String artifact = artifactLabel(rawSubject, safePaths);
        if (!artifact.isBlank()) return artifact;
        if (!skeleton.isBlank()) return skeleton;

        if (!sourceObject.isBlank()) return sourceObject;
        if (subjectPathAnchored && safePaths.stream().anyMatch(path -> CODE_EXTENSIONS.contains(extension(path)))) {
            return "源码功能";
        }

        String human = humanize(rawSubject);
        if (containsHan(human) && safeHumanLabel(human)) return boundedWords(human, 28);
        return "项目材料";
    }

    /** Small translation vocabulary for bounded source identifiers, not a
     * project classifier. Unknown vocabulary stays unknown; labels confer no
     * functional or runtime authority. Directory names do not supply objects. */
    static String concreteObjects(List<String> paths, String subject) {
        String sample = String.join(" ", paths.stream().map(path -> {
            String safe = path.replace('\\', '/');
            return safe.substring(safe.lastIndexOf('/') + 1);
        }).toList()) + " " + (paths.isEmpty() && !subject.startsWith("project-area-") ? subject : "");
        sample = sample.replaceAll("([a-z0-9])([A-Z])", "$1 $2").toLowerCase(Locale.ROOT);
        LinkedHashSet<String> result = new LinkedHashSet<>();
        // Stable order and a three-object ceiling keep bulk changes readable.
        String[][] vocabulary = {
            {"notification", "通知"}, {"communication", "沟通"}, {"attachment", "附件"},
            {"understanding", "项目理解"}, {"workline", "开发工作线"}, {"workspace", "工作区"},
            {"history", "项目历程"}, {"evidence", "来源证据"}, {"credential", "凭据保护"},
            {"intake", "资料接入"}, {"provider", "模型配置"}, {"telemetry", "调用诊断"},
            {"collaboration", "协作"}, {"artifact", "成果交付"}, {"submission", "提交审核"},
            {"speech", "语音输入"}, {"invoice", "发票"}, {"payment", "支付"},
            {"inventory", "库存"}, {"schedule", "日程"}, {"search", "搜索"},
            {"upload", "上传"}, {"download", "下载"}, {"export", "导出"},
            {"import", "导入"}, {"backup", "备份"}, {"storage", "存储"},
            {"authentication", "身份验证"}, {"login", "登录"}, {"permission", "权限"},
            {"report", "报告"}, {"review", "审阅"}, {"conversation", "对话"},
            {"chat", "对话"}, {"session", "会话"}, {"memory", "项目记忆"},
            {"capability", "能力地图"}, {"timeline", "项目历程"}, {"migration", "数据迁移"}
        };
        for (String[] entry : vocabulary) {
            if (Pattern.compile("(?<![a-z])" + entry[0] + "(?:s|ing|d)?(?![a-z])").matcher(sample).find()) {
                result.add(entry[1]);
                if (result.size() == 3) break;
            }
        }
        return String.join("、", result);
    }

    public String safeFocus(String value) {
        String safe = text(value);
        if (safe.isBlank()) return "项目阶段成果";
        if (safe.contains("（含") && safe.contains("相关文件）")) {
            return safe.substring(safe.indexOf("（含") + 2, safe.indexOf("相关文件）"));
        }
        String mapped = label(safe, List.of(), List.of());
        if (!"项目材料".equals(mapped)) return mapped;
        String human = humanize(safe.replaceFirst(
            "^(新增|建立|完成|更新|完善|移除|恢复|重命名|调整|替换|拆分|合并|撤销|重新加入|记录|推进|围绕)", ""
        ));
        int comma = human.indexOf('，');
        if (comma > 0) human = human.substring(0, comma);
        return containsHan(human) && safeHumanLabel(human) ? boundedWords(human, 24) : "项目阶段成果";
    }

    private static String skeletonLabel(List<String> paths) {
        String sample = String.join(" ", paths).replace('\\', '/').toLowerCase(Locale.ROOT);
        boolean frontend = containsAny(sample, "frontend/", "next.config", "next-env", "package.json", "postcss");
        boolean backend = containsAny(sample, "backend/", "pom.xml", "application.yml", "application.yaml");
        if (frontend && backend) return "前后端项目骨架";
        if (frontend) return "前端项目骨架";
        if (backend) return "后端项目骨架";
        return "";
    }

    private static String artifactLabel(String subject, List<String> paths) {
        if (paths.isEmpty()) return "";
        String extension = extension(paths.get(0));
        String candidate = humanize(subject);
        if (!containsHan(candidate) || !safeHumanLabel(candidate)) candidate = "";
        if (SLIDE_EXTENSIONS.contains(extension)) return named(candidate, "项目演示文稿");
        if (DOCUMENT_EXTENSIONS.contains(extension)) return named(candidate, "项目文档");
        if (DATA_EXTENSIONS.contains(extension)) return named(candidate, "数据分析结果");
        if (VIDEO_EXTENSIONS.contains(extension)) return named(candidate, "项目视频");
        if (DESIGN_EXTENSIONS.contains(extension)) return named(candidate, "设计稿");
        if ("html".equals(extension) || "htm".equals(extension)) return named(candidate, "项目页面");
        return "";
    }

    private static String named(String candidate, String fallback) {
        return candidate == null || candidate.isBlank() ? fallback : boundedWords(candidate, 20) + fallbackSuffix(candidate, fallback);
    }

    private static String sourceObject(List<String> labels) {
        for (String label : labels) {
            String safe = text(label);
            // Version and conventional commit prefixes are context, not paths.
            safe = safe.replaceFirst("(?i)^(?:feat|fix|docs|test|chore)(?:\\([^)]{1,50}\\))?:\\s*", "")
                .replaceAll("(?i)\\b(?:v?\\d+(?:\\.\\d+)+(?:[-.]?[a-z])?|agent)\\b", "").trim();
            if (!containsHan(safe) || safe.contains("/") || safe.contains("\\")
                || safe.codePoints().anyMatch(codePoint -> codePoint < 128 && Character.isLetter(codePoint))) continue;
            safe = safe.replaceFirst(
                "^(新增|建立|完成|更新|完善|移除|恢复|重命名|调整|替换|拆分|合并|撤销|重新加入|记录|规划|说明|补充|实现|验证|整理|编写)", ""
            ).trim();
            int boundary = safe.indexOf('，');
            if (boundary > 0) safe = safe.substring(0, boundary).trim();
            if (safeHumanLabel(safe)) return boundedWords(safe, 24);
        }
        return "";
    }

    private static String fallbackSuffix(String candidate, String fallback) {
        if (fallback.endsWith("文档") && candidate.endsWith("报告")) return "";
        if (candidate.endsWith("文档") || candidate.endsWith("报告") || candidate.endsWith("演示文稿")
            || candidate.endsWith("设计稿") || candidate.endsWith("页面") || candidate.endsWith("视频")) return "";
        String type = fallback.startsWith("项目") ? fallback.substring(2) : fallback;
        return type.isBlank() ? "" : type;
    }

    private static boolean documentOnly(List<String> paths) {
        return !paths.isEmpty() && paths.stream().allMatch(path -> DOCUMENT_EXTENSIONS.contains(extension(path)));
    }

    private static boolean isEnvironmentExample(String value) {
        return containsAny(value, ".env.example", "env example", "env-example", "environment example", "环境配置示例");
    }

    private static boolean genericSubject(String subject) {
        return subject.isBlank() || subject.startsWith("project-area-")
            || Set.of("project", "frontend", "backend", "skeleton", "project material").contains(subject);
    }

    private static String humanize(String value) {
        String safe = text(value);
        if (safe.isBlank()) return "";
        safe = safe.replaceAll("([a-z0-9])([A-Z])", "$1 $2").replace('-', ' ').replace('_', ' ');
        safe = safe.replaceAll(
            "(?i)\\b(project area|project subject|unknown subject|project change|project content|change)\\b", " "
        );
        safe = safe.replaceAll("(?i)\\b(controller|repository|dto|entity|mapper|handler|adapter|provider|service)\\b", " ");
        return safe.replaceAll("\\s+", " ").trim();
    }

    private static boolean safeHumanLabel(String value) {
        String safe = text(value);
        return !safe.isBlank() && !safe.contains("…") && !safe.contains("/") && !safe.contains("\\")
            && safe.codePoints().noneMatch(codePoint -> codePoint < 128 && Character.isLetter(codePoint))
            && !FIXTURE_IDENTIFIER.matcher(safe).matches() && !TECHNICAL_TYPE.matcher(safe).matches()
            && !INDEXED_PLACEHOLDER_IDENTIFIER.matcher(safe).matches()
            && !VERSION_DOCUMENT_IDENTIFIER.matcher(safe.toLowerCase(Locale.ROOT)).matches();
    }

    private static String boundedWords(String value, int max) {
        String safe = text(value);
        if (safe.length() <= max) return safe;
        int boundary = safe.lastIndexOf(' ', max);
        if (boundary < max / 2) return "项目阶段成果";
        return safe.substring(0, boundary).trim();
    }

    private static String extension(String path) {
        String safe = text(path).replace('\\', '/');
        int slash = safe.lastIndexOf('/');
        int dot = safe.lastIndexOf('.');
        return dot > slash ? safe.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    private static List<String> values(List<String> values) {
        return values == null ? List.of() : values.stream().filter(value -> value != null && !value.isBlank()).toList();
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean containsAny(String value, String... markers) {
        String safe = value == null ? "" : value.toLowerCase(Locale.ROOT);
        for (String marker : markers) if (safe.contains(marker.toLowerCase(Locale.ROOT))) return true;
        return false;
    }

    private static boolean containsHan(String value) {
        return value != null && value.codePoints().anyMatch(codePoint -> codePoint >= 0x3400 && codePoint <= 0x9FFF);
    }
}
