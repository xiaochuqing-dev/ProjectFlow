package com.projectflow.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.projectflow.entity.ProjectMemoryReadAudit;

public interface ProjectMemoryReadAuditRepository extends JpaRepository<ProjectMemoryReadAudit, UUID> {
    List<ProjectMemoryReadAudit> findTop50ByProjectIdOrderByOccurredAtDesc(UUID projectId);
    void deleteByProjectId(UUID projectId);
}
