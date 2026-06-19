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
@Table(name = "project_fact_sources")
public class ProjectFactSource {
    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "field_key", nullable = false, length = 80)
    private String fieldKey;

    @Column(name = "fact_value", nullable = false, columnDefinition = "text")
    private String value;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 40)
    private ProjectFactSourceType sourceType;

    @Column(name = "source_id")
    private UUID sourceId;

    @Column(name = "confidence", nullable = false, length = 40)
    private String confidence;

    @Column(name = "confirmed_by_user", nullable = false)
    private boolean confirmedByUser;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProjectFactSource() {
    }

    public ProjectFactSource(UUID projectId, String fieldKey) {
        this.id = UUID.randomUUID();
        this.projectId = projectId;
        this.fieldKey = fieldKey;
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

    public String getFieldKey() {
        return fieldKey;
    }

    public String getValue() {
        return value;
    }

    public ProjectFactSourceType getSourceType() {
        return sourceType;
    }

    public UUID getSourceId() {
        return sourceId;
    }

    public String getConfidence() {
        return confidence;
    }

    public boolean isConfirmedByUser() {
        return confirmedByUser;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void update(String value, ProjectFactSourceType sourceType, UUID sourceId, String confidence, boolean confirmedByUser) {
        this.value = value;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.confidence = confidence;
        this.confirmedByUser = confirmedByUser;
    }
}
