package com.projectflow.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.projectflow.entity.ProjectSpace;

public interface ProjectRepository extends JpaRepository<ProjectSpace, UUID> {
    List<ProjectSpace> findByUserIdOrderByUpdatedAtDesc(UUID userId);

    Optional<ProjectSpace> findByIdAndUserId(UUID id, UUID userId);
}
