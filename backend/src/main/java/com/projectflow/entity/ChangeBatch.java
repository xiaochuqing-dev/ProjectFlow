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
    @Column(name = "scan_fingerprint", length = 64)
    private String scanFingerprint = "";
    @Column(name = "worktree_dirty")
    private boolean worktreeDirty;
    @Column(name = "github_status", length = 40)
    private String githubStatus = "";
    @Column(name = "remote_relation", length = 40)
    private String remoteRelation = "";
    @Column(name = "segmentation_mode", length = 40)
    private String segmentationMode = "";
    @Column(name = "model_status", length = 40)
    private String modelStatus = "";
    @Column(name = "model_provider", length = 160)
    private String modelProvider = "";
    @Column(name = "fallback_reason", columnDefinition = "text")
    private String fallbackReason = "";
    @Column(name = "git_scan_ms")
    private long gitScanMs;
    @Column(name = "model_segment_ms")
    private long modelSegmentMs;
    @Column(name = "github_inspect_ms")
    private long githubInspectMs;
    @Column(name = "total_scan_ms")
    private long totalScanMs;
    // V3.3.3: 分析口径 JSON——记录本次用了哪些来源（本地Git/工作区/GitHub/Agent result/模型）。
    @Column(name = "analysis_scope", columnDefinition = "text")
    private String analysisScope = "";
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
    public void updateTotalScanMs(long value) { this.totalScanMs = Math.max(0, value); }
    public void updateDiagnostics(
        String fingerprint, boolean dirty, String github, String remote, String mode, String model,
        String provider, String fallback, long gitMs, long modelMs, long githubMs, long totalMs
    ) {
        this.scanFingerprint = safe(fingerprint);
        this.worktreeDirty = dirty;
        this.githubStatus = safe(github);
        this.remoteRelation = safe(remote);
        this.segmentationMode = safe(mode);
        this.modelStatus = safe(model);
        this.modelProvider = safe(provider);
        this.fallbackReason = safe(fallback);
        this.gitScanMs = Math.max(0, gitMs);
        this.modelSegmentMs = Math.max(0, modelMs);
        this.githubInspectMs = Math.max(0, githubMs);
        this.totalScanMs = Math.max(0, totalMs);
    }
    // V3.3.3: 记录本次分析口径（哪些来源参与、是否有未提交/未同步内容、证据缺口等）。
    public void recordAnalysisScope(String scope) {
        this.analysisScope = scope == null ? "" : scope;
    }
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
    public String getScanFingerprint() { return scanFingerprint; }
    public boolean isWorktreeDirty() { return worktreeDirty; }
    public String getGithubStatus() { return githubStatus; }
    public String getRemoteRelation() { return remoteRelation; }
    public String getSegmentationMode() { return segmentationMode; }
    public String getModelStatus() { return modelStatus; }
    public String getModelProvider() { return modelProvider; }
    public String getFallbackReason() { return fallbackReason; }
    public long getGitScanMs() { return gitScanMs; }
    public long getModelSegmentMs() { return modelSegmentMs; }
    public long getGithubInspectMs() { return githubInspectMs; }
    public long getTotalScanMs() { return totalScanMs; }
    public String getAnalysisScope() { return analysisScope; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    private static String safe(String value) { return value == null ? "" : value.trim(); }
}
