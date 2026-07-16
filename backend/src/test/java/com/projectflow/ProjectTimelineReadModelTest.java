package com.projectflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.projectflow.entity.EvidenceConfidence;
import com.projectflow.entity.ProjectFact;
import com.projectflow.entity.ProjectFactAgentResultRef;
import com.projectflow.entity.ProjectFactCommitRef;
import com.projectflow.entity.ProjectFactFileRef;
import com.projectflow.entity.ProjectFactOrigin;
import com.projectflow.entity.ProjectFactRecordStatus;
import com.projectflow.entity.ProjectSpace;
import com.projectflow.entity.ProjectStatus;
import com.projectflow.entity.ProjectTimelineSummary;
import com.projectflow.entity.ProjectTimelineSummaryStatus;
import com.projectflow.entity.ProjectTimelineTheme;
import com.projectflow.entity.ProjectTimelineThemeFact;
import com.projectflow.entity.TimelineGranularity;
import com.projectflow.repository.ProjectFactAgentResultRefRepository;
import com.projectflow.repository.ProjectFactCommitRefRepository;
import com.projectflow.repository.ProjectFactFileRefRepository;
import com.projectflow.repository.ProjectFactRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.repository.ProjectTimelineSummaryRepository;
import com.projectflow.repository.ProjectTimelineThemeFactRepository;
import com.projectflow.repository.ProjectTimelineThemeRepository;
import com.projectflow.service.ProjectTimelineService;
import com.projectflow.service.TimelinePeriodResolver;
import com.projectflow.support.AppException;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProjectTimelineReadModelTest {
    @Autowired ProjectRepository projectRepository;
    @Autowired ProjectFactRepository factRepository;
    @Autowired ProjectFactCommitRefRepository commitRepository;
    @Autowired ProjectFactFileRefRepository fileRepository;
    @Autowired ProjectFactAgentResultRefRepository agentRepository;
    @Autowired ProjectTimelineSummaryRepository summaryRepository;
    @Autowired ProjectTimelineThemeRepository themeRepository;
    @Autowired ProjectTimelineThemeFactRepository themeFactRepository;
    @Autowired ProjectTimelineService timelineService;
    @Autowired TimelinePeriodResolver resolver;

    UUID userId;
    UUID otherUserId;
    ProjectSpace project;
    ProjectSpace otherProject;
    ProjectFact juneFact;
    List<ProjectFact> julyFacts;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();
        project = project(userId, "Timeline A");
        otherProject = project(otherUserId, "Timeline B");
        UUID batchA = UUID.randomUUID();
        UUID batchB = UUID.randomUUID();
        juneFact = fact(project.getId(), batchA, "2026-06-29T08:00:00Z", ProjectFactRecordStatus.RECORDED);
        ProjectFact julyOne = fact(project.getId(), batchA, "2026-07-01T08:00:00Z", ProjectFactRecordStatus.RECORDED);
        ProjectFact julyTwo = fact(project.getId(), batchB, "2026-07-15T08:00:00Z", ProjectFactRecordStatus.NEEDS_ATTENTION);
        ProjectFact julyThree = fact(project.getId(), batchB, "2026-07-15T10:00:00Z", ProjectFactRecordStatus.RECORDED);
        julyFacts = List.of(julyOne, julyTwo, julyThree);

        commit(juneFact, "aaaaaaaa");
        commit(julyOne, "bbbbbbbb");
        commit(julyTwo, "bbbbbbbb");
        commit(julyTwo, "cccccccc");
        file(julyOne, "src/shared.java");
        file(julyTwo, "src/shared.java");
        file(julyTwo, "src/other.java");
        agent(julyTwo, "agent-result:one");
        agent(julyThree, "agent-result:two");
        factRepository.flush();
    }

    @Test
    void overviewUsesDatabaseAggregatesAndDistinctCommitCoverage() {
        var overview = timelineService.overview(userId, project.getId());
        assertThat(overview.factCount()).isEqualTo(4);
        assertThat(overview.batchCount()).isEqualTo(2);
        assertThat(overview.commitCoverage().coveredCommitCount()).isEqualTo(3);
        assertThat(overview.timelineZone()).isEqualTo(resolver.zoneId());
        assertThat(overview.earliestFactAt()).isEqualTo(juneFact.getTimelineEventAt());
    }

    @Test
    void overviewReturnsZeroStatisticsForAnOwnedProjectWithoutFacts() {
        var overview = timelineService.overview(otherUserId, otherProject.getId());
        assertThat(overview.factCount()).isZero();
        assertThat(overview.batchCount()).isZero();
        assertThat(overview.earliestFactAt()).isNull();
        assertThat(overview.latestFactAt()).isNull();
    }

    @Test
    void monthStatsAreDistinctAndCrossMonthFactHasOnePrimaryMonth() {
        var page = timelineService.periods(userId, project.getId(), TimelineGranularity.MONTH, null, null, 0, 20);
        assertThat(page.totalElements()).isEqualTo(2);
        var july = page.items().stream().filter(item -> item.periodKey().equals("2026-07")).findFirst().orElseThrow();
        assertThat(july.stats().factCount()).isEqualTo(3);
        assertThat(july.stats().batchCount()).isEqualTo(2);
        assertThat(july.stats().commitCount()).isEqualTo(2);
        assertThat(july.stats().fileCount()).isEqualTo(2);
        assertThat(july.stats().agentResultCount()).isEqualTo(2);
        assertThat(july.stats().attentionCount()).isEqualTo(1);
    }

    @Test
    void dayWeekAndMonthPaginationUsePrimaryAssignments() {
        var days = timelineService.periods(userId, project.getId(), TimelineGranularity.DAY, null, null, 0, 1);
        assertThat(days.size()).isEqualTo(1);
        assertThat(days.totalElements()).isEqualTo(3);
        assertThat(days.totalPages()).isEqualTo(3);
        assertThat(days.items().get(0).summaryStatus()).isEqualTo("NOT_REQUIRED");

        var weeks = timelineService.periods(userId, project.getId(), TimelineGranularity.WEEK, null, null, 0, 20);
        assertThat(weeks.items()).extracting(item -> item.periodKey()).contains("2026-W27", "2026-W29");
    }

    @Test
    void periodDetailPaginatesFactsAndReturnsExactRange() {
        var detail = timelineService.period(userId, project.getId(), TimelineGranularity.MONTH, "2026-07", 0, 2);
        assertThat(detail.facts().items()).hasSize(2);
        assertThat(detail.facts().totalElements()).isEqualTo(3);
        assertThat(detail.periodStart()).isEqualTo(resolver.resolve(TimelineGranularity.MONTH, "2026-07").startInclusive());
        assertThat(detail.sourceFactCount()).isEqualTo(3);
    }

    @Test
    void lifecycleCountsEveryFactOnceAndReturnsCompactMonths() {
        var lifecycle = timelineService.lifecycle(userId, project.getId());
        assertThat(lifecycle.stats().factCount()).isEqualTo(4);
        assertThat(lifecycle.months()).hasSize(2);
        assertThat(lifecycle.months()).extracting(item -> item.stats().factCount()).containsExactlyInAnyOrder(1L, 3L);
        assertThat(lifecycle.sourceFactCount()).isEqualTo(4);
    }

    @Test
    void staleSummaryKeepsOldThemesAndNewFactsVisible() {
        var range = resolver.resolve(TimelineGranularity.MONTH, "2026-07");
        ProjectTimelineSummary summary = new ProjectTimelineSummary(
            project.getId(), TimelineGranularity.MONTH, "2026-07", range.startInclusive(), range.endExclusive(), resolver.zoneId()
        );
        summary.markDirty(2, "old", Instant.parse("2026-07-15T08:00:00Z"));
        summary.complete("旧版本摘要仍可读取", 2, "fixed", "fixed", UUID.randomUUID());
        summary.markDirty(3, "new", Instant.parse("2026-07-15T10:00:00Z"));
        summary = summaryRepository.saveAndFlush(summary);
        ProjectTimelineTheme theme = themeRepository.saveAndFlush(new ProjectTimelineTheme(
            summary.getId(), project.getId(), "已有演进主题", "旧主题在更新失败前保留", 0
        ));
        themeFactRepository.saveAndFlush(new ProjectTimelineThemeFact(project.getId(), theme.getId(), julyFacts.get(0).getId()));

        var detail = timelineService.period(userId, project.getId(), TimelineGranularity.MONTH, "2026-07", 0, 20);
        assertThat(detail.currentSummary().stale()).isTrue();
        assertThat(detail.currentSummary().summary()).isEqualTo("旧版本摘要仍可读取");
        assertThat(detail.themes()).extracting(item -> item.title()).containsExactly("已有演进主题");
        assertThat(detail.facts().totalElements()).isEqualTo(3);
    }

    @Test
    void themeFactsAreExactAndPaged() {
        var range = resolver.resolve(TimelineGranularity.MONTH, "2026-07");
        ProjectTimelineSummary summary = new ProjectTimelineSummary(
            project.getId(), TimelineGranularity.MONTH, "2026-07", range.startInclusive(), range.endExclusive(), resolver.zoneId()
        );
        summary.markDirty(3, "ready", Instant.now());
        summary.complete("摘要", 3, "fixed", "fixed", UUID.randomUUID());
        summary = summaryRepository.saveAndFlush(summary);
        ProjectTimelineTheme theme = themeRepository.saveAndFlush(new ProjectTimelineTheme(
            summary.getId(), project.getId(), "主题 A", "只包含两个事实", 0
        ));
        themeFactRepository.save(new ProjectTimelineThemeFact(project.getId(), theme.getId(), julyFacts.get(0).getId()));
        themeFactRepository.saveAndFlush(new ProjectTimelineThemeFact(project.getId(), theme.getId(), julyFacts.get(1).getId()));

        var response = timelineService.themeFacts(userId, project.getId(), theme.getId(), 0, 1);
        assertThat(response.facts().totalElements()).isEqualTo(2);
        assertThat(response.facts().items()).hasSize(1);
    }

    @Test
    void ownershipAndCrossProjectThemeAccessAreRejected() {
        assertThatThrownBy(() -> timelineService.overview(otherUserId, project.getId()))
            .isInstanceOf(AppException.class);

        var range = resolver.resolve(TimelineGranularity.MONTH, "2026-07");
        ProjectTimelineSummary summary = summaryRepository.saveAndFlush(new ProjectTimelineSummary(
            project.getId(), TimelineGranularity.MONTH, "2026-07", range.startInclusive(), range.endExclusive(), resolver.zoneId()
        ));
        ProjectTimelineTheme theme = themeRepository.saveAndFlush(new ProjectTimelineTheme(
            summary.getId(), project.getId(), "私有主题", "不可跨项目读取", 0
        ));
        assertThatThrownBy(() -> timelineService.themeFacts(otherUserId, otherProject.getId(), theme.getId(), 0, 20))
            .isInstanceOf(AppException.class);
    }

    @Test
    void invalidPeriodKeysAndLifecycleListGranularityReturnClientErrors() {
        assertThatThrownBy(() -> timelineService.period(
            userId, project.getId(), TimelineGranularity.MONTH, "2026-13", 0, 20
        )).isInstanceOf(AppException.class);
        assertThatThrownBy(() -> timelineService.periods(
            userId, project.getId(), TimelineGranularity.LIFECYCLE, null, null, 0, 20
        )).isInstanceOf(AppException.class);
    }

    private ProjectSpace project(UUID owner, String name) {
        ProjectSpace value = new ProjectSpace(owner);
        value.update(name, "Timeline test", ProjectStatus.BUILDING, List.of("Spring Boot"), "", LocalDate.now(), null);
        return projectRepository.saveAndFlush(value);
    }

    private ProjectFact fact(UUID projectId, UUID batchId, String occurredAt, ProjectFactRecordStatus status) {
        Instant instant = Instant.parse(occurredAt);
        String fingerprint = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        ProjectFact fact = new ProjectFact(projectId, batchId, UUID.randomUUID(), ProjectFactOrigin.INCREMENTAL_SCAN, fingerprint);
        fact.updateContent(
            "项目事实 " + occurredAt, "已记录的项目变化", List.of("完成已有变化"), "形成可追溯结果",
            instant.minusSeconds(60), instant, List.of(), List.of(), List.of(), List.of(), List.of("commit:evidence"),
            "MODEL", "PASS", EvidenceConfidence.HIGH, status, status == ProjectFactRecordStatus.NEEDS_ATTENTION ? "边界需要关注" : ""
        );
        var assignment = resolver.assign(fact);
        fact.assignTimeline(assignment.eventAt(), assignment.dayKey(), assignment.weekKey(), assignment.monthKey());
        return factRepository.saveAndFlush(fact);
    }

    private void commit(ProjectFact fact, String sha) {
        commitRepository.save(new ProjectFactCommitRef(fact.getProjectId(), fact.getId(), sha));
    }

    private void file(ProjectFact fact, String path) {
        fileRepository.save(new ProjectFactFileRef(fact.getProjectId(), fact.getId(), path));
    }

    private void agent(ProjectFact fact, String ref) {
        agentRepository.save(new ProjectFactAgentResultRef(fact.getProjectId(), fact.getId(), ref));
    }
}
