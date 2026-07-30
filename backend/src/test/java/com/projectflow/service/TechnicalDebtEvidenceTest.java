package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class TechnicalDebtEvidenceTest {
    @Test
    void technicalDebtNeedsConcreteGapEvidence() {
        StrongFactPromotionGuard guard = new StrongFactPromotionGuard();
        assertThat(guard.classify(StrongFactTestSupport.segment(
            "这里存在尚未解决的技术债", List.of("commit:abcdef1"), false
        )).recordStatus().name()).isEqualTo("NEEDS_ATTENTION");
        assertThat(guard.classify(StrongFactTestSupport.segment(
            "这里存在尚未解决的技术债", List.of("commit:abcdef1", "todo:src/Main.java:10"), false
        )).recordStatus().name()).isEqualTo("RECORDED");
    }
}
