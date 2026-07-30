package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContentOverFilenameTest {
    @TempDir
    Path root;

    @Test
    void misleadingFrontendFilenameDoesNotEraseBackendContentSignal() throws Exception {
        Files.writeString(root.resolve("frontend.md"), "实际内容：后端事务边界由数据库唯一约束保护。\n");

        var result = EvidenceDiscoveryFixture.discover(root);

        assertThat(result.promptEvidence()).singleElement()
            .satisfies(item -> assertThat(item.boundedSample())
                .contains("后端事务边界", "数据库唯一约束"));
    }
}
