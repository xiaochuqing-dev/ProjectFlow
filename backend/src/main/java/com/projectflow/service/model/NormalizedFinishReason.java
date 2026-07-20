package com.projectflow.service.model;

public enum NormalizedFinishReason {
    COMPLETE,
    OUTPUT_LIMIT,
    CONTEXT_LIMIT,
    REFUSAL,
    CONTENT_FILTERED,
    TOOL_USE,
    INCOMPLETE,
    ERROR,
    UNKNOWN
}
