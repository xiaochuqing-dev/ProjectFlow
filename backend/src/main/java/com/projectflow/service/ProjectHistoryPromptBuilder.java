package com.projectflow.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Shared production/evaluation prompt builder for bounded project-history wording. */
@Component
public final class ProjectHistoryPromptBuilder {
    public static final String PROMPT_VERSION = "project-history-synthesis-v11";
    public static final String CHAPTER_PROMPT_VERSION = "project-history-chapter-synthesis-v6";
    static final int MAX_PROMPT_CHARS = 60_000;
    public static final String VALIDATION_REPAIR_MARKER = "\nHISTORY_VALIDATION_REPAIR=";
    private static final String VALIDATION_REPAIR_INSTRUCTIONS = """
        上一次输出未通过 ProjectFlow 的统一语义安全校验。请从原输入重新生成一次完整 JSON，不要复述或修补上一次输出。
        只能使用 OUTPUT_TEMPLATE_JSON 中的对象、ID 和字段；每个 required ID 必须且只能返回一次。
        reasonEvidenceRefs 只能逐项复制对应 Story 的 reasonEligibleEvidenceRefs；没有采用合格 Evidence 时 reason 留空，并保留模板中的“原因未知”。只有 reason 非空且 reasonEvidenceRefs 非空时才可清空 unknownWording。
        Before / Change / After 只能改写 wording，不得改变 verified semantic、claimState 或 allowed claims。
        不得返回工程层字段，不得创造 ID、Evidence、实体、动作、原因、冲突、数字或项目状态。只返回严格 JSON。
        不得使用“相关变化”“工程分组”“形成初始结果”“进入当前时间点可确认的新状态”等空泛或内部模板表达。
        """;
    private static final String CHAPTER_VALIDATION_REPAIR_INSTRUCTIONS = """
        上一次篇章输出未通过 ProjectFlow 的统一语义安全校验。请从原始 CHAPTER_SYNTHESIS_JSON 重新生成一次完整 JSON，不要复述或修补上一次输出。
        只能返回一个篇章对象，且只能包含 chapterId、title、summary；chapterId 必须与原输入完全一致。不得返回或修改 Story 成员、时间、边界或其他字段。
        title 必须直接写出至少一个 Primary Story 已支持的具体动作、对象和结果；summary 必须说明这一时期实际完成了什么，并把 Supporting 信息保持为次要说明。
        不得使用“围绕”“推进”“完善”“建设”等空泛词替代具体结果，不得返回列表、路径、成熟度、原因、计划或项目状态。
        只返回严格 JSON，不得增加字段：
        {"chapters":[{"chapterId":"","title":"","summary":""}]}
        """;
    private static final int MAX_INITIAL_PROMPT_CHARS = MAX_PROMPT_CHARS
        - VALIDATION_REPAIR_MARKER.length() - VALIDATION_REPAIR_INSTRUCTIONS.length() - 40;
    static final int MAX_CHAPTER_SYNTHESIS_PROMPT_CHARS = 48_000;
    static final int MAX_CHAPTER_STORY_SUMMARIES = 80;
    static final int MAX_STORY_SECTION_CHARS = 45_000;
    static final int MAX_CHAPTER_SECTION_CHARS = 12_000;
    static final int MAX_STORY_RECORD_CHARS = 9_000;
    static final int MAX_CHAPTER_RECORD_CHARS = 4_500;

