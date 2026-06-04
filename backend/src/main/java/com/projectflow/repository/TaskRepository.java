package com.projectflow.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.projectflow.entity.TaskItem;

public interface TaskRepository extends JpaRepository<TaskItem, UUID> {
    List<TaskItem> findByProjectIdOrderByUpdatedAtDesc(UUID projectId);
}
