package com.projectflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.projectflow.entity.EvidenceConfidence;
import com.projectflow.entity.ProjectFact;
import com.projectflow.entity.ProjectFactOrigin;
import com.projectflow.entity.ProjectFactRecordStatus;
import com.projectflow.entity.ProjectSpace;
import com.projectflow.entity.ProjectStatus;
import com.projectflow.entity.ProjectTimelineSummary;
import com.projectflow.entity.ProjectTimelineTheme;
import com.projectflow.entity.ProjectTimelineThemeFact;
import com.projectflow.entity.TimelineGranularity;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.repository.ProjectTimelineSummaryRepository;
import com.projectflow.repository.ProjectTimelineThemeFactRepository;
import com.projectflow.repository.ProjectTimelineThemeRepository;
import com.projectflow.service.ProjectTimelineService;
import com.projectflow.service.TimelinePeriodResolver;

import jakarta.persistence.EntityManager;

@SpringBootTest(properties = {
    "projectflow.timeline.zone=Asia/Shanghai",
    "spring.jpa.properties.hibernate.generate_statistics=true",
    "spring.jpa.properties.hibernate.session.events.log=false"
})
@ActiveProfiles("test")
@Transactional
class ProjectTimelinePerformanceTest {
    private static final int FACT_COUNT = 5_000;
    private static final int BATCH_COUNT = 100;
    private static final int MONTH_COUNT = 36;
    private static final int THEME_COUNT = 300;
    private static final String LARGE_MONTH = "2023-01";

    @Autowired ProjectRepository projectRepository;
    @Autowired ProjectTimelineSummaryRepository summaryRepository;
    @Autowired ProjectTimelineThemeRepository themeRepository;
    @Autowired ProjectTimelineThemeFactRepository themeFactRepository;
    @Autowired ProjectTimelineService timelineService;
    @Autowired TimelinePeriodResolver resolver;
    @Autowired EntityManager entityManager;

    @Test
    void servesLargeTimelineWithBoundedQueriesAndLatency() {
        UUID userId = UUID.randomUUID();
        ProjectSpace project = new ProjectSpace(userId);
        project.update("V3.4.1 timeline performance", "synthetic timeline gate", ProjectStatus.BUILDING,
            List.of("Spring Boot"), "", LocalDate.of(2023, 1, 1), null);
        project = projectRepository.saveAndFlush(project);
        UUID projectId = project.getId();
        List<UUID> factIds = insertFacts(projectId);
        insertLifecycleThemes(projectId, factIds);
        entityManager.flush();
        entityManager.clear();

        var overview = timelineService.overview(userId, projectId);
        var months = timelineService.periods(userId, projectId, TimelineGranularity.MONTH, null, null, 0, 50);
        var largeMonth = timelineService.period(userId, projectId, TimelineGranularity.MONTH, LARGE_MONTH, 0, 100);
        var lifecycle = timelineService.lifecycle(userId, projectId);
        assertThat(overview.factCount()).isEqualTo(FACT_COUNT);
        assertThat(overview.batchCount()).isEqualTo(BATCH_COUNT);
        assertThat(months.totalElements()).isEqualTo(MONTH_COUNT);
        assertThat(largeMonth.stats().factCount()).isEqualTo(230);
        assertThat(lifecycle.stages()).hasSize(THEME_COUNT);

        Sample overviewTiming = sample(10, () -> timelineService.overview(userId, projectId));
        Sample periodsTiming = sample(10, () -> timelineService.periods(
            userId, projectId, TimelineGranularity.MONTH, null, null, 0, 50
        ));
        Sample detailTiming = sample(10, () -> timelineService.period(
            userId, projectId, TimelineGranularity.MONTH, LARGE_MONTH, 0, 100
        ));
        Sample lifecycleTiming = sample(6, () -> timelineService.lifecycle(userId, projectId));

        long overviewQueries = statements(() -> timelineService.overview(userId, projectId));
        long periodsQueries = statements(() -> timelineService.periods(
            userId, projectId, TimelineGranularity.MONTH, null, null, 0, 50
        ));
        long detailQueries = statements(() -> timelineService.period(
            userId, projectId, TimelineGranularity.MONTH, LARGE_MONTH, 0, 100
        ));
        long lifecycleQueries = statements(() -> timelineService.lifecycle(userId, projectId));

        System.out.printf(
            "V341_TIMELINE_PERF facts=%d batches=%d months=%d themes=%d largeMonthFacts=%d "
                + "overviewP50Ms=%d overviewP95Ms=%d overviewQueries=%d "
                + "periodsP50Ms=%d periodsP95Ms=%d periodsQueries=%d "
                + "detailP50Ms=%d detailP95Ms=%d detailQueries=%d "
                + "lifecycleP50Ms=%d lifecycleP95Ms=%d lifecycleQueries=%d%n",
            FACT_COUNT, BATCH_COUNT, MONTH_COUNT, THEME_COUNT, largeMonth.stats().factCount(),
            overviewTiming.p50Ms(), overviewTiming.p95Ms(), overviewQueries,
            periodsTiming.p50Ms(), periodsTiming.p95Ms(), periodsQueries,
            detailTiming.p50Ms(), detailTiming.p95Ms(), detailQueries,
            lifecycleTiming.p50Ms(), lifecycleTiming.p95Ms(), lifecycleQueries
        );

        assertThat(overviewTiming.p95Ms()).isLessThan(2_500);
        assertThat(periodsTiming.p95Ms()).isLessThan(2_500);
        assertThat(detailTiming.p95Ms()).isLessThan(2_500);
        assertThat(lifecycleTiming.p95Ms()).isLessThan(3_500);
        assertThat(overviewQueries).isLessThanOrEqualTo(8);
        assertThat(periodsQueries).isLessThanOrEqualTo(10);
        assertThat(detailQueries).isLessThanOrEqualTo(12);
        assertThat(lifecycleQueries).isLessThanOrEqualTo(20);
    }

