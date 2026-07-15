package com.projectflow.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.projectflow.entity.ProjectFactHistoryState;

import jakarta.persistence.LockModeType;

public interface ProjectFactHistoryStateRepository extends JpaRepository<ProjectFactHistoryState, UUID> {
    Optional<ProjectFactHistoryState> findByProjectId(UUID projectId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select state from ProjectFactHistoryState state where state.projectId = :projectId")
    Optional<ProjectFactHistoryState> findLockedByProjectId(@Param("projectId") UUID projectId);
}
