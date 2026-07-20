package com.projectflow.service.model;

public record CanonicalModelResponse(
    String content,
    String providerFinishReason,
    NormalizedFinishReason finishReason,
    CanonicalModelUsage usage,
    String requestId,
    boolean reasoningPresent,
    int reasoningLength
) {
}
