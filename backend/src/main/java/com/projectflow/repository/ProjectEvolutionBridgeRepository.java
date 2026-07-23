package com.projectflow.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.projectflow.entity.ProjectEvolutionBridge;

public interface ProjectEvolutionBridgeRepository extends JpaRepository<ProjectEvolutionBridge, UUID> {
    boolean existsByProjectIdAndBridgeFingerprint(UUID projectId, String bridgeFingerprint);
    Page<ProjectEvolutionBridge> findByProjectIdOrderByOccurredAtDescCreatedAtDesc(UUID projectId, Pageable pageable);
    void deleteByProjectId(UUID projectId);
}
