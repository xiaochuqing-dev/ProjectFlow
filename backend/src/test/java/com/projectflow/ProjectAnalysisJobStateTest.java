package com.projectflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.projectflow.entity.ProjectAnalysisJob;
import com.projectflow.entity.ProjectAnalysisJobStatus;
import com.projectflow.entity.ProjectAnalysisJobType;

class ProjectAnalysisJobStateTest {
    @Test
    void cancellationIsIdempotentAndStopsAtTerminalState() {
        ProjectAnalysisJob job = new ProjectAnalysisJob(
            UUID.randomUUID(), UUID.randomUUID(), ProjectAnalysisJobType.PROJECT, null
        );
        job.markRunning();
        job.requestCancellation();
        Instant firstRequest = job.getCancellationRequestedAt();
        job.requestCancellation();
        job.markCancelled();
        job.requestCancellation();

        assertThat(job.getCancellationRequestedAt()).isEqualTo(firstRequest);
        assertThat(job.getStatus()).isEqualTo(ProjectAnalysisJobStatus.CANCELLED);
        assertThat(job.getRequestCount()).isZero();
    }

    @Test
    void requestAndTokenBudgetsAreAccountedAcrossRetries() {
        ProjectAnalysisJob job = new ProjectAnalysisJob(
            UUID.randomUUID(), UUID.randomUUID(), ProjectAnalysisJobType.FILE, "src/App.java"
        );
        job.recordModelRequest(100, 50, 150);
        job.recordModelRequest(80, 20, 100);
        job.recordModelRequest(70, 30, 100);

        assertThat(job.getRequestCount()).isEqualTo(3);
        assertThat(job.getTotalTokens()).isEqualTo(350);
        assertThat(job.hasRequestBudget()).isFalse();
        assertThat(job.hasTokenBudget()).isTrue();
    }
}
