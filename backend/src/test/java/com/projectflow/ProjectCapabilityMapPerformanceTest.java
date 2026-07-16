package com.projectflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
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
    @Autowired EntityManager entityManager;

    @Test
    void servesLargeCapabilityMapWithBoundedQueriesAndLatency() {
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

        Sample overviewTiming = sample(8, () -> queryService.overview(userId, projectId));
        Sample listTiming = sample(8, () -> queryService.list(userId, projectId, "ACTIVE", "", "", "factCount", 0, 50));
        Sample detailTiming = sample(8, () -> queryService.detail(userId, capabilityId));
        Sample factsTiming = sample(8, () -> queryService.facts(userId, capabilityId, 0, 100));
        Sample changesTiming = sample(8, () -> queryService.changes(userId, projectId, 0, 100));

        long overviewQueries = statements(() -> queryService.overview(userId, projectId));
        long listQueries = statements(() -> queryService.list(userId, projectId, "ACTIVE", "", "", "factCount", 0, 50));
        long detailQueries = statements(() -> queryService.detail(userId, capabilityId));
        long factsQueries = statements(() -> queryService.facts(userId, capabilityId, 0, 100));
        long changesQueries = statements(() -> queryService.changes(userId, projectId, 0, 100));

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
    }

    private List<ProjectFact> insertFacts(UUID projectId) {
        List<UUID> batchIds = java.util.stream.IntStream.range(0, 100)
            .mapToObj(ignored -> UUID.randomUUID()).toList();
        List<ProjectFact> facts = new ArrayList<>(FACT_COUNT);
        Instant start = Instant.parse("2023-01-01T00:00:00Z");
        for (int index = 0; index < FACT_COUNT; index++) {
            Instant occurredAt = start.plus(index, ChronoUnit.HOURS);
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
