package com.projectflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import com.projectflow.entity.ChangeBatch;
import com.projectflow.entity.ChangeBatchStatus;
import com.projectflow.entity.DevelopmentSegment;
import com.projectflow.entity.DevelopmentSegmentStatus;
import com.projectflow.entity.EvidenceConfidence;
import com.projectflow.entity.ProjectChange;
import com.projectflow.entity.ProjectChangeImpactLevel;
import com.projectflow.entity.ProjectChangeKind;
import com.projectflow.entity.ProjectChangeSourceType;
import com.projectflow.entity.ProjectSediment;
import com.projectflow.entity.ProjectReviewCursor;
import com.projectflow.entity.SedimentAction;
import com.projectflow.repository.ChangeBatchRepository;
import com.projectflow.repository.DevelopmentSegmentRepository;
import com.projectflow.repository.ProjectChangeRepository;
import com.projectflow.repository.ProjectReviewCursorRepository;
import com.projectflow.repository.ProjectSedimentRepository;

@DataJpaTest
class V33PersistenceTest {
    @Autowired
    private ProjectReviewCursorRepository cursorRepository;

    @Autowired
    private ChangeBatchRepository batchRepository;

    @Autowired
    private DevelopmentSegmentRepository segmentRepository;

    @Autowired
    private ProjectChangeRepository changeRepository;

    @Autowired
    private ProjectSedimentRepository sedimentRepository;

    @Test
    void persistsTheV33SedimentationRelationshipsAndListFields() {
        UUID projectId = UUID.randomUUID();

        ProjectReviewCursor cursor = new ProjectReviewCursor(projectId);
        cursor.advance("base-sha", Instant.parse("2026-07-01T08:00:00Z"), "master", "remote-sha", null);
        cursorRepository.save(cursor);

        ChangeBatch batch = new ChangeBatch(projectId, "base-sha", "head-sha", "master", true);
        batch.complete(3, 7, 1, List.of("首次扫描最近 30 个提交。"));
        batchRepository.save(batch);

        DevelopmentSegment segment = new DevelopmentSegment(projectId, batch.getId());
        segment.updateContent(
            "Agent protocol recovery",
            "Restore the agent result bridge.",
            List.of("Add protocol health check"),
            "Agent results are no longer silently lost.",
            List.of("abc123"),
            List.of("agent-result-1"),
            List.of("backend/Bridge.java"),
            List.of("commit:abc123", "file:backend/Bridge.java"),
            EvidenceConfidence.HIGH,
            DevelopmentSegmentStatus.PENDING
        );
        segmentRepository.save(segment);
        batch.updateSegmentCount(1);
        batchRepository.save(batch);

        ProjectSediment sediment = new ProjectSediment(projectId);
        sediment.updateCore(
            "Agent protocol integration",
            "Keep Agent result write-back reliable.",
            "Avoid missing development evidence.",
            "PROJECT_CAPABILITY",
            List.of(segment.getId().toString()),
            List.of("commit:abc123")
        );
        sedimentRepository.save(sediment);

        ProjectChange change = new ProjectChange(projectId, null);
        change.update(
            ProjectChangeSourceType.EVIDENCE_BUNDLE,
            segment.getId().toString(),
            null,
            ProjectChangeKind.CAPABILITY,
            ProjectChangeImpactLevel.MAJOR,
            "Agent protocol recovery",
            "Restore the Agent result bridge.",
            "Evidence-backed candidate.",
            "backend/Bridge.java",
            "",
            "",
            "",
            "",
            "",
            "",
            "Agent protocol integration"
        );
        change.updateSedimentSuggestion(
            segment.getId(),
            SedimentAction.MERGE_EXISTING,
            sediment.getId(),
            "Avoid missing development evidence.",
            List.of("segment:" + segment.getId(), "commit:abc123"),
            EvidenceConfidence.HIGH,
            true
        );
        changeRepository.save(change);

        assertThat(cursorRepository.findByProjectId(projectId)).get()
            .extracting(ProjectReviewCursor::getLastReviewedCommitSha).isEqualTo("base-sha");
        assertThat(batchRepository.findFirstByProjectIdOrderByScanStartedAtDesc(projectId)).get()
            .extracting(ChangeBatch::getStatus).isEqualTo(ChangeBatchStatus.PENDING);
        assertThat(segmentRepository.findByBatchIdOrderByCreatedAtAsc(batch.getId()))
            .extracting(DevelopmentSegment::getTitle).containsExactly("Agent protocol recovery");

        ProjectChange persistedChange = changeRepository.findById(change.getId()).orElseThrow();
        assertThat(persistedChange.getSuggestedAction()).isEqualTo(SedimentAction.MERGE_EXISTING);
        assertThat(persistedChange.getEvidenceRefs()).containsExactly("segment:" + segment.getId(), "commit:abc123");
        assertThat(persistedChange.isNeedsUserReview()).isTrue();

        assertThat(sedimentRepository.findByProjectIdOrderByUpdatedAtDesc(projectId))
            .singleElement()
            .satisfies(saved -> {
                assertThat(saved.getTitle()).isEqualTo("Agent protocol integration");
                assertThat(saved.getSourceSegmentIds()).containsExactly(segment.getId().toString());
                assertThat(saved.getEvidenceRefs()).containsExactly("commit:abc123");
            });
    }
}
