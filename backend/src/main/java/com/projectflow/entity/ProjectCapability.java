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
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

@Entity
@Table(
    name = "project_capabilities",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_capability_identity", columnNames = {"project_id", "stable_identity_key"}),
        @UniqueConstraint(name = "uk_capability_legacy_card", columnNames = {"project_id", "legacy_card_id"})
    },
    indexes = {
        @Index(name = "idx_capability_project_status", columnList = "project_id,status"),
        @Index(name = "idx_capability_project_maturity", columnList = "project_id,maturity_level"),
        @Index(name = "idx_capability_project_updated", columnList = "project_id,updated_at")
    }
)
public class ProjectCapability {
    @Id
    private UUID id;
    @Column(name = "project_id", nullable = false)
    private UUID projectId;
    @Column(name = "canonical_name", nullable = false, length = 200)
    private String canonicalName = "";
    @Convert(converter = StringListConverter.class)
    @Column(columnDefinition = "text")
    private List<String> aliases = new ArrayList<>();
    @Column(name = "current_summary", columnDefinition = "text")
    private String currentSummary = "";
    @Column(name = "problem_solved", columnDefinition = "text")
    private String problemSolved = "";
    @Column(name = "long_term_value", columnDefinition = "text")
    private String longTermValue = "";
    @Convert(converter = StringListConverter.class)
    @Column(name = "product_areas", columnDefinition = "text")
    private List<String> productAreas = new ArrayList<>();
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProjectCapabilityStatus status = ProjectCapabilityStatus.ACTIVE;
    @Enumerated(EnumType.STRING)
    @Column(name = "maturity_level", nullable = false, length = 40)
    private ProjectCapabilityMaturity maturityLevel = ProjectCapabilityMaturity.FORMING;
    @Column(name = "maturity_reason", length = 1000)
    private String maturityReason = "";
    @Column(name = "first_formed_at")
    private Instant firstFormedAt;
    @Column(name = "last_enhanced_at")
    private Instant lastEnhancedAt;
    @Column(name = "source_fact_count", nullable = false)
    private Integer sourceFactCount = 0;
    @Column(name = "source_batch_count", nullable = false)
    private Integer sourceBatchCount = 0;
    @Column(name = "distinct_commit_count", nullable = false)
    private Integer distinctCommitCount = 0;
    @Column(name = "evidence_count", nullable = false)
    private Integer evidenceCount = 0;
    @Column(name = "attention_fact_count", nullable = false)
    private Integer attentionFactCount = 0;
    @Column(name = "evolution_count", nullable = false)
    private Integer evolutionCount = 0;
    @Column(name = "stable_identity_key", nullable = false, length = 64)
    private String stableIdentityKey;
    @Column(name = "capability_fingerprint", nullable = false, length = 64)
    private String capabilityFingerprint;
    @Column(name = "current_version", nullable = false)
    private Integer currentVersion = 1;
    @Column(name = "generation_mode", nullable = false, length = 40)
    private String generationMode = "MODEL";
    @Column(name = "model_provider", length = 200)
    private String modelProvider = "";
    @Column(name = "model_name", length = 200)
    private String modelName = "";
    @Column(name = "latest_analysis_job_id")
    private UUID latestAnalysisJobId;
    @Column(name = "merged_into_capability_id")
    private UUID mergedIntoCapabilityId;
    @Column(name = "legacy_card_id")
    private UUID legacyCardId;
    @Column(name = "readme_expression", columnDefinition = "text")
    private String readmeExpression = "";
    @Column(name = "resume_expression", columnDefinition = "text")
    private String resumeExpression = "";
    @Column(name = "interview_expression", columnDefinition = "text")
    private String interviewExpression = "";
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private Long rowVersion;

    protected ProjectCapability() {
    }

