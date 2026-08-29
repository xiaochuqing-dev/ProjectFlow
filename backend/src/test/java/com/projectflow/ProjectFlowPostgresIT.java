package com.projectflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.dto.ProjectHistoryDtos.ChangeStory;
import com.projectflow.dto.ProjectHistoryDtos.EvolutionThread;
import com.projectflow.dto.ProjectHistoryDtos.HistoryChapter;
import com.projectflow.dto.ProjectHistoryDtos.HistoryCorrectionRequest;
import com.projectflow.dto.ProjectHistoryDtos.HistoryCoverage;
import com.projectflow.dto.ProjectHistoryDtos.HistoryOverviewContent;
import com.projectflow.dto.V33WorkflowDtos.CapabilityCardAction;
import com.projectflow.entity.AiProvider;
import com.projectflow.entity.AiProviderType;
import com.projectflow.entity.ProjectAnalysisJob;
import com.projectflow.entity.ProjectAnalysisJobStatus;
import com.projectflow.entity.ProjectAnalysisJobType;
import com.projectflow.entity.EvidenceConfidence;
import com.projectflow.entity.ProjectCapability;
import com.projectflow.entity.ProjectCapabilityEvolution;
import com.projectflow.entity.ProjectCapabilityEvolutionType;
import com.projectflow.entity.ProjectCapabilityFact;
import com.projectflow.entity.ProjectCapabilityFactClassification;
import com.projectflow.entity.ProjectCapabilityFactCoverage;
import com.projectflow.entity.ProjectCapabilityMapState;
import com.projectflow.entity.ProjectCapabilityRelationRole;
import com.projectflow.entity.ProjectFact;
import com.projectflow.entity.ProjectFactOrigin;
import com.projectflow.entity.ProjectFactRecordStatus;
import com.projectflow.entity.ProjectHistoryCorrection;
import com.projectflow.entity.ProjectHistorySnapshot;
import com.projectflow.entity.ProjectMemory;
import com.projectflow.entity.ProjectSediment;
import com.projectflow.entity.ProjectSpace;
import com.projectflow.entity.ProjectStatus;
import com.projectflow.repository.AiProviderRepository;
import com.projectflow.repository.ProjectAnalysisJobRepository;
import com.projectflow.repository.ProjectCapabilityCardRepository;
import com.projectflow.repository.ProjectCapabilityEvolutionRepository;
import com.projectflow.repository.ProjectCapabilityFactCoverageRepository;
import com.projectflow.repository.ProjectCapabilityFactRepository;
import com.projectflow.repository.ProjectCapabilityMapStateRepository;
import com.projectflow.repository.ProjectCapabilityRepository;
import com.projectflow.repository.ProjectChangeRepository;
import com.projectflow.repository.ProjectMemoryRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.repository.ProjectSedimentRepository;
import com.projectflow.repository.ProjectFactCursorRepository;
import com.projectflow.repository.ProjectFactRepository;
import com.projectflow.repository.ProjectHistoryCorrectionRepository;
import com.projectflow.repository.ProjectHistorySnapshotRepository;
import com.projectflow.repository.ProjectHistoryWindowCheckpointRepository;
import com.projectflow.service.ProjectAnalysisJobRunner;
import com.projectflow.service.ProjectAnalysisJobService;
import com.projectflow.service.ProjectCapabilityService;
import com.projectflow.service.ProjectCapabilityQueryService;
import com.projectflow.service.DashboardBootstrapService;
import com.projectflow.service.ProjectHistoryCorrectionService;
import com.projectflow.service.ProjectContinuityDirtyMarker;
import com.projectflow.service.ProjectHistoryWindowCheckpointService;
import com.projectflow.service.ProjectService;
import com.projectflow.service.ProviderCredentialStore;
import com.projectflow.service.WorkSessionScanService;
import com.projectflow.support.AppException;
import com.sun.net.httpserver.HttpServer;

@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=update",
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
})
@ActiveProfiles("ci")
class ProjectFlowPostgresIT {
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("projectflow_test")
        .withUsername("projectflow")
        .withPassword("projectflow_test");

    static {
        POSTGRES.start();
    }

