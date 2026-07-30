package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.projectflow.entity.ProjectFactEpistemicStatus;

class DeclaredFactValidationTest {
    @Test
    void designDocumentAloneRemainsDeclared() {
        var result = new StrongFactPromotionGuard().classify(StrongFactTestSupport.segment(
            "设计文档声明支持多项目",
            List.of("design-doc:docs/design.md"), false
        ));

        assertThat(result.epistemicStatus()).isEqualTo(ProjectFactEpistemicStatus.DECLARED);
        assertThat(result.reasons()).anyMatch(value -> value.contains("尚未经过工程验证"));
    }
}
