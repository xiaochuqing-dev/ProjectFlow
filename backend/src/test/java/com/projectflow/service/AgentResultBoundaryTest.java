package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class AgentResultBoundaryTest {
    @Test
    void agentResultClaimIsProcessEvidence() {
        var result = new StrongFactPromotionGuard().classify(StrongFactTestSupport.segment(
            "Agent 声称测试已经通过",
            List.of("agent-result:result-1"), true
        ));

        assertThat(result.epistemicStatus().name()).isEqualTo("PROCESS_EVIDENCE");
        assertThat(result.recordStatus().name()).isEqualTo("NEEDS_ATTENTION");
    }
}
