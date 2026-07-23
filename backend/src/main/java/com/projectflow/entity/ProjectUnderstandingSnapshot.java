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
@Table(name = "project_understanding_snapshots")
public class ProjectUnderstandingSnapshot {
    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false, unique = true)
    private UUID projectId;

    @Column(name = "source_revision", nullable = false, length = 180)
    private String sourceRevision;

    @Column(name = "structure_hash", nullable = false, length = 64)
    private String structureHash;

    @Column(name = "structure_index_version", nullable = false, length = 40)
    private String structureIndexVersion;

    @Column(name = "model_analysis_version", nullable = false, length = 60)
    private String modelAnalysisVersion;

    @Column(name = "current_status", nullable = false, length = 20)
    private String currentStatus;

    @Column(name = "semantic_status", nullable = false, length = 30)
    private String semanticStatus;

    @Column(name = "snapshot_json", nullable = false, columnDefinition = "text")
    private String snapshotJson;

    @Column(name = "analyzed_at", nullable = false)
    private Instant analyzedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProjectUnderstandingSnapshot() {
    }

    public ProjectUnderstandingSnapshot(UUID projectId) {
        this.id = UUID.randomUUID();
        this.projectId = projectId;
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

    public void replace(
        String sourceRevision,
        String structureHash,
        String structureIndexVersion,
        String modelAnalysisVersion,
        String semanticStatus,
        String snapshotJson,
        Instant analyzedAt
    ) {
        this.sourceRevision = sourceRevision;
        this.structureHash = structureHash;
        this.structureIndexVersion = structureIndexVersion;
        this.modelAnalysisVersion = modelAnalysisVersion;
        this.semanticStatus = semanticStatus;
        this.snapshotJson = snapshotJson;
        this.analyzedAt = analyzedAt;
        this.currentStatus = "CURRENT";
    }

    public void markStale() {
        this.currentStatus = "STALE";
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getSourceRevision() {
        return sourceRevision;
    }

    public String getStructureHash() {
        return structureHash;
    }

    public String getStructureIndexVersion() {
        return structureIndexVersion;
    }

    public String getModelAnalysisVersion() {
        return modelAnalysisVersion;
    }

    public String getCurrentStatus() {
        return currentStatus;
    }

    public String getSemanticStatus() {
        return semanticStatus;
    }

    public String getSnapshotJson() {
        return snapshotJson;
    }

    public Instant getAnalyzedAt() {
        return analyzedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
