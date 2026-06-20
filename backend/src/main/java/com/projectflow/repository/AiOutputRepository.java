package com.projectflow.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.projectflow.entity.AiOutput;

public interface AiOutputRepository extends JpaRepository<AiOutput, UUID> {
    List<AiOutput> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    void deleteByProjectId(UUID projectId);
}
