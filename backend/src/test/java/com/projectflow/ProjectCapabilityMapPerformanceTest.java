package com.projectflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
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

import com.fasterxml.jackson.databind.ObjectMapper;

import com.projectflow.entity.EvidenceConfidence;
import com.projectflow.entity.ProjectCapability;
import com.projectflow.entity.ProjectCapabilityEvolution;
import com.projectflow.entity.ProjectCapabilityEvolutionType;
import com.projectflow.entity.ProjectCapabilityFact;
import com.projectflow.entity.ProjectCapabilityMapState;
import com.projectflow.entity.ProjectCapabilityMaturity;
import com.projectflow.entity.ProjectCapabilityRelationRole;
import com.projectflow.entity.ProjectFact;
import com.projectflow.entity.ProjectFactOrigin;
import com.projectflow.entity.ProjectFactRecordStatus;
import com.projectflow.entity.ProjectSpace;
import com.projectflow.entity.ProjectStatus;
import com.projectflow.repository.ProjectCapabilityEvolutionRepository;
import com.projectflow.repository.ProjectCapabilityFactRepository;
import com.projectflow.repository.ProjectCapabilityMapStateRepository;
import com.projectflow.repository.ProjectCapabilityRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.service.ProjectCapabilityQueryService;
import com.projectflow.service.ProjectMemoryGatewayService;
import com.projectflow.service.TimelinePeriodResolver;

import jakarta.persistence.EntityManager;

@SpringBootTest(properties = {
    "spring.jpa.properties.hibernate.generate_statistics=true",
    "spring.jpa.properties.hibernate.session.events.log=false"
})
@ActiveProfiles("test")
@Transactional
class ProjectCapabilityMapPerformanceTest {
    private static final int FACT_COUNT = 5_000;
    private static final int CAPABILITY_COUNT = 100;
    private static final int EVOLUTION_COUNT = 1_000;
    private static final int RELATION_COUNT = 10_000;

    @Autowired ProjectRepository projectRepository;
    @Autowired ProjectCapabilityRepository capabilityRepository;
    @Autowired ProjectCapabilityEvolutionRepository evolutionRepository;
    @Autowired ProjectCapabilityFactRepository capabilityFactRepository;
    @Autowired ProjectCapabilityMapStateRepository stateRepository;
    @Autowired ProjectCapabilityQueryService queryService;
    @Autowired ProjectMemoryGatewayService memoryGateway;
    @Autowired TimelinePeriodResolver timelineResolver;
    @Autowired ObjectMapper objectMapper;
    @Autowired EntityManager entityManager;

