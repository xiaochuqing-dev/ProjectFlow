package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.entity.ProjectSpace;
import com.projectflow.entity.ProjectStatus;
import com.projectflow.repository.ProjectAgentCandidateRepository;
import com.projectflow.repository.ProjectFactRepository;
import com.projectflow.repository.ProjectMemoryRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.repository.ProjectUnderstandingSnapshotRepository;

class MultiProjectAuthorizationTest {
    @Test
    void catalogListsEveryProjectReturnedByTheOwnedProjectRepositoryBoundary() {
        UUID userId = UUID.randomUUID();
        ProjectRepository projects = mock(ProjectRepository.class);
        ProjectFactRepository facts = mock(ProjectFactRepository.class);
        ProjectAgentCandidateRepository candidates = mock(ProjectAgentCandidateRepository.class);
        ProjectMemoryRepository memories = mock(ProjectMemoryRepository.class);
        ProjectUnderstandingSnapshotRepository snapshots = mock(ProjectUnderstandingSnapshotRepository.class);
        ProjectSpace first = project(userId, "alpha");
        ProjectSpace second = project(userId, "beta");
        when(projects.findByUserIdOrderByUpdatedAtDesc(userId)).thenReturn(List.of(first, second));
        for (ProjectSpace project : List.of(first, second)) {
            when(snapshots.findByProjectId(project.getId())).thenReturn(Optional.empty());
            when(facts.summarize(project.getId(), com.projectflow.entity.ProjectFactRecordStatus.RECORDED,
                com.projectflow.entity.ProjectFactRecordStatus.NEEDS_ATTENTION)).thenReturn(null);
            when(facts.findTop200ByProjectIdOrderByOccurredToDescCreatedAtDesc(project.getId())).thenReturn(List.of());
            when(candidates.findTop100ByProjectIdOrderByCreatedAtDesc(project.getId())).thenReturn(List.of());
            when(memories.findByProjectId(project.getId())).thenReturn(Optional.empty());
        }
        ProjectAgentHistoryService service = new ProjectAgentHistoryService(
            projects, memories, facts, candidates, snapshots,
            mock(ProjectMemorySearchService.class), new ObjectMapper().findAndRegisterModules()
        );

        assertThat(service.catalog(userId).items())
            .extracting(item -> item.projectId())
            .containsExactly(first.getId(), second.getId());
    }

    private static ProjectSpace project(UUID userId, String name) {
        ProjectSpace value = new ProjectSpace(userId);
        ReflectionTestUtils.setField(value, "id", UUID.randomUUID());
        value.update(name, "test", ProjectStatus.BUILDING, List.of(), "", LocalDate.now(), null);
        return value;
    }
}
