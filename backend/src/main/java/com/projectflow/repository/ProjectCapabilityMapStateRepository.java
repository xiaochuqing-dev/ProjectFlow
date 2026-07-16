package com.projectflow.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.projectflow.entity.ProjectCapabilityMapState;

import jakarta.persistence.LockModeType;

public interface ProjectCapabilityMapStateRepository extends JpaRepository<ProjectCapabilityMapState, UUID> {
    Optional<ProjectCapabilityMapState> findByProjectId(UUID projectId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select state from ProjectCapabilityMapState state where state.projectId = :projectId")
    Optional<ProjectCapabilityMapState> findLockedByProjectId(@Param("projectId") UUID projectId);
}
