package com.projectflow.service.model;

import java.time.Duration;

import com.projectflow.entity.AiProvider;

public record CanonicalModelRequest(
    AiProvider provider,
    String credential,
    String systemPrompt,
    String userPrompt,
    int maxOutputTokens,
    Double temperature,
    boolean jsonMode,
    String reasoningEffort,
    Duration connectionTimeout,
    Duration requestTimeout
) {
    public CanonicalModelRequest(
        AiProvider provider,
        String credential,
        String systemPrompt,
        String userPrompt,
        int maxOutputTokens,
        Double temperature,
        boolean jsonMode,
        Duration connectionTimeout,
        Duration requestTimeout
    ) {
        this(
            provider,
            credential,
            systemPrompt,
            userPrompt,
            maxOutputTokens,
            temperature,
            jsonMode,
            null,
            connectionTimeout,
            requestTimeout
        );
    }

    public CanonicalModelRequest(
        AiProvider provider,
        String systemPrompt,
        String userPrompt,
        int maxOutputTokens,
        Double temperature,
        boolean jsonMode,
        Duration requestTimeout
    ) {
        this(
            provider,
            "",
            systemPrompt,
            userPrompt,
            maxOutputTokens,
            temperature,
            jsonMode,
            null,
            requestTimeout.compareTo(Duration.ofSeconds(10)) < 0 ? requestTimeout : Duration.ofSeconds(10),
            requestTimeout
        );
    }

    /** Compatibility constructor for focused tests; production Gateway passes a request-scoped credential. */
    public CanonicalModelRequest(
        AiProvider provider,
        String systemPrompt,
        String userPrompt,
        int maxOutputTokens,
        Double temperature,
        boolean jsonMode,
        String reasoningEffort,
        Duration connectionTimeout,
        Duration requestTimeout
    ) {
        this(
            provider,
            "",
            systemPrompt,
            userPrompt,
            maxOutputTokens,
            temperature,
            jsonMode,
            reasoningEffort,
            connectionTimeout,
            requestTimeout
        );
    }

    /** Request-scoped credential variant for direct protocol adapter tests. */
    public CanonicalModelRequest(
        AiProvider provider,
        String credential,
        String systemPrompt,
        String userPrompt,
        int maxOutputTokens,
        Double temperature,
        boolean jsonMode,
        Duration requestTimeout
    ) {
        this(
            provider,
            credential,
            systemPrompt,
            userPrompt,
            maxOutputTokens,
            temperature,
            jsonMode,
            null,
            requestTimeout.compareTo(Duration.ofSeconds(10)) < 0 ? requestTimeout : Duration.ofSeconds(10),
            requestTimeout
        );
    }

    /** A canonical request must never print the request-scoped credential. */
    @Override
    public String toString() {
        return "CanonicalModelRequest[provider=" + (provider == null ? "" : provider.getId())
            + ", credential=[REDACTED], systemPrompt=<redacted>, userPrompt=<redacted>, maxOutputTokens="
            + maxOutputTokens + ", temperature=" + temperature + ", jsonMode=" + jsonMode
            + ", reasoningEffort=" + reasoningEffort + ", connectionTimeout=" + connectionTimeout
            + ", requestTimeout=" + requestTimeout + "]";
    }
}