    private static final String STORIES_MARKER = "\nSTORIES_JSON=";
    private static final String CHAPTERS_MARKER = "\nCHAPTERS_JSON=";
    private static final String OUTPUT_TEMPLATE_MARKER = "\nOUTPUT_TEMPLATE_JSON=";
    private static final String CHAPTER_SYNTHESIS_MARKER = "\nCHAPTER_SYNTHESIS_JSON=";
    private static final String UNKNOWN_REASON = "目前没有足够信息确认为什么做这次调整。";
    private static final String INSTRUCTIONS = """
        任务：把工程层已经组织好的项目历程改写成普通用户能看懂的中文。只改文字，不改事实或结构。
        只返回输入中的 storyId 和 chapterId，每个 ID 必须且只能返回一次。
        可改字段只有 Story 的 humanTitle、oneSentenceSummary、beforeWording、changeWording、afterWording、reason、reasonEvidenceRefs、unknownWording，以及 Chapter 的 title、summary。
        role、primaryStoryId、supportingChangeRefs、storyRefs、时间、verified semantic、claimState、laterOutcome、成员和 Evidence 都由工程层固定，禁止返回或改写。
        humanTitle 只用一句话表达“做了什么 + 对象 + 形成的结果”；oneSentenceSummary 补充范围或影响；Before 只讲此前状态；Change 只讲本阶段动作；After 只讲最终状态。五段不得复读同一句话。
        subjectDisplayConcept 是第一层唯一允许的主要对象；不得输出 raw subject、路径、文件名、class、internal slug、截断 token 或输入外的新实体。
        claimState、claimAction、supportedOutcome、supportClass、allowedClaims 与 forbiddenClaims 是硬边界。PLANNED 不得写成 IMPLEMENTED，DECLARED 不得写成 VERIFIED，CONFIGURED 不得写成已部署，未给直接验证 Evidence 不得写稳定或生产可用。
        directSupportSummary 是与当前 subject/action 直接匹配的有界支持；indirectContextSummary 只解释上下文，明确不能提升 Claim。不得因为同 Commit、相邻时间、相同区域或 Supporting Story 把间接上下文借给当前 Claim。
        downgradeReason 必须被遵守：只能在工程层给出的 supportedOutcome 内改写，不得自行提高状态。
        Commit message 只是线索。reason 仅在 reasonEvidenceRefs 非空且全部来自该 Story 的 reasonEligibleEvidenceRefs 时填写；否则 reason 留空，并保留模板中的自然 unknownWording，说明原因暂时无法确认。即使存在可选 Evidence，只要本次没有实际采用，也不得清空 unknownWording。
        不得创造 ID、Evidence、文件、数字、原因或项目状态；不得写重要性、成熟度、里程碑、成功判断、下一步或建议。非软件项目不要使用 Controller、Service、Capability 等软件术语。
        只返回严格 JSON，不得增加字段：
        {"stories":[{"storyId":"","humanTitle":"","oneSentenceSummary":"","beforeWording":"","changeWording":"","afterWording":"","reason":"","reasonEvidenceRefs":[],"unknownWording":""}],
         "chapters":[{"chapterId":"","title":"","summary":""}]}
        """;
    private static final String CHAPTER_SYNTHESIS_INSTRUCTIONS = """
        你正在对一个成员关系已经由工程层固定的项目历程篇章做第二阶段归纳。输入只包含已经校验的 Story 展示摘要，不包含 Raw Event、Evidence、文件路径或提交原文。
        只能改进篇章的中文标题和摘要。chapterId 必须原样返回且只能返回一次；不得返回或修改 Story 成员、时间、边界、权威或任何其他字段。
        标题必须直接写出至少一个 Primary Story 已支持的具体动作、对象和结果；摘要必须让普通用户能够复述这一时期实际完成了什么，再把 Supporting 保留为次要工程信息。不得以数量开头，不得把 Story subject 拼接成标题，不得把文件、测试、配置或验证数量描述为用户成果。
        不得仅用“围绕某主题推进”“相关成果逐步形成并得到完善”“完成相关建设”等空泛句式。不得使用“相关变化”“工程分组”“形成初始结果”“进入当前时间点可确认的新状态”等内部模板表达。
        如果部分 Story 摘要因边界被省略，只能根据输入中的数量和代表摘要保守归纳，不得补造遗漏内容。
        禁止重要性、成熟度、里程碑、成功判断、下一步、计划或建议。禁止创造 ID、Evidence、文件、数字、原因或项目状态。
        只返回严格 JSON，不得增加字段：
        {"chapters":[{"chapterId":"","title":"","summary":""}]}
        """;

