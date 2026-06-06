package com.projectflow.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.projectflow.entity.AiProviderType;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class AiProviderDtos {
    private AiProviderDtos() {
    }

    public record AiProviderRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 500) String baseUrl,
        @Size(max = 5000) String apiKey,
        @NotBlank @Size(max = 160) String modelName,
        @NotNull AiProviderType type,
        @NotNull @Min(0) @Max(2) Double temperature,
        @NotNull @Min(1) @Max(5000000) Integer maxTokens,
        boolean defaultEnabled,
        List<@Size(max = 60) String> purposeTags
    ) {
    }

    public record AiProviderResponse(
        UUID id,
        String name,
        String baseUrl,
        String modelName,
        AiProviderType type,
        Double temperature,
        Integer maxTokens,
        boolean defaultEnabled,
        List<String> purposeTags,
        boolean apiKeyConfigured,
        Instant createdAt,
        Instant updatedAt
    ) {
    }

    public record ProviderTestResponse(
        boolean ok,
        String provider,
        String message
    ) {
    }
}
