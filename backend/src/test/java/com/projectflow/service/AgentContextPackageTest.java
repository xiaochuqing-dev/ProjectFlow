package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.entity.EvidenceConfidence;
import com.projectflow.entity.ProjectFact;
import com.projectflow.entity.ProjectFactOrigin;
import com.projectflow.entity.ProjectFactEpistemicStatus;
import com.projectflow.entity.ProjectFactRecordStatus;
import com.projectflow.entity.ProjectSpace;
import com.projectflow.entity.ProjectStatus;
import com.projectflow.repository.ProjectAgentCandidateRepository;
import com.projectflow.repository.ProjectFactRepository;
import com.projectflow.repository.ProjectMemoryRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.repository.ProjectUnderstandingSnapshotRepository;
import com.projectflow.support.AppException;

class AgentContextPackageTest {
    @Test
    void packageComesFromPersistedFactsAndRespectsItsCharacterBudget() {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        ProjectSpace project = new ProjectSpace(userId);
        ReflectionTestUtils.setField(project, "id", projectId);
        project.update("context-project", "test", ProjectStatus.BUILDING, List.of(), "", LocalDate.now(), null);
        ProjectFact fact = new ProjectFact(
            projectId, UUID.randomUUID(), UUID.randomUUID(), ProjectFactOrigin.INCREMENTAL_SCAN, "a".repeat(64)
        );
        fact.updateContent(
            "项目级写入边界", "候选写入不会直接创建强事实", List.of(), "",
            Instant.parse("2026-07-29T00:00:00Z"), Instant.parse("2026-07-29T00:00:00Z"),
            List.of("abcdef12"), List.of(), List.of(), List.of("src/Boundary.java"),
            List.of("commit:abcdef12", "file:src/Boundary.java"), "LOCAL_RULE", "PASS",
            EvidenceConfidence.HIGH, ProjectFactRecordStatus.RECORDED, ""
        );
        ProjectRepository projects = mock(ProjectRepository.class);
        ProjectFactRepository facts = mock(ProjectFactRepository.class);
        ProjectAgentCandidateRepository candidates = mock(ProjectAgentCandidateRepository.class);
        ProjectUnderstandingSnapshotRepository snapshots = mock(ProjectUnderstandingSnapshotRepository.class);
        when(projects.findByIdAndUserId(projectId, userId)).thenReturn(Optional.of(project));
        when(facts.findTop200ByProjectIdOrderByOccurredToDescCreatedAtDesc(projectId)).thenReturn(List.of(fact));
        when(candidates.findTop100ByProjectIdOrderByCreatedAtDesc(projectId)).thenReturn(List.of());
        when(snapshots.findByProjectId(projectId)).thenReturn(Optional.empty());
        ProjectAgentHistoryService service = new ProjectAgentHistoryService(
            projects, mock(ProjectMemoryRepository.class), facts, candidates, snapshots,
            mock(ProjectMemorySearchService.class), new ObjectMapper().findAndRegisterModules()
        );

        var result = service.contextPackage(userId, projectId, 2_000);

        assertThat(result.currentStrongFacts()).singleElement()
            .satisfies(item -> {
                assertThat(item.epistemicStatus()).isEqualTo("OBSERVED");
                assertThat(item.evidenceRefs()).contains("commit:abcdef12");
            });
        assertThat(result.provenance()).contains("fact:" + fact.getId());
        assertThat(result.actualCharacters()).isLessThanOrEqualTo(result.sizeBudget());
    }

