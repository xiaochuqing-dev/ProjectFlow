package com.projectflow.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.projectflow.entity.ProjectAgentCandidate;

public interface ProjectAgentCandidateRepository extends JpaRepository<ProjectAgentCandidate, UUID> {
    Page<ProjectAgentCandidate> findByProjectIdOrderByCreatedAtDesc(UUID projectId, Pageable pageable);
    List<ProjectAgentCandidate> findTop100ByProjectIdOrderByCreatedAtDesc(UUID projectId);
    long countByProjectId(UUID projectId);
    void deleteByProjectId(UUID projectId);
}
