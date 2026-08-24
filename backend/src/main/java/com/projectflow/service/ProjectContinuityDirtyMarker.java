package com.projectflow.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projectflow.entity.ProjectHistorySnapshot;
import com.projectflow.repository.ProjectHistorySnapshotRepository;

/** Durable signal for ProjectFlow-owned writes awaiting explicit refresh. */
@Service
public class ProjectContinuityDirtyMarker {
    private final ProjectHistorySnapshotRepository snapshotRepository;

    public ProjectContinuityDirtyMarker(ProjectHistorySnapshotRepository snapshotRepository) {
        this.snapshotRepository = snapshotRepository;
    }

    @Transactional
    public String mark(UUID projectId, String reason, String affectedRevision) {
        ProjectHistorySnapshot snapshot = snapshotRepository.findLockedByProjectId(projectId).orElse(null);
        if (snapshot == null) return "";
        String safeReason = bounded(reason, 80);
        String safeAffectedRevision = bounded(affectedRevision, 180);
        long generation = snapshot.advanceContinuityDirtyGeneration();
        String contextHash = ProjectHistorySourceCollector.sha256(
            "project-continuity-dirty-v2|" + projectId + "|generation:" + generation
                + "|" + safeReason + "|" + safeAffectedRevision
        );
        String revision = "continuity-dirty:g" + generation + ":" + contextHash.substring(0, 32);
        snapshot.markContinuityDirty(revision, safeReason, Instant.now());
        snapshotRepository.save(snapshot);
        return revision;
    }

    private static String bounded(String value, int maximum) {
        String safe = value == null ? "" : value.trim();
        return safe.length() <= maximum ? safe : safe.substring(0, maximum);
    }
}
