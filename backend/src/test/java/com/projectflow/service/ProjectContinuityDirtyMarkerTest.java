package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.projectflow.entity.ProjectHistorySnapshot;
import com.projectflow.repository.ProjectHistorySnapshotRepository;

class ProjectContinuityDirtyMarkerTest {
    @Test
    void marksKnownInternalWriteAndOnlyAcknowledgesObservedRevision() {
        UUID projectId = UUID.randomUUID();
        ProjectHistorySnapshot snapshot = readySnapshot(projectId);
        ProjectHistorySnapshotRepository repository = mock(ProjectHistorySnapshotRepository.class);
        when(repository.findLockedByProjectId(projectId)).thenReturn(Optional.of(snapshot));
        ProjectContinuityDirtyMarker marker = new ProjectContinuityDirtyMarker(repository);

        String first = marker.mark(projectId, "AGENT_RESULT_CANDIDATE", "agent-result:first");
        assertThat(snapshot.getStatus()).isEqualTo(ProjectHistorySnapshot.Status.STALE);
        assertThat(snapshot.getContinuityDirtyRevision()).isEqualTo(first).startsWith("continuity-dirty:");

        String second = marker.mark(projectId, "HISTORY_CORRECTION", "correction:second");
        snapshot.complete(
            "revision-2", "fingerprint-2", 1, Instant.EPOCH, Instant.EPOCH, "strategy", "prompt",
            "{}", "[]", "[]", "[]", "{}", "{}", UUID.randomUUID(), false
        );
        assertThat(snapshot.acknowledgeContinuityDirty(first)).isFalse();
        assertThat(snapshot.getStatus()).isEqualTo(ProjectHistorySnapshot.Status.STALE);
        assertThat(snapshot.getContinuityDirtyRevision()).isEqualTo(second);

        assertThat(snapshot.acknowledgeContinuityDirty(second)).isTrue();
        assertThat(snapshot.getContinuityDirtyRevision()).isBlank();
    }

    private static ProjectHistorySnapshot readySnapshot(UUID projectId) {
        ProjectHistorySnapshot snapshot = new ProjectHistorySnapshot(projectId);
        snapshot.complete(
            "revision-1", "fingerprint-1", 1, Instant.EPOCH, Instant.EPOCH, "strategy", "prompt",
            "{}", "[]", "[]", "[]", "{}", "{}", UUID.randomUUID(), false
        );
        return snapshot;
    }
}
