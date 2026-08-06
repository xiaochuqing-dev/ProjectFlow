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
    public static final String PROMPT_VERSION = "project-history-synthesis-v4";
    public static final String CHAPTER_PROMPT_VERSION = "project-history-chapter-synthesis-v1";
    static final int MAX_PROMPT_CHARS = 60_000;
    static final int MAX_CHAPTER_SYNTHESIS_PROMPT_CHARS = 48_000;
    static final int MAX_CHAPTER_STORY_SUMMARIES = 80;
    static final int MAX_STORY_SECTION_CHARS = 45_000;
    static final int MAX_CHAPTER_SECTION_CHARS = 12_000;
    static final int MAX_STORY_RECORD_CHARS = 9_000;
    static final int MAX_CHAPTER_RECORD_CHARS = 4_500;

    private static final String STORIES_MARKER = "\nSTORIES_JSON=";
    private static final String CHAPTERS_MARKER = "\nCHAPTERS_JSON=";
    private static final String CHAPTER_SYNTHESIS_MARKER = "\nCHAPTER_SYNTHESIS_JSON=";
    private static final String INSTRUCTIONS = """
        你正在改写一个项目已经由工程层确定成员关系的项目历程。只能改进中文表达和展示角色，不能改变故事、篇章、时间、成员或 Evidence。
        只返回输入中列出的 storyId 和 chapterId；每个 ID 必须且只能返回一次，storyRefs 必须原样覆盖其允许集合。
        humanTitle 必须是“动作 + 对象 + 结果”，禁止只写“优化系统、改进功能、进行了重构、提升体验、修改相关文件”。
        role、primaryStoryId 和 supportingChangeRefs 是候选展示关系，必须构成完整双向图。PRIMARY 的 primaryStoryId 必须为空；SUPPORTING 必须指向本输入中的一个 PRIMARY，且该 PRIMARY 的 supportingChangeRefs 必须反向包含它。
        不得形成循环、孤立 Supporting、Supporting 指向 Supporting 或未知 ID。如果不确定是否需要改变角色，原样保留输入的三个关系字段。Supporting 只表示对主成果的支撑。
        工程层已经固定 Before / Change / After 与 laterOutcome；不得返回或改写这些字段。Commit message 只是线索，不是无需验证的事实。
        reason 只有在 reasonEvidenceRefs 非空且全部来自 reasonEligibleEvidenceRefs 时才可填写；否则 reason 必须为空并在 unknowns 写明原因未知。
        禁止重要性、成熟度、里程碑、成功判断、下一步、计划或建议。禁止创造 ID、Evidence、文件、数字、原因或项目状态。
        返回严格 JSON，不得增加字段：
        {"stories":[{"storyId":"","humanTitle":"","oneSentenceSummary":"","role":"PRIMARY","primaryStoryId":"","supportingChangeRefs":[],"reason":"","reasonEvidenceRefs":[],"conflicts":[],"unknowns":[]}],
         "chapters":[{"chapterId":"","title":"","summary":"","storyRefs":[]}]}
        """;
    private static final String CHAPTER_SYNTHESIS_INSTRUCTIONS = """
        你正在对一个成员关系已经由工程层固定的项目历程篇章做第二阶段归纳。输入只包含已经校验的 Story 展示摘要，不包含 Raw Event、Evidence、文件路径或提交原文。
        只能改进篇章的中文标题和摘要。chapterId 必须原样返回且只能返回一次；不得返回或修改 Story 成员、时间、边界、权威或任何其他字段。
        标题必须表达这一阶段实际形成的主要结果；摘要必须区分 Primary 主要结果和 Supporting 支撑工作，不得把测试、配置或验证数量描述为用户成果。
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
        return INSTRUCTIONS + STORIES_MARKER + json(stories) + CHAPTERS_MARKER + json(chapters);
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
