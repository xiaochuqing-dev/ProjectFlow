package com.projectflow.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.projectflow.entity.ProjectMaterial;

public interface ProjectMaterialRepository extends JpaRepository<ProjectMaterial, UUID> {
    List<ProjectMaterial> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    void deleteByProjectId(UUID projectId);
}
