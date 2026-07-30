package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.repository.ProjectAgentCandidateRepository;
import com.projectflow.repository.ProjectFactRepository;
import com.projectflow.repository.ProjectMemoryRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.repository.ProjectUnderstandingSnapshotRepository;
import com.projectflow.support.AppException;

class CrossProjectIsolationTest {
    @Test
    void evidenceReadStopsAtProjectOwnershipBeforeLookingUpEvidence() {
        UUID userId = UUID.randomUUID();
        UUID foreignProjectId = UUID.randomUUID();
        ProjectRepository projects = mock(ProjectRepository.class);
        when(projects.findByIdAndUserId(foreignProjectId, userId)).thenReturn(Optional.empty());
        ProjectAgentHistoryService service = new ProjectAgentHistoryService(
            projects,
            mock(ProjectMemoryRepository.class),
            mock(ProjectFactRepository.class),
            mock(ProjectAgentCandidateRepository.class),
            mock(ProjectUnderstandingSnapshotRepository.class),
            mock(ProjectMemorySearchService.class),
            new ObjectMapper().findAndRegisterModules()
        );

        assertThatThrownBy(() -> service.evidence(userId, foreignProjectId, "source:foreign"))
            .isInstanceOf(AppException.class)
            .hasMessageContaining("项目不存在");
    }
}