    private static final AtomicInteger FAIL_NEXT = new AtomicInteger();
    private static HttpServer modelServer;
    private static int modelPort;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired ProjectRepository projectRepository;
    @Autowired ProjectMemoryRepository memoryRepository;
    @Autowired AiProviderRepository providerRepository;
    @Autowired ProjectAnalysisJobRepository jobRepository;
    @Autowired ProjectChangeRepository changeRepository;
    @Autowired ProjectSedimentRepository sedimentRepository;
    @Autowired ProjectFactRepository factRepository;
    @Autowired ProjectFactCursorRepository factCursorRepository;
    @Autowired ProjectCapabilityCardRepository cardRepository;
    @Autowired ProjectCapabilityRepository capabilityRepository;
    @Autowired ProjectCapabilityEvolutionRepository capabilityEvolutionRepository;
    @Autowired ProjectCapabilityFactRepository capabilityFactRepository;
    @Autowired ProjectCapabilityFactCoverageRepository capabilityCoverageRepository;
    @Autowired ProjectCapabilityMapStateRepository capabilityMapStateRepository;
    @Autowired ProjectHistoryCorrectionRepository historyCorrectionRepository;
    @Autowired ProjectHistorySnapshotRepository historySnapshotRepository;
    @Autowired ProjectHistoryWindowCheckpointRepository historyWindowCheckpointRepository;
    @Autowired WorkSessionScanService workSessionScanService;
    @Autowired ProjectCapabilityService capabilityService;
    @Autowired ProjectCapabilityQueryService capabilityQueryService;
    @Autowired DashboardBootstrapService dashboardBootstrapService;
    @Autowired ProjectHistoryCorrectionService historyCorrectionService;
    @Autowired ProjectContinuityDirtyMarker continuityDirtyMarker;
    @Autowired ProjectHistoryWindowCheckpointService historyWindowCheckpointService;
    @Autowired ProjectService projectService;
    @Autowired ProviderCredentialStore providerCredentialStore;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired ObjectMapper objectMapper;

    @BeforeAll
    static void startModelServer() throws IOException {
        modelServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        modelPort = modelServer.getAddress().getPort();
        modelServer.createContext("/v1/chat/completions", exchange -> {
            String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            boolean capabilityRequest = requestBody.contains("capabilities") || requestBody.contains("项目能力");
            if (capabilityRequest && FAIL_NEXT.getAndUpdate(value -> Math.max(0, value - 1)) > 0) {
                writeResponse(exchange, 503, "{\"error\":{\"message\":\"controlled PostgreSQL workflow failure\"}}");
                return;
            }
            if (requestBody.contains("ALLOWED_FACT_IDS_JSON=")) {
                String content = capabilityMapContent(requestBody);
                String escaped = new ObjectMapper().writeValueAsString(content);
                writeResponse(exchange, 200, "{\"choices\":[{\"message\":{\"content\":" + escaped + "},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":120,\"completion_tokens\":80,\"total_tokens\":200}}");
                return;
            }
            if (requestBody.contains("ALLOWED_IDS_JSON=") || requestBody.contains("ALLOWED_MONTH_KEYS_JSON=")) {
                String content = timelineContent(requestBody);
                String escaped = new ObjectMapper().writeValueAsString(content);
                writeResponse(exchange, 200, "{\"choices\":[{\"message\":{\"content\":" + escaped + "},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":120,\"completion_tokens\":80,\"total_tokens\":200}}");
                return;
            }
            String content = capabilityRequest
                ? "{\"capabilities\":[{\"name\":\"PostgreSQL 工作流可靠性\",\"summary\":\"真实数据库下完成沉淀与能力闭环。\",\"problemSolved\":\"验证事务和关系持久化。\",\"featureEntry\":\"能力与成果\",\"sourceIndexes\":[\"S1\"],\"readme\":\"真实 PostgreSQL 16 工作流验证。\",\"resume\":\"完成项目沉淀到能力卡片闭环。\",\"interview\":\"可说明事务、幂等和失败保留。\"}]}"
                : "{\"segments\":[{\"segmentTitle\":\"完成 PostgreSQL 自动事实工作流验证\",\"plainSummary\":\"将固定 Git 证据整理为开发推进段并自动记录项目事实。\",\"sourceIndexes\":[\"S1\"],\"mainChanges\":[\"读取提交证据\",\"生成分析批次\",\"自动形成项目事实\"],\"userVisibleValue\":\"验证真实数据库自动事实闭环。\",\"affectedFiles\":[],\"confidence\":\"HIGH\",\"needsUserReview\":false}]}";
            String escaped = new ObjectMapper().writeValueAsString(content);
            writeResponse(exchange, 200, "{\"choices\":[{\"message\":{\"content\":" + escaped + "},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":120,\"completion_tokens\":80,\"total_tokens\":200}}");
        });
        modelServer.setExecutor(Executors.newCachedThreadPool());
        modelServer.start();
    }

    @AfterAll
    static void stopModelServer() {
        if (modelServer != null) modelServer.stop(0);
        POSTGRES.stop();
    }

    @Test
    void persistsCoreWorkflowAndCancellationStateInRealPostgres() {
        WorkflowFixture fixture = createFixture("PostgreSQL 基础验收");
        ProjectAnalysisJob job = new ProjectAnalysisJob(fixture.project().getId(), fixture.userId(), ProjectAnalysisJobType.PROJECT, null);
        job.configureExecution("fingerprint", "idempotency-key", 0);
        jobRepository.saveAndFlush(job);
        job.markRunning();
        job.requestCancellation();
        job.markCancelled();
        jobRepository.saveAndFlush(job);

        ProjectAnalysisJob loaded = jobRepository.findById(job.getId()).orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(ProjectAnalysisJobStatus.CANCELLED);
        assertThat(loaded.getRequestCount()).isZero();
        assertThat(loaded.getResultJson()).isNull();
    }

