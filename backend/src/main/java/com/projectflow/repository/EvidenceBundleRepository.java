package com.projectflow.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.projectflow.entity.EvidenceBundle;

public interface EvidenceBundleRepository extends JpaRepository<EvidenceBundle, UUID> {
    List<EvidenceBundle> findByProjectIdOrderByUpdatedAtDesc(UUID projectId);

    Optional<EvidenceBundle> findByWorkSessionId(UUID workSessionId);
}
