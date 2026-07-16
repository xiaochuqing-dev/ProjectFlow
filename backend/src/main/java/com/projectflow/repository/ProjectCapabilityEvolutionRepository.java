package com.projectflow.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.projectflow.entity.ProjectCapabilityEvolution;

public interface ProjectCapabilityEvolutionRepository extends JpaRepository<ProjectCapabilityEvolution, UUID> {
    Optional<ProjectCapabilityEvolution> findByProjectIdAndOperationFingerprint(UUID projectId, String fingerprint);
    Page<ProjectCapabilityEvolution> findByProjectIdAndCapabilityIdOrderByOccurredAtDescCreatedAtDesc(UUID projectId, UUID capabilityId, Pageable pageable);
    Page<ProjectCapabilityEvolution> findByProjectIdOrderByOccurredAtDescCreatedAtDesc(UUID projectId, Pageable pageable);
    List<ProjectCapabilityEvolution> findByProjectIdAndCapabilityIdOrderByOccurredAtAscCreatedAtAsc(UUID projectId, UUID capabilityId);
    long countByCapabilityId(UUID capabilityId);
}
