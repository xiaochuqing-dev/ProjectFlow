package com.projectflow.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ProjectEvolutionBridgeDtos {
    private ProjectEvolutionBridgeDtos() {
    }

    public record EvolutionBridgeResponse(
        UUID id,
        UUID projectId,
        Instant occurredAt,
        String beforeRevision,
        String afterRevision,
        String beforeStructureVersion,
        String afterStructureVersion,
        String meaningfulChange,
        String affectedAreaId,
        String affectedAreaLabel,
        String beforeState,
        String afterState,
        String epistemicStatus,
        String confidence,
        List<UUID> sourceFactIds,
        List<String> sourceCommitRefs,
        List<String> changedPaths,
        List<String> evidenceRefs,
        int generationVersion,
        Instant createdAt
    ) {
    }

    public record EvolutionBridgePageResponse(
        List<EvolutionBridgeResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
    ) {
    }
}
