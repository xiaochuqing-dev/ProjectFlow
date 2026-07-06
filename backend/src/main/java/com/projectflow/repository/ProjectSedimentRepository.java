package com.projectflow.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.projectflow.entity.ProjectSediment;

public interface ProjectSedimentRepository extends JpaRepository<ProjectSediment, UUID> {
    List<ProjectSediment> findByProjectIdOrderByUpdatedAtDesc(UUID projectId);
    Optional<ProjectSediment> findByIdAndProjectId(UUID id, UUID projectId);
}
