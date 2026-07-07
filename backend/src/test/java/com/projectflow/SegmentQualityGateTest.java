package com.projectflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.projectflow.entity.EvidenceConfidence;
import com.projectflow.service.DevelopmentSegmentationService;
import com.projectflow.service.DevelopmentSegmentationService.ChangeAtom;
import com.projectflow.service.DevelopmentSegmentationService.SegmentDraft;
import com.projectflow.service.SegmentQualityGate;

class SegmentQualityGateTest {
    private final SegmentQualityGate gate = new SegmentQualityGate();

    @Test
    void rejectsDirectoryAndCountOnlySummaries() {
        SegmentDraft draft = draft(
            "backend 开发推进",
            "这一组变化围绕 backend 展开，共包含 8 条原子变化。",
            List.of("修改后端", "整理证据", "相关能力已归并")
        );

        var result = gate.evaluate(draft, List.of());

        // V3.3.3: 质量门槛改为标记器。模板化摘要不再返回 NEEDS_MANUAL，而是 NEEDS_REVIEW（保留模型结果，标记需复核）。
        assertThat(result.status()).isEqualTo("NEEDS_REVIEW");
        assertThat(result.reason()).contains("具体开发结果");
    }

    @Test
    void flagsChineseMissingTitleAsNeedingChineseRewrite() {
        // V3.3.3: 用户可见主内容必须中文。整句无汉字标记为需中文改写。
        SegmentDraft draft = new SegmentDraft(
            "fix keep selected project after refresh",
            "fix keep selected project after refresh on review pages",
            List.of("commit:abc"), List.of("fix refresh", "keep selected", "after refresh"),
            "keep selected project after refresh",
            List.of("commit:abc", "file:src/Review.tsx"), List.of("src/Review.tsx"), EvidenceConfidence.MEDIUM
        );

        assertThat(gate.evaluate(draft, List.of()).status()).isEqualTo("NEEDS_CHINESE_REWRITE");
    }

    @Test
    void flagsLowConfidenceModelOutputForReview() {
        SegmentDraft draft = new SegmentDraft(
            "接入扫描指纹并复用稳定分析结果",
            "新增扫描指纹，在代码、整理进度和模型配置未变化时复用已有分析。",
            List.of("commit:abc"), List.of("计算工作区差异哈希", "持久化模型配置标识", "命中相同指纹时复用开发推进段"),
            "开发者重复扫描时会获得稳定结果。",
            List.of("commit:abc", "file:backend/Scan.java"), List.of("backend/Scan.java"), EvidenceConfidence.LOW
        );

        assertThat(gate.evaluate(draft, List.of()).status()).isEqualTo("LOW_CONFIDENCE");
    }

    @Test
    void acceptsConcreteResultWithUserVisibleBehaviorAndThreeChanges() {
        SegmentDraft draft = draft(
            "接入扫描指纹并复用稳定分析结果",
            "新增扫描指纹，在代码、整理进度和模型配置未变化时复用已有分析。",
            List.of("计算工作区差异哈希", "持久化模型配置标识", "命中相同指纹时复用开发推进段")
        );

        assertThat(gate.evaluate(draft, List.of()).status()).isEqualTo("PASS");
    }

    @Test
    void localFallbackProducesConcreteHumanReadableStructure() {
        DevelopmentSegmentationService service = new DevelopmentSegmentationService();

        SegmentDraft draft = service.group(List.of(atom())).get(0);

        assertThat(draft.title()).doesNotContain("开发推进");
        assertThat(draft.plainSummary()).contains("扫描游标");
        assertThat(draft.mainChanges()).hasSizeBetween(3, 6);
        assertThat(gate.evaluate(draft, List.of()).status()).isEqualTo("PASS");
    }

    private SegmentDraft draft(String title, String summary, List<String> changes) {
        return new SegmentDraft(
            title, summary, List.of("commit:abc"), changes, "开发者重复扫描时会获得稳定结果。",
            List.of("commit:abc", "file:backend/Scan.java"), List.of("backend/Scan.java"), EvidenceConfidence.HIGH
        );
    }

    private ChangeAtom atom() {
        return new ChangeAtom(
            "commit:abc", "feat(scan): 新增扫描游标", Instant.parse("2026-07-06T08:00:00Z"),
            List.of("backend"), List.of("backend/Scan.java"), List.of("commit:abc", "file:backend/Scan.java")
        );
    }
}
