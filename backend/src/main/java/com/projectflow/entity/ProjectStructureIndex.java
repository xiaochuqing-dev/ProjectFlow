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
@Table(name = "project_structure_indexes")
public class ProjectStructureIndex {
    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false, unique = true)
    private UUID projectId;

    @Column(name = "source_revision", nullable = false, length = 180)
    private String sourceRevision;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "index_version", nullable = false, length = 40)
    private String indexVersion;

    @Column(name = "indexer_source", nullable = false, length = 80)
    private String indexerSource;

    @Column(name = "intake_json", nullable = false, columnDefinition = "text")
    private String intakeJson;

    @Column(name = "index_json", nullable = false, columnDefinition = "text")
    private String indexJson;

    // Nullable for idempotent upgrade from V3.4.x/V3.5 pre-release databases.
    @Column(name = "inventory_json", columnDefinition = "text")
    private String inventoryJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProjectStructureIndex() {
    }

    public ProjectStructureIndex(UUID projectId) {
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
        String contentHash,
        String indexVersion,
        String indexerSource,
        String intakeJson,
        String indexJson,
        String inventoryJson
    ) {
        this.sourceRevision = sourceRevision;
        this.contentHash = contentHash;
        this.indexVersion = indexVersion;
        this.indexerSource = indexerSource;
        this.intakeJson = intakeJson;
        this.indexJson = indexJson;
        this.inventoryJson = inventoryJson;
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

    public String getContentHash() {
        return contentHash;
    }

    public String getIndexVersion() {
        return indexVersion;
    }

    public String getIndexerSource() {
        return indexerSource;
    }

    public String getIntakeJson() {
        return intakeJson;
    }

    public String getIndexJson() {
        return indexJson;
    }

    public String getInventoryJson() {
        return inventoryJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
