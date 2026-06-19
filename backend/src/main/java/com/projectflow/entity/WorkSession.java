package com.projectflow.entity;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import com.projectflow.dto.V2ProjectDtos.WorkSessionCandidateResponse;

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

    public void updateFromCandidate(WorkSessionCandidateResponse candidate) {
        boolean userCorrected = "USER_CORRECTED".equals(this.detectionMethod);
        if (!userCorrected) {
            this.agentType = nonBlank(candidate.agentType(), "UNKNOWN");
            this.agentName = nonBlank(candidate.agentName(), "Unknown");
            this.taskIntent = nonBlank(candidate.taskIntent(), "根据今日 Git 证据生成的候选工作会话");
            this.attributionConfidence = nonBlank(candidate.attributionConfidence(), "MEDIUM");
            this.detectionMethod = nonBlank(candidate.detectionMethod(), "GIT_EVIDENCE");
        }
        this.branchName = nonBlank(candidate.branchName(), "");
        this.baseCommit = nonBlank(candidate.baseCommit(), "");
        this.startTime = candidate.startTime() == null ? Instant.now() : candidate.startTime();
        this.endTime = candidate.endTime() == null ? Instant.now() : candidate.endTime();
        this.changedFiles = candidate.changedFiles();
        this.addedLines = candidate.addedLines();
        this.deletedLines = candidate.deletedLines();
        this.affectedModules = joinLines(candidate.affectedModules());
        this.evidence = joinLines(candidate.evidence());
        this.files = joinLines(candidate.files());
    }

    public void correctAttribution(String agentType, String taskIntent) {
        this.agentType = normalizeAgentType(agentType);
        if (taskIntent != null && !taskIntent.isBlank()) {
            this.taskIntent = taskIntent.trim();
        }
        this.detectionMethod = "USER_CORRECTED";
    }

    public WorkSessionCandidateResponse toResponse() {
        return new WorkSessionCandidateResponse(
            id.toString(),
            projectId,
            agentType,
            agentName,
            taskIntent,
            branchName,
            baseCommit,
            startTime,
            endTime,
            attributionConfidence,
            detectionMethod,
            changedFiles,
            addedLines,
            deletedLines,
            splitLines(affectedModules),
            splitLines(evidence),
            splitLines(files)
        );
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public Instant getEndTime() {
        return endTime;
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
