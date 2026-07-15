package com.projectflow.repository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.projectflow.entity.ChangeBatch;

import jakarta.persistence.LockModeType;

public interface ChangeBatchRepository extends JpaRepository<ChangeBatch, UUID> {
    List<ChangeBatch> findByProjectIdOrderByScanStartedAtDesc(UUID projectId);
    Page<ChangeBatch> findByProjectIdOrderByScanStartedAtDesc(UUID projectId, Pageable pageable);
    Optional<ChangeBatch> findFirstByProjectIdOrderByScanStartedAtDesc(UUID projectId);
    Optional<ChangeBatch> findFirstByProjectIdAndScanFingerprintOrderByScanStartedAtDesc(UUID projectId, String scanFingerprint);
    Optional<ChangeBatch> findFirstByProjectIdAndBranchNameAndBaseCommitShaAndHeadCommitShaOrderByScanStartedAtDesc(
        UUID projectId, String branchName, String baseCommitSha, String headCommitSha
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select batch from ChangeBatch batch where batch.id = :id")
    Optional<ChangeBatch> findLockedById(@Param("id") UUID id);
}
