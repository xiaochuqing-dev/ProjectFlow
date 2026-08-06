package com.projectflow.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.projectflow.entity.ProjectHistoryCorrection;

public interface ProjectHistoryCorrectionRepository extends JpaRepository<ProjectHistoryCorrection, UUID> {
    List<ProjectHistoryCorrection> findByProjectIdOrderByCreatedAtAsc(UUID projectId);
    Page<ProjectHistoryCorrection> findByProjectIdOrderByCreatedAtAsc(UUID projectId, Pageable pageable);
    Page<ProjectHistoryCorrection> findByProjectIdOrderByCreatedAtAscIdAsc(UUID projectId, Pageable pageable);
    List<ProjectHistoryCorrection> findByProjectIdAndStatusOrderByCreatedAtAsc(
        UUID projectId, ProjectHistoryCorrection.Status status
    );
    List<ProjectHistoryCorrection> findByProjectIdAndStatusOrderByCreatedAtAscIdAsc(
        UUID projectId, ProjectHistoryCorrection.Status status
    );
    Page<ProjectHistoryCorrection> findByProjectIdAndStatusOrderByCreatedAtAsc(
        UUID projectId, ProjectHistoryCorrection.Status status, Pageable pageable
    );
    Optional<ProjectHistoryCorrection> findByIdAndProjectId(UUID id, UUID projectId);
    long countByProjectIdAndStatus(UUID projectId, ProjectHistoryCorrection.Status status);
    void deleteByProjectId(UUID projectId);
}
