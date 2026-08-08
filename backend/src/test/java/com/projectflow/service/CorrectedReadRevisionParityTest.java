package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.projectflow.dto.ProjectHistoryDtos.EvolutionThreadDetailResponse;
import com.projectflow.dto.ProjectHistoryDtos.HistoryChapterDetailResponse;
import com.projectflow.dto.ProjectHistoryDtos.HistoryStoryDetailResponse;

class CorrectedReadRevisionParityTest {
    @Test
    void detailReadsExposeTheSameRevisionEvenWhenTheyContainDifferentDensities() {
        String revision = "presentation:one-read-view";
        UUID projectId = UUID.randomUUID();
        var story = ProjectHistoryContractFixtures.story("story-a", "PRIMARY", "", List.of(), revision);
        var chapter = ProjectHistoryContractFixtures.chapter("chapter-a", List.of(story), revision);
        var thread = ProjectHistoryContractFixtures.thread("thread-a", List.of(story), revision);

        var chapterDetail = new HistoryChapterDetailResponse(projectId, revision, chapter, List.of(story));
        var storyDetail = new HistoryStoryDetailResponse(projectId, revision, story, List.of(), List.of(thread));
        var threadDetail = new EvolutionThreadDetailResponse(projectId, revision, thread, List.of(story));

        assertThat(List.of(
            chapterDetail.presentationRevision(), storyDetail.presentationRevision(), threadDetail.presentationRevision(),
            chapterDetail.chapter().presentationRevision(), storyDetail.story().presentationRevision(),
            threadDetail.thread().presentationRevision()
        )).allMatch(revision::equals);
    }
}
