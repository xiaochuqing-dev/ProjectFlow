package com.projectflow.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "project_analysis_records")
public class ProjectAnalysisRecord {
    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "record_type", nullable = false, length = 40)
    private ProjectAnalysisRecordType recordType;

    @Column(name = "file_path", length = 1000)
    private String filePath;

    @Column(nullable = false, columnDefinition = "text")
    private String summary;

    @Column(nullable = false, columnDefinition = "text")
    private String details;

    @Column(name = "analysis_source", nullable = false, length = 60)
    private String analysisSource;

    @Column(name = "model_used", nullable = false)
    private boolean modelUsed;

    @Column(name = "provider_name", length = 160)
    private String providerName;

    @Column(nullable = false, length = 40)
    private String confidence;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ProjectAnalysisRecord() {
    }

    public ProjectAnalysisRecord(UUID projectId, ProjectAnalysisRecordType recordType) {
        this.id = UUID.randomUUID();
        this.projectId = projectId;
        this.recordType = recordType;
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

    public ProjectAnalysisRecordType getRecordType() {
        return recordType;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getSummary() {
        return summary;
    }

    public String getDetails() {
        return details;
    }

    public String getAnalysisSource() {
        return analysisSource;
    }

    public boolean isModelUsed() {
        return modelUsed;
    }

    public String getProviderName() {
        return providerName;
    }

    public String getConfidence() {
        return confidence;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void update(
        String filePath,
        String summary,
        String details,
        String analysisSource,
        boolean modelUsed,
        String providerName,
        String confidence
    ) {
        this.filePath = filePath;
        this.summary = summary;
        this.details = details;
        this.analysisSource = analysisSource;
        this.modelUsed = modelUsed;
        this.providerName = providerName;
        this.confidence = confidence;
    }
}
