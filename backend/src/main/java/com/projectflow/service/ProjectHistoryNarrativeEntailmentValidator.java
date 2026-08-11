package com.projectflow.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.projectflow.entity.ProjectFactEpistemicStatus;
import com.projectflow.entity.ProjectHistoryEvent.Authority;
import com.projectflow.entity.ProjectHistoryEvent.Category;
import com.projectflow.entity.ProjectHistoryEvent.Transition;

/**
 * Provider-neutral presentation boundary. It validates wording against the
 * strongest state supported by the story's existing events; it never changes
 * facts, membership, chronology or Evidence.
 */
@Component
public final class ProjectHistoryNarrativeEntailmentValidator {
    private static final Pattern RELATIVE_PATH = Pattern.compile(
        "(?i)(?:^|[\\s（(])(?:[.A-Za-z0-9_-]+/)+[.A-Za-z0-9_-]+"
    );
    private static final Pattern FILE_NAME = Pattern.compile(
        "(?i)(?:^|[\\s（(])\\.?[A-Za-z0-9_-]+\\.(?:java|kt|go|rs|py|js|jsx|ts|tsx|md|json|ya?ml|xml|csv|pptx?|docx?|env)\\b"
    );
    private static final Pattern INTERNAL_ENUM = Pattern.compile("\\b[A-Z][A-Z0-9_]{2,}\\b");
    private static final Pattern ASCII_TOKEN = Pattern.compile("\\b[A-Za-z][A-Za-z0-9_.-]{1,}\\b");
    private static final Pattern CAMEL_OR_SNAKE = Pattern.compile(
        "\\b(?:[a-z]+(?:_[a-z0-9]+)+|[A-Z][a-z0-9]+(?:[A-Z][A-Za-z0-9]+)+)\\b"
    );
    private static final Pattern FIXTURE_IDENTIFIER = Pattern.compile(
        "(?i).*\\b(?:outcome|part|fixture|phase|embedded|segment)[-_ ]*\\d+\\b.*"
    );
    private static final Pattern COUNT_LED_CHAPTER = Pattern.compile(
        "^这一(?:阶段|时期)(?:形成|完成|包含)了?\\s*\\d+\\s*项.*"
    );
    private static final Set<String> CODE_EXTENSIONS = Set.of(
        "java", "kt", "kts", "go", "rs", "py", "js", "jsx", "ts", "tsx", "vue", "svelte", "cs", "cpp", "c", "h"
    );
    private static final Set<String> CONFIG_EXTENSIONS = Set.of(
        "yml", "yaml", "toml", "ini", "properties", "conf", "config", "env"
    );
    private static final Set<String> DOCUMENT_EXTENSIONS = Set.of(
        "md", "mdx", "txt", "rst", "adoc", "pdf", "doc", "docx", "ppt", "pptx"
    );
    private static final List<String> PRODUCTION_CLAIMS = List.of(
        "生产可用", "生产环境稳定", "生产部署", "完成部署", "部署完成", "已经上线", "正式上线", "稳定运行", "完全可靠", "成熟可用"
    );
    private static final List<String> VERIFIED_CLAIMS = List.of(
        "验证通过", "已经验证", "已验证", "确认无误", "测试证明", "自动化验证通过"
    );
    private static final List<String> IMPLEMENTED_CLAIMS = List.of(
        "已经实现", "实现了", "完成开发", "具备用户", "用户可以", "可直接使用", "新增了登录功能", "搭建了登录流程"
    );
    private static final List<String> GENERIC_FIRST_LAYER = List.of(
        "相关变化", "工程分组", "形成初始结果", "进入当前时间点可确认的新状态", "修改 n 个文件",
        "当前行为得到更新", "项目开始具备这项能力"
    );

    public NarrativeEnvelope envelope(EvidenceProfile profile) {
        EvidenceProfile safe = profile == null ? EvidenceProfile.empty() : profile;
        ClaimState state = classify(safe);
        return new NarrativeEnvelope(
            state,
            text(safe.subjectLabel()).isBlank() ? "项目材料" : text(safe.subjectLabel()),
            allowedClaims(state),
            forbiddenClaims(state),
            humanSafeContext(safe),
            safe.reasonEligible()
        );
    }

