package com.projectflow.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.projectflow.entity.ProjectChange;
import com.projectflow.entity.ProjectChangeSourceType;
import com.projectflow.entity.ProjectChangeStatus;

public interface ProjectChangeRepository extends JpaRepository<ProjectChange, UUID> {
    List<ProjectChange> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    void deleteByProjectId(UUID projectId);

    Optional<ProjectChange> findByLinkedSuggestionId(UUID linkedSuggestionId);

    Optional<ProjectChange> findBySourceTypeAndSourceRef(ProjectChangeSourceType sourceType, String sourceRef);

    Optional<ProjectChange> findByDevelopmentSegmentId(UUID developmentSegmentId);

    List<ProjectChange> findBySourceBatchIdOrderByCreatedAtAsc(UUID sourceBatchId);

    List<ProjectChange> findBySourceBatchIdInOrderByCreatedAtAsc(List<UUID> sourceBatchIds);

    long countByProjectIdAndSourceBatchIdIsNotNullAndStatusIn(UUID projectId, List<ProjectChangeStatus> statuses);
}
