package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.projectflow.dto.ProjectUnderstandingDtos.AdaptiveAnalysisPlanResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.AnalysisToolEvidenceResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.EvidenceSourceMapResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.ProjectEvidenceSourceResponse;
import com.projectflow.service.ProjectEvidenceDiscoveryService.PromptEvidence;

class HighValueEvidenceGateTest {
    @Test
    void substantiveDeepContentTriggersSecondStage() {
        var decision = HighValueEvidenceGate.decide(
            plan(2),
            evidence("tool:doc", "DOC_READER"),
            prompt("tool:doc", "本次深读发现了此前样本没有覆盖的具体约束。".repeat(20))
        );

        assertThat(decision.secondStageTriggered()).isTrue();
        assertThat(decision.evidenceIds()).containsExactly("tool:doc");
        assertThat(decision.triggerReasons()).containsExactly("NEW_DEEP_CONTENT:tool:doc");
    }

    @Test
    void metadataShortAndDuplicateContentDoNotTrigger() {
        String duplicate = "相同的工具输出只应被计算一次。".repeat(30);
        var decision = HighValueEvidenceGate.decide(
            plan(2),
            List.of(
                tool("tool:worktree", "WORKTREE"),
                tool("tool:doc-1", "DOC_READER"),
                tool("tool:doc-2", "DOC_READER")
            ),
            List.of(
                promptItem("tool:worktree", "working tree clean"),
                promptItem("tool:doc-1", duplicate),
                promptItem("tool:doc-2", duplicate)
            )
        );

        assertThat(decision.secondStageTriggered()).isTrue();
        assertThat(decision.evidenceIds()).containsExactly("tool:doc-1");
        assertThat(decision.skippedReasons())
            .contains("INSUFFICIENT_CONTENT:tool:worktree", "DUPLICATE_TOOL_CONTENT:tool:doc-2");
    }

    @Test
    void modelBudgetPreventsTriggerEvenWithStrongEvidence() {
        var decision = HighValueEvidenceGate.decide(
            plan(1),
            evidence("tool:doc", "DOC_READER"),
            prompt("tool:doc", "高价值正文".repeat(80))
        );

        assertThat(decision.secondStageTriggered()).isFalse();
        assertThat(decision.skippedReasons()).containsExactly("MODEL_REQUEST_BUDGET_LIMIT");
    }

    @Test
    void processMetadataCapabilityCannotUpgradeSemanticClaims() {
        var decision = HighValueEvidenceGate.decide(
            plan(2),
            evidence("tool:metrics", "PROCESS_METADATA"),
            prompt("tool:metrics", "token=2000 latency=1200 requestCount=2 model=example".repeat(12))
        );

        assertThat(decision.secondStageTriggered()).isFalse();
        assertThat(decision.skippedReasons()).contains("NO_NEW_SEMANTIC_VALUE:tool:metrics");
    }

    @Test
    void validatedConflictEvidenceCanTriggerWithoutPretendingItIsDeepContent() {
        var decision = HighValueEvidenceGate.decide(
            plan(2),
            evidence("tool:filesystem", "FILESYSTEM"),
            prompt("tool:filesystem", "README 与当前源码存在冲突，旧说明已过期。".repeat(20))
        );

        assertThat(decision.secondStageTriggered()).isTrue();
        assertThat(decision.triggerReasons())
            .containsExactly("CONFLICT_OR_CURRENTNESS_EVIDENCE:tool:filesystem");
    }

    @Test
    void contentAlreadyKnownToStageOneDoesNotTrigger() {
        String known = "第一阶段已经完整读取并持有的同一段内容。".repeat(20);
        EvidenceSourceMapResponse sourceMap = mock(EvidenceSourceMapResponse.class);
        when(sourceMap.sources()).thenReturn(List.of(new ProjectEvidenceSourceResponse(
            "source:known", "DOC", "MARKDOWN", "docs/known.md", "CURRENT_CONTEXT",
            "HIGH", "CURRENT", "HIGH", "SAMPLED", known, List.of("source:known")
        )));

        var decision = HighValueEvidenceGate.decide(
            plan(2),
            evidence("tool:doc", "DOC_READER"),
            prompt("tool:doc", known),
            sourceMap
        );

        assertThat(decision.secondStageTriggered()).isFalse();
        assertThat(decision.skippedReasons()).contains("KNOWN_STAGE_ONE_CONTENT:tool:doc");
    }

    private static AdaptiveAnalysisPlanResponse plan(int requests) {
        AdaptiveAnalysisPlanResponse plan = mock(AdaptiveAnalysisPlanResponse.class);
        when(plan.maxModelRequests()).thenReturn(requests);
        return plan;
    }

    private static List<AnalysisToolEvidenceResponse> evidence(String id, String capability) {
        return List.of(tool(id, capability));
    }

    private static AnalysisToolEvidenceResponse tool(String id, String capability) {
        return new AnalysisToolEvidenceResponse(
            id, capability, "TOOL_RESULT", capability + "_RESULT", "摘要", List.of("source:base")
        );
    }

    private static List<PromptEvidence> prompt(String id, String content) {
        return List.of(promptItem(id, content));
    }

    private static PromptEvidence promptItem(String id, String content) {
        return new PromptEvidence(id, "TOOL_RESULT", "RESULT", "docs/test.md", "摘要", content);
    }
}
