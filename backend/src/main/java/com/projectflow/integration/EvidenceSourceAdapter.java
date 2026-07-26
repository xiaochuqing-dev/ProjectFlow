package com.projectflow.integration;

import java.util.List;
import java.util.Set;

public interface EvidenceSourceAdapter {
    String adapterId();

    String adapterVersion();

    List<ExternalEvidenceEnvelope> collect(EvidenceSourceRequest request);

    record EvidenceSourceRequest(
        String projectBinding,
        String sourceRevision,
        Set<String> requestedEvidenceRefs,
        int maxItems,
        int maxTotalChars
    ) {
    }
}
