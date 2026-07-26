package com.projectflow.integration;

import java.util.List;

public interface ProjectionAdapter {
    String adapterId();

    String adapterVersion();

    ProjectionResult project(ProjectionRequest request);

    record ProjectionRequest(
        String projectBinding,
        String projectionRoot,
        List<ExternalEvidenceEnvelope> evidence,
        boolean dryRun
    ) {
    }

    record ProjectionResult(
        String status,
        int changedItemCount,
        int unchangedItemCount,
        List<String> conflicts
    ) {
    }
}
