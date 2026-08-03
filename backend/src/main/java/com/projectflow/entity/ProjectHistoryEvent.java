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

/**
 * Source-backed history metadata. An event preserves what a bounded source exposed;
 * it is not a second fact table and never becomes a ProjectFact automatically.
 */
@Entity
@Table(
    name = "project_history_events",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_project_history_event_key",
        columnNames = {"project_id", "stable_event_key"}
    ),
    indexes = {
        @Index(name = "idx_history_event_project_time", columnList = "project_id,occurred_at"),
        @Index(name = "idx_history_event_project_source", columnList = "project_id,source_type"),
        @Index(name = "idx_history_event_project_category", columnList = "project_id,event_category"),
        @Index(name = "idx_history_event_project_rewrite", columnList = "project_id,rewrite_state")
    }
)
public class ProjectHistoryEvent {
    public enum SourceType {
        GIT,
        GITHUB,
        FILESYSTEM,
        PROJECT_FACT,
        AGENT_RESULT,
        DOCUMENT,
        USER,
        EXTERNAL
    }

    public enum Scope {
        CURRENT,
        HISTORICAL,
        UNKNOWN
    }

    public enum Category {
        COMMIT,
        MERGE,
        PULL_REQUEST,
        ISSUE,
        TAG,
        FILE_CHANGE,
        DOCUMENT_VERSION,
        AGENT_RESULT,
        VALIDATION,
        USER_DECLARATION,
        PROJECT_FACT,
        EXTERNAL
    }

    public enum Transition {
        CREATED,
        MODIFIED,
        REMOVED,
        RESTORED,
        RENAMED,
        MOVED,
        REPLACED,
        SPLIT,
        MERGED,
        REVERTED,
        REAPPLIED,
        UNKNOWN_TRANSITION
    }

    public enum Authority {
        SOURCE_BACKED,
        FACTUAL_SOURCE,
        DECLARED,
        PROCESS_EVIDENCE,
        INFERRED_NON_AUTHORITATIVE,
        UNKNOWN
    }

