package com.projectflow.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.projectflow.entity.ProjectChange;

public interface ProjectChangeRepository extends JpaRepository<ProjectChange, UUID> {
    List<ProjectChange> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    Optional<ProjectChange> findByLinkedSuggestionId(UUID linkedSuggestionId);
}
