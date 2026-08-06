package com.projectflow.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import com.projectflow.entity.ProjectHistoryWindowCheckpoint;

public interface ProjectHistoryWindowCheckpointRepository extends JpaRepository<ProjectHistoryWindowCheckpoint, UUID> {
    interface StatusCount {
        String getStatus();
        long getCount();
    }

    Optional<ProjectHistoryWindowCheckpoint> findByProjectIdAndCacheKey(UUID projectId, String cacheKey);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ProjectHistoryWindowCheckpoint> findLockedByProjectIdAndCacheKey(UUID projectId, String cacheKey);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ProjectHistoryWindowCheckpoint> findLockedByIdAndProjectId(UUID id, UUID projectId);
    List<ProjectHistoryWindowCheckpoint> findByProjectIdAndSourceFingerprint(UUID projectId, String sourceFingerprint);
    List<ProjectHistoryWindowCheckpoint> findByProjectIdOrderByUpdatedAtAsc(UUID projectId);
    @Query("""
        select checkpoint.status as status, count(checkpoint) as count
        from ProjectHistoryWindowCheckpoint checkpoint
        where checkpoint.projectId = :projectId and checkpoint.cacheKey in :cacheKeys
        group by checkpoint.status
        """)
    List<StatusCount> countStatuses(
        @Param("projectId") UUID projectId,
        @Param("cacheKeys") Collection<String> cacheKeys
    );
    void deleteByProjectId(UUID projectId);
}