    @Test
    void servesLargeCapabilityMapWithBoundedQueriesAndLatency() throws Exception {
        UUID userId = UUID.randomUUID();
        ProjectSpace project = new ProjectSpace(userId);
        project.update("V3.4.2 capability performance", "synthetic capability gate", ProjectStatus.BUILDING,
            List.of("Spring Boot"), "", LocalDate.of(2023, 1, 1), null);
        project = projectRepository.saveAndFlush(project);
        UUID projectId = project.getId();
        List<ProjectFact> facts = insertFacts(projectId);
        List<ProjectCapability> capabilities = insertCapabilities(projectId);
        insertEvolutionsAndRelations(projectId, facts, capabilities);
        ProjectCapabilityMapState state = new ProjectCapabilityMapState(projectId);
        state.complete(FACT_COUNT, FACT_COUNT, FACT_COUNT, 0, 0, "performance-fingerprint",
            facts.get(FACT_COUNT - 1).getOccurredTo(), null);
        stateRepository.save(state);
        entityManager.flush();
        entityManager.clear();

        List<String> timelineMonths = entityManager.createQuery(
            "select distinct fact.timelineMonthKey from ProjectFact fact where fact.projectId = :projectId order by fact.timelineMonthKey",
            String.class
        ).setParameter("projectId", projectId).getResultList();
        assertThat(timelineMonths).hasSize(36).allMatch(value -> value.matches("\\d{4}-\\d{2}"));

        var overview = queryService.overview(userId, projectId);
        var list = queryService.list(userId, projectId, "ACTIVE", "", "", "factCount", 0, 50);
        UUID capabilityId = list.items().get(0).id();
        var detail = queryService.detail(userId, capabilityId);
        var evolutions = queryService.evolutions(userId, capabilityId, 0, 20);
        var capabilityFacts = queryService.facts(userId, capabilityId, 0, 100);
        var changes = queryService.changes(userId, projectId, 0, 100);

        assertThat(overview.sourceFactCount()).isEqualTo(FACT_COUNT);
        assertThat(overview.capabilityCount()).isEqualTo(CAPABILITY_COUNT);
        assertThat(list.totalElements()).isEqualTo(CAPABILITY_COUNT);
        assertThat(detail.factCount()).isEqualTo(100);
        assertThat(evolutions.totalElements()).isEqualTo(10);
        assertThat(capabilityFacts.totalElements()).isEqualTo(100);
        assertThat(changes.totalElements()).isEqualTo(EVOLUTION_COUNT);

        var snapshot = memoryGateway.snapshot(userId, projectId);
        var recent = memoryGateway.recentChanges(userId, projectId, null, null, true, 0, 20, "compact");
        var search = memoryGateway.search(userId, projectId, "项目事实 4999", null, null, null, 0, 20, "compact");
        var months = memoryGateway.timeline(userId, projectId, "MONTH", "", null, null, 0, 50, "detailed");
        var month = memoryGateway.timeline(userId, projectId, "MONTH", "2023-01", null, null, 0, 100, "detailed");
        var lifecycle = memoryGateway.timeline(userId, projectId, "LIFECYCLE", "", null, null, 0, 20, "compact");
        var memoryCapabilities = memoryGateway.capabilities(userId, projectId, true, "", "", 0, 50, "detailed");
        var memoryEvolution = memoryGateway.capabilityEvolution(userId, projectId, capabilityId, 0, 20, "detailed");
        var trace = memoryGateway.traceFact(userId, projectId, facts.get(0).getId(), "compact");
        var brief = memoryGateway.brief(userId, projectId, 6_000);

        assertThat(snapshot.factCount()).isEqualTo(FACT_COUNT);
        assertThat(recent.items()).hasSize(20);
        assertThat(search.items()).isNotEmpty();
        assertThat(months.periods().totalElements()).isEqualTo(36);
        assertThat(month.period().facts().totalElements()).isGreaterThan(100);
        assertThat(lifecycle.lifecycle().stats().factCount()).isEqualTo(FACT_COUNT);
        assertThat(memoryCapabilities.totalElements()).isEqualTo(CAPABILITY_COUNT);
        assertThat(memoryEvolution.totalElements()).isEqualTo(10);
        assertThat(trace.factId()).isEqualTo(facts.get(0).getId());
        assertThat(brief.actualCharacters()).isLessThanOrEqualTo(6_000);

        Sample overviewTiming = sample(8, () -> queryService.overview(userId, projectId));
        Sample listTiming = sample(8, () -> queryService.list(userId, projectId, "ACTIVE", "", "", "factCount", 0, 50));
        Sample detailTiming = sample(8, () -> queryService.detail(userId, capabilityId));
        Sample factsTiming = sample(8, () -> queryService.facts(userId, capabilityId, 0, 100));
        Sample changesTiming = sample(8, () -> queryService.changes(userId, projectId, 0, 100));
        Sample snapshotTiming = sample(5, () -> memoryGateway.snapshot(userId, projectId));
        Sample recentTiming = sample(5, () -> memoryGateway.recentChanges(userId, projectId, null, null, true, 0, 20, "compact"));
        Sample searchTiming = sample(5, () -> memoryGateway.search(userId, projectId, "项目事实 4999", null, null, null, 0, 20, "compact"));
        Sample monthTiming = sample(5, () -> memoryGateway.timeline(userId, projectId, "MONTH", "2023-01", null, null, 0, 100, "detailed"));
        Sample lifecycleTiming = sample(5, () -> memoryGateway.timeline(userId, projectId, "LIFECYCLE", "", null, null, 0, 20, "compact"));
        Sample gatewayCapabilitiesTiming = sample(5, () -> memoryGateway.capabilities(userId, projectId, true, "", "", 0, 50, "detailed"));
        Sample evolutionTiming = sample(5, () -> memoryGateway.capabilityEvolution(userId, projectId, capabilityId, 0, 20, "detailed"));
        Sample traceTiming = sample(5, () -> memoryGateway.traceFact(userId, projectId, facts.get(0).getId(), "compact"));
        Sample briefTiming = sample(5, () -> memoryGateway.brief(userId, projectId, 6_000));

        long overviewQueries = statements(() -> queryService.overview(userId, projectId));
        long listQueries = statements(() -> queryService.list(userId, projectId, "ACTIVE", "", "", "factCount", 0, 50));
        long detailQueries = statements(() -> queryService.detail(userId, capabilityId));
        long factsQueries = statements(() -> queryService.facts(userId, capabilityId, 0, 100));
        long changesQueries = statements(() -> queryService.changes(userId, projectId, 0, 100));
        long snapshotQueries = statements(() -> memoryGateway.snapshot(userId, projectId));
        long recentQueries = statements(() -> memoryGateway.recentChanges(userId, projectId, null, null, true, 0, 20, "compact"));
        long searchQueries = statements(() -> memoryGateway.search(userId, projectId, "项目事实 4999", null, null, null, 0, 20, "compact"));
        long monthQueries = statements(() -> memoryGateway.timeline(userId, projectId, "MONTH", "2023-01", null, null, 0, 100, "detailed"));
        long lifecycleQueries = statements(() -> memoryGateway.timeline(userId, projectId, "LIFECYCLE", "", null, null, 0, 20, "compact"));
        long gatewayCapabilitiesQueries = statements(() -> memoryGateway.capabilities(userId, projectId, true, "", "", 0, 50, "detailed"));
        long evolutionQueries = statements(() -> memoryGateway.capabilityEvolution(userId, projectId, capabilityId, 0, 20, "detailed"));
        long traceQueries = statements(() -> memoryGateway.traceFact(userId, projectId, facts.get(0).getId(), "compact"));
        long briefQueries = statements(() -> memoryGateway.brief(userId, projectId, 6_000));

        System.out.printf(
            "V342_CAPABILITY_PERF facts=%d capabilities=%d evolutions=%d relations=%d "
                + "overviewP50Ms=%d overviewP95Ms=%d overviewQueries=%d "
                + "listP50Ms=%d listP95Ms=%d listQueries=%d "
                + "detailP50Ms=%d detailP95Ms=%d detailQueries=%d "
                + "factsP50Ms=%d factsP95Ms=%d factsQueries=%d "
                + "changesP50Ms=%d changesP95Ms=%d changesQueries=%d%n",
            FACT_COUNT, CAPABILITY_COUNT, EVOLUTION_COUNT, RELATION_COUNT,
            overviewTiming.p50Ms(), overviewTiming.p95Ms(), overviewQueries,
            listTiming.p50Ms(), listTiming.p95Ms(), listQueries,
            detailTiming.p50Ms(), detailTiming.p95Ms(), detailQueries,
            factsTiming.p50Ms(), factsTiming.p95Ms(), factsQueries,
            changesTiming.p50Ms(), changesTiming.p95Ms(), changesQueries
        );

        System.out.printf(
            "V343_MEMORY_GATEWAY_PERF facts=%d months=%d capabilities=%d evolutions=%d relations=%d "
                + "snapshotP50Ms=%d snapshotP95Ms=%d snapshotQueries=%d snapshotBytes=%d "
                + "recentP50Ms=%d recentP95Ms=%d recentQueries=%d recentBytes=%d "
                + "searchP50Ms=%d searchP95Ms=%d searchQueries=%d searchBytes=%d "
                + "monthP50Ms=%d monthP95Ms=%d monthQueries=%d monthBytes=%d "
                + "lifecycleP50Ms=%d lifecycleP95Ms=%d lifecycleQueries=%d lifecycleBytes=%d "
                + "capabilitiesP50Ms=%d capabilitiesP95Ms=%d capabilitiesQueries=%d capabilitiesBytes=%d "
                + "evolutionP50Ms=%d evolutionP95Ms=%d evolutionQueries=%d evolutionBytes=%d "
                + "traceP50Ms=%d traceP95Ms=%d traceQueries=%d traceBytes=%d "
                + "briefP50Ms=%d briefP95Ms=%d briefQueries=%d briefBytes=%d%n",
            FACT_COUNT, 36, CAPABILITY_COUNT, EVOLUTION_COUNT, RELATION_COUNT,
            snapshotTiming.p50Ms(), snapshotTiming.p95Ms(), snapshotQueries, objectMapper.writeValueAsBytes(snapshot).length,
            recentTiming.p50Ms(), recentTiming.p95Ms(), recentQueries, objectMapper.writeValueAsBytes(recent).length,
            searchTiming.p50Ms(), searchTiming.p95Ms(), searchQueries, objectMapper.writeValueAsBytes(search).length,
            monthTiming.p50Ms(), monthTiming.p95Ms(), monthQueries, objectMapper.writeValueAsBytes(month).length,
            lifecycleTiming.p50Ms(), lifecycleTiming.p95Ms(), lifecycleQueries, objectMapper.writeValueAsBytes(lifecycle).length,
            gatewayCapabilitiesTiming.p50Ms(), gatewayCapabilitiesTiming.p95Ms(), gatewayCapabilitiesQueries, objectMapper.writeValueAsBytes(memoryCapabilities).length,
            evolutionTiming.p50Ms(), evolutionTiming.p95Ms(), evolutionQueries, objectMapper.writeValueAsBytes(memoryEvolution).length,
            traceTiming.p50Ms(), traceTiming.p95Ms(), traceQueries, objectMapper.writeValueAsBytes(trace).length,
            briefTiming.p50Ms(), briefTiming.p95Ms(), briefQueries, objectMapper.writeValueAsBytes(brief).length
        );

        assertThat(overviewTiming.p95Ms()).isLessThan(2_500);
        assertThat(listTiming.p95Ms()).isLessThan(2_500);
        assertThat(detailTiming.p95Ms()).isLessThan(2_500);
        assertThat(factsTiming.p95Ms()).isLessThan(2_500);
        assertThat(changesTiming.p95Ms()).isLessThan(2_500);
        assertThat(overviewQueries).isLessThanOrEqualTo(18);
        assertThat(listQueries).isLessThanOrEqualTo(5);
        assertThat(detailQueries).isLessThanOrEqualTo(12);
        assertThat(factsQueries).isLessThanOrEqualTo(4);
        assertThat(changesQueries).isLessThanOrEqualTo(4);
        assertThat(snapshotTiming.p95Ms()).isLessThan(3_500);
        assertThat(recentTiming.p95Ms()).isLessThan(2_500);
        assertThat(searchTiming.p95Ms()).isLessThan(3_500);
        assertThat(monthTiming.p95Ms()).isLessThan(3_500);
        assertThat(lifecycleTiming.p95Ms()).isLessThan(3_500);
        assertThat(gatewayCapabilitiesTiming.p95Ms()).isLessThan(2_500);
        assertThat(evolutionTiming.p95Ms()).isLessThan(2_500);
        assertThat(traceTiming.p95Ms()).isLessThan(2_500);
        assertThat(briefTiming.p95Ms()).isLessThan(3_500);
        assertThat(snapshotQueries).isLessThanOrEqualTo(45);
        assertThat(recentQueries).isLessThanOrEqualTo(8);
        assertThat(searchQueries).isLessThanOrEqualTo(15);
        assertThat(monthQueries).isLessThanOrEqualTo(14);
        assertThat(lifecycleQueries).isLessThanOrEqualTo(20);
        assertThat(gatewayCapabilitiesQueries).isLessThanOrEqualTo(5);
        assertThat(evolutionQueries).isLessThanOrEqualTo(8);
        assertThat(traceQueries).isLessThanOrEqualTo(12);
        assertThat(briefQueries).isLessThanOrEqualTo(55);
    }

