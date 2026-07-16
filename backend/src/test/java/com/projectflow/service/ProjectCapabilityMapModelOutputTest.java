package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.entity.ProjectCapabilityFactClassification;
import com.projectflow.entity.ProjectCapabilityMaturity;

class ProjectCapabilityMapModelOutputTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void validOutputClassifiesEveryFactAndAllowsTemporaryIdentity() throws Exception {
        String one = "00000000-0000-0000-0000-000000000001";
        String two = "00000000-0000-0000-0000-000000000002";
        String three = "00000000-0000-0000-0000-000000000003";
        var parsed = ProjectCapabilityMapService.parseChunk(mapper.readTree("""
            {"operations":[{"type":"NEW_CAPABILITY","temporaryKey":"TMP-A","canonicalName":"模型可靠性治理","summary":"统一处理模型失败。","problemSolved":"避免模型失败破坏结果","longTermValue":"长期保持模型入口稳定","productAreas":["模型"],"factIds":["%s"],"evolutionTitle":"形成模型治理","evolutionSummary":"形成统一模型治理能力"}],"noCapabilityChangeFactIds":["%s"],"attentionFacts":[{"factId":"%s","reason":"事实证据存在冲突"}]}
            """.formatted(one, two, three)), new LinkedHashSet<>(Set.of(one, two, three)), new LinkedHashSet<>());

        assertThat(parsed.operations()).hasSize(1);
        assertThat(parsed.classifications()).extracting(ProjectCapabilityMapService.FactClassification::classification)
            .containsExactly(ProjectCapabilityFactClassification.NO_CAPABILITY_CHANGE, ProjectCapabilityFactClassification.NEEDS_ATTENTION);
    }

    @Test
    void rejectsUnknownCrossScopeDuplicateAndMissingFactIds() throws Exception {
        String fact = "00000000-0000-0000-0000-000000000001";
        assertThatThrownBy(() -> ProjectCapabilityMapService.parseChunk(mapper.readTree("""
            {"operations":[],"noCapabilityChangeFactIds":["unknown"],"attentionFacts":[]}
            """), new LinkedHashSet<>(Set.of(fact)), new LinkedHashSet<>()))
            .isInstanceOf(ProjectCapabilityMapService.CapabilityCoverageException.class);
        assertThatThrownBy(() -> ProjectCapabilityMapService.parseChunk(mapper.readTree("""
            {"operations":[{"type":"ADD_EVIDENCE","capabilityId":"cap-1","factIds":["%s"]}],"noCapabilityChangeFactIds":["%s"],"attentionFacts":[]}
            """.formatted(fact, fact)), new LinkedHashSet<>(Set.of(fact)), new LinkedHashSet<>(Set.of("cap-1"))))
            .isInstanceOf(ProjectCapabilityMapService.CapabilityCoverageException.class);
        assertThatThrownBy(() -> ProjectCapabilityMapService.parseChunk(mapper.readTree("""
            {"operations":[],"noCapabilityChangeFactIds":[],"attentionFacts":[]}
            """), new LinkedHashSet<>(Set.of(fact)), new LinkedHashSet<>()))
            .isInstanceOf(ProjectCapabilityMapService.CapabilityCoverageException.class)
            .hasMessageContaining("omitted");
    }

    @Test
    void rejectsUnknownCapabilityPlanningAndModelMaturity() throws Exception {
        String fact = "00000000-0000-0000-0000-000000000001";
        assertThatThrownBy(() -> ProjectCapabilityMapService.parseChunk(mapper.readTree("""
            {"operations":[{"type":"ENHANCE_CAPABILITY","capabilityId":"missing","factIds":["%s"]}],"noCapabilityChangeFactIds":[],"attentionFacts":[]}
            """.formatted(fact)), new LinkedHashSet<>(Set.of(fact)), new LinkedHashSet<>(Set.of("known"))))
            .isInstanceOf(ProjectCapabilityMapService.CapabilityCoverageException.class)
            .hasMessageContaining("unknown capability");
        assertThatThrownBy(() -> ProjectCapabilityMapService.parseChunk(mapper.readTree("""
            {"maturityScore":87,"operations":[],"noCapabilityChangeFactIds":["%s"],"attentionFacts":[]}
            """.formatted(fact)), new LinkedHashSet<>(Set.of(fact)), new LinkedHashSet<>()))
            .isInstanceOf(ProjectCapabilityMapService.CapabilityCoverageException.class);
        assertThatThrownBy(() -> ProjectCapabilityMapService.parseChunk(mapper.readTree("""
            {"operations":[],"noCapabilityChangeFactIds":["%s"],"attentionFacts":[],"note":"下一步扩展能力"}
            """.formatted(fact)), new LinkedHashSet<>(Set.of(fact)), new LinkedHashSet<>()))
            .isInstanceOf(ProjectCapabilityMapService.CapabilityCoverageException.class);
    }

    @Test
    void bootstrapPlanningIsBoundedForLargeHistories() {
        assertThat(ProjectCapabilityMapService.plannedChunkCount(0)).isZero();
        assertThat(ProjectCapabilityMapService.plannedChunkCount(42)).isEqualTo(1);
        assertThat(ProjectCapabilityMapService.plannedChunkCount(230)).isEqualTo(2);
        assertThat(ProjectCapabilityMapService.plannedChunkCount(5_000)).isEqualTo(42);
    }

    @Test
    void maturityIsDeterministicAndAttentionPreventsInflation() {
        Instant start = Instant.parse("2025-01-01T00:00:00Z");
        assertThat(ProjectCapabilityMapService.maturity(1, 1, 1, 2, 0, 1, start, start)).isEqualTo(ProjectCapabilityMaturity.FORMING);
        assertThat(ProjectCapabilityMapService.maturity(3, 2, 3, 6, 0, 2, start, start.plusSeconds(10 * 86400L))).isEqualTo(ProjectCapabilityMaturity.FORMED);
        assertThat(ProjectCapabilityMapService.maturity(6, 3, 6, 12, 0, 3, start, start.plusSeconds(40 * 86400L))).isEqualTo(ProjectCapabilityMaturity.CONTINUOUSLY_ENHANCED);
        assertThat(ProjectCapabilityMapService.maturity(10, 4, 10, 25, 0, 5, start, start.plusSeconds(200 * 86400L))).isEqualTo(ProjectCapabilityMaturity.LONG_TERM_STABLE);
        assertThat(ProjectCapabilityMapService.maturity(10, 4, 10, 25, 1, 5, start, start.plusSeconds(200 * 86400L))).isEqualTo(ProjectCapabilityMaturity.FORMED);
    }
}
