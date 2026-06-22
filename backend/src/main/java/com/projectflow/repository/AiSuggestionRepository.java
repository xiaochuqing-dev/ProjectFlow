package com.projectflow.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.projectflow.entity.AiSuggestion;

public interface AiSuggestionRepository extends JpaRepository<AiSuggestion, UUID> {
    List<AiSuggestion> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    void deleteByProjectId(UUID projectId);
}
