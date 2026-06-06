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
@Table(name = "ai_suggestions")
public class AiSuggestion {
    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "material_id")
    private UUID materialId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 60)
    private AiSuggestionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AiSuggestionStatus status;

    @Column(nullable = false, length = 180)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String reason;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected AiSuggestion() {
    }

    public AiSuggestion(UUID projectId, UUID materialId) {
        this.id = UUID.randomUUID();
        this.projectId = projectId;
        this.materialId = materialId;
        this.status = AiSuggestionStatus.PENDING;
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

    public AiSuggestionType getType() {
        return type;
    }

    public AiSuggestionStatus getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }

    public String getReason() {
        return reason;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void update(AiSuggestionType type, String title, String reason, String payload) {
        this.type = type;
        this.title = title;
        this.reason = reason;
        this.payload = payload;
    }

    public void markApplied() {
        this.status = AiSuggestionStatus.APPLIED;
        this.resolvedAt = Instant.now();
    }

    public void markIgnored() {
        this.status = AiSuggestionStatus.IGNORED;
        this.resolvedAt = Instant.now();
    }
}
