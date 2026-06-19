package com.projectflow.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "model_usage_records")
public class ModelUsageRecord {
    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(nullable = false, length = 80)
    private String operation;

    @Column(name = "provider_name", nullable = false, length = 80)
    private String providerName;

    @Column(name = "model_name", nullable = false, length = 120)
    private String modelName;

    @Column(name = "prompt_tokens", nullable = false)
    private int promptTokens;

    @Column(name = "completion_tokens", nullable = false)
    private int completionTokens;

    @Column(name = "total_tokens", nullable = false)
    private int totalTokens;

    @Column(name = "usage_estimated", nullable = false)
    private boolean usageEstimated;

    @Column(name = "latency_ms", nullable = false)
    private long latencyMs;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "error_type", length = 80)
    private String errorType;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "quality_warnings", columnDefinition = "text")
    private String qualityWarnings;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ModelUsageRecord() {
    }

    public ModelUsageRecord(
        UUID projectId,
        String operation,
        String providerName,
        String modelName,
        int promptTokens,
        int completionTokens,
        boolean usageEstimated,
        long latencyMs,
        String status,
        String errorType,
        String errorMessage,
        String qualityWarnings
    ) {
        this.id = UUID.randomUUID();
        this.projectId = projectId;
        this.operation = operation;
        this.providerName = providerName;
        this.modelName = modelName;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = promptTokens + completionTokens;
        this.usageEstimated = usageEstimated;
        this.latencyMs = latencyMs;
        this.status = status;
        this.errorType = errorType;
        this.errorMessage = errorMessage;
        this.qualityWarnings = qualityWarnings;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getOperation() {
        return operation;
    }

    public String getProviderName() {
        return providerName;
    }

    public String getModelName() {
        return modelName;
    }

    public int getPromptTokens() {
        return promptTokens;
    }

    public int getCompletionTokens() {
        return completionTokens;
    }

    public int getTotalTokens() {
        return totalTokens;
    }

    public boolean isUsageEstimated() {
        return usageEstimated;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public String getStatus() {
        return status;
    }

    public String getErrorType() {
        return errorType;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getQualityWarnings() {
        return qualityWarnings;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
