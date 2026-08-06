package com.projectflow.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.projectflow.entity.ProjectHistoryWindowCheckpoint;

public interface ProjectHistoryWindowCheckpointRepository extends JpaRepository<ProjectHistoryWindowCheckpoint, UUID> {
    Optional<ProjectHistoryWindowCheckpoint> findByProjectIdAndCacheKey(UUID projectId, String cacheKey);
    List<ProjectHistoryWindowCheckpoint> findByProjectIdAndSourceFingerprint(UUID projectId, String sourceFingerprint);
    List<ProjectHistoryWindowCheckpoint> findByProjectIdOrderByUpdatedAtAsc(UUID projectId);
    void deleteByProjectId(UUID projectId);
}
