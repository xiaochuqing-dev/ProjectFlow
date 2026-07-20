package com.projectflow.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.projectflow.entity.ProjectTimelineSummary;
import com.projectflow.entity.ProjectTimelineSummaryStatus;
import com.projectflow.entity.TimelineGranularity;

import jakarta.persistence.LockModeType;

public interface ProjectTimelineSummaryRepository extends JpaRepository<ProjectTimelineSummary, UUID> {
    Optional<ProjectTimelineSummary> findByProjectIdAndGranularityAndPeriodKey(
        UUID projectId, TimelineGranularity granularity, String periodKey
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select summary from ProjectTimelineSummary summary
        where summary.projectId = :projectId and summary.granularity = :granularity and summary.periodKey = :periodKey
        """)
    Optional<ProjectTimelineSummary> findLocked(
        @Param("projectId") UUID projectId,
        @Param("granularity") TimelineGranularity granularity,
        @Param("periodKey") String periodKey
    );

    List<ProjectTimelineSummary> findByProjectIdAndGranularityAndPeriodKeyIn(
        UUID projectId, TimelineGranularity granularity, List<String> periodKeys
    );
    List<ProjectTimelineSummary> findByProjectIdAndGranularityOrderByPeriodKeyDesc(
        UUID projectId, TimelineGranularity granularity
    );
    List<ProjectTimelineSummary> findByProjectIdOrderByUpdatedAtDesc(UUID projectId);
    long countByProjectIdAndStatusIn(UUID projectId, List<ProjectTimelineSummaryStatus> statuses);
    Optional<ProjectTimelineSummary> findFirstByProjectIdOrderByUpdatedAtDesc(UUID projectId);
    List<ProjectTimelineSummary> findByProjectIdAndStatusInOrderByUpdatedAtAsc(
        UUID projectId, List<ProjectTimelineSummaryStatus> statuses
    );
}
