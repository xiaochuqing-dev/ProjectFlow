package com.projectflow.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "project_analysis_jobs")
public class ProjectAnalysisJob {
    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 40)
    private ProjectAnalysisJobType jobType;

    @Column(name = "file_path", length = 1000)
    private String filePath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ProjectAnalysisJobStatus status;

    @Column(name = "result_json", columnDefinition = "text")
    private String resultJson;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "warning_message", columnDefinition = "text")
    private String warningMessage;

    @Column(name = "failure_stage", length = 40)
    private String failureStage;

    @Column(name = "record_id")
    private UUID recordId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "queued_at")
    private Instant queuedAt;

    @Column(name = "heartbeat_at")
    private Instant heartbeatAt;

    @Column(name = "cancellation_requested_at")
    private Instant cancellationRequestedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "attempt_count")
    private Integer attemptCount = 0;

    @Column(name = "max_attempts")
    private Integer maxAttempts = 2;

    @Column(name = "request_count")
    private Integer requestCount = 0;

    @Column(name = "max_request_count")
    private Integer maxRequestCount = 3;

    @Column(name = "prompt_tokens")
    private Integer promptTokens = 0;

    @Column(name = "completion_tokens")
    private Integer completionTokens = 0;

    @Column(name = "total_tokens")
    private Integer totalTokens = 0;

    @Column(name = "max_total_tokens")
    private Integer maxTotalTokens = 60000;

    // 单个任务总体时长策略。Long.MAX_VALUE 表示没有主动 overall deadline；
    // connection/request timeout、重试与取消仍然保持独立有界。
    @Column(name = "max_duration_ms")
    private Long maxDurationMs = 600000L;

    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    @Column(name = "input_fingerprint", length = 128)
    private String inputFingerprint;

    @Column(name = "failure_code", length = 80)
    private String failureCode;

    @Column(name = "restart_recovery_state", length = 40)
    private String restartRecoveryState = "NONE";

    @Column(name = "queue_position")
    private Integer queuePosition = 0;

    @Column(name = "retried_from_job_id")
    private UUID retriedFromJobId;

    @Column(name = "retry_reason", length = 80)
    private String retryReason;

    @Version
    @Column(columnDefinition = "bigint default 0")
    private Long version;

    // V3.3.3: 分析进度可视化。stage 是当前阶段枚举字符串，stageMessage 是面向用户的中文说明。
    @Column(name = "stage", length = 40)
    private String stage = "";

    @Column(name = "stage_message", length = 500)
    private String stageMessage = "";

    @Column(name = "current_step_started_at")
    private Instant currentStepStartedAt;

    // V3.3.3: 输入规模快照（提交/文件/Agent result 数量、GitHub 是否参与、模型是否参与等），JSON 文本。
    @Column(name = "input_summary", columnDefinition = "text")
    private String inputSummary;

    @Column(name = "diagnostics_json", columnDefinition = "text")
    private String diagnosticsJson;

    @Column(name = "model_returned")
    private Boolean modelReturned = false;

    @Column(name = "failure_acknowledged")
    private Boolean failureAcknowledged = false;

    protected ProjectAnalysisJob() {
    }

    public ProjectAnalysisJob(UUID projectId, UUID userId, ProjectAnalysisJobType jobType, String filePath) {
        this.id = UUID.randomUUID();
        this.projectId = projectId;
        this.userId = userId;
        this.jobType = jobType;
        this.filePath = filePath;
        this.status = ProjectAnalysisJobStatus.QUEUED;
        this.stage = "QUEUED";
        this.stageMessage = "等待任务启动";
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.queuedAt = now;
        this.updatedAt = now;
        if (this.currentStepStartedAt == null) {
            this.currentStepStartedAt = now;
        }
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }

    @PostLoad
    void applyLegacyDefaults() {
        if (attemptCount == null) attemptCount = 0;
        if (maxAttempts == null || maxAttempts < 1) maxAttempts = 2;
        if (requestCount == null) requestCount = 0;
        if (maxRequestCount == null || maxRequestCount < 1) maxRequestCount = 3;
        if (promptTokens == null) promptTokens = 0;
        if (completionTokens == null) completionTokens = 0;
        if (totalTokens == null) totalTokens = 0;
        if (maxTotalTokens == null || maxTotalTokens < 1) maxTotalTokens = 60000;
        if (maxDurationMs == null || maxDurationMs < 1) maxDurationMs = 600000L;
        if (restartRecoveryState == null) restartRecoveryState = "LEGACY";
        if (queuePosition == null) queuePosition = 0;
        if (queuedAt == null) queuedAt = createdAt;
    }

    public void markRunning() {
        this.status = ProjectAnalysisJobStatus.RUNNING;
        Instant now = Instant.now();
        if (this.startedAt == null) this.startedAt = now;
        this.heartbeatAt = now;
        this.attemptCount = getAttemptCount() + 1;
        this.errorMessage = null;
        this.warningMessage = null;
        this.failureStage = null;
        this.currentStepStartedAt = Instant.now();
    }

    // V3.3.3: 阶段推进。每完成一个阶段调用一次，前端据此显示"现在在做什么"和已等待时间。
    public void advanceStage(String stage, String message) {
        this.stage = stage == null ? "" : stage.trim();
        this.stageMessage = message == null ? "" : message.trim();
        this.currentStepStartedAt = Instant.now();
        this.heartbeatAt = this.currentStepStartedAt;
    }

    public void heartbeat() {
        if (!isTerminal()) this.heartbeatAt = Instant.now();
    }

    public void configureExecution(String fingerprint, String idempotencyKey, int queuePosition) {
        this.inputFingerprint = fingerprint;
        this.idempotencyKey = idempotencyKey;
        this.queuePosition = Math.max(0, queuePosition);
    }

    public void configureBudgets(int maxRequests, int maxTokens, long maxDurationMs) {
        this.maxRequestCount = Math.max(1, maxRequests);
        this.maxTotalTokens = Math.max(1, maxTokens);
        this.maxDurationMs = Math.max(1_000L, maxDurationMs);
    }

    public void configureRetry(UUID previousJobId, String reason) {
        this.retriedFromJobId = previousJobId;
        this.retryReason = reason == null ? "USER_RETRY" : reason.trim();
    }

    public void requestCancellation() {
        if (isTerminal()) return;
        Instant now = Instant.now();
        this.status = ProjectAnalysisJobStatus.CANCEL_REQUESTED;
        if (this.cancellationRequestedAt == null) this.cancellationRequestedAt = now;
        this.stage = "CANCEL_REQUESTED";
        this.stageMessage = "正在安全停止分析";
        this.currentStepStartedAt = now;
        this.heartbeatAt = now;
    }

    public void markCancelled() {
        Instant now = Instant.now();
        this.status = ProjectAnalysisJobStatus.CANCELLED;
        this.cancelledAt = now;
        this.completedAt = now;
        this.stage = "CANCELLED";
        this.stageMessage = "分析已取消，旧结果不受影响";
        this.currentStepStartedAt = now;
        this.heartbeatAt = now;
    }

    public void markInterrupted(boolean retryable, String message) {
        Instant now = Instant.now();
        this.status = retryable ? ProjectAnalysisJobStatus.RETRYABLE : ProjectAnalysisJobStatus.INTERRUPTED;
        this.failureCode = retryable ? "SAFE_TO_RETRY" : "MODEL_REQUEST_STATE_UNKNOWN";
        this.restartRecoveryState = retryable ? "USER_RETRY_ALLOWED" : "USER_CONFIRMATION_REQUIRED";
        this.errorMessage = message;
        this.completedAt = now;
        this.stage = this.status.name();
        this.stageMessage = message;
        this.currentStepStartedAt = now;
    }

    public void markRejected(String message) {
        markTerminalFailure(ProjectAnalysisJobStatus.REJECTED, "QUEUE_FULL", message, "任务未进入执行队列，未产生模型费用");
    }

    public void markExpired(String code, String message) {
        markTerminalFailure(ProjectAnalysisJobStatus.EXPIRED, code, message, message);
    }

    private void markTerminalFailure(ProjectAnalysisJobStatus target, String code, String message, String stageMessage) {
        Instant now = Instant.now();
        this.status = target;
        this.failureCode = code;
        this.errorMessage = message;
        this.completedAt = now;
        this.stage = target.name();
        this.stageMessage = stageMessage;
        this.currentStepStartedAt = now;
    }

    public void recordModelRequest(int prompt, int completion, int total) {
        this.requestCount = getRequestCount() + 1;
        this.promptTokens = getPromptTokens() + Math.max(0, prompt);
        this.completionTokens = getCompletionTokens() + Math.max(0, completion);
        this.totalTokens = getTotalTokens() + Math.max(0, total);
        this.heartbeatAt = Instant.now();
    }

    public boolean hasDurationBudget(Instant now) {
        if (!com.projectflow.service.AnalysisTimePolicy.hasOverallDeadline(getMaxDurationMs())) return true;
        Instant base = startedAt == null ? (createdAt == null ? now : createdAt) : startedAt;
        return java.time.Duration.between(base, now).toMillis() <= getMaxDurationMs();
    }

    public boolean hasRequestBudget() { return getRequestCount() < getMaxRequestCount(); }

    public boolean hasTokenBudget() { return getTotalTokens() < getMaxTotalTokens(); }

    public boolean isCancellationRequested() { return status == ProjectAnalysisJobStatus.CANCEL_REQUESTED; }

    public boolean isTerminal() {
        return status != ProjectAnalysisJobStatus.QUEUED
            && status != ProjectAnalysisJobStatus.RUNNING
            && status != ProjectAnalysisJobStatus.CANCEL_REQUESTED;
    }

    public void recordInputSummary(String summary) {
        this.inputSummary = summary;
    }

    public void recordDiagnostics(String diagnostics, boolean returned) {
        this.diagnosticsJson = diagnostics;
        this.modelReturned = returned;
    }

    public void acknowledgeFailure() {
        this.failureAcknowledged = true;
    }

    public void markSucceeded(String resultJson, UUID recordId) {
        this.status = ProjectAnalysisJobStatus.SUCCEEDED;
        this.resultJson = resultJson;
        this.recordId = recordId;
        this.errorMessage = null;
        this.warningMessage = null;
        this.failureStage = null;
        this.completedAt = Instant.now();
        this.stage = "SUCCEEDED";
        this.stageMessage = "分析完成";
        this.currentStepStartedAt = this.completedAt;
    }

    public void markSucceededWithWarnings(String resultJson, UUID recordId, String warningMessage) {
        this.status = ProjectAnalysisJobStatus.SUCCEEDED_WITH_WARNINGS;
        this.resultJson = resultJson;
        this.recordId = recordId;
        this.errorMessage = null;
        this.warningMessage = warningMessage == null ? "分析已完成，但部分结果需要复核。" : warningMessage.trim();
        this.failureStage = null;
        this.completedAt = Instant.now();
        this.stage = "SUCCEEDED_WITH_WARNINGS";
        this.stageMessage = this.warningMessage;
        this.currentStepStartedAt = this.completedAt;
    }

    public void markFailed(String errorMessage) {
        this.failureStage = this.stage;
        this.status = ProjectAnalysisJobStatus.FAILED;
        this.errorMessage = errorMessage;
        this.failureCode = "EXECUTION_FAILED";
        this.completedAt = Instant.now();
        this.stage = "FAILED";
        this.stageMessage = "分析失败";
        this.currentStepStartedAt = this.completedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getUserId() {
        return userId;
    }

    public ProjectAnalysisJobType getJobType() {
        return jobType;
    }

    public String getFilePath() {
        return filePath;
    }

    public ProjectAnalysisJobStatus getStatus() {
        return status;
    }

    public String getResultJson() {
        return resultJson;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getWarningMessage() { return warningMessage; }

    public String getFailureStage() { return failureStage; }

    public UUID getRecordId() {
        return recordId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getQueuedAt() { return queuedAt; }

    public Instant getHeartbeatAt() { return heartbeatAt; }

    public Instant getCancellationRequestedAt() { return cancellationRequestedAt; }

    public Instant getCancelledAt() { return cancelledAt; }

    public int getAttemptCount() { return attemptCount == null ? 0 : attemptCount; }

    public int getMaxAttempts() { return maxAttempts == null ? 2 : maxAttempts; }

    public int getRequestCount() { return requestCount == null ? 0 : requestCount; }

    public int getMaxRequestCount() { return maxRequestCount == null ? 3 : maxRequestCount; }

    public int getPromptTokens() { return promptTokens == null ? 0 : promptTokens; }

    public int getCompletionTokens() { return completionTokens == null ? 0 : completionTokens; }

    public int getTotalTokens() { return totalTokens == null ? 0 : totalTokens; }

    public int getMaxTotalTokens() { return maxTotalTokens == null ? 60000 : maxTotalTokens; }

    public long getMaxDurationMs() { return maxDurationMs == null ? 600000L : maxDurationMs; }

    public long getElapsedMs() {
        if (createdAt == null) return 0;
        Instant end = completedAt == null ? Instant.now() : completedAt;
        return Math.max(0, java.time.Duration.between(createdAt, end).toMillis());
    }

    public String getIdempotencyKey() { return idempotencyKey; }

    public String getInputFingerprint() { return inputFingerprint; }

    public String getFailureCode() { return failureCode; }

    public String getRestartRecoveryState() { return restartRecoveryState; }

    public int getQueuePosition() { return queuePosition == null ? 0 : queuePosition; }

    public UUID getRetriedFromJobId() { return retriedFromJobId; }

    public String getRetryReason() { return retryReason; }

    public Long getVersion() { return version; }

    public String getStage() {
        return stage;
    }

    public String getStageMessage() {
        return stageMessage;
    }

    public Instant getCurrentStepStartedAt() {
        return currentStepStartedAt;
    }

    public String getInputSummary() {
        return inputSummary;
    }

    public String getDiagnosticsJson() { return diagnosticsJson; }

    public boolean isModelReturned() { return Boolean.TRUE.equals(modelReturned); }

    public boolean isFailureAcknowledged() { return Boolean.TRUE.equals(failureAcknowledged); }
}
