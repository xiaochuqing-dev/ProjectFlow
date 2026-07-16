package com.projectflow.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.projectflow.entity.ProjectCapabilityAttention;
import com.projectflow.entity.ProjectCapabilityAttentionStatus;

public interface ProjectCapabilityAttentionRepository extends JpaRepository<ProjectCapabilityAttention, UUID> {
    Optional<ProjectCapabilityAttention> findByProjectIdAndAttentionFingerprint(UUID projectId, String fingerprint);
    Page<ProjectCapabilityAttention> findByProjectIdAndStatusOrderByCreatedAtDesc(UUID projectId, ProjectCapabilityAttentionStatus status, Pageable pageable);
    long countByProjectIdAndStatus(UUID projectId, ProjectCapabilityAttentionStatus status);
}
