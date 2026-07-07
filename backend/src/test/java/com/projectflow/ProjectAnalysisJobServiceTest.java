package com.projectflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.entity.ProjectAnalysisJob;
import com.projectflow.entity.ProjectAnalysisJobStatus;
import com.projectflow.entity.ProjectAnalysisJobType;
import com.projectflow.repository.ProjectAnalysisJobRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.service.ProjectAnalysisJobRunner;
import com.projectflow.service.ProjectAnalysisJobService;

@ExtendWith(MockitoExtension.class)
class ProjectAnalysisJobServiceTest {
    @Mock
    private ProjectAnalysisJobRepository jobRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectAnalysisJobRunner jobRunner;
    @Mock
    private PlatformTransactionManager transactionManager;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void markInterruptedJobsFailedMarksStaleJobsAndDoesNotReRunThem() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ProjectAnalysisJob queuedJob = new ProjectAnalysisJob(projectId, userId, ProjectAnalysisJobType.WORK_SESSION_SCAN, null);
        ProjectAnalysisJob runningJob = new ProjectAnalysisJob(projectId, userId, ProjectAnalysisJobType.PROJECT, null);
        runningJob.markRunning();
        ProjectAnalysisJob succeededJob = new ProjectAnalysisJob(projectId, userId, ProjectAnalysisJobType.FILE, "a.java");
        succeededJob.markSucceeded("{}", null);
        // markRunning 把 stage 设为 RUNNING，markSucceeded 把 status 设为 SUCCEEDED 并设 stage=SUCCEEDED。
        // queuedJob 仍是 QUEUED/stage=QUEUED，runningJob 是 RUNNING/stage=RUNNING。
        when(jobRepository.findAll()).thenReturn(List.of(queuedJob, runningJob, succeededJob));

        ProjectAnalysisJobService service = new ProjectAnalysisJobService(
            jobRepository, projectRepository, jobRunner, objectMapper, transactionManager
        );

        service.markInterruptedJobsFailed();

        // 残留的 QUEUED/RUNNING 任务被标记为 FAILED。
        assertThat(queuedJob.getStatus()).isEqualTo(ProjectAnalysisJobStatus.FAILED);
        assertThat(queuedJob.getErrorMessage()).contains("服务重启");
        assertThat(runningJob.getStatus()).isEqualTo(ProjectAnalysisJobStatus.FAILED);
        assertThat(runningJob.getErrorMessage()).contains("服务重启");
        // 已完成的任务不受影响。
        assertThat(succeededJob.getStatus()).isEqualTo(ProjectAnalysisJobStatus.SUCCEEDED);
        // 关键：不再自动重跑任何任务——用户没点击就不应触发分析。
        verify(jobRunner, never()).execute(any(UUID.class));
    }
}
