package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CrossChunkFactMergeTest {
    @TempDir
    Path root;

    @Test
    void rangesKeepOneSourceHashAndDistinctLocations() throws Exception {
        Path target = root.resolve("temp-final-final.md");
        Files.writeString(target, IntStream.rangeClosed(1, 3_000)
            .mapToObj(line -> {
                if (line == 20) return "FACT_A strong facts require evidence";
                if (line == 1_500) return "FACT_B candidates stay separate";
                if (line == 2_999) return "FACT_C tail supersedes the old declaration";
                return "padding " + line;
            }).reduce((left, right) -> left + "\n" + right).orElse(""));
        LargeFileContentService service = new LargeFileContentService(new SensitiveContentRedactor());

        var map = service.analyze(target, 20_000);

        assertThat(map.samples()).extracting(LargeFileContentService.RangeSample::sourceHash)
            .containsOnly(map.sourceHash());
        assertThat(map.samples()).extracting(LargeFileContentService.RangeSample::startLine)
            .doesNotHaveDuplicates();
        assertThat(service.toPromptText(map, 20_000)).contains("FACT_A", "FACT_B", "FACT_C");
    }
}
