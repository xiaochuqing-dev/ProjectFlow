package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class HistoricalReasonEvidenceTest {
    @Test
    void reasonRequiresExplicitReasonSource() {
        StrongFactPromotionGuard guard = new StrongFactPromotionGuard();
        var unsupported = guard.classify(StrongFactTestSupport.segment(
            "当初为了降低耦合而这样设计",
            List.of("commit:abcdef1", "file:src/Main.java"), false
        ));
        var supported = guard.classify(StrongFactTestSupport.segment(
            "当初为了降低耦合而这样设计",
            List.of("commit:abcdef1", "adr:docs/adr-1.md"), false
        ));

        assertThat(unsupported.recordStatus().name()).isEqualTo("NEEDS_ATTENTION");
        assertThat(supported.recordStatus().name()).isEqualTo("RECORDED");
    }
}
