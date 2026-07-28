package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.dto.ProjectUnderstandingDtos.StructureEvidence;
import com.projectflow.service.ProjectEvidenceDiscoveryService.PromptEvidence;

class SemanticScoutPromptContextTest {
    private static final List<String> CATEGORIES = List.of(
        "MANIFEST", "CI_CD", "TEST", "MIGRATION", "INFRA",
        "PRODUCT_CONTEXT", "AGENT_CONTEXT", "AGENT_RESULT",
        "README", "UNKNOWN_DOCUMENT", "ADR", "CHANGELOG",
        "CONFIG", "BUILD", "LICENSE"
    );

    @Test
    void retainsEverySelectedEvidenceSummaryButOnlyDiverseBoundedSamples() throws Exception {
        List<PromptEvidence> evidence = new ArrayList<>();
        for (int index = 0; index < 40; index++) {
            String category = CATEGORIES.get(index % CATEGORIES.size());
            evidence.add(new PromptEvidence(
                "source:" + index,
                category,
                category + "_CANDIDATE",
                "module-" + (index % 9) + "/very-long-location-" + "x".repeat(100) + index,
                "有界来源摘要-" + index + "-" + "摘".repeat(160),
                "正文样本-" + index + "-" + "样".repeat(800)
            ));
        }

        List<Map<String, Object>> compact = SemanticScoutService.compactPromptEvidence(evidence);

        assertThat(compact).hasSize(40);
        assertThat(compact)
            .extracting(item -> item.get("id"))
            .containsExactlyElementsOf(evidence.stream().map(PromptEvidence::id).toList());
        assertThat(compact)
            .allSatisfy(item -> {
                assertThat(item).containsKeys("id", "category", "locator", "summary");
                assertThat(item.get("summary").toString()).hasSizeLessThanOrEqualTo(96);
                assertThat(item.get("locator").toString()).hasSizeLessThanOrEqualTo(80);
            });
        assertThat(compact.stream().filter(item -> item.containsKey("boundedSample")))
            .hasSize(8)
            .allSatisfy(item ->
                assertThat(item.get("boundedSample").toString()).hasSizeLessThanOrEqualTo(240)
            );
        assertThat(new ObjectMapper().writeValueAsString(compact).length()).isLessThan(13_000);
    }

    @Test
    void structureEvidenceKeepsPriorityKindAndModuleDiversityWithinBound() {
        List<StructureEvidence> evidence = new ArrayList<>();
        for (int index = 0; index < 64; index++) {
            evidence.add(new StructureEvidence(
                "structure:" + index,
                "KIND_" + (index % 5),
                "module-" + (index % 8) + "/path-" + index,
                "结构摘要-" + index
            ));
        }
        Set<String> priority = new LinkedHashSet<>(List.of(
            "structure:3", "structure:27", "structure:51"
        ));

        List<Map<String, Object>> compact =
            SemanticScoutService.compactStructureEvidence(evidence, priority);

        assertThat(compact).hasSizeLessThanOrEqualTo(16);
        assertThat(compact)
            .extracting(item -> item.get("id"))
            .containsAll(priority);
        assertThat(compact)
            .extracting(item -> item.get("kind"))
            .contains("KIND_0", "KIND_1", "KIND_2", "KIND_3", "KIND_4");
        assertThat(compact.stream()
            .map(item -> item.get("path").toString().split("/", 2)[0])
            .distinct()
            .count()).isGreaterThanOrEqualTo(7);
    }
}
