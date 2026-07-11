package com.projectflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.projectflow.entity.ProjectAnalysisJob;
import com.projectflow.entity.ProjectAnalysisJobType;
import com.projectflow.entity.ProjectSpace;
import com.projectflow.entity.ProjectStatus;
import com.projectflow.repository.ProjectAnalysisJobRepository;
import com.projectflow.repository.ProjectRepository;

import jakarta.persistence.EntityManager;

@DataJpaTest
class ProjectAnalysisJobCompatibilityTest {
    @Autowired ProjectRepository projectRepository;
    @Autowired ProjectAnalysisJobRepository jobRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired EntityManager entityManager;

    @Test
    void loadsLegacyJobWithoutReliabilityFieldsUsingSafeDefaults() {
        UUID userId = UUID.randomUUID();
        ProjectSpace project = new ProjectSpace(userId);
        project.update("旧数据项目", "兼容测试", ProjectStatus.BUILDING, java.util.List.of(), "", LocalDate.now(), null);
        projectRepository.save(project);
        ProjectAnalysisJob job = jobRepository.saveAndFlush(
            new ProjectAnalysisJob(project.getId(), userId, ProjectAnalysisJobType.PROJECT, null)
        );

        jdbcTemplate.update("""
            update project_analysis_jobs
            set attempt_count = null, max_attempts = null, request_count = null,
                max_request_count = null, total_tokens = null, max_total_tokens = null,
                max_duration_ms = null, restart_recovery_state = null
            where id = ?
            """, job.getId());
        entityManager.clear();

        ProjectAnalysisJob loaded = jobRepository.findById(job.getId()).orElseThrow();
        assertThat(loaded.getAttemptCount()).isZero();
        assertThat(loaded.getMaxAttempts()).isEqualTo(2);
        assertThat(loaded.getRequestCount()).isZero();
        assertThat(loaded.getMaxRequestCount()).isEqualTo(3);
        assertThat(loaded.getMaxDurationMs()).isEqualTo(600000L);
        assertThat(loaded.getRestartRecoveryState()).isEqualTo("LEGACY");
    }
}
