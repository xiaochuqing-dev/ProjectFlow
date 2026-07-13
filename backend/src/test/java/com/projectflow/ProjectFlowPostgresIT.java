package com.projectflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.dto.V33WorkflowDtos.CapabilityCardAction;
import com.projectflow.entity.AiProvider;
import com.projectflow.entity.AiProviderType;
import com.projectflow.entity.ProjectAnalysisJob;
import com.projectflow.entity.ProjectAnalysisJobStatus;
import com.projectflow.entity.ProjectAnalysisJobType;
import com.projectflow.entity.ProjectMemory;
import com.projectflow.entity.ProjectSpace;
import com.projectflow.entity.ProjectStatus;
import com.projectflow.entity.SedimentAction;
import com.projectflow.repository.AiProviderRepository;
import com.projectflow.repository.ProjectAnalysisJobRepository;
import com.projectflow.repository.ProjectCapabilityCardRepository;
import com.projectflow.repository.ProjectChangeRepository;
import com.projectflow.repository.ProjectMemoryRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.repository.ProjectSedimentRepository;
import com.projectflow.service.ProjectAnalysisJobRunner;
import com.projectflow.service.ProjectAnalysisJobService;
import com.projectflow.service.ProjectCapabilityService;
import com.projectflow.service.ProjectSedimentService;
import com.projectflow.service.DashboardBootstrapService;
import com.projectflow.service.WorkSessionScanService;
import com.sun.net.httpserver.HttpServer;

@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=update",
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
})
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
    @Autowired ProjectCapabilityCardRepository cardRepository;
    @Autowired WorkSessionScanService workSessionScanService;
    @Autowired ProjectSedimentService sedimentService;
    @Autowired ProjectCapabilityService capabilityService;
    @Autowired DashboardBootstrapService dashboardBootstrapService;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired ObjectMapper objectMapper;

    @BeforeAll
    static void startModelServer() throws IOException {
        modelServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        modelPort = modelServer.getAddress().getPort();
        modelServer.createContext("/v1/chat/completions", exchange -> {
            String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (FAIL_NEXT.getAndUpdate(value -> Math.max(0, value - 1)) > 0) {
                writeResponse(exchange, 503, "{\"error\":{\"message\":\"controlled PostgreSQL workflow failure\"}}");
                return;
            }
            String content = requestBody.contains("capabilities") || requestBody.contains("项目能力")
                ? "{\"capabilities\":[{\"name\":\"PostgreSQL 工作流可靠性\",\"summary\":\"真实数据库下完成沉淀与能力闭环。\",\"problemSolved\":\"验证事务和关系持久化。\",\"featureEntry\":\"能力与成果\",\"sourceIndexes\":[\"S1\"],\"readme\":\"真实 PostgreSQL 16 工作流验证。\",\"resume\":\"完成项目沉淀到能力卡片闭环。\",\"interview\":\"可说明事务、幂等和失败保留。\"}]}"
                : "{\"segments\":[{\"segmentTitle\":\"完成 PostgreSQL 工作流验证\",\"plainSummary\":\"将固定 Git 证据整理为可确认开发推进段。\",\"sourceIndexes\":[\"S1\"],\"mainChanges\":[\"读取提交证据\",\"生成分析批次\",\"形成正式建议\"],\"userVisibleValue\":\"验证真实数据库业务闭环。\",\"affectedFiles\":[],\"confidence\":\"HIGH\",\"needsUserReview\":true}]}";
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
        assertThat(changeRepository.findByProjectIdOrderByCreatedAtDesc(fixture.project().getId())).hasSize(1);
        var bootstrap = dashboardBootstrapService.load(fixture.userId(), fixture.project().getId());
        assertThat(bootstrap.workSessionScan().batch().id()).isEqualTo(scan.batch().id());
        assertThat(bootstrap.workSessionScan().segments()).hasSize(1);
        assertThat(bootstrap.pendingSedimentReviewCount()).isEqualTo(1);
        var change = changeRepository.findByProjectIdOrderByCreatedAtDesc(fixture.project().getId()).get(0);
        var confirmation = sedimentService.confirm(fixture.userId(), change.getId(), SedimentAction.NEW_SEDIMENT, null);
        assertThat(confirmation.sediment().capabilityStatus()).isEqualTo("PENDING_ANALYSIS");

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

        var reloadedSediment = sedimentRepository.findById(confirmation.sediment().id()).orElseThrow();
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

    private WorkflowFixture createFixture(String name) {
        UUID userId = UUID.randomUUID();
        ProjectSpace project = new ProjectSpace(userId);
        project.update(name, "真实容器测试", ProjectStatus.BUILDING, List.of("Spring Boot"), "", LocalDate.now(), null);
        project = projectRepository.saveAndFlush(project);
        AiProvider provider = new AiProvider(userId);
        provider.update("固定 PostgreSQL E2E 模型", "http://127.0.0.1:" + modelPort + "/v1", "postgres-it-placeholder",
            "projectflow-fixed-postgres", AiProviderType.OPENAI_COMPATIBLE, 0.1, 4000, true, List.of("POSTGRES_WORKFLOW_NOT_REAL_DEEPSEEK"));
        providerRepository.saveAndFlush(provider);
        return new WorkflowFixture(userId, project);
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

    private static void writeResponse(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private record WorkflowFixture(UUID userId, ProjectSpace project) {}
}
