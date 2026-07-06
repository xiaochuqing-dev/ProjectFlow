package com.projectflow.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.projectflow.entity.ProjectReviewCursor;

public interface ProjectReviewCursorRepository extends JpaRepository<ProjectReviewCursor, UUID> {
    Optional<ProjectReviewCursor> findByProjectId(UUID projectId);
}