    private final ObjectMapper objectMapper;

    public ProjectHistoryPromptBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public PromptBuildResult buildProduction(PromptInput input) {
        return build(input);
    }

    public PromptBuildResult buildEvaluation(PromptInput input) {
        return build(input);
    }

    public ChapterSynthesisBuildResult buildChapterProduction(ChapterSynthesisPromptInput input) {
        return buildChapter(input);
    }

    public ChapterSynthesisBuildResult buildChapterEvaluation(ChapterSynthesisPromptInput input) {
        return buildChapter(input);
    }

    /** One bounded Provider-neutral repair instruction; no prior raw output is repeated. */
    public String validationRepair(String originalPrompt, String validationKind) {
        String safePrompt = originalPrompt == null ? "" : originalPrompt;
        String safeKind = switch (validationKind == null ? "" : validationKind) {
            case "INVALID_EVIDENCE", "CROSS_PROJECT_REFERENCE", "UNSUPPORTED_CLAIM", "CONTRACT" -> validationKind;
            default -> "CONTRACT";
        };
        String repairInstructions = safePrompt.contains(CHAPTER_SYNTHESIS_MARKER)
            ? CHAPTER_VALIDATION_REPAIR_INSTRUCTIONS
            : VALIDATION_REPAIR_INSTRUCTIONS;
        String repaired = safePrompt + VALIDATION_REPAIR_MARKER + safeKind + "\n" + repairInstructions;
        if (repaired.length() > MAX_PROMPT_CHARS) {
            throw new IllegalStateException("项目历程安全修复 Prompt 超过有界上限");
        }
        return repaired;
    }

    private ChapterSynthesisBuildResult buildChapter(ChapterSynthesisPromptInput input) {
        ChapterSynthesisPromptInput safe = input == null
            ? new ChapterSynthesisPromptInput("", "", "", 0, 0, 0, List.of(), "", List.of())
            : input;
        List<ChapterStorySummaryInput> candidates = representativeStorySummaries(safe.storySummaries());
        List<ChapterStorySummaryInput> selected = new ArrayList<>();
        for (ChapterStorySummaryInput story : candidates) {
            if (story == null || text(story.storyId()).isBlank()) continue;
            List<ChapterStorySummaryInput> next = new ArrayList<>(selected);
            next.add(story);
            String rendered = renderChapter(safe, next);
            if (rendered.length() > MAX_CHAPTER_SYNTHESIS_PROMPT_CHARS) continue;
            selected = next;
        }
        String prompt = renderChapter(safe, selected);
        Set<String> included = selected.stream().map(ChapterStorySummaryInput::storyId)
            .collect(LinkedHashSet::new, Set::add, Set::addAll);
        return new ChapterSynthesisBuildResult(
            prompt, ordered(included), prompt.length(), Math.max(0, values(safe.storySummaries()).size() - selected.size())
        );
    }

    private List<ChapterStorySummaryInput> representativeStorySummaries(List<ChapterStorySummaryInput> input) {
        List<ChapterStorySummaryInput> values = values(input);
        if (values.size() <= MAX_CHAPTER_STORY_SUMMARIES) return new ArrayList<>(values);
        LinkedHashSet<Integer> indices = new LinkedHashSet<>();
        for (int index = 0; index < MAX_CHAPTER_STORY_SUMMARIES; index++) {
            indices.add((int) Math.round((double) index * (values.size() - 1) / (MAX_CHAPTER_STORY_SUMMARIES - 1)));
        }
        return indices.stream().sorted().map(values::get).toList();
    }

