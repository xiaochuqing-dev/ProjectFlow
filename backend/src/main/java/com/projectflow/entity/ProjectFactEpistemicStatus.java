package com.projectflow.entity;

public enum ProjectFactEpistemicStatus {
    OBSERVED,
    VERIFIED,
    DECLARED,
    INFERRED,
    CONFLICTED,
    UNKNOWN,
    PROCESS_EVIDENCE;

    public boolean isStrongFact() {
        return this == OBSERVED || this == VERIFIED;
    }
}
