package com.projectflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
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
import com.projectflow.entity.ProjectSpace;
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
    void listsRunningJobsWhenCompletedCapabilitySummaryExists() {
        UUID userId = UUID.randomUUID();
        ProjectSpace project = new ProjectSpace(userId);
        UUID projectId = project.getId();
        ProjectAnalysisJob runningJob = new ProjectAnalysisJob(projectId, userId, ProjectAnalysisJobType.PROJECT, null);
        runningJob.markRunning();
        ProjectAnalysisJob completedCapabilityJob = new ProjectAnalysisJob(
            projectId,
            userId,
            ProjectAnalysisJobType.CAPABILITY_CARD_ANALYSIS,
            null
        );
        completedCapabilityJob.markSucceeded("{\"cardCount\":2,\"mode\":\"MODEL\"}", null);
        when(projectRepository.findByIdAndUserId(projectId, userId)).thenReturn(Optional.of(project));
        when(jobRepository.findByProjectIdOrderByCreatedAtDesc(projectId))
            .thenReturn(List.of(runningJob, completedCapabilityJob));

        ProjectAnalysisJobService service = new ProjectAnalysisJobService(
            jobRepository, projectRepository, jobRunner, objectMapper, transactionManager
        );

        var jobs = service.listProjectJobs(userId, projectId);

        assertThat(jobs).hasSize(2);
        assertThat(jobs.get(0).status()).isEqualTo(ProjectAnalysisJobStatus.RUNNING);
        assertThat(jobs.get(1).jobType()).isEqualTo(ProjectAnalysisJobType.CAPABILITY_CARD_ANALYSIS);
    }

    @Test
    void listsLegacyFileJobsWithNullableCollectionFields() {
        UUID userId = UUID.randomUUID();
        ProjectSpace project = new ProjectSpace(userId);
        UUID projectId = project.getId();
        ProjectAnalysisJob legacy = new ProjectAnalysisJob(projectId, userId, ProjectAnalysisJobType.FILE, "legacy.java");
        legacy.markSucceeded("{\"path\":\"legacy.java\",\"role\":\"legacy\",\"summary\":\"kept\",\"evidence\":null,\"relatedFiles\":null,\"limitations\":null}", null);
        when(projectRepository.findByIdAndUserId(projectId, userId)).thenReturn(Optional.of(project));
        when(jobRepository.findByProjectIdOrderByCreatedAtDesc(projectId)).thenReturn(List.of(legacy));
        ProjectAnalysisJobService service = new ProjectAnalysisJobService(
            jobRepository, projectRepository, jobRunner, objectMapper, transactionManager
        );

        var jobs = service.listProjectJobs(userId, projectId);

        assertThat(jobs).hasSize(1);
        assertThat(jobs.get(0).fileResult().path()).isEqualTo("legacy.java");
    }

    @Test
    void recoverInterruptedJobsRequeuesQueuedAndMarksSafeRunningJobRetryable() {
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
        when(jobRepository.findById(queuedJob.getId())).thenReturn(Optional.of(queuedJob));

        ProjectAnalysisJobService service = new ProjectAnalysisJobService(
            jobRepository, projectRepository, jobRunner, objectMapper, transactionManager
        );

        service.recoverInterruptedJobs();

        assertThat(queuedJob.getStatus()).isEqualTo(ProjectAnalysisJobStatus.QUEUED);
        verify(jobRunner).execute(queuedJob.getId());
        assertThat(runningJob.getStatus()).isEqualTo(ProjectAnalysisJobStatus.RETRYABLE);
        assertThat(runningJob.getErrorMessage()).contains("服务重启");
        assertThat(succeededJob.getStatus()).isEqualTo(ProjectAnalysisJobStatus.SUCCEEDED);
        verify(jobRunner, never()).execute(runningJob.getId());
    }

    @Test
    void acknowledgesFailedCapabilityJobWithoutChangingItsResultHistory() {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        ProjectAnalysisJob failed = new ProjectAnalysisJob(projectId, userId, ProjectAnalysisJobType.CAPABILITY_CARD_ANALYSIS, null);
        failed.recordDiagnostics("{\"finishReason\":\"length\"}", true);
        failed.markFailed("模型输出达到长度上限");
        when(jobRepository.findById(failed.getId())).thenReturn(Optional.of(failed));
        when(jobRepository.save(failed)).thenReturn(failed);
        ProjectAnalysisJobService service = new ProjectAnalysisJobService(
            jobRepository, projectRepository, jobRunner, objectMapper, transactionManager
        );

        var response = service.acknowledgeFailure(userId, failed.getId());

        assertThat(response.failureAcknowledged()).isTrue();
        assertThat(response.modelReturned()).isTrue();
        assertThat(response.diagnosticsJson()).contains("length");
        assertThat(failed.getStatus()).isEqualTo(ProjectAnalysisJobStatus.FAILED);
    }
}
