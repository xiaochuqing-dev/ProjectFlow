package com.projectflow.repository;

import java.time.Instant;

public record TimelineOverviewRow(
    long factCount,
    long batchCount,
    long attentionCount,
    Instant earliestFactAt,
    Instant latestFactAt,
    Instant maxUpdatedAt
) {
}
