package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UnknownExtensionTextTest {
    @TempDir
    Path root;

    @Test
    void extensionlessUnicodeTextIsAReadableEvidenceCandidate() throws Exception {
        Files.writeString(root.resolve("不知道有没有用"), "迁移脚本已经替代手工数据库初始化。\n");

        var result = EvidenceDiscoveryFixture.discover(root);

        assertThat(result.promptEvidence()).singleElement()
            .satisfies(item -> {
                assertThat(item.category()).isEqualTo("UNKNOWN_DOCUMENT");
                assertThat(item.boundedSample()).contains("迁移脚本");
            });
    }
}
