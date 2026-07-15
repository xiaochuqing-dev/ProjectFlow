package com.projectflow.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "project_fact_cursors", uniqueConstraints = @UniqueConstraint(name = "uk_project_fact_cursor_project", columnNames = "project_id"))
public class ProjectFactCursor {
    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "last_recorded_commit_sha", length = 64)
    private String lastRecordedCommitSha;

    @Column(name = "last_recorded_at")
    private Instant lastRecordedAt;

    @Column(name = "branch_name", length = 255)
    private String branchName;

    @Column(name = "last_batch_id")
    private UUID lastBatchId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProjectFactCursor() {
    }

    public ProjectFactCursor(UUID projectId) {
        this.id = UUID.randomUUID();
        this.projectId = projectId;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() { updatedAt = Instant.now(); }

    public void advance(String commitSha, Instant recordedAt, String branchName, UUID batchId) {
        this.lastRecordedCommitSha = safe(commitSha);
        this.lastRecordedAt = recordedAt == null ? Instant.now() : recordedAt;
        this.branchName = safe(branchName);
        this.lastBatchId = batchId;
    }

    public UUID getId() { return id; }
    public UUID getProjectId() { return projectId; }
    public String getLastRecordedCommitSha() { return safe(lastRecordedCommitSha); }
    public Instant getLastRecordedAt() { return lastRecordedAt; }
    public String getBranchName() { return safe(branchName); }
    public UUID getLastBatchId() { return lastBatchId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    private static String safe(String value) { return value == null ? "" : value.trim(); }
}
