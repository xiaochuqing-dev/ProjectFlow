package com.projectflow.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "project_memory_read_audits", indexes = {
    @Index(name = "idx_memory_audit_project_time", columnList = "project_id,occurred_at"),
    @Index(name = "idx_memory_audit_user_time", columnList = "user_id,occurred_at")
})
public class ProjectMemoryReadAudit {
    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "project_id") private UUID projectId;
    @Column(name = "operation_name", nullable = false, length = 80) private String operationName;
    @Column(name = "result_count", nullable = false) private Integer resultCount;
    @Column(name = "latency_ms", nullable = false) private Long latencyMs;
    @Column(nullable = false, length = 30) private String status;
    @Column(name = "caller_hash", length = 64) private String callerHash;
    @Column(name = "query_length", nullable = false) private Integer queryLength;
    @Column(name = "query_hash", length = 64) private String queryHash;
    @Column(name = "entity_types", length = 200) private String entityTypes;
    @Column(name = "filter_summary", length = 500) private String filterSummary;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;

    protected ProjectMemoryReadAudit() {
    }

    public ProjectMemoryReadAudit(
        UUID userId, UUID projectId, String operationName, int resultCount, long latencyMs,
        String status, String callerHash, int queryLength, String queryHash,
        String entityTypes, String filterSummary
    ) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.projectId = projectId;
        this.operationName = safe(operationName);
        this.resultCount = Math.max(0, resultCount);
        this.latencyMs = Math.max(0, latencyMs);
        this.status = safe(status);
        this.callerHash = safe(callerHash);
        this.queryLength = Math.max(0, queryLength);
        this.queryHash = safe(queryHash);
        this.entityTypes = limit(entityTypes, 200);
        this.filterSummary = limit(filterSummary, 500);
    }

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (occurredAt == null) occurredAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getProjectId() { return projectId; }
    public String getOperationName() { return safe(operationName); }
    public int getResultCount() { return resultCount == null ? 0 : resultCount; }
    public long getLatencyMs() { return latencyMs == null ? 0 : latencyMs; }
    public String getStatus() { return safe(status); }
    public String getCallerHash() { return safe(callerHash); }
    public int getQueryLength() { return queryLength == null ? 0 : queryLength; }
    public String getQueryHash() { return safe(queryHash); }
    public String getEntityTypes() { return safe(entityTypes); }
    public String getFilterSummary() { return safe(filterSummary); }
    public Instant getOccurredAt() { return occurredAt; }

    private static String safe(String value) { return value == null ? "" : value.trim(); }
    private static String limit(String value, int max) {
        String safe = safe(value);
        return safe.length() <= max ? safe : safe.substring(0, max);
    }
}
