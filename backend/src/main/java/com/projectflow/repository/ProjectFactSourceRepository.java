package com.projectflow.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.projectflow.entity.ProjectFactSource;

public interface ProjectFactSourceRepository extends JpaRepository<ProjectFactSource, UUID> {
    List<ProjectFactSource> findByProjectIdOrderByUpdatedAtDesc(UUID projectId);

    Optional<ProjectFactSource> findByProjectIdAndFieldKey(UUID projectId, String fieldKey);
}
