package com.projectflow.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.projectflow.entity.ProjectCapabilityFact;
import com.projectflow.entity.ProjectFactRecordStatus;

public interface ProjectCapabilityFactRepository extends JpaRepository<ProjectCapabilityFact, UUID> {
    boolean existsByCapabilityIdAndFactId(UUID capabilityId, UUID factId);
    List<ProjectCapabilityFact> findByProjectIdAndCapabilityId(UUID projectId, UUID capabilityId);
    List<ProjectCapabilityFact> findByProjectIdAndSourceEvolutionId(UUID projectId, UUID evolutionId);
    List<ProjectCapabilityFact> findByProjectIdAndSourceEvolutionIdIn(UUID projectId, List<UUID> evolutionIds);
    List<ProjectCapabilityFact> findByProjectIdAndFactIdIn(UUID projectId, List<UUID> factIds);
    long countByCapabilityId(UUID capabilityId);

    @Query("""
        select new com.projectflow.repository.CapabilityFactStatsRow(
          count(link), count(distinct fact.batchId), coalesce(sum(fact.evidenceCount), 0),
          sum(case when fact.recordStatus = :attention then 1 else 0 end),
          min(coalesce(fact.occurredFrom, fact.occurredTo, fact.createdAt)),
          max(coalesce(fact.occurredTo, fact.occurredFrom, fact.createdAt))
        ) from ProjectCapabilityFact link, ProjectFact fact
        where link.factId = fact.id and link.projectId = :projectId and link.capabilityId = :capabilityId
        """)
    CapabilityFactStatsRow summarize(
        @Param("projectId") UUID projectId,
        @Param("capabilityId") UUID capabilityId,
        @Param("attention") ProjectFactRecordStatus attention
    );

    @Query("""
        select count(distinct ref.commitSha) from ProjectCapabilityFact link, ProjectFactCommitRef ref
        where link.factId = ref.factId and link.projectId = :projectId and link.capabilityId = :capabilityId
        """)
    long countDistinctCommits(@Param("projectId") UUID projectId, @Param("capabilityId") UUID capabilityId);

    @Query(value = """
        select new com.projectflow.repository.CapabilityFactRow(
          fact.id, fact.projectId, fact.batchId, fact.title, fact.summary, fact.occurredFrom, fact.occurredTo,
          fact.recordStatus, fact.attentionReason, fact.commitCount, fact.affectedFileCount, fact.evidenceCount,
          link.relationRole, link.sourceEvolutionId, link.linkedAt
        ) from ProjectCapabilityFact link, ProjectFact fact
        where link.factId = fact.id and link.projectId = :projectId and link.capabilityId = :capabilityId
        order by coalesce(fact.occurredTo, fact.occurredFrom, fact.createdAt) desc
        """, countQuery = """
        select count(link) from ProjectCapabilityFact link
        where link.projectId = :projectId and link.capabilityId = :capabilityId
        """)
    Page<CapabilityFactRow> pageFacts(
        @Param("projectId") UUID projectId,
        @Param("capabilityId") UUID capabilityId,
        Pageable pageable
    );
}
