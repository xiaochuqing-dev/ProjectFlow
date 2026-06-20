package com.projectflow.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.projectflow.entity.ProjectSnapshot;

public interface ProjectSnapshotRepository extends JpaRepository<ProjectSnapshot, UUID> {
    List<ProjectSnapshot> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    void deleteByProjectId(UUID projectId);

    Optional<ProjectSnapshot> findFirstByProjectIdOrderByCreatedAtDesc(UUID projectId);
}
