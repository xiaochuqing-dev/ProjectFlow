package com.projectflow.service;

import java.util.List;
import java.util.UUID;

public record ProjectFactsCommittedEvent(UUID projectId, List<UUID> factIds) {
    public ProjectFactsCommittedEvent {
        factIds = factIds == null ? List.of() : List.copyOf(factIds);
    }
}
