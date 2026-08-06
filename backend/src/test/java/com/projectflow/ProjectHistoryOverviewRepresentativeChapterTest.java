package com.projectflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.dto.ProjectHistoryDtos.ChangeStory;
import com.projectflow.dto.ProjectHistoryDtos.HistoryChapter;
import com.projectflow.dto.ProjectHistoryDtos.HistoryCoverage;
import com.projectflow.dto.ProjectHistoryDtos.HistoryOverviewContent;
import com.projectflow.entity.ProjectHistorySnapshot;
import com.projectflow.entity.ProjectSpace;
import com.projectflow.entity.ProjectStatus;
import com.projectflow.repository.ProjectHistorySnapshotRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.service.ProjectHistoryReadService;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProjectHistoryOverviewRepresentativeChapterTest {
    private static final Instant FIRST = Instant.parse("2020-01-01T00:00:00Z");

    @Autowired ProjectRepository projectRepository;
    @Autowired ProjectHistorySnapshotRepository snapshotRepository;
    @Autowired ProjectHistoryReadService readService;
    @Autowired ObjectMapper objectMapper;

    @Test
    void keepsRepresentativeCorrectedChaptersBoundedWhileThePagedInventoryRemainsComplete() throws Exception {
        UUID userId = UUID.randomUUID();
        ProjectSpace project = new ProjectSpace(userId);
        project.update("Representative chapters", "Bounded overview fixture", ProjectStatus.BUILDING,
            List.of(), "", LocalDate.of(2020, 1, 1), null);
        project = projectRepository.saveAndFlush(project);

        List<ChangeStory> stories = new ArrayList<>();
        List<HistoryChapter> chapters = new ArrayList<>();
        for (int index = 0; index < 100; index++) {
            Instant occurredAt = FIRST.plusSeconds(index * 86_400L);
            String storyId = "story-" + index;
            List<String> conflicts = index == 61 ? List.of("两个来源对当前结果存在冲突。") : List.of();
            List<String> unknowns = index == 74 ? List.of("来源状态为 UNKNOWN，需进一步核对。")
                : List.of("未发现可独立验证的变更原因；原因保持 UNKNOWN。 ");
            stories.add(story(storyId, occurredAt, conflicts, unknowns, index == 17));
            chapters.add(new HistoryChapter(
                "chapter-" + index, "阶段 " + index, "阶段摘要 " + index, occurredAt, occurredAt,
                List.of(), List.of(storyId), 1, 1, "ENGINEERING_GROUPING", "FULL_WITHIN_DISCOVERED_SOURCES",
                List.of(), index == 33 ? "USER_DECLARED_PRESENTATION" : "AUTOMATIC", "", index == 33,
                List.of(), false, index == 17
            ));
        }

        ProjectHistorySnapshot snapshot = new ProjectHistorySnapshot(project.getId());
        snapshot.complete(
            "fixture-revision", "fixture-fingerprint", 100, FIRST, FIRST.plusSeconds(99 * 86_400L),
            "project-history-v385-semantic-compression-v1", "project-history-synthesis-v3",
            objectMapper.writeValueAsString(new HistoryOverviewContent(
                "最早状态", "当前状态", List.of(), List.of(), List.of(), List.of()
            )),
            objectMapper.writeValueAsString(chapters), objectMapper.writeValueAsString(stories), "[]",
            objectMapper.writeValueAsString(new HistoryCoverage(
                true, "CURRENT", 100, 100, 0, 0, java.util.Map.of("GIT", 100), List.of(), List.of()
            )),
            "{}", UUID.randomUUID(), false
        );
        snapshotRepository.saveAndFlush(snapshot);

        List<String> overviewIds = readService.overview(userId, project.getId()).overview().chapters().stream()
            .map(value -> value.id()).toList();
        assertThat(overviewIds).hasSizeLessThanOrEqualTo(8)
            .contains("chapter-0", "chapter-99", "chapter-17", "chapter-33", "chapter-61", "chapter-74");
        assertThat(overviewIds.stream().filter(id -> {
            int index = Integer.parseInt(id.substring("chapter-".length()));
            return index > 20 && index < 80 && !List.of(33, 61, 74).contains(index);
        }).toList()).as("overview keeps at least one time-span representative").isNotEmpty();

        var allChapters = readService.chapters(userId, project.getId(), 0, 100);
        assertThat(allChapters.totalElements()).isEqualTo(100);
        assertThat(allChapters.items()).hasSize(100);
    }

    private static ChangeStory story(
        String id,
        Instant occurredAt,
        List<String> conflicts,
        List<String> unknowns,
        boolean pinned
    ) {
        UUID eventId = UUID.randomUUID();
        String title = "项目阶段结果 " + id.substring(id.lastIndexOf('-') + 1);
        return new ChangeStory(
            id, id, title, title + "已形成。", "此前状态", "形成阶段结果", "当前状态", List.of("项目结果"),
            "", List.of(), "", conflicts, unknowns, occurredAt, occurredAt, 1, 1, "ENGINEERING_GROUPING",
            "DETERMINISTIC", "FULL_WITHIN_DISCOVERED_SOURCES", List.of(), List.of(eventId),
            List.of("event:" + eventId), "PRIMARY", "", List.of(), List.of("atom:" + id), List.of(), List.of(),
            "AUTOMATIC", "", title, title + "已形成。", List.of(), false, pinned, "", "ACTIVE", List.of()
        );
    }
}
