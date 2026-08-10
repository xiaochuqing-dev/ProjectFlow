package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProjectHistoryUnknownHumanWordingTest {
    private final ProjectHistoryNarrativeEntailmentValidator validator = new ProjectHistoryNarrativeEntailmentValidator();

    @Test
    void firstLayerKeepsOneNaturalUnknownWithoutInternalTerms() {
        var unknowns = validator.normalizeUnknowns(
            "原因未知：输入未提供可核验的原因 Evidence。UNKNOWN", false
        );

        assertThat(unknowns).containsExactly("目前没有足够信息确认为什么做这次调整。");
        assertThat(String.join(" ", unknowns)).doesNotContain("UNKNOWN", "Evidence", "reason eligibility");
    }
}
