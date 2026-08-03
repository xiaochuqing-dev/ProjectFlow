package com.projectflow.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.projectflow.entity.ProjectHistorySnapshot;

import jakarta.persistence.LockModeType;

public interface ProjectHistorySnapshotRepository extends JpaRepository<ProjectHistorySnapshot, UUID> {
    Optional<ProjectHistorySnapshot> findByProjectId(UUID projectId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select snapshot from ProjectHistorySnapshot snapshot where snapshot.projectId = :projectId")
    Optional<ProjectHistorySnapshot> findLockedByProjectId(@Param("projectId") UUID projectId);

    void deleteByProjectId(UUID projectId);
}
