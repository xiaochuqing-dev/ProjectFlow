package com.projectflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

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
import com.projectflow.repository.ProjectAnalysisJobRepository;
import com.projectflow.repository.ProjectCapabilityCardRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.repository.ProjectSedimentRepository;

@Testcontainers
@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=update",
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
})
class ProjectFlowPostgresIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("projectflow_test")
        .withUsername("projectflow")
        .withPassword("projectflow_test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired ProjectRepository projectRepository;
    @Autowired AiProviderRepository providerRepository;
    @Autowired ProjectAnalysisJobRepository jobRepository;
    @Autowired ProjectSedimentRepository sedimentRepository;
    @Autowired ProjectCapabilityCardRepository cardRepository;

    @Test
    @Transactional
    void persistsCoreWorkflowAndCancellationStateInRealPostgres() {
        UUID userId = UUID.randomUUID();
        ProjectSpace project = new ProjectSpace(userId);
        project.update("PostgreSQL 验收项目", "真实容器测试", ProjectStatus.BUILDING, List.of("Spring Boot"), "", LocalDate.now(), null);
        projectRepository.save(project);

        AiProvider provider = new AiProvider(userId);
        provider.update("集成测试模型", "https://example.invalid", "encrypted-placeholder", "test-model",
            AiProviderType.MOCK, 0.1, 1000, true, List.of("ANALYSIS"));
        providerRepository.save(provider);

        ProjectSediment sediment = new ProjectSediment(project.getId());
        sediment.updateCore("已确认沉淀", "验证 PostgreSQL 持久化", "防止升级丢数据", "PROJECT_CAPABILITY",
            List.of("segment:1"), List.of("commit:test"));
        sedimentRepository.save(sediment);

        ProjectCapabilityCard card = new ProjectCapabilityCard(project.getId());
        card.update("任务可靠性", "可取消和恢复", "避免重复计费", "分析入口", List.of("sediment:" + sediment.getId()),
            List.of("commit:test"), "说明", "成果", "复盘", "MODEL", "集成测试模型", "", null);
        card.confirm();
        cardRepository.save(card);

        ProjectAnalysisJob job = new ProjectAnalysisJob(project.getId(), userId, ProjectAnalysisJobType.PROJECT, null);
        job.configureExecution("fingerprint", "idempotency-key", 0);
        jobRepository.saveAndFlush(job);
        job.markRunning();
        job.requestCancellation();
        job.markCancelled();
        jobRepository.saveAndFlush(job);

        ProjectAnalysisJob loaded = jobRepository.findById(job.getId()).orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(ProjectAnalysisJobStatus.CANCELLED);
        assertThat(loaded.getRequestCount()).isZero();
        assertThat(projectRepository.count()).isPositive();
        assertThat(providerRepository.count()).isPositive();
        assertThat(sedimentRepository.count()).isPositive();
        assertThat(cardRepository.count()).isPositive();
    }
}
