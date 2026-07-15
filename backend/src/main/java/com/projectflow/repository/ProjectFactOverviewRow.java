package com.projectflow.repository;

import java.time.Instant;

public record ProjectFactOverviewRow(
    Long totalCount,
    Long recordedCount,
    Long attentionCount,
    Instant earliestOccurredAt,
    Instant latestOccurredAt
) {
    public long safeTotalCount() { return totalCount == null ? 0 : totalCount; }
    public long safeRecordedCount() { return recordedCount == null ? 0 : recordedCount; }
    public long safeAttentionCount() { return attentionCount == null ? 0 : attentionCount; }
}
