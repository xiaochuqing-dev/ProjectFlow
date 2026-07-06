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
    public void markIgnored() { status = DevelopmentSegmentStatus.IGNORED; }
    private static List<String> copy(List<String> values) { return values == null ? new ArrayList<>() : new ArrayList<>(values); }

    public UUID getId() { return id; }
    public UUID getProjectId() { return projectId; }
    public UUID getBatchId() { return batchId; }
    public String getTitle() { return title; }
    public String getPlainSummary() { return plainSummary; }
    public List<String> getMainChanges() { return List.copyOf(mainChanges); }
    public String getUserVisibleValue() { return userVisibleValue; }
    public List<String> getIncludedCommitRefs() { return List.copyOf(includedCommitRefs); }
    public List<String> getIncludedAgentResultRefs() { return List.copyOf(includedAgentResultRefs); }
    public List<String> getAffectedFiles() { return List.copyOf(affectedFiles); }
    public List<String> getEvidenceRefs() { return List.copyOf(evidenceRefs); }
    public EvidenceConfidence getConfidence() { return confidence; }
    public DevelopmentSegmentStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
