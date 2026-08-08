package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.projectflow.entity.ProjectHistoryEvent.Transition;

class StoryHumanLanguageContractTest {
    private final ProjectHistoryLanguageService language = new ProjectHistoryLanguageService();

    @Test
    void firstLayerExplainsActionObjectAndResultWithoutInternalEnums() {
        var created = language.fallback(
            Transition.CREATED, "auth", List.of("frontend/login/page.tsx"), List.of("add login"), List.of("CREATED")
        );
        var restored = language.fallback(
            Transition.RESTORED, "export", List.of("reports/export.csv"), List.of("restore export"), List.of("RESTORED")
        );

        assertThat(created.title()).contains("新增", "登录流程", "可确认版本");
        assertThat(created.before()).contains("此前");
        assertThat(created.after()).contains("变化后");
        assertThat(restored.title()).contains("恢复", "成果导出", "重新出现");
        assertThat(String.join(" ", created.title(), created.summary(), created.before(), created.after()))
            .doesNotContain("PRIMARY", "SUPPORTING", "ENGINEERING_GROUPING", "Controller", "相关变化");
    }
}