    private List<ProjectFact> insertFacts(UUID projectId) {
        List<UUID> batchIds = java.util.stream.IntStream.range(0, 100)
            .mapToObj(ignored -> UUID.randomUUID()).toList();
        List<ProjectFact> facts = new ArrayList<>(FACT_COUNT);
        for (int index = 0; index < FACT_COUNT; index++) {
            YearMonth month = YearMonth.of(2023, 1).plusMonths(index % 36);
            Instant occurredAt = month.atDay(1 + (index / 36) % 27).atTime(12, index % 60)
                .atZone(ZoneId.of("Asia/Shanghai")).toInstant();
            ProjectFact fact = new ProjectFact(
                projectId, batchIds.get(index % batchIds.size()), null,
                ProjectFactOrigin.INCREMENTAL_SCAN, String.format("%064x", index + 1)
            );
            fact.updateContent(
                "项目事实 " + index, "用于 V3.4.2 能力地图性能验收的已发生事实。", List.of("记录事实关系"),
                "支持长期能力追溯", occurredAt, occurredAt,
                List.of("commit-" + index), List.of(), List.of("src/file-" + index + ".java"), List.of(),
                List.of("perf:" + index), "MODEL_RESULT", "PASS", EvidenceConfidence.HIGH,
                ProjectFactRecordStatus.RECORDED, ""
            );
            var assignment = timelineResolver.assign(fact);
            fact.assignTimeline(assignment.eventAt(), assignment.dayKey(), assignment.weekKey(), assignment.monthKey());
            entityManager.persist(fact);
            facts.add(fact);
            if ((index + 1) % 250 == 0) entityManager.flush();
        }
        return facts;
    }

