package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.projectflow.dto.ProjectHistoryDtos.EvolutionThreadPageResponse;
import com.projectflow.dto.ProjectHistoryDtos.HistoryChapterPageResponse;
import com.projectflow.dto.ProjectHistoryDtos.HistoryCorrectionListResponse;
import com.projectflow.dto.ProjectHistoryDtos.HistoryStoryPageResponse;

class CrossConsumerHistoryConsistencyTest {
    @Test
    void apiGatewayAgentHermesFrontendAndObsidianCanShareOneRevisionAndMembershipGraph() {
        String revision = "presentation:corrected-view";
        UUID projectId = UUID.randomUUID();
        var primary = ProjectHistoryContractFixtures.story(
            "story-a", "PRIMARY", "", List.of("story-split"), revision
        );
        var split = ProjectHistoryContractFixtures.story(
            "story-split", "SUPPORTING", "story-a", List.of(), revision
        );
        var chapter = ProjectHistoryContractFixtures.chapter("chapter-a", List.of(primary, split), revision);
        var thread = ProjectHistoryContractFixtures.thread("thread-a", List.of(primary, split), revision);
        var stories = new HistoryStoryPageResponse(projectId, revision, List.of(primary, split), 0, 100, 2, 1);
        var chapters = new HistoryChapterPageResponse(projectId, revision, List.of(chapter), 0, 100, 1, 1);
        var threads = new EvolutionThreadPageResponse(projectId, revision, List.of(thread), 0, 100, 1, 1);
        var corrections = new HistoryCorrectionListResponse(projectId, List.of(), revision, false);

        assertThat(List.of(
            stories.presentationRevision(), chapters.presentationRevision(), threads.presentationRevision(),
            corrections.presentationRevision(), primary.presentationRevision(), split.presentationRevision(),
            chapter.presentationRevision(), thread.presentationRevision()
        )).allMatch(revision::equals);
        assertThat(chapters.items().get(0).storyRefs()).containsExactly(primary.id(), split.id());
        assertThat(threads.items().get(0).storyRefs()).containsExactly(primary.id(), split.id());
        assertThat(primary.supportingChangeRefs()).containsExactly(split.id());
        assertThat(split.primaryStoryId()).isEqualTo(primary.id());
    }
}
