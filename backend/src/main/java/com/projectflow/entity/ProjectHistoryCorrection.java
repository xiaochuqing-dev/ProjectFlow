package com.projectflow.entity;

import java.time.Instant;
import java.util.List;
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
import jakarta.persistence.Version;

/**
 * Durable presentation overlay for Project History. This table intentionally
 * has no relationship that can mutate ProjectFact or ProjectHistoryEvent.
 */
@Entity
@Table(
    name = "project_history_corrections",
    indexes = {
        @Index(name = "idx_history_correction_project_updated", columnList = "project_id,updated_at"),
        @Index(name = "idx_history_correction_project_target", columnList = "project_id,target_type,target_id"),
        @Index(name = "idx_history_correction_project_status", columnList = "project_id,status")
    }
)
public class ProjectHistoryCorrection {
    public enum Status { ACTIVE, REVERTED, CONFLICT }

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "actor_user_id", nullable = false)
    private UUID actorUserId;

    @Column(name = "correction_type", nullable = false, length = 40)
    private String correctionType;

    @Column(name = "target_type", nullable = false, length = 30)
    private String targetType;

    @Column(name = "target_id", nullable = false, length = 180)
    private String targetId;

    @Column(name = "target_ids_json", nullable = false, columnDefinition = "text")
    private String targetIdsJson;

    @Column(name = "declared_title", columnDefinition = "text")
    private String declaredTitle;

    @Column(name = "declared_summary", columnDefinition = "text")
    private String declaredSummary;

    @Column(name = "declared_role", length = 30)
    private String declaredRole;

    @Column(name = "declared_chapter_id", length = 180)
    private String declaredChapterId;

    @Column(name = "secondary_declared_title", columnDefinition = "text")
    private String secondaryDeclaredTitle;

    @Column(name = "secondary_declared_summary", columnDefinition = "text")
    private String secondaryDeclaredSummary;

    @Column(name = "before_presentation_revision", nullable = false, length = 180)
    private String beforePresentationRevision;

    @Column(name = "source_fingerprint", nullable = false, length = 64)
    private String sourceFingerprint;

    @Column(name = "target_membership_fingerprint", length = 64)
    private String targetMembershipFingerprint;

    @Column(name = "target_membership_refs_json", columnDefinition = "text")
    private String targetMembershipRefsJson;

    @Column(name = "automatic_presentation_fingerprint", length = 64)
    private String automaticPresentationFingerprint;

    @Column(name = "conflict_reason", columnDefinition = "text")
    private String conflictReason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "replaced_by_id")
    private UUID replacedById;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected ProjectHistoryCorrection() {
    }

    public ProjectHistoryCorrection(
        UUID projectId,
        UUID actorUserId,
        String correctionType,
        String targetType,
        String targetId,
        String targetIdsJson,
        String declaredTitle,
        String declaredSummary,
        String declaredRole,
        String declaredChapterId,
        String beforePresentationRevision,
        String sourceFingerprint
    ) {
        this(projectId, actorUserId, correctionType, targetType, targetId, targetIdsJson, declaredTitle, declaredSummary,
            declaredRole, declaredChapterId, beforePresentationRevision, sourceFingerprint, "", "[]", "", "", "");
    }

    public ProjectHistoryCorrection(
        UUID projectId,
        UUID actorUserId,
        String correctionType,
        String targetType,
        String targetId,
        String targetIdsJson,
        String declaredTitle,
        String declaredSummary,
        String declaredRole,
        String declaredChapterId,
        String beforePresentationRevision,
        String sourceFingerprint,
        String targetMembershipFingerprint,
        String automaticPresentationFingerprint,
        String secondaryDeclaredTitle,
        String secondaryDeclaredSummary
    ) {
        this(projectId, actorUserId, correctionType, targetType, targetId, targetIdsJson, declaredTitle, declaredSummary,
            declaredRole, declaredChapterId, beforePresentationRevision, sourceFingerprint,
            targetMembershipFingerprint, "[]", automaticPresentationFingerprint,
            secondaryDeclaredTitle, secondaryDeclaredSummary);
    }

    public ProjectHistoryCorrection(
        UUID projectId,
        UUID actorUserId,
        String correctionType,
        String targetType,
        String targetId,
        String targetIdsJson,
        String declaredTitle,
        String declaredSummary,
        String declaredRole,
        String declaredChapterId,
        String beforePresentationRevision,
        String sourceFingerprint,
        String targetMembershipFingerprint,
        String targetMembershipRefsJson,
        String automaticPresentationFingerprint,
        String secondaryDeclaredTitle,
        String secondaryDeclaredSummary
    ) {
        this.id = UUID.randomUUID();
        this.projectId = projectId;
        this.actorUserId = actorUserId;
        this.correctionType = bounded(correctionType, 40, "EDIT_SUMMARY");
        this.targetType = bounded(targetType, 30, "STORY");
        this.targetId = bounded(targetId, 180, "");
        this.targetIdsJson = boundedJson(targetIdsJson);
        this.declaredTitle = bounded(declaredTitle, 8_000, "");
        this.declaredSummary = bounded(declaredSummary, 12_000, "");
        this.declaredRole = bounded(declaredRole, 30, "");
        this.declaredChapterId = bounded(declaredChapterId, 180, "");
        this.secondaryDeclaredTitle = bounded(secondaryDeclaredTitle, 8_000, "");
        this.secondaryDeclaredSummary = bounded(secondaryDeclaredSummary, 12_000, "");
        this.beforePresentationRevision = bounded(beforePresentationRevision, 180, "");
        this.sourceFingerprint = bounded(sourceFingerprint, 64, "");
        this.targetMembershipFingerprint = bounded(targetMembershipFingerprint, 64, "");
        this.targetMembershipRefsJson = boundedJson(targetMembershipRefsJson);
        this.automaticPresentationFingerprint = bounded(automaticPresentationFingerprint, 64, "");
        this.conflictReason = "";
        this.status = Status.ACTIVE;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (id == null) id = UUID.randomUUID();
        if (status == null) status = Status.ACTIVE;
        if (targetIdsJson == null || targetIdsJson.isBlank()) targetIdsJson = "[]";
        if (beforePresentationRevision == null) beforePresentationRevision = "";
        if (sourceFingerprint == null) sourceFingerprint = "";
        if (targetMembershipFingerprint == null) targetMembershipFingerprint = "";
        if (targetMembershipRefsJson == null || targetMembershipRefsJson.isBlank()) targetMembershipRefsJson = "[]";
        if (automaticPresentationFingerprint == null) automaticPresentationFingerprint = "";
        if (secondaryDeclaredTitle == null) secondaryDeclaredTitle = "";
        if (secondaryDeclaredSummary == null) secondaryDeclaredSummary = "";
        if (conflictReason == null) conflictReason = "";
        createdAt = createdAt == null ? now : createdAt;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() { updatedAt = Instant.now(); }

    public void markReverted(UUID replacementId) {
        status = Status.REVERTED;
        replacedById = replacementId;
    }

    public void markConflict(String reason) {
        status = Status.CONFLICT;
        conflictReason = bounded(reason, 2_000, "无法安全应用展示修正");
    }

    public UUID getId() { return id; }
    public UUID getProjectId() { return projectId; }
    public UUID getActorUserId() { return actorUserId; }
    public String getCorrectionType() { return correctionType == null ? "" : correctionType; }
    public String getTargetType() { return targetType == null ? "" : targetType; }
    public String getTargetId() { return targetId == null ? "" : targetId; }
    public String getTargetIdsJson() { return targetIdsJson == null ? "[]" : targetIdsJson; }
    public String getDeclaredTitle() { return declaredTitle == null ? "" : declaredTitle; }
    public String getDeclaredSummary() { return declaredSummary == null ? "" : declaredSummary; }
    public String getDeclaredRole() { return declaredRole == null ? "" : declaredRole; }
    public String getDeclaredChapterId() { return declaredChapterId == null ? "" : declaredChapterId; }
    public String getSecondaryDeclaredTitle() { return secondaryDeclaredTitle == null ? "" : secondaryDeclaredTitle; }
    public String getSecondaryDeclaredSummary() { return secondaryDeclaredSummary == null ? "" : secondaryDeclaredSummary; }
    public String getBeforePresentationRevision() { return beforePresentationRevision == null ? "" : beforePresentationRevision; }
    public String getSourceFingerprint() { return sourceFingerprint == null ? "" : sourceFingerprint; }
    public String getTargetMembershipFingerprint() { return targetMembershipFingerprint == null ? "" : targetMembershipFingerprint; }
    public String getTargetMembershipRefsJson() { return targetMembershipRefsJson == null ? "[]" : targetMembershipRefsJson; }
    public String getAutomaticPresentationFingerprint() { return automaticPresentationFingerprint == null ? "" : automaticPresentationFingerprint; }
    public String getConflictReason() { return conflictReason == null ? "" : conflictReason; }
    public Status getStatus() { return status == null ? Status.CONFLICT : status; }
    public UUID getReplacedById() { return replacedById; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }

    private static String bounded(String value, int max, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        String safe = value.trim();
        return safe.length() <= max ? safe : safe.substring(0, max);
    }

    private static String boundedJson(String value) {
        String safe = value == null || value.isBlank() ? "[]" : value.trim();
        return safe;
    }
}
