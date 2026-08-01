package com.projectflow.entity;

import java.util.Locale;

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

    /**
     * Normalizes legacy/model-facing labels into the seven product-authoritative
     * epistemic states. This method never grants VERIFIED authority.
     */
    public static ProjectFactEpistemicStatus fromAnalysisLabel(String value) {
        String normalized = value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "OBSERVED", "ENGINEERING_OBSERVATION", "CURRENT_STATE", "HISTORICAL_EVENT" -> OBSERVED;
            case "VERIFIED" -> INFERRED;
            case "DECLARED", "USER_ASSERTION", "USER_DECLARED", "PROJECT_INTENT" -> DECLARED;
            case "CONFLICTED" -> CONFLICTED;
            case "UNKNOWN" -> UNKNOWN;
            case "PROCESS_EVIDENCE", "PROCESS_METADATA" -> PROCESS_EVIDENCE;
            case "INFERRED", "POSSIBLY_STALE", "MODEL_SUMMARY", "MODEL_SUGGESTED_HIGHLIGHT" -> INFERRED;
            default -> INFERRED;
        };
    }
}
