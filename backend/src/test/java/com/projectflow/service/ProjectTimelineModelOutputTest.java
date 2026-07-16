package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.entity.ProjectTimelineSummaryStatus;

class ProjectTimelineModelOutputTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @ParameterizedTest
    @CsvSource({
        "false,true,WAITING_FOR_MODEL,false,true",
        "false,true,FAILED,false,true",
        "false,true,GENERATING,true,true",
        "false,true,GENERATING,false,false",
        "false,false,READY,false,false",
        "true,true,READY,false,false"
    })
    void keepsOnlyReusableOrActivelyGeneratingSummaryState(
        boolean force, boolean sameFingerprint, ProjectTimelineSummaryStatus status, boolean activeJob, boolean expected
    ) {
        assertThat(ProjectTimelineSummaryService.keepsExistingState(force, sameFingerprint, status, activeJob))
            .isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
        "false,true,QUEUED,false,true",
        "false,true,GENERATING,false,true",
        "false,true,GENERATING,true,false",
        "false,false,GENERATING,false,false",
        "true,true,GENERATING,false,false"
    })
    void detectsOnlyInactiveSameVersionGenerationAsInterrupted(
        boolean force, boolean sameFingerprint, ProjectTimelineSummaryStatus status, boolean activeJob, boolean expected
    ) {
        assertThat(ProjectTimelineSummaryService.interruptedGeneration(force, sameFingerprint, status, activeJob))
            .isEqualTo(expected);
    }

    @Test
    void acceptsCompleteCoverage() throws Exception {
        List<String> ids = ids(3);
        assertThat(ProjectTimelineSummaryService.validatePeriodOutput(period(ids, ids, List.of()), ids)).isEqualTo(3);
    }

    @Test
    void ungroupedIdsCountAsCovered() throws Exception {
        List<String> ids = ids(3);
        assertThat(ProjectTimelineSummaryService.validatePeriodOutput(
            period(ids, ids.subList(0, 2), ids.subList(2, 3)), ids
        )).isEqualTo(3);
    }

    @Test
    void duplicateMembershipUsesFirstThemeWithoutInflatingCoverage() throws Exception {
        List<String> ids = ids(2);
        JsonNode root = objectMapper.readTree("""
            {"periodSummary":"已发生的变化","themes":[
              {"title":"A","summary":"A","factIds":["%s","%s"]},
              {"title":"B","summary":"B","factIds":["%s"]}
            ],"ungroupedFactIds":[]}
            """.formatted(ids.get(0), ids.get(1), ids.get(0)));
        assertThat(ProjectTimelineSummaryService.validatePeriodOutput(root, ids)).isEqualTo(2);
    }

    @Test
    void rejectsUnknownOrCrossProjectFactId() throws Exception {
        List<String> ids = ids(2);
        assertThatThrownBy(() -> ProjectTimelineSummaryService.validatePeriodOutput(
            period(ids, List.of(ids.get(0), UUID.randomUUID().toString()), List.of()), ids
        )).isInstanceOf(ProjectTimelineSummaryService.TimelineCoverageException.class);
    }

    @Test
    void missingOneFactCannotBecomeReady() throws Exception {
        List<String> ids = ids(3);
        assertThatThrownBy(() -> ProjectTimelineSummaryService.validatePeriodOutput(
            period(ids, ids.subList(0, 2), List.of()), ids
        )).isInstanceOf(ProjectTimelineSummaryService.TimelineCoverageException.class)
            .hasMessageContaining("omitted 1");
    }

    @Test
    void validatesAll230FactsWithoutRecentLimit() throws Exception {
        List<String> ids = ids(230);
        assertThat(ProjectTimelineSummaryService.validatePeriodOutput(period(ids, ids, List.of()), ids)).isEqualTo(230);
    }

    @ParameterizedTest
    @CsvSource({ "1,1", "120,1", "121,3", "230,3", "240,3", "241,4", "500,6" })
    void plansBoundedChunkAndSynthesisRequests(int facts, int requests) {
        assertThat(ProjectTimelineSummaryService.plannedPeriodRequestCount(facts)).isEqualTo(requests);
    }

    @ParameterizedTest
    @ValueSource(strings = { "nextSteps", "recommendations", "futurePlan", "priority", "developerShould", "roadmap" })
    void rejectsPlanningFields(String field) throws Exception {
        List<String> ids = ids(1);
        JsonNode root = objectMapper.readTree("""
            {"periodSummary":"已发生变化","themes":[{"title":"A","summary":"A","factIds":["%s"]}],"ungroupedFactIds":[],"%s":[]}
            """.formatted(ids.get(0), field));
        assertThatThrownBy(() -> ProjectTimelineSummaryService.validatePeriodOutput(root, ids))
            .isInstanceOf(ProjectTimelineSummaryService.TimelineCoverageException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = { "下一步优化", "形成未来计划", "后续建议如下", "项目路线图", "应该继续开发" })
    void rejectsFuturePlanningLanguage(String summary) throws Exception {
        List<String> ids = ids(1);
        JsonNode root = objectMapper.readTree("""
            {"periodSummary":"%s","themes":[{"title":"A","summary":"A","factIds":["%s"]}],"ungroupedFactIds":[]}
            """.formatted(summary, ids.get(0)));
        assertThatThrownBy(() -> ProjectTimelineSummaryService.validatePeriodOutput(root, ids))
            .isInstanceOf(ProjectTimelineSummaryService.TimelineCoverageException.class);
    }

    @Test
    void lifecycleCoversEveryMonthIncludingEarliest() throws Exception {
        List<String> months = List.of("2024-01", "2025-06", "2026-07");
        JsonNode root = objectMapper.readTree("""
            {"periodSummary":"项目形成连续演进","stages":[{"title":"演进阶段","summary":"已有变化","monthKeys":["2024-01","2025-06","2026-07"]}],"ungroupedMonthKeys":[]}
            """);
        assertThat(ProjectTimelineSummaryService.validateLifecycleOutput(root, months)).isEqualTo(3);
    }

    private JsonNode period(List<String> allowed, List<String> themed, List<String> ungrouped) {
        return objectMapper.valueToTree(java.util.Map.of(
            "periodSummary", "本时间段完成了已记录的项目变化",
            "themes", List.of(java.util.Map.of("title", "演进主题", "summary", "已有事实", "factIds", themed)),
            "ungroupedFactIds", ungrouped
        ));
    }

    private List<String> ids(int count) {
        return IntStream.range(0, count).mapToObj(ignored -> UUID.randomUUID().toString()).toList();
    }
}
