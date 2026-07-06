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
@Table(name = "project_review_cursors", uniqueConstraints = @UniqueConstraint(columnNames = "project_id"))
public class ProjectReviewCursor {
    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "last_reviewed_commit_sha", length = 64)
    private String lastReviewedCommitSha;

    @Column(name = "last_reviewed_at")
    private Instant lastReviewedAt;

    @Column(name = "last_reviewed_branch", length = 255)
    private String lastReviewedBranch;

    @Column(name = "last_reviewed_remote_sha", length = 64)
    private String lastReviewedRemoteSha;

    @Column(name = "last_snapshot_id")
    private UUID lastSnapshotId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProjectReviewCursor() {
    }

    public ProjectReviewCursor(UUID projectId) {
        this.id = UUID.randomUUID();
        this.projectId = projectId;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public void advance(String commitSha, Instant reviewedAt, String branch, String remoteSha, UUID snapshotId) {
        this.lastReviewedCommitSha = commitSha;
        this.lastReviewedAt = reviewedAt;
        this.lastReviewedBranch = branch;
        this.lastReviewedRemoteSha = remoteSha;
        this.lastSnapshotId = snapshotId;
    }

    public UUID getId() { return id; }
    public UUID getProjectId() { return projectId; }
    public String getLastReviewedCommitSha() { return lastReviewedCommitSha; }
    public Instant getLastReviewedAt() { return lastReviewedAt; }
    public String getLastReviewedBranch() { return lastReviewedBranch; }
    public String getLastReviewedRemoteSha() { return lastReviewedRemoteSha; }
    public UUID getLastSnapshotId() { return lastSnapshotId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