    @Test
    void executesSedimentCapabilityAndRetryWorkflowInRealPostgres() throws Exception {
        WorkflowFixture fixture = createFixture("PostgreSQL 完整工作流");
        Path repository = createGitProject();
        ProjectMemory memory = new ProjectMemory(fixture.project().getId());
        memory.update("测试项目", "收尾验证", "", "", "", "", "", "", "");
        memory.rememberLocalProjectPath(repository.toAbsolutePath().normalize().toString());
        memoryRepository.saveAndFlush(memory);

        var scan = workSessionScanService.scan(fixture.userId(), fixture.project().getId());
        assertThat(scan.batch().segmentationMode()).isEqualTo("MODEL");
        assertThat(changeRepository.findByProjectIdOrderByCreatedAtDesc(fixture.project().getId())).isEmpty();
        assertThat(factRepository.countByProjectId(fixture.project().getId())).isEqualTo(1);
        var fact = factRepository.findByBatchIdOrderByOccurredFromAscCreatedAtAsc(scan.batch().id()).get(0);
        assertThat(fact.getCommitRefs()).isNotEmpty();
        assertThat(factCursorRepository.findByProjectId(fixture.project().getId()).orElseThrow().getLastBatchId())
            .isEqualTo(scan.batch().id());

        var repeatedScan = workSessionScanService.scan(fixture.userId(), fixture.project().getId());
        assertThat(repeatedScan.batch().id()).isEqualTo(scan.batch().id());
        assertThat(factRepository.countByProjectId(fixture.project().getId())).isEqualTo(1);
        var bootstrap = dashboardBootstrapService.load(fixture.userId(), fixture.project().getId());
        assertThat(bootstrap.workSessionScan().batch().id()).isEqualTo(scan.batch().id());
        assertThat(bootstrap.workSessionScan().segments()).hasSize(1);
        assertThat(bootstrap.pendingSedimentReviewCount()).isZero();

        ProjectSediment legacySediment = new ProjectSediment(fixture.project().getId());
        legacySediment.updateCore(
            "PostgreSQL 旧沉淀兼容", "旧沉淀继续参与既有能力分析", "验证 V3.3.x 兼容链路",
            "PROJECT_CAPABILITY", List.of(scan.segments().get(0).id().toString()), fact.getEvidenceRefs()
        );
        legacySediment.recordConfirmation(scan.batch().id(), fact.getAffectedFiles(), "MODEL_RESULT", "PASS");
        legacySediment = sedimentRepository.saveAndFlush(legacySediment);
        assertThat(legacySediment.getCapabilityStatus()).isEqualTo("PENDING_ANALYSIS");

        ProjectAnalysisJob capabilityJob = new ProjectAnalysisJob(
            fixture.project().getId(), fixture.userId(), ProjectAnalysisJobType.CAPABILITY_CARD_ANALYSIS, null
        );
        capabilityJob.configureExecution("capability-fingerprint", "capability-key", 0);
        capabilityJob.markRunning();
        capabilityJob = jobRepository.saveAndFlush(capabilityJob);
        var outcome = capabilityService.analyzeWithOutcome(fixture.userId(), fixture.project().getId(), capabilityJob.getId());
        assertThat(outcome.cards()).hasSize(1);
        var confirmedCard = capabilityService.patch(fixture.userId(), outcome.cards().get(0).id(), CapabilityCardAction.CONFIRM);
        assertThat(confirmedCard.status()).isEqualTo("CONFIRMED");
        capabilityJob = jobRepository.findById(capabilityJob.getId()).orElseThrow();
        capabilityJob.markSucceeded("{\"cardCount\":1}", null);
        jobRepository.saveAndFlush(capabilityJob);

        var reloadedSediment = sedimentRepository.findById(legacySediment.getId()).orElseThrow();
        var reloadedCard = cardRepository.findById(confirmedCard.id()).orElseThrow();
        assertThat(reloadedSediment.getLastCapabilityAnalysisJobId()).isEqualTo(capabilityJob.getId());
        assertThat(reloadedSediment.getCapabilityStatus()).isNotEqualTo("PENDING_ANALYSIS");
        assertThat(reloadedCard.getAnalysisJobId()).isEqualTo(capabilityJob.getId());

        int cardCountBeforeFailure = cardRepository.findByProjectIdOrderByCreatedAtDesc(fixture.project().getId()).size();
        FAIL_NEXT.set(2);
        ProjectAnalysisJob failedCapabilityJob = new ProjectAnalysisJob(
            fixture.project().getId(), fixture.userId(), ProjectAnalysisJobType.CAPABILITY_CARD_ANALYSIS, null
        );
        failedCapabilityJob.markRunning();
        failedCapabilityJob = jobRepository.saveAndFlush(failedCapabilityJob);
        UUID failedCapabilityJobId = failedCapabilityJob.getId();
        assertThatThrownBy(() -> capabilityService.analyzeWithOutcome(fixture.userId(), fixture.project().getId(), failedCapabilityJobId));
        assertThat(cardRepository.findByProjectIdOrderByCreatedAtDesc(fixture.project().getId())).hasSize(cardCountBeforeFailure);
        assertThat(cardRepository.findById(confirmedCard.id())).isPresent();

        ProjectAnalysisJob failedRetrySource = new ProjectAnalysisJob(
            fixture.project().getId(), fixture.userId(), ProjectAnalysisJobType.PROJECT, null
        );
        failedRetrySource.markRunning();
        failedRetrySource.markFailed("可重试失败");
        failedRetrySource = jobRepository.saveAndFlush(failedRetrySource);
        ProjectAnalysisJobRunner runner = mock(ProjectAnalysisJobRunner.class);
        ProjectAnalysisJobService jobService = new ProjectAnalysisJobService(
            jobRepository, projectRepository, runner, objectMapper, transactionManager
        );
        UUID failedRetrySourceId = failedRetrySource.getId();
        var executor = Executors.newFixedThreadPool(10);
        try {
            var futures = java.util.stream.IntStream.range(0, 10)
                .mapToObj(index -> executor.submit(() -> jobService.retry(fixture.userId(), failedRetrySourceId).id()))
                .toList();
            var retryIds = futures.stream().map(future -> {
                try { return future.get(); } catch (Exception exception) { throw new AssertionError(exception); }
            }).distinct().toList();
            assertThat(retryIds).hasSize(1);
            ProjectAnalysisJob retry = jobRepository.findById(retryIds.get(0)).orElseThrow();
            assertThat(retry.getRetriedFromJobId()).isEqualTo(failedRetrySourceId);
            jobService.cancel(fixture.userId(), retry.getId());
            assertThat(jobRepository.findById(retry.getId()).orElseThrow().getStatus()).isEqualTo(ProjectAnalysisJobStatus.CANCELLED);
            assertThat(jobRepository.findById(retry.getId()).orElseThrow().getResultJson()).isNull();
        } finally {
            executor.shutdownNow();
            Files.walk(repository).sorted(java.util.Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        }
    }

    @Test
    void persistsFactNativeCapabilityMapAndConstraintsInRealPostgres() {
        UUID userId = UUID.randomUUID();
        ProjectSpace project = new ProjectSpace(userId);
        project.update("PostgreSQL 能力地图", "验证事实原生关系和约束", ProjectStatus.BUILDING,
            List.of("Spring Boot"), "", LocalDate.now(), null);
        project = projectRepository.saveAndFlush(project);

        ProjectFact fact = new ProjectFact(
            project.getId(), UUID.randomUUID(), null, ProjectFactOrigin.INCREMENTAL_SCAN,
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        );
        Instant occurredAt = Instant.parse("2026-07-16T08:00:00Z");
        fact.updateContent(
            "形成事实原生能力地图", "已把稳定能力、演进和证据关系写入 PostgreSQL。", List.of("保存稳定能力"),
            "支持长期追溯", occurredAt, occurredAt, List.of("postgres-capability-map"), List.of(),
            List.of("backend/src/main/java/com/projectflow/entity/ProjectCapability.java"), List.of(),
            List.of("postgres-it:capability"), "MODEL_RESULT", "PASS", EvidenceConfidence.HIGH,
            ProjectFactRecordStatus.RECORDED, ""
        );
        fact = factRepository.saveAndFlush(fact);

        ProjectCapability capability = new ProjectCapability(
            project.getId(), "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
            "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
        );
        capability.initialize("事实原生能力地图", "以项目事实持续维护能力。", "避免一次性能力卡片割裂历史", "形成稳定可追溯的能力资产",
            List.of("项目理解"), occurredAt, "MODEL", "postgres-fixed", "projectflow-postgres", null);
        capability = capabilityRepository.saveAndFlush(capability);

        ProjectCapabilityEvolution evolution = new ProjectCapabilityEvolution(
            project.getId(), capability.getId(), ProjectCapabilityEvolutionType.NEW_CAPABILITY, 0, 1,
            "形成事实原生能力", "首次形成稳定能力。", occurredAt,
            "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"
        );
        evolution.attachSourceStats(1, 1, List.of("2026-07"), null, "postgres-fixed", "projectflow-postgres");
        evolution = capabilityEvolutionRepository.saveAndFlush(evolution);
        capabilityFactRepository.saveAndFlush(new ProjectCapabilityFact(
            project.getId(), capability.getId(), fact.getId(), ProjectCapabilityRelationRole.FORMATION, evolution.getId()
        ));

        ProjectCapabilityFactCoverage coverage = new ProjectCapabilityFactCoverage(
            project.getId(), fact.getId(), fact.getFactFingerprint(), fact.getUpdatedAt()
        );
        coverage.classify(ProjectCapabilityFactClassification.CONTRIBUTES_TO_CAPABILITY, capability.getId(), evolution.getId(), "形成长期能力");
        capabilityCoverageRepository.saveAndFlush(coverage);
        capability.updateStatistics(1, 1, 1, 1, 0, 1, com.projectflow.entity.ProjectCapabilityMaturity.FORMING,
            "当前由一个批次的事实形成。", occurredAt, occurredAt);
        capabilityRepository.saveAndFlush(capability);
        ProjectCapabilityMapState state = new ProjectCapabilityMapState(project.getId());
        state.complete(1, 1, 1, 0, 0, "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee", occurredAt, null);
        capabilityMapStateRepository.saveAndFlush(state);

        var overview = capabilityQueryService.overview(userId, project.getId());
        var detail = capabilityQueryService.detail(userId, capability.getId());
        assertThat(overview.mapStatus()).isEqualTo("READY");
        assertThat(overview.coveredFactCount()).isEqualTo(1);
        assertThat(detail.factCount()).isEqualTo(1);
        assertThat(detail.evolutionCount()).isEqualTo(1);
        assertThat(capabilityFactRepository.findByProjectIdAndCapabilityId(project.getId(), capability.getId())).hasSize(1);
        assertThat(capabilityCoverageRepository.findByProjectIdAndFactId(project.getId(), fact.getId())).isPresent();
    }

    @Test
    void serializesCheckpointContinuationRecoversExpiredLeaseAndCleansUpProject() throws Exception {
        WorkflowFixture fixture = createFixture("PostgreSQL checkpoint 并发验收");
        UUID projectId = fixture.project().getId();
        String sourceFingerprint = "s".repeat(64);
        String sharedCacheKey = "c".repeat(64);
        int contenders = 8;
        CountDownLatch ready = new CountDownLatch(contenders);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(contenders);
        try {
            var futures = java.util.stream.IntStream.range(0, contenders)
                .mapToObj(index -> executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("checkpoint start timeout");
                    return historyWindowCheckpointService.begin(
                        projectId, "window-0", sharedCacheKey, sourceFingerprint, 32, 120
                    );
                }))
                .toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            var attempts = futures.stream().map(future -> {
                try { return future.get(20, TimeUnit.SECONDS); }
                catch (Exception exception) { throw new AssertionError(exception); }
            }).toList();
            assertThat(attempts).filteredOn(ProjectHistoryWindowCheckpointService.Attempt::claimed).hasSize(1);
            assertThat(historyWindowCheckpointRepository.findByProjectIdAndCacheKey(projectId, sharedCacheKey)).isPresent();

            var winner = attempts.stream().filter(ProjectHistoryWindowCheckpointService.Attempt::claimed)
                .findFirst().orElseThrow();
            String largeValidatedResult = "{\"value\":\"" + "x".repeat(450_000) + "\"}";
            assertThat(historyWindowCheckpointService.succeed(
                winner, largeValidatedResult, 1, "{\"status\":\"SUCCEEDED\"}"
            )).isTrue();
            assertThat(historyWindowCheckpointRepository.findByProjectIdAndCacheKey(projectId, sharedCacheKey)
                .orElseThrow().getValidatedResultJson()).hasSize(largeValidatedResult.length());

            String expiredCacheKey = "e".repeat(64);
            var expiredAttempt = historyWindowCheckpointService.begin(
                projectId, "window-1", expiredCacheKey, sourceFingerprint, 16, 60
            );
            assertThat(expiredAttempt.claimed()).isTrue();
            jdbcTemplate.update(
                "update project_history_window_checkpoints set updated_at = ? where id = ?",
                Timestamp.from(Instant.now().minus(Duration.ofMinutes(6))), expiredAttempt.checkpointId()
            );

            var replacement = historyWindowCheckpointService.begin(
                projectId, "window-1", expiredCacheKey, sourceFingerprint, 16, 60
            );
            assertThat(replacement.claimed()).isTrue();
            assertThat(replacement.version()).isGreaterThan(expiredAttempt.version());
            assertThat(historyWindowCheckpointService.fail(
                expiredAttempt, "stale attempt", "{\"status\":\"FAILED\"}"
            )).isFalse();
            assertThat(historyWindowCheckpointService.succeed(
                replacement, "{\"stories\":[],\"chapters\":[]}", 1, "{\"status\":\"SUCCEEDED\"}"
            )).isTrue();
            assertThat(historyWindowCheckpointService.summarize(
                projectId, List.of(sharedCacheKey, expiredCacheKey)
            ).count("SUCCEEDED")).isEqualTo(2);

            projectService.delete(fixture.userId(), projectId);
            assertThat(historyWindowCheckpointRepository.findByProjectIdOrderByUpdatedAtAsc(projectId)).isEmpty();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void serializesDirtyGenerationsInRealPostgresStartingFromLegacyNull() throws Exception {
        WorkflowFixture fixture = createFixture("PostgreSQL dirty generation 并发验收");
        UUID projectId = fixture.project().getId();
        createHistorySnapshot(fixture.project(), "dirty-generation-source");
        jdbcTemplate.update(
            "update project_history_snapshots set continuity_dirty_generation = null where project_id = ?",
            projectId
        );
        int writeCount = 8;
        CountDownLatch ready = new CountDownLatch(writeCount);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(writeCount);
        try {
            var futures = java.util.stream.IntStream.range(0, writeCount)
                .mapToObj(index -> executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(20, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("dirty generation start timeout");
                    }
                    return continuityDirtyMarker.mark(
                        projectId, "HISTORY_CORRECTION", "correction:same"
                    );
                }))
                .toList();
            assertThat(ready.await(20, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<String> revisions = futures.stream().map(future -> {
                try { return future.get(30, TimeUnit.SECONDS); }
                catch (Exception exception) { throw new AssertionError(exception); }
            }).toList();

            ProjectHistorySnapshot snapshot = historySnapshotRepository.findByProjectId(projectId).orElseThrow();
            assertThat(Set.copyOf(revisions)).hasSize(writeCount);
            assertThat(snapshot.getContinuityDirtyGeneration()).isEqualTo(writeCount);
            assertThat(revisions).contains(snapshot.getContinuityDirtyRevision());
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(20, TimeUnit.SECONDS)).isTrue();
            projectService.delete(fixture.userId(), projectId);
        }
    }

