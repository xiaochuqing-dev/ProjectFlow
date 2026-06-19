package com.projectflow.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.projectflow.entity.ModelUsageRecord;

public interface ModelUsageRecordRepository extends JpaRepository<ModelUsageRecord, UUID> {
    List<ModelUsageRecord> findByProjectIdOrderByCreatedAtDesc(UUID projectId);
}
