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
    Duration timeout
) {
}