    private String renderChapter(
        ChapterSynthesisPromptInput input,
        List<ChapterStorySummaryInput> selected
    ) {
        PackedChapterSynthesisInput packed = new PackedChapterSynthesisInput(
            input.chapterId(), input.from(), input.to(), input.storyCount(), input.primaryStoryCount(),
            input.supportingStoryCount(), input.boundarySignals(), input.membershipFingerprint(),
            selected.size(), Math.max(0, input.primaryStoryCount() - selected.size()), selected
        );
        return CHAPTER_SYNTHESIS_INSTRUCTIONS + CHAPTER_SYNTHESIS_MARKER + json(packed);
    }

    private PromptBuildResult build(PromptInput input) {
        PromptInput safeInput = input == null ? new PromptInput(List.of(), List.of()) : input;
        List<StoryPromptInput> selectedStories = packStories(safeInput.stories());
        Set<String> storyIds = selectedStories.stream().map(StoryPromptInput::storyId)
            .collect(LinkedHashSet::new, Set::add, Set::addAll);
        List<ChapterPromptInput> selectedChapters = packChapters(safeInput.chapters(), storyIds, selectedStories);

        String prompt = render(selectedStories, selectedChapters);
        while (prompt.length() > MAX_INITIAL_PROMPT_CHARS && !selectedChapters.isEmpty()) {
            selectedChapters.remove(selectedChapters.size() - 1);
            prompt = render(selectedStories, selectedChapters);
        }
        while (prompt.length() > MAX_INITIAL_PROMPT_CHARS && !selectedStories.isEmpty()) {
            selectedStories.remove(selectedStories.size() - 1);
            storyIds = selectedStories.stream().map(StoryPromptInput::storyId)
                .collect(LinkedHashSet::new, Set::add, Set::addAll);
            Set<String> retained = storyIds;
            selectedChapters.removeIf(chapter -> !retained.containsAll(chapter.storyRefs()));
            prompt = render(selectedStories, selectedChapters);
        }

        Set<String> chapterIds = selectedChapters.stream().map(ChapterPromptInput::chapterId)
            .collect(LinkedHashSet::new, Set::add, Set::addAll);
        return new PromptBuildResult(
            prompt, ordered(storyIds), ordered(chapterIds), prompt.length(),
            Math.max(0, safeInput.stories().size() - selectedStories.size()),
            Math.max(0, safeInput.chapters().size() - selectedChapters.size())
        );
    }

    private List<StoryPromptInput> packStories(List<StoryPromptInput> input) {
        List<StoryPromptInput> selected = new ArrayList<>();
        int sectionChars = 2;
        int baseChars = INSTRUCTIONS.length() + STORIES_MARKER.length() + CHAPTERS_MARKER.length() + 4;
        for (StoryPromptInput story : values(input)) {
            if (story == null || text(story.storyId()).isBlank()) continue;
            String serialized = json(story);
            if (serialized.length() > MAX_STORY_RECORD_CHARS) continue;
            int next = sectionChars + serialized.length() + (selected.isEmpty() ? 0 : 1);
            if (next > MAX_STORY_SECTION_CHARS || baseChars + next > MAX_INITIAL_PROMPT_CHARS) continue;
            selected.add(story);
            sectionChars = next;
        }
        return selected;
    }

    private List<ChapterPromptInput> packChapters(
        List<ChapterPromptInput> input,
        Set<String> selectedStoryIds,
        List<StoryPromptInput> selectedStories
    ) {
        List<ChapterPromptInput> selected = new ArrayList<>();
        int storyChars = json(selectedStories).length();
        int chapterChars = 2;
        int baseChars = INSTRUCTIONS.length() + STORIES_MARKER.length() + CHAPTERS_MARKER.length() + storyChars;
        for (ChapterPromptInput chapter : values(input)) {
            if (chapter == null || text(chapter.chapterId()).isBlank() || chapter.storyRefs() == null
                || chapter.storyRefs().isEmpty() || !selectedStoryIds.containsAll(chapter.storyRefs())) continue;
            String serialized = json(chapter);
            if (serialized.length() > MAX_CHAPTER_RECORD_CHARS) continue;
            int next = chapterChars + serialized.length() + (selected.isEmpty() ? 0 : 1);
            if (next > MAX_CHAPTER_SECTION_CHARS || baseChars + next > MAX_INITIAL_PROMPT_CHARS) continue;
            selected.add(chapter);
            chapterChars = next;
        }
        return selected;
    }

