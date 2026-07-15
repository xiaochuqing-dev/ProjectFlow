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
    long countByProjectIdAndRecordStatus(UUID projectId, ProjectFactRecordStatus recordStatus);
    long countByBatchId(UUID batchId);
    long countByBatchIdAndRecordStatus(UUID batchId, ProjectFactRecordStatus recordStatus);

    Optional<ProjectFact> findFirstByProjectIdOrderByOccurredFromAscCreatedAtAsc(UUID projectId);
    Optional<ProjectFact> findFirstByProjectIdOrderByOccurredToDescCreatedAtDesc(UUID projectId);
    List<ProjectFact> findTop10ByProjectIdOrderByOccurredToDescCreatedAtDesc(UUID projectId);

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
          sum(case when fact.recordStatus = :attention then 1 else 0 end),
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
}
