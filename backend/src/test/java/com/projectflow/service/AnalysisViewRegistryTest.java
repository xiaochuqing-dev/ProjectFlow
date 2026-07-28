package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.projectflow.dto.ProjectUnderstandingDtos.EvidenceSourceMapResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.HistoricalCoverageResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.ProjectStructureIndexResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.RepositoryIntakeResponse;

class AnalysisViewRegistryTest {
    private final AnalysisViewRegistry registry = new AnalysisViewRegistry();

    @Test
    void documentWithoutSourceOrHistoryCannotRequestCodeOrEvolutionViews() {
        RepositoryIntakeResponse intake = mock(RepositoryIntakeResponse.class);
        ProjectStructureIndexResponse index = mock(ProjectStructureIndexResponse.class);
        EvidenceSourceMapResponse sourceMap = mock(EvidenceSourceMapResponse.class);
        HistoricalCoverageResponse history = mock(HistoricalCoverageResponse.class);
        when(intake.sourceFileCount()).thenReturn(0L);
        when(sourceMap.scoutEvidenceCount()).thenReturn(1);
        when(history.historyAvailable()).thenReturn(false);
        when(index.engineeringSignals()).thenReturn(Map.of());

        List<String> eligible = registry.eligible(intake, index, sourceMap, history);

        assertThat(eligible).contains("DOCUMENT_OVERVIEW", "CURRENTNESS", "CONFLICTS");
        assertThat(eligible).doesNotContain("ARCHITECTURE", "BACKEND", "EVOLUTION");
    }

    @Test
    void smallScriptCanExposeFocusedCodeViewsButNotMultiLayerArchitecture() {
        RepositoryIntakeResponse intake = mock(RepositoryIntakeResponse.class);
        ProjectStructureIndexResponse index = mock(ProjectStructureIndexResponse.class);
        EvidenceSourceMapResponse sourceMap = mock(EvidenceSourceMapResponse.class);
        HistoricalCoverageResponse history = mock(HistoricalCoverageResponse.class);
        when(intake.sourceFileCount()).thenReturn(1L);
        when(intake.estimatedLoc()).thenReturn(200L);
        when(sourceMap.scoutEvidenceCount()).thenReturn(0);
        when(history.historyAvailable()).thenReturn(false);
        when(index.engineeringSignals()).thenReturn(Map.of());

        List<String> eligible = registry.eligible(intake, index, sourceMap, history);

        assertThat(eligible).contains("PURPOSE", "INPUT_OUTPUT", "DEPENDENCIES", "USAGE");
        assertThat(eligible).doesNotContain("ARCHITECTURE", "EVOLUTION");
    }

    @Test
    void validationRejectsGenericAndIneligibleModelSections() {
        assertThat(registry.validate(
            List.of("current-state", "database", "summary", "EVOLUTION"),
            List.of("CURRENT_STATE", "DATA")
        )).containsExactly("CURRENT_STATE", "DATA");
    }

    @Test
    void eligibilityAloneNeverSelectsAViewForTheModel() {
        assertThat(registry.validate(
            List.of(),
            List.of("CURRENT_STATE", "CURRENTNESS", "CONFLICTS", "LIMITATIONS")
        )).isEmpty();
    }
}
