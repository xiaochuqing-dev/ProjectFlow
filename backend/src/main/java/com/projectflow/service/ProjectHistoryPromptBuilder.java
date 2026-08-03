package com.projectflow.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Shared production/evaluation prompt builder for bounded project-history wording. */
@Component
public final class ProjectHistoryPromptBuilder {
    public static final String PROMPT_VERSION = "project-history-synthesis-v2";
    static final int MAX_PROMPT_CHARS = 60_000;
    static final int MAX_STORY_SECTION_CHARS = 46_000;
    static final int MAX_CHAPTER_SECTION_CHARS = 10_000;
    static final int MAX_STORY_RECORD_CHARS = 8_000;
    static final int MAX_CHAPTER_RECORD_CHARS = 4_000;

    private static final String STORIES_MARKER = "\nSTORIES_JSON=";
    private static final String CHAPTERS_MARKER = "\nCHAPTERS_JSON=";
    private static final String INSTRUCTIONS = """
        你正在改写 ProjectFlow 已由工程层确定成员关系的项目历程。只能改进中文表达，不能改变故事、篇章、时间、成员或 Evidence。
        只返回输入中列出的 storyId 和 chapterId；每个 ID 必须且只能返回一次，storyRefs 必须原样覆盖其允许集合。
        humanTitle 必须是“动作 + 对象 + 结果”，禁止只写“优化系统、改进功能、进行了重构、提升体验、修改相关文件”。
        工程层已经固定 Before / Change / After 与 laterOutcome；不得返回或改写这些字段。Commit message 只是线索，不是无需验证的事实。
        reason 只有在 reasonEvidenceRefs 非空且全部来自 reasonEligibleEvidenceRefs 时才可填写；否则 reason 必须为空并在 unknowns 写明原因未知。
        禁止重要性、成熟度、里程碑、成功判断、下一步、计划或建议。禁止创造 ID、Evidence、文件、数字、原因或项目状态。
        返回严格 JSON，不得增加字段：
        {"stories":[{"storyId":"","humanTitle":"","oneSentenceSummary":"","reason":"","reasonEvidenceRefs":[],"conflicts":[],"unknowns":[]}],
         "chapters":[{"chapterId":"","title":"","summary":"","storyRefs":[]}]}
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
            prompt, Set.copyOf(storyIds), Set.copyOf(chapterIds), prompt.length(),
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
        String deterministicAfter
    ) {
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
}
