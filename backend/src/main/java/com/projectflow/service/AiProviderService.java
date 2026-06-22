package com.projectflow.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projectflow.dto.AiProviderDtos.AiProviderRequest;
import com.projectflow.dto.AiProviderDtos.AiProviderResponse;
import com.projectflow.dto.AiProviderDtos.ProviderTestResponse;
import com.projectflow.entity.AiProvider;
import com.projectflow.entity.AiProviderType;
import com.projectflow.repository.AiProviderRepository;
import com.projectflow.support.AppException;

@Service
public class AiProviderService {
    private final AiProviderRepository aiProviderRepository;
    private final HttpClient httpClient;

    public AiProviderService(AiProviderRepository aiProviderRepository) {
        this.aiProviderRepository = aiProviderRepository;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    }

    @Transactional(readOnly = true)
    public List<AiProviderResponse> list(UUID userId) {
        List<AiProviderResponse> saved = aiProviderRepository.findByUserIdOrderByDefaultEnabledDescUpdatedAtDesc(userId)
            .stream()
            .map(this::toResponse)
            .toList();
        if (!saved.isEmpty()) {
            return saved;
        }
        return List.of(mockProvider());
    }

    @Transactional
    public AiProviderResponse create(UUID userId, AiProviderRequest request) {
        AiProvider provider = new AiProvider(userId);
        provider.update(
            request.name().trim(),
            normalizeBaseUrl(request.baseUrl()),
            blankToNull(request.apiKey()),
            request.modelName().trim(),
            request.type(),
            request.temperature(),
            request.maxTokens(),
            request.defaultEnabled(),
            request.purposeTags()
        );
        return toResponse(aiProviderRepository.save(provider));
    }

    @Transactional
    public AiProviderResponse update(UUID userId, UUID providerId, AiProviderRequest request) {
        AiProvider provider = findOwned(userId, providerId);
        provider.update(
            request.name().trim(),
            normalizeBaseUrl(request.baseUrl()),
            blankToNull(request.apiKey()),
            request.modelName().trim(),
            request.type(),
            request.temperature(),
            request.maxTokens(),
            request.defaultEnabled(),
            request.purposeTags()
        );
        return toResponse(provider);
    }

    @Transactional
    public void delete(UUID userId, UUID providerId) {
        aiProviderRepository.delete(findOwned(userId, providerId));
    }

    public ProviderTestResponse test(UUID userId, UUID providerId) {
        AiProvider provider = findOwned(userId, providerId);
        if (provider.getType() == AiProviderType.MOCK) {
            return new ProviderTestResponse(true, provider.getName(), "Mock provider is ready.");
        }
        if (provider.getApiKey() == null || provider.getApiKey().isBlank()) {
            return new ProviderTestResponse(false, provider.getName(), "API key is required before testing this provider.");
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(provider.getBaseUrl() + "/chat/completions"))
                .timeout(Duration.ofSeconds(12))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + provider.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString("""
                    {
                      "model": "%s",
                      "messages": [
                        {"role": "user", "content": "Reply with OK."}
                      ],
                      "temperature": 0,
                      "max_tokens": 8
                    }
                    """.formatted(escapeJson(provider.getModelName()))))
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            boolean ok = response.statusCode() >= 200 && response.statusCode() < 300;
            return new ProviderTestResponse(
                ok,
                provider.getName(),
                ok ? "Provider responded successfully." : "Provider test failed with HTTP " + response.statusCode() + "."
            );
        } catch (Exception exception) {
            return new ProviderTestResponse(false, provider.getName(), "Provider test failed. Please check base URL, model name, and API key.");
        }
    }

    private AiProvider findOwned(UUID userId, UUID providerId) {
        return aiProviderRepository.findByIdAndUserId(providerId, userId)
            .orElseThrow(() -> new AppException("AI_PROVIDER_NOT_FOUND", "AI provider was not found", HttpStatus.NOT_FOUND));
    }

    private AiProviderResponse toResponse(AiProvider provider) {
        return new AiProviderResponse(
            provider.getId(),
            provider.getName(),
            provider.getBaseUrl(),
            provider.getModelName(),
            provider.getType(),
            provider.getTemperature(),
            provider.getMaxTokens(),
            provider.isDefaultEnabled(),
            provider.getPurposeTags(),
            provider.getApiKey() != null && !provider.getApiKey().isBlank(),
            provider.getCreatedAt(),
            provider.getUpdatedAt()
        );
    }

    private AiProviderResponse mockProvider() {
        return new AiProviderResponse(
            null,
            "Mock Provider",
            "mock://local",
            "projectflow-mock",
            AiProviderType.MOCK,
            0.2,
            2048,
            true,
            List.of("项目分析", "材料解析", "成果生成"),
            false,
            Instant.EPOCH,
            Instant.EPOCH
        );
    }

    private String normalizeBaseUrl(String baseUrl) {
        String trimmed = baseUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        String lower = trimmed.toLowerCase();
        if (lower.endsWith("/chat/completions")) {
            trimmed = trimmed.substring(0, trimmed.length() - "/chat/completions".length());
        }
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
