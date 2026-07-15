package com.projectflow.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    name = "project_fact_commit_refs",
    uniqueConstraints = @UniqueConstraint(name = "uk_project_fact_commit", columnNames = {"fact_id", "commit_sha"}),
    indexes = @Index(name = "idx_project_fact_commit_project_sha", columnList = "project_id,commit_sha")
)
public class ProjectFactCommitRef {
    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "fact_id", nullable = false)
    private UUID factId;

    @Column(name = "commit_sha", nullable = false, length = 64)
    private String commitSha;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ProjectFactCommitRef() {
    }

    public ProjectFactCommitRef(UUID projectId, UUID factId, String commitSha) {
        this.id = UUID.randomUUID();
        this.projectId = projectId;
        this.factId = factId;
        this.commitSha = commitSha == null ? "" : commitSha.trim();
        this.createdAt = Instant.now();
    }

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getProjectId() { return projectId; }
    public UUID getFactId() { return factId; }
    public String getCommitSha() { return commitSha == null ? "" : commitSha; }
    public Instant getCreatedAt() { return createdAt; }
}
