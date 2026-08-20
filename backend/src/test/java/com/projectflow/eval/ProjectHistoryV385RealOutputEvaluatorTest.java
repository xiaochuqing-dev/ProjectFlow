package com.projectflow.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.entity.AiProvider;
import com.projectflow.repository.AiProviderRepository;
import com.projectflow.repository.ProjectFactRepository;
import com.projectflow.repository.ProjectHistoryEventRepository;
import com.projectflow.repository.ProjectHistorySnapshotRepository;
import com.projectflow.repository.ProjectHistoryWindowCheckpointRepository;
import com.projectflow.repository.ProjectMemoryRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.service.ProjectHistoryCorrectionService;
import com.projectflow.service.ProjectHistoryReadService;
import com.projectflow.service.ProjectHistoryReconstructionService;

/** Qualifies real Provider output through the production history refresh path. */
@SpringBootTest
@ActiveProfiles("test")
class ProjectHistoryV385RealOutputEvaluatorTest {
    private static final String RESOURCE = "/projectflow-v385/history-ground-truth.json";

    @Autowired ProjectRepository projectRepository;
    @Autowired ProjectMemoryRepository memoryRepository;
    @Autowired ProjectFactRepository factRepository;
    @Autowired ProjectHistoryEventRepository eventRepository;
    @Autowired ProjectHistorySnapshotRepository snapshotRepository;
    @Autowired ProjectHistoryWindowCheckpointRepository checkpointRepository;
    @Autowired AiProviderRepository providerRepository;
    @Autowired ProjectHistoryReconstructionService reconstructionService;
    @Autowired ProjectHistoryCorrectionService correctionService;
    @Autowired ProjectHistoryReadService readService;
    @Autowired ObjectMapper objectMapper;

    @TempDir Path temporaryRoot;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void evaluatesCalibrationAndFrozenHoldoutUsingRealProductionRefresh() throws Exception {
        ProjectFlowRealModelEvalIT.ProviderConfig config = ProjectFlowRealModelEvalIT.providerConfig();
        Assumptions.assumeTrue(config != null, "未提供真实 Provider 安全配置，V3.8.5 真实输出评测跳过");

        JsonNode groundTruth = loadGroundTruth();
        UUID userId = UUID.randomUUID();
        providerRepository.saveAndFlush(provider(userId, config));
        ProjectHistoryV385FixtureRunner runner = new ProjectHistoryV385FixtureRunner(
            projectRepository, memoryRepository, factRepository, eventRepository, snapshotRepository,
            reconstructionService, correctionService, readService, objectMapper
        );

        long started = System.nanoTime();
        List<ProjectHistoryV385QualityEvaluator.CaseObservation> observations = new ArrayList<>();
        List<SafeCaseRun> runs = new ArrayList<>();
        int ordinal = 0;
        System.out.printf("V385_REAL_PROVIDER_START provider=%s model=%s protocol=%s%n",
            config.name(), config.model(), config.protocol());
        for (JsonNode testCase : groundTruth.path("cases")) {
            ProjectHistoryV385FixtureRunner.FixtureExecution execution = runner.executeWithOneFailedWindowRetry(
                userId, testCase, temporaryRoot.resolve("case-" + ++ordinal)
            );
            observations.add(execution.observation());
            SafeCaseRun run = safeRun(execution);
            runs.add(run);
            System.out.printf(
                "V385_REAL_CASE_DONE split=%s status=%s requests=%d tokens=%d elapsedMs=%d retryRefreshes=%d recovered=%s%n",
                run.split(), run.modelStatus(), run.requestCount(), run.tokenCount(), run.latencyMs(),
                run.retryRefreshCount(), run.recoveredAfterRetry()
            );
            if (!run.failureDiagnostics().isEmpty()) {
                System.out.printf("V385_REAL_CASE_FAILURES case=%s diagnostics=%s%n",
                    run.caseId(), run.failureDiagnostics());
            }
        }

        ProjectHistoryV385QualityEvaluator.EvaluationReport report =
            ProjectHistoryV385QualityEvaluator.evaluateCases(groundTruth, observations);
        QualificationSummary qualification = qualification(report, runs, elapsedMs(started));
        assertSafetyGates(report.calibration());
        assertSafetyGates(report.holdout());
        writeSafeArtifact(config, report, observations, runs, qualification);
        System.out.printf(
            "V385_REAL_PROVIDER_DONE provider=%s model=%s status=%s requests=%d tokens=%d elapsedMs=%d%n",
            config.name(), config.model(), qualification.qualified() ? "PASS" : "FAIL",
            qualification.requestCount(), qualification.tokenCount(), qualification.elapsedMs()
        );

        assertThat(report.missingCases()).isEmpty();
        assertThat(report.calibration().passes()).isTrue();
        assertThat(report.holdout().passes()).isTrue();
        assertThat(qualification.calibrationRequestCount()).isPositive();
        assertThat(qualification.holdoutRequestCount()).isPositive();
        assertThat(qualification.modelDegradedCaseCount()).isZero();
        assertThat(qualification.failedOrPendingWindowCount()).isZero();
        assertThat(qualification.rejectedModelOutputCount()).isZero();
        assertThat(qualification.validationRepairFailureCount()).isZero();
        assertThat(qualification.unresolvedAfterRetryCaseCount()).isZero();
        assertThat(runs).filteredOn(value -> value.requestCount() > 0)
            .allSatisfy(value -> assertThat(value.modelUsed()).isTrue());
        assertThat(qualification.qualified()).isTrue();
    }

