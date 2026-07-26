package com.projectflow.integration;

import java.util.List;

public interface IntelligenceProviderAdapter {
    String adapterId();

    String adapterVersion();

    IntelligenceResult analyze(IntelligenceRequest request);

    record IntelligenceRequest(
        String projectBinding,
        List<ExternalEvidenceEnvelope> evidence,
        List<String> requestedDimensions,
        int maxInputChars,
        int maxOutputChars
    ) {
    }

    record IntelligenceResult(
        String status,
        String normalizedSummary,
        List<String> evidenceRefs,
        List<String> unknowns
    ) {
    }
}
