package com.projectflow.repository;

import java.time.Instant;
import java.util.UUID;

import com.projectflow.entity.EvidenceConfidence;
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
    Instant updatedAt
) {
    public int safeCommitCount() { return commitCount == null ? 0 : commitCount; }
    public int safeAgentResultCount() { return agentResultCount == null ? 0 : agentResultCount; }
    public int safeAffectedFileCount() { return affectedFileCount == null ? 0 : affectedFileCount; }
    public int safeEvidenceCount() { return evidenceCount == null ? 0 : evidenceCount; }
}
