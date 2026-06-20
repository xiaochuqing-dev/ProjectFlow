package com.projectflow.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.projectflow.entity.ProjectAnalysisRecord;

public interface ProjectAnalysisRecordRepository extends JpaRepository<ProjectAnalysisRecord, UUID> {
    List<ProjectAnalysisRecord> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    void deleteByProjectId(UUID projectId);
}
