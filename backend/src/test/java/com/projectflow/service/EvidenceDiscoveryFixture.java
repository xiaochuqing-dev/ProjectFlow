package com.projectflow.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;

import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

final class EvidenceDiscoveryFixture {
    private EvidenceDiscoveryFixture() {
    }

    static ProjectEvidenceDiscoveryService.DiscoveryResult discover(Path root) {
        LocalCommandExecutor commands = (directory, command, timeout) ->
            new LocalCommandExecutor.CommandResult(1, "", false);
        SccCodeMetricsAdapter scc = mock(SccCodeMetricsAdapter.class);
        when(scc.inspect(root)).thenReturn(SccCodeMetricsAdapter.CodeMetrics.unavailable());
        RepositoryIntakeService intake = new RepositoryIntakeService(
            commands, scc, new ObjectMapper().findAndRegisterModules()
        );
        ReflectionTestUtils.setField(intake, "maxFiles", 100);
        ReflectionTestUtils.setField(intake, "maxFileDetails", 100);
        ReflectionTestUtils.setField(intake, "maxFileReadBytes", 4L * 1024L * 1024L);
        ReflectionTestUtils.setField(intake, "maxTotalReadBytes", 16L * 1024L * 1024L);
        ReflectionTestUtils.setField(intake, "smallLoc", 20_000L);
        ReflectionTestUtils.setField(intake, "mediumLoc", 100_000L);
        ReflectionTestUtils.setField(intake, "largeLoc", 500_000L);

        ProjectEvidenceDiscoveryService discovery =
            new ProjectEvidenceDiscoveryService(new SensitiveContentRedactor());
        ReflectionTestUtils.setField(discovery, "maxCandidates", 100);
        ReflectionTestUtils.setField(discovery, "maxScoutEvidence", 80);
        ReflectionTestUtils.setField(discovery, "maxSampleChars", 2_000);
        ReflectionTestUtils.setField(discovery, "maxSampleBytes", 16_384);
        return discovery.discover(intake.scan(root));
    }
}
