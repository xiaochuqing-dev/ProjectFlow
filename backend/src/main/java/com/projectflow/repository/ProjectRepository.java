package com.projectflow.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.projectflow.entity.ProjectSpace;

import jakarta.persistence.LockModeType;

public interface ProjectRepository extends JpaRepository<ProjectSpace, UUID> {
    List<ProjectSpace> findByUserIdOrderByUpdatedAtDesc(UUID userId);

    Optional<ProjectSpace> findByIdAndUserId(UUID id, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select project from ProjectSpace project where project.id = :id and project.userId = :userId")
    Optional<ProjectSpace> findLockedByIdAndUserId(UUID id, UUID userId);

    Optional<ProjectSpace> findFirstByUserIdAndNameIgnoreCaseOrderByUpdatedAtDesc(UUID userId, String name);
}
