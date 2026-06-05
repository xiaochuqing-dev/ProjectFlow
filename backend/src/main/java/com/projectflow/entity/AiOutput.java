package com.projectflow.entity;

import java.time.Instant;
import java.time.LocalDate;
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
@Table(name = "ai_outputs")
public class AiOutput {
    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AiOutputType type;

    @Column(nullable = false, length = 180)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "from_date")
    private LocalDate fromDate;

    @Column(name = "to_date")
    private LocalDate toDate;

    @Column(nullable = false, length = 60)
    private String provider;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AiOutput() {
    }

    public AiOutput(UUID projectId) {
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

    public AiOutputType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public LocalDate getFromDate() {
        return fromDate;
    }

    public LocalDate getToDate() {
        return toDate;
    }

    public String getProvider() {
        return provider;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void update(AiOutputType type, String title, String content, LocalDate fromDate, LocalDate toDate, String provider) {
        this.type = type;
        this.title = title;
        this.content = content;
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.provider = provider;
    }
}
