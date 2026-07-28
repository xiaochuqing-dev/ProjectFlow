package com.projectflow.service.model;

import java.time.Duration;

import com.projectflow.entity.AiProvider;

public record CanonicalModelRequest(
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
    public CanonicalModelRequest(
        AiProvider provider,
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
}