    private AiProvider provider(UUID userId, ProjectFlowRealModelEvalIT.ProviderConfig config) {
        AiProvider provider = new AiProvider(userId);
        provider.update(
            config.name(), config.baseUrl(), config.apiKey(), config.model(), config.type(),
            0.1, config.maxTokens(), true, List.of("V3.8.5_HISTORY_REAL_OUTPUT")
        );
        provider.configureProtocol(
            config.protocol(), null, null, null, null, Map.of(), config.timeoutSeconds(), null,
            config.supportsJsonMode(), null, config.supportsReasoning(), config.supportsReasoningControl()
        );
        return provider;
    }

    private SafeCaseRun safeRun(ProjectHistoryV385FixtureRunner.FixtureExecution execution) {
        Map<String, Object> diagnostics = execution.diagnostics();
        Map<String, Object> initialDiagnostics = execution.initialDiagnostics();
        ProjectHistoryV385QualityEvaluator.CaseObservation observation = execution.observation();
        String modelStatus = text(diagnostics.get("modelStatus"));
        int failedWindowCount = number(diagnostics, "failedWindowCount");
        int unprocessedWindowCount = number(diagnostics, "modelUnprocessedWindowCount");
        int skippedWindowCount = number(diagnostics, "skippedWindowCount");
        int chapterFailedCount = number(diagnostics, "chapterSynthesisFailedCount");
        int chapterPendingCount = number(diagnostics, "chapterSynthesisPendingCount");
        return new SafeCaseRun(
            observation.caseId(), observation.split(), observation.modelRequestCount(), observation.modelTokenCount(),
            execution.modelLatencyMs(), execution.modelUsed(), execution.degraded(),
            booleanValue(diagnostics, "complete", true),
            modelExecutionDegraded(
                modelStatus, failedWindowCount, unprocessedWindowCount, skippedWindowCount,
                chapterFailedCount, chapterPendingCount
            ),
            execution.cacheHit(), modelStatus, failedWindowCount,
            unprocessedWindowCount, skippedWindowCount, chapterFailedCount, chapterPendingCount,
            text(initialDiagnostics.get("modelStatus")),
            failedOrPendingWindowCount(initialDiagnostics),
            number(initialDiagnostics, "modelValidationRepairCount"),
            number(initialDiagnostics, "modelValidationRepairFailureCount"),
            execution.retryRefreshCount(), execution.recoveredAfterRetry(),
            number(diagnostics, "modelRejectedInvalidEvidenceRefCount"),
            number(diagnostics, "modelRejectedCrossProjectRefCount"),
            number(diagnostics, "modelRejectedUnsupportedClaimCount"),
            number(diagnostics, "modelDeterministicTitleFallbackCount"),
            number(diagnostics, "modelValidationRepairCount"),
            number(diagnostics, "modelValidationRepairFailureCount"),
            safeFailureDiagnostics(execution.projectId())
        );
    }

