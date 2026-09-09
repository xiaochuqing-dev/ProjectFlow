package com.projectflow.service;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.entity.ProjectHistorySnapshot;
import com.projectflow.entity.ProjectSpace;
import com.projectflow.repository.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ProjectWorklineReadTest {
    final UUID user = UUID.randomUUID(), project = UUID.randomUUID();
    final ProjectRepository projects = mock(ProjectRepository.class);
    final ProjectHistorySnapshotRepository snapshots = mock(ProjectHistorySnapshotRepository.class);
    final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    final ProjectHistoryReadService reads = new ProjectHistoryReadService(projects, snapshots,
        mock(ProjectHistoryEventRepository.class), mock(ProjectHistoryCorrectionService.class), mock(ProjectEvidenceTraceService.class),
        new SensitiveContentRedactor(), mock(ProjectHistoryLanguageService.class), mapper);
    @Test void missingSnapshotsAreUnknownAndOwnershipIsRequired() {
        when(projects.findByIdAndUserId(project, user)).thenReturn(Optional.of(mock(ProjectSpace.class)));
        assertThat(reads.worklines(user, project, 0, 12, "ALL", "").items()).isEmpty();
        assertThatThrownBy(() -> reads.worklines(UUID.randomUUID(), project, 0, 12, "ALL", "")).hasMessage("项目不存在");
        verify(snapshots, never()).save(any());
    }
    @Test void pagingAndFreshnessUsePersistedObservationsOnly() throws Exception {
        when(projects.findByIdAndUserId(project, user)).thenReturn(Optional.of(mock(ProjectSpace.class)));
        var item = new ProjectWorklineCollector.Workline("id", "main", "b".repeat(40), "已读取变更", "INFERRED", "MAIN", "UNKNOWN", "UNKNOWN", "", "", null, null, "", "", true, false, List.of(), List.of(), List.of(), null, List.of(), List.of());
        var snapshot = new ProjectWorklineCollector.Snapshot("v1", Instant.EPOCH.toString(), "", "UNAVAILABLE", "main", "LOCAL_REMOTE_HEAD", 40, 1, false, Collections.nCopies(40, item), List.of());
        var history = new ProjectHistorySnapshot(project);
        ReflectionTestUtils.setField(history, "diagnosticsJson", mapper.writeValueAsString(Map.of("worklinesV1", snapshot)));
        when(snapshots.findByProjectId(project)).thenReturn(Optional.of(history));
        var page = reads.worklines(user, project, 1, 12, "MAIN", "main");
        assertThat(page.items()).hasSize(12); assertThat(page.totalPages()).isEqualTo(4); assertThat(page.totalElements()).isEqualTo(40); assertThat(page.stale()).isTrue();
        assertThat(reads.worklines(user, project, 999999, 999999, "ALL", "").items()).isEmpty();
        assertThat(reads.worklines(user, project, 0, 999, "REVIEW", "").items()).isEmpty();
        verify(snapshots, never()).save(any());
    }
}
