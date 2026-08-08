package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.projectflow.entity.ProjectHistoryEvent.Category;
import com.projectflow.entity.ProjectHistoryEvent.Transition;

class GenericCommitGroupingTest {
    private final ProjectHistoryLanguageService language = new ProjectHistoryLanguageService();

    @Test
    void genericAndSupportOnlyCommitsAreCandidatesForNearbyResultsNotStandaloneClaims() {
        assertThat(language.supportingCommitLabel("fix")).isTrue();
        assertThat(language.supportingCommitLabel("update")).isTrue();
        assertThat(language.supportingCommitLabel("tests: cover login retry")).isTrue();
        assertThat(language.supportingCommitLabel("新增登录入口并支持邮箱验证")).isFalse();
        assertThat(language.supporting(
            List.of(Category.VALIDATION), List.of("tests/login.spec.ts"), List.of("tests"),
            List.of(Transition.MODIFIED), false
        )).isTrue();
        assertThat(language.supporting(
            List.of(Category.COMMIT), List.of("src/login/page.tsx"), List.of("新增登录入口"),
            List.of(Transition.CREATED), true
        )).isFalse();
    }
}