    public void validateStory(
        NarrativeEnvelope envelope,
        String title,
        String summary,
        String before,
        String change,
        String after,
        String reason,
        String unknown
    ) {
        NarrativeEnvelope safe = envelope == null ? this.envelope(EvidenceProfile.empty()) : envelope;
        List<String> fields = List.of(text(title), text(summary), text(before), text(change), text(after));
        if (fields.stream().anyMatch(String::isBlank)) {
            throw violation(ViolationKind.CONTRACT, "Narrative wording is incomplete");
        }
        String firstLayer = String.join(" ", fields) + " " + text(unknown);
        if (containsFirstLayerLeak(firstLayer, safe.humanSafeSourceContext())) {
            throw violation(ViolationKind.FIRST_LAYER_LEAK, "Narrative wording exposes an internal subject or path");
        }
        if (containsAny(firstLayer, GENERIC_FIRST_LAYER)) {
            throw violation(ViolationKind.INTERNAL_LANGUAGE, "Narrative wording uses a generic internal template");
        }
        if (!mentionsSubject(title + " " + summary, safe.subjectLabel())) {
            throw violation(ViolationKind.UNSUPPORTED_OBJECT, "Narrative wording is not anchored to the allowed subject");
        }
        if (stateUpgrade(firstLayer, safe.claimState())) {
            throw violation(ViolationKind.STATE_UPGRADE, "Narrative claim is stronger than its Evidence state");
        }
        if (!text(reason).isBlank() && !safe.reasonEligible()) {
            throw violation(ViolationKind.REASON_WITHOUT_EVIDENCE, "Narrative reason has no eligible Evidence");
        }
        if (text(unknown).contains("UNKNOWN") || text(unknown).contains("Evidence") || text(unknown).contains("reason eligibility")) {
            throw violation(ViolationKind.INTERNAL_LANGUAGE, "Narrative unknown wording exposes internal language");
        }
        if (repetitionRate(fields) > 0.0) {
            throw violation(ViolationKind.REPETITION, "Narrative fields repeat the same wording");
        }
    }

    public void validateChapter(String title, String summary, List<String> primaryStoryWording) {
        String safeTitle = text(title);
        String safeSummary = text(summary);
        List<String> context = values(primaryStoryWording);
        if (safeTitle.isBlank() || safeSummary.isBlank()) {
            throw violation(ViolationKind.CONTRACT, "Chapter wording is incomplete");
        }
        String firstLayer = safeTitle + " " + safeSummary;
        if (containsFirstLayerLeak(firstLayer, context)) {
            throw violation(ViolationKind.FIRST_LAYER_LEAK, "Chapter wording exposes an internal subject or path");
        }
        if (containsAny(firstLayer, GENERIC_FIRST_LAYER)) {
            throw violation(ViolationKind.INTERNAL_LANGUAGE, "Chapter wording uses a generic internal template");
        }
        if (safeTitle.contains("在这一阶段继续完善") || safeTitle.matches(".*[^，。]{2,}与[^，。]{2,}等?可确认结果.*")
            || safeSummary.contains("主要包括") || COUNT_LED_CHAPTER.matcher(safeSummary).matches()) {
            throw violation(ViolationKind.SUBJECT_CONCATENATION, "Chapter wording is a subject list instead of a phase narrative");
        }
        if (!context.isEmpty() && !sharesHanBigram(firstLayer, String.join(" ", context))) {
            throw violation(ViolationKind.UNSUPPORTED_OBJECT, "Chapter wording is not supported by its Primary stories");
        }
        if (containsAffirmedAny(firstLayer, PRODUCTION_CLAIMS)) {
            throw violation(ViolationKind.STATE_UPGRADE, "Chapter wording invents unsupported maturity");
        }
    }

    public List<String> normalizeUnknowns(String modelUnknown, boolean sourceStateUnknown) {
        String value = text(modelUnknown);
        if (value.isBlank() || value.contains("UNKNOWN") || value.contains("Evidence")) {
            value = sourceStateUnknown
                ? "现有信息还不足以完整确认这项变化的状态和原因。"
                : "目前没有足够信息确认为什么做这次调整。";
        }
        return List.of(value);
    }

