package com.projectflow.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.projectflow.entity.ProjectFactCommitRef;

public interface ProjectFactCommitRefRepository extends JpaRepository<ProjectFactCommitRef, UUID> {
    boolean existsByFactIdAndCommitSha(UUID factId, String commitSha);
    List<ProjectFactCommitRef> findByFactId(UUID factId);

    @Query("select distinct ref.commitSha from ProjectFactCommitRef ref where ref.projectId = :projectId")
    List<String> findDistinctCommitShasByProjectId(@Param("projectId") UUID projectId);

    @Query("select count(distinct ref.commitSha) from ProjectFactCommitRef ref where ref.projectId = :projectId")
    long countDistinctCommitShaByProjectId(@Param("projectId") UUID projectId);
}
