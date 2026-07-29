package com.projectflow.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.eval.ProjectFlowEvalObservation.EvalClaim;
import com.projectflow.service.AnalysisViewRegistry;
import com.projectflow.service.ProjectUnderstandingPromptBuilder;

class V374DatasetSeparationTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void calibrationCoversRequiredShapesAndHoldoutIsIdentitySeparated() throws Exception {
        ProjectFlowEvalGroundTruth calibration = load(
            "/projectflow-eval/v374-calibration-ground-truth.json"
        );
        ProjectFlowEvalGroundTruth holdout = load(
            "/projectflow-eval/v374-holdout-ground-truth.json"
        );

        Set<String> calibrationIds = ids(calibration);
        Set<String> holdoutIds = ids(holdout);
        assertThat(calibrationIds).hasSizeGreaterThanOrEqualTo(18);
        assertThat(holdoutIds).hasSizeGreaterThanOrEqualTo(6);
        assertThat(calibrationIds).allMatch(id -> id.startsWith("cal-"));
        assertThat(holdoutIds).allMatch(id -> id.startsWith("holdout-"));
        assertThat(calibrationIds).doesNotContainAnyElementsOf(holdoutIds);
        assertThat(calibrationIds).contains(
            "cal-frontend", "cal-backend", "cal-fullstack", "cal-cli", "cal-scripts",
            "cal-notebook", "cal-desktop", "cal-monorepo", "cal-infrastructure",
            "cal-doc-research", "cal-agent-driven", "cal-legacy", "cal-unfinished",
            "cal-no-git", "cal-chaotic", "cal-long-file", "cal-conflicting-docs",
            "cal-portfolio"
        );
        assertContractVocabulary(calibration);
        assertContractVocabulary(holdout);
    }

    @Test
    void holdoutLabelsNeverEnterProductionPromptAndBuilderHasNoCaseSpecificRules() throws Exception {
        ProjectFlowEvalGroundTruth holdout = load(
            "/projectflow-eval/v374-holdout-ground-truth.json"
        );
        String builderSource = java.nio.file.Files.readString(
            java.nio.file.Path.of(
                "src/main/java/com/projectflow/service/ProjectUnderstandingPromptBuilder.java"
            )
        );
        ProjectUnderstandingPromptBuilder builder = new ProjectUnderstandingPromptBuilder();
        for (var value : holdout.cases()) {
            var sentinel = new ProjectFlowEvalGroundTruth.EvalCase(
                "HOLDOUT_LABEL_SENTINEL",
                value.source(),
                value.important(),
                value.context(),
                "HOLDOUT_TOOL_LABEL_SENTINEL",
                java.util.List.of("HOLDOUT_SHAPE_LABEL_SENTINEL"),
                value.mustFindEvidence(),
                java.util.List.of("HOLDOUT_FORBIDDEN_LABEL_SENTINEL"),
                value.expectedTools(),
                value.forbiddenTools(),
                java.util.List.of("HOLDOUT_VIEW_LABEL_SENTINEL"),
                value.forbiddenViews(),
                java.util.List.of("HOLDOUT_UNKNOWN_LABEL_SENTINEL"),
                java.util.List.of("HOLDOUT_CONFLICT_LABEL_SENTINEL"),
                "HOLDOUT_HISTORY_LABEL_SENTINEL",
                value.expectedDeepReadTargets()
            );
            String prompt = ProjectFlowRealModelEvalIT.buildScoutPrompt(mapper, builder, sentinel);
            assertThat(prompt).doesNotContain("HOLDOUT_", value.id());
            assertThat(builderSource).doesNotContain(value.id());
        }
    }

    @Test
    void deepReadExpectationsHaveSeparateSourceContentAndARealInformationGap() throws Exception {
        ProjectFlowEvalGroundTruth calibration = load(
            "/projectflow-eval/v374-calibration-ground-truth.json"
        );
        ProjectFlowEvalGroundTruth holdout = load(
            "/projectflow-eval/v374-holdout-ground-truth.json"
        );
        assertCapabilityFixtures(
            calibration,
            "/projectflow-eval/v374-calibration-capability-evidence.json"
        );
        assertCapabilityFixtures(
            holdout,
            "/projectflow-eval/v374-holdout-capability-evidence.json"
        );
    }

    @Test
    void evaluationAppliesTheSameEvidenceAllowListWithoutHidingUnsupportedClaims() {
        var claims = ProjectFlowRealModelEvalIT.filterClaimEvidence(
            java.util.List.of(
                new EvalClaim(
                    "有一个合法来源",
                    "CURRENT_STATE",
                    "OBSERVED",
                    java.util.List.of("source:known", "source:invented"),
                    false,
                    false
                ),
                new EvalClaim(
                    "只有非法来源",
                    "CURRENT_STATE",
                    "OBSERVED",
                    java.util.List.of("source:invented"),
                    false,
                    false
                )
            ),
            Set.of("source:known")
        );
        assertThat(claims.get(0).evidenceRefs()).containsExactly("source:known");
        assertThat(claims.get(1).evidenceRefs()).isEmpty();
        assertThat(claims.get(1).epistemicStatus()).isEqualTo("OBSERVED");
    }

    @Test
    void conflictMetricsUseGenericCanonicalTopicsInsteadOfFreeFormExactText() {
        var values = mapper.createArrayNode();
        values.addObject().put(
            "text",
            "source:guide-a 声明 SQLite，source:guide-b 声明 PostgreSQL"
        );
        values.addObject().put(
            "text",
            "Agent 声称迁移完成，但 CI verification FAILED"
        );
        values.addObject().put(
            "text",
            "README 声明端口 8080，而运行配置为 9090，存在冲突"
        );
        assertThat(ProjectFlowRealModelEvalIT.normalizedConflictLabels(values))
            .containsExactly(
                "DATABASE_DEFAULT_CONFLICT",
                "AGENT_VERIFICATION_CONFLICT",
                "README_SOURCE_CONFLICT"
            );
    }

    private ProjectFlowEvalGroundTruth load(String resource) throws Exception {
        try (InputStream input = getClass().getResourceAsStream(resource)) {
            assertThat(input).isNotNull();
            return mapper.readValue(input, ProjectFlowEvalGroundTruth.class);
        }
    }

    private static Set<String> ids(ProjectFlowEvalGroundTruth set) {
        return set.cases().stream()
            .map(ProjectFlowEvalGroundTruth.EvalCase::id)
            .collect(Collectors.toSet());
    }

    private static void assertContractVocabulary(ProjectFlowEvalGroundTruth set) {
        Set<String> shapes = Set.of(
            "DOCUMENT", "SCRIPT", "FRONTEND", "BACKEND", "DESKTOP", "MONOREPO",
            "CODE_PROJECT", "LARGE_REPOSITORY", "AGENT_RESULT_MATERIAL",
            "PROCESS_METADATA", "OTHER_MATERIAL", "DEVELOPER_WORKBENCH"
        );
        assertThat(set.cases().stream()
            .flatMap(value -> value.expectedProjectShapes().stream()))
            .allMatch(shapes::contains);
        assertThat(set.cases().stream()
            .flatMap(value -> value.expectedViews().stream())
            .map(AnalysisViewRegistry::normalize))
            .allMatch(AnalysisViewRegistry.registered()::contains);
    }

    private void assertCapabilityFixtures(
        ProjectFlowEvalGroundTruth set,
        String resource
    ) throws Exception {
        com.fasterxml.jackson.databind.JsonNode fixtures;
        try (InputStream input = getClass().getResourceAsStream(resource)) {
            assertThat(input).isNotNull();
            fixtures = mapper.readTree(input);
        }
        Set<String> required = new HashSet<>();
        for (var value : set.cases()) {
            boolean contentRead = value.expectedTools().stream()
                .anyMatch(tool -> Set.of("DOC_READER", "AGENT_RESULT").contains(tool));
            if (contentRead) {
                required.add(value.id());
                assertThat(value.expectedDeepReadTargets()).isNotEmpty();
                assertThat(value.context().toLowerCase())
                    .containsAnyOf("未采样", "尚未读取", "未进入 intake");
            } else if (value.expectedTools().isEmpty()) {
                assertThat(value.expectedDeepReadTargets()).isEmpty();
            }
        }
        assertThat(fixtures.properties().stream()
            .map(java.util.Map.Entry::getKey)
            .collect(Collectors.toSet()))
            .isEqualTo(required);
        fixtures.properties().forEach(entry ->
            assertThat(entry.getValue().asText()).doesNotContain(
                "expectedProjectShapes", "expectedViews", "mustNotClaim", "HOLDOUT_"
            )
        );
    }
}
