package com.projectflow.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.projectflow.entity.ProjectCapability;
import com.projectflow.entity.ProjectCapabilityMaturity;
import com.projectflow.entity.ProjectCapabilityStatus;

import jakarta.persistence.LockModeType;

public interface ProjectCapabilityRepository extends JpaRepository<ProjectCapability, UUID> {
    Optional<ProjectCapability> findByIdAndProjectId(UUID id, UUID projectId);
    Optional<ProjectCapability> findByProjectIdAndStableIdentityKey(UUID projectId, String key);
    Optional<ProjectCapability> findByProjectIdAndLegacyCardId(UUID projectId, UUID legacyCardId);
    List<ProjectCapability> findByProjectIdOrderByCreatedAtAsc(UUID projectId);
    List<ProjectCapability> findByProjectIdAndStatusOrderByFirstFormedAtAsc(UUID projectId, ProjectCapabilityStatus status);
    List<ProjectCapability> findByProjectIdAndMergedIntoCapabilityIdOrderByUpdatedAtDesc(UUID projectId, UUID targetId);
    long countByProjectId(UUID projectId);
    long countByProjectIdAndStatus(UUID projectId, ProjectCapabilityStatus status);
    long countByProjectIdAndMaturityLevel(UUID projectId, ProjectCapabilityMaturity maturity);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select capability from ProjectCapability capability where capability.id = :id and capability.projectId = :projectId")
    Optional<ProjectCapability> findLocked(@Param("projectId") UUID projectId, @Param("id") UUID id);

    @Query(value = """
        select capability from ProjectCapability capability
        where capability.projectId = :projectId
          and (:status is null or capability.status = :status)
          and (:maturity is null or capability.maturityLevel = :maturity)
          and (:search = '' or lower(capability.canonicalName) like lower(concat('%', :search, '%'))
            or lower(capability.currentSummary) like lower(concat('%', :search, '%'))
            or lower(capability.problemSolved) like lower(concat('%', :search, '%')))
        """, countQuery = """
        select count(capability) from ProjectCapability capability
        where capability.projectId = :projectId
          and (:status is null or capability.status = :status)
          and (:maturity is null or capability.maturityLevel = :maturity)
          and (:search = '' or lower(capability.canonicalName) like lower(concat('%', :search, '%'))
            or lower(capability.currentSummary) like lower(concat('%', :search, '%'))
            or lower(capability.problemSolved) like lower(concat('%', :search, '%')))
        """)
    Page<ProjectCapability> search(
        @Param("projectId") UUID projectId,
        @Param("status") ProjectCapabilityStatus status,
        @Param("maturity") ProjectCapabilityMaturity maturity,
        @Param("search") String search,
        Pageable pageable
    );
}
