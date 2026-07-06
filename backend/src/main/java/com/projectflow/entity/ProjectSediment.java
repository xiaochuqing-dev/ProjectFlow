package com.projectflow.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

import com.projectflow.support.StringListConverter;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "project_sediments")
public class ProjectSediment {
    @Id
    private UUID id;
    @Column(name = "project_id", nullable = false)
    private UUID projectId;
    @Column(nullable = false, length = 200)
    private String title = "";
    @Column(columnDefinition = "text")
    private String summary = "";
    @Column(name = "problem_solved", columnDefinition = "text")
    private String problemSolved = "";
    @Column(name = "sediment_type", nullable = false, length = 80)
    private String sedimentType = "PROJECT_CAPABILITY";
    @Column(nullable = false, length = 40)
    private String status = "CONFIRMED";
    @Convert(converter = StringListConverter.class)
    @Column(name = "source_segment_ids", columnDefinition = "text")
    private List<String> sourceSegmentIds = new ArrayList<>();
    @Convert(converter = StringListConverter.class)
    @Column(name = "evidence_refs", columnDefinition = "text")
    private List<String> evidenceRefs = new ArrayList<>();
    @Column(name = "developer_notes", columnDefinition = "text")
    private String developerNotes = "";
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProjectSediment() {
    }

    public ProjectSediment(UUID projectId) {
        this.id = UUID.randomUUID();
        this.projectId = projectId;
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

    public void updateCore(String title, String summary, String problemSolved, String type, List<String> segmentIds, List<String> evidenceRefs) {
        this.title = title == null ? "" : title.trim();
        this.summary = summary == null ? "" : summary.trim();
        this.problemSolved = problemSolved == null ? "" : problemSolved.trim();
        this.sedimentType = type == null || type.isBlank() ? "PROJECT_CAPABILITY" : type.trim();
        this.sourceSegmentIds = segmentIds == null ? new ArrayList<>() : new ArrayList<>(segmentIds);
        this.evidenceRefs = evidenceRefs == null ? new ArrayList<>() : new ArrayList<>(evidenceRefs);
    }

    public void updateDeveloperNotes(String notes) { this.developerNotes = notes == null ? "" : notes.trim(); }

    public void merge(String newSummary, String newProblemSolved, String segmentId, List<String> newEvidenceRefs) {
        if (newSummary != null && !newSummary.isBlank() && !summary.contains(newSummary.trim())) {
            summary = summary.isBlank() ? newSummary.trim() : summary + "\n" + newSummary.trim();
        }
        if (problemSolved.isBlank() && newProblemSolved != null) {
            problemSolved = newProblemSolved.trim();
        }
        addSourceAndEvidence(segmentId, newEvidenceRefs);
    }

    public void addEvidence(String segmentId, List<String> newEvidenceRefs) {
        addSourceAndEvidence(segmentId, newEvidenceRefs);
    }

    private void addSourceAndEvidence(String segmentId, List<String> newEvidenceRefs) {
        LinkedHashSet<String> segments = new LinkedHashSet<>(sourceSegmentIds);
        if (segmentId != null && !segmentId.isBlank()) {
            segments.add(segmentId);
        }
        sourceSegmentIds = new ArrayList<>(segments);
        LinkedHashSet<String> evidence = new LinkedHashSet<>(evidenceRefs);
        if (newEvidenceRefs != null) {
            evidence.addAll(newEvidenceRefs);
        }
        evidenceRefs = new ArrayList<>(evidence);
    }

    public UUID getId() { return id; }
    public UUID getProjectId() { return projectId; }
    public String getTitle() { return title; }
    public String getSummary() { return summary; }
    public String getProblemSolved() { return problemSolved; }
    public String getSedimentType() { return sedimentType; }
    public String getStatus() { return status; }
    public List<String> getSourceSegmentIds() { return List.copyOf(sourceSegmentIds); }
    public List<String> getEvidenceRefs() { return List.copyOf(evidenceRefs); }
    public String getDeveloperNotes() { return developerNotes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
