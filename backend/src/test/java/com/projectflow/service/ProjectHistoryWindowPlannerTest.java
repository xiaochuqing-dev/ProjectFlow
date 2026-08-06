package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.projectflow.dto.ProjectHistoryDtos.ChangeStory;

class ProjectHistoryWindowPlannerTest {
    private final ProjectHistoryWindowPlanner planner = new ProjectHistoryWindowPlanner();

    @Test
    void preservesStoryOrderAndSplitsOnStoryAndEventBounds() {
        List<ChangeStory> stories = new ArrayList<>();
        for (int index = 0; index < 33; index++) stories.add(story("story-" + index, 1));

        List<ProjectHistoryWindowPlanner.Window> windows = planner.planAll(
            stories, ids(stories), "source-v1", "strategy-v1", "prompt-v1", "correction-v1"
        );

        assertThat(windows).hasSize(2);
        assertThat(windows.get(0).storyIds()).containsExactlyElementsOf(stories.subList(0, 32).stream().map(ChangeStory::id).toList());
        assertThat(windows.get(1).storyIds()).containsExactly("story-32");
        assertThat(windows.stream().mapToInt(ProjectHistoryWindowPlanner.Window::eventCount).sum()).isEqualTo(33);
    }

    @Test
    void capsExecutionPlanButKeepsCompletePlanAvailableForDiagnostics() {
        List<ChangeStory> stories = new ArrayList<>();
        for (int index = 0; index < (ProjectHistoryWindowPlanner.MAX_WINDOWS * ProjectHistoryWindowPlanner.DEFAULT_STORY_LIMIT) + 1; index++) {
            stories.add(story("story-" + index, 1));
        }

        List<ProjectHistoryWindowPlanner.Window> all = planner.planAll(
            stories, ids(stories), "source-v1", "strategy-v1", "prompt-v1", "correction-v1"
        );
        List<ProjectHistoryWindowPlanner.Window> execution = planner.plan(
            stories, ids(stories), "source-v1", "strategy-v1", "prompt-v1", "correction-v1"
        );

        assertThat(all).hasSize(ProjectHistoryWindowPlanner.MAX_WINDOWS + 1);
        assertThat(execution).hasSize(ProjectHistoryWindowPlanner.MAX_WINDOWS);
        assertThat(execution.stream().flatMap(window -> window.storyIds().stream()).distinct()).hasSize(
            ProjectHistoryWindowPlanner.MAX_WINDOWS * ProjectHistoryWindowPlanner.DEFAULT_STORY_LIMIT
        );
        assertThat(execution.get(0).ordinal()).isZero();
        assertThat(execution.get(ProjectHistoryWindowPlanner.MAX_WINDOWS - 1).ordinal())
            .isEqualTo(ProjectHistoryWindowPlanner.MAX_WINDOWS - 1);
    }

    @Test
    void eventLimitStartsTheNextWindowWithoutDroppingTheBoundaryStory() {
        List<ChangeStory> stories = new ArrayList<>();
        for (int index = 0; index < 31; index++) stories.add(story("event-story-" + index, 12));

        List<ProjectHistoryWindowPlanner.Window> windows = planner.planAll(
            stories, ids(stories), "source-v1", "strategy-v1", "prompt-v1", "correction-v1"
        );

        assertThat(windows).hasSize(2);
        assertThat(windows.get(0).eventCount()).isEqualTo(ProjectHistoryWindowPlanner.DEFAULT_EVENT_LIMIT);
        assertThat(windows.get(1).storyIds()).containsExactly("event-story-30");
        assertThat(windows.stream().flatMap(window -> window.storyIds().stream()).toList())
            .containsExactlyElementsOf(stories.stream().map(ChangeStory::id).toList());
    }

    @Test
    void keepsIdentityStableWhileCorrectionRevisionInvalidatesCacheKey() {
        List<ChangeStory> stories = List.of(story("stable-story", 1));
        ProjectHistoryWindowPlanner.Window first = planner.planAll(
            stories, Set.of("stable-story"), "source-v1", "strategy-v1", "prompt-v1", "correction-v1"
        ).get(0);
        ProjectHistoryWindowPlanner.Window corrected = planner.planAll(
            stories, Set.of("stable-story"), "source-v1", "strategy-v1", "prompt-v1", "correction-v2"
        ).get(0);

        assertThat(corrected.identity()).isEqualTo(first.identity());
        assertThat(corrected.cacheKey()).isNotEqualTo(first.cacheKey());
    }

    @Test
    void selectsTheNextIncompleteWindowInsteadOfStarvingTheTail() {
        List<ChangeStory> stories = new ArrayList<>();
        for (int index = 0; index < (ProjectHistoryWindowPlanner.MAX_WINDOWS + 1)
            * ProjectHistoryWindowPlanner.DEFAULT_STORY_LIMIT; index++) {
            stories.add(story("continuation-story-" + index, 1));
        }
        List<ProjectHistoryWindowPlanner.Window> all = planner.planAll(
            stories, ids(stories), "source-v1", "strategy-v1", "prompt-v1", "correction-v1"
        );
        Set<String> completed = all.subList(0, ProjectHistoryWindowPlanner.MAX_WINDOWS).stream()
            .map(ProjectHistoryWindowPlanner.Window::identity)
            .collect(LinkedHashSet::new, Set::add, Set::addAll);

        List<ProjectHistoryWindowPlanner.Window> next = planner.selectExecutionWindows(all, completed,
            ProjectHistoryWindowPlanner.MAX_WINDOWS);

        assertThat(next).hasSize(1);
        assertThat(next.get(0).ordinal()).isEqualTo(ProjectHistoryWindowPlanner.MAX_WINDOWS);
        assertThat(next.get(0).storyIds()).containsExactlyElementsOf(
            stories.subList(ProjectHistoryWindowPlanner.MAX_WINDOWS * ProjectHistoryWindowPlanner.DEFAULT_STORY_LIMIT,
                stories.size()).stream().map(ChangeStory::id).toList()
        );
    }

    private ChangeStory story(String id, int eventCount) {
        List<UUID> events = new ArrayList<>();
        for (int index = 0; index < eventCount; index++) events.add(UUID.nameUUIDFromBytes((id + index).getBytes()));
        return new ChangeStory(
            id, id, "可读标题", "可读摘要", "此前状态", "发生变化", "当前结果", List.of("项目结果"),
            "", List.of(), "", List.of(), List.of(), Instant.parse("2024-01-01T00:00:00Z"),
            Instant.parse("2024-01-01T00:00:00Z"), eventCount, eventCount, "ENGINEERING_GROUPING", "PASS",
            "FULL", List.of(), events, List.of("evidence:" + id)
        );
    }

    private Set<String> ids(List<ChangeStory> stories) {
        Set<String> result = new LinkedHashSet<>();
        stories.forEach(story -> result.add(story.id()));
        return result;
    }
}
