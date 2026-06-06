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
@Table(name = "project_materials")
public class ProjectMaterial {
    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 60)
    private MaterialSourceType sourceType;

    @Column(name = "file_name", length = 260)
    private String fileName;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "normalized_summary", columnDefinition = "text")
    private String normalizedSummary;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProjectMaterial() {
    }

    public ProjectMaterial(UUID projectId) {
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

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public MaterialSourceType getSourceType() {
        return sourceType;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContent() {
        return content;
    }

    public String getNormalizedSummary() {
        return normalizedSummary;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void update(MaterialSourceType sourceType, String fileName, String content, String normalizedSummary) {
        this.sourceType = sourceType;
        this.fileName = fileName;
        this.content = content;
        this.normalizedSummary = normalizedSummary;
    }
}