    private List<ProjectCapability> insertCapabilities(UUID projectId) {
        List<ProjectCapability> capabilities = new ArrayList<>(CAPABILITY_COUNT);
        Instant start = Instant.parse("2023-01-01T00:00:00Z");
        for (int index = 0; index < CAPABILITY_COUNT; index++) {
            ProjectCapability capability = new ProjectCapability(
                projectId, String.format("%064x", index + 10_001), String.format("%064x", index + 20_001)
            );
            capability.initialize(
                "长期能力 " + index, "持续积累的项目能力。", "解决问题 " + index, "长期价值 " + index,
                List.of("area-" + index % 10), start.plus(index, ChronoUnit.DAYS), "MODEL", "fixed", "perf", null
            );
            capability.updateStatistics(100, 50, 100, 100, 0, 10, ProjectCapabilityMaturity.CONTINUOUSLY_ENHANCED,
                "多个批次和演进事件形成持续增强。", start.plus(index, ChronoUnit.DAYS), start.plus(index + 90, ChronoUnit.DAYS));
            entityManager.persist(capability);
            capabilities.add(capability);
        }
        return capabilities;
    }

    private void insertEvolutionsAndRelations(
        UUID projectId, List<ProjectFact> facts, List<ProjectCapability> capabilities
    ) {
        int evolutionCount = 0;
        int relationCount = 0;
        for (int capabilityIndex = 0; capabilityIndex < capabilities.size(); capabilityIndex++) {
            ProjectCapability capability = capabilities.get(capabilityIndex);
            List<ProjectCapabilityEvolution> evolutions = new ArrayList<>(10);
            for (int version = 0; version < 10; version++) {
                ProjectCapabilityEvolution evolution = new ProjectCapabilityEvolution(
                    projectId, capability.getId(), version == 0 ? ProjectCapabilityEvolutionType.NEW_CAPABILITY : ProjectCapabilityEvolutionType.ENHANCE_CAPABILITY,
                    Math.max(0, version), version + 1, "演进 " + version, "有事实支撑的能力演进。",
                    Instant.parse("2023-01-01T00:00:00Z").plus(capabilityIndex * 10L + version, ChronoUnit.DAYS),
                    String.format("%064x", 30_001 + evolutionCount)
                );
                evolution.attachSourceStats(10, 5, List.of("2023-01"), null, "fixed", "perf");
                entityManager.persist(evolution);
                evolutions.add(evolution);
                evolutionCount++;
            }
            for (int offset = 0; offset < 100; offset++) {
                ProjectFact fact = facts.get((capabilityIndex * 50 + offset) % facts.size());
                ProjectCapabilityEvolution source = evolutions.get(offset % evolutions.size());
                entityManager.persist(new ProjectCapabilityFact(
                    projectId, capability.getId(), fact.getId(),
                    offset == 0 ? ProjectCapabilityRelationRole.FORMATION : ProjectCapabilityRelationRole.ENHANCEMENT,
                    source.getId()
                ));
                relationCount++;
                if (relationCount % 500 == 0) entityManager.flush();
            }
        }
        assertThat(evolutionCount).isEqualTo(EVOLUTION_COUNT);
        assertThat(relationCount).isEqualTo(RELATION_COUNT);
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
