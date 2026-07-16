package com.projectflow.repository;

import java.time.Instant;

public interface TimelinePeriodStatsRow {
    String getPeriodKey();
    long getFactCount();
    long getBatchCount();
    long getAttentionCount();
    Instant getEarliestEventAt();
    Instant getLatestEventAt();
    Instant getMaxUpdatedAt();
}
