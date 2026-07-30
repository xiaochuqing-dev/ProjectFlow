package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LargeFileContentMapTest {
    @TempDir
    Path root;

    @Test
    void mapsEightyThousandLinesAndReadsHeadMiddleTail() throws Exception {
        Path target = root.resolve("不知道有没有用");
        try (BufferedWriter writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
            for (int line = 1; line <= 80_000; line++) {
                String value = switch (line) {
                    case 1 -> "# HEAD_FACT repository intake exists";
                    case 20_000 -> "## ENGINEERING_DECISIONS";
                    case 40_000 -> "MIDDLE_FACT candidate writes never create ProjectFact";
                    case 79_999 -> "TAIL_REVISION old plan is deprecated and replaced by strong facts";
                    default -> "ordinary line " + line;
                };
                writer.write(value);
                writer.newLine();
            }
        }
        LargeFileContentService service = new LargeFileContentService(new SensitiveContentRedactor());

        var map = service.analyze(target, 24_000);
        String prompt = service.toPromptText(map, 24_000);

        assertThat(map.binary()).isFalse();
        assertThat(map.lineCount()).isEqualTo(80_000);
        assertThat(map.sourceHash()).hasSize(64);
        assertThat(map.samples()).extracting(LargeFileContentService.RangeSample::kind)
            .contains("HEAD", "MIDDLE", "TAIL", "HEADING", "MARKER");
        assertThat(prompt).contains("HEAD_FACT", "MIDDLE_FACT", "TAIL_REVISION", "UNREAD_RANGES");
        assertThat(map.unreadRanges()).isNotEmpty();
    }
}
