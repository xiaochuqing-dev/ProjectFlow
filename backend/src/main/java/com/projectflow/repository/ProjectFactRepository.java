package com.projectflow.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.projectflow.entity.ProjectFact;
import com.projectflow.entity.ProjectFactRecordStatus;

public interface ProjectFactRepository extends JpaRepository<ProjectFact, UUID> {
    Optional<ProjectFact> findByIdAndProjectId(UUID id, UUID projectId);
    Optional<ProjectFact> findByProjectIdAndFactFingerprint(UUID projectId, String factFingerprint);
    Optional<ProjectFact> findFirstByProjectIdAndSourceSegmentId(UUID projectId, UUID sourceSegmentId);
    List<ProjectFact> findByBatchIdOrderByOccurredFromAscCreatedAtAsc(UUID batchId);
    List<ProjectFact> findBySourceSegmentIdIn(List<UUID> sourceSegmentIds);
    long countByProjectId(UUID projectId);

    boolean existsByProjectIdAndTimelineEventAtIsNotNull(UUID projectId);
    long countByProjectIdAndRecordStatus(UUID projectId, ProjectFactRecordStatus recordStatus);
    long countByBatchId(UUID batchId);
    long countByBatchIdAndRecordStatus(UUID batchId, ProjectFactRecordStatus recordStatus);

    Optional<ProjectFact> findFirstByProjectIdOrderByOccurredFromAscCreatedAtAsc(UUID projectId);
    Optional<ProjectFact> findFirstByProjectIdOrderByOccurredToDescCreatedAtDesc(UUID projectId);
    List<ProjectFact> findTop10ByProjectIdOrderByOccurredToDescCreatedAtDesc(UUID projectId);
    Page<ProjectFact> findByProjectIdOrderByTimelineEventAtAscCreatedAtAsc(UUID projectId, Pageable pageable);

    @Query(value = """
        select fact from ProjectFact fact where fact.projectId = :projectId and not exists (
          select coverage from ProjectCapabilityFactCoverage coverage
          where coverage.projectId = :projectId and coverage.factId = fact.id
            and coverage.sourceFactUpdatedAt = fact.updatedAt
        ) order by fact.timelineEventAt asc, fact.createdAt asc
        """, countQuery = """
        select count(fact) from ProjectFact fact where fact.projectId = :projectId and not exists (
          select coverage from ProjectCapabilityFactCoverage coverage
          where coverage.projectId = :projectId and coverage.factId = fact.id
            and coverage.sourceFactUpdatedAt = fact.updatedAt
        )
        """)
    Page<ProjectFact> findCapabilityFactsNeedingCoverage(@Param("projectId") UUID projectId, Pageable pageable);

    @Query(value = """
        select new com.projectflow.repository.ProjectFactSummaryRow(
          fact.id, fact.projectId, fact.batchId, fact.sourceSegmentId, fact.legacySedimentId,
          fact.origin, fact.title, fact.summary, fact.occurredFrom, fact.occurredTo,
          fact.sourceMode, fact.qualityStatus, fact.confidence, fact.recordStatus, fact.attentionReason,
          fact.commitCount, fact.agentResultCount, fact.affectedFileCount, fact.evidenceCount,
          fact.createdAt, fact.updatedAt
        ) from ProjectFact fact
        where fact.projectId = :projectId
          and (:fromTime is null or coalesce(fact.occurredTo, fact.occurredFrom, fact.createdAt) >= :fromTime)
          and (:toTime is null or coalesce(fact.occurredFrom, fact.occurredTo, fact.createdAt) <= :toTime)
          and (:batchId is null or fact.batchId = :batchId)
          and (:recordStatus is null or fact.recordStatus = :recordStatus)
        order by coalesce(fact.occurredTo, fact.occurredFrom, fact.createdAt) desc, fact.createdAt desc
        """, countQuery = """
        select count(fact) from ProjectFact fact
        where fact.projectId = :projectId
          and (:fromTime is null or coalesce(fact.occurredTo, fact.occurredFrom, fact.createdAt) >= :fromTime)
          and (:toTime is null or coalesce(fact.occurredFrom, fact.occurredTo, fact.createdAt) <= :toTime)
          and (:batchId is null or fact.batchId = :batchId)
          and (:recordStatus is null or fact.recordStatus = :recordStatus)
        """)
    Page<ProjectFactSummaryRow> searchSummaries(
        @Param("projectId") UUID projectId,
        @Param("fromTime") Instant from,
        @Param("toTime") Instant to,
        @Param("batchId") UUID batchId,
        @Param("recordStatus") ProjectFactRecordStatus recordStatus,
        Pageable pageable
    );

