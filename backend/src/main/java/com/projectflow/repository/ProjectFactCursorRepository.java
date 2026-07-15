package com.projectflow.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.projectflow.entity.ProjectFactCursor;

import jakarta.persistence.LockModeType;

public interface ProjectFactCursorRepository extends JpaRepository<ProjectFactCursor, UUID> {
    Optional<ProjectFactCursor> findByProjectId(UUID projectId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select cursor from ProjectFactCursor cursor where cursor.projectId = :projectId")
    Optional<ProjectFactCursor> findLockedByProjectId(@Param("projectId") UUID projectId);
}
