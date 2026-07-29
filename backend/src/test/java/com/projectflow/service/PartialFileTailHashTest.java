package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

class PartialFileTailHashTest {
    @TempDir
    Path root;

    @Test
    void boundedContentHashStillDetectsARevisionAtThePhysicalTail() throws Exception {
        Path target = root.resolve("large-history.txt");
        try (BufferedWriter writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
            for (int line = 0; line < 90_000; line++) {
                writer.write("history material with enough width " + line);
                writer.newLine();
            }
            writer.write("TAIL_REVISION=v1");
        }
        LargeFileContentService service = new LargeFileContentService(new SensitiveContentRedactor());
        ReflectionTestUtils.setField(service, "maxContentMapBytes", 1_048_576L);
        String first = service.analyze(target, 2_000).sourceHash();

        Files.writeString(target, "\nTAIL_REVISION=v2", StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);
        var second = service.analyze(target, 2_000);

        assertThat(second.partial()).isTrue();
        assertThat(second.sourceHash()).startsWith("partial:").isNotEqualTo(first);
        assertThat(service.toPromptText(second, 1_000)).contains("UNREAD_RANGES", "bytes ");
    }
}
