package com.projectflow.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
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
    name = "project_capability_fact_coverage",
    uniqueConstraints = @UniqueConstraint(name = "uk_capability_fact_coverage", columnNames = {"project_id", "fact_id"}),
    indexes = @Index(name = "idx_capability_coverage_classification", columnList = "project_id,classification")
)
public class ProjectCapabilityFactCoverage {
    @Id private UUID id;
    @Column(name = "project_id", nullable = false) private UUID projectId;
    @Column(name = "fact_id", nullable = false) private UUID factId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40) private ProjectCapabilityFactClassification classification;
    @Column(name = "capability_id") private UUID capabilityId;
    @Column(name = "source_evolution_id") private UUID sourceEvolutionId;
    @Column(name = "reason", length = 1000) private String reason = "";
    @Column(name = "source_fingerprint", nullable = false, length = 64) private String sourceFingerprint;
    @Column(name = "source_fact_updated_at") private Instant sourceFactUpdatedAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected ProjectCapabilityFactCoverage() {
    }
    public ProjectCapabilityFactCoverage(UUID projectId, UUID factId, String fingerprint, Instant factUpdatedAt) {
        this.id = UUID.randomUUID(); this.projectId = projectId; this.factId = factId; this.sourceFingerprint = fingerprint; this.sourceFactUpdatedAt = factUpdatedAt;
    }
    @PrePersist void prePersist() { Instant now=Instant.now(); if(id==null)id=UUID.randomUUID(); if(createdAt==null)createdAt=now; updatedAt=now; }
    @PreUpdate void preUpdate() { updatedAt=Instant.now(); }
    public void classify(ProjectCapabilityFactClassification value, UUID capabilityId, UUID evolutionId, String reason) {
        this.classification=value; this.capabilityId=capabilityId; this.sourceEvolutionId=evolutionId; this.reason=reason==null?"":reason.trim();
    }
    public void refreshSource(String fingerprint, Instant factUpdatedAt) { this.sourceFingerprint=fingerprint; this.sourceFactUpdatedAt=factUpdatedAt; }
    public UUID getId(){return id;} public UUID getProjectId(){return projectId;} public UUID getFactId(){return factId;}
    public ProjectCapabilityFactClassification getClassification(){return classification;} public UUID getCapabilityId(){return capabilityId;}
    public UUID getSourceEvolutionId(){return sourceEvolutionId;} public String getReason(){return reason==null?"":reason;}
    public String getSourceFingerprint(){return sourceFingerprint;} public Instant getSourceFactUpdatedAt(){return sourceFactUpdatedAt;} public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
}
