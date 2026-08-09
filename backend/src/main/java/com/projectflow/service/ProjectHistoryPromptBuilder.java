package com.projectflow.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Shared production/evaluation prompt builder for bounded project-history wording. */
@Component
public final class ProjectHistoryPromptBuilder {
    public static final String PROMPT_VERSION = "project-history-synthesis-v7";
    public static final String CHAPTER_PROMPT_VERSION = "project-history-chapter-synthesis-v2";
    static final int MAX_PROMPT_CHARS = 60_000;
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
    private static final String UNKNOWN_REASON = "原因未知：输入未提供可核验的原因 Evidence。";
    private static final String INSTRUCTIONS = """
        任务：把工程层已经组织好的项目历程改写成普通用户能看懂的中文。只改文字，不改事实或结构。
        只返回输入中的 storyId 和 chapterId，每个 ID 必须且只能返回一次。
        可改字段只有 Story 的 humanTitle、oneSentenceSummary、reason、reasonEvidenceRefs、unknowns，以及 Chapter 的 title、summary。
        role、primaryStoryId、supportingChangeRefs、storyRefs、时间、Before / Change / After、laterOutcome、成员和 Evidence 都由工程层固定，禁止返回或改写。
        humanTitle 要表达“做了什么 + 对象 + 形成的结果”。正例：补充项目使用说明，让读者知道如何开始。反例：整理 readme、优化系统、修改相关文件。
        Commit message 只是线索。reason 仅在 reasonEvidenceRefs 非空且全部来自该 Story 的 reasonEligibleEvidenceRefs 时填写；否则 reason 留空，并在 unknowns 说明原因未知。
        不得创造 ID、Evidence、文件、数字、原因或项目状态；不得写重要性、成熟度、里程碑、成功判断、下一步、计划或建议。非软件项目不要使用 Controller、Service、Capability 等软件术语。
        只返回严格 JSON，不得增加字段：
        {"stories":[{"storyId":"","humanTitle":"","oneSentenceSummary":"","reason":"","reasonEvidenceRefs":[],"unknowns":[]}],
         "chapters":[{"chapterId":"","title":"","summary":""}]}
        """;
    private static final String CHAPTER_SYNTHESIS_INSTRUCTIONS = """
        你正在对一个成员关系已经由工程层固定的项目历程篇章做第二阶段归纳。输入只包含已经校验的 Story 展示摘要，不包含 Raw Event、Evidence、文件路径或提交原文。
        只能改进篇章的中文标题和摘要。chapterId 必须原样返回且只能返回一次；不得返回或修改 Story 成员、时间、边界、权威或任何其他字段。
        标题必须让普通用户看懂这一阶段形成的主要结果；摘要必须区分 Primary 主要结果和 Supporting 支撑工作，不得把测试、配置或验证数量描述为用户成果。
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
            selected.size(), Math.max(0, input.storyCount() - selected.size()), selected
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
        while (prompt.length() > MAX_PROMPT_CHARS && !selectedChapters.isEmpty()) {
            selectedChapters.remove(selectedChapters.size() - 1);
            prompt = render(selectedStories, selectedChapters);
        }
        while (prompt.length() > MAX_PROMPT_CHARS && !selectedStories.isEmpty()) {
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
            if (next > MAX_STORY_SECTION_CHARS || baseChars + next > MAX_PROMPT_CHARS) continue;
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
            if (next > MAX_CHAPTER_SECTION_CHARS || baseChars + next > MAX_PROMPT_CHARS) continue;
            selected.add(chapter);
            chapterChars = next;
        }
        return selected;
    }

    private String render(List<StoryPromptInput> stories, List<ChapterPromptInput> chapters) {
        List<String> storyIds = stories.stream().map(StoryPromptInput::storyId).toList();
        List<String> chapterIds = chapters.stream().map(ChapterPromptInput::chapterId).toList();
        List<StoryOutputTemplate> storyTemplates = stories.stream().map(story -> new StoryOutputTemplate(
            story.storyId(), "", "", "", List.of(),
            story.reasonEligibleEvidenceRefs().isEmpty()
                ? List.of(UNKNOWN_REASON) : List.of()
        )).toList();
        List<ChapterOutputTemplate> chapterTemplates = chapters.stream()
            .map(chapter -> new ChapterOutputTemplate(chapter.chapterId(), "", ""))
            .toList();
        String outputCheck = """

            输出前做机械核对：本次必须返回 Story %d 个、Chapter %d 个；requiredStoryIds=%s；requiredChapterIds=%s。
            每个 required ID 恰好一次，且对象只能包含上面列出的可改字段；数量、ID 或字段不一致时先修正再输出。
            必须复制 OUTPUT_TEMPLATE_JSON 的对象、ID、字段和数组类型，只填写允许的文字；不得删除、增加或重排对象。
            reasonEvidenceRefs 只能从对应 Story 的 reasonEligibleEvidenceRefs 中选择；没有可核验原因时保留模板中的 UNKNOWN。
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
        String deterministicAfter,
        String role,
        String primaryStoryId,
        List<String> supportingChangeRefs,
        List<String> commitSummaries,
        List<String> technicalDetails
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
                reasonEligibleEvidenceRefs, deterministicBefore, deterministicChange, deterministicAfter, "PRIMARY",
                "", List.of(), List.of(), List.of());
        }

        public StoryPromptInput {
            transitions = immutable(transitions);
            sourceLabels = immutable(sourceLabels);
            affectedAreas = immutable(affectedAreas);
            evidenceRefs = immutable(evidenceRefs);
            reasonEligibleEvidenceRefs = immutable(reasonEligibleEvidenceRefs);
            supportingChangeRefs = immutable(supportingChangeRefs);
            commitSummaries = immutable(commitSummaries);
            technicalDetails = immutable(technicalDetails);
            role = role == null || role.isBlank() ? "PRIMARY" : role.trim();
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
        String reason,
        List<String> reasonEvidenceRefs,
        List<String> unknowns
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
