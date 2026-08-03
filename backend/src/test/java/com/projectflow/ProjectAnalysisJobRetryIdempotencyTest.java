package com.projectflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.projectflow.entity.ProjectAnalysisJob;
import com.projectflow.entity.ProjectAnalysisJobStatus;
import com.projectflow.entity.ProjectAnalysisJobType;
import com.projectflow.entity.ProjectSpace;
import com.projectflow.entity.ProjectStatus;
import com.projectflow.repository.ProjectAnalysisJobRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.service.ProjectAnalysisJobRunner;
import com.projectflow.service.ProjectAnalysisJobService;
import com.projectflow.support.AppException;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ProjectAnalysisJobRetryIdempotencyTest {
    @Autowired ProjectAnalysisJobRepository jobRepository;
    @Autowired ProjectRepository projectRepository;
    @Autowired PlatformTransactionManager transactionManager;

    private ProjectAnalysisJobService service;
    private ProjectAnalysisJobRunner jobRunner;

    private UUID userId;
    private ProjectSpace project;

    @BeforeEach
    void setUp() {
        jobRepository.deleteAll();
        projectRepository.deleteAll();
        jobRunner = mock(ProjectAnalysisJobRunner.class);
        service = new ProjectAnalysisJobService(
            jobRepository, projectRepository, jobRunner, new ObjectMapper(), transactionManager
        );
        userId = UUID.randomUUID();
        project = new ProjectSpace(userId);
        project.update("retry 幂等测试", "活动任务不可绕过", ProjectStatus.BUILDING, List.of("Spring Boot"), "", LocalDate.now(), null);
        project = projectRepository.saveAndFlush(project);
    }

    @AfterEach
    void tearDown() {
        jobRepository.deleteAll();
        projectRepository.deleteAll();
    }

    @Test
    void retryReusesEquivalentQueuedRunningAndCancelRequestedJobs() {
        for (ProjectAnalysisJobStatus status : List.of(
            ProjectAnalysisJobStatus.QUEUED,
            ProjectAnalysisJobStatus.RUNNING,
            ProjectAnalysisJobStatus.CANCEL_REQUESTED
        )) {
            jobRepository.deleteAll();
            reset(jobRunner);
            ProjectAnalysisJob failed = failedJob(ProjectAnalysisJobType.PROJECT, null);
            var activeResponse = service.startProjectAnalysis(userId, project.getId());
            ProjectAnalysisJob active = jobRepository.findById(activeResponse.id()).orElseThrow();
            if (status == ProjectAnalysisJobStatus.RUNNING) active.markRunning();
            if (status == ProjectAnalysisJobStatus.CANCEL_REQUESTED) {
                active.markRunning();
                active.requestCancellation();
            }
            jobRepository.saveAndFlush(active);

            long before = jobRepository.count();
            var retried = service.retry(userId, failed.getId());

            assertThat(retried.id()).isEqualTo(active.getId());
            assertThat(jobRepository.count()).isEqualTo(before);
            verify(jobRunner, times(1)).execute(active.getId());
        }
    }

    @Test
    void retryWithoutActiveJobCreatesOneTraceableJob() {
        ProjectAnalysisJob failed = failedJob(ProjectAnalysisJobType.FILE, "src/App.java");

        var retried = service.retry(userId, failed.getId());
        ProjectAnalysisJob created = jobRepository.findById(retried.id()).orElseThrow();

        assertThat(created.getStatus()).isEqualTo(ProjectAnalysisJobStatus.QUEUED);
        assertThat(created.getRetriedFromJobId()).isEqualTo(failed.getId());
        assertThat(created.getRetryReason()).isEqualTo("USER_RETRY");
        assertThat(jobRepository.count()).isEqualTo(2);
        verify(jobRunner).execute(created.getId());
    }

    @Test
    void historyJobIdempotencyKeyFitsThePostgresColumn() {
        var response = service.startProjectFactHistoryRebuild(
            userId,
            project.getId(),
            "0123456789abcdef0123456789abcdef01234567"
        );

        ProjectAnalysisJob created = jobRepository.findById(response.id()).orElseThrow();

        assertThat(created.getIdempotencyKey()).hasSizeLessThanOrEqualTo(128);
        assertThat(created.getIdempotencyKey()).startsWith("PROJECT_FACT_HISTORY_REBUILD:");
    }

    @Test
    void projectHistoryRefreshReusesActiveJobAcrossForceVariants() {
        var ordinary = service.startProjectHistoryRefresh(userId, project.getId(), false);
        var forced = service.startProjectHistoryRefresh(userId, project.getId(), true);

        assertThat(forced.id()).isEqualTo(ordinary.id());
        assertThat(jobRepository.count()).isEqualTo(1);
        verify(jobRunner, times(1)).execute(ordinary.id());
    }

    @Test
    void tenConcurrentRetriesCreateOnlyOneEquivalentActiveJob() throws Exception {
        ProjectAnalysisJob failed = failedJob(ProjectAnalysisJobType.PROJECT, null);
        int attempts = 10;
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(attempts);
        try {
            var futures = java.util.stream.IntStream.range(0, attempts).mapToObj(index -> executor.submit(() -> {
                ready.countDown();
                start.await();
                return service.retry(userId, failed.getId()).id();
            })).toList();
            ready.await();
            start.countDown();
            var ids = futures.stream().map(future -> {
                try {
                    return future.get();
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }
            }).distinct().toList();

            assertThat(ids).hasSize(1);
            assertThat(jobRepository.count()).isEqualTo(2);
            ProjectAnalysisJob created = jobRepository.findById(ids.get(0)).orElseThrow();
            assertThat(created.getRetriedFromJobId()).isEqualTo(failed.getId());
            verify(jobRunner, times(1)).execute(created.getId());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void successfulJobCannotBeRetried() {
        ProjectAnalysisJob succeeded = new ProjectAnalysisJob(project.getId(), userId, ProjectAnalysisJobType.PROJECT, null);
        succeeded.markSucceeded("{}", null);
        succeeded = jobRepository.saveAndFlush(succeeded);
        UUID jobId = succeeded.getId();

        assertThatThrownBy(() -> service.retry(userId, jobId))
            .isInstanceOf(AppException.class)
            .hasMessageContaining("任务已成功");
        assertThat(jobRepository.count()).isEqualTo(1);
        verify(jobRunner, times(0)).execute(jobId);
    }

    @Test
    void differentInputProjectAndTypeDoNotShareActiveJobs() {
        UUID otherUserId = UUID.randomUUID();
        ProjectSpace otherProject = new ProjectSpace(otherUserId);
        otherProject.update("另一个项目", "隔离验证", ProjectStatus.BUILDING, List.of(), "", LocalDate.now(), null);
        otherProject = projectRepository.saveAndFlush(otherProject);

        var projectJob = service.startProjectAnalysis(userId, project.getId());
        var fileA = service.startFileAnalysis(userId, project.getId(), "src/A.java");
        var fileB = service.startFileAnalysis(userId, project.getId(), "src/B.java");
        var otherProjectJob = service.startProjectAnalysis(otherUserId, otherProject.getId());

        assertThat(List.of(projectJob.id(), fileA.id(), fileB.id(), otherProjectJob.id())).doesNotHaveDuplicates();
        assertThat(jobRepository.count()).isEqualTo(4);
    }

    private ProjectAnalysisJob failedJob(ProjectAnalysisJobType type, String input) {
        ProjectAnalysisJob failed = new ProjectAnalysisJob(project.getId(), userId, type, input);
        failed.markRunning();
        failed.markFailed("测试失败");
        return jobRepository.saveAndFlush(failed);
    }
}
