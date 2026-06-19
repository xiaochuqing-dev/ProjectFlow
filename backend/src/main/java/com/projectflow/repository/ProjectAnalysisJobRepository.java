package com.projectflow.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.projectflow.entity.ProjectAnalysisJob;
import com.projectflow.entity.ProjectAnalysisJobStatus;
import com.projectflow.entity.ProjectAnalysisJobType;

public interface ProjectAnalysisJobRepository extends JpaRepository<ProjectAnalysisJob, UUID> {
    List<ProjectAnalysisJob> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    Optional<ProjectAnalysisJob> findFirstByProjectIdAndJobTypeAndFilePathAndStatusInOrderByCreatedAtDesc(
        UUID projectId,
        ProjectAnalysisJobType jobType,
        String filePath,
        List<ProjectAnalysisJobStatus> statuses
    );
}
