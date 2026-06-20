package com.projectflow.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.projectflow.entity.ImportRecord;

public interface ImportRecordRepository extends JpaRepository<ImportRecord, UUID> {
    List<ImportRecord> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    void deleteByProjectId(UUID projectId);
}
