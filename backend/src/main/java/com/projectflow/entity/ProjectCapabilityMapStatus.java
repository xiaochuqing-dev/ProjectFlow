package com.projectflow.entity;

public enum ProjectCapabilityMapStatus {
    NOT_INITIALIZED,
    DIRTY,
    QUEUED,
    GENERATING,
    READY,
    READY_STALE,
    WAITING_FOR_MODEL,
    FAILED
}