    @Test
    void taskPackageRanksRelevantFactsRedactsTaskAndHasDeterministicRevision() {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        ProjectSpace project = new ProjectSpace(userId);
        ReflectionTestUtils.setField(project, "id", projectId);
        project.update("context-project", "test", ProjectStatus.BUILDING, List.of(), "", LocalDate.now(), null);
        ProjectFact mail = fact(
            projectId, "邮件失败重试和状态反馈", "src/mail/MailService.java",
            "revision-1", ProjectFactEpistemicStatus.VERIFIED
        );
        ProjectFact colors = fact(
            projectId, "首页主题颜色调整", "src/ui/Theme.tsx",
            "revision-1", ProjectFactEpistemicStatus.OBSERVED
        );
        ProjectRepository projects = mock(ProjectRepository.class);
        ProjectFactRepository facts = mock(ProjectFactRepository.class);
        ProjectAgentCandidateRepository candidates = mock(ProjectAgentCandidateRepository.class);
        ProjectUnderstandingSnapshotRepository snapshots = mock(ProjectUnderstandingSnapshotRepository.class);
        when(projects.findByIdAndUserId(projectId, userId)).thenReturn(Optional.of(project));
        when(facts.findTop200ByProjectIdOrderByOccurredToDescCreatedAtDesc(projectId)).thenReturn(List.of(colors, mail));
        when(candidates.findTop100ByProjectIdOrderByCreatedAtDesc(projectId)).thenReturn(List.of());
        when(snapshots.findByProjectId(projectId)).thenReturn(Optional.empty());
        ProjectAgentHistoryService service = new ProjectAgentHistoryService(
            projects, mock(ProjectMemoryRepository.class), facts, candidates, snapshots,
            mock(ProjectMemorySearchService.class), new ObjectMapper().findAndRegisterModules()
        );

        var first = service.contextPackage(
            userId, projectId, "改进邮件失败重试 api_key=abcdefghijklmnop", List.of("src/mail"),
            "CURRENT_SNAPSHOT", "DEEP", 8_000
        );
        var second = service.contextPackage(
            userId, projectId, "改进邮件失败重试 api_key=abcdefghijklmnop", List.of("src/mail"),
            "CURRENT_SNAPSHOT", "DEEP", 8_000
        );

        assertThat(first.currentStrongFacts()).extracting(item -> item.itemId())
            .containsExactly("fact:" + mail.getId());
        assertThat(first.taskDescription()).contains(SensitiveContentRedactor.REDACTED)
            .doesNotContain("abcdefghijklmnop");
        assertThat(first.packageRevision()).isEqualTo(second.packageRevision()).startsWith("sha256:");
        assertThat(first.trustGuidance().generallyReusableItemIds()).contains("fact:" + mail.getId());
        assertThat(first.coverageDisclosure().partial()).isTrue();
        assertThat(first.generationMetadata().modelCalled()).isFalse();
    }

    @Test
    void contextScopeRejectsAbsoluteOrParentTraversalPaths() {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        ProjectRepository projects = mock(ProjectRepository.class);
        ProjectSpace project = mock(ProjectSpace.class);
        when(projects.findByIdAndUserId(projectId, userId)).thenReturn(Optional.of(project));
        ProjectAgentHistoryService service = new ProjectAgentHistoryService(
            projects, mock(ProjectMemoryRepository.class), mock(ProjectFactRepository.class),
            mock(ProjectAgentCandidateRepository.class), mock(ProjectUnderstandingSnapshotRepository.class),
            mock(ProjectMemorySearchService.class), new ObjectMapper().findAndRegisterModules()
        );

        assertThatThrownBy(() -> service.contextPackage(
            userId, projectId, "task", List.of("../other-project"), "CURRENT_SNAPSHOT", "STANDARD", 8_000
        )).isInstanceOf(AppException.class);
    }

    private static ProjectFact fact(
        UUID projectId,
        String statement,
        String path,
        String revision,
        ProjectFactEpistemicStatus epistemicStatus
    ) {
        ProjectFact fact = new ProjectFact(
            projectId, UUID.randomUUID(), UUID.randomUUID(), ProjectFactOrigin.INCREMENTAL_SCAN,
            UUID.randomUUID().toString().replace("-", "")
        );
        Instant occurred = Instant.parse("2026-07-29T00:00:00Z");
        fact.updateContent(
            statement, statement, List.of(), "", occurred, occurred, List.of(), List.of(), List.of(),
            List.of(path), List.of("file:" + path),
            "LOCAL_RULE", "PASS", EvidenceConfidence.HIGH, ProjectFactRecordStatus.RECORDED, ""
        );
        fact.applyKnowledgeContract(
            epistemicStatus, List.of("PROJECT_FILE"), "CURRENT", revision, occurred, occurred,
            List.of(), List.of(), "ENGINEERING_VALIDATION", "", "", "VALIDATED"
        );
        return fact;
    }
}
