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
@Table(name = "work_sessions")
public class WorkSession {
    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "agent_type", nullable = false, length = 40)
    private String agentType = "UNKNOWN";

    @Column(name = "agent_name", nullable = false, length = 180)
    private String agentName = "Unknown";

    @Column(name = "task_intent", nullable = false, columnDefinition = "text")
    private String taskIntent = "";

    @Column(name = "branch_name", length = 180)
    private String branchName = "";

    @Column(name = "base_commit", length = 80)
    private String baseCommit = "";

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Column(name = "attribution_confidence", nullable = false, length = 40)
    private String attributionConfidence = "MEDIUM";

    @Column(name = "detection_method", nullable = false, length = 60)
    private String detectionMethod = "GIT_EVIDENCE";

    @Column(name = "changed_files", nullable = false)
    private int changedFiles;

    @Column(name = "added_lines", nullable = false)
    private int addedLines;

    @Column(name = "deleted_lines", nullable = false)
    private int deletedLines;

    @Column(name = "affected_modules", columnDefinition = "text")
    private String affectedModules = "";

    @Column(columnDefinition = "text")
    private String evidence = "";

    @Column(name = "file_paths", columnDefinition = "text")
    private String files = "";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WorkSession() {
    }

    public WorkSession(UUID id, UUID projectId) {
        this.id = id;
        this.projectId = projectId;
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

    public void updateFromCandidate(
        String agentType,
        String agentName,
        String taskIntent,
        String branchName,
        String baseCommit,
        Instant startTime,
        Instant endTime,
        String attributionConfidence,
        String detectionMethod,
        int changedFiles,
        int addedLines,
        int deletedLines,
        List<String> affectedModules,
        List<String> evidence,
        List<String> files
    ) {
        boolean userCorrected = "USER_CORRECTED".equals(this.detectionMethod);
        if (!userCorrected) {
            this.agentType = nonBlank(agentType, "UNKNOWN");
            this.agentName = nonBlank(agentName, "Unknown");
            this.taskIntent = nonBlank(taskIntent, "根据今日 Git 证据生成的候选工作会话");
            this.attributionConfidence = nonBlank(attributionConfidence, "MEDIUM");
            this.detectionMethod = nonBlank(detectionMethod, "GIT_EVIDENCE");
        }
        this.branchName = nonBlank(branchName, "");
        this.baseCommit = nonBlank(baseCommit, "");
        this.startTime = startTime == null ? Instant.now() : startTime;
        this.endTime = endTime == null ? Instant.now() : endTime;
        this.changedFiles = changedFiles;
        this.addedLines = addedLines;
        this.deletedLines = deletedLines;
        this.affectedModules = joinLines(affectedModules);
        this.evidence = joinLines(evidence);
        this.files = joinLines(files);
    }

    public void correctAttribution(String agentType, String taskIntent) {
        this.agentType = normalizeAgentType(agentType);
        if (taskIntent != null && !taskIntent.isBlank()) {
            this.taskIntent = taskIntent.trim();
        }
        this.detectionMethod = "USER_CORRECTED";
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getAgentType() {
        return agentType;
    }

    public String getAgentName() {
        return agentName;
    }

    public String getTaskIntent() {
        return taskIntent;
    }

    public String getBranchName() {
        return branchName;
    }

    public String getBaseCommit() {
        return baseCommit;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public String getAttributionConfidence() {
        return attributionConfidence;
    }

    public String getDetectionMethod() {
        return detectionMethod;
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

    public List<String> getAffectedModules() {
        return splitLines(affectedModules);
    }

    public List<String> getEvidence() {
        return splitLines(evidence);
    }

    public List<String> getFiles() {
        return splitLines(files);
    }

    private static String normalizeAgentType(String value) {
        if (value == null || value.isBlank()) {
            return "UNKNOWN";
        }
        String normalized = value.trim().toUpperCase();
        return switch (normalized) {
            case "CODEX", "CLAUDE_CODE", "CURSOR", "DEEPSEEK", "OTHER", "UNKNOWN" -> normalized;
            default -> "OTHER";
        };
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
