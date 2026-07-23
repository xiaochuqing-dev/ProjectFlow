package com.projectflow.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.projectflow.entity.ProjectStructureIndex;

public interface ProjectStructureIndexRepository extends JpaRepository<ProjectStructureIndex, UUID> {
    Optional<ProjectStructureIndex> findByProjectId(UUID projectId);

    void deleteByProjectId(UUID projectId);
}
