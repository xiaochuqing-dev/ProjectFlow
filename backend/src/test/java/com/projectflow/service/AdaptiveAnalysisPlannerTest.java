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
import com.projectflow.dto.ProjectUnderstandingDtos.ProjectShapeHypothesis;
import com.projectflow.dto.ProjectUnderstandingDtos.ProjectStructureIndexResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.RepositoryIntakeResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.SemanticScoutResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.StructureCoverage;

class AdaptiveAnalysisPlannerTest {
    @Test
    void objectiveEligibilityNeverBecomesAnUnrequestedToolOrView() {
        RepositoryIntakeResponse intake = mock(RepositoryIntakeResponse.class);
        when(intake.classification()).thenReturn("CODE");
        when(intake.scale()).thenReturn("SMALL");
        when(intake.sourceFileCount()).thenReturn(3L);
        when(intake.estimatedLoc()).thenReturn(1_000L);
        when(intake.manifestFiles()).thenReturn(List.of("package.json"));
        when(intake.git()).thenReturn(new GitEvidenceResponse(true, "main", "abc", 20, "CLEAN", 0));
        when(intake.supportedStructureCoverage()).thenReturn(0.8);

        ProjectStructureIndexResponse index = mock(ProjectStructureIndexResponse.class);
        when(index.indexerSource()).thenReturn("MANIFEST_FILESYSTEM");
        when(index.symbols()).thenReturn(List.of());
        when(index.unsupportedAreas()).thenReturn(List.of());
        when(index.engineeringSignals()).thenReturn(Map.of());
        when(index.coverage()).thenReturn(new StructureCoverage(1, 1, 1, 0, "NONE", 0.7));

        EvidenceSourceMapResponse sourceMap = new EvidenceSourceMapResponse(
            2, 2, 2, 0, 0,
            Map.of("MANIFEST", 1L, "README", 1L),
            List.of(),
            List.of(),
            null
        );
        HistoricalCoverageResponse history = mock(HistoricalCoverageResponse.class);
        when(history.historyAvailable()).thenReturn(true);
        when(history.availability()).thenReturn("MILESTONE_WINDOWS");
        when(history.tagCount()).thenReturn(2);

        SemanticScoutResponse scout = new SemanticScoutResponse(
            List.of(new ProjectShapeHypothesis("FRONTEND", "HIGH", List.of("intake:scan"), "模型判断")),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            true
        );

        var plan = new AdaptiveAnalysisPlanner(new AnalysisToolRegistry(), new AnalysisViewRegistry())
            .plan(intake, index, sourceMap, history, scout, true);

        assertThat(plan.eligibleCapabilities())
            .contains("MANIFEST", "GIT_HISTORY", "GIT_TAG", "DOC_READER");
        assertThat(plan.toolsToInvoke()).containsExactly("FILESYSTEM");
        assertThat(plan.eligibleViews()).contains("CURRENT_STATE", "FRONTEND", "HISTORICAL_COVERAGE");
        assertThat(plan.applicableDimensions()).isEmpty();
        assertThat(plan.toolSelectionRationales()).isEmpty();
    }
}
