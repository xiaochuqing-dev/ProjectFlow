package com.projectflow.service;

import java.util.UUID;

public record ProjectTimelineRefreshRequestedEvent(UUID userId, UUID projectId) {
}
