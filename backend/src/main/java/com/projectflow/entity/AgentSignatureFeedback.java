package com.projectflow.entity;

import java.time.Instant;
import java.util.UUID;

import com.projectflow.dto.V2ProjectDtos.AgentSignatureFeedbackResponse;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "agent_signature_feedback")
public class AgentSignatureFeedback {
    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "agent_name", nullable = false, length = 180)
    private String agentName;

    @Column(name = "original_agent_type", nullable = false, length = 40)
    private String originalAgentType;

    @Column(name = "corrected_agent_type", nullable = false, length = 40)
    private String correctedAgentType;

    @Column(name = "corrected_task_intent", columnDefinition = "text")
    private String correctedTaskIntent;

    @Column(nullable = false, length = 40)
    private String scope = "PROJECT";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AgentSignatureFeedback() {
    }

    public AgentSignatureFeedback(UUID projectId, String agentName) {
        this.id = UUID.randomUUID();
        this.projectId = projectId;
        this.agentName = nonBlank(agentName, "Unknown");
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

    public void update(String originalAgentType, String correctedAgentType, String correctedTaskIntent) {
        this.originalAgentType = nonBlank(originalAgentType, "UNKNOWN");
        this.correctedAgentType = nonBlank(correctedAgentType, "UNKNOWN");
        this.correctedTaskIntent = correctedTaskIntent == null ? "" : correctedTaskIntent.trim();
    }

    public AgentSignatureFeedbackResponse toResponse() {
        return new AgentSignatureFeedbackResponse(
            id,
            projectId,
            agentName,
            originalAgentType,
            correctedAgentType,
            correctedTaskIntent,
            scope,
            createdAt,
            updatedAt
        );
    }

    public String getCorrectedAgentType() {
        return correctedAgentType;
    }

    public String getCorrectedTaskIntent() {
        return correctedTaskIntent;
    }

    private static String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
