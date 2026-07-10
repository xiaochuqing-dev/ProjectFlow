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
        this.updatedAt = now;
        if (this.currentStepStartedAt == null) {
            this.currentStepStartedAt = now;
        }
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public void markRunning() {
        this.status = ProjectAnalysisJobStatus.RUNNING;
        this.startedAt = Instant.now();
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
    }

    public void recordInputSummary(String summary) {
        this.inputSummary = summary;
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
}
