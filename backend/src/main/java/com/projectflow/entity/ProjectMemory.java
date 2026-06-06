package com.projectflow.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "project_memories")
public class ProjectMemory {
    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false, unique = true)
    private UUID projectId;

    @Column(nullable = false, columnDefinition = "text")
    private String positioning;

    @Column(name = "current_stage", nullable = false, columnDefinition = "text")
    private String currentStage;

    @Column(name = "completed_capabilities", columnDefinition = "text")
    private String completedCapabilities;

    @Column(name = "in_progress_capabilities", columnDefinition = "text")
    private String inProgressCapabilities;

    @Column(name = "current_risks", columnDefinition = "text")
    private String currentRisks;

    @Column(name = "technical_decisions", columnDefinition = "text")
    private String technicalDecisions;

    @Column(name = "developer_learnings", columnDefinition = "text")
    private String developerLearnings;

    @Column(name = "showcase_assets", columnDefinition = "text")
    private String showcaseAssets;

    @Column(name = "next_step_suggestions", columnDefinition = "text")
    private String nextStepSuggestions;

    @Column(name = "local_project_path", columnDefinition = "text")
    private String localProjectPath;

    @Column(nullable = false)
    private Integer version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProjectMemory() {
    }

    public ProjectMemory(UUID projectId) {
        this.id = UUID.randomUUID();
        this.projectId = projectId;
        this.version = 1;
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

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getPositioning() {
        return positioning;
    }

    public String getCurrentStage() {
        return currentStage;
    }

    public String getCompletedCapabilities() {
        return completedCapabilities;
    }

    public String getInProgressCapabilities() {
        return inProgressCapabilities;
    }

    public String getCurrentRisks() {
        return currentRisks;
    }

    public String getTechnicalDecisions() {
        return technicalDecisions;
    }

    public String getDeveloperLearnings() {
        return developerLearnings;
    }

    public String getShowcaseAssets() {
        return showcaseAssets;
    }

    public String getNextStepSuggestions() {
        return nextStepSuggestions;
    }

    public String getLocalProjectPath() {
        return localProjectPath;
    }

    public Integer getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void update(
        String positioning,
        String currentStage,
        String completedCapabilities,
        String inProgressCapabilities,
        String currentRisks,
        String technicalDecisions,
        String developerLearnings,
        String showcaseAssets,
        String nextStepSuggestions
    ) {
        this.positioning = positioning;
        this.currentStage = currentStage;
        this.completedCapabilities = completedCapabilities;
        this.inProgressCapabilities = inProgressCapabilities;
        this.currentRisks = currentRisks;
        this.technicalDecisions = technicalDecisions;
        this.developerLearnings = developerLearnings;
        this.showcaseAssets = showcaseAssets;
        this.nextStepSuggestions = nextStepSuggestions;
        this.version = this.version == null ? 1 : this.version + 1;
    }

    public void rememberLocalProjectPath(String localProjectPath) {
        this.localProjectPath = localProjectPath;
        this.version = this.version == null ? 1 : this.version + 1;
    }
}
