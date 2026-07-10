package com.projectflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.projectflow.service.DisplayContentSanitizer;

/**
 * V3.3.4 小阶段修复：主视图可读性过滤机制测试。
 * 验证 commit hash、长 URL、evidenceRefs、JSON、内部枚举、长路径、英文 commit 等脏内容
 * 不会进入主视图 title / summary / mainChanges。
 */
class DisplayContentSanitizerTest {

    @Test
    void stripsCommitHashFromTitle() {
        String result = DisplayContentSanitizer.sanitizeTitle("修复登录超时 commit:abc123def456");
        assertThat(result).contains("修复登录超时");
        assertThat(result).doesNotContain("abc123def456");
        assertThat(result).doesNotContain("commit:");
    }

    @Test
    void stripsLongUrlFromSummary() {
        String result = DisplayContentSanitizer.sanitizeSummary(
            "详见 https://github.com/example/repo/commit/abc123def456789abc123def456789abc123def456789 的讨论"
        );
        assertThat(result).doesNotContain("https://github.com");
        assertThat(result).contains("讨论");
    }

    @Test
    void stripsEvidenceRefsPrefixes() {
        String result = DisplayContentSanitizer.sanitizeTitle("新增功能 file:src/main/App.java 和 commit:deadbeef");
        assertThat(result).doesNotContain("file:src");
        assertThat(result).doesNotContain("commit:deadbeef");
    }

    @Test
    void stripsInternalEnumValues() {
        String result = DisplayContentSanitizer.sanitizeSummary("状态 CALL_FAILED 转为 LOCAL_RULE 处理");
        assertThat(result).doesNotContain("CALL_FAILED");
        assertThat(result).doesNotContain("LOCAL_RULE");
    }

    @Test
    void stripsJsonFragments() {
        String result = DisplayContentSanitizer.sanitizeSummary(
            "返回 {\"status\":\"ok\",\"count\":3} 和 [\"a\",\"b\"] 结构"
        );
        assertThat(result).doesNotContain("{\"status");
        assertThat(result).contains("结构");
    }

    @Test
    void preservesCompleteNormalizedTitle() {
        String longTitle = "新增".repeat(50);
        String result = DisplayContentSanitizer.sanitizeTitle(longTitle);
        assertThat(result).isEqualTo(longTitle);
    }

    @Test
    void preservesCompleteNormalizedSummary() {
        String longSummary = "这是一段很长的摘要内容".repeat(50);
        String result = DisplayContentSanitizer.sanitizeSummary(longSummary);
        assertThat(result).isEqualTo(longSummary);
    }

    @Test
    void preservesCompleteNormalizedChangeItem() {
        String longChange = "修改了".repeat(60);
        String result = DisplayContentSanitizer.sanitizeChange(longChange);
        assertThat(result).isEqualTo(longChange);
    }

    @Test
    void identifiesLegacyTruncatedContentWithoutPretendingToRestoreIt() {
        assertThat(DisplayContentSanitizer.isLikelyLegacyTruncated("旧版摘要…")).isTrue();
        assertThat(DisplayContentSanitizer.isLikelyLegacyTruncated("旧版摘要...")).isTrue();
        assertThat(DisplayContentSanitizer.isLikelyLegacyTruncated("完整摘要。")).isFalse();
    }

    @Test
    void usesFallbackWhenContentHasNoReadableChinese() {
        String result = DisplayContentSanitizer.sanitizeTitle("abc123 def456 COMMIT_HASH");
        assertThat(result).isEqualTo("根据提交记录整理的变更");
    }

    @Test
    void usesFallbackForBlankInput() {
        assertThat(DisplayContentSanitizer.sanitizeTitle(null)).isEqualTo("根据提交记录整理的变更");
        assertThat(DisplayContentSanitizer.sanitizeTitle("   ")).isEqualTo("根据提交记录整理的变更");
        assertThat(DisplayContentSanitizer.sanitizeSummary(null)).isEqualTo("整理了一组可追溯的开发变化。");
    }

    @Test
    void sanitizesChangeListAndFiltersBlanks() {
        List<String> result = DisplayContentSanitizer.sanitizeChanges(List.of(
            "新增扫描游标",
            "commit:abc123def456",
            "   ",
            "从确认点读取提交"
        ));
        // 空白条目被跳过；commit 证据前缀被清洗后不可读，用兜底替代。
        assertThat(result).hasSize(3);
        assertThat(result).contains("新增扫描游标", "从确认点读取提交");
        assertThat(result).noneMatch(change -> change.contains("commit:"));
    }

    @Test
    void preservesReadableChineseTitle() {
        String result = DisplayContentSanitizer.sanitizeTitle("修复扫描游标在首次扫描时遗漏跨天提交的问题");
        assertThat(result).contains("修复扫描游标");
        assertThat(result).contains("跨天提交");
    }

    @Test
    void collapsesPathListsToReadableLabel() {
        String result = DisplayContentSanitizer.sanitizeSummary(
            "影响 backend/src/a.java, backend/src/b.java, backend/src/c.java 等文件"
        );
        assertThat(result).contains("相关文件");
    }

    @Test
    void stripsLongNumberStrings() {
        String result = DisplayContentSanitizer.sanitizeSummary("编号 12345678901234 已记录");
        assertThat(result).doesNotContain("12345678901234");
        assertThat(result).contains("已记录");
    }

    @Test
    void sanitizeUserVisibleValueUsesFallbackForRawEnglish() {
        String result = DisplayContentSanitizer.sanitizeUserVisibleValue("fix bug in ScanService");
        // 去掉英文后汉字不足，应兜底。
        assertThat(result).contains("可追溯");
    }

    @Test
    void sanitizeCapabilityNameAndSummary() {
        assertThat(DisplayContentSanitizer.sanitizeCapabilityName(null)).isEqualTo("根据项目证据整理的能力");
        assertThat(DisplayContentSanitizer.sanitizeCapabilitySummary(null)).contains("能力说明");
    }
}
