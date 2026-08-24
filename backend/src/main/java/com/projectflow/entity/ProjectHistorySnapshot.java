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
import jakarta.persistence.Version;

/** Replaceable level 0-3 project-history read model. */
@Entity
@Table(name = "project_history_snapshots")
public class ProjectHistorySnapshot {
    public enum Status {
        NOT_INITIALIZED,
        RUNNING,
        READY,
        DEGRADED,
        STALE,
        FAILED
    }

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false, unique = true)
    private UUID projectId;

    @Column(name = "project_revision", nullable = false, length = 180)
    private String projectRevision;

    @Column(name = "source_event_fingerprint", nullable = false, length = 64)
    private String sourceEventFingerprint;

    @Column(name = "source_event_count", nullable = false)
    private Integer sourceEventCount;

    @Column(name = "earliest_event_at")
    private Instant earliestEventAt;

    @Column(name = "latest_event_at")
    private Instant latestEventAt;

    @Column(name = "strategy_version", nullable = false, length = 60)
    private String strategyVersion;

    @Column(name = "prompt_version", nullable = false, length = 60)
    private String promptVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Status status;

    @Column(name = "overview_json", nullable = false, columnDefinition = "text")
    private String overviewJson;

    @Column(name = "chapters_json", nullable = false, columnDefinition = "text")
    private String chaptersJson;

    @Column(name = "stories_json", nullable = false, columnDefinition = "text")
    private String storiesJson;

    @Column(name = "threads_json", nullable = false, columnDefinition = "text")
    private String threadsJson;

    @Column(name = "coverage_json", nullable = false, columnDefinition = "text")
    private String coverageJson;

    @Column(name = "diagnostics_json", nullable = false, columnDefinition = "text")
    private String diagnosticsJson;

    @Column(name = "analysis_job_id")
    private UUID analysisJobId;

    @Column(name = "generated_at")
    private Instant generatedAt;

    @Column(name = "latest_successful_at")
    private Instant latestSuccessfulAt;

    @Column(name = "continuity_dirty_revision", length = 96)
    private String continuityDirtyRevision;

    @Column(name = "continuity_dirty_reason", length = 80)
    private String continuityDirtyReason;

    @Column(name = "continuity_dirty_at")
    private Instant continuityDirtyAt;

    @Column(name = "continuity_dirty_generation")
    private Long continuityDirtyGeneration;

    @Column(name = "error_code", length = 80)
    private String errorCode;

    @Column(name = "error_summary", columnDefinition = "text")
    private String errorSummary;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected ProjectHistorySnapshot() {
    }

    public ProjectHistorySnapshot(UUID projectId) {
        this.id = UUID.randomUUID();
        this.projectId = projectId;
        this.projectRevision = "";
        this.sourceEventFingerprint = "";
        this.sourceEventCount = 0;
        this.strategyVersion = "";
        this.promptVersion = "";
        this.status = Status.NOT_INITIALIZED;
        this.overviewJson = "{}";
        this.chaptersJson = "[]";
        this.storiesJson = "[]";
        this.threadsJson = "[]";
        this.coverageJson = "{}";
        this.diagnosticsJson = "{}";
        this.continuityDirtyGeneration = 0L;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (id == null) id = UUID.randomUUID();
        if (status == null) status = Status.NOT_INITIALIZED;
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public void begin(UUID jobId, boolean sourceChanged) {
        this.analysisJobId = jobId;
        this.errorCode = "";
        this.errorSummary = "";
        this.status = latestSuccessfulAt == null ? Status.RUNNING : sourceChanged ? Status.STALE : Status.RUNNING;
    }

    public void complete(
        String projectRevision,
        String sourceEventFingerprint,
        int sourceEventCount,
        Instant earliestEventAt,
        Instant latestEventAt,
        String strategyVersion,
        String promptVersion,
        String overviewJson,
        String chaptersJson,
        String storiesJson,
        String threadsJson,
        String coverageJson,
        String diagnosticsJson,
        UUID analysisJobId,
        boolean degraded
    ) {
        Instant now = Instant.now();
        this.projectRevision = text(projectRevision);
        this.sourceEventFingerprint = text(sourceEventFingerprint);
        this.sourceEventCount = Math.max(0, sourceEventCount);
        this.earliestEventAt = earliestEventAt;
        this.latestEventAt = latestEventAt;
        this.strategyVersion = text(strategyVersion);
        this.promptVersion = text(promptVersion);
        this.overviewJson = objectJson(overviewJson);
        this.chaptersJson = arrayJson(chaptersJson);
        this.storiesJson = arrayJson(storiesJson);
        this.threadsJson = arrayJson(threadsJson);
        this.coverageJson = objectJson(coverageJson);
        this.diagnosticsJson = objectJson(diagnosticsJson);
        this.analysisJobId = analysisJobId;
        this.generatedAt = now;
        this.latestSuccessfulAt = now;
        this.errorCode = "";
        this.errorSummary = "";
        this.status = degraded ? Status.DEGRADED : Status.READY;
    }

    public void recordCacheHit(UUID jobId, String diagnosticsJson) {
        this.analysisJobId = jobId;
        this.diagnosticsJson = objectJson(diagnosticsJson);
        this.errorCode = "";
        this.errorSummary = "";
        if (this.status == Status.RUNNING || this.status == Status.STALE) {
            this.status = latestSuccessfulAt == null ? Status.NOT_INITIALIZED : Status.READY;
        }
    }

    public void fail(String errorCode, String errorSummary, String diagnosticsJson, UUID jobId) {
        this.analysisJobId = jobId;
        this.errorCode = text(errorCode);
        this.errorSummary = text(errorSummary);
        this.diagnosticsJson = objectJson(diagnosticsJson);
        this.status = latestSuccessfulAt == null ? Status.FAILED : Status.DEGRADED;
    }

    /**
     * Marks a known internal write for the next explicit continuity refresh.
     * The marker is diagnostic state on the existing read model, not another
     * source ledger and never starts scanning or model work by itself.
     */
    public void markContinuityDirty(String revision, String reason, Instant occurredAt) {
        String safeRevision = bounded(revision, 96);
        if (safeRevision.isBlank()) return;
        this.continuityDirtyRevision = safeRevision;
        this.continuityDirtyReason = bounded(reason, 80);
        this.continuityDirtyAt = occurredAt == null ? Instant.now() : occurredAt;
        preserveDirtyStatus();
    }

    /** Advances the project-local dirty generation while the snapshot row is locked. */
    public long advanceContinuityDirtyGeneration() {
        long current = continuityDirtyGeneration == null ? 0L : continuityDirtyGeneration;
        if (current == Long.MAX_VALUE) {
            throw new IllegalStateException("continuity dirty generation exhausted");
        }
        continuityDirtyGeneration = current + 1L;
        return continuityDirtyGeneration;
    }

    /** Clears only the marker observed before source discovery. */
    public boolean acknowledgeContinuityDirty(String observedRevision) {
        String current = text(continuityDirtyRevision);
        if (current.isBlank()) return true;
        if (!current.equals(text(observedRevision))) {
            preserveDirtyStatus();
            return false;
        }
        this.continuityDirtyRevision = "";
        this.continuityDirtyReason = "";
        this.continuityDirtyAt = null;
        return true;
    }

    private void preserveDirtyStatus() {
        if (latestSuccessfulAt != null && status != Status.DEGRADED && status != Status.FAILED) {
            status = Status.STALE;
        }
    }

    private static String text(String value) { return value == null ? "" : value.trim(); }
    private static String bounded(String value, int maximum) {
        String safe = text(value);
        return safe.length() <= maximum ? safe : safe.substring(0, maximum);
    }
    private static String objectJson(String value) { String safe = text(value); return safe.isBlank() ? "{}" : safe; }
    private static String arrayJson(String value) { String safe = text(value); return safe.isBlank() ? "[]" : safe; }

    public UUID getId() { return id; }
    public UUID getProjectId() { return projectId; }
    public String getProjectRevision() { return projectRevision; }
    public String getSourceEventFingerprint() { return sourceEventFingerprint; }
    public int getSourceEventCount() { return sourceEventCount == null ? 0 : sourceEventCount; }
    public Instant getEarliestEventAt() { return earliestEventAt; }
    public Instant getLatestEventAt() { return latestEventAt; }
    public String getStrategyVersion() { return strategyVersion; }
    public String getPromptVersion() { return promptVersion; }
    public Status getStatus() { return status; }
    public String getOverviewJson() { return overviewJson; }
    public String getChaptersJson() { return chaptersJson; }
    public String getStoriesJson() { return storiesJson; }
    public String getThreadsJson() { return threadsJson; }
    public String getCoverageJson() { return coverageJson; }
    public String getDiagnosticsJson() { return diagnosticsJson; }
    public UUID getAnalysisJobId() { return analysisJobId; }
    public Instant getGeneratedAt() { return generatedAt; }
    public Instant getLatestSuccessfulAt() { return latestSuccessfulAt; }
    public String getContinuityDirtyRevision() { return text(continuityDirtyRevision); }
    public String getContinuityDirtyReason() { return text(continuityDirtyReason); }
    public Instant getContinuityDirtyAt() { return continuityDirtyAt; }
    public long getContinuityDirtyGeneration() {
        return continuityDirtyGeneration == null ? 0L : continuityDirtyGeneration;
    }
    public String getErrorCode() { return errorCode; }
    public String getErrorSummary() { return errorSummary; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}
