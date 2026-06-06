package com.projectflow.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.projectflow.entity.ProjectEvolutionRecord;

public interface ProjectEvolutionRecordRepository extends JpaRepository<ProjectEvolutionRecord, UUID> {
    List<ProjectEvolutionRecord> findByProjectIdOrderByCreatedAtDesc(UUID projectId);
}
