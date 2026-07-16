package com.projectflow.repository;

import java.time.Instant;

public record CapabilityFactStatsRow(
    long factCount,
    long batchCount,
    long evidenceCount,
    long attentionCount,
    Instant earliestAt,
    Instant latestAt
) {
    public int safeFactCount() { return Math.toIntExact(Math.max(0, factCount)); }
    public int safeBatchCount() { return Math.toIntExact(Math.max(0, batchCount)); }
    public int safeEvidenceCount() { return Math.toIntExact(Math.max(0, evidenceCount)); }
    public int safeAttentionCount() { return Math.toIntExact(Math.max(0, attentionCount)); }
}
