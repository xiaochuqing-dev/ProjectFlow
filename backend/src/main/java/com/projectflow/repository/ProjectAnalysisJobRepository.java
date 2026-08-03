package com.projectflow.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.projectflow.entity.ProjectAnalysisJob;
import com.projectflow.entity.ProjectAnalysisJobStatus;
import com.projectflow.entity.ProjectAnalysisJobType;

public interface ProjectAnalysisJobRepository extends JpaRepository<ProjectAnalysisJob, UUID> {
    List<ProjectAnalysisJob> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    void deleteByProjectId(UUID projectId);

    Optional<ProjectAnalysisJob> findFirstByProjectIdAndJobTypeAndFilePathAndStatusInOrderByCreatedAtDesc(
        UUID projectId,
        ProjectAnalysisJobType jobType,
        String filePath,
        List<ProjectAnalysisJobStatus> statuses
    );

    Optional<ProjectAnalysisJob> findFirstByProjectIdAndJobTypeAndInputFingerprintAndStatusInOrderByCreatedAtDesc(
        UUID projectId,
        ProjectAnalysisJobType jobType,
        String inputFingerprint,
        List<ProjectAnalysisJobStatus> statuses
    );

    @Query("""
        select job from ProjectAnalysisJob job
        where job.projectId = :projectId
          and job.jobType = :jobType
          and job.status in :statuses
        order by job.createdAt desc
        """)
    List<ProjectAnalysisJob> findActiveByProjectIdAndJobType(
        @Param("projectId") UUID projectId,
        @Param("jobType") ProjectAnalysisJobType jobType,
        @Param("statuses") List<ProjectAnalysisJobStatus> statuses
    );

    Optional<DashboardJobView> findFirstByProjectIdAndJobTypeAndStatusInOrderByCreatedAtDesc(
        UUID projectId,
        ProjectAnalysisJobType jobType,
        List<ProjectAnalysisJobStatus> statuses
    );

    long countByStatusIn(List<ProjectAnalysisJobStatus> statuses);

    interface DashboardJobView {
        UUID getId();
        ProjectAnalysisJobStatus getStatus();
        Instant getCreatedAt();
        Instant getUpdatedAt();
        Instant getCompletedAt();
    }
}
