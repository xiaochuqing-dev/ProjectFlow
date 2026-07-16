package com.projectflow.repository;

import java.time.Instant;
import java.util.UUID;

public record TimelineFactPeriodVersionRow(UUID id, String periodKey, Instant updatedAt) {
}
