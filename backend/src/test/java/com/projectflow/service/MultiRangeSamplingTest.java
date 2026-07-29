package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MultiRangeSamplingTest {
    @TempDir
    Path root;

    @Test
    void everyRangeCarriesLinesBytesHashAndTruncation() throws Exception {
        Path target = root.resolve("range.md");
        Files.writeString(target, IntStream.rangeClosed(1, 2_000)
            .mapToObj(line -> line == 1_000 ? "## middle decision" : "line " + line)
            .reduce((left, right) -> left + "\n" + right).orElse(""));
        LargeFileContentService service = new LargeFileContentService(new SensitiveContentRedactor());

        var map = service.analyze(target, 16_000);

        assertThat(map.samples()).hasSizeGreaterThanOrEqualTo(3).allSatisfy(sample -> {
            assertThat(sample.startLine()).isPositive();
            assertThat(sample.endLine()).isGreaterThanOrEqualTo(sample.startLine());
            assertThat(sample.endByte()).isGreaterThan(sample.startByte());
            assertThat(sample.sourceHash()).isEqualTo(map.sourceHash());
        });
        assertThat(service.readRange(target, 990, 1_010, "CHANGED", 4_000))
            .satisfies(sample -> {
                assertThat(sample.kind()).isEqualTo("CHANGED");
                assertThat(sample.text()).contains("middle decision");
            });
        assertThat(service.queryTargeted(target, "middle decision", 5, 2_000).text())
            .contains("middle decision");
    }
}