    private List<SafeFailureDiagnostic> safeFailureDiagnostics(UUID projectId) {
        return checkpointRepository.findByProjectIdOrderByUpdatedAtAsc(projectId).stream()
            .filter(checkpoint -> "FAILED".equals(checkpoint.getStatus()))
            .map(checkpoint -> {
                try {
                    JsonNode value = objectMapper.readTree(checkpoint.getDiagnosticsJson());
                    return new SafeFailureDiagnostic(
                        safeToken(value.path("scope").asText()),
                        safeToken(value.path("failureClass").asText()),
                        safeToken(value.path("failureStage").asText()),
                        safeToken(value.path("failureCode").asText()),
                        safeToken(value.path("repairFailureStage").asText()),
                        safeToken(value.path("repairFailureCode").asText()),
                        safeToken(value.path("validationKind").asText()),
                        safeToken(value.path("validationCode").asText()),
                        Math.max(0, value.path("requestCount").asInt(0))
                    );
                } catch (Exception ignored) {
                    return new SafeFailureDiagnostic("", "DIAGNOSTIC_PARSE_FAILURE", "", "", "", "", "", "", 0);
                }
            })
            .toList();
    }

    private static String safeToken(String value) {
        if (value == null || value.isBlank()) return "";
        String safe = value.trim().replaceAll("[^A-Za-z0-9_:,.-]", "_");
        return safe.length() <= 160 ? safe : safe.substring(0, 160);
    }

    private QualificationSummary qualification(
        ProjectHistoryV385QualityEvaluator.EvaluationReport report,
        List<SafeCaseRun> runs,
        long elapsedMs
    ) {
        int calibrationRequests = runs.stream().filter(value -> "CALIBRATION".equals(value.split()))
            .mapToInt(SafeCaseRun::requestCount).sum();
        int holdoutRequests = runs.stream().filter(value -> "HOLDOUT".equals(value.split()))
            .mapToInt(SafeCaseRun::requestCount).sum();
        int sourceCoverageIncomplete = (int) runs.stream()
            .filter(value -> !value.sourceCollectionComplete()).count();
        int refreshDegraded = (int) runs.stream().filter(SafeCaseRun::refreshDegraded).count();
        int modelDegraded = (int) runs.stream().filter(SafeCaseRun::modelDegraded).count();
        int failedOrPending = runs.stream().mapToInt(value -> value.failedWindowCount()
            + value.unprocessedWindowCount() + value.skippedWindowCount()
            + value.chapterFailedCount() + value.chapterPendingCount()).sum();
        int rejected = runs.stream().mapToInt(value -> value.rejectedInvalidEvidenceCount()
            + value.rejectedCrossProjectCount() + value.rejectedUnsupportedClaimCount()).sum();
        int deterministicTitleFallbacks = runs.stream()
            .mapToInt(SafeCaseRun::deterministicTitleFallbackCount).sum();
        int validationRepairs = runs.stream().mapToInt(SafeCaseRun::validationRepairCount).sum();
        int validationRepairFailures = runs.stream().mapToInt(SafeCaseRun::validationRepairFailureCount).sum();
        int initialFailedOrPending = runs.stream().mapToInt(SafeCaseRun::initialFailedOrPendingWindowCount).sum();
        int initialValidationRepairFailures = runs.stream()
            .mapToInt(SafeCaseRun::initialValidationRepairFailureCount).sum();
        int retryRefreshes = runs.stream().mapToInt(SafeCaseRun::retryRefreshCount).sum();
        int retryAttemptedCases = (int) runs.stream().filter(value -> value.retryRefreshCount() > 0).count();
        int recoveredAfterRetry = (int) runs.stream().filter(SafeCaseRun::recoveredAfterRetry).count();
        int unresolvedAfterRetry = (int) runs.stream()
            .filter(value -> value.retryRefreshCount() > 0 && !value.recoveredAfterRetry()).count();
        int requests = runs.stream().mapToInt(SafeCaseRun::requestCount).sum();
        long tokens = runs.stream().mapToLong(SafeCaseRun::tokenCount).sum();
        boolean qualified = report.passes() && calibrationRequests > 0 && holdoutRequests > 0
            && modelDegraded == 0 && failedOrPending == 0 && rejected == 0
            && validationRepairFailures == 0 && unresolvedAfterRetry == 0
            && runs.stream().filter(value -> value.requestCount() > 0).allMatch(SafeCaseRun::modelUsed);
        return new QualificationSummary(
            qualified, requests, tokens, elapsedMs, calibrationRequests, holdoutRequests,
            sourceCoverageIncomplete, refreshDegraded, modelDegraded, failedOrPending, rejected,
            deterministicTitleFallbacks, validationRepairs, validationRepairFailures,
            initialFailedOrPending, initialValidationRepairFailures, retryRefreshes,
            retryAttemptedCases, recoveredAfterRetry, unresolvedAfterRetry
        );
    }

