package com.projectflow.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.projectflow.dto.ProjectHistoryDtos.ChangeStory;
import com.projectflow.dto.ProjectHistoryDtos.HistoryChapter;

class ProjectHistoryV385ReviewArtifactTest {

    @Test
    void keepsRealPresentationRevisionAndReadableFieldsInFrozenCandidates() {
        String revision = "presentation:review-candidate";
        UUID eventId = UUID.randomUUID();
        ChangeStory story = new ChangeStory(
            "story-review", "history", "整理项目历程", "项目历程已经形成可阅读结果。",
            "此前结果分散。", "将相关结果整理为一条历程。", "现在可以按时间阅读。",
            List.of("项目历程"), "来源记录说明了整理原因。", List.of("fact:review"), "结果保持可追溯。",
            List.of(), List.of(), Instant.parse("2026-08-01T00:00:00Z"),
            Instant.parse("2026-08-02T00:00:00Z"), 1, 1, "ENGINEERING_GROUPING", "MODEL_VALIDATED",
            "FULL_WITHIN_DISCOVERED_SOURCES", List.of(), List.of(eventId), List.of("fact:review"),
            "PRIMARY", "", List.of(), List.of(), List.of(), List.of(), "AUTOMATIC", revision,
            "整理项目历程", "项目历程已经形成可阅读结果。", List.of(), false, false, "", "ACTIVE", List.of()
        );
        HistoryChapter chapter = new HistoryChapter(
            "chapter-review", "形成可阅读历程", "这一阶段完成了历程整理。",
            Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-08-02T00:00:00Z"),
            List.of("SOURCE_BOUNDARY"), List.of(story.id()), 1, 1, "ENGINEERING_GROUPING",
            "FULL_WITHIN_DISCOVERED_SOURCES", List.of(), "AUTOMATIC", revision, false, List.of(), false, false
        );
        var observation = new ProjectHistoryV385QualityEvaluator.CaseObservation(
            "holdout-review", "HOLDOUT", List.of(story), List.of(chapter), List.of(), List.of(), Map.of(),
            List.of(), List.of(), revision, 1, 1, 1, 100
        );

        Map<String, Object> candidate = ProjectHistoryV385RealOutputEvaluatorTest
            .humanReviewCandidates(List.of(observation)).get(0);
        Map<?, ?> storySample = (Map<?, ?>) ((List<?>) candidate.get("stories")).get(0);
        Map<?, ?> chapterSample = (Map<?, ?>) ((List<?>) candidate.get("chapters")).get(0);

        assertThat(storySample.get("presentationRevision")).isEqualTo(revision);
        assertThat(storySample.get("summaryStatus")).isEqualTo("MODEL_VALIDATED");
        assertThat(storySample.get("reason")).isEqualTo("来源记录说明了整理原因。");
        assertThat(storySample.get("before")).isEqualTo("此前结果分散。");
        assertThat(storySample.get("after")).isEqualTo("现在可以按时间阅读。");
        assertThat(chapterSample.get("presentationRevision")).isEqualTo(revision);
        assertThat(chapterSample.get("storyCount")).isEqualTo(1);
        assertThat(chapterSample.get("summary")).isEqualTo("这一阶段完成了历程整理。");
    }
}
