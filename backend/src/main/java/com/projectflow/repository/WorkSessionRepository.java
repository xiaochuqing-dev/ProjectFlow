package com.projectflow.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.projectflow.entity.WorkSession;

public interface WorkSessionRepository extends JpaRepository<WorkSession, UUID> {
    List<WorkSession> findByProjectIdOrderByEndTimeDesc(UUID projectId);

    void deleteByProjectId(UUID projectId);
}