    @Test
    void commitsConcurrentCorrectionConflictAndPagesTwentyFiveHundredRows() throws Exception {
        WorkflowFixture concurrentFixture = createFixture("PostgreSQL correction 并发验收");
        ProjectHistorySnapshot concurrentSnapshot = createHistorySnapshot(concurrentFixture.project(), "concurrent-source");
        String revision = historyCorrectionService.list(
            concurrentFixture.userId(), concurrentFixture.project().getId()
        ).presentationRevision();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            List<String> titles = List.of("并发确认结果 A", "并发确认结果 B");
            var futures = titles.stream().map(title -> executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("correction start timeout");
                try {
                    historyCorrectionService.create(
                        concurrentFixture.userId(), concurrentFixture.project().getId(),
                        new HistoryCorrectionRequest(
                            "RENAME_STORY", "STORY", "story-postgres", List.of(), title, "", "", "",
                            revision, concurrentSnapshot.getSourceEventFingerprint()
                        )
                    );
                    return "SUCCESS";
                } catch (AppException exception) {
                    return exception.getCode();
                }
            })).toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<String> outcomes = futures.stream().map(future -> {
                try { return future.get(20, TimeUnit.SECONDS); }
                catch (Exception exception) { throw new AssertionError(exception); }
            }).toList();
            assertThat(outcomes).containsExactlyInAnyOrder("SUCCESS", "PROJECT_HISTORY_CORRECTION_CONFLICT");
            assertThat(historyCorrectionRepository.countByProjectIdAndStatus(
                concurrentFixture.project().getId(), ProjectHistoryCorrection.Status.ACTIVE
            )).isEqualTo(1);
            assertThat(historyCorrectionRepository.countByProjectIdAndStatus(
                concurrentFixture.project().getId(), ProjectHistoryCorrection.Status.CONFLICT
            )).isEqualTo(1);
            assertThat(historyCorrectionService.resolve(
                concurrentFixture.project().getId(), concurrentSnapshot
            ).stories().get(0).humanTitle()).isIn("并发确认结果 A", "并发确认结果 B");
        } finally {
            executor.shutdownNow();
        }

        WorkflowFixture paginationFixture = createFixture("PostgreSQL correction 分页验收");
        ProjectHistorySnapshot paginationSnapshot = createHistorySnapshot(paginationFixture.project(), "pagination-source");
        List<ProjectHistoryCorrection> corrections = java.util.stream.IntStream.range(0, 2_500)
            .mapToObj(index -> new ProjectHistoryCorrection(
                paginationFixture.project().getId(), paginationFixture.userId(), "RENAME_STORY", "STORY",
                "story-postgres", "[]", "分页标题 " + index, "", "", "", "",
                paginationSnapshot.getSourceEventFingerprint(), "", "", "", ""
            )).toList();
        historyCorrectionRepository.saveAllAndFlush(corrections);

        var firstPage = historyCorrectionService.list(
            paginationFixture.userId(), paginationFixture.project().getId(), 0, 100
        );
        var lastPage = historyCorrectionService.list(
            paginationFixture.userId(), paginationFixture.project().getId(), 24, 100
        );
        var corrected = historyCorrectionService.resolve(paginationFixture.project().getId(), paginationSnapshot);
        assertThat(firstPage.items()).hasSize(100);
        assertThat(lastPage.items()).hasSize(100);
        assertThat(firstPage.total()).isEqualTo(2_500);
        assertThat(lastPage.total()).isEqualTo(2_500);
        assertThat(firstPage.activeCount()).isEqualTo(2_500);
        assertThat(firstPage.activeLimit()).isEqualTo(2_000);
        assertThat(firstPage.activeLimitExceeded()).isTrue();
        assertThat(firstPage.presentationRevision()).isEqualTo(corrected.presentationRevision());
        assertThat(lastPage.items()).allSatisfy(item -> assertThat(item.declaredTitle()).startsWith("分页标题 "));

        projectService.delete(concurrentFixture.userId(), concurrentFixture.project().getId());
        projectService.delete(paginationFixture.userId(), paginationFixture.project().getId());
        assertThat(historyCorrectionRepository.findByProjectIdOrderByCreatedAtAsc(
            paginationFixture.project().getId()
        )).isEmpty();
    }

    private WorkflowFixture createFixture(String name) {
        UUID userId = UUID.randomUUID();
        ProjectSpace project = new ProjectSpace(userId);
        project.update(name, "真实容器测试", ProjectStatus.BUILDING, List.of("Spring Boot"), "", LocalDate.now(), null);
        project = projectRepository.saveAndFlush(project);
        AiProvider provider = new AiProvider(userId);
        String secretRef = providerCredentialStore.writeAndVerify(provider.getId(), "postgres-it-placeholder");
        provider.updateWithSecretRef("固定 PostgreSQL E2E 模型", "http://127.0.0.1:" + modelPort + "/v1", secretRef,
            "projectflow-fixed-postgres", AiProviderType.OPENAI_COMPATIBLE, 0.1, 4000, true,
            List.of("POSTGRES_WORKFLOW_NOT_REAL_DEEPSEEK"));
        providerRepository.saveAndFlush(provider);
        return new WorkflowFixture(userId, project);
    }

    private ProjectHistorySnapshot createHistorySnapshot(ProjectSpace project, String sourceFingerprint) throws Exception {
        Instant occurredAt = Instant.parse("2026-08-01T00:00:00Z");
        UUID eventId = UUID.randomUUID();
        ChangeStory story = new ChangeStory(
            "story-postgres", "report", "形成可阅读报告", "已整理并形成可核对的报告。",
            "材料尚未整理。", "整理项目材料并形成报告。", "报告已经可以阅读和核对。",
            List.of("报告"), "", List.of(), "", List.of(), List.of(), occurredAt, occurredAt,
            1, 1, "FACTUAL_SOURCE", "DETERMINISTIC", "FULL_WITHIN_DISCOVERED_SOURCES", List.of(),
            List.of(eventId), List.of("source:postgres-report")
        );
        HistoryChapter chapter = new HistoryChapter(
            "chapter-postgres", "形成首份报告", "这一阶段整理出首份可阅读报告。", occurredAt, occurredAt,
            List.of("EARLIEST_DISCOVERED_EVENT"), List.of(story.id()), 1, 1,
            "ENGINEERING_GROUPING", "FULL_WITHIN_DISCOVERED_SOURCES", List.of()
        );
        EvolutionThread thread = new EvolutionThread(
            "thread-postgres", "report", "报告", "PROJECT_SUBJECT", List.of(story.id()), List.of("CREATED"),
            "报告已经形成。", List.of(), List.of(), List.of(), 1, null
        );
        ProjectHistorySnapshot snapshot = new ProjectHistorySnapshot(project.getId());
        snapshot.complete(
            "postgres-fixture", sourceFingerprint, 1, occurredAt, occurredAt,
            "project-history-v385-semantic-compression-v1", "project-history-synthesis-v3",
            objectMapper.writeValueAsString(new HistoryOverviewContent(
                "材料尚未整理。", "报告已经形成。", List.of(), List.of(), List.of(), List.of()
            )),
            objectMapper.writeValueAsString(List.of(chapter)), objectMapper.writeValueAsString(List.of(story)),
            objectMapper.writeValueAsString(List.of(thread)),
            objectMapper.writeValueAsString(new HistoryCoverage(
                true, "CURRENT", 1, 1, 0, 0, Map.of("DOCUMENT", 1), List.of(), List.of()
            )),
            "{}", UUID.randomUUID(), false
        );
        return historySnapshotRepository.saveAndFlush(snapshot);
    }

    private Path createGitProject() throws Exception {
        Path root = Files.createDirectories(Path.of("target", "postgres-workflow", UUID.randomUUID().toString()));
        run(root, "git", "init", "-b", "master");
        run(root, "git", "config", "user.email", "postgres-it@example.com");
        run(root, "git", "config", "user.name", "ProjectFlow PostgreSQL IT");
        Path file = root.resolve("src/workflow.txt");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "postgres workflow", StandardCharsets.UTF_8);
        run(root, "git", "add", ".");
        run(root, "git", "commit", "-m", "feat: verify postgres workflow");
        Files.writeString(file, "postgres workflow\nuncommitted evidence", StandardCharsets.UTF_8);
        return root;
    }

    private void run(Path root, String... command) throws Exception {
        Process process = new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.waitFor() != 0) throw new AssertionError(String.join(" ", command) + " failed: " + output);
    }

    private static String capabilityMapContent(String requestBody) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        String prompt = mapper.readTree(requestBody).path("messages").findValuesAsText("content").stream()
            .reduce("", (left, right) -> left + "\n" + right);
        List<String> factIds = jsonArrayAfter(mapper, prompt, "ALLOWED_FACT_IDS_JSON=");
        String capabilityLine = jsonLineAfter(prompt, "EXISTING_CAPABILITIES_JSON=");
        var existingCapabilities = mapper.readTree(capabilityLine);
        Map<String, Object> operation = new java.util.LinkedHashMap<>();
        if (!existingCapabilities.isArray() || existingCapabilities.isEmpty()) {
            operation.put("type", "NEW_CAPABILITY");
            operation.put("temporaryKey", "TMP-POSTGRES-1");
        } else {
            operation.put("type", "ENHANCE_CAPABILITY");
            operation.put("capabilityId", existingCapabilities.get(0).path("capabilityId").asText());
        }
        operation.put("canonicalName", "PostgreSQL 事实原生工作流");
        operation.put("summary", "通过真实 PostgreSQL 持久化能力、演进和事实关系。");
        operation.put("problemSolved", "验证事实原生能力地图的事务和约束");
        operation.put("longTermValue", "保持长期能力资产稳定可追溯");
        operation.put("productAreas", List.of("项目理解"));
        operation.put("factIds", factIds);
        operation.put("evolutionTitle", "形成 PostgreSQL 事实原生能力");
        operation.put("evolutionSummary", "新增事实形成或增强稳定能力。");
        return mapper.writeValueAsString(Map.of(
            "operations", factIds.isEmpty() ? List.of() : List.of(operation),
            "noCapabilityChangeFactIds", List.of(),
            "attentionFacts", List.of()
        ));
    }

    private static String timelineContent(String requestBody) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        String prompt = mapper.readTree(requestBody).path("messages").findValuesAsText("content").stream()
            .reduce("", (left, right) -> left + "\n" + right);
        if (prompt.contains("ALLOWED_MONTH_KEYS_JSON=")) {
            List<String> monthKeys = jsonArrayAfter(mapper, prompt, "ALLOWED_MONTH_KEYS_JSON=");
            return mapper.writeValueAsString(Map.of(
                "periodSummary", "项目已经形成连续且可追溯的历史演进。",
                "stages", List.of(Map.of(
                    "title", "持续演进", "summary", "各月事实共同构成项目的持续演进。", "monthKeys", monthKeys
                )),
                "ungroupedMonthKeys", List.of()
            ));
        }
        List<String> factIds = jsonArrayAfter(mapper, prompt, "ALLOWED_IDS_JSON=");
        return mapper.writeValueAsString(Map.of(
            "periodSummary", "本时间段形成了有证据支撑的项目演进记录。",
            "themes", List.of(Map.of(
                "title", "可追溯的项目变化", "summary", "已记录事实完整反映本时间段的项目变化。", "factIds", factIds
            )),
            "ungroupedFactIds", List.of()
        ));
    }

    private static List<String> jsonArrayAfter(ObjectMapper mapper, String prompt, String marker) throws IOException {
        int start = prompt.indexOf(marker);
        if (start < 0) return List.of();
        String line = prompt.substring(start + marker.length()).split("\\R", 2)[0];
        var values = mapper.readTree(line);
        if (!values.isArray()) return List.of();
        List<String> result = new java.util.ArrayList<>();
        values.forEach(value -> result.add(value.asText()));
        return result;
    }

    private static String jsonLineAfter(String prompt, String marker) {
        int start = prompt.indexOf(marker);
        if (start < 0) return "[]";
        return prompt.substring(start + marker.length()).split("\\R", 2)[0];
    }

    private static void writeResponse(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private record WorkflowFixture(UUID userId, ProjectSpace project) {}
}
