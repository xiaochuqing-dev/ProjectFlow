package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class ProjectHistoryPromptBuilderTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ProjectHistoryPromptBuilder builder = new ProjectHistoryPromptBuilder(mapper);

    @Test
    void productionAndEvaluationShareTheSameBoundedCompleteRecordPacking() throws Exception {
        List<ProjectHistoryPromptBuilder.StoryPromptInput> stories = new ArrayList<>();
        List<ProjectHistoryPromptBuilder.ChapterPromptInput> chapters = new ArrayList<>();
        for (int index = 0; index < 120; index++) {
            String id = "story-" + index;
            stories.add(new ProjectHistoryPromptBuilder.StoryPromptInput(
                id, "项目要素 " + index, "2026-01-01T00:00:00Z", "2026-01-02T00:00:00Z",
                List.of("MODIFIED"), List.of("来源说明 ".repeat(30) + index), List.of("项目区域"),
                List.of("commit:" + String.format("%040d", index)), List.of(),
                "此前状态只按来源保留。", "来源记录显示项目要素发生变化。", "变化后形成可确认的新状态。"
            ));
            chapters.add(new ProjectHistoryPromptBuilder.ChapterPromptInput(
                "chapter-" + index, "2026-01-01T00:00:00Z", "2026-01-02T00:00:00Z",
                List.of(id), List.of("DENSITY_BOUNDARY")
            ));
        }
        var input = new ProjectHistoryPromptBuilder.PromptInput(stories, chapters);

        var production = builder.buildProduction(input);
        var evaluation = builder.buildEvaluation(input);

        assertThat(production).isEqualTo(evaluation);
        assertThat(production.promptCharacterCount()).isLessThanOrEqualTo(ProjectHistoryPromptBuilder.MAX_PROMPT_CHARS);
        assertThat(production.omittedStoryCount()).isGreaterThan(0);
        assertThat(production.includedStoryIds()).isNotEmpty();

        int storiesStart = production.prompt().indexOf("\nSTORIES_JSON=");
        int chaptersStart = production.prompt().indexOf("\nCHAPTERS_JSON=");
        JsonNode packedStories = mapper.readTree(production.prompt().substring(storiesStart + 14, chaptersStart));
        JsonNode packedChapters = mapper.readTree(production.prompt().substring(chaptersStart + 15));
        assertThat(packedStories.size()).isEqualTo(production.includedStoryIds().size());
        assertThat(packedChapters.size()).isEqualTo(production.includedChapterIds().size());
        assertThat(packedStories).allSatisfy(node -> assertThat(node.path("deterministicAfter").asText()).isNotBlank());
        assertThat(packedChapters).allSatisfy(node -> assertThat(production.includedStoryIds())
            .containsAll(mapper.convertValue(node.path("storyRefs"), List.class)));
    }
}
