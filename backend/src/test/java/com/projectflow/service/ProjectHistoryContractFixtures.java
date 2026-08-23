package com.projectflow.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.projectflow.dto.ProjectHistoryDtos.ChangeStory;
import com.projectflow.dto.ProjectHistoryDtos.EvolutionThread;
import com.projectflow.dto.ProjectHistoryDtos.HistoryChapter;

final class ProjectHistoryContractFixtures {
    private ProjectHistoryContractFixtures() {
    }

    static ChangeStory story(String id, String role, String primaryId, List<String> supportingRefs) {
        return story(id, role, primaryId, supportingRefs, "presentation:test");
    }

    static ChangeStory story(
        String id, String role, String primaryId, List<String> supportingRefs, String revision
    ) {
        UUID eventId = UUID.nameUUIDFromBytes(("event:" + id).getBytes(StandardCharsets.UTF_8));
        String evidence = "commit:" + UUID.nameUUIDFromBytes(("evidence:" + id).getBytes(StandardCharsets.UTF_8))
            .toString().replace("-", "");
        return new ChangeStory(
            id, "project-history", "完善项目历程并形成可读结果", "项目历程已经形成可追溯的阅读结果。",
            "此前尚未形成该结果。", "来源记录显示项目历程发生变化。", "当前结果可以继续核对。",
            List.of("项目历程"), "", List.of(), "", List.of(), List.of("原因保持 UNKNOWN。"),
            Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"),
            1, 1, "ENGINEERING_GROUPING", "DETERMINISTIC", "FULL_WITHIN_DISCOVERED_SOURCES", List.of(),
            List.of(eventId), List.of(evidence), role, primaryId, supportingRefs, List.of(), List.of(), List.of(),
            "AUTOMATIC", revision, "完善项目历程并形成可读结果",
            "项目历程已经形成可追溯的阅读结果。", List.of(), false, false, "", "ACTIVE", List.of()
        );
    }

    static HistoryChapter chapter(String id, List<ChangeStory> stories, String revision) {
        return new HistoryChapter(
            id, "项目历程形成可读阶段", "这一阶段形成主要结果，并保留支撑工作供下钻。",
            Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"),
            List.of("SOURCE_BOUNDARY"), stories.stream().map(ChangeStory::id).toList(), stories.size(),
            (int) stories.stream().flatMap(value -> value.eventRefs().stream()).distinct().count(),
            "ENGINEERING_GROUPING", "FULL_WITHIN_DISCOVERED_SOURCES", List.of(),
            "AUTOMATIC", revision, false, List.of(), false, false
        );
    }

    static EvolutionThread thread(String id, List<ChangeStory> stories, String revision) {
        return new EvolutionThread(
            id, "project-history", "项目历程", "PROJECT_SUBJECT", stories.stream().map(ChangeStory::id).toList(),
            List.of("CREATED", "MODIFIED"), "项目历程已经形成当前结果。", List.of(), List.of(), List.of(),
            (int) stories.stream().flatMap(value -> value.evidenceRefs().stream()).distinct().count(), null,
            "AUTOMATIC", revision, List.of()
        );
    }
}