    public enum RewriteState {
        CURRENT,
        STALE,
        INVALIDATED
    }

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "stable_event_key", nullable = false, length = 64)
    private String stableEventKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 30)
    private SourceType sourceType;

    @Column(name = "source_identity", nullable = false, length = 500)
    private String sourceIdentity;

    @Column(name = "source_revision", nullable = false, length = 180)
    private String sourceRevision;

    @Column(name = "project_revision", nullable = false, length = 180)
    private String projectRevision;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "effective_at")
    private Instant effectiveAt;

    @Column(name = "actor_label", length = 200)
    private String actorLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "history_scope", nullable = false, length = 20)
    private Scope scope;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_category", nullable = false, length = 30)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(name = "transition_type", nullable = false, length = 30)
    private Transition transition;

    @Column(name = "safe_source_label", nullable = false, columnDefinition = "text")
    private String safeSourceLabel;

    @Column(name = "affected_paths_json", nullable = false, columnDefinition = "text")
    private String affectedPathsJson;

    @Column(name = "subject_keys_json", nullable = false, columnDefinition = "text")
    private String subjectKeysJson;

    @Column(name = "evidence_refs_json", nullable = false, columnDefinition = "text")
    private String evidenceRefsJson;

    @Column(name = "relation_refs_json", nullable = false, columnDefinition = "text")
    private String relationRefsJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Authority authority;

    @Enumerated(EnumType.STRING)
    @Column(name = "epistemic_status", nullable = false, length = 30)
    private ProjectFactEpistemicStatus epistemicStatus;

    @Column(name = "coverage_json", nullable = false, columnDefinition = "text")
    private String coverageJson;

    @Column(name = "limitations_json", nullable = false, columnDefinition = "text")
    private String limitationsJson;

    @Column(name = "raw_source_deep_link", length = 1000)
    private String rawSourceDeepLink;

    @Enumerated(EnumType.STRING)
    @Column(name = "rewrite_state", nullable = false, length = 20)
    private RewriteState rewriteState;

    @Column(name = "payload_hash", nullable = false, length = 64)
    private String payloadHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProjectHistoryEvent() {
    }

    public ProjectHistoryEvent(UUID projectId, String stableEventKey) {
        this.id = UUID.randomUUID();
        this.projectId = projectId;
        this.stableEventKey = stableEventKey;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (id == null) id = UUID.randomUUID();
        if (scope == null) scope = Scope.UNKNOWN;
        if (category == null) category = Category.EXTERNAL;
        if (transition == null) transition = Transition.UNKNOWN_TRANSITION;
        if (authority == null) authority = Authority.UNKNOWN;
        if (epistemicStatus == null) epistemicStatus = ProjectFactEpistemicStatus.UNKNOWN;
        if (rewriteState == null) rewriteState = RewriteState.CURRENT;
        if (occurredAt == null) occurredAt = now;
        if (effectiveAt == null) effectiveAt = occurredAt;
        affectedPathsJson = json(affectedPathsJson);
        subjectKeysJson = json(subjectKeysJson);
        evidenceRefsJson = json(evidenceRefsJson);
        relationRefsJson = json(relationRefsJson);
        coverageJson = objectJson(coverageJson);
        limitationsJson = json(limitationsJson);
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public void replace(
        SourceType sourceType,
        String sourceIdentity,
        String sourceRevision,
        String projectRevision,
        Instant occurredAt,
        Instant effectiveAt,
        String actorLabel,
        Scope scope,
        Category category,
        Transition transition,
        String safeSourceLabel,
        String affectedPathsJson,
        String subjectKeysJson,
        String evidenceRefsJson,
        String relationRefsJson,
        Authority authority,
        ProjectFactEpistemicStatus epistemicStatus,
        String coverageJson,
        String limitationsJson,
        String rawSourceDeepLink,
        String payloadHash
    ) {
        this.sourceType = sourceType;
        this.sourceIdentity = text(sourceIdentity);
        this.sourceRevision = text(sourceRevision);
        this.projectRevision = text(projectRevision);
        this.occurredAt = occurredAt;
        this.effectiveAt = effectiveAt == null ? occurredAt : effectiveAt;
        this.actorLabel = text(actorLabel);
        this.scope = scope;
        this.category = category;
        this.transition = transition;
        this.safeSourceLabel = text(safeSourceLabel);
        this.affectedPathsJson = json(affectedPathsJson);
        this.subjectKeysJson = json(subjectKeysJson);
        this.evidenceRefsJson = json(evidenceRefsJson);
        this.relationRefsJson = json(relationRefsJson);
        this.authority = authority;
        this.epistemicStatus = epistemicStatus;
        this.coverageJson = objectJson(coverageJson);
        this.limitationsJson = json(limitationsJson);
        this.rawSourceDeepLink = text(rawSourceDeepLink);
        this.payloadHash = text(payloadHash);
        this.rewriteState = RewriteState.CURRENT;
    }

    public void markRewriteState(RewriteState rewriteState) {
        this.rewriteState = rewriteState == null ? RewriteState.STALE : rewriteState;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private static String json(String value) {
        String safe = text(value);
        return safe.isBlank() ? "[]" : safe;
    }

    private static String objectJson(String value) {
        String safe = text(value);
        return safe.isBlank() ? "{}" : safe;
    }

    public UUID getId() { return id; }
    public UUID getProjectId() { return projectId; }
    public String getStableEventKey() { return stableEventKey; }
    public SourceType getSourceType() { return sourceType; }
    public String getSourceIdentity() { return sourceIdentity; }
    public String getSourceRevision() { return sourceRevision; }
    public String getProjectRevision() { return projectRevision; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getEffectiveAt() { return effectiveAt; }
    public String getActorLabel() { return actorLabel; }
    public Scope getScope() { return scope; }
    public Category getCategory() { return category; }
    public Transition getTransition() { return transition; }
    public String getSafeSourceLabel() { return safeSourceLabel; }
    public String getAffectedPathsJson() { return affectedPathsJson; }
    public String getSubjectKeysJson() { return subjectKeysJson; }
    public String getEvidenceRefsJson() { return evidenceRefsJson; }
    public String getRelationRefsJson() { return relationRefsJson; }
    public Authority getAuthority() { return authority; }
    public ProjectFactEpistemicStatus getEpistemicStatus() { return epistemicStatus; }
    public String getCoverageJson() { return coverageJson; }
    public String getLimitationsJson() { return limitationsJson; }
    public String getRawSourceDeepLink() { return rawSourceDeepLink; }
    public RewriteState getRewriteState() { return rewriteState; }
    public String getPayloadHash() { return payloadHash; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
