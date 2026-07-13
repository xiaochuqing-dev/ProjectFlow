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
@Table(name = "development_segments")
public class DevelopmentSegment {
    @Id
    private UUID id;
    @Column(name = "project_id", nullable = false)
    private UUID projectId;
    @Column(name = "batch_id", nullable = false)
    private UUID batchId;
    @Column(nullable = false, length = 200)
    private String title = "";
    @Column(name = "plain_summary", columnDefinition = "text")
    private String plainSummary = "";
    @Convert(converter = StringListConverter.class)
    @Column(name = "main_changes", columnDefinition = "text")
    private List<String> mainChanges = new ArrayList<>();
    @Column(name = "user_visible_value", columnDefinition = "text")
    private String userVisibleValue = "";
    @Convert(converter = StringListConverter.class)
    @Column(name = "included_commit_refs", columnDefinition = "text")
    private List<String> includedCommitRefs = new ArrayList<>();
    @Convert(converter = StringListConverter.class)
    @Column(name = "included_agent_result_refs", columnDefinition = "text")
    private List<String> includedAgentResultRefs = new ArrayList<>();
    @Convert(converter = StringListConverter.class)
    @Column(name = "affected_files", columnDefinition = "text")
    private List<String> affectedFiles = new ArrayList<>();
    @Convert(converter = StringListConverter.class)
    @Column(name = "evidence_refs", columnDefinition = "text")
    private List<String> evidenceRefs = new ArrayList<>();
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EvidenceConfidence confidence = EvidenceConfidence.LOW;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private DevelopmentSegmentStatus status = DevelopmentSegmentStatus.NEEDS_REVIEW;
    @Column(name = "generation_mode", length = 40)
    private String generationMode = "LOCAL_RULE";
    @Column(name = "model_provider", length = 160)
    private String modelProvider = "";
    @Column(name = "fallback_reason", columnDefinition = "text")
    private String fallbackReason = "";
    @Column(name = "quality_status", length = 40)
    private String qualityStatus = "NEEDS_MANUAL";
    @Column(name = "quality_reason", columnDefinition = "text")
    private String qualityReason = "";
    @Convert(converter = StringListConverter.class)
    @Column(name = "commit_urls", columnDefinition = "text")
    private List<String> commitUrls = new ArrayList<>();
    @Convert(converter = StringListConverter.class)
    @Column(name = "uncertainties", columnDefinition = "text")
    private List<String> uncertainties = new ArrayList<>();
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DevelopmentSegment() {
    }

    public DevelopmentSegment(UUID projectId, UUID batchId) {
        this.id = UUID.randomUUID();
        this.projectId = projectId;
        this.batchId = batchId;
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
    void preUpdate() { updatedAt = Instant.now(); }

    public void updateContent(
        String title,
        String plainSummary,
        List<String> mainChanges,
        String userVisibleValue,
        List<String> commitRefs,
        List<String> agentResultRefs,
        List<String> affectedFiles,
        List<String> evidenceRefs,
        EvidenceConfidence confidence,
        DevelopmentSegmentStatus status
    ) {
        this.title = title == null ? "" : title.trim();
        this.plainSummary = plainSummary == null ? "" : plainSummary.trim();
        this.mainChanges = copy(mainChanges);
        this.userVisibleValue = userVisibleValue == null ? "" : userVisibleValue.trim();
        this.includedCommitRefs = copy(commitRefs);
        this.includedAgentResultRefs = copy(agentResultRefs);
        this.affectedFiles = copy(affectedFiles);
        this.evidenceRefs = copy(evidenceRefs);
        this.confidence = confidence == null ? EvidenceConfidence.LOW : confidence;
        this.status = status == null ? DevelopmentSegmentStatus.NEEDS_REVIEW : status;
    }

    public void markConfirmed() { status = DevelopmentSegmentStatus.CONFIRMED; }
    public void updateAnalysis(
        String mode, String provider, String fallback, String quality, String qualityDetail,
        List<String> urls, List<String> uncertaintyItems
    ) {
        generationMode = safe(mode);
        modelProvider = safe(provider);
        fallbackReason = safe(fallback);
        qualityStatus = safe(quality);
        qualityReason = safe(qualityDetail);
        commitUrls = copy(urls);
        uncertainties = copy(uncertaintyItems);
    }
    public void markIgnored() { status = DevelopmentSegmentStatus.IGNORED; }
    private static List<String> copy(List<String> values) { return values == null ? new ArrayList<>() : new ArrayList<>(values); }

    public UUID getId() { return id; }
    public UUID getProjectId() { return projectId; }
    public UUID getBatchId() { return batchId; }
    public String getTitle() { return safe(title); }
    public String getPlainSummary() { return safe(plainSummary); }
    public List<String> getMainChanges() { return immutable(mainChanges); }
    public String getUserVisibleValue() { return safe(userVisibleValue); }
    public List<String> getIncludedCommitRefs() { return immutable(includedCommitRefs); }
    public List<String> getIncludedAgentResultRefs() { return immutable(includedAgentResultRefs); }
    public List<String> getAffectedFiles() { return immutable(affectedFiles); }
    public List<String> getEvidenceRefs() { return immutable(evidenceRefs); }
    public EvidenceConfidence getConfidence() { return confidence == null ? EvidenceConfidence.LOW : confidence; }
    public DevelopmentSegmentStatus getStatus() { return status == null ? DevelopmentSegmentStatus.NEEDS_REVIEW : status; }
    public String getGenerationMode() { return generationMode == null || generationMode.isBlank() ? "LOCAL_RULE" : generationMode.trim(); }
    public String getModelProvider() { return safe(modelProvider); }
    public String getFallbackReason() { return safe(fallbackReason); }
    public String getQualityStatus() { return qualityStatus == null || qualityStatus.isBlank() ? "NEEDS_REVIEW" : qualityStatus.trim(); }
    public String getQualityReason() { return safe(qualityReason); }
    public List<String> getCommitUrls() { return immutable(commitUrls); }
    public List<String> getUncertainties() { return immutable(uncertainties); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    private static String safe(String value) { return value == null ? "" : value.trim(); }
    private static List<String> immutable(List<String> values) { return values == null ? List.of() : List.copyOf(values); }
}
