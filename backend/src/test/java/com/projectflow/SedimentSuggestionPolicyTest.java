package com.projectflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.projectflow.entity.SedimentAction;
import com.projectflow.service.SedimentSuggestionPolicy;

class SedimentSuggestionPolicyTest {
    private final SedimentSuggestionPolicy policy = new SedimentSuggestionPolicy();
    private final UUID target = UUID.randomUUID();

    @Test
    void suggestsEvidenceOnlyForTestsAndDocsMatchingAnExistingSediment() {
        var suggestion = policy.suggest(
            "补充扫描指纹验证", "验证扫描指纹稳定复用", List.of("backend/ScanFingerprintTest.java", "docs/scan.md"),
            List.of("test:scan"), List.of(new SedimentSuggestionPolicy.ExistingSediment(target, "接入扫描指纹并稳定复用结果", "扫描指纹稳定复用"))
        );

        assertThat(suggestion.action()).isEqualTo(SedimentAction.EVIDENCE_ONLY);
        assertThat(suggestion.targetSedimentId()).isEqualTo(target);
    }

    @Test
    void suggestsMergeForTheSameProblemAndNewForIndependentCapability() {
        var existing = List.of(new SedimentSuggestionPolicy.ExistingSediment(target, "接入 GitHub CLI 远程状态", "远程状态辅助本地分析"));

        assertThat(policy.suggest("增强 GitHub CLI 状态", "远程状态辅助本地分析", List.of("GitHubCliService.java"), List.of("commit:a"), existing).action())
            .isEqualTo(SedimentAction.MERGE_EXISTING);
        assertThat(policy.suggest("新增能力卡片", "整体分析项目能力", List.of("CapabilityCard.java"), List.of("commit:b"), existing).action())
            .isEqualTo(SedimentAction.NEW_SEDIMENT);
    }
}
