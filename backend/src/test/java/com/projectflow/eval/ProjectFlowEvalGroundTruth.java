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
        String resource = System.getProperty(
            "projectflow.eval.ground-truth-resource",
            "/projectflow-eval/ground-truth.json"
        ).strip();
        if (!resource.startsWith("/projectflow-eval/") || !resource.endsWith(".json")) {
            throw new IOException("ground truth resource must stay under /projectflow-eval/");
        }
        try (InputStream stream = ProjectFlowEvalGroundTruth.class.getResourceAsStream(resource)) {
            if (stream == null) throw new IOException(resource + " not found");
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
