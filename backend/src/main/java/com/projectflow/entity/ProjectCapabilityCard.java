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
@Table(name = "project_capability_cards")
public class ProjectCapabilityCard {
    @Id
    private UUID id;
    @Column(name = "project_id", nullable = false)
    private UUID projectId;
    @Column(nullable = false, length = 180)
    private String name = "";
    @Column(columnDefinition = "text")
    private String summary = "";
    @Column(name = "problem_solved", columnDefinition = "text")
    private String problemSolved = "";
    @Column(name = "feature_entry", columnDefinition = "text")
    private String featureEntry = "";
    @Convert(converter = StringListConverter.class)
    @Column(name = "source_refs", columnDefinition = "text")
    private List<String> sourceRefs = new ArrayList<>();
    @Convert(converter = StringListConverter.class)
    @Column(name = "evidence_refs", columnDefinition = "text")
    private List<String> evidenceRefs = new ArrayList<>();
    @Column(name = "readme_expression", columnDefinition = "text")
    private String readmeExpression = "";
    @Column(name = "resume_expression", columnDefinition = "text")
    private String resumeExpression = "";
    @Column(name = "interview_expression", columnDefinition = "text")
    private String interviewExpression = "";
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CapabilityCardStatus status = CapabilityCardStatus.CANDIDATE;
    @Column(name = "generation_mode", length = 40)
    private String generationMode = "LOCAL_RULE";
    @Column(name = "model_provider", length = 160)
    private String modelProvider = "";
    @Column(name = "fallback_reason", columnDefinition = "text")
    private String fallbackReason = "";
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProjectCapabilityCard() {
    }

    public ProjectCapabilityCard(UUID projectId) {
        this.id = UUID.randomUUID();
        this.projectId = projectId;
    }

    @PrePersist
    void prePersist() { createdAt = updatedAt = Instant.now(); }
    @PreUpdate
    void preUpdate() { updatedAt = Instant.now(); }

    public void update(
        String cardName, String cardSummary, String problem, String entry, List<String> sources, List<String> evidence,
        String readme, String resume, String interview, String mode, String provider, String fallback
    ) {
        name = safe(cardName);
        summary = safe(cardSummary);
        problemSolved = safe(problem);
        featureEntry = safe(entry);
        sourceRefs = copy(sources);
        evidenceRefs = copy(evidence);
        readmeExpression = safe(readme);
        resumeExpression = safe(resume);
        interviewExpression = safe(interview);
        generationMode = safe(mode);
        modelProvider = safe(provider);
        fallbackReason = safe(fallback);
        status = evidenceRefs.isEmpty() ? CapabilityCardStatus.NEEDS_EVIDENCE : CapabilityCardStatus.CANDIDATE;
    }

    public void confirm() { status = evidenceRefs.isEmpty() ? CapabilityCardStatus.NEEDS_EVIDENCE : CapabilityCardStatus.CONFIRMED; }
    public void ignore() { status = CapabilityCardStatus.IGNORED; }

    public UUID getId() { return id; }
    public UUID getProjectId() { return projectId; }
    public String getName() { return name; }
    public String getSummary() { return summary; }
    public String getProblemSolved() { return problemSolved; }
    public String getFeatureEntry() { return featureEntry; }
    public List<String> getSourceRefs() { return List.copyOf(sourceRefs); }
    public List<String> getEvidenceRefs() { return List.copyOf(evidenceRefs); }
    public String getReadmeExpression() { return readmeExpression; }
    public String getResumeExpression() { return resumeExpression; }
    public String getInterviewExpression() { return interviewExpression; }
    public CapabilityCardStatus getStatus() { return status; }
    public String getGenerationMode() { return generationMode; }
    public String getModelProvider() { return modelProvider; }
    public String getFallbackReason() { return fallbackReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    private static String safe(String value) { return value == null ? "" : value.trim(); }
    private static List<String> copy(List<String> values) { return values == null ? new ArrayList<>() : new ArrayList<>(values); }
}
