package com.projectflow.repository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.projectflow.entity.ChangeBatch;

public interface ChangeBatchRepository extends JpaRepository<ChangeBatch, UUID> {
    List<ChangeBatch> findByProjectIdOrderByScanStartedAtDesc(UUID projectId);
    Optional<ChangeBatch> findFirstByProjectIdOrderByScanStartedAtDesc(UUID projectId);
    Optional<ChangeBatch> findFirstByProjectIdAndScanFingerprintOrderByScanStartedAtDesc(UUID projectId, String scanFingerprint);
    Optional<ChangeBatch> findFirstByProjectIdAndBranchNameAndBaseCommitShaAndHeadCommitShaOrderByScanStartedAtDesc(
        UUID projectId, String branchName, String baseCommitSha, String headCommitSha
    );
}