    @Query("""
        select new com.projectflow.repository.ProjectFactOverviewRow(
          count(fact),
          sum(case when fact.recordStatus = :recorded then 1 else 0 end),
          coalesce(sum(case when fact.recordStatus = :attention then 1 else 0 end), 0L),
          min(coalesce(fact.occurredFrom, fact.occurredTo, fact.createdAt)),
          max(coalesce(fact.occurredTo, fact.occurredFrom, fact.createdAt))
        )
        from ProjectFact fact where fact.projectId = :projectId
        """)
    ProjectFactOverviewRow summarize(
        @Param("projectId") UUID projectId,
        @Param("recorded") ProjectFactRecordStatus recorded,
        @Param("attention") ProjectFactRecordStatus attention
    );

    @Query("""
        select new com.projectflow.repository.TimelineOverviewRow(
          count(fact), count(distinct fact.batchId),
          sum(case when fact.recordStatus = :attention then 1 else 0 end),
          min(fact.timelineEventAt), max(fact.timelineEventAt), max(fact.updatedAt)
        ) from ProjectFact fact where fact.projectId = :projectId and fact.timelineEventAt is not null
        """)
    TimelineOverviewRow timelineOverview(
        @Param("projectId") UUID projectId,
        @Param("attention") ProjectFactRecordStatus attention
    );

    @Query(value = """
        select fact.timelineDayKey as periodKey, count(fact) as factCount,
          count(distinct fact.batchId) as batchCount,
          sum(case when fact.recordStatus = :attention then 1 else 0 end) as attentionCount,
          min(fact.timelineEventAt) as earliestEventAt, max(fact.timelineEventAt) as latestEventAt,
          max(fact.updatedAt) as maxUpdatedAt
        from ProjectFact fact
        where fact.projectId = :projectId and fact.timelineDayKey is not null
          and (:fromKey is null or fact.timelineDayKey >= :fromKey)
          and (:toKey is null or fact.timelineDayKey <= :toKey)
        group by fact.timelineDayKey order by fact.timelineDayKey desc
        """, countQuery = """
        select count(distinct fact.timelineDayKey) from ProjectFact fact
        where fact.projectId = :projectId and fact.timelineDayKey is not null
          and (:fromKey is null or fact.timelineDayKey >= :fromKey)
          and (:toKey is null or fact.timelineDayKey <= :toKey)
        """)
    Page<TimelinePeriodStatsRow> summarizeTimelineDays(
        @Param("projectId") UUID projectId, @Param("fromKey") String fromKey, @Param("toKey") String toKey,
        @Param("attention") ProjectFactRecordStatus attention, Pageable pageable
    );

    @Query(value = """
        select fact.timelineWeekKey as periodKey, count(fact) as factCount,
          count(distinct fact.batchId) as batchCount,
          sum(case when fact.recordStatus = :attention then 1 else 0 end) as attentionCount,
          min(fact.timelineEventAt) as earliestEventAt, max(fact.timelineEventAt) as latestEventAt,
          max(fact.updatedAt) as maxUpdatedAt
        from ProjectFact fact
        where fact.projectId = :projectId and fact.timelineWeekKey is not null
          and (:fromKey is null or fact.timelineWeekKey >= :fromKey)
          and (:toKey is null or fact.timelineWeekKey <= :toKey)
        group by fact.timelineWeekKey order by fact.timelineWeekKey desc
        """, countQuery = """
        select count(distinct fact.timelineWeekKey) from ProjectFact fact
        where fact.projectId = :projectId and fact.timelineWeekKey is not null
          and (:fromKey is null or fact.timelineWeekKey >= :fromKey)
          and (:toKey is null or fact.timelineWeekKey <= :toKey)
        """)
    Page<TimelinePeriodStatsRow> summarizeTimelineWeeks(
        @Param("projectId") UUID projectId, @Param("fromKey") String fromKey, @Param("toKey") String toKey,
        @Param("attention") ProjectFactRecordStatus attention, Pageable pageable
    );

