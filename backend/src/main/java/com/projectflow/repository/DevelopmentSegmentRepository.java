package com.projectflow.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.projectflow.entity.DevelopmentSegment;

public interface DevelopmentSegmentRepository extends JpaRepository<DevelopmentSegment, UUID> {
    List<DevelopmentSegment> findByBatchIdOrderByCreatedAtAsc(UUID batchId);
    List<DevelopmentSegment> findByProjectIdOrderByCreatedAtDesc(UUID projectId);
}
