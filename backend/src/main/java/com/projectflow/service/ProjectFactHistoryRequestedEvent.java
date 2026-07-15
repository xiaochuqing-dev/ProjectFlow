package com.projectflow.service;

import java.util.UUID;

/**
 * Requests one bounded history-rebuild job after an incremental fact batch.
 * The event keeps WorkSessionScanService independent from the job runner and
 * therefore avoids a WorkSession -> JobService -> Runner -> WorkSession cycle.
 */
public record ProjectFactHistoryRequestedEvent(
    UUID userId,
    UUID projectId,
    String upperBoundSha,
    boolean modelConfigured
) {
}
