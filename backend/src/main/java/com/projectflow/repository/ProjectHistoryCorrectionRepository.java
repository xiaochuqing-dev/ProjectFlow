package com.projectflow.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.projectflow.entity.ProjectHistoryCorrection;

public interface ProjectHistoryCorrectionRepository extends JpaRepository<ProjectHistoryCorrection, UUID> {
    List<ProjectHistoryCorrection> findByProjectIdOrderByCreatedAtAsc(UUID projectId);
    List<ProjectHistoryCorrection> findByProjectIdAndStatusOrderByCreatedAtAsc(
        UUID projectId, ProjectHistoryCorrection.Status status
    );
    Optional<ProjectHistoryCorrection> findByIdAndProjectId(UUID id, UUID projectId);
    long countByProjectIdAndStatus(UUID projectId, ProjectHistoryCorrection.Status status);
    void deleteByProjectId(UUID projectId);
}