    @Query(value = """
        select fact.timelineMonthKey as periodKey, count(fact) as factCount,
          count(distinct fact.batchId) as batchCount,
          sum(case when fact.recordStatus = :attention then 1 else 0 end) as attentionCount,
          min(fact.timelineEventAt) as earliestEventAt, max(fact.timelineEventAt) as latestEventAt,
          max(fact.updatedAt) as maxUpdatedAt
        from ProjectFact fact
        where fact.projectId = :projectId and fact.timelineMonthKey is not null
          and (:fromKey is null or fact.timelineMonthKey >= :fromKey)
          and (:toKey is null or fact.timelineMonthKey <= :toKey)
        group by fact.timelineMonthKey order by fact.timelineMonthKey desc
        """, countQuery = """
        select count(distinct fact.timelineMonthKey) from ProjectFact fact
        where fact.projectId = :projectId and fact.timelineMonthKey is not null
          and (:fromKey is null or fact.timelineMonthKey >= :fromKey)
          and (:toKey is null or fact.timelineMonthKey <= :toKey)
        """)
    Page<TimelinePeriodStatsRow> summarizeTimelineMonths(
        @Param("projectId") UUID projectId, @Param("fromKey") String fromKey, @Param("toKey") String toKey,
        @Param("attention") ProjectFactRecordStatus attention, Pageable pageable
    );

    Page<ProjectFact> findByProjectIdAndTimelineDayKeyOrderByTimelineEventAtDescCreatedAtDesc(
        UUID projectId, String timelineDayKey, Pageable pageable
    );
    Page<ProjectFact> findByProjectIdAndTimelineWeekKeyOrderByTimelineEventAtDescCreatedAtDesc(
        UUID projectId, String timelineWeekKey, Pageable pageable
    );
    Page<ProjectFact> findByProjectIdAndTimelineMonthKeyOrderByTimelineEventAtDescCreatedAtDesc(
        UUID projectId, String timelineMonthKey, Pageable pageable
    );
    List<ProjectFact> findByProjectIdAndTimelineWeekKeyOrderByTimelineEventAtAscCreatedAtAsc(UUID projectId, String timelineWeekKey);
    List<ProjectFact> findByProjectIdAndTimelineMonthKeyOrderByTimelineEventAtAscCreatedAtAsc(UUID projectId, String timelineMonthKey);

    @Query("""
        select new com.projectflow.repository.TimelineFactVersionRow(fact.id, fact.updatedAt)
        from ProjectFact fact where fact.projectId = :projectId and fact.timelineDayKey = :periodKey
        order by fact.id
        """)
    List<TimelineFactVersionRow> dayVersions(@Param("projectId") UUID projectId, @Param("periodKey") String periodKey);

    @Query("""
        select new com.projectflow.repository.TimelineFactVersionRow(fact.id, fact.updatedAt)
        from ProjectFact fact where fact.projectId = :projectId and fact.timelineWeekKey = :periodKey
        order by fact.id
        """)
    List<TimelineFactVersionRow> weekVersions(@Param("projectId") UUID projectId, @Param("periodKey") String periodKey);

    @Query("""
        select new com.projectflow.repository.TimelineFactVersionRow(fact.id, fact.updatedAt)
        from ProjectFact fact where fact.projectId = :projectId and fact.timelineMonthKey = :periodKey
        order by fact.id
        """)
    List<TimelineFactVersionRow> monthVersions(@Param("projectId") UUID projectId, @Param("periodKey") String periodKey);

    @Query("""
        select new com.projectflow.repository.TimelineFactVersionRow(fact.id, fact.updatedAt)
        from ProjectFact fact where fact.projectId = :projectId and fact.timelineEventAt is not null
        order by fact.id
        """)
    List<TimelineFactVersionRow> lifecycleVersions(@Param("projectId") UUID projectId);

    @Query("""
        select new com.projectflow.repository.TimelineFactVersionRow(fact.id, fact.updatedAt)
        from ProjectFact fact where fact.projectId = :projectId order by fact.id
        """)
    List<TimelineFactVersionRow> capabilityVersions(@Param("projectId") UUID projectId);

    @Query("""
        select new com.projectflow.repository.TimelineFactPeriodVersionRow(fact.id, fact.timelineMonthKey, fact.updatedAt)
        from ProjectFact fact where fact.projectId = :projectId and fact.timelineEventAt is not null
        order by fact.timelineMonthKey, fact.id
        """)
    List<TimelineFactPeriodVersionRow> lifecyclePeriodVersions(@Param("projectId") UUID projectId);

    @Query("""
        select fact from ProjectFact fact
        where fact.projectId = :projectId and fact.id in (
          select relation.factId from ProjectTimelineThemeFact relation
          where relation.projectId = :projectId and relation.themeId = :themeId
        ) order by fact.timelineEventAt desc, fact.createdAt desc
        """)
    Page<ProjectFact> findThemeFacts(
        @Param("projectId") UUID projectId, @Param("themeId") UUID themeId, Pageable pageable
    );

    Page<ProjectFact> findByTimelineEventAtIsNull(Pageable pageable);
}