    public boolean containsFirstLayerLeak(String value, List<String> allowedContext) {
        String safe = text(value);
        if (safe.isBlank()) return false;
        if (safe.contains("…") || safe.contains("...") || RELATIVE_PATH.matcher(safe).find()
            || FILE_NAME.matcher(safe).find() || CAMEL_OR_SNAKE.matcher(safe).find()
            || FIXTURE_IDENTIFIER.matcher(safe).matches()) return true;
        // Reason/source context may help the model phrase an eligible reason,
        // but it must never whitelist raw technical tokens in the first layer.
        // Only the normalized public subject can authorize an ASCII token.
        String publicSubject = values(allowedContext).stream().findFirst().orElse("");
        Set<String> allowedAscii = asciiTokens(publicSubject);
        Matcher enumMatcher = INTERNAL_ENUM.matcher(safe);
        while (enumMatcher.find()) if (!allowedAscii.contains(enumMatcher.group().toLowerCase(Locale.ROOT))) return true;
        Matcher tokenMatcher = ASCII_TOKEN.matcher(safe);
        while (tokenMatcher.find()) {
            String token = tokenMatcher.group().toLowerCase(Locale.ROOT);
            if (!allowedAscii.contains(token)) return true;
        }
        return false;
    }

    public double repetitionRate(List<String> fields) {
        List<String> values = values(fields).stream().map(ProjectHistoryNarrativeEntailmentValidator::normalize)
            .filter(value -> !value.isBlank()).toList();
        int repeated = 0;
        int pairs = 0;
        for (int left = 0; left < values.size(); left++) {
            for (int right = left + 1; right < values.size(); right++) {
                pairs++;
                if (values.get(left).equals(values.get(right)) || bigramSimilarity(values.get(left), values.get(right)) >= 0.86) {
                    repeated++;
                }
            }
        }
        return pairs == 0 ? 0.0 : (double) repeated / pairs;
    }

    private static ClaimState classify(EvidenceProfile profile) {
        Transition transition = profile.transition() == null ? Transition.UNKNOWN_TRANSITION : profile.transition();
        if (transition == Transition.REMOVED || transition == Transition.REVERTED) return ClaimState.REMOVED;
        if (transition == Transition.RESTORED || transition == Transition.REAPPLIED) return ClaimState.RESTORED;

        List<String> paths = values(profile.paths());
        String labels = String.join(" ", values(profile.sourceLabels())).toLowerCase(Locale.ROOT);
        boolean hasStrong = values(profile.epistemicStatuses()).stream().anyMatch(ProjectFactEpistemicStatus::isStrongFact);
        boolean hasDeclared = values(profile.authorities()).contains(Authority.DECLARED)
            || values(profile.epistemicStatuses()).contains(ProjectFactEpistemicStatus.DECLARED);
        boolean planned = containsAny(labels, "plan", "roadmap", "proposal", "will support", "计划", "规划", "后续", "方案");
        boolean validation = values(profile.categories()).contains(Category.VALIDATION) && hasStrong;
        boolean documentsOnly = !paths.isEmpty() && paths.stream().allMatch(path -> DOCUMENT_EXTENSIONS.contains(extension(path)));
        boolean configsOnly = !paths.isEmpty() && paths.stream().allMatch(ProjectHistoryNarrativeEntailmentValidator::configurationPath);
        boolean implementationAnchor = directImplementationAnchor(profile.subjectLabel(), paths, values(profile.sourceLabels()));
        boolean artifact = containsAny(text(profile.subjectLabel()),
            "报告", "数据", "演示文稿", "设计稿", "页面", "视频", "文档", "说明", "成果记录", "项目骨架", "配置示例", "忽略规则"
        );

        if (planned) return ClaimState.PLANNED;
        if (hasDeclared && !hasStrong) return ClaimState.DECLARED;
        if (documentsOnly && !artifact) return ClaimState.DECLARED;
        if (validation && implementationAnchor) return ClaimState.VERIFIED;
        if (configsOnly) return ClaimState.CONFIGURED;
        if (implementationAnchor && !containsAny(text(profile.subjectLabel()), "项目骨架")) return ClaimState.IMPLEMENTED;
        if (hasStrong || artifact) return ClaimState.OBSERVED;
        return ClaimState.UNKNOWN;
    }

    private static boolean directImplementationAnchor(String subjectLabel, List<String> paths, List<String> sourceLabels) {
        String subject = text(subjectLabel).toLowerCase(Locale.ROOT);
        String pathText = String.join(" ", paths).replace('\\', '/').toLowerCase(Locale.ROOT);
        String labelText = String.join(" ", sourceLabels).toLowerCase(Locale.ROOT);
        boolean code = paths.stream().anyMatch(path -> CODE_EXTENSIONS.contains(extension(path)));
        if (!code) return false;
        if (containsAny(subject, "登录", "认证")) {
            return containsAny(pathText, "/auth", "auth/", "login", "signin", "sign-in", "oauth")
                || containsAny(labelText, "实现登录", "login flow", "auth flow", "登录页面", "登录接口");
        }
        return true;
    }

