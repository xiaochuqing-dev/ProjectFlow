package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.dto.ProjectUnderstandingDtos.EvidenceSourceAssessment;
import com.projectflow.dto.ProjectUnderstandingDtos.ProjectShapeHypothesis;
import com.projectflow.dto.ProjectUnderstandingDtos.SemanticToolRequest;

class SemanticContractDiagnosticsTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void smallEvidenceSetAndEveryCapabilityMustBeHandledExactlyOnce() throws Exception {
        var scout = mapper.readTree("""
            {"capabilityDecisions":[
              {"capability":"AGENT_RESULT","decision":"REQUEST"},
              {"capability":"GIT_HISTORY","decision":"REQUEST"}
            ]}
            """);
        var diagnostics = SemanticScoutService.contractDiagnostics(
            scout,
            Set.of("source:agent", "source:ci"),
            List.of("AGENT_RESULT", "GIT_HISTORY"),
            List.of(new ProjectShapeHypothesis("AGENT_RESULT_MATERIAL", "HIGH", List.of("source:agent"), "")),
            List.of(assessment("source:agent"), assessment("source:ci")),
            List.of(request("AGENT_RESULT", "source:agent"), request("GIT_HISTORY", "source:ci")),
            List.of("PROCESS_EVIDENCE", "HISTORICAL_COVERAGE")
        );

        assertThat(diagnostics.status()).isEqualTo("PASSED");
        assertThat(diagnostics.assessedEvidenceIds()).containsExactlyInAnyOrder("source:agent", "source:ci");
        assertThat(diagnostics.gaps()).isEmpty();
    }

    @Test
    void missingAssessmentHistoryAndAgentDeepReadAreExplicitlyDegraded() throws Exception {
        var scout = mapper.readTree("""
            {"capabilityDecisions":[
              {"capability":"AGENT_RESULT","decision":"SKIP"},
              {"capability":"GIT_HISTORY","decision":"SKIP"}
            ]}
            """);
        var diagnostics = SemanticScoutService.contractDiagnostics(
            scout,
            Set.of("source:agent", "source:ci"),
            List.of("AGENT_RESULT", "GIT_HISTORY"),
            List.of(new ProjectShapeHypothesis("AGENT_RESULT_MATERIAL", "HIGH", List.of("source:agent"), "")),
            List.of(assessment("source:agent")),
            List.of(),
            List.of("PROCESS_EVIDENCE", "HISTORICAL_COVERAGE")
        );

        assertThat(diagnostics.status()).isEqualTo("FAILED_DEGRADED");
        assertThat(diagnostics.unassessedEvidenceIds()).containsExactly("source:ci");
        assertThat(diagnostics.gaps()).contains(
            "SMALL_EVIDENCE_SET_NOT_FULLY_ASSESSED",
            "HISTORY_VIEW_WITHOUT_GIT_HISTORY_REQUEST",
            "PROCESS_EVIDENCE_VIEW_WITHOUT_AGENT_RESULT_REQUEST"
        );
    }

    @Test
    void duplicateAssessmentCannotMasqueradeAsCompleteCoverage() throws Exception {
        var scout = mapper.readTree("""
            {"capabilityDecisions":[{"capability":"DOC_READER","decision":"SKIP"}]}
            """);
        var diagnostics = SemanticScoutService.contractDiagnostics(
            scout,
            Set.of("source:doc"),
            List.of("DOC_READER"),
            List.of(new ProjectShapeHypothesis("DOCUMENT", "HIGH", List.of("source:doc"), "")),
            List.of(assessment("source:doc"), assessment("source:doc")),
            List.of(),
            List.of("DOCUMENT_OVERVIEW")
        );

        assertThat(diagnostics.gaps()).contains("SMALL_EVIDENCE_SET_ASSESSED_MORE_THAN_ONCE");
    }

    private static EvidenceSourceAssessment assessment(String evidenceId) {
        return new EvidenceSourceAssessment(
            evidenceId, "SOURCE", "HIGH", "CURRENT", true, false,
            "", "需要校验", List.of(), "HIGH"
        );
    }

    private static SemanticToolRequest request(String capability, String evidenceId) {
        return new SemanticToolRequest(
            capability, "缺少规范证据", "补足规范证据", List.of(evidenceId), "当前只有压缩摘要"
        );
    }
}
