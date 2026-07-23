package com.projectflow.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.projectflow.support.StringListConverter;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    name = "project_evolution_bridges",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_evolution_bridge_fingerprint",
        columnNames = {"project_id", "bridge_fingerprint"}
    ),
    indexes = @Index(name = "idx_evolution_bridge_project_time", columnList = "project_id,occurred_at")
)
public class ProjectEvolutionBridge {
    @Id private UUID id;
    @Column(name = "project_id", nullable = false) private UUID projectId;
    @Column(name = "bridge_fingerprint", nullable = false, length = 64) private String bridgeFingerprint;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;
    @Column(name = "before_revision", nullable = false, length = 64) private String beforeRevision;
    @Column(name = "after_revision", nullable = false, length = 64) private String afterRevision;
    @Column(name = "before_structure_version", nullable = false, length = 40) private String beforeStructureVersion;
    @Column(name = "after_structure_version", nullable = false, length = 40) private String afterStructureVersion;
    @Column(name = "meaningful_change", nullable = false, columnDefinition = "text") private String meaningfulChange;
    @Column(name = "affected_area_id", nullable = false, length = 80) private String affectedAreaId;
    @Column(name = "affected_area_label", nullable = false, length = 200) private String affectedAreaLabel;
    @Column(name = "before_state", nullable = false, columnDefinition = "text") private String beforeState;
    @Column(name = "after_state", nullable = false, columnDefinition = "text") private String afterState;
    @Column(name = "epistemic_status", nullable = false, length = 20) private String epistemicStatus;
    @Column(nullable = false, length = 20) private String confidence;
    @Convert(converter = StringListConverter.class)
    @Column(name = "source_fact_ids", nullable = false, columnDefinition = "text")
    private List<String> sourceFactIds = new ArrayList<>();
    @Convert(converter = StringListConverter.class)
    @Column(name = "source_commit_refs", nullable = false, columnDefinition = "text")
    private List<String> sourceCommitRefs = new ArrayList<>();
    @Convert(converter = StringListConverter.class)
    @Column(name = "changed_paths", nullable = false, columnDefinition = "text")
    private List<String> changedPaths = new ArrayList<>();
    @Convert(converter = StringListConverter.class)
    @Column(name = "evidence_refs", nullable = false, columnDefinition = "text")
    private List<String> evidenceRefs = new ArrayList<>();
    @Column(name = "generation_version", nullable = false) private Integer generationVersion = 1;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected ProjectEvolutionBridge() {
    }

    public ProjectEvolutionBridge(UUID projectId, String fingerprint) {
        this.id = UUID.randomUUID();
        this.projectId = projectId;
        this.bridgeFingerprint = required(fingerprint);
    }

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
        if (occurredAt == null) occurredAt = createdAt;
    }

    public void describe(
        Instant occurredAt,
        String beforeRevision,
        String afterRevision,
        String beforeStructureVersion,
        String afterStructureVersion,
        String meaningfulChange,
        String affectedAreaId,
        String affectedAreaLabel,
        String beforeState,
        String afterState,
        String epistemicStatus,
        String confidence,
        List<String> sourceFactIds,
        List<String> sourceCommitRefs,
        List<String> changedPaths,
        List<String> evidenceRefs
    ) {
        this.occurredAt = occurredAt == null ? Instant.now() : occurredAt;
        this.beforeRevision = required(beforeRevision);
        this.afterRevision = required(afterRevision);
        this.beforeStructureVersion = required(beforeStructureVersion);
        this.afterStructureVersion = required(afterStructureVersion);
        this.meaningfulChange = required(meaningfulChange);
        this.affectedAreaId = required(affectedAreaId);
        this.affectedAreaLabel = required(affectedAreaLabel);
        this.beforeState = required(beforeState);
        this.afterState = required(afterState);
        this.epistemicStatus = required(epistemicStatus);
        this.confidence = required(confidence);
        this.sourceFactIds = copy(sourceFactIds);
        this.sourceCommitRefs = copy(sourceCommitRefs);
        this.changedPaths = copy(changedPaths);
        this.evidenceRefs = copy(evidenceRefs);
    }

    public UUID getId() { return id; }
    public UUID getProjectId() { return projectId; }
    public String getBridgeFingerprint() { return bridgeFingerprint; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getBeforeRevision() { return beforeRevision; }
    public String getAfterRevision() { return afterRevision; }
    public String getBeforeStructureVersion() { return beforeStructureVersion; }
    public String getAfterStructureVersion() { return afterStructureVersion; }
    public String getMeaningfulChange() { return meaningfulChange; }
    public String getAffectedAreaId() { return affectedAreaId; }
    public String getAffectedAreaLabel() { return affectedAreaLabel; }
    public String getBeforeState() { return beforeState; }
    public String getAfterState() { return afterState; }
    public String getEpistemicStatus() { return epistemicStatus; }
    public String getConfidence() { return confidence; }
    public List<String> getSourceFactIds() { return immutable(sourceFactIds); }
    public List<String> getSourceCommitRefs() { return immutable(sourceCommitRefs); }
    public List<String> getChangedPaths() { return immutable(changedPaths); }
    public List<String> getEvidenceRefs() { return immutable(evidenceRefs); }
    public int getGenerationVersion() { return generationVersion == null ? 1 : generationVersion; }
    public Instant getCreatedAt() { return createdAt; }

    private static String required(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Evolution bridge field is required");
        return value.trim();
    }
    private static List<String> copy(List<String> values) {
        return values == null ? new ArrayList<>() : new ArrayList<>(values);
    }
    private static List<String> immutable(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
