package com.projectflow.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.projectflow.entity.AgentSignatureFeedback;

public interface AgentSignatureFeedbackRepository extends JpaRepository<AgentSignatureFeedback, UUID> {
    List<AgentSignatureFeedback> findByProjectIdOrderByUpdatedAtDesc(UUID projectId);

    Optional<AgentSignatureFeedback> findFirstByProjectIdAndAgentNameOrderByUpdatedAtDesc(UUID projectId, String agentName);
}