    private static boolean configurationPath(String path) {
        String safe = text(path).replace('\\', '/').toLowerCase(Locale.ROOT);
        String extension = extension(safe);
        return CONFIG_EXTENSIONS.contains(extension) || safe.endsWith(".env.example") || safe.endsWith(".gitignore")
            || safe.endsWith("package.json") || safe.endsWith("pom.xml") || safe.contains("/config/");
    }

    private static boolean stateUpgrade(String wording, ClaimState state) {
        if (containsAffirmedAny(wording, PRODUCTION_CLAIMS)) return true;
        return switch (state) {
            case PLANNED, DECLARED -> containsAffirmedAny(wording, IMPLEMENTED_CLAIMS)
                || containsAffirmedAny(wording, VERIFIED_CLAIMS);
            case CONFIGURED -> containsAffirmedAny(wording, List.of("部署完成", "完成部署", "用户可以", "可直接使用"))
                || containsAffirmedAny(wording, VERIFIED_CLAIMS);
            case IMPLEMENTED, OBSERVED, REMOVED, RESTORED -> containsAffirmedAny(wording, VERIFIED_CLAIMS);
            case UNKNOWN -> containsAffirmedAny(wording, IMPLEMENTED_CLAIMS)
                || containsAffirmedAny(wording, VERIFIED_CLAIMS);
            case VERIFIED -> false;
        };
    }

    private static boolean containsAffirmedAny(String value, List<String> markers) {
        String safe = text(value);
        for (String marker : markers) {
            int from = 0;
            while (from < safe.length()) {
                int index = safe.indexOf(marker, from);
                if (index < 0) break;
                int contextStart = Math.max(0, index - 14);
                String prefix = safe.substring(contextStart, index);
                if (!containsAny(prefix,
                    "不能确认", "无法确认", "尚不能确认", "还不能确认", "未能确认", "不能据此判断",
                    "无法据此判断", "没有证据表明", "没有证据证明", "不代表", "并不表示", "并非", "不是"
                )) return true;
                from = index + marker.length();
            }
        }
        return false;
    }

    private static boolean mentionsSubject(String wording, String subjectLabel) {
        String subject = text(subjectLabel);
        if (subject.isBlank() || "项目材料".equals(subject)) return wording.contains("项目") || wording.contains("材料");
        if (wording.contains(subject)) return true;
        Set<String> subjectBigrams = hanBigrams(subject);
        Set<String> wordingBigrams = hanBigrams(wording);
        subjectBigrams.retainAll(wordingBigrams);
        return !subjectBigrams.isEmpty();
    }

