package com.projectflow.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.projectflow.entity.CapabilityCardStatus;
import com.projectflow.entity.ProjectCapabilityCard;

public interface ProjectCapabilityCardRepository extends JpaRepository<ProjectCapabilityCard, UUID> {
    List<ProjectCapabilityCard> findByProjectIdOrderByCreatedAtDesc(UUID projectId);
    List<ProjectCapabilityCard> findByStatusOrderByCreatedAtAsc(CapabilityCardStatus status);
    void deleteByProjectIdAndStatus(UUID projectId, CapabilityCardStatus status);
}
