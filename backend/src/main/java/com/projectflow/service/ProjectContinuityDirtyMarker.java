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
        String revision = "continuity-dirty:" + ProjectHistorySourceCollector.sha256(
            "project-continuity-dirty-v1|" + projectId + "|" + safeReason + "|" + safeAffectedRevision
        );
        snapshot.markContinuityDirty(revision, safeReason, Instant.now());
        snapshotRepository.save(snapshot);
        return revision;
    }

    private static String bounded(String value, int maximum) {
        String safe = value == null ? "" : value.trim();
        return safe.length() <= maximum ? safe : safe.substring(0, maximum);
    }
}
