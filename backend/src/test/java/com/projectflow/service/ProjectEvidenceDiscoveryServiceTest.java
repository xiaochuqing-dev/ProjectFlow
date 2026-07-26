package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

class ProjectEvidenceDiscoveryServiceTest {
    @TempDir
    Path root;

    @Test
    void strangeNamedDocumentIsSampledAndSensitiveContentIsRedacted() throws Exception {
        Files.writeString(root.resolve("fuck-this-bug.md"), "# 线上事故复盘\n决定保留兼容路径。\n");
        Files.writeString(root.resolve("notes.txt"), "API_KEY=must-not-leak\n普通说明\n");
        Files.writeString(root.resolve("empty.txt"), "");
        Files.writeString(root.resolve(".env"), "SECRET=must-not-leak\n");

        LocalCommandExecutor commands = (directory, command, timeout) ->
            new LocalCommandExecutor.CommandResult(1, "", false);
        SccCodeMetricsAdapter scc = mock(SccCodeMetricsAdapter.class);
        when(scc.inspect(root)).thenReturn(SccCodeMetricsAdapter.CodeMetrics.unavailable());
        RepositoryIntakeService intake = new RepositoryIntakeService(
            commands,
            scc,
            new ObjectMapper().findAndRegisterModules()
        );
        ReflectionTestUtils.setField(intake, "maxFiles", 100);
        ReflectionTestUtils.setField(intake, "maxFileDetails", 100);
        ReflectionTestUtils.setField(intake, "maxFileReadBytes", 1024L * 1024L);
        ReflectionTestUtils.setField(intake, "maxTotalReadBytes", 16L * 1024L * 1024L);
        ReflectionTestUtils.setField(intake, "smallLoc", 20_000L);
        ReflectionTestUtils.setField(intake, "mediumLoc", 100_000L);
        ReflectionTestUtils.setField(intake, "largeLoc", 500_000L);
        ProjectEvidenceDiscoveryService discovery = new ProjectEvidenceDiscoveryService(new SensitiveContentRedactor());
        ReflectionTestUtils.setField(discovery, "maxCandidates", 100);
        ReflectionTestUtils.setField(discovery, "maxScoutEvidence", 80);
        ReflectionTestUtils.setField(discovery, "maxSampleChars", 1600);
        ReflectionTestUtils.setField(discovery, "maxSampleBytes", 8192);

        var result = discovery.discover(intake.scan(root));

        assertThat(result.promptEvidence())
            .extracting(ProjectEvidenceDiscoveryService.PromptEvidence::locator)
            .contains("fuck-this-bug.md", "notes.txt")
            .doesNotContain(".env", "empty.txt");
        assertThat(result.promptEvidence())
            .extracting(ProjectEvidenceDiscoveryService.PromptEvidence::boundedSample)
            .noneMatch(sample -> sample.contains("must-not-leak"))
            .anyMatch(sample -> sample.contains(SensitiveContentRedactor.REDACTED));
        assertThat(result.sourceMap().categoryCounts()).containsEntry("UNKNOWN_DOCUMENT", 3L);
    }

    @Test
    void manyDocumentsCannotCrowdOutManifestTestAndCiEvidence() throws Exception {
        Files.createDirectories(root.resolve("docs"));
        for (int index = 0; index < 40; index++) {
            Files.writeString(root.resolve("docs/note-" + index + ".md"), "# Note " + index + "\n内容\n");
        }
        Files.writeString(root.resolve("package.json"), "{\"scripts\":{\"test\":\"node --test\"}}");
        Files.createDirectories(root.resolve(".github/workflows"));
        Files.writeString(root.resolve(".github/workflows/ci.yml"), "name: ci\n");
        Files.createDirectories(root.resolve("tests"));
        Files.writeString(root.resolve("tests/example.test.js"), "test('x', () => {});\n");

        LocalCommandExecutor commands = (directory, command, timeout) ->
            new LocalCommandExecutor.CommandResult(1, "", false);
        SccCodeMetricsAdapter scc = mock(SccCodeMetricsAdapter.class);
        when(scc.inspect(root)).thenReturn(SccCodeMetricsAdapter.CodeMetrics.unavailable());
        RepositoryIntakeService intake = intake(commands, scc);
        ProjectEvidenceDiscoveryService discovery = new ProjectEvidenceDiscoveryService(new SensitiveContentRedactor());
        ReflectionTestUtils.setField(discovery, "maxCandidates", 100);
        ReflectionTestUtils.setField(discovery, "maxScoutEvidence", 5);
        ReflectionTestUtils.setField(discovery, "maxSampleChars", 1600);
        ReflectionTestUtils.setField(discovery, "maxSampleBytes", 8192);

        var result = discovery.discover(intake.scan(root));

        assertThat(result.promptEvidence())
            .extracting(ProjectEvidenceDiscoveryService.PromptEvidence::category)
            .contains("MANIFEST", "CI_CD", "TEST");
        assertThat(result.sourceMap().diversityMetrics().selectedByCategory())
            .containsKeys("MANIFEST", "CI_CD", "TEST", "UNKNOWN_DOCUMENT");
        assertThat(result.sourceMap().scoutEvidenceCount()).isLessThanOrEqualTo(5);
    }

    private RepositoryIntakeService intake(LocalCommandExecutor commands, SccCodeMetricsAdapter scc) {
        RepositoryIntakeService intake = new RepositoryIntakeService(
            commands,
            scc,
            new ObjectMapper().findAndRegisterModules()
        );
        ReflectionTestUtils.setField(intake, "maxFiles", 100);
        ReflectionTestUtils.setField(intake, "maxFileDetails", 100);
        ReflectionTestUtils.setField(intake, "maxFileReadBytes", 1024L * 1024L);
        ReflectionTestUtils.setField(intake, "maxTotalReadBytes", 16L * 1024L * 1024L);
        ReflectionTestUtils.setField(intake, "smallLoc", 20_000L);
        ReflectionTestUtils.setField(intake, "mediumLoc", 100_000L);
        ReflectionTestUtils.setField(intake, "largeLoc", 500_000L);
        return intake;
    }
}
