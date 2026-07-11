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
@Table(name = "project_changes")
public class ProjectChange {
    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "material_id")
    private UUID materialId;

    @Column(name = "linked_suggestion_id")
    private UUID linkedSuggestionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 40)
    private ProjectChangeSourceType sourceType;

    @Column(name = "source_ref", columnDefinition = "text")
    private String sourceRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_kind", nullable = false, length = 40)
    private ProjectChangeKind changeKind;

    @Enumerated(EnumType.STRING)
    @Column(name = "impact_level", nullable = false, length = 40)
    private ProjectChangeImpactLevel impactLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ProjectChangeStatus status;

    @Column(nullable = false, length = 180)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String summary;

    @Column(columnDefinition = "text")
    private String details;

    @Column(name = "affected_files", columnDefinition = "text")
    private String affectedFiles;

    @Column(name = "related_tasks", columnDefinition = "text")
    private String relatedTasks;

    @Column(name = "test_evidence", columnDefinition = "text")
    private String testEvidence;

    @Column(name = "build_evidence", columnDefinition = "text")
    private String buildEvidence;

    @Column(name = "risk_notes", columnDefinition = "text")
    private String riskNotes;

    @Column(name = "decision_notes", columnDefinition = "text")
    private String decisionNotes;

    @Column(name = "learning_notes", columnDefinition = "text")
    private String learningNotes;

    @Column(name = "asset_candidates", columnDefinition = "text")
    private String assetCandidates;

    @Column(name = "development_segment_id")
    private UUID developmentSegmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "suggested_action", length = 40)
    private SedimentAction suggestedAction;

    @Column(name = "target_sediment_id")
    private UUID targetSedimentId;

    @Column(name = "problem_solved", columnDefinition = "text")
    private String problemSolved;

    @Column(name = "suggestion_reason", columnDefinition = "text")
    private String suggestionReason;

    @Convert(converter = StringListConverter.class)
    @Column(name = "evidence_refs", columnDefinition = "text")
    private List<String> evidenceRefs = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_confidence", length = 20)
    private EvidenceConfidence evidenceConfidence;

    @Column(name = "needs_user_review")
    private Boolean needsUserReview;

    @Column(name = "source_batch_id")
    private UUID sourceBatchId;

    @Column(name = "content_source", length = 40)
    private String contentSource = "LEGACY_UNKNOWN";

    @Column(name = "quality_status", length = 40)
    private String qualityStatus = "NEEDS_REVIEW";

    @Column(name = "recommendation_strength", length = 30)
    private String recommendationStrength = "REFERENCE_ONLY";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    protected ProjectChange() {
    }

    public ProjectChange(UUID projectId, UUID materialId) {
        this.id = UUID.randomUUID();
        this.projectId = projectId;
        this.materialId = materialId;
        this.status = ProjectChangeStatus.PENDING;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getMaterialId() {
        return materialId;
    }

    public UUID getLinkedSuggestionId() {
        return linkedSuggestionId;
    }

    public ProjectChangeSourceType getSourceType() {
        return sourceType;
    }

    public String getSourceRef() {
        return sourceRef;
    }

    public ProjectChangeKind getChangeKind() {
        return changeKind;
    }

    public ProjectChangeImpactLevel getImpactLevel() {
        return impactLevel;
    }

    public ProjectChangeStatus getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }

    public String getSummary() {
        return summary;
    }

    public String getDetails() {
        return details;
    }

    public String getAffectedFiles() {
        return affectedFiles;
    }

    public String getRelatedTasks() {
        return relatedTasks;
    }

    public String getTestEvidence() {
        return testEvidence;
    }

    public String getBuildEvidence() {
        return buildEvidence;
    }

    public String getRiskNotes() {
        return riskNotes;
    }

    public String getDecisionNotes() {
        return decisionNotes;
    }

    public String getLearningNotes() {
        return learningNotes;
    }

    public String getAssetCandidates() {
        return assetCandidates;
    }

    public UUID getDevelopmentSegmentId() { return developmentSegmentId; }
    public SedimentAction getSuggestedAction() { return suggestedAction; }
    public UUID getTargetSedimentId() { return targetSedimentId; }
    public String getProblemSolved() { return problemSolved; }
    public String getSuggestionReason() { return suggestionReason; }
    public List<String> getEvidenceRefs() { return List.copyOf(evidenceRefs); }
    public EvidenceConfidence getEvidenceConfidence() { return evidenceConfidence; }
    public boolean isNeedsUserReview() { return Boolean.TRUE.equals(needsUserReview); }
    public UUID getSourceBatchId() { return sourceBatchId; }
    public String getContentSource() { return contentSource == null ? "LEGACY_UNKNOWN" : contentSource; }
    public String getQualityStatus() { return qualityStatus == null ? "NEEDS_REVIEW" : qualityStatus; }
    public String getRecommendationStrength() { return recommendationStrength == null ? "REFERENCE_ONLY" : recommendationStrength; }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public void update(
        ProjectChangeSourceType sourceType,
        String sourceRef,
        UUID linkedSuggestionId,
        ProjectChangeKind changeKind,
        ProjectChangeImpactLevel impactLevel,
        String title,
        String summary,
        String details,
        String affectedFiles,
        String relatedTasks,
        String testEvidence,
        String buildEvidence,
        String riskNotes,
        String decisionNotes,
        String learningNotes,
        String assetCandidates
    ) {
        this.sourceType = sourceType;
        this.sourceRef = sourceRef;
        this.linkedSuggestionId = linkedSuggestionId;
        this.changeKind = changeKind;
        this.impactLevel = impactLevel;
        this.title = title;
        this.summary = summary;
        this.details = details;
        this.affectedFiles = affectedFiles;
        this.relatedTasks = relatedTasks;
        this.testEvidence = testEvidence;
        this.buildEvidence = buildEvidence;
        this.riskNotes = riskNotes;
        this.decisionNotes = decisionNotes;
        this.learningNotes = learningNotes;
        this.assetCandidates = assetCandidates;
        if (this.status == ProjectChangeStatus.PENDING) {
            return;
        }
        this.status = ProjectChangeStatus.EDITED;
    }

    public void markAccepted() {
        this.status = ProjectChangeStatus.ACCEPTED;
        this.reviewedAt = Instant.now();
    }

    public void markIgnored() {
        this.status = ProjectChangeStatus.IGNORED;
        this.reviewedAt = Instant.now();
    }

    public void markMerged() {
        this.status = ProjectChangeStatus.MERGED;
        this.reviewedAt = Instant.now();
    }

    public void updateSedimentSuggestion(
        UUID developmentSegmentId,
        SedimentAction suggestedAction,
        UUID targetSedimentId,
        String problemSolved,
        String recommendationReason,
        List<String> evidenceRefs,
        EvidenceConfidence evidenceConfidence,
        boolean needsUserReview
    ) {
        this.developmentSegmentId = developmentSegmentId;
        this.suggestedAction = suggestedAction;
        this.targetSedimentId = targetSedimentId;
        this.problemSolved = problemSolved == null ? "" : problemSolved.trim();
        this.suggestionReason = recommendationReason == null ? "" : recommendationReason.trim();
        this.evidenceRefs = evidenceRefs == null ? new ArrayList<>() : new ArrayList<>(evidenceRefs);
        this.evidenceConfidence = evidenceConfidence == null ? EvidenceConfidence.LOW : evidenceConfidence;
        this.needsUserReview = needsUserReview;
    }

    public void recordReviewMetadata(UUID batchId, String source, String quality, String strength) {
        this.sourceBatchId = batchId;
        this.contentSource = safe(source, "LEGACY_UNKNOWN");
        this.qualityStatus = safe(quality, "NEEDS_REVIEW");
        this.recommendationStrength = safe(strength, "REFERENCE_ONLY");
    }

    private String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
