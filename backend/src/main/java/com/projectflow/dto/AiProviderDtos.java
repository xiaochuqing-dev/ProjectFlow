package com.projectflow.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.projectflow.entity.AiProviderAuthMode;
import com.projectflow.entity.AiProviderType;
import com.projectflow.entity.ModelProtocol;

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
        @NotNull @Min(256) @Max(200000) Integer maxTokens,
        boolean defaultEnabled,
        List<@Size(max = 60) String> purposeTags,
        boolean clearApiKey,
        ModelProtocol protocol,
        @Size(max = 500) String endpointOverride,
        AiProviderAuthMode authMode,
        @Size(max = 120) String authHeaderName,
        @Size(max = 120) String queryKeyName,
        @Size(max = 20) Map<@Size(max = 120) String, @Size(max = 2000) String> safeHeaders,
        @Min(30) @Max(900) Integer requestTimeoutSeconds,
        Boolean supportsTemperature,
        Boolean supportsJsonMode,
        Boolean supportsStructuredOutput,
        Boolean supportsReasoning,
        Boolean supportsReasoningControl
    ) {
    }

    public record AiProviderResponse(
        UUID id,
        String name,
        String baseUrl,
        String modelName,
        AiProviderType type,
        ModelProtocol protocol,
        String endpointOverride,
        AiProviderAuthMode authMode,
        String authHeaderName,
        String queryKeyName,
        List<String> safeHeaderNames,
        Integer requestTimeoutSeconds,
        Boolean supportsTemperature,
        Boolean supportsJsonMode,
        Boolean supportsStructuredOutput,
        Boolean supportsReasoning,
        Boolean supportsReasoningControl,
        Double temperature,
        Integer maxTokens,
        boolean defaultEnabled,
        List<String> purposeTags,
        boolean apiKeyConfigured,
        String lastProbeProfile,
        Instant lastProbedAt,
        Instant createdAt,
        Instant updatedAt
    ) {
    }

    public record ProviderTestResponse(
        boolean ok,
        String provider,
        String message,
        ProviderCompatibilityProfile profile
    ) {
    }

    public record ProviderCompatibilityProfile(
        String connection,
        ModelProtocol protocol,
        AiProviderAuthMode authMode,
        String auth,
        String structuredOutput,
        String jsonMode,
        String temperature,
        String reasoning,
        String usage,
        String outputLimitDetection,
        String projectFlowCompatibility,
        List<String> warnings,
        int requestsMade
    ) {}

    public record DuplicateProviderGroupResponse(
        String groupKey,
        AiProviderResponse recommendedKeeper,
        List<AiProviderResponse> duplicates
    ) {
    }

    public record DuplicateCleanupRequest(
        @NotNull @Size(min = 1, max = 50) List<@NotNull UUID> providerIds
    ) {
    }

    public record DuplicateCleanupResponse(
        int deletedCount,
        List<AiProviderResponse> remainingProviders
    ) {
    }
}
