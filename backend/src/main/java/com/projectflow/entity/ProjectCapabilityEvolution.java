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
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    name = "project_capability_evolutions",
    uniqueConstraints = @UniqueConstraint(name = "uk_capability_evolution_fingerprint", columnNames = {"project_id", "operation_fingerprint"}),
    indexes = {
        @Index(name = "idx_capability_evolution_capability", columnList = "capability_id,occurred_at"),
        @Index(name = "idx_capability_evolution_project", columnList = "project_id,occurred_at")
    }
)
public class ProjectCapabilityEvolution {
    @Id private UUID id;
    @Column(name = "project_id", nullable = false) private UUID projectId;
    @Column(name = "capability_id", nullable = false) private UUID capabilityId;
    @Enumerated(EnumType.STRING)
    @Column(name = "evolution_type", nullable = false, length = 40)
    private ProjectCapabilityEvolutionType evolutionType;
    @Column(name = "version_before", nullable = false) private Integer versionBefore;
    @Column(name = "version_after", nullable = false) private Integer versionAfter;
    @Column(nullable = false, length = 200) private String title = "";
    @Column(columnDefinition = "text") private String summary = "";
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;
    @Column(name = "source_fact_count", nullable = false) private Integer sourceFactCount = 0;
    @Column(name = "source_batch_count", nullable = false) private Integer sourceBatchCount = 0;
    @Convert(converter = StringListConverter.class)
    @Column(name = "source_timeline_periods", columnDefinition = "text")
    private List<String> sourceTimelinePeriods = new ArrayList<>();
    @Column(name = "analysis_job_id") private UUID analysisJobId;
    @Column(name = "generation_version", nullable = false) private Integer generationVersion = 1;
    @Column(name = "model_provider", length = 200) private String modelProvider = "";
    @Column(name = "model_name", length = 200) private String modelName = "";
    @Column(name = "operation_fingerprint", nullable = false, length = 64) private String operationFingerprint;
    @Column(name = "merged_from_capability_id") private UUID mergedFromCapabilityId;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected ProjectCapabilityEvolution() {
    }

    public ProjectCapabilityEvolution(
        UUID projectId, UUID capabilityId, ProjectCapabilityEvolutionType type, int before, int after,
        String title, String summary, Instant occurredAt, String operationFingerprint
    ) {
        this.id = UUID.randomUUID();
        this.projectId = projectId;
        this.capabilityId = capabilityId;
        this.evolutionType = type;
        this.versionBefore = before;
        this.versionAfter = after;
        this.title = safe(title);
        this.summary = safe(summary);
        this.occurredAt = occurredAt == null ? Instant.now() : occurredAt;
        this.operationFingerprint = required(operationFingerprint);
    }

    @PrePersist void prePersist() { Instant now = Instant.now(); if (id == null) id = UUID.randomUUID(); if (createdAt == null) createdAt = now; updatedAt = now; }
    @PreUpdate void preUpdate() { updatedAt = Instant.now(); }

    public void attachSourceStats(int facts, int batches, List<String> periods, UUID jobId, String provider, String model) {
        sourceFactCount = Math.max(0, facts);
        sourceBatchCount = Math.max(0, batches);
        sourceTimelinePeriods = periods == null ? new ArrayList<>() : new ArrayList<>(periods);
        analysisJobId = jobId;
        modelProvider = safe(provider);
        modelName = safe(model);
    }
    public void markMergeSource(UUID sourceId) { mergedFromCapabilityId = sourceId; }
    public UUID getId() { return id; }
    public UUID getProjectId() { return projectId; }
    public UUID getCapabilityId() { return capabilityId; }
    public ProjectCapabilityEvolutionType getEvolutionType() { return evolutionType; }
    public int getVersionBefore() { return versionBefore == null ? 0 : versionBefore; }
    public int getVersionAfter() { return versionAfter == null ? 0 : versionAfter; }
    public String getTitle() { return safe(title); }
    public String getSummary() { return safe(summary); }
    public Instant getOccurredAt() { return occurredAt; }
    public int getSourceFactCount() { return sourceFactCount == null ? 0 : sourceFactCount; }
    public int getSourceBatchCount() { return sourceBatchCount == null ? 0 : sourceBatchCount; }
    public List<String> getSourceTimelinePeriods() { return sourceTimelinePeriods == null ? List.of() : List.copyOf(sourceTimelinePeriods); }
    public UUID getAnalysisJobId() { return analysisJobId; }
    public int getGenerationVersion() { return generationVersion == null ? 1 : generationVersion; }
    public String getModelProvider() { return safe(modelProvider); }
    public String getModelName() { return safe(modelName); }
    public String getOperationFingerprint() { return safe(operationFingerprint); }
    public UUID getMergedFromCapabilityId() { return mergedFromCapabilityId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    private static String required(String value) { if (value == null || value.isBlank()) throw new IllegalArgumentException("Evolution fingerprint is required"); return value.trim(); }
    private static String safe(String value) { return value == null ? "" : value.trim(); }
}
