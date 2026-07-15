package com.projectflow.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.projectflow.entity.ProjectFactAgentResultRef;

public interface ProjectFactAgentResultRefRepository extends JpaRepository<ProjectFactAgentResultRef, UUID> {
    boolean existsByFactIdAndAgentResultRef(UUID factId, String agentResultRef);

    @Query("select distinct ref.agentResultRef from ProjectFactAgentResultRef ref where ref.projectId = :projectId")
    List<String> findDistinctAgentResultRefsByProjectId(@Param("projectId") UUID projectId);
}
