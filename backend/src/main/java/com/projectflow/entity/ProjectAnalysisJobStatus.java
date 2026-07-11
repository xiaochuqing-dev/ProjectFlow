package com.projectflow.entity;

public enum ProjectAnalysisJobStatus {
    QUEUED,
    RUNNING,
    CANCEL_REQUESTED,
    CANCELLED,
    SUCCEEDED,
    SUCCEEDED_WITH_WARNINGS,
    FAILED,
    INTERRUPTED,
    RETRYABLE,
    EXPIRED,
    REJECTED
}
