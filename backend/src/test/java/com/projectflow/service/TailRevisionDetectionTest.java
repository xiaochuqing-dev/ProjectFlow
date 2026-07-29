package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TailRevisionDetectionTest {
    @TempDir
    Path root;

    @Test
    void tailRevisionDoesNotDisappearBehindOldHeadPlan() throws Exception {
        List<String> lines = new ArrayList<>();
        lines.add("# CURRENT_PLAN");
        lines.add("旧方案声明：Agent Result 可以直接成为事实");
        for (int index = 0; index < 1_000; index++) lines.add("背景材料 " + index);
        lines.add("## 2026 revision");
        lines.add("DEPRECATED：旧方案已由候选写入边界替代，Agent Result 不能直接成为事实");
        Path target = root.resolve("FINAL_DESIGN.md");
        Files.write(target, lines);
        LargeFileContentService service = new LargeFileContentService(new SensitiveContentRedactor());

        var map = service.analyze(target, 20_000);
        String text = service.toPromptText(map, 20_000);

        assertThat(text).contains("旧方案声明", "DEPRECATED", "2026 revision");
        assertThat(map.samples())
            .filteredOn(sample -> "TAIL".equals(sample.kind()))
            .singleElement()
            .satisfies(sample -> assertThat(sample.text()).contains("候选写入边界替代"));
    }
}
