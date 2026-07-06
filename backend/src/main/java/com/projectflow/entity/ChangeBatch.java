package com.projectflow.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.projectflow.support.StringListConverter;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "change_batches")
public class ChangeBatch {
    @Id
    private UUID id;
    @Column(name = "project_id", nullable = false)
    private UUID projectId;
    @Column(name = "scan_started_at", nullable = false)
    private Instant scanStartedAt;
    @Column(name = "scan_finished_at")
    private Instant scanFinishedAt;
    @Column(name = "base_commit_sha", length = 64)
    private String baseCommitSha;
    @Column(name = "head_commit_sha", length = 64)
    private String headCommitSha;
    @Column(name = "branch_name", length = 255)
    private String branchName;
    @Column(name = "new_commit_count", nullable = false)
    private int newCommitCount;
    @Column(name = "changed_file_count", nullable = false)
    private int changedFileCount;
    @Column(name = "agent_result_count", nullable = false)
    private int agentResultCount;
    @Column(name = "segment_count", nullable = false)
    private int segmentCount;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ChangeBatchStatus status;
    @Convert(converter = StringListConverter.class)
    @Column(columnDefinition = "text")
    private List<String> warnings = new ArrayList<>();
    @Column(name = "first_scan", nullable = false)
    private boolean firstScan;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ChangeBatch() {
    }

    public ChangeBatch(UUID projectId, String baseCommitSha, String headCommitSha, String branchName, boolean firstScan) {
        this.id = UUID.randomUUID();
        this.projectId = projectId;
        this.baseCommitSha = baseCommitSha;
        this.headCommitSha = headCommitSha;
        this.branchName = branchName;
        this.firstScan = firstScan;
        this.scanStartedAt = Instant.now();
        this.status = ChangeBatchStatus.PENDING;
        this.createdAt = this.scanStartedAt;
        this.updatedAt = this.scanStartedAt;
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
    void preUpdate() { updatedAt = Instant.now(); }

    public void complete(int commits, int files, int agentResults, List<String> scanWarnings) {
        this.newCommitCount = Math.max(0, commits);
        this.changedFileCount = Math.max(0, files);
        this.agentResultCount = Math.max(0, agentResults);
        this.warnings = scanWarnings == null ? new ArrayList<>() : new ArrayList<>(scanWarnings);
        this.scanFinishedAt = Instant.now();
    }

    public void updateSegmentCount(int count) { this.segmentCount = Math.max(0, count); }
    public void markPartial() { this.status = ChangeBatchStatus.PARTIAL; }
    public void markReviewed() { this.status = ChangeBatchStatus.REVIEWED; }
    public void markFailed(List<String> failureWarnings) {
        this.status = ChangeBatchStatus.FAILED;
        this.warnings = failureWarnings == null ? new ArrayList<>() : new ArrayList<>(failureWarnings);
        this.scanFinishedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getProjectId() { return projectId; }
    public Instant getScanStartedAt() { return scanStartedAt; }
    public Instant getScanFinishedAt() { return scanFinishedAt; }
    public String getBaseCommitSha() { return baseCommitSha; }
    public String getHeadCommitSha() { return headCommitSha; }
    public String getBranchName() { return branchName; }
    public int getNewCommitCount() { return newCommitCount; }
    public int getChangedFileCount() { return changedFileCount; }
    public int getAgentResultCount() { return agentResultCount; }
    public int getSegmentCount() { return segmentCount; }
    public ChangeBatchStatus getStatus() { return status; }
    public List<String> getWarnings() { return List.copyOf(warnings); }
    public boolean isFirstScan() { return firstScan; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
