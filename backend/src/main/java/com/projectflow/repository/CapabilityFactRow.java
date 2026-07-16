package com.projectflow.repository;

import java.time.Instant;
import java.util.UUID;

import com.projectflow.entity.ProjectCapabilityRelationRole;
import com.projectflow.entity.ProjectFactRecordStatus;

public record CapabilityFactRow(
    UUID factId,
    UUID projectId,
    UUID batchId,
    String title,
    String summary,
    Instant occurredFrom,
    Instant occurredTo,
    ProjectFactRecordStatus recordStatus,
    String attentionReason,
    int commitCount,
    int affectedFileCount,
    int evidenceCount,
    ProjectCapabilityRelationRole relationRole,
    UUID sourceEvolutionId,
    Instant linkedAt
) {
}
