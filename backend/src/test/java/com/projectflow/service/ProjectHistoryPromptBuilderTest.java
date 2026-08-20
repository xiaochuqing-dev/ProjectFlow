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
        assertThat(builder.validationRepair(production.prompt(), "CONTRACT"))
            .hasSizeLessThanOrEqualTo(ProjectHistoryPromptBuilder.MAX_PROMPT_CHARS)
            .contains(ProjectHistoryPromptBuilder.VALIDATION_REPAIR_MARKER + "CONTRACT");
        assertThat(production.omittedStoryCount()).isGreaterThan(0);
        assertThat(production.includedStoryIds()).isNotEmpty();

        int storiesStart = production.prompt().indexOf("\nSTORIES_JSON=");
        int chaptersStart = production.prompt().indexOf("\nCHAPTERS_JSON=");
        JsonNode packedStories = mapper.readTree(production.prompt().substring(storiesStart + 14, chaptersStart));
        JsonNode packedChapters = mapper.readTree(production.prompt().substring(chaptersStart + 15));
        assertThat(packedStories.size()).isEqualTo(production.includedStoryIds().size());
        assertThat(packedChapters.size()).isEqualTo(production.includedChapterIds().size());
        assertThat(packedStories).allSatisfy(node -> {
            assertThat(node.path("deterministicAfter").asText()).isNotBlank();
            assertThat(node.path("claimState").asText()).isEqualTo("OBSERVED");
            assertThat(node.path("subjectDisplayConcept").asText()).isNotBlank();
            assertThat(node.has("sourceLabels")).isFalse();
            assertThat(node.has("affectedAreas")).isFalse();
            assertThat(node.has("evidenceRefs")).isFalse();
            assertThat(node.has("role")).isFalse();
            assertThat(node.has("primaryStoryId")).isFalse();
            assertThat(node.has("supportingChangeRefs")).isFalse();
        });
        assertThat(packedChapters).allSatisfy(node -> assertThat(production.includedStoryIds())
            .containsAll(mapper.convertValue(node.path("storyRefs"), List.class)));
    }

    @Test
    void chapterStageUsesOnlyBoundedValidatedStorySummaries() {
        List<ProjectHistoryPromptBuilder.ChapterStorySummaryInput> stories = new ArrayList<>();
        for (int index = 0; index < 200; index++) {
            stories.add(new ProjectHistoryPromptBuilder.ChapterStorySummaryInput(
                "story-" + index, "形成项目结果 " + index, "这项结果已经通过来源校验。" + "摘要".repeat(40),
                index % 5 == 0 ? "SUPPORTING" : "PRIMARY", "2026-01-01T00:00:00Z", "2026-01-02T00:00:00Z"
            ));
        }
        var input = new ProjectHistoryPromptBuilder.ChapterSynthesisPromptInput(
            "chapter-large", "2026-01-01T00:00:00Z", "2026-02-01T00:00:00Z", 200, 160, 40,
            List.of("DENSITY_BOUNDARY"), "membership-fingerprint", List.of(cluster("cluster-main")),
            List.of("cluster-main"), List.of("cluster-main"), 0.80, List.of(), List.of(),
            List.of("不得提高状态"), stories,
            "建立登录流程", "登录入口已经形成，并保留了相应支撑说明。"
        );

        var production = builder.buildChapterProduction(input);
        var evaluation = builder.buildChapterEvaluation(input);

        assertThat(production).isEqualTo(evaluation);
        assertThat(production.promptCharacterCount())
            .isLessThanOrEqualTo(ProjectHistoryPromptBuilder.MAX_CHAPTER_SYNTHESIS_PROMPT_CHARS);
        assertThat(production.includedStoryIds()).hasSize(ProjectHistoryPromptBuilder.MAX_CHAPTER_STORY_SUMMARIES)
            .doesNotContain("story-0");
        assertThat(production.omittedStoryCount()).isEqualTo(80);
        assertThat(production.prompt()).contains(
            "primaryStoryCount", "supportingStoryCount", "membershipFingerprint", "representativeClusters",
            "requiredRepresentativeClusterIds", "dominantClusterIds", "deterministicFallback", "cluster-main",
            "建立登录流程", "登录入口已经形成"
        )
            .doesNotContain("rawEvent", "evidenceRefs", "technicalDetails", "commitSummaries", "file:", "commit:");
    }

    @Test
    void chapterValidationRepairUsesTheChapterContractInsteadOfTheStoryTemplate() {
        var chapter = builder.buildChapterProduction(new ProjectHistoryPromptBuilder.ChapterSynthesisPromptInput(
            "chapter-one", "2026-01-01T00:00:00Z", "2026-01-02T00:00:00Z", 1, 1, 0,
            List.of("TIME_BOUNDARY"), "membership-fingerprint", List.of(
                new ProjectHistoryPromptBuilder.ChapterStorySummaryInput(
                    "story-one", "形成登录入口", "用户可以打开登录入口。", "PRIMARY",
                    "2026-01-01T00:00:00Z", "2026-01-02T00:00:00Z"
                )
            )
        ));

        String repair = builder.validationRepair(chapter.prompt(), "CONTRACT");

        assertThat(repair)
            .hasSizeLessThanOrEqualTo(ProjectHistoryPromptBuilder.MAX_PROMPT_CHARS)
            .contains(
                "CHAPTER_SYNTHESIS_JSON=",
                ProjectHistoryPromptBuilder.VALIDATION_REPAIR_MARKER + "CONTRACT",
                "必须把 CHAPTER_SYNTHESIS_JSON.deterministicFallback 原样包装",
                "只能包含 chapterId、representedClusterIds、title、summary",
                "representedClusterIds 必须逐项复制 requiredRepresentativeClusterIds",
                "原样返回整个 deterministicFallback",
                "{\"chapters\":[{\"chapterId\":\"\",\"representedClusterIds\":[],\"title\":\"\",\"summary\":\"\"}]}"
            )
            .doesNotContain("只能使用 OUTPUT_TEMPLATE_JSON");
    }

    private static ProjectHistoryPromptBuilder.ChapterRepresentativeClusterInput cluster(String id) {
        return new ProjectHistoryPromptBuilder.ChapterRepresentativeClusterInput(
            id, "登录流程", "DOMINANT", 4.0, 8, 2, List.of("story-one"),
            List.of("建立登录流程并形成登录入口"), List.of("IMPLEMENTED"), "IMPLEMENTED",
            List.of(), List.of()
        );
    }

    @Test
    void outputTemplateFreezesEveryRequiredIdAndKeepsUnknownReasonSafeByDefault() throws Exception {
        var input = new ProjectHistoryPromptBuilder.PromptInput(List.of(
            new ProjectHistoryPromptBuilder.StoryPromptInput(
                "story-without-reason", "项目结果", "2026-01-01T00:00:00Z", "2026-01-02T00:00:00Z",
                List.of("MODIFIED"), List.of(), List.of(), List.of("source:one"), List.of(),
                "此前状态", "形成变化", "当前状态", "OBSERVED", "OBSERVE", "只能描述可观察变化",
                List.of(), List.of(), "DIRECT", "原因证据缺失", List.of(), List.of(), List.of(),
                "PRIMARY", "", List.of(),
                "调整项目结果并保留未知原因", "项目结果已发生可观察变化，原因仍待核对。"
            ),
            new ProjectHistoryPromptBuilder.StoryPromptInput(
                "story-with-reason", "项目结果", "2026-01-01T00:00:00Z", "2026-01-02T00:00:00Z",
                List.of("MODIFIED"), List.of(), List.of(), List.of("source:two"), List.of("source:two"),
                "此前状态", "形成变化", "当前状态"
            )
        ), List.of(new ProjectHistoryPromptBuilder.ChapterPromptInput(
            "chapter-one", "2026-01-01T00:00:00Z", "2026-01-02T00:00:00Z",
            List.of("story-without-reason", "story-with-reason"), List.of("TIME_BOUNDARY")
        )));

        String prompt = builder.buildProduction(input).prompt();
        String templateMarker = "\nOUTPUT_TEMPLATE_JSON=";
        String storiesMarker = "\nSTORIES_JSON=";
        JsonNode template = mapper.readTree(prompt.substring(
            prompt.indexOf(templateMarker) + templateMarker.length(), prompt.indexOf(storiesMarker)
        ));

        assertThat(template.path("stories").findValuesAsText("storyId"))
            .containsExactly("story-without-reason", "story-with-reason");
        assertThat(template.path("chapters").findValuesAsText("chapterId")).containsExactly("chapter-one");
        assertThat(template.path("stories").get(0).path("unknownWording").asText())
            .contains("没有足够信息", "为什么");
        assertThat(template.path("stories").get(1).path("unknownWording").asText())
            .isEqualTo("目前没有足够信息确认为什么做这次调整。");
        assertThat(template.path("stories").get(0).path("beforeWording").asText()).isEqualTo("此前状态");
        assertThat(template.path("stories").get(0).path("changeWording").asText()).isEqualTo("形成变化");
        assertThat(template.path("stories").get(0).path("afterWording").asText()).isEqualTo("当前状态");
        assertThat(template.path("stories").get(0).path("humanTitle").asText())
            .isEqualTo("调整项目结果并保留未知原因");
        assertThat(template.path("stories").get(0).path("oneSentenceSummary").asText())
            .isEqualTo("项目结果已发生可观察变化，原因仍待核对。");
        assertThat(builder.validationRepair(prompt, "CROSS_PROJECT_REFERENCE"))
            .contains("REQUIRED_OUTPUT_TEMPLATE_JSON=", "story-without-reason", "chapter-one");
        assertThat(prompt).doesNotContain("DeepSeek", "GLM");
    }
}
