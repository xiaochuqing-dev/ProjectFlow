package com.projectflow.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.projectflow.entity.ProjectFactFileRef;

public interface ProjectFactFileRefRepository extends JpaRepository<ProjectFactFileRef, UUID> {
    boolean existsByFactIdAndFilePath(UUID factId, String filePath);

    @Query("""
        select fact.timelineDayKey as periodKey, count(distinct ref.filePath) as itemCount
        from ProjectFactFileRef ref, ProjectFact fact
        where ref.factId = fact.id and ref.projectId = :projectId and fact.timelineDayKey in :keys
        group by fact.timelineDayKey
        """)
    List<TimelineNamedCountRow> countByTimelineDays(@Param("projectId") UUID projectId, @Param("keys") List<String> keys);

    @Query("""
        select fact.timelineWeekKey as periodKey, count(distinct ref.filePath) as itemCount
        from ProjectFactFileRef ref, ProjectFact fact
        where ref.factId = fact.id and ref.projectId = :projectId and fact.timelineWeekKey in :keys
        group by fact.timelineWeekKey
        """)
    List<TimelineNamedCountRow> countByTimelineWeeks(@Param("projectId") UUID projectId, @Param("keys") List<String> keys);

    @Query("""
        select fact.timelineMonthKey as periodKey, count(distinct ref.filePath) as itemCount
        from ProjectFactFileRef ref, ProjectFact fact
        where ref.factId = fact.id and ref.projectId = :projectId and fact.timelineMonthKey in :keys
        group by fact.timelineMonthKey
        """)
    List<TimelineNamedCountRow> countByTimelineMonths(@Param("projectId") UUID projectId, @Param("keys") List<String> keys);

    @Query("""
        select count(distinct ref.filePath) from ProjectFactFileRef ref, ProjectFact fact
        where ref.factId = fact.id and ref.projectId = :projectId and fact.timelineDayKey = :key
        """)
    long countByTimelineDay(@Param("projectId") UUID projectId, @Param("key") String key);

    @Query("""
        select count(distinct ref.filePath) from ProjectFactFileRef ref, ProjectFact fact
        where ref.factId = fact.id and ref.projectId = :projectId and fact.timelineWeekKey = :key
        """)
    long countByTimelineWeek(@Param("projectId") UUID projectId, @Param("key") String key);

    @Query("""
        select count(distinct ref.filePath) from ProjectFactFileRef ref, ProjectFact fact
        where ref.factId = fact.id and ref.projectId = :projectId and fact.timelineMonthKey = :key
        """)
    long countByTimelineMonth(@Param("projectId") UUID projectId, @Param("key") String key);

    @Query("select count(distinct ref.filePath) from ProjectFactFileRef ref where ref.projectId = :projectId")
    long countDistinctByProjectId(@Param("projectId") UUID projectId);
}
