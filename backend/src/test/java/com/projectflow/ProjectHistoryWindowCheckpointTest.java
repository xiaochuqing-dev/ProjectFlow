package com.projectflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.projectflow.entity.ProjectHistoryWindowCheckpoint;

class ProjectHistoryWindowCheckpointTest {
    @Test
    void retainsFailureAuditAndClearsItWhenRetryBegins() {
        ProjectHistoryWindowCheckpoint checkpoint = checkpoint();

        checkpoint.fail("模型返回格式无效", "{\"status\":\"FAILED\"}");
        assertThat(checkpoint.getStatus()).isEqualTo("FAILED");
        assertThat(checkpoint.getLastError()).isEqualTo("模型返回格式无效");
        assertThat(checkpoint.getDiagnosticsJson()).contains("FAILED");

        checkpoint.beginAttempt();
        assertThat(checkpoint.getStatus()).isEqualTo("RUNNING");
        assertThat(checkpoint.getLastError()).isEmpty();
    }

    @Test
    void recordsValidatedSuccessAndDistinctCancellationAndSkipStates() {
        ProjectHistoryWindowCheckpoint success = checkpoint();
        success.storeValidatedResult("{\"stories\":[],\"chapters\":[]}");
        success.succeed(1, "{\"finishReason\":\"stop\"}");
        assertThat(success.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(success.getRequestCount()).isEqualTo(1);
        assertThat(success.getValidatedResultJson()).contains("stories");
        assertThat(success.getLastError()).isEmpty();

        ProjectHistoryWindowCheckpoint cancelled = checkpoint();
        cancelled.cancel("窗口语义归纳已取消", "{\"status\":\"CANCELLED\"}");
        assertThat(cancelled.getStatus()).isEqualTo("CANCELLED");
        assertThat(cancelled.getLastError()).contains("取消");

        ProjectHistoryWindowCheckpoint skipped = checkpoint();
        skipped.skip("Prompt 容量不足", "{\"status\":\"SKIPPED\"}");
        assertThat(skipped.getStatus()).isEqualTo("SKIPPED");
        assertThat(skipped.getLastError()).contains("Prompt");

        ProjectHistoryWindowCheckpoint oversized = checkpoint();
        oversized.skipOversize("单个变化故事超过安全 Prompt 记录上限",
            "{\"status\":\"SKIPPED_OVERSIZE\",\"terminal\":true}");
        assertThat(oversized.getStatus()).isEqualTo("SKIPPED_OVERSIZE");
        assertThat(oversized.getDiagnosticsJson()).contains("\"terminal\":true");
        assertThat(oversized.getLastError()).contains("超过安全 Prompt");
    }

    @Test
    void boundsOversizedValidatedPayloadInsteadOfPersistingUnboundedJson() {
        ProjectHistoryWindowCheckpoint checkpoint = checkpoint();
        checkpoint.storeValidatedResult("x".repeat(500_001));

        assertThat(checkpoint.getValidatedResultJson()).isEqualTo("{}");
    }

    private ProjectHistoryWindowCheckpoint checkpoint() {
        return new ProjectHistoryWindowCheckpoint(
            UUID.randomUUID(), "window-0", "cache-key", "source-fingerprint", 2, 4
        );
    }
}
