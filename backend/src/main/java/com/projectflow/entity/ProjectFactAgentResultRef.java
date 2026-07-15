package com.projectflow.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    name = "project_fact_agent_result_refs",
    uniqueConstraints = @UniqueConstraint(name = "uk_project_fact_agent_result", columnNames = {"fact_id", "agent_result_ref"}),
    indexes = @Index(name = "idx_project_fact_agent_project_ref", columnList = "project_id,agent_result_ref")
)
public class ProjectFactAgentResultRef {
    @Id
    private UUID id;
    @Column(name = "project_id", nullable = false)
    private UUID projectId;
    @Column(name = "fact_id", nullable = false)
    private UUID factId;
    @Column(name = "agent_result_ref", nullable = false, length = 1200)
    private String agentResultRef;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ProjectFactAgentResultRef() {
    }

    public ProjectFactAgentResultRef(UUID projectId, UUID factId, String agentResultRef) {
        this.id = UUID.randomUUID();
        this.projectId = projectId;
        this.factId = factId;
        this.agentResultRef = agentResultRef == null ? "" : agentResultRef.trim();
        this.createdAt = Instant.now();
    }

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getProjectId() { return projectId; }
    public UUID getFactId() { return factId; }
    public String getAgentResultRef() { return agentResultRef == null ? "" : agentResultRef; }
    public Instant getCreatedAt() { return createdAt; }
}
