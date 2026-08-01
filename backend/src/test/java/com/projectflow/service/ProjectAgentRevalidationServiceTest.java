package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.projectflow.dto.ProjectAgentHistoryDtos.AgentEvidenceResponse;
import com.projectflow.dto.ProjectAgentRevalidationDtos.AgentRevalidationRequest;
import com.projectflow.entity.ProjectFact;
import com.projectflow.entity.ProjectMemory;
import com.projectflow.entity.ProjectSpace;
import com.projectflow.repository.ProjectFactRepository;
import com.projectflow.repository.ProjectMemoryRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.support.AppException;

class ProjectAgentRevalidationServiceTest {
    @TempDir Path projectRoot;

    @Test
    void rereadRangeIsBoundedRedactedAndProjectRelative() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Files.createDirectories(projectRoot.resolve("docs"));
        Files.writeString(projectRoot.resolve("docs/spec.md"), String.join("\n", List.of(
            "head", "retry policy", "status feedback", "tail"
        )));
        ProjectRepository projects = owned(userId, projectId);
        ProjectMemoryRepository memories = memory(projectId);
        ProjectAgentHistoryService history = mock(ProjectAgentHistoryService.class);
        when(history.evidence(userId, projectId, "source:spec")).thenReturn(new AgentEvidenceResponse(
            projectId, "source:spec", "DOC", "DOCUMENT_CANDIDATE", "docs/spec.md",
            "SPECIFICATION", "HIGH", "CURRENT", "HIGH", "PLANNED", "邮件重试规范",
            List.of("source:spec"), "abcdef1234567"
        ));
        LocalCommandExecutor commands = (directory, command, timeout) ->
            new LocalCommandExecutor.CommandResult(0, "abcdef1234567\n", false);
        SensitiveContentRedactor redactor = new SensitiveContentRedactor();
        ProjectAgentRevalidationService service = new ProjectAgentRevalidationService(
            projects, memories, mock(ProjectFactRepository.class), history, new LocalProjectPathGuard(),
            new LargeFileContentService(redactor), commands, redactor
        );

        var result = service.revalidate(userId, projectId, new AgentRevalidationRequest(
            "REREAD_RANGE", "", "source:spec", 2L, 3L, 2_000,
            "", List.of(), "CURRENT_SNAPSHOT", "STANDARD", 8_000
        ));

        assertThat(result.status()).isEqualTo("SUCCEEDED");
        assertThat(result.currentness()).isEqualTo("CURRENT");
        assertThat(result.range().locator()).isEqualTo("docs/spec.md");
        assertThat(result.range().text()).contains("retry policy", "status feedback")
            .doesNotContain(projectRoot.toString());
        assertThat(result.range().startLine()).isEqualTo(2);
        assertThat(result.range().endLine()).isEqualTo(3);
    }

    @Test
    void currentnessCheckExposesRevisionMismatchWithoutChangingTheFact() {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID factId = UUID.randomUUID();
        ProjectFactRepository facts = mock(ProjectFactRepository.class);
        ProjectFact fact = mock(ProjectFact.class);
        when(fact.getId()).thenReturn(factId);
        when(fact.getRevision()).thenReturn("aaaaaaaaaaaaaaa");
        when(facts.findByIdAndProjectId(factId, projectId)).thenReturn(Optional.of(fact));
        LocalCommandExecutor commands = (directory, command, timeout) ->
            new LocalCommandExecutor.CommandResult(0, "bbbbbbbbbbbbbbb\n", false);
        SensitiveContentRedactor redactor = new SensitiveContentRedactor();
        ProjectAgentRevalidationService service = new ProjectAgentRevalidationService(
            owned(userId, projectId), memory(projectId), facts, mock(ProjectAgentHistoryService.class),
            new LocalProjectPathGuard(), new LargeFileContentService(redactor), commands, redactor
        );

        var result = service.revalidate(userId, projectId, new AgentRevalidationRequest(
            "VALIDATE_CURRENTNESS", "fact:" + factId, "", null, null, null,
            "", List.of(), "CURRENT_SNAPSHOT", "STANDARD", 8_000
        ));

        assertThat(result.currentness()).isEqualTo("POSSIBLY_STALE");
        assertThat(result.validationStatus()).isEqualTo("REVISION_MISMATCH");
    }

    @Test
    void revalidationRejectsCrossProjectAccessBeforeReadingFiles() {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        SensitiveContentRedactor redactor = new SensitiveContentRedactor();
        ProjectAgentRevalidationService service = new ProjectAgentRevalidationService(
            mock(ProjectRepository.class), mock(ProjectMemoryRepository.class), mock(ProjectFactRepository.class),
            mock(ProjectAgentHistoryService.class), new LocalProjectPathGuard(),
            new LargeFileContentService(redactor), mock(LocalCommandExecutor.class), redactor
        );

        assertThatThrownBy(() -> service.revalidate(userId, projectId, new AgentRevalidationRequest(
            "VALIDATE_CURRENTNESS", "fact:" + UUID.randomUUID(), "", null, null, null,
            "", List.of(), "CURRENT_SNAPSHOT", "STANDARD", 8_000
        ))).isInstanceOf(AppException.class);
    }

    private ProjectRepository owned(UUID userId, UUID projectId) {
        ProjectRepository projects = mock(ProjectRepository.class);
        when(projects.findByIdAndUserId(projectId, userId)).thenReturn(Optional.of(mock(ProjectSpace.class)));
        return projects;
    }

    private ProjectMemoryRepository memory(UUID projectId) {
        ProjectMemoryRepository memories = mock(ProjectMemoryRepository.class);
        ProjectMemory memory = mock(ProjectMemory.class);
        when(memory.getLocalProjectPath()).thenReturn(projectRoot.toString());
        when(memories.findByProjectId(projectId)).thenReturn(Optional.of(memory));
        return memories;
    }
}
