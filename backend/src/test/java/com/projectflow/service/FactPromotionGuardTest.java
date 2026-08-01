package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.projectflow.entity.ProjectFactEpistemicStatus;
import com.projectflow.entity.ProjectFactRecordStatus;

class FactPromotionGuardTest {
    private final StrongFactPromotionGuard guard = new StrongFactPromotionGuard();

    @Test
    void engineeringObservationMayBeRecordedButInferenceMayNot() {
        var observed = guard.classify(StrongFactTestSupport.segment(
            "提交与文件显示新增只读上下文接口",
            List.of("commit:abcdef1", "file:src/Context.java"), false
        ));
        var inferred = guard.classify(StrongFactTestSupport.segment(
            "当前结构可能是为了未来扩展而设计",
            List.of("commit:abcdef1", "file:src/Context.java"), false
        ));

        assertThat(observed.epistemicStatus()).isEqualTo(ProjectFactEpistemicStatus.OBSERVED);
        assertThat(observed.recordStatus()).isEqualTo(ProjectFactRecordStatus.RECORDED);
        assertThat(inferred.epistemicStatus()).isEqualTo(ProjectFactEpistemicStatus.INFERRED);
        assertThat(inferred.recordStatus()).isEqualTo(ProjectFactRecordStatus.NEEDS_ATTENTION);
    }

    @Test
    void modelSummaryAttentionAndFallbackCannotPromote() {
        for (String evidence : List.of(
            "model-summary:summary-1",
            "model-attention:attention-1",
            "model-phase:phase-1",
            "fallback:degraded-1"
        )) {
            var decision = guard.classify(StrongFactTestSupport.segment(
                "模型给出的当前项目判断",
                List.of(evidence),
                false
            ));
            assertThat(decision.recordStatus()).isEqualTo(ProjectFactRecordStatus.NEEDS_ATTENTION);
            assertThat(decision.epistemicStatus().isStrongFact()).isFalse();
        }
    }

    @Test
    void readmeObsidianMilestoneAndUserPhaseStayDeclared() {
        for (String evidence : List.of(
            "readme:main",
            "obsidian-note:roadmap",
            "user-milestone:beta",
            "user-phase:prototype"
        )) {
            var decision = guard.classify(StrongFactTestSupport.segment(
                "项目已进入 Beta 阶段",
                List.of(evidence),
                false
            ));
            assertThat(decision.epistemicStatus()).isEqualTo(ProjectFactEpistemicStatus.DECLARED);
            assertThat(decision.recordStatus()).isEqualTo(ProjectFactRecordStatus.NEEDS_ATTENTION);
        }
    }
}
