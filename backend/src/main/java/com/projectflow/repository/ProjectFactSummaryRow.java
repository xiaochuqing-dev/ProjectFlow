package com.projectflow.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.projectflow.entity.EvidenceConfidence;
import com.projectflow.entity.ProjectFactEpistemicStatus;
import com.projectflow.entity.ProjectFactOrigin;
import com.projectflow.entity.ProjectFactRecordStatus;

public record ProjectFactSummaryRow(
    UUID id,
    UUID projectId,
    UUID batchId,
    UUID sourceSegmentId,
    UUID legacySedimentId,
    ProjectFactOrigin origin,
    String title,
    String summary,
    Instant occurredFrom,
    Instant occurredTo,
    String sourceMode,
    String qualityStatus,
    EvidenceConfidence confidence,
    ProjectFactRecordStatus recordStatus,
    String attentionReason,
    Integer commitCount,
    Integer agentResultCount,
    Integer affectedFileCount,
    Integer evidenceCount,
    Instant createdAt,
    Instant updatedAt,
    ProjectFactEpistemicStatus epistemicStatus,
    String currentness,
    String revision,
    String validationStatus,
    List<String> limitations
) {
    public int safeCommitCount() { return commitCount == null ? 0 : commitCount; }
    public int safeAgentResultCount() { return agentResultCount == null ? 0 : agentResultCount; }
    public int safeAffectedFileCount() { return affectedFileCount == null ? 0 : affectedFileCount; }
    public int safeEvidenceCount() { return evidenceCount == null ? 0 : evidenceCount; }
    public ProjectFactEpistemicStatus safeEpistemicStatus() {
        if (epistemicStatus != null) return epistemicStatus;
        return recordStatus == ProjectFactRecordStatus.RECORDED
            ? ProjectFactEpistemicStatus.OBSERVED
            : ProjectFactEpistemicStatus.UNKNOWN;
    }
    public String safeCurrentness() {
        return currentness == null || currentness.isBlank() ? "UNKNOWN" : currentness;
    }
    public String safeRevision() { return revision == null ? "" : revision; }
    public String safeValidationStatus() {
        if (validationStatus != null && !validationStatus.isBlank()) return validationStatus;
        return recordStatus == ProjectFactRecordStatus.RECORDED ? "VALIDATED" : "PENDING_VALIDATION";
    }
    public List<String> safeLimitations() {
        return limitations == null ? List.of() : List.copyOf(limitations);
    }
}
