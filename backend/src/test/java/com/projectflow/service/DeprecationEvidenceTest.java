package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class DeprecationEvidenceTest {
    @Test
    void deprecationNeedsMarkerReplacementDeletionOrMigrationEvidence() {
        StrongFactPromotionGuard guard = new StrongFactPromotionGuard();
        assertThat(guard.classify(StrongFactTestSupport.segment(
            "旧接口已经废弃", List.of("commit:abcdef1"), false
        )).recordStatus().name()).isEqualTo("NEEDS_ATTENTION");
        assertThat(guard.classify(StrongFactTestSupport.segment(
            "旧接口已经废弃", List.of("commit:abcdef1", "deprecated:src/Old.java"), false
        )).recordStatus().name()).isEqualTo("RECORDED");
    }
}
