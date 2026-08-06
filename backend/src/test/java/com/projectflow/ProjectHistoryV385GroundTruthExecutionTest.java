package com.projectflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.eval.ProjectHistoryV385FixtureRunner;
import com.projectflow.eval.ProjectHistoryV385QualityEvaluator;
import com.projectflow.repository.ProjectFactRepository;
import com.projectflow.repository.ProjectHistoryEventRepository;
import com.projectflow.repository.ProjectHistorySnapshotRepository;
import com.projectflow.repository.ProjectMemoryRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.service.ProjectHistoryCorrectionService;
import com.projectflow.service.ProjectHistoryReadService;
import com.projectflow.service.ProjectHistoryReconstructionService;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProjectHistoryV385GroundTruthExecutionTest {
    private static final String RESOURCE = "/projectflow-v385/history-ground-truth.json";

    @Autowired ProjectRepository projectRepository;
    @Autowired ProjectMemoryRepository memoryRepository;
    @Autowired ProjectFactRepository factRepository;
    @Autowired ProjectHistoryEventRepository eventRepository;
    @Autowired ProjectHistorySnapshotRepository snapshotRepository;
    @Autowired ProjectHistoryReconstructionService reconstructionService;
    @Autowired ProjectHistoryCorrectionService correctionService;
    @Autowired ProjectHistoryReadService readService;
    @Autowired ObjectMapper objectMapper;

    @TempDir Path temporaryRoot;

    @Test
    void executesEveryFrozenCaseThroughProductionRefreshAndSeparatesCalibrationFromHoldout() throws Exception {
        JsonNode groundTruth = loadGroundTruth();
        ProjectHistoryV385FixtureRunner runner = new ProjectHistoryV385FixtureRunner(
            projectRepository, memoryRepository, factRepository, eventRepository, snapshotRepository,
            reconstructionService, correctionService, readService, objectMapper
        );
        UUID userId = UUID.randomUUID();
        List<ProjectHistoryV385QualityEvaluator.CaseObservation> observations = new ArrayList<>();
        int ordinal = 0;
        for (JsonNode testCase : groundTruth.path("cases")) {
            System.out.printf("V385_GROUND_TRUTH_START case=%s split=%s%n",
                testCase.path("id").asText(), testCase.path("split").asText());
            var observation = runner.run(userId, testCase, temporaryRoot.resolve("case-" + ++ordinal));
            observations.add(observation);
            System.out.printf(
                "V385_GROUND_TRUTH_DONE case=%s stories=%d chapters=%d threads=%d events=%d requests=%d%n",
                observation.caseId(), observation.stories().size(), observation.chapters().size(),
                observation.threads().size(), observation.events().size(), observation.modelRequestCount()
            );
        }

        var report = ProjectHistoryV385QualityEvaluator.evaluateCases(groundTruth, observations);
        Path output = Path.of("target", "projectflow-eval", "v385-deterministic");
        Files.createDirectories(output);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(
            output.resolve("history-ground-truth-result.json").toFile(), report
        );
        System.out.println("V385_GROUND_TRUTH_CALIBRATION " + objectMapper.writeValueAsString(report.calibration()));
        System.out.println("V385_GROUND_TRUTH_HOLDOUT " + objectMapper.writeValueAsString(report.holdout()));

        assertThat(observations).hasSize(groundTruth.path("cases").size());
        assertThat(report.missingCases()).isEmpty();
        assertSafetyGates(report.calibration());
        assertSafetyGates(report.holdout());
        assertThat(report.calibration().passes())
            .as("Calibration failures: %s", failures(report, "CALIBRATION"))
            .isTrue();
        assertThat(report.holdout().passes())
            .as("Holdout failures: %s", failures(report, "HOLDOUT"))
            .isTrue();
        assertThat(report.passes()).isTrue();
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

    private static List<String> failures(
        ProjectHistoryV385QualityEvaluator.EvaluationReport report,
        String split
    ) {
        return report.cases().stream().filter(value -> split.equals(value.split()))
            .filter(value -> !value.failures().isEmpty())
            .map(value -> value.caseId() + ": " + String.join("; ", value.failures())).toList();
    }
}