    public ProjectCapability(UUID projectId, String stableIdentityKey, String capabilityFingerprint) {
        this.id = UUID.randomUUID();
        this.projectId = projectId;
        this.stableIdentityKey = required(stableIdentityKey);
        this.capabilityFingerprint = required(capabilityFingerprint);
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() { updatedAt = Instant.now(); }

    public void initialize(
        String name, String summary, String problem, String value, List<String> areas,
        Instant formedAt, String generationMode, String provider, String model, UUID jobId
    ) {
        canonicalName = safe(name);
        currentSummary = safe(summary);
        problemSolved = safe(problem);
        longTermValue = safe(value);
        productAreas = copy(areas);
        firstFormedAt = formedAt;
        lastEnhancedAt = formedAt;
        this.generationMode = fallback(generationMode, "MODEL");
        modelProvider = safe(provider);
        modelName = safe(model);
        latestAnalysisJobId = jobId;
    }

    public void enhance(
        String name, String summary, String problem, String value, List<String> areas,
        Instant enhancedAt, String provider, String model, UUID jobId, boolean semanticChange
    ) {
        String previousName = canonicalName;
        if (!safe(name).isBlank()) canonicalName = safe(name);
        if (!previousName.isBlank() && !previousName.equalsIgnoreCase(canonicalName)) addAlias(previousName);
        if (!safe(summary).isBlank()) currentSummary = safe(summary);
        if (!safe(problem).isBlank()) problemSolved = safe(problem);
        if (!safe(value).isBlank()) longTermValue = safe(value);
        if (areas != null && !areas.isEmpty()) productAreas = copy(areas);
        if (enhancedAt != null && (lastEnhancedAt == null || enhancedAt.isAfter(lastEnhancedAt))) lastEnhancedAt = enhancedAt;
        if (semanticChange) currentVersion = getCurrentVersion() + 1;
        modelProvider = safe(provider);
        modelName = safe(model);
        latestAnalysisJobId = jobId;
    }

    public void updateExpressions(String readme, String resume, String interview) {
        if (!safe(readme).isBlank()) readmeExpression = safe(readme);
        if (!safe(resume).isBlank()) resumeExpression = safe(resume);
        if (!safe(interview).isBlank()) interviewExpression = safe(interview);
    }

    public void addAlias(String alias) {
        String normalized = safe(alias);
        if (normalized.isBlank() || normalized.equalsIgnoreCase(canonicalName)) return;
        LinkedHashSet<String> values = new LinkedHashSet<>(aliases == null ? List.of() : aliases);
        values.add(normalized);
        aliases = new ArrayList<>(values);
    }

    public void markLegacy(UUID cardId) { legacyCardId = cardId; generationMode = "LEGACY_SEED"; }

    public void markMerged(UUID targetId) {
        if (targetId == null || targetId.equals(id)) throw new IllegalArgumentException("Invalid capability merge target");
        status = ProjectCapabilityStatus.MERGED;
        mergedIntoCapabilityId = targetId;
    }

    public void updateStatistics(
        int facts, int batches, int commits, int evidence, int attention, int evolutions,
        ProjectCapabilityMaturity maturity, String reason, Instant first, Instant latest
    ) {
        sourceFactCount = Math.max(0, facts);
        sourceBatchCount = Math.max(0, batches);
        distinctCommitCount = Math.max(0, commits);
        evidenceCount = Math.max(0, evidence);
        attentionFactCount = Math.max(0, attention);
        evolutionCount = Math.max(0, evolutions);
        maturityLevel = maturity == null ? ProjectCapabilityMaturity.FORMING : maturity;
        maturityReason = safe(reason);
        if (first != null) firstFormedAt = first;
        if (latest != null) lastEnhancedAt = latest;
    }

    public UUID getId() { return id; }
    public UUID getProjectId() { return projectId; }
    public String getCanonicalName() { return safe(canonicalName); }
    public List<String> getAliases() { return aliases == null ? List.of() : List.copyOf(aliases); }
    public String getCurrentSummary() { return safe(currentSummary); }
    public String getProblemSolved() { return safe(problemSolved); }
    public String getLongTermValue() { return safe(longTermValue); }
    public List<String> getProductAreas() { return productAreas == null ? List.of() : List.copyOf(productAreas); }
    public ProjectCapabilityStatus getStatus() { return status == null ? ProjectCapabilityStatus.ACTIVE : status; }
    public ProjectCapabilityMaturity getMaturityLevel() { return maturityLevel == null ? ProjectCapabilityMaturity.FORMING : maturityLevel; }
    public String getMaturityReason() { return safe(maturityReason); }
    public Instant getFirstFormedAt() { return firstFormedAt; }
    public Instant getLastEnhancedAt() { return lastEnhancedAt; }
    public int getSourceFactCount() { return value(sourceFactCount); }
    public int getSourceBatchCount() { return value(sourceBatchCount); }
    public int getDistinctCommitCount() { return value(distinctCommitCount); }
    public int getEvidenceCount() { return value(evidenceCount); }
    public int getAttentionFactCount() { return value(attentionFactCount); }
    public int getEvolutionCount() { return value(evolutionCount); }
    public String getStableIdentityKey() { return safe(stableIdentityKey); }
    public String getCapabilityFingerprint() { return safe(capabilityFingerprint); }
    public int getCurrentVersion() { return currentVersion == null ? 1 : currentVersion; }
    public String getGenerationMode() { return safe(generationMode); }
    public String getModelProvider() { return safe(modelProvider); }
    public String getModelName() { return safe(modelName); }
    public UUID getLatestAnalysisJobId() { return latestAnalysisJobId; }
    public UUID getMergedIntoCapabilityId() { return mergedIntoCapabilityId; }
    public UUID getLegacyCardId() { return legacyCardId; }
    public String getReadmeExpression() { return safe(readmeExpression); }
    public String getResumeExpression() { return safe(resumeExpression); }
    public String getInterviewExpression() { return safe(interviewExpression); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    private static int value(Integer value) { return value == null ? 0 : value; }
    private static String required(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Capability identity is required");
        return value.trim();
    }
    private static String safe(String value) { return value == null ? "" : value.trim(); }
    private static String fallback(String value, String fallback) { return safe(value).isBlank() ? fallback : safe(value); }
    private static List<String> copy(List<String> values) {
        return values == null ? new ArrayList<>() : new ArrayList<>(new LinkedHashSet<>(values.stream().filter(v -> v != null && !v.isBlank()).map(String::trim).toList()));
    }
}
