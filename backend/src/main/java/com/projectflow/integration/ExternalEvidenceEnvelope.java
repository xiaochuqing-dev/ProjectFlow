package com.projectflow.integration;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ExternalEvidenceEnvelope(
    String sourceSystem,
    String sourceType,
    String sourceRef,
    String projectBinding,
    String normalizedSummary,
    Instant occurredAt,
    Instant collectedAt,
    String confidence,
    String currentness,
    String temporalRole,
    List<String> evidenceRefs,
    boolean sensitiveRedacted,
    boolean rawPayloadStored,
    String adapterId,
    String adapterVersion,
    String sourceRevision,
    Map<String, String> processMetadata
) {
}
