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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    name = "project_capability_facts",
    uniqueConstraints = @UniqueConstraint(name = "uk_capability_fact", columnNames = {"capability_id", "fact_id"}),
    indexes = {
        @Index(name = "idx_capability_fact_capability", columnList = "capability_id"),
        @Index(name = "idx_capability_fact_fact", columnList = "fact_id"),
        @Index(name = "idx_capability_fact_evolution", columnList = "source_evolution_id"),
        @Index(name = "idx_capability_fact_project", columnList = "project_id")
    }
)
public class ProjectCapabilityFact {
    @Id private UUID id;
    @Column(name = "project_id", nullable = false) private UUID projectId;
    @Column(name = "capability_id", nullable = false) private UUID capabilityId;
    @Column(name = "fact_id", nullable = false) private UUID factId;
    @Enumerated(EnumType.STRING)
    @Column(name = "relation_role", nullable = false, length = 20)
    private ProjectCapabilityRelationRole relationRole;
    @Column(name = "source_evolution_id", nullable = false) private UUID sourceEvolutionId;
    @Column(name = "linked_at", nullable = false) private Instant linkedAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected ProjectCapabilityFact() {
    }
    public ProjectCapabilityFact(UUID projectId, UUID capabilityId, UUID factId, ProjectCapabilityRelationRole role, UUID evolutionId) {
        this.id = UUID.randomUUID(); this.projectId = projectId; this.capabilityId = capabilityId; this.factId = factId;
        this.relationRole = role; this.sourceEvolutionId = evolutionId; this.linkedAt = Instant.now();
    }
    @PrePersist void prePersist() { Instant now = Instant.now(); if (id == null) id = UUID.randomUUID(); if (linkedAt == null) linkedAt = now; if (createdAt == null) createdAt = now; }
    public UUID getId() { return id; }
    public UUID getProjectId() { return projectId; }
    public UUID getCapabilityId() { return capabilityId; }
    public UUID getFactId() { return factId; }
    public ProjectCapabilityRelationRole getRelationRole() { return relationRole; }
    public UUID getSourceEvolutionId() { return sourceEvolutionId; }
    public Instant getLinkedAt() { return linkedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
