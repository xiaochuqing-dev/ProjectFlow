package com.projectflow.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.projectflow.entity.ProjectMemory;

public interface ProjectMemoryRepository extends JpaRepository<ProjectMemory, UUID> {
    Optional<ProjectMemory> findByProjectId(UUID projectId);

    void deleteByProjectId(UUID projectId);
}
