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
    final ProjectHistoryCorrectionService corrections = mock(ProjectHistoryCorrectionService.class);
    final ProjectHistoryReadService reads = new ProjectHistoryReadService(projects, snapshots,
        mock(ProjectHistoryEventRepository.class), corrections, mock(ProjectEvidenceTraceService.class),
        new SensitiveContentRedactor(), mock(ProjectHistoryLanguageService.class), mapper);
    @Test void missingSnapshotsAreUnknownAndOwnershipIsRequired() {
        when(projects.findByIdAndUserId(project, user)).thenReturn(Optional.of(mock(ProjectSpace.class)));
        assertThat(reads.worklines(user, project, 0, 12, "ALL", "").items()).isEmpty();
        assertThatThrownBy(() -> reads.worklines(UUID.randomUUID(), project, 0, 12, "ALL", "")).hasMessage("项目不存在");
        verify(snapshots, never()).save(any());
    }
    @Test void firstRefreshWithEmptyJsonHasReadableCurrentStateAndOverview() {
        when(projects.findByIdAndUserId(project, user)).thenReturn(Optional.of(mock(ProjectSpace.class)));
        var history = new ProjectHistorySnapshot(project); history.begin(UUID.randomUUID(), true);
        when(snapshots.findByProjectId(project)).thenReturn(Optional.of(history));
        when(corrections.resolve(project, history)).thenReturn(new ProjectHistoryCorrectionService.CorrectedHistory(List.of(), List.of(), List.of(), "initial", List.of()));
        assertThat(reads.currentState(user, project).historyStatus()).isEqualTo("RUNNING");
        assertThat(reads.currentState(user, project).recentConfirmedChanges()).isEmpty();
        assertThat(reads.overview(user, project).overview().chapters()).isEmpty();
        verify(snapshots, never()).save(any());
    }
    @Test void recentStoryPagingSelectsLatestResultsBeforeApplyingThePageLimit() {
        when(projects.findByIdAndUserId(project, user)).thenReturn(Optional.of(mock(ProjectSpace.class)));
        var history = new ProjectHistorySnapshot(project);
        when(snapshots.findByProjectId(project)).thenReturn(Optional.of(history));
        var old = mock(com.projectflow.dto.ProjectHistoryDtos.ChangeStory.class);
        var recent = mock(com.projectflow.dto.ProjectHistoryDtos.ChangeStory.class);
        when(old.id()).thenReturn("old"); when(recent.id()).thenReturn("recent");
        when(old.occurredFrom()).thenReturn(Instant.EPOCH); when(old.occurredTo()).thenReturn(Instant.EPOCH);
        when(recent.occurredFrom()).thenReturn(Instant.EPOCH.plusSeconds(60)); when(recent.occurredTo()).thenReturn(Instant.EPOCH.plusSeconds(120));
        when(corrections.resolve(project, history)).thenReturn(new ProjectHistoryCorrectionService.CorrectedHistory(List.of(), List.of(old, recent), List.of(), "r", List.of()));
        assertThat(reads.stories(user, project, null, false, false, null, null, 0, 1, true).items()).containsExactly(recent);
        assertThat(reads.stories(user, project, null, false, false, null, null, 0, 1).items()).containsExactly(old);
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
