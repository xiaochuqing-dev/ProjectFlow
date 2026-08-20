package com.projectflow.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class ProjectFlowRuntimeParityTest {
    @Test
    void realProviderPathsDoNotClampConfiguredRequestTimeout() throws Exception {
        List<Path> sources = List.of(
            Path.of("src/test/java/com/projectflow/eval/ProjectFlowRealProviderProbeIT.java"),
            Path.of("src/test/java/com/projectflow/eval/ProjectFlowRealModelEvalIT.java"),
            Path.of("src/test/java/com/projectflow/eval/ProjectUnderstandingRealModelIT.java")
        );

        for (Path source : sources) {
            String content = Files.readString(source);
            assertThat(content)
                .as(source.toString())
                .contains("config.timeoutSeconds()")
                .doesNotContain(
                    "Math.min(45",
                    "Math.min(120",
                    "Math.min(180",
                    "Math.max(30, config.timeoutSeconds())"
                );
        }
    }

    @Test
    void realProviderPathsPreserveExplicitJsonModeCapability() throws Exception {
        List<Path> sources = List.of(
            Path.of("src/test/java/com/projectflow/eval/ProjectFlowRealProviderProbeIT.java"),
            Path.of("src/test/java/com/projectflow/eval/ProjectFlowRealModelEvalIT.java"),
            Path.of("src/test/java/com/projectflow/eval/ProjectUnderstandingRealModelIT.java")
        );

        for (Path source : sources) {
            assertThat(Files.readString(source))
                .as(source.toString())
                .contains("config.supportsJsonMode()");
        }
        assertThat(Files.readString(
            Path.of("src/test/java/com/projectflow/eval/ProjectFlowRealModelEvalIT.java")
        )).contains("PROJECTFLOW_REAL_MODEL_SUPPORTS_JSON_MODE");
    }

    @Test
    void directEvalExecutesBoundedProviderWithoutGroundTruthToolEvidence() throws Exception {
        String content = Files.readString(
            Path.of("src/test/java/com/projectflow/eval/ProjectFlowRealModelEvalIT.java")
        );

        assertThat(content)
            .contains(
                "BoundedLocalAnalysisCapabilityProvider",
                "executeFixtureCapability",
                "capability-evidence.json",
                "HighValueEvidenceGate.decide"
            )
            .doesNotContain(
                "testCase.toolEvidence()",
                "value.toolEvidence()"
            );
    }

    @Test
    void realWorkflowCollectsThreeProviderSuitesAcrossBoundedSequentialPhases() throws Exception {
        String workflow = Files.readString(Path.of("../.github/workflows/quality-gates.yml"));

        assertThat(workflow)
            .contains(
                "optional-real-provider-qualification:",
                "optional-real-provider-scenarios:",
                "needs: optional-real-provider-qualification",
                "always() && github.event_name == 'workflow_dispatch' && inputs.run_real_model",
                "fail-fast: false",
                "- id: luna",
                "- id: deepseek",
                "- id: qwen",
                "secret_name: PROJECTFLOW_DEEPSEEK_API_KEY",
                "PROJECTFLOW_REAL_MODEL_API_KEY: ${{ secrets[matrix.secret_name] }}",
                "model: gpt-5.6-luna",
                "model: deepseek-v4-flash",
                "model: qwen3.7-plus",
                "protocol: OPENAI_RESPONSES",
                "protocol: OPENAI_CHAT_COMPLETIONS",
                "protocol: ANTHROPIC_MESSAGES",
                "supports_json_mode: \"true\"",
                "supports_reasoning_control: \"true\"",
                "reasoning_effort: max",
                "PROJECTFLOW_MODEL_REASONING_EFFORT: ${{ matrix.reasoning_effort }}",
                "dogfood_test: LunaDogfoodRegressionTest",
                "dogfood_test: DeepSeekDogfoodRegressionTest",
                "dogfood_test: QwenDogfoodRegressionTest",
                "run_gate history-v385-qualification",
                "run_gate history-v385-chapter-affected",
                "-Dprojectflow.eval.scenario-scope=chapter",
                "run_gate history-v385-scenarios",
                "run_gate history-v385-dogfood",
                "projectflow-real-model-qualification-${{ matrix.id }}",
                "projectflow-real-model-scenarios-${{ matrix.id }}"
            )
            .doesNotContain("model: deepseek-v4-pro", "model: glm-5.2", "- id: glm");
        assertThat(count(workflow, "timeout-minutes: 360")).isEqualTo(2);
        assertThat(count(workflow, "fail-fast: false")).isEqualTo(2);
        assertThat(count(workflow, "reasoning_effort: max")).isEqualTo(6);
        assertThat(count(workflow, "- id: luna")).isEqualTo(2);
        assertThat(count(workflow, "- id: deepseek")).isEqualTo(2);
        assertThat(count(workflow, "- id: qwen")).isEqualTo(2);
        assertThat(count(workflow, "run_gate provider-probe")).isEqualTo(1);
        assertThat(count(workflow, "run_gate history-v385-qualification")).isEqualTo(1);
        assertThat(count(workflow, "run_gate history-v385-scenarios")).isEqualTo(1);
        assertThat(count(workflow, "run_gate history-v385-dogfood")).isEqualTo(1);
    }

    private static int count(String value, String needle) {
        return value.split(java.util.regex.Pattern.quote(needle), -1).length - 1;
    }
}