    private void writeSafeArtifact(
        ProjectFlowRealModelEvalIT.ProviderConfig config,
        ProjectHistoryV385QualityEvaluator.EvaluationReport report,
        List<ProjectHistoryV385QualityEvaluator.CaseObservation> observations,
        List<SafeCaseRun> runs,
        QualificationSummary qualification
    ) throws Exception {
        String defaultName = "v385-real-" + config.protocol().name().toLowerCase(java.util.Locale.ROOT);
        String outputName = System.getProperty("projectflow.eval.output-name", defaultName)
            .replaceAll("[^A-Za-z0-9._-]", "_");
        Path output = Path.of("target", "projectflow-eval", outputName);
        Files.createDirectories(output);
        Map<String, Object> artifact = new LinkedHashMap<>();
        artifact.put("version", "projectflow-v3.8.5-history-real-output-v6");
        artifact.put("generatedAt", Instant.now().toString());
        artifact.put("provider", Map.of(
            "name", config.name(), "model", config.model(), "protocol", config.protocol().name(),
            "reasoningEffort", config.reasoningEffort()
        ));
        artifact.put("qualification", qualification);
        artifact.put("evaluation", report);
        artifact.put("caseRuns", List.copyOf(runs));
        artifact.put("humanReviewCandidates", humanReviewCandidates(observations));
        artifact.put("security", Map.of(
            "apiKeyPersisted", false,
            "promptPersisted", false,
            "rawResponsePersisted", false,
            "reasoningPersisted", false,
            "absolutePathPersisted", false
        ));
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(
            output.resolve("history-ground-truth-real-result.json").toFile(), artifact
        );
    }

    static List<Map<String, Object>> humanReviewCandidates(
        List<ProjectHistoryV385QualityEvaluator.CaseObservation> observations
    ) {
        return observations.stream().map(observation -> Map.<String, Object>of(
            "caseId", observation.caseId(),
            "split", observation.split(),
            "stories", ProjectHistoryV385ReviewSamples.stories(
                observation.stories(), 3, observation.presentationRevision()),
            "chapters", ProjectHistoryV385ReviewSamples.chapters(
                observation.chapters(), 2, observation.presentationRevision())
        )).toList();
    }

    private void assertSafetyGates(ProjectHistoryV385QualityEvaluator.AggregateMetrics metrics) {
        assertThat(metrics.invalidEvidenceReferenceCount()).isZero();
        assertThat(metrics.crossProjectReferenceCount()).isZero();
        assertThat(metrics.unsupportedStrongFactCount()).isZero();
        assertThat(metrics.rawEventLossCount()).isZero();
        assertThat(metrics.orphanSupportingCount()).isZero();
        assertThat(metrics.chapterStoryOverlapCount()).isZero();
        assertThat(metrics.reasonWithoutEvidenceCount()).isZero();
        assertThat(metrics.absolutePathOrSecretLeakCount()).isZero();
    }

