package com.projectflow.eval;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

record ProjectFlowEvalGroundTruth(
    String standard,
    List<EvalCase> cases
) {
    static ProjectFlowEvalGroundTruth load(ObjectMapper mapper) throws IOException {
        try (InputStream stream = ProjectFlowEvalGroundTruth.class.getResourceAsStream(
            "/projectflow-eval/ground-truth.json"
        )) {
            if (stream == null) throw new IOException("ground-truth.json not found");
            return mapper.readValue(stream, ProjectFlowEvalGroundTruth.class);
        }
    }

    record EvalCase(
        String id,
        String source,
        boolean important,
        String context,
        String toolEvidence,
        List<String> expectedProjectShapes,
        List<String> mustFindEvidence,
        List<String> mustNotClaim,
        List<String> expectedTools,
        List<String> forbiddenTools,
        List<String> expectedViews,
        List<String> forbiddenViews,
        List<String> expectedUnknowns,
        List<String> expectedConflicts,
        String expectedHistoryMode,
        List<String> expectedDeepReadTargets
    ) {
    }
}