    private static List<String> humanSafeContext(EvidenceProfile profile) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        result.add(text(profile.subjectLabel()));
        values(profile.sourceLabels()).stream().map(ProjectHistoryNarrativeEntailmentValidator::text)
            .filter(value -> containsHan(value) && !value.contains("/") && !value.contains("\\") && !value.contains("…"))
            .filter(value -> !FIXTURE_IDENTIFIER.matcher(value).matches())
            .limit(5).forEach(result::add);
        return List.copyOf(result);
    }

    private static List<String> allowedClaims(ClaimState state) {
        return switch (state) {
            case PLANNED -> List.of("只能表达规划、目标或后续方向", "不能表达已经实现");
            case DECLARED -> List.of("只能表达材料中已经说明或设计的内容", "不能表达功能已经可用");
            case CONFIGURED -> List.of("只能表达配置或模板已经形成", "不能表达部署或生产可用");
            case IMPLEMENTED -> List.of("可以表达代码已经实现", "没有验证 Evidence 时不能表达验证通过或稳定");
            case OBSERVED -> List.of("只能表达来源中直接观察到的成果或变化", "不能升级为验证通过");
            case VERIFIED -> List.of("可以表达已有自动化验证", "不能表达生产环境稳定");
            case REMOVED -> List.of("只能表达内容已移除或回退");
            case RESTORED -> List.of("只能表达此前内容已恢复或重新出现");
            case UNKNOWN -> List.of("只能保守表达已有记录", "不能表达实现或验证完成");
        };
    }

    private static List<String> forbiddenClaims(ClaimState state) {
        List<String> result = new ArrayList<>(PRODUCTION_CLAIMS);
        if (state != ClaimState.VERIFIED) result.addAll(VERIFIED_CLAIMS);
        if (state == ClaimState.PLANNED || state == ClaimState.DECLARED || state == ClaimState.UNKNOWN) {
            result.addAll(IMPLEMENTED_CLAIMS);
        }
        return List.copyOf(new LinkedHashSet<>(result));
    }

    private static boolean sharesHanBigram(String left, String right) {
        Set<String> leftValues = hanBigrams(left);
        leftValues.retainAll(hanBigrams(right));
        return !leftValues.isEmpty();
    }

    private static double bigramSimilarity(String left, String right) {
        Set<String> leftValues = bigrams(left);
        Set<String> rightValues = bigrams(right);
        if (leftValues.isEmpty() || rightValues.isEmpty()) return 0.0;
        Set<String> intersection = new LinkedHashSet<>(leftValues);
        intersection.retainAll(rightValues);
        Set<String> union = new LinkedHashSet<>(leftValues);
        union.addAll(rightValues);
        return (double) intersection.size() / Math.max(1, union.size());
    }

    private static Set<String> bigrams(String value) {
        String safe = normalize(value);
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (int index = 0; index + 1 < safe.length(); index++) result.add(safe.substring(index, index + 2));
        return result;
    }

    private static Set<String> hanBigrams(String value) {
        String safe = text(value).replaceAll("[^\\p{IsHan}]", "");
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (int index = 0; index + 1 < safe.length(); index++) result.add(safe.substring(index, index + 2));
        return result;
    }

    private static Set<String> asciiTokens(String value) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        Matcher matcher = ASCII_TOKEN.matcher(text(value));
        while (matcher.find()) result.add(matcher.group().toLowerCase(Locale.ROOT));
        return result;
    }

    private static String normalize(String value) {
        return text(value).toLowerCase(Locale.ROOT).replaceAll("[\\p{Punct}\\s，。；：！？、（）]", "");
    }

    private static String extension(String path) {
        String safe = text(path).replace('\\', '/');
        int slash = safe.lastIndexOf('/');
        int dot = safe.lastIndexOf('.');
        return dot > slash ? safe.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    private static <T> List<T> values(List<T> values) {
        return values == null ? List.of() : values.stream().filter(java.util.Objects::nonNull).toList();
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean containsAny(String value, List<String> markers) {
        return containsAny(value, markers.toArray(String[]::new));
    }

    private static boolean containsAny(String value, String... markers) {
        String safe = value == null ? "" : value.toLowerCase(Locale.ROOT);
        for (String marker : markers) if (safe.contains(marker.toLowerCase(Locale.ROOT))) return true;
        return false;
    }

    private static boolean containsHan(String value) {
        return value != null && value.codePoints().anyMatch(codePoint -> codePoint >= 0x3400 && codePoint <= 0x9FFF);
    }

    private static NarrativeViolation violation(ViolationKind kind, String message) {
        return new NarrativeViolation(kind, message);
    }

    public enum ClaimState {
        PLANNED,
        DECLARED,
        CONFIGURED,
        IMPLEMENTED,
        OBSERVED,
        VERIFIED,
        REMOVED,
        RESTORED,
        UNKNOWN
    }

    public enum ViolationKind {
        CONTRACT,
        FIRST_LAYER_LEAK,
        INTERNAL_LANGUAGE,
        UNSUPPORTED_OBJECT,
        STATE_UPGRADE,
        REASON_WITHOUT_EVIDENCE,
        REPETITION,
        SUBJECT_CONCATENATION
    }

    public record EvidenceProfile(
        String subjectLabel,
        Transition transition,
        List<Category> categories,
        List<Authority> authorities,
        List<ProjectFactEpistemicStatus> epistemicStatuses,
        List<String> paths,
        List<String> sourceLabels,
        boolean reasonEligible
    ) {
        public EvidenceProfile {
            categories = values(categories);
            authorities = values(authorities);
            epistemicStatuses = values(epistemicStatuses);
            paths = values(paths);
            sourceLabels = values(sourceLabels);
        }

        public static EvidenceProfile empty() {
            return new EvidenceProfile("项目材料", Transition.UNKNOWN_TRANSITION, List.of(), List.of(), List.of(), List.of(), List.of(), false);
        }
    }

    public record NarrativeEnvelope(
        ClaimState claimState,
        String subjectLabel,
        List<String> allowedClaims,
        List<String> forbiddenClaims,
        List<String> humanSafeSourceContext,
        boolean reasonEligible
    ) {
    }

    public static final class NarrativeViolation extends IllegalArgumentException {
        private final ViolationKind kind;

        private NarrativeViolation(ViolationKind kind, String message) {
            super(message);
            this.kind = kind;
        }

        public ViolationKind kind() {
            return kind;
        }
    }
}
