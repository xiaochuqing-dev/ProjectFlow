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
}
