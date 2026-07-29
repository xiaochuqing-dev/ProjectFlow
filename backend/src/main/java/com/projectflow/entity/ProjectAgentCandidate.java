package com.projectflow.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.projectflow.support.StringListConverter;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(
    name = "project_agent_candidates",
    indexes = {
        @Index(name = "idx_agent_candidate_project_created", columnList = "project_id,created_at"),
        @Index(name = "idx_agent_candidate_project_validation", columnList = "project_id,validation_status")
    }
)
public class ProjectAgentCandidate {
    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "candidate_type", nullable = false, length = 40)
    private String candidateType;

    @Column(name = "assertion_text", nullable = false, columnDefinition = "text")
    private String assertion;

    @Enumerated(EnumType.STRING)
    @Column(name = "epistemic_status", nullable = false, length = 30)
    private ProjectFactEpistemicStatus epistemicStatus;

    @Convert(converter = StringListConverter.class)
    @Column(name = "evidence_refs", columnDefinition = "text")
    private List<String> evidenceRefs = new ArrayList<>();

    @Column(length = 30)
    private String currentness = "UNKNOWN";

    @Column(name = "source_revision", length = 180)
    private String sourceRevision = "";

    @Convert(converter = StringListConverter.class)
    @Column(columnDefinition = "text")
    private List<String> limitations = new ArrayList<>();

    @Column(name = "source_agent_id", nullable = false, length = 160)
    private String sourceAgentId;

    @Column(name = "validation_status", nullable = false, length = 50)
    private String validationStatus = "PENDING_ENGINEERING_VALIDATION";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ProjectAgentCandidate() {
    }

    public ProjectAgentCandidate(
        UUID projectId,
        String candidateType,
        String assertion,
        ProjectFactEpistemicStatus epistemicStatus,
        List<String> evidenceRefs,
        String currentness,
        String sourceRevision,
        List<String> limitations,
        String sourceAgentId
    ) {
        if (epistemicStatus == null || epistemicStatus.isStrongFact()) {
            throw new IllegalArgumentException("Agent candidates cannot use OBSERVED or VERIFIED");
        }
        this.id = UUID.randomUUID();
        this.projectId = java.util.Objects.requireNonNull(projectId, "projectId");
        this.candidateType = normalizeType(candidateType);
        this.assertion = required(assertion, "assertion", 4_000);
        this.epistemicStatus = epistemicStatus;
        this.evidenceRefs = evidenceRefs == null ? new ArrayList<>() : new ArrayList<>(evidenceRefs);
        this.currentness = bounded(currentness, 30, "UNKNOWN");
        this.sourceRevision = bounded(sourceRevision, 180, "");
        this.limitations = limitations == null ? new ArrayList<>() : new ArrayList<>(limitations);
        this.sourceAgentId = required(sourceAgentId, "sourceAgentId", 160);
        this.createdAt = Instant.now();
    }

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
        if (validationStatus == null || validationStatus.isBlank()) {
            validationStatus = "PENDING_ENGINEERING_VALIDATION";
        }
    }

    public UUID getId() { return id; }
    public UUID getProjectId() { return projectId; }
    public String getCandidateType() { return bounded(candidateType, 40, "ASSERTION"); }
    public String getAssertion() { return assertion == null ? "" : assertion; }
    public ProjectFactEpistemicStatus getEpistemicStatus() {
        return epistemicStatus == null ? ProjectFactEpistemicStatus.UNKNOWN : epistemicStatus;
    }
    public List<String> getEvidenceRefs() { return evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs); }
    public String getCurrentness() { return bounded(currentness, 30, "UNKNOWN"); }
    public String getSourceRevision() { return bounded(sourceRevision, 180, ""); }
    public List<String> getLimitations() { return limitations == null ? List.of() : List.copyOf(limitations); }
    public String getSourceAgentId() { return bounded(sourceAgentId, 160, "UNKNOWN_AGENT"); }
    public String getValidationStatus() { return bounded(validationStatus, 50, "PENDING_ENGINEERING_VALIDATION"); }
    public Instant getCreatedAt() { return createdAt; }

    private static String normalizeType(String value) {
        String normalized = bounded(value, 40, "ASSERTION").toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "ASSERTION", "EVIDENCE_LINK", "CORRECTION", "CONFLICT_REPORT", "USER_REVIEW_REQUEST" -> normalized;
            default -> throw new IllegalArgumentException("Unsupported candidate type");
        };
    }

    private static String required(String value, String field, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        String safe = value.strip();
        if (safe.length() > max) throw new IllegalArgumentException(field + " exceeds " + max + " characters");
        return safe;
    }

    private static String bounded(String value, int max, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        String safe = value.strip();
        return safe.length() <= max ? safe : safe.substring(0, max);
    }
}