    private String render(List<StoryPromptInput> stories, List<ChapterPromptInput> chapters) {
        List<String> storyIds = stories.stream().map(StoryPromptInput::storyId).toList();
        List<String> chapterIds = chapters.stream().map(ChapterPromptInput::chapterId).toList();
        List<StoryOutputTemplate> storyTemplates = stories.stream().map(story -> new StoryOutputTemplate(
            story.storyId(), "", "", "", "", "", "", List.of(), UNKNOWN_REASON
        )).toList();
        List<ChapterOutputTemplate> chapterTemplates = chapters.stream()
            .map(chapter -> new ChapterOutputTemplate(chapter.chapterId(), "", ""))
            .toList();
        String outputCheck = """

            输出前做机械核对：本次必须返回 Story %d 个、Chapter %d 个；requiredStoryIds=%s；requiredChapterIds=%s。
            每个 required ID 恰好一次，且对象只能包含上面列出的可改字段；数量、ID 或字段不一致时先修正再输出。
            必须复制 OUTPUT_TEMPLATE_JSON 的对象、ID、字段和数组类型，只填写允许的文字；不得删除、增加或重排对象。
            reasonEvidenceRefs 只能从对应 Story 的 reasonEligibleEvidenceRefs 中选择；没有实际采用可核验原因时保留模板中的自然 unknownWording。只有 reason 与 reasonEvidenceRefs 同时非空时才可清空 unknownWording。
            """.formatted(storyIds.size(), chapterIds.size(), json(storyIds), json(chapterIds));
        return INSTRUCTIONS + outputCheck
            + OUTPUT_TEMPLATE_MARKER + json(new OutputTemplate(storyTemplates, chapterTemplates))
            + STORIES_MARKER + json(stories) + CHAPTERS_MARKER + json(chapters);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("项目历程 Prompt JSON 无法序列化", exception);
        }
    }

    private static <T> List<T> values(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private static <T> Set<T> ordered(Set<T> values) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(values == null ? Set.of() : values));
    }

    public record PromptInput(List<StoryPromptInput> stories, List<ChapterPromptInput> chapters) {
    }

    public record StoryPromptInput(
        String storyId,
        String subjectDisplayConcept,
        String occurredFrom,
        String occurredTo,
        List<String> transitions,
        @JsonIgnore List<String> sourceLabels,
        @JsonIgnore List<String> affectedAreas,
        @JsonIgnore List<String> evidenceRefs,
        List<String> reasonEligibleEvidenceRefs,
        String deterministicBefore,
        String deterministicChange,
        String deterministicAfter,
        String claimState,
        String claimAction,
        String supportedOutcome,
        List<String> directSupportSummary,
        List<String> indirectContextSummary,
        String supportClass,
        String downgradeReason,
        List<String> allowedClaims,
        List<String> forbiddenClaims,
        List<String> humanSafeSourceContext,
        @JsonIgnore String role,
        @JsonIgnore String primaryStoryId,
        @JsonIgnore List<String> supportingChangeRefs
    ) {
        public StoryPromptInput(
            String storyId,
            String subject,
            String occurredFrom,
            String occurredTo,
            List<String> transitions,
            List<String> sourceLabels,
            List<String> affectedAreas,
            List<String> evidenceRefs,
            List<String> reasonEligibleEvidenceRefs,
            String deterministicBefore,
            String deterministicChange,
            String deterministicAfter
        ) {
            this(storyId, subject, occurredFrom, occurredTo, transitions, sourceLabels, affectedAreas, evidenceRefs,
                reasonEligibleEvidenceRefs, deterministicBefore, deterministicChange, deterministicAfter, "OBSERVED",
                "OBSERVE", "只能描述直接观察到的变化", List.of(), List.of(), "DIRECT", "",
                List.of(), List.of(), List.of(), "PRIMARY", "", List.of());
        }

        public StoryPromptInput {
            transitions = immutable(transitions);
            sourceLabels = immutable(sourceLabels);
            affectedAreas = immutable(affectedAreas);
            evidenceRefs = immutable(evidenceRefs);
            reasonEligibleEvidenceRefs = immutable(reasonEligibleEvidenceRefs);
            directSupportSummary = immutable(directSupportSummary);
            indirectContextSummary = immutable(indirectContextSummary);
            allowedClaims = immutable(allowedClaims);
            forbiddenClaims = immutable(forbiddenClaims);
            humanSafeSourceContext = immutable(humanSafeSourceContext);
            supportingChangeRefs = immutable(supportingChangeRefs);
            role = role == null || role.isBlank() ? "PRIMARY" : role.trim();
            claimState = claimState == null || claimState.isBlank() ? "UNKNOWN" : claimState.trim();
            claimAction = claimAction == null || claimAction.isBlank() ? "UNKNOWN" : claimAction.trim();
            supportedOutcome = supportedOutcome == null ? "" : supportedOutcome.trim();
            supportClass = supportClass == null || supportClass.isBlank() ? "INSUFFICIENT" : supportClass.trim();
            downgradeReason = downgradeReason == null ? "" : downgradeReason.trim();
            primaryStoryId = primaryStoryId == null ? "" : primaryStoryId.trim();
        }

        private static List<String> immutable(List<String> values) {
            return values == null ? List.of() : List.copyOf(values);
        }
    }

    public record ChapterPromptInput(
        String chapterId,
        String from,
        String to,
        List<String> storyRefs,
        List<String> boundarySignals
    ) {
    }

    private record OutputTemplate(
        List<StoryOutputTemplate> stories,
        List<ChapterOutputTemplate> chapters
    ) {
    }

    private record StoryOutputTemplate(
        String storyId,
        String humanTitle,
        String oneSentenceSummary,
        String beforeWording,
        String changeWording,
        String afterWording,
        String reason,
        List<String> reasonEvidenceRefs,
        String unknownWording
    ) {
    }

    private record ChapterOutputTemplate(String chapterId, String title, String summary) {
    }

    public record PromptBuildResult(
        String prompt,
        Set<String> includedStoryIds,
        Set<String> includedChapterIds,
        int promptCharacterCount,
        int omittedStoryCount,
        int omittedChapterCount
    ) {
    }

    public record ChapterSynthesisPromptInput(
        String chapterId,
        String from,
        String to,
        int storyCount,
        int primaryStoryCount,
        int supportingStoryCount,
        List<String> boundarySignals,
        String membershipFingerprint,
        List<ChapterStorySummaryInput> storySummaries
    ) {
        public ChapterSynthesisPromptInput {
            boundarySignals = boundarySignals == null ? List.of() : List.copyOf(boundarySignals);
            storySummaries = storySummaries == null ? List.of() : List.copyOf(storySummaries);
        }
    }

    public record ChapterStorySummaryInput(
        String storyId,
        String humanTitle,
        String oneSentenceSummary,
        String role,
        String occurredFrom,
        String occurredTo
    ) {
    }

    private record PackedChapterSynthesisInput(
        String chapterId,
        String from,
        String to,
        int storyCount,
        int primaryStoryCount,
        int supportingStoryCount,
        List<String> boundarySignals,
        String membershipFingerprint,
        int includedStorySummaryCount,
        int omittedStorySummaryCount,
        List<ChapterStorySummaryInput> storySummaries
    ) {
    }

    public record ChapterSynthesisBuildResult(
        String prompt,
        Set<String> includedStoryIds,
        int promptCharacterCount,
        int omittedStoryCount
    ) {
    }
}
