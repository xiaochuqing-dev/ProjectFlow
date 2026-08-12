package com.projectflow.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
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
    private final ProjectHistoryClaimEvidenceAttributionService attributionService;
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
    private static final Pattern INDEXED_PLACEHOLDER_IDENTIFIER = Pattern.compile(
        ".*主题[-_ ]*\\d{3,}内容[-_ ]*\\d{3,}.*"
    );
    private static final Pattern COUNT_LED_CHAPTER = Pattern.compile(
        "^这一(?:阶段|时期)(?:形成|完成|包含)了?\\s*\\d+\\s*项.*"
    );
    private static final List<String> PRODUCTION_CLAIMS = List.of(
        "生产可用", "生产环境稳定", "生产部署", "完成部署", "部署完成", "已经上线", "正式上线", "稳定运行", "完全可靠", "成熟可用"
    );
    private static final List<String> VERIFIED_CLAIMS = List.of(
        "验证通过", "已经验证", "已验证", "确认无误", "测试证明", "自动化验证通过"
    );
    private static final List<String> IMPLEMENTED_CLAIMS = List.of(
        "已经实现", "实现了", "形成实现", "代码实现", "实现所需的代码", "已有代码实现", "完成开发",
        "具备用户", "用户可以", "可直接使用", "新增了登录功能", "搭建了登录流程"
    );
    private static final List<String> GENERIC_FIRST_LAYER = List.of(
        "相关变化", "工程分组", "形成初始结果", "进入当前时间点可确认的新状态", "修改 n 个文件",
        "当前行为得到更新", "项目开始具备这项能力"
    );
    private static final List<String> TITLE_ACTION_MARKERS = List.of(
        "新增", "新建", "建立", "整理", "完善", "更新", "恢复", "移除", "撤销", "重新", "替换", "拆分", "合并",
        "调整", "记录", "保留", "隐藏", "统一", "形成", "推进", "实现", "完成", "创建", "编写", "补充", "保存", "应用", "搭建"
    );
    private static final List<String> TITLE_RESULT_MARKERS = List.of(
        "结果", "版本", "当前", "可以", "可确认", "可核对", "可阅读", "继续", "重新出现", "不再", "保留",
        "恢复", "完成", "形成", "统一", "分别查看", "代码实现", "实现代码", "实现基础", "基础代码", "功能基础",
        "变更记录", "现状记录", "结构更新", "更新了结构", "首次创建", "保存了", "保存相关", "工作交接记录",
        "可供查看", "可供后续查看", "可看", "从无到有", "首次出现", "再次出现", "从项目中移除", "消失", "方案记录", "初始代码",
        "已有实现", "已有内容", "初始记录", "调整记录", "可查看"
    );
    private static final List<String> VAGUE_CHAPTER_LANGUAGE = List.of(
        "围绕项目基础建设推进阶段成果", "相关成果逐步形成并得到完善", "完成相关建设", "持续推进相关工作"
    );

    public ProjectHistoryNarrativeEntailmentValidator() {
        this(new ProjectHistoryClaimEvidenceAttributionService());
    }

    @Autowired
    ProjectHistoryNarrativeEntailmentValidator(ProjectHistoryClaimEvidenceAttributionService attributionService) {
        this.attributionService = attributionService;
    }

    public NarrativeEnvelope envelope(EvidenceProfile profile) {
        EvidenceProfile safe = profile == null ? EvidenceProfile.empty() : profile;
        ProjectHistoryClaimEvidenceAttributionService.Attribution attribution = attributionService.attribute(safe);
        ClaimState state = attribution.state();
        return new NarrativeEnvelope(
            state,
            attribution.subjectKey(),
            text(safe.subjectLabel()).isBlank() ? "项目材料" : text(safe.subjectLabel()),
            attribution.action(),
            attribution.outcome(),
            attribution.directEvidenceRefs(),
            attribution.indirectEvidenceRefs(),
            attribution.sourceAuthorities(),
            attribution.supportClass(),
            attribution.downgradeReason(),
            attribution.directSupportSummary(),
            attribution.indirectContextSummary(),
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
        if (safe.claimState() == ClaimState.CONFLICTED && containsPositiveOutcome(firstLayer)) {
            throw violation(ViolationKind.STATE_UPGRADE, "Conflicted Evidence cannot produce a positive outcome");
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

    /**
     * Provider-neutral first-layer quality boundary. A model title must name an
     * action and object, while the title/summary pair must state a supported
     * result. Callers may retain the already validated deterministic pair when
     * a Provider returns weaker wording.
     */
    public boolean hasActionObjectResult(String title, String summary) {
        String safeTitle = text(title);
        String firstLayer = safeTitle + " " + text(summary);
        boolean action = containsAny(safeTitle, TITLE_ACTION_MARKERS);
        boolean result = containsAny(firstLayer, TITLE_RESULT_MARKERS);
        String object = safeTitle.replaceAll("[，,。.!！？]", "");
        for (String marker : TITLE_ACTION_MARKERS) object = object.replace(marker, "");
        return action && result && object.trim().length() >= 2
            && !containsAny(firstLayer, GENERIC_FIRST_LAYER);
    }

    public void validateChapter(String title, String summary, List<String> primaryStoryWording) {
        String safeTitle = text(title);
        String safeSummary = text(summary);
        List<String> context = values(primaryStoryWording);
        if (safeTitle.isBlank() || safeSummary.isBlank()) {
            throw violation(ViolationKind.CONTRACT, "Chapter wording is incomplete");
        }
        String firstLayer = safeTitle + " " + safeSummary;
        if (containsChapterFirstLayerLeak(firstLayer, context)) {
            throw violation(ViolationKind.FIRST_LAYER_LEAK,
                "Chapter wording exposes an internal subject or path: "
                    + firstLayerLeakKind(firstLayer, context, true));
        }
        if (containsAny(firstLayer, GENERIC_FIRST_LAYER)) {
            throw violation(ViolationKind.INTERNAL_LANGUAGE, "Chapter wording uses a generic internal template");
        }
        if (containsAny(firstLayer, VAGUE_CHAPTER_LANGUAGE)
            || vagueChapter(safeTitle, safeSummary, context)) {
            throw violation(ViolationKind.INTERNAL_LANGUAGE, "Chapter wording does not name a concrete supported outcome");
        }
        if (safeTitle.contains("在这一阶段继续完善") || safeTitle.matches(".*[^，。]{2,}与[^，。]{2,}等?可确认结果.*")
            || safeSummary.contains("主要包括") || COUNT_LED_CHAPTER.matcher(safeSummary).matches()) {
            throw violation(ViolationKind.SUBJECT_CONCATENATION, "Chapter wording is a subject list instead of a phase narrative");
        }
        if (!context.isEmpty() && !sharesConcreteOutcome(firstLayer, context)) {
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
        return containsFirstLayerLeak(value, allowedContext, false);
    }

    private static boolean containsChapterFirstLayerLeak(String value, List<String> allowedContext) {
        return containsFirstLayerLeak(value, allowedContext, true);
    }

    private static boolean containsFirstLayerLeak(String value, List<String> allowedContext, boolean allPublicContext) {
        String safe = text(value);
        if (safe.isBlank()) return false;
        if (safe.contains("…") || safe.contains("...") || RELATIVE_PATH.matcher(safe).find()
            || FILE_NAME.matcher(safe).find() || CAMEL_OR_SNAKE.matcher(safe).find()
            || FIXTURE_IDENTIFIER.matcher(safe).matches()
            || INDEXED_PLACEHOLDER_IDENTIFIER.matcher(safe).matches()) return true;
        // Reason/source context may help the model phrase an eligible reason,
        // but it must never whitelist raw technical tokens in the first layer.
        // Only the normalized public subject can authorize an ASCII token.
        Set<String> allowedAscii = allowedAscii(allowedContext, allPublicContext);
        Matcher enumMatcher = INTERNAL_ENUM.matcher(safe);
        while (enumMatcher.find()) if (!allowedAscii.contains(enumMatcher.group().toLowerCase(Locale.ROOT))) return true;
        Matcher tokenMatcher = ASCII_TOKEN.matcher(safe);
        while (tokenMatcher.find()) {
            String token = tokenMatcher.group().toLowerCase(Locale.ROOT);
            if (!allowedAscii.contains(token)) return true;
        }
        return false;
    }

    private static String firstLayerLeakKind(String value, List<String> allowedContext, boolean allPublicContext) {
        String safe = text(value);
        if (safe.contains("…") || safe.contains("...")) return "TRUNCATION";
        if (RELATIVE_PATH.matcher(safe).find()) return "RELATIVE_PATH";
        if (FILE_NAME.matcher(safe).find()) return "FILE_NAME";
        if (CAMEL_OR_SNAKE.matcher(safe).find()) return "TECHNICAL_IDENTIFIER";
        if (FIXTURE_IDENTIFIER.matcher(safe).matches()) return "FIXTURE_IDENTIFIER";
        if (INDEXED_PLACEHOLDER_IDENTIFIER.matcher(safe).matches()) return "INDEXED_PLACEHOLDER";
        Set<String> allowedAscii = allowedAscii(allowedContext, allPublicContext);
        Matcher enumMatcher = INTERNAL_ENUM.matcher(safe);
        while (enumMatcher.find()) {
            if (!allowedAscii.contains(enumMatcher.group().toLowerCase(Locale.ROOT))) return "INTERNAL_ENUM";
        }
        Matcher tokenMatcher = ASCII_TOKEN.matcher(safe);
        while (tokenMatcher.find()) {
            if (!allowedAscii.contains(tokenMatcher.group().toLowerCase(Locale.ROOT))) return "ASCII_TOKEN";
        }
        return "UNKNOWN";
    }

    private static Set<String> allowedAscii(List<String> allowedContext, boolean allPublicContext) {
        List<String> context = values(allowedContext);
        if (!allPublicContext) return asciiTokens(context.stream().findFirst().orElse(""));
        LinkedHashSet<String> result = new LinkedHashSet<>();
        context.forEach(value -> result.addAll(asciiTokens(value)));
        return Set.copyOf(result);
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

    private static boolean stateUpgrade(String wording, ClaimState state) {
        if (containsAffirmedAny(wording, PRODUCTION_CLAIMS)) return true;
        return switch (state) {
            case PLANNED, DECLARED, CONFIGURED, OBSERVED, UNKNOWN, CONFLICTED -> containsAffirmedAny(wording, IMPLEMENTED_CLAIMS)
                || containsAffirmedAny(wording, VERIFIED_CLAIMS);
            case IMPLEMENTED, REMOVED, RESTORED -> containsAffirmedAny(wording, VERIFIED_CLAIMS);
            case VERIFIED -> false;
        };
    }

    public void validateStateCeiling(ClaimState state, String... wording) {
        String combined = String.join(" ", wording == null ? new String[0] : wording);
        if (stateUpgrade(combined, state == null ? ClaimState.UNKNOWN : state)) {
            throw violation(ViolationKind.STATE_UPGRADE, "Presentation correction is stronger than its Evidence state");
        }
    }

    private static boolean containsPositiveOutcome(String wording) {
        return containsAffirmedAny(wording, IMPLEMENTED_CLAIMS)
            || containsAffirmedAny(wording, VERIFIED_CLAIMS)
            || containsAffirmedAny(wording, List.of("已经完成", "形成成果", "可以使用", "当前可用"));
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
        profile.atoms().stream().map(EvidenceAtom::sourceLabel).map(ProjectHistoryNarrativeEntailmentValidator::text)
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
            case CONFLICTED -> List.of("只能表达来源存在冲突", "不能形成正向完成结论");
        };
    }

    private static List<String> forbiddenClaims(ClaimState state) {
        List<String> result = new ArrayList<>(PRODUCTION_CLAIMS);
        if (state != ClaimState.VERIFIED) result.addAll(VERIFIED_CLAIMS);
        if (state == ClaimState.PLANNED || state == ClaimState.DECLARED || state == ClaimState.CONFIGURED
            || state == ClaimState.OBSERVED || state == ClaimState.UNKNOWN || state == ClaimState.CONFLICTED) {
            result.addAll(IMPLEMENTED_CLAIMS);
        }
        return List.copyOf(new LinkedHashSet<>(result));
    }

    private static boolean sharesHanBigram(String left, String right) {
        Set<String> leftValues = hanBigrams(left);
        leftValues.retainAll(hanBigrams(right));
        return !leftValues.isEmpty();
    }

    private static boolean sharesConcreteOutcome(String chapterWording, List<String> primaryWording) {
        Set<String> chapter = meaningfulHanBigrams(chapterWording);
        if (chapter.isEmpty()) return false;
        for (String wording : primaryWording) {
            Set<String> primary = meaningfulHanBigrams(wording);
            primary.retainAll(chapter);
            if (primary.size() >= 2) return true;
        }
        return false;
    }

    private static boolean vagueChapter(String title, String summary, List<String> primaryWording) {
        String combined = title + " " + summary;
        boolean vagueVerb = containsAny(combined, "围绕", "推进", "逐步形成", "得到完善", "相关建设");
        return vagueVerb && !sharesConcreteOutcome(combined, primaryWording);
    }

    private static Set<String> meaningfulHanBigrams(String value) {
        Set<String> result = hanBigrams(value);
        result.removeAll(Set.of(
            "这一", "阶段", "时期", "围绕", "推进", "相关", "成果", "形成", "逐步", "得到", "完善", "项目", "主要"
        ));
        return result;
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
        UNKNOWN,
        CONFLICTED
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

    public record EvidenceAtom(
        String atomRef,
        List<String> subjectKeys,
        Category category,
        Transition transition,
        Authority authority,
        ProjectFactEpistemicStatus epistemicStatus,
        List<String> paths,
        String sourceLabel,
        List<String> evidenceRefs
    ) {
        public EvidenceAtom {
            atomRef = text(atomRef);
            subjectKeys = values(subjectKeys);
            category = category == null ? Category.EXTERNAL : category;
            transition = transition == null ? Transition.UNKNOWN_TRANSITION : transition;
            authority = authority == null ? Authority.UNKNOWN : authority;
            epistemicStatus = epistemicStatus == null ? ProjectFactEpistemicStatus.UNKNOWN : epistemicStatus;
            paths = values(paths);
            sourceLabel = text(sourceLabel);
            evidenceRefs = values(evidenceRefs);
        }
    }

    public record EvidenceProfile(
        String subjectKey,
        String subjectLabel,
        Transition transition,
        List<EvidenceAtom> atoms,
        boolean reasonEligible
    ) {
        public EvidenceProfile {
            subjectKey = text(subjectKey);
            subjectLabel = text(subjectLabel);
            transition = transition == null ? Transition.UNKNOWN_TRANSITION : transition;
            atoms = values(atoms);
        }

        public EvidenceProfile(
            String subjectLabel,
            Transition transition,
            List<Category> categories,
            List<Authority> authorities,
            List<ProjectFactEpistemicStatus> epistemicStatuses,
            List<String> paths,
            List<String> sourceLabels,
            boolean reasonEligible
        ) {
            this(legacySubjectKey(paths, subjectLabel), subjectLabel, transition,
                legacyAtoms(transition, categories, authorities, epistemicStatuses, paths, sourceLabels), reasonEligible);
        }

        public static EvidenceProfile empty() {
            return new EvidenceProfile("project-material", "项目材料", Transition.UNKNOWN_TRANSITION, List.of(), false);
        }

        private static String legacySubjectKey(List<String> paths, String subjectLabel) {
            List<String> safePaths = values(paths);
            return safePaths.isEmpty()
                ? text(subjectLabel).toLowerCase(Locale.ROOT).replaceAll("\\s+", "-")
                : ProjectHistorySourceCollector.historySubjectKey(safePaths.get(0));
        }

        private static List<EvidenceAtom> legacyAtoms(
            Transition transition,
            List<Category> categories,
            List<Authority> authorities,
            List<ProjectFactEpistemicStatus> statuses,
            List<String> paths,
            List<String> sourceLabels
        ) {
            List<String> safePaths = values(paths);
            Category category = first(categories, Category.EXTERNAL);
            Authority authority = first(authorities, Authority.UNKNOWN);
            ProjectFactEpistemicStatus status = first(statuses, ProjectFactEpistemicStatus.UNKNOWN);
            String label = values(sourceLabels).stream().findFirst().orElse("");
            if (safePaths.isEmpty()) {
                return List.of(new EvidenceAtom("legacy", List.of(), category, transition, authority, status,
                    List.of(), label, List.of()));
            }
            return safePaths.stream().map(path -> {
                String key = ProjectHistorySourceCollector.historySubjectKey(path);
                return new EvidenceAtom("legacy:" + key + ":" + path, List.of(key), category, transition,
                    authority, status, List.of(path), label, List.of());
            }).toList();
        }

        private static <T> T first(List<T> values, T fallback) {
            return values == null ? fallback : values.stream().filter(java.util.Objects::nonNull).findFirst().orElse(fallback);
        }
    }

    public record NarrativeEnvelope(
        ClaimState claimState,
        String subjectKey,
        String subjectLabel,
        String claimAction,
        String supportedOutcome,
        List<String> directEvidenceRefs,
        List<String> indirectEvidenceRefs,
        List<String> sourceAuthorities,
        String supportClass,
        String downgradeReason,
        List<String> directSupportSummary,
        List<String> indirectContextSummary,
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
