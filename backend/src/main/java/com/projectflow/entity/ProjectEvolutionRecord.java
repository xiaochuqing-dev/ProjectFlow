package com.projectflow.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "project_evolution_records")
public class ProjectEvolutionRecord {
    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "material_id")
    private UUID materialId;

    @Column(nullable = false, columnDefinition = "text")
    private String summary;

    @Column(name = "detected_changes", columnDefinition = "text")
    private String detectedChanges;

    @Column(name = "key_achievements", columnDefinition = "text")
    private String keyAchievements;

    @Column(name = "key_issues", columnDefinition = "text")
    private String keyIssues;

    @Column(name = "technical_decisions", columnDefinition = "text")
    private String technicalDecisions;

    @Column(name = "developer_learnings", columnDefinition = "text")
    private String developerLearnings;

    @Column(name = "next_steps", columnDefinition = "text")
    private String nextSteps;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ProjectEvolutionRecord() {
    }

    public ProjectEvolutionRecord(UUID projectId, UUID materialId) {
        this.id = UUID.randomUUID();
        this.projectId = projectId;
        this.materialId = materialId;
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

    public UUID getMaterialId() {
        return materialId;
    }

    public String getSummary() {
        return summary;
    }

    public String getDetectedChanges() {
        return detectedChanges;
    }

    public String getKeyAchievements() {
        return keyAchievements;
    }

    public String getKeyIssues() {
        return keyIssues;
    }

    public String getTechnicalDecisions() {
        return technicalDecisions;
    }

    public String getDeveloperLearnings() {
        return developerLearnings;
    }

    public String getNextSteps() {
        return nextSteps;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void update(
        String summary,
        String detectedChanges,
        String keyAchievements,
        String keyIssues,
        String technicalDecisions,
        String developerLearnings,
        String nextSteps
    ) {
        this.summary = summary;
        this.detectedChanges = detectedChanges;
        this.keyAchievements = keyAchievements;
        this.keyIssues = keyIssues;
        this.technicalDecisions = technicalDecisions;
        this.developerLearnings = developerLearnings;
        this.nextSteps = nextSteps;
    }
}
