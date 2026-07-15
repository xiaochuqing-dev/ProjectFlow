package com.projectflow.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "project_fact_history_states", uniqueConstraints = @UniqueConstraint(name = "uk_project_fact_history_project", columnNames = "project_id"))
public class ProjectFactHistoryState {
    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ProjectFactHistoryStatus status = ProjectFactHistoryStatus.NOT_STARTED;

    @Column(name = "head_snapshot_sha", length = 64)
    private String headSnapshotSha = "";

    @Column(name = "total_commit_count")
    private Integer totalCommitCount = 0;

    @Column(name = "covered_commit_count")
    private Integer coveredCommitCount = 0;

    @Column(name = "remaining_commit_count")
    private Integer remainingCommitCount = 0;

    @Column(name = "last_processed_commit_sha", length = 64)
    private String lastProcessedCommitSha = "";

    @Column(name = "current_chunk")
    private Integer currentChunk = 0;

    @Column(name = "completed_chunk_count")
    private Integer completedChunkCount = 0;

    @Column(name = "last_batch_id")
    private UUID lastBatchId;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "error_code", length = 80)
    private String errorCode = "";

    @Column(name = "error_summary", columnDefinition = "text")
    private String errorSummary = "";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProjectFactHistoryState() {
    }

    public ProjectFactHistoryState(UUID projectId) {
        this.id = UUID.randomUUID();
        this.projectId = projectId;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = now;
        if (status == null) status = ProjectFactHistoryStatus.NOT_STARTED;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() { updatedAt = Instant.now(); }

    public void initialize(String upperBoundSha, int total, int covered, boolean modelConfigured) {
        this.headSnapshotSha = safe(upperBoundSha);
        updateCounts(total, covered);
        if (startedAt == null) startedAt = Instant.now();
        completedAt = null;
        errorCode = "";
        errorSummary = "";
        status = modelConfigured ? ProjectFactHistoryStatus.PAUSED : ProjectFactHistoryStatus.WAITING_FOR_MODEL;
    }

    public void markWaitingForModel(String upperBoundSha) {
        if (upperBoundSha != null && !upperBoundSha.isBlank()) headSnapshotSha = upperBoundSha.trim();
        status = ProjectFactHistoryStatus.WAITING_FOR_MODEL;
        errorCode = "MODEL_NOT_CONFIGURED";
        errorSummary = "配置模型后将自动继续补齐项目历史记忆。";
    }

    public void markRunning(int total, int covered) {
        if (startedAt == null) startedAt = Instant.now();
        updateCounts(total, covered);
        currentChunk = getCompletedChunkCount() + 1;
        status = ProjectFactHistoryStatus.RUNNING;
        errorCode = "";
        errorSummary = "";
    }

    public void recordChunk(String lastCommitSha, UUID batchId, int total, int covered) {
        this.lastProcessedCommitSha = safe(lastCommitSha);
        this.lastBatchId = batchId;
        this.completedChunkCount = getCompletedChunkCount() + 1;
        this.currentChunk = getCompletedChunkCount();
        updateCounts(total, covered);
        status = getRemainingCommitCount() == 0 ? ProjectFactHistoryStatus.COMPLETED : ProjectFactHistoryStatus.PAUSED;
        if (status == ProjectFactHistoryStatus.COMPLETED) completedAt = Instant.now();
    }

    public void markCompleted(int total, int covered) {
        updateCounts(total, covered);
        status = ProjectFactHistoryStatus.COMPLETED;
        completedAt = Instant.now();
        errorCode = "";
        errorSummary = "";
    }

    public void markPaused(String code, String summary) {
        status = ProjectFactHistoryStatus.PAUSED;
        errorCode = safe(code);
        errorSummary = safe(summary);
    }

    public void markFailed(String code, String summary) {
        status = ProjectFactHistoryStatus.FAILED;
        errorCode = safe(code);
        errorSummary = safe(summary);
    }

    private void updateCounts(int total, int covered) {
        totalCommitCount = Math.max(0, total);
        coveredCommitCount = Math.min(totalCommitCount, Math.max(0, covered));
        remainingCommitCount = Math.max(0, totalCommitCount - coveredCommitCount);
    }

    public UUID getId() { return id; }
    public UUID getProjectId() { return projectId; }
    public ProjectFactHistoryStatus getStatus() { return status == null ? ProjectFactHistoryStatus.NOT_STARTED : status; }
    public String getHeadSnapshotSha() { return safe(headSnapshotSha); }
    public int getTotalCommitCount() { return totalCommitCount == null ? 0 : totalCommitCount; }
    public int getCoveredCommitCount() { return coveredCommitCount == null ? 0 : coveredCommitCount; }
    public int getRemainingCommitCount() { return remainingCommitCount == null ? 0 : remainingCommitCount; }
    public String getLastProcessedCommitSha() { return safe(lastProcessedCommitSha); }
    public int getCurrentChunk() { return currentChunk == null ? 0 : currentChunk; }
    public int getCompletedChunkCount() { return completedChunkCount == null ? 0 : completedChunkCount; }
    public UUID getLastBatchId() { return lastBatchId; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public String getErrorCode() { return safe(errorCode); }
    public String getErrorSummary() { return safe(errorSummary); }
    private static String safe(String value) { return value == null ? "" : value.trim(); }
}
