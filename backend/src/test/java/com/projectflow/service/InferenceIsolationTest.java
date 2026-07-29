package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class InferenceIsolationTest {
    @Test
    void likelyLanguageNeverReachesRecordedLayer() {
        var result = new StrongFactPromotionGuard().classify(StrongFactTestSupport.segment(
            "This likely exists to support a future platform",
            List.of("commit:abcdef1", "file:src/Main.java"), false
        ));

        assertThat(result.epistemicStatus().name()).isEqualTo("INFERRED");
        assertThat(result.recordStatus().name()).isEqualTo("NEEDS_ATTENTION");
    }
}