    private List<UUID> insertFacts(UUID projectId) {
        List<UUID> batchIds = java.util.stream.IntStream.range(0, BATCH_COUNT)
            .mapToObj(ignored -> UUID.randomUUID()).toList();
        List<YearMonth> months = java.util.stream.IntStream.range(0, MONTH_COUNT)
            .mapToObj(index -> YearMonth.of(2023, 1).plusMonths(index)).toList();
        List<UUID> ids = new ArrayList<>(FACT_COUNT);
        int index = 0;
        for (int monthIndex = 0; monthIndex < months.size(); monthIndex++) {
            int count = monthIndex == 0 ? 230 : 136 + (monthIndex <= 10 ? 1 : 0);
            for (int item = 0; item < count; item++, index++) {
                YearMonth month = months.get(monthIndex);
                Instant occurredAt = month.atDay(1 + item % 27).atTime(12, 0)
                    .atZone(ZoneId.of("Asia/Shanghai")).toInstant();
                ProjectFact fact = new ProjectFact(
                    projectId, batchIds.get(index % batchIds.size()), null,
                    ProjectFactOrigin.INCREMENTAL_SCAN, String.format("%064x", index + 1)
                );
                fact.updateContent(
                    "项目事实 " + index, "用于 V3.4.1 时间线性能验收的已发生事实。", List.of("记录可追溯变化"),
                    "可按时间查看", occurredAt, occurredAt, List.of(), List.of(), List.of(), List.of(),
                    List.of("perf:" + index), "MODEL_RESULT", "PASS", EvidenceConfidence.HIGH,
                    ProjectFactRecordStatus.RECORDED, ""
                );
                var assignment = resolver.assign(fact);
                fact.assignTimeline(assignment.eventAt(), assignment.dayKey(), assignment.weekKey(), assignment.monthKey());
                entityManager.persist(fact);
                ids.add(fact.getId());
                if ((index + 1) % 250 == 0) entityManager.flush();
            }
        }
        assertThat(index).isEqualTo(FACT_COUNT);
        return ids;
    }

    private void insertLifecycleThemes(UUID projectId, List<UUID> factIds) {
        var range = resolver.resolve(TimelineGranularity.LIFECYCLE, "ALL");
        ProjectTimelineSummary summary = new ProjectTimelineSummary(
            projectId, TimelineGranularity.LIFECYCLE, "ALL", range.startInclusive(), range.endExclusive(), resolver.zoneId()
        );
        summary.markDirty(FACT_COUNT, "performance-fingerprint", Instant.now());
        summary.complete("项目已形成 36 个月的可追溯演进。", FACT_COUNT, "fixed-performance", "none", null);
        summaryRepository.saveAndFlush(summary);
        for (int index = 0; index < THEME_COUNT; index++) {
            ProjectTimelineTheme theme = themeRepository.save(new ProjectTimelineTheme(
                summary.getId(), projectId, "演进主题 " + index, "基于已记录事实形成的演进主题。", index
            ));
            themeFactRepository.save(new ProjectTimelineThemeFact(projectId, theme.getId(), factIds.get(index)));
        }
    }

    private Sample sample(int iterations, Runnable action) {
        List<Long> elapsed = new ArrayList<>(iterations);
        action.run();
        for (int index = 0; index < iterations; index++) {
            entityManager.clear();
            long started = System.nanoTime();
            action.run();
            elapsed.add((System.nanoTime() - started) / 1_000_000);
        }
        Collections.sort(elapsed);
        return new Sample(percentile(elapsed, 0.50), percentile(elapsed, 0.95));
    }

    private long statements(Runnable action) {
        Statistics statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        entityManager.clear();
        action.run();
        return statistics.getPrepareStatementCount();
    }

    private long percentile(List<Long> sorted, double percentile) {
        return sorted.get(Math.min(sorted.size() - 1, (int) Math.ceil(sorted.size() * percentile) - 1));
    }

    private record Sample(long p50Ms, long p95Ms) {
    }
}
