package com.projectflow.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "project_snapshots")
public class ProjectSnapshot {
    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "current_stage", nullable = false, columnDefinition = "text")
    private String currentStage;

    @Column(name = "task_status_summary", columnDefinition = "text")
    private String taskStatusSummary;

    @Column(name = "tech_stack_summary", columnDefinition = "text")
    private String techStackSummary;

    @Column(name = "module_completion", columnDefinition = "text")
    private String moduleCompletion;

    @Column(name = "risk_summary", columnDefinition = "text")
    private String riskSummary;

    @Column(name = "recent_achievements", columnDefinition = "text")
    private String recentAchievements;

    @Column(name = "next_step_suggestions", columnDefinition = "text")
    private String nextStepSuggestions;

    @Column(name = "memory_version", nullable = false)
    private Integer memoryVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ProjectSnapshot() {
    }

    public ProjectSnapshot(UUID projectId) {
        this.id = UUID.randomUUID();
        this.projectId = projectId;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getCurrentStage() {
        return currentStage;
    }

    public String getTaskStatusSummary() {
        return taskStatusSummary;
    }

    public String getTechStackSummary() {
        return techStackSummary;
    }

    public String getModuleCompletion() {
        return moduleCompletion;
    }

    public String getRiskSummary() {
        return riskSummary;
    }

    public String getRecentAchievements() {
        return recentAchievements;
    }

    public String getNextStepSuggestions() {
        return nextStepSuggestions;
    }

    public Integer getMemoryVersion() {
        return memoryVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void update(
        String currentStage,
        String taskStatusSummary,
        String techStackSummary,
        String moduleCompletion,
        String riskSummary,
        String recentAchievements,
        String nextStepSuggestions,
        Integer memoryVersion
    ) {
        this.currentStage = currentStage;
        this.taskStatusSummary = taskStatusSummary;
        this.techStackSummary = techStackSummary;
        this.moduleCompletion = moduleCompletion;
        this.riskSummary = riskSummary;
        this.recentAchievements = recentAchievements;
        this.nextStepSuggestions = nextStepSuggestions;
        this.memoryVersion = memoryVersion;
    }
}
