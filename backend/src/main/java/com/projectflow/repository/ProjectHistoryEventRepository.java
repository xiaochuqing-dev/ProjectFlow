package com.projectflow.repository;

import java.util.List;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.projectflow.entity.ProjectHistoryEvent;
import com.projectflow.entity.ProjectHistoryEvent.RewriteState;

public interface ProjectHistoryEventRepository
    extends JpaRepository<ProjectHistoryEvent, UUID>, JpaSpecificationExecutor<ProjectHistoryEvent> {

    Optional<ProjectHistoryEvent> findByProjectIdAndStableEventKey(UUID projectId, String stableEventKey);

    Optional<ProjectHistoryEvent> findByIdAndProjectId(UUID id, UUID projectId);

    List<ProjectHistoryEvent> findByProjectIdAndRewriteStateOrderByOccurredAtAscIdAsc(
        UUID projectId,
        RewriteState rewriteState
    );

    List<ProjectHistoryEvent> findByProjectId(UUID projectId);

    List<ProjectHistoryEvent> findByProjectIdAndIdIn(UUID projectId, Collection<UUID> ids);

    long countByProjectIdAndRewriteState(UUID projectId, RewriteState rewriteState);

    void deleteByProjectId(UUID projectId);
}
