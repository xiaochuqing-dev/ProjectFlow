package com.projectflow.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.projectflow.entity.ProjectCapabilityFactClassification;
import com.projectflow.entity.ProjectCapabilityFactCoverage;

public interface ProjectCapabilityFactCoverageRepository extends JpaRepository<ProjectCapabilityFactCoverage, UUID> {
    Optional<ProjectCapabilityFactCoverage> findByProjectIdAndFactId(UUID projectId, UUID factId);
    List<ProjectCapabilityFactCoverage> findByProjectIdAndFactIdIn(UUID projectId, List<UUID> factIds);
    long countByProjectId(UUID projectId);
    long countByProjectIdAndClassification(UUID projectId, ProjectCapabilityFactClassification classification);
}
