package com.projectflow.eval;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.eval.ProjectFlowEvalMetrics.EvalSummary;

final class ProjectFlowEvalHarness {
    static final String SCOPE_NOTICE =
        "本指标仅代表本阶段人工标注代表性测试集，不构成对任意项目的通用准确率承诺。";

    private final ObjectMapper mapper;

    ProjectFlowEvalHarness(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    EvalRun evaluate(ProjectFlowEvalGroundTruth groundTruth, List<ProjectFlowEvalObservation> observations) {
        return new EvalRun(
            "projectflow-v3.7.4-eval-v1",
            Instant.now(),
            SCOPE_NOTICE,
            groundTruth.standard(),
            observations.stream().map(ProjectFlowEvalObservation::promptVersion).distinct().toList(),
            observations,
            ProjectFlowEvalMetrics.calculate(groundTruth, observations)
        );
    }

    ArtifactPaths writeArtifacts(EvalRun run, Path outputDirectory) throws IOException {
        Files.createDirectories(outputDirectory);
        Path json = outputDirectory.resolve("projectflow-eval-result.json");
        Path markdown = outputDirectory.resolve("projectflow-eval-result.md");
        mapper.writerWithDefaultPrettyPrinter().writeValue(json.toFile(), run);
        Files.writeString(markdown, markdown(run));
        return new ArtifactPaths(json, markdown);
    }

    private static String markdown(EvalRun run) {
        EvalSummary value = run.summary();
        return """
            # ProjectFlow V3.7.4 Internal Model Evaluation

            %s

            Dataset standard: %s
            Harness: %s
            Prompt versions: %s

            ## Product-level reliability

            Total / successful / failed runs: %d / %d / %d
            Timeout / schema failure / retry: %d / %d / %d
            Average / P95 latency ms: %.2f / %.2f
            End-to-end completion: %.4f
            Degradation / cancellation: %d / %d

            ## Conditional semantic quality

            Valid structured runs: %d
            Unsupported claim rate: %.4f
            Critical evidence recall: %.4f
            Evidence precision: %.4f
            Project shape F1: %.4f
            Tool precision / recall: %.4f / %.4f
            Deep-read precision / sufficiency: %.4f / %.4f
            Dynamic view precision / recall: %.4f / %.4f
            Conflict detection: %.4f
            Repeatability: %.4f
            Second-stage evidence gain: %.4f
            Requests / tokens: %d / %d
            Estimated cost: %s

            Raw model responses, reasoning and secrets are not stored.
            """.formatted(
            run.scopeNotice(),
            run.groundTruthStandard(),
            run.harnessVersion(),
            String.join(", ", run.promptVersions()),
            value.runCount(),
            value.successfulRunCount(),
            value.failureCount(),
            value.timeoutCount(),
            value.schemaFailureCount(),
            value.retryCount(),
            value.averageLatencyMs(),
            value.p95LatencyMs(),
            value.endToEndCompletionRate(),
            value.degradedRunCount(),
            value.cancellationCount(),
            value.conditionalSemanticRunCount(),
            value.unsupportedClaimRate(),
            value.criticalEvidenceRecall(),
            value.evidencePrecision(),
            value.projectShapeF1(),
            value.toolSelectionPrecision(),
            value.toolSelectionRecall(),
            value.deepReadTargetAccuracy(),
            value.deepReadSufficiency(),
            value.dynamicViewPrecision(),
            value.dynamicViewRecall(),
            value.conflictDetectionRate(),
            value.repeatability(),
            value.secondStageEvidenceGain(),
            value.modelRequestCount(),
            value.totalTokens(),
            value.estimatedCost() == null ? "UNAVAILABLE" : value.estimatedCost().toString()
        );
    }

    record EvalRun(
        String harnessVersion,
        Instant generatedAt,
        String scopeNotice,
        String groundTruthStandard,
        List<String> promptVersions,
        List<ProjectFlowEvalObservation> observations,
        EvalSummary summary
    ) {
    }

    record ArtifactPaths(Path json, Path markdown) {
    }
}
