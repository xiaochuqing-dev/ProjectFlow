package com.projectflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.entity.AiProvider;
import com.projectflow.entity.AiProviderType;
import com.projectflow.entity.ProjectAnalysisJob;
import com.projectflow.entity.ProjectAnalysisJobStatus;
import com.projectflow.entity.ProjectAnalysisJobType;
import com.projectflow.entity.ProjectCapabilityCard;
import com.projectflow.entity.ProjectSediment;
import com.projectflow.entity.ProjectSpace;
import com.projectflow.entity.ProjectStatus;
import com.projectflow.repository.AiProviderRepository;
import com.projectflow.repository.ChangeBatchRepository;
import com.projectflow.repository.ProjectAnalysisJobRepository;
import com.projectflow.repository.ProjectCapabilityCardRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.repository.ProjectSedimentRepository;
import com.projectflow.service.ProjectAnalysisJobRunner;
import com.projectflow.service.ProjectAnalysisJobService;

class ProjectFlowH2UpgradeIntegrationTest {
    @Test
    void upgradesV336LikeDatabaseWithoutDeletingOrReclassifyingLegacyData() throws Exception {
        Path root = Files.createTempDirectory("projectflow-v336-upgrade-");
        String url = "jdbc:h2:file:" + root.resolve("projectflow").toAbsolutePath().normalize()
            + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";
        LegacyIds legacy;

        try (ConfigurableApplicationContext oldContext = start(url, "create")) {
            legacy = seedLegacyData(oldContext);
            JdbcTemplate jdbc = oldContext.getBean(JdbcTemplate.class);
            for (String column : List.of(
                "queued_at", "heartbeat_at", "cancellation_requested_at", "cancelled_at",
                "attempt_count", "max_attempts", "request_count", "max_request_count",
                "prompt_tokens", "completion_tokens", "total_tokens", "max_total_tokens",
                "max_duration_ms", "idempotency_key", "input_fingerprint", "failure_code",
                "restart_recovery_state", "queue_position", "version", "retried_from_job_id", "retry_reason"
            )) {
                jdbc.execute("alter table project_analysis_jobs drop column if exists " + column);
            }
            jdbc.execute("alter table project_analysis_jobs alter column status varchar(30)");
        }

        try (ConfigurableApplicationContext upgraded = start(url, "update")) {
            ProjectRepository projects = upgraded.getBean(ProjectRepository.class);
            AiProviderRepository providers = upgraded.getBean(AiProviderRepository.class);
            ProjectAnalysisJobRepository jobs = upgraded.getBean(ProjectAnalysisJobRepository.class);
            ProjectSedimentRepository sediments = upgraded.getBean(ProjectSedimentRepository.class);
            ProjectCapabilityCardRepository cards = upgraded.getBean(ProjectCapabilityCardRepository.class);
            ChangeBatchRepository batches = upgraded.getBean(ChangeBatchRepository.class);

            assertThat(projects.count()).isEqualTo(1);
            assertThat(providers.count()).isEqualTo(1);
            assertThat(sediments.count()).isEqualTo(1);
            assertThat(cards.count()).isEqualTo(2);
            assertThat(cards.findById(legacy.confirmedCardId())).get().extracting(ProjectCapabilityCard::getStatus)
                .isEqualTo(com.projectflow.entity.CapabilityCardStatus.CONFIRMED);
            assertThat(cards.findById(legacy.candidateCardId())).get().extracting(ProjectCapabilityCard::getStatus)
                .isEqualTo(com.projectflow.entity.CapabilityCardStatus.CANDIDATE);
            assertThat(batches.count()).isZero();

            ProjectAnalysisJob failed = jobs.findById(legacy.failedJobId()).orElseThrow();
            ProjectAnalysisJob succeeded = jobs.findById(legacy.succeededJobId()).orElseThrow();
            assertThat(failed.getStatus()).isEqualTo(ProjectAnalysisJobStatus.FAILED);
            assertThat(failed.getAttemptCount()).isZero();
            assertThat(failed.getMaxAttempts()).isEqualTo(2);
            assertThat(failed.getRequestCount()).isZero();
            assertThat(failed.getMaxRequestCount()).isEqualTo(3);
            assertThat(failed.getMaxDurationMs()).isEqualTo(600000L);
            assertThat(failed.getRestartRecoveryState()).isEqualTo("LEGACY");
            assertThat(succeeded.getResultJson()).isEqualTo("{\"legacy\":true}");

            ProjectAnalysisJobService service = new ProjectAnalysisJobService(
                jobs,
                projects,
                mock(ProjectAnalysisJobRunner.class),
                upgraded.getBean(ObjectMapper.class),
                upgraded.getBean(PlatformTransactionManager.class)
            );
            var retry = service.retry(legacy.userId(), legacy.failedJobId());
            assertThat(retry.retriedFromJobId()).isEqualTo(legacy.failedJobId());
            assertThat(retry.status()).isEqualTo(ProjectAnalysisJobStatus.QUEUED);
            var cancelled = service.cancel(legacy.userId(), retry.id());
            assertThat(cancelled.status()).isEqualTo(ProjectAnalysisJobStatus.CANCELLED);
            assertThat(jobs.findById(legacy.succeededJobId()).orElseThrow().getResultJson()).isEqualTo("{\"legacy\":true}");
            assertThat(batches.count()).isZero();
        } finally {
            Files.walk(root).sorted(java.util.Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        }
    }

    private ConfigurableApplicationContext start(String url, String ddlAuto) {
        return new SpringApplicationBuilder(ProjectFlowApplication.class)
            .web(WebApplicationType.NONE)
            .run(
                "--spring.datasource.url=" + url,
                "--spring.datasource.driver-class-name=org.h2.Driver",
                "--spring.datasource.username=sa",
                "--spring.datasource.password=",
                "--spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "--spring.jpa.hibernate.ddl-auto=" + ddlAuto,
                "--spring.jpa.open-in-view=false",
                "--spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",
                "--projectflow.auth.required=false"
            );
    }

    private LegacyIds seedLegacyData(ConfigurableApplicationContext context) {
        UUID userId = UUID.randomUUID();
        ProjectRepository projects = context.getBean(ProjectRepository.class);
        AiProviderRepository providers = context.getBean(AiProviderRepository.class);
        ProjectAnalysisJobRepository jobs = context.getBean(ProjectAnalysisJobRepository.class);
        ProjectSedimentRepository sediments = context.getBean(ProjectSedimentRepository.class);
        ProjectCapabilityCardRepository cards = context.getBean(ProjectCapabilityCardRepository.class);

        ProjectSpace project = new ProjectSpace(userId);
        project.update("V3.3.6 旧库项目", "升级兼容测试", ProjectStatus.BUILDING, List.of("Spring Boot"), "", LocalDate.of(2026, 7, 1), null);
        project = projects.saveAndFlush(project);
        AiProvider provider = new AiProvider(userId);
        provider.update("旧 Provider", "https://example.invalid/v1", "legacy-placeholder", "legacy-model",
            AiProviderType.OPENAI_COMPATIBLE, 0.2, 2000, true, List.of("LEGACY"));
        providers.saveAndFlush(provider);

        ProjectSediment sediment = new ProjectSediment(project.getId());
        sediment.updateCore("旧版已确认沉淀", "旧摘要保持不变", "验证升级不改内容", "PROJECT_CAPABILITY",
            List.of("segment:legacy"), List.of("commit:legacy"));
        sediment.recordConfirmation(UUID.randomUUID(), List.of("src/legacy.txt"), "MODEL_RESULT", "PASS");
        sediment = sediments.saveAndFlush(sediment);

        ProjectCapabilityCard confirmed = card(project.getId(), sediment.getId(), "旧版已确认能力");
        confirmed.confirm();
        confirmed = cards.saveAndFlush(confirmed);
        ProjectCapabilityCard candidate = card(project.getId(), sediment.getId(), "旧版未确认候选");
        candidate = cards.saveAndFlush(candidate);

        ProjectAnalysisJob failed = new ProjectAnalysisJob(project.getId(), userId, ProjectAnalysisJobType.PROJECT, null);
        failed.markRunning();
        failed.markFailed("旧版历史失败");
        failed = jobs.saveAndFlush(failed);
        ProjectAnalysisJob succeeded = new ProjectAnalysisJob(project.getId(), userId, ProjectAnalysisJobType.FILE, "src/legacy.txt");
        succeeded.markSucceeded("{\"legacy\":true}", null);
        succeeded = jobs.saveAndFlush(succeeded);
        return new LegacyIds(userId, failed.getId(), succeeded.getId(), confirmed.getId(), candidate.getId());
    }

    private ProjectCapabilityCard card(UUID projectId, UUID sedimentId, String name) {
        ProjectCapabilityCard card = new ProjectCapabilityCard(projectId);
        card.update(name, "旧版能力摘要", "旧版问题", "旧版入口", List.of("sediment:" + sedimentId),
            List.of("commit:legacy"), "README 表达", "简历表达", "面试表达", "MODEL", "旧 Provider", "", null);
        return card;
    }

    private record LegacyIds(UUID userId, UUID failedJobId, UUID succeededJobId, UUID confirmedCardId, UUID candidateCardId) {}
}
