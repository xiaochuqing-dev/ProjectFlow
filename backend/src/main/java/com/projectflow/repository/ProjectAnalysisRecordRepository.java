package com.projectflow.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.projectflow.entity.ProjectAnalysisRecord;
import com.projectflow.entity.ProjectAnalysisRecordType;

public interface ProjectAnalysisRecordRepository extends JpaRepository<ProjectAnalysisRecord, UUID> {
    List<ProjectAnalysisRecord> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    Optional<DashboardAnalysisView> findFirstByProjectIdAndRecordTypeOrderByCreatedAtDesc(
        UUID projectId,
        ProjectAnalysisRecordType recordType
    );

    void deleteByProjectId(UUID projectId);

    interface DashboardAnalysisView {
        UUID getId();
        String getSummary();
        String getAnalysisSource();
        boolean isModelUsed();
        String getProviderName();
        String getConfidence();
        Instant getCreatedAt();
    }
}
