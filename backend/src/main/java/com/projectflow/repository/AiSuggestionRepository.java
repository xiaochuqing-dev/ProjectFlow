package com.projectflow.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.projectflow.entity.AiSuggestion;
import com.projectflow.entity.AiSuggestionStatus;

public interface AiSuggestionRepository extends JpaRepository<AiSuggestion, UUID> {
    List<AiSuggestion> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    List<AiSuggestion> findByProjectIdAndStatusOrderByCreatedAtDesc(UUID projectId, AiSuggestionStatus status);

    void deleteByProjectId(UUID projectId);
}
