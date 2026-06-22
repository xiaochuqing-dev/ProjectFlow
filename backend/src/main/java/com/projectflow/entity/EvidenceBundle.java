package com.projectflow.entity;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "evidence_bundles")
public class EvidenceBundle {
    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "work_session_id", nullable = false, unique = true)
    private UUID workSessionId;

    @Column(name = "agent_type", nullable = false, length = 40)
    private String agentType = "UNKNOWN";

    @Column(name = "task_intent", nullable = false, columnDefinition = "text")
    private String taskIntent = "";

    @Column(name = "branch_name", length = 180)
    private String branchName = "";

    @Column(name = "attribution_confidence", nullable = false, length = 40)
    private String attributionConfidence = "MEDIUM";

    @Column(name = "changed_files", nullable = false)
    private int changedFiles;

    @Column(name = "added_lines", nullable = false)
    private int addedLines;

    @Column(name = "deleted_lines", nullable = false)
    private int deletedLines;

    @Column(name = "file_paths", columnDefinition = "text")
    private String files = "";

    @Column(name = "objective_evidence", columnDefinition = "text")
    private String objectiveEvidence = "";

    @Column(name = "agent_claims", columnDefinition = "text")
    private String agentClaims = "";

    @Column(name = "sources", columnDefinition = "text")
    private String sources = "";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected EvidenceBundle() {
    }

    public EvidenceBundle(UUID projectId, UUID workSessionId) {
        this.id = UUID.randomUUID();
        this.projectId = projectId;
        this.workSessionId = workSessionId;
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

    public void updateFromWorkSession(
        String agentType,
        String taskIntent,
        String branchName,
        String attributionConfidence,
        int changedFiles,
        int addedLines,
        int deletedLines,
        List<String> files,
        List<String> evidence,
        String baseCommit
    ) {
        this.agentType = nonBlank(agentType, "UNKNOWN");
        this.taskIntent = nonBlank(taskIntent, "");
        this.branchName = nonBlank(branchName, "");
        this.attributionConfidence = nonBlank(attributionConfidence, "MEDIUM");
        this.changedFiles = changedFiles;
        this.addedLines = addedLines;
        this.deletedLines = deletedLines;
        this.files = joinLines(files);
        this.objectiveEvidence = joinLines(evidence);
        this.agentClaims = "";
        this.sources = "GIT_EVIDENCE\t%s\t%d files, +%d/-%d lines".formatted(
            nonBlank(baseCommit, branchName),
            changedFiles,
            addedLines,
            deletedLines
        );
    }

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getWorkSessionId() {
        return workSessionId;
    }

    public String getAgentType() {
        return agentType;
    }

    public String getTaskIntent() {
        return taskIntent;
    }

    public String getBranchName() {
        return branchName;
    }

    public String getAttributionConfidence() {
        return attributionConfidence;
    }

    public int getChangedFiles() {
        return changedFiles;
    }

    public int getAddedLines() {
        return addedLines;
    }

    public int getDeletedLines() {
        return deletedLines;
    }

    public List<String> getFiles() {
        return splitLines(files);
    }

    public List<String> getObjectiveEvidence() {
        return splitLines(objectiveEvidence);
    }

    public List<String> getAgentClaims() {
        return splitLines(agentClaims);
    }

    public List<String> getSourceLines() {
        return splitLines(sources);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    private static String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String joinLines(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return String.join("\n", values.stream()
            .filter(value -> value != null && !value.isBlank())
            .map(String::trim)
            .toList());
    }

    private static List<String> splitLines(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split("\\R"))
            .filter(line -> !line.isBlank())
            .toList();
    }
}
