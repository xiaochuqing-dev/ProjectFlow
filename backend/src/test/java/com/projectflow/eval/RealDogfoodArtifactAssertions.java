package com.projectflow.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assumptions;

final class RealDogfoodArtifactAssertions {
    private RealDogfoodArtifactAssertions() {
    }

    static void assertQualified(String expectedProtocol) throws Exception {
        String outputName = System.getProperty("projectflow.eval.output-name", "").trim();
        Assumptions.assumeTrue(!outputName.isBlank(), "仅在真实模型 profile 提供工件名时执行");
        Path artifact = Path.of("target", "projectflow-eval", outputName, "history-real-scenarios.json");
        assertThat(Files.isRegularFile(artifact)).as("real scenario artifact").isTrue();
        JsonNode root = new ObjectMapper().readTree(artifact.toFile());

        assertThat(root.path("provider").path("protocol").asText()).isEqualTo(expectedProtocol);
        assertThat(root.path("qualification").path("qualified").asBoolean()).isTrue();
        JsonNode dogfood = null;
        for (JsonNode scenario : root.path("scenarios")) {
            if ("projectflow-current-history-dogfood".equals(scenario.path("name").asText())) {
                dogfood = scenario;
                break;
            }
        }
        assertThat(dogfood).as("ProjectFlow dogfood scenario").isNotNull();
        assertThat(dogfood.path("status").asText()).isEqualTo("PASS");
        assertThat(dogfood.path("failure").asText()).isBlank();
    }
}
