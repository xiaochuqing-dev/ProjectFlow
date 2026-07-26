package com.projectflow.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class ProjectFlowEvalProductBoundaryTest {
    private static final List<String> FORBIDDEN_PRODUCT_FIELDS = List.of(
        "hallucinationRate",
        "analysisAccuracy",
        "modelScore",
        "criticalEvidenceRecall",
        "toolSelectionPrecision",
        "secondStageGain"
    );

    @Test
    void internalEvaluationFieldsDoNotEnterProductionSource() throws Exception {
        Path source = Path.of("src", "main");
        try (var paths = Files.walk(source)) {
            List<Path> files = paths.filter(Files::isRegularFile).toList();
            for (Path file : files) {
                String content = Files.readString(file);
                for (String forbidden : FORBIDDEN_PRODUCT_FIELDS) {
                    assertThat(content)
                        .as("%s must remain internal and not appear in %s", forbidden, file)
                        .doesNotContain(forbidden);
                }
            }
        }
    }
}
