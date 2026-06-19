package com.projectflow.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.projectflow.entity.AiOutputType;

import jakarta.validation.constraints.NotNull;

public final class AiOutputDtos {
    private AiOutputDtos() {
    }

    public record AiOutputRequest(
        @NotNull AiOutputType type,
        LocalDate fromDate,
        LocalDate toDate
    ) {
    }

    public record AiOutputResponse(
        UUID id,
        UUID projectId,
        AiOutputType type,
        String title,
        String content,
        LocalDate fromDate,
        LocalDate toDate,
        String provider,
        Instant createdAt,
        Instant updatedAt
    ) {
    }

    public record ModelUsageRecordResponse(
        UUID id,
        UUID projectId,
        String operation,
        String providerName,
        String modelName,
        int promptTokens,
        int completionTokens,
        int totalTokens,
        boolean usageEstimated,
        long latencyMs,
        String status,
        String errorType,
        String errorMessage,
        String qualityWarnings,
        Instant createdAt
    ) {
    }
}
