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
    @Column(name = "job_type", nullable = false, length = 20)
    private ProjectAnalysisJobType jobType;

    @Column(name = "file_path", length = 1000)
    private String filePath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProjectAnalysisJobStatus status;

    @Column(name = "result_json", columnDefinition = "text")
    private String resultJson;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

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

    protected ProjectAnalysisJob() {
    }

    public ProjectAnalysisJob(UUID projectId, UUID userId, ProjectAnalysisJobType jobType, String filePath) {
        this.id = UUID.randomUUID();
        this.projectId = projectId;
        this.userId = userId;
        this.jobType = jobType;
        this.filePath = filePath;
        this.status = ProjectAnalysisJobStatus.QUEUED;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public void markRunning() {
        this.status = ProjectAnalysisJobStatus.RUNNING;
        this.startedAt = Instant.now();
        this.errorMessage = null;
    }

    public void markSucceeded(String resultJson, UUID recordId) {
        this.status = ProjectAnalysisJobStatus.SUCCEEDED;
        this.resultJson = resultJson;
        this.recordId = recordId;
        this.errorMessage = null;
        this.completedAt = Instant.now();
    }

    public void markFailed(String errorMessage) {
        this.status = ProjectAnalysisJobStatus.FAILED;
        this.errorMessage = errorMessage;
        this.completedAt = Instant.now();
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
}
