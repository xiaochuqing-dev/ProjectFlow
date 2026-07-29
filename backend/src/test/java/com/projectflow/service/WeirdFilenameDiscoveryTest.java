package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WeirdFilenameDiscoveryTest {
    @TempDir
    Path root;

    @Test
    void semanticallyUsefulTextWithHostileLookingNameIsStillDiscovered() throws Exception {
        Files.writeString(root.resolve("fuck-this-bug.md"), "事故复盘：CI 已验证回滚路径。\n");

        var result = EvidenceDiscoveryFixture.discover(root);

        assertThat(result.promptEvidence()).singleElement()
            .satisfies(item -> {
                assertThat(item.locator()).isEqualTo("fuck-this-bug.md");
                assertThat(item.boundedSample()).contains("CI 已验证回滚路径");
            });
    }
}
