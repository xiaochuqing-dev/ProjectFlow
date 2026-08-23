package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.projectflow.entity.ProjectHistoryEvent.Transition;

class CommitHumanSummaryContractTest {
    private final ProjectHistoryLanguageService language = new ProjectHistoryLanguageService();

    @Test
    void genericCommitGetsDerivedReadableSummaryWhileRawLabelCanRemainSeparate() {
        String summary = language.commitSummary(
            "fix", Transition.MODIFIED, List.of("frontend/login/page.tsx", "tests/login.spec.ts")
        );
        String unknownScope = language.commitSummary(
            "update", Transition.MODIFIED, List.of("src/main/java/org/acme/Thing.java")
        );

        assertThat(summary).contains("完善登录流程", "保留原始提交信息供核对").doesNotContain("fix");
        assertThat(unknownScope).contains("完善项目材料", "保留原始提交信息供核对")
            .doesNotContain("update", "Thing.java");
    }
}
