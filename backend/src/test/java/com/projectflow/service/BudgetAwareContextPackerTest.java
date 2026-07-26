package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

class BudgetAwareContextPackerTest {
    @Test
    void keepsCompleteJsonAndReservedCategoriesUnderGlobalBudget() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        BudgetAwareContextPacker packer = new BudgetAwareContextPacker(mapper, new SensitiveContentRedactor());
        ReflectionTestUtils.setField(packer, "maxChars", 12_000);
        Map<String, Object> sections = new LinkedHashMap<>();
        sections.put("projectIntake", Map.of("classification", "MONOREPO", "files", 100_000));
        sections.put("manifests", List.of("package.json", "pom.xml", "pyproject.toml"));
        sections.put("documents", repeated("document", 80, 300));
        sections.put("structure", repeated("structure", 80, 300));
        sections.put("git", List.of(Map.of("commits", 10_000)));
        sections.put("historicalCoverage", Map.of("overall", 0.25));
        sections.put("unknownsAndConflicts", List.of("README 可能过期"));
        sections.put("toolResults", repeated("tool", 30, 300));

        var packed = packer.pack(sections, 12_000);

        assertThat(packed.json().length()).isLessThanOrEqualTo(12_000);
        assertThat(mapper.readTree(packed.json()).isObject()).isTrue();
        assertThat(packed.diagnostics().validJson()).isTrue();
        assertThat(packed.diagnostics().selectedItems().keySet())
            .contains("projectIntake", "manifests", "documents", "structure", "git", "toolResults");
        assertThat(packed.diagnostics().droppedItems().values()).anyMatch(value -> value > 0);
    }

    @Test
    void performsOutboundSecretScanBeforeSerialization() {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        BudgetAwareContextPacker packer = new BudgetAwareContextPacker(mapper, new SensitiveContentRedactor());
        ReflectionTestUtils.setField(packer, "maxChars", 12_000);
        String token = "gh" + "p_abcdefghijklmnopqrstuvwxyz1234567890AB";

        var packed = packer.pack(Map.of("documents", List.of(Map.of("content", token))), 12_000);

        assertThat(packed.json()).doesNotContain(token).contains(SensitiveContentRedactor.REDACTED);
    }

    private static List<String> repeated(String prefix, int count, int length) {
        List<String> result = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            result.add(prefix + "-" + index + "-" + "x".repeat(length));
        }
        return result;
    }
}
