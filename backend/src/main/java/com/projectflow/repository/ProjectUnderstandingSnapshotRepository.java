package com.projectflow.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.projectflow.entity.ProjectUnderstandingSnapshot;

public interface ProjectUnderstandingSnapshotRepository extends JpaRepository<ProjectUnderstandingSnapshot, UUID> {
    Optional<ProjectUnderstandingSnapshot> findByProjectId(UUID projectId);

    void deleteByProjectId(UUID projectId);
}
