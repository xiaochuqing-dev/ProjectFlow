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
@Table(name = "project_changes")
public class ProjectChange {
    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "material_id")
    private UUID materialId;

    @Column(name = "linked_suggestion_id")
    private UUID linkedSuggestionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 40)
    private ProjectChangeSourceType sourceType;

    @Column(name = "source_ref", columnDefinition = "text")
    private String sourceRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_kind", nullable = false, length = 40)
    private ProjectChangeKind changeKind;

    @Enumerated(EnumType.STRING)
    @Column(name = "impact_level", nullable = false, length = 40)
    private ProjectChangeImpactLevel impactLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ProjectChangeStatus status;

    @Column(nullable = false, length = 180)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String summary;

    @Column(columnDefinition = "text")
    private String details;

    @Column(name = "affected_files", columnDefinition = "text")
    private String affectedFiles;

    @Column(name = "related_tasks", columnDefinition = "text")
    private String relatedTasks;

    @Column(name = "test_evidence", columnDefinition = "text")
    private String testEvidence;

    @Column(name = "build_evidence", columnDefinition = "text")
    private String buildEvidence;

    @Column(name = "risk_notes", columnDefinition = "text")
    private String riskNotes;

    @Column(name = "decision_notes", columnDefinition = "text")
    private String decisionNotes;

    @Column(name = "learning_notes", columnDefinition = "text")
    private String learningNotes;

    @Column(name = "asset_candidates", columnDefinition = "text")
    private String assetCandidates;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    protected ProjectChange() {
    }

    public ProjectChange(UUID projectId, UUID materialId) {
        this.id = UUID.randomUUID();
        this.projectId = projectId;
        this.materialId = materialId;
        this.status = ProjectChangeStatus.PENDING;
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

    public UUID getMaterialId() {
        return materialId;
    }

    public UUID getLinkedSuggestionId() {
        return linkedSuggestionId;
    }

    public ProjectChangeSourceType getSourceType() {
        return sourceType;
    }

    public String getSourceRef() {
        return sourceRef;
    }

    public ProjectChangeKind getChangeKind() {
        return changeKind;
    }

    public ProjectChangeImpactLevel getImpactLevel() {
        return impactLevel;
    }

    public ProjectChangeStatus getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }

    public String getSummary() {
        return summary;
    }

    public String getDetails() {
        return details;
    }

    public String getAffectedFiles() {
        return affectedFiles;
    }

    public String getRelatedTasks() {
        return relatedTasks;
    }

    public String getTestEvidence() {
        return testEvidence;
    }

    public String getBuildEvidence() {
        return buildEvidence;
    }

    public String getRiskNotes() {
        return riskNotes;
    }

    public String getDecisionNotes() {
        return decisionNotes;
    }

    public String getLearningNotes() {
        return learningNotes;
    }

    public String getAssetCandidates() {
        return assetCandidates;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public void update(
        ProjectChangeSourceType sourceType,
        String sourceRef,
        UUID linkedSuggestionId,
        ProjectChangeKind changeKind,
        ProjectChangeImpactLevel impactLevel,
        String title,
        String summary,
        String details,
        String affectedFiles,
        String relatedTasks,
        String testEvidence,
        String buildEvidence,
        String riskNotes,
        String decisionNotes,
        String learningNotes,
        String assetCandidates
    ) {
        this.sourceType = sourceType;
        this.sourceRef = sourceRef;
        this.linkedSuggestionId = linkedSuggestionId;
        this.changeKind = changeKind;
        this.impactLevel = impactLevel;
        this.title = title;
        this.summary = summary;
        this.details = details;
        this.affectedFiles = affectedFiles;
        this.relatedTasks = relatedTasks;
        this.testEvidence = testEvidence;
        this.buildEvidence = buildEvidence;
        this.riskNotes = riskNotes;
        this.decisionNotes = decisionNotes;
        this.learningNotes = learningNotes;
        this.assetCandidates = assetCandidates;
        if (this.status == ProjectChangeStatus.PENDING) {
            return;
        }
        this.status = ProjectChangeStatus.EDITED;
    }

    public void markAccepted() {
        this.status = ProjectChangeStatus.ACCEPTED;
        this.reviewedAt = Instant.now();
    }

    public void markIgnored() {
        this.status = ProjectChangeStatus.IGNORED;
        this.reviewedAt = Instant.now();
    }
}
