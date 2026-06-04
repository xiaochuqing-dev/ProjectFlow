package com.projectflow.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.projectflow.entity.DevLog;

public interface DevLogRepository extends JpaRepository<DevLog, UUID> {
    List<DevLog> findByProjectIdOrderByLogDateDescUpdatedAtDesc(UUID projectId);
}
