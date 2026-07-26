package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.projectflow.dto.ProjectUnderstandingDtos.EvidenceSourceMapResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.GitEvidenceResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.HistoricalCoverageResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.ProjectStructureIndexResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.RepositoryIntakeResponse;

class AnalysisToolRegistryTest {
    private final AnalysisToolRegistry registry = new AnalysisToolRegistry();

    @Test
    void gitAndDocumentPresenceDoNotForceEveryProjectIntoTwoStageExecution() {
        RepositoryIntakeResponse intake = mock(RepositoryIntakeResponse.class);
        when(intake.git()).thenReturn(new GitEvidenceResponse(true, "master", "abc", 1_000, "CLEAN", 0));
        when(intake.manifestFiles()).thenReturn(List.of("pom.xml"));
        ProjectStructureIndexResponse index = mock(ProjectStructureIndexResponse.class);
        when(index.indexerSource()).thenReturn("MANIFEST_FILESYSTEM");
        when(index.symbols()).thenReturn(List.of());
        EvidenceSourceMapResponse sourceMap = new EvidenceSourceMapResponse(
            2, 1, 1, 0, 0, Map.of("README", 1L), List.of(), List.of(), null
        );
        HistoricalCoverageResponse history = mock(HistoricalCoverageResponse.class);
        when(history.historyAvailable()).thenReturn(true);

        List<String> defaults = registry.defaults(intake, index, sourceMap, history);
        List<String> requested = registry.validateRequested(
            List.of("GIT_HISTORY", "DOC_READER"),
            defaults,
            intake,
            index,
            sourceMap
        );

        assertThat(defaults).containsExactly("FILESYSTEM");
        assertThat(requested).containsExactly("FILESYSTEM", "GIT_HISTORY", "DOC_READER");
    }
}
