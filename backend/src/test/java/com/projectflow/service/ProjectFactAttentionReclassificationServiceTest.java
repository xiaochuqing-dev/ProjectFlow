package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.projectflow.entity.EvidenceConfidence;
import com.projectflow.entity.ProjectFact;
import com.projectflow.entity.ProjectFactOrigin;
import com.projectflow.entity.ProjectFactRecordStatus;
import com.projectflow.repository.ProjectFactRepository;

class ProjectFactAttentionReclassificationServiceTest {
    @Test
    void recoversOnlyEvidenceBackedPassFactsWithoutChangingFingerprint() {
        ProjectFactRepository repository = mock(ProjectFactRepository.class);
        ProjectFactAttentionReclassificationService service = new ProjectFactAttentionReclassificationService(repository);
        ProjectFact fact = fact("PASS", ProjectFactAttentionReclassificationService.FALLBACK_TIME_REASON);
        String fingerprint = fact.getFactFingerprint();

        var result = service.reclassify(List.of(fact));

        assertThat(result.beforeAttention()).isEqualTo(1);
        assertThat(result.recorded()).isEqualTo(1);
        assertThat(fact.getRecordStatus()).isEqualTo(ProjectFactRecordStatus.RECORDED);
        assertThat(fact.getAttentionReason()).isEmpty();
        assertThat(fact.getFactFingerprint()).isEqualTo(fingerprint);
        verify(repository).save(fact);
    }

    @Test
    void retainsNeedsReviewAndConflictingReasons() {
        ProjectFactRepository repository = mock(ProjectFactRepository.class);
        ProjectFactAttentionReclassificationService service = new ProjectFactAttentionReclassificationService(repository);
        ProjectFact needsReview = fact("NEEDS_REVIEW", "分析质量状态为 NEEDS_REVIEW；" + ProjectFactAttentionReclassificationService.FALLBACK_TIME_REASON);
        ProjectFact duplicate = fact("PASS", "同一批次出现重复标题，需人工区分；" + ProjectFactAttentionReclassificationService.FALLBACK_TIME_REASON);

        var result = service.reclassify(List.of(needsReview, duplicate));

        assertThat(result.retainedAttention()).isEqualTo(2);
        assertThat(needsReview.getRecordStatus()).isEqualTo(ProjectFactRecordStatus.NEEDS_ATTENTION);
        assertThat(duplicate.getRecordStatus()).isEqualTo(ProjectFactRecordStatus.NEEDS_ATTENTION);
        verifyNoInteractions(repository);
    }

    @Test
    void rerunIsIdempotent() {
        ProjectFactRepository repository = mock(ProjectFactRepository.class);
        ProjectFactAttentionReclassificationService service = new ProjectFactAttentionReclassificationService(repository);
        ProjectFact fact = fact("PASS", ProjectFactAttentionReclassificationService.FALLBACK_TIME_REASON);
        service.reclassify(List.of(fact));

        var rerun = service.reclassify(List.of(fact));

        assertThat(rerun.changed()).isZero();
        assertThat(fact.getRecordStatus()).isEqualTo(ProjectFactRecordStatus.RECORDED);
    }

    private static ProjectFact fact(String quality, String reason) {
        ProjectFact fact = new ProjectFact(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), ProjectFactOrigin.LEGACY_SEGMENT_MIGRATION,
            "a".repeat(64)
        );
        fact.updateContent(
            "可靠事实", "已有提交、文件和 evidence", List.of("完成实现"), "用户可见变化",
            Instant.parse("2026-07-01T00:00:00Z"), Instant.parse("2026-07-02T00:00:00Z"),
            List.of("abcdef12"), List.of(), List.of(), List.of("src/main.java"),
            List.of("commit:abcdef12", "file:src/main.java"), "LOCAL_RULE", quality,
            EvidenceConfidence.MEDIUM, ProjectFactRecordStatus.NEEDS_ATTENTION, reason
        );
        return fact;
    }
}
