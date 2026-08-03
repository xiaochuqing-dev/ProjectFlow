package com.projectflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.projectflow.entity.ProjectHistorySnapshot;
import com.projectflow.entity.ProjectSpace;
import com.projectflow.entity.ProjectStatus;
import com.projectflow.repository.ProjectHistorySnapshotRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.service.ProjectHistoryReconstructionService;
import com.projectflow.service.ProjectHistorySourceCollector;

@SpringBootTest
@ActiveProfiles("test")
class ProjectHistoryRefreshFailureTest {
    @Autowired ProjectRepository projectRepository;
    @Autowired ProjectHistorySnapshotRepository snapshotRepository;
    @Autowired ProjectHistoryReconstructionService reconstructionService;
    @MockitoBean ProjectHistorySourceCollector sourceCollector;

    @Test
    void recordsFailedSnapshotWhenSourceDiscoveryFailsBeforeUpsert() {
        UUID userId = UUID.randomUUID();
        ProjectSpace project = new ProjectSpace(userId);
        project.update("History Failure", "Failure fixture", ProjectStatus.BUILDING, List.of(), "", LocalDate.now(), null);
        project = projectRepository.saveAndFlush(project);
        when(sourceCollector.collect(userId, project.getId()))
            .thenThrow(new IllegalStateException("discovery failed at C:\\Users\\private-user\\project"));

        UUID projectId = project.getId();
        assertThatThrownBy(() -> reconstructionService.refresh(userId, projectId, UUID.randomUUID(), false))
            .isInstanceOf(IllegalStateException.class);

        ProjectHistorySnapshot snapshot = snapshotRepository.findByProjectId(projectId).orElseThrow();
        assertThat(snapshot.getStatus()).isEqualTo(ProjectHistorySnapshot.Status.FAILED);
        assertThat(snapshot.getErrorCode()).isEqualTo("PROJECT_HISTORY_REFRESH_FAILED");
        assertThat(snapshot.getErrorSummary()).doesNotContain("C:\\Users\\private-user").contains("ABSOLUTE_PATH_REDACTED");
    }
}
