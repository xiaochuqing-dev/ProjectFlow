package com.projectflow.service;

import java.util.UUID;

public record ProjectCapabilityRefreshRequestedEvent(UUID userId, UUID projectId) {
}
