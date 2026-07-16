package com.projectflow.repository;

import java.time.Instant;
import java.util.UUID;

public record TimelineFactVersionRow(UUID id, Instant updatedAt) {
}
