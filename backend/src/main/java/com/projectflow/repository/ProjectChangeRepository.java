package com.projectflow.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.projectflow.entity.ProjectChange;
import com.projectflow.entity.ProjectChangeSourceType;

public interface ProjectChangeRepository extends JpaRepository<ProjectChange, UUID> {
    List<ProjectChange> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    void deleteByProjectId(UUID projectId);

    Optional<ProjectChange> findByLinkedSuggestionId(UUID linkedSuggestionId);

    Optional<ProjectChange> findBySourceTypeAndSourceRef(ProjectChangeSourceType sourceType, String sourceRef);
}
