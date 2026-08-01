package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.dto.ProjectUnderstandingDtos.AdaptiveAnalysisPlanResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.EvidenceSourceMapResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.HistoricalCoverageResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.ProjectStructureIndexResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.RepositoryIntakeResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.SemanticScoutResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.StructureCoverage;
import com.projectflow.entity.ProjectSpace;

class DynamicProfileClaimLimitTest {
    @Test
    void finalSynthesisCannotExpandPastTheGlobalClaimLimit() throws Exception {
        ProjectSpace project = mock(ProjectSpace.class);
        when(project.getName()).thenReturn("Claim limit");
        RepositoryIntakeResponse intake = mock(RepositoryIntakeResponse.class);
        when(intake.classification()).thenReturn("CODE");
        when(intake.sourceFileCount()).thenReturn(1L);
        when(intake.fileCount()).thenReturn(1L);
        when(intake.estimatedLoc()).thenReturn(10L);
        when(intake.scale()).thenReturn("SMALL");
        when(intake.languageDistribution()).thenReturn(Map.of());
        when(intake.supportedStructureCoverage()).thenReturn(1.0);
        ProjectStructureIndexResponse index = mock(ProjectStructureIndexResponse.class);
        when(index.coverage()).thenReturn(new StructureCoverage(1, 1, 1, 0, "NONE", 1));
        when(index.symbols()).thenReturn(List.of());
        when(index.evidence()).thenReturn(List.of());
        when(index.engineeringSignals()).thenReturn(Map.of());
        HistoricalCoverageResponse history = mock(HistoricalCoverageResponse.class);
        when(history.historyAvailable()).thenReturn(false);
        EvidenceSourceMapResponse sourceMap = new EvidenceSourceMapResponse(
            1, 1, 1, 0, 0, Map.of("DOC", 1L), List.of(), List.of(), null
        );
        SemanticScoutResponse scout = mock(SemanticScoutResponse.class);
        when(scout.projectShapeHypotheses()).thenReturn(List.of());
        when(scout.unknowns()).thenReturn(List.of());
        when(scout.potentialConflicts()).thenReturn(List.of());
        when(scout.currentnessWarnings()).thenReturn(List.of());
        when(scout.applicableDimensions()).thenReturn(List.of("PURPOSE", "USAGE", "LIMITATIONS", "UNKNOWN"));
        when(scout.modelUsed()).thenReturn(true);
        AdaptiveAnalysisPlanResponse plan = mock(AdaptiveAnalysisPlanResponse.class);
        when(plan.eligibleViews()).thenReturn(List.of("PURPOSE", "USAGE", "LIMITATIONS", "UNKNOWN"));
        when(plan.skippedDimensions()).thenReturn(List.of());
        when(plan.unavailableCapabilities()).thenReturn(List.of());
        StringBuilder sections = new StringBuilder();
        for (int section = 0; section < 4; section++) {
            if (section > 0) sections.append(',');
            String type = List.of("PURPOSE", "USAGE", "LIMITATIONS", "UNKNOWN").get(section);
            sections.append("{\"id\":\"section-").append(section).append("\",\"type\":\"")
                .append(type).append("\",\"title\":\"T\",\"summary\":\"S\",\"claims\":[");
            for (int claim = 0; claim < 5; claim++) {
                if (claim > 0) sections.append(',');
                sections.append("{\"text\":\"claim-").append(section).append('-').append(claim)
                    .append("\",\"epistemicStatus\":\"INFERRED\",\"evidenceRefs\":[\"source:x\"]}");
            }
            sections.append("],\"displayPriority\":50}");
        }
        var root = new ObjectMapper().readTree(
            "{\"dynamicProfile\":{\"summary\":\"summary\",\"sections\":[" + sections
                + "]},\"stageTwoChanges\":[]}"
        );

        var result = new DynamicProjectProfileSynthesizer().synthesize(
            project, intake, index, sourceMap, history, scout, plan, root, Set.of("source:x")
        );

        long modelClaims = result.sections().stream().flatMap(section -> section.claims().stream())
            .filter(claim -> claim.id().startsWith("profile-model-"))
            .count();
        assertThat(modelClaims).isEqualTo(12);
        assertThat(result.sections().stream().filter(section -> section.id().startsWith("section-")))
            .allSatisfy(section -> assertThat(section.claims()).hasSizeLessThanOrEqualTo(3));
    }
}
