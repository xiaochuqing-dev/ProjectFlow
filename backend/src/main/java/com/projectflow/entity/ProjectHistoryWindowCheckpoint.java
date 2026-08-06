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
import jakarta.persistence.Version;

/** Durable, redacted progress for one bounded history semantic window. */
@Entity
@Table(name = "project_history_window_checkpoints", uniqueConstraints = @UniqueConstraint(
    name = "uk_history_window_cache", columnNames = {"project_id", "cache_key"}
))
public class ProjectHistoryWindowCheckpoint {
    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "window_identity", nullable = false, length = 120)
    private String windowIdentity;

    @Column(name = "cache_key", nullable = false, length = 64)
    private String cacheKey;

    @Column(name = "source_fingerprint", nullable = false, length = 64)
    private String sourceFingerprint;

    @Column(nullable = false, length = 24)
    private String status;

    @Column(name = "story_count", nullable = false)
    private int storyCount;

    @Column(name = "event_count", nullable = false)
    private int eventCount;

    @Column(name = "request_count", nullable = false)
    private int requestCount;

    @Column(name = "diagnostics_json", nullable = false, columnDefinition = "text")
    private String diagnosticsJson;

    /**
     * Validated presentation only. It lets a cache hit restore safe wording
     * without retaining a provider request, raw response, or reasoning text.
     */
    @Column(name = "validated_result_json", nullable = false, columnDefinition = "text")
    private String validatedResultJson;

    @Column(name = "last_error", nullable = false, length = 500)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected ProjectHistoryWindowCheckpoint() {
    }

    public ProjectHistoryWindowCheckpoint(UUID projectId, String windowIdentity, String cacheKey, String sourceFingerprint,
        int storyCount, int eventCount) {
        this.id = UUID.randomUUID();
        this.projectId = projectId;
        this.windowIdentity = clean(windowIdentity, 120);
        this.cacheKey = clean(cacheKey, 64);
        this.sourceFingerprint = clean(sourceFingerprint, 64);
        this.status = "RUNNING";
        this.storyCount = Math.max(0, storyCount);
        this.eventCount = Math.max(0, eventCount);
        this.diagnosticsJson = "{}";
        this.validatedResultJson = "{}";
        this.lastError = "";
    }

    public void succeed(int requestCount, String diagnosticsJson) {
        this.status = "SUCCEEDED";
        this.requestCount = Math.max(0, requestCount);
        this.diagnosticsJson = object(diagnosticsJson);
        this.lastError = "";
    }

    /** Start (or restart) the bounded window attempt without discarding its audit row. */
    public void beginAttempt() {
        this.status = "RUNNING";
        this.lastError = "";
    }

    public void fail(String error, String diagnosticsJson) {
        this.status = "FAILED";
        this.lastError = clean(error, 500);
        this.diagnosticsJson = object(diagnosticsJson);
    }

    public void cancel(String summary, String diagnosticsJson) {
        this.status = "CANCELLED";
        this.lastError = clean(summary, 500);
        this.diagnosticsJson = object(diagnosticsJson);
    }

    public void skip(String summary, String diagnosticsJson) {
        this.status = "SKIPPED";
        this.lastError = clean(summary, 500);
        this.diagnosticsJson = object(diagnosticsJson);
    }

    public void storeValidatedResult(String resultJson) {
        this.validatedResultJson = boundedJson(resultJson);
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = now;
        if (diagnosticsJson == null || diagnosticsJson.isBlank()) diagnosticsJson = "{}";
        if (validatedResultJson == null || validatedResultJson.isBlank()) validatedResultJson = "{}";
        if (lastError == null) lastError = "";
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        if (validatedResultJson == null || validatedResultJson.isBlank()) validatedResultJson = "{}";
        updatedAt = Instant.now();
    }

    private static String clean(String value, int max) {
        String safe = value == null ? "" : value.trim();
        return safe.length() <= max ? safe : safe.substring(0, Math.max(0, max - 1)) + "…";
    }

    private static String object(String value) {
        String safe = value == null ? "" : value.trim();
        return safe.isBlank() ? "{}" : safe;
    }

    private static String boundedJson(String value) {
        String safe = value == null || value.isBlank() ? "{}" : value.trim();
        return safe.length() <= 500_000 ? safe : "{}";
    }

    public UUID getId() { return id; }
    public UUID getProjectId() { return projectId; }
    public String getWindowIdentity() { return windowIdentity; }
    public String getCacheKey() { return cacheKey; }
    public String getSourceFingerprint() { return sourceFingerprint; }
    public String getStatus() { return status; }
    public int getStoryCount() { return storyCount; }
    public int getEventCount() { return eventCount; }
    public int getRequestCount() { return requestCount; }
    public String getDiagnosticsJson() { return diagnosticsJson; }
    public String getValidatedResultJson() { return validatedResultJson == null ? "{}" : validatedResultJson; }
    public String getLastError() { return lastError; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
