package com.projectflow.service.model;

public record CanonicalModelUsage(
    int inputTokens,
    int outputTokens,
    int totalTokens,
    int reasoningTokens,
    String source
) {
    public static CanonicalModelUsage unavailable() {
        return new CanonicalModelUsage(0, 0, 0, 0, "UNAVAILABLE");
    }
}