    private JsonNode loadGroundTruth() throws Exception {
        try (InputStream input = getClass().getResourceAsStream(RESOURCE)) {
            assertThat(input).isNotNull();
            return objectMapper.readTree(input);
        }
    }

    private static int number(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (value instanceof Number number) return number.intValue();
        if (value == null) return 0;
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }

    static int failedOrPendingWindowCount(Map<String, Object> diagnostics) {
        return number(diagnostics, "failedWindowCount")
            + number(diagnostics, "modelUnprocessedWindowCount")
            + number(diagnostics, "skippedWindowCount")
            + number(diagnostics, "chapterSynthesisFailedCount")
            + number(diagnostics, "chapterSynthesisPendingCount");
    }

    private static boolean booleanValue(Map<String, Object> values, String key, boolean fallback) {
        Object value = values.get(key);
        if (value instanceof Boolean bool) return bool;
        if (value == null) return fallback;
        return Boolean.parseBoolean(value.toString());
    }

    static boolean modelExecutionDegraded(
        String modelStatus,
        int failedWindowCount,
        int unprocessedWindowCount,
        int skippedWindowCount,
        int chapterFailedCount,
        int chapterPendingCount
    ) {
        boolean validated = "MODEL_VALIDATED".equals(modelStatus)
            || "MODEL_VALIDATED_INCREMENTAL".equals(modelStatus);
        return !validated || failedWindowCount > 0 || unprocessedWindowCount > 0 || skippedWindowCount > 0
            || chapterFailedCount > 0 || chapterPendingCount > 0;
    }

    private static long elapsedMs(long started) {
        return Math.max(0, (System.nanoTime() - started) / 1_000_000L);
    }

    private record SafeCaseRun(
        String caseId,
        String split,
        int requestCount,
        long tokenCount,
        long latencyMs,
        boolean modelUsed,
        boolean refreshDegraded,
        boolean sourceCollectionComplete,
        boolean modelDegraded,
        boolean cacheHit,
        String modelStatus,
        int failedWindowCount,
        int unprocessedWindowCount,
        int skippedWindowCount,
        int chapterFailedCount,
        int chapterPendingCount,
        String initialModelStatus,
        int initialFailedOrPendingWindowCount,
        int initialValidationRepairCount,
        int initialValidationRepairFailureCount,
        int retryRefreshCount,
        boolean recoveredAfterRetry,
        int rejectedInvalidEvidenceCount,
        int rejectedCrossProjectCount,
        int rejectedUnsupportedClaimCount,
        int deterministicTitleFallbackCount,
        int validationRepairCount,
        int validationRepairFailureCount,
        List<SafeFailureDiagnostic> failureDiagnostics
    ) {
        private SafeCaseRun {
            failureDiagnostics = failureDiagnostics == null ? List.of() : List.copyOf(failureDiagnostics);
        }
    }

    private record SafeFailureDiagnostic(
        String scope,
        String failureClass,
        String failureStage,
        String failureCode,
        String repairFailureStage,
        String repairFailureCode,
        String validationKind,
        String validationCode,
        int requestCount
    ) {
    }

    private record QualificationSummary(
        boolean qualified,
        int requestCount,
        long tokenCount,
        long elapsedMs,
        int calibrationRequestCount,
        int holdoutRequestCount,
        int sourceCoverageIncompleteCaseCount,
        int refreshDegradedCaseCount,
        int modelDegradedCaseCount,
        int failedOrPendingWindowCount,
        int rejectedModelOutputCount,
        int deterministicTitleFallbackCount,
        int validationRepairCount,
        int validationRepairFailureCount,
        int initialFailedOrPendingWindowCount,
        int initialValidationRepairFailureCount,
        int retryRefreshCount,
        int retryAttemptedCaseCount,
        int recoveredAfterRetryCaseCount,
        int unresolvedAfterRetryCaseCount
    ) {
    }
}
