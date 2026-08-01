package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.projectflow.dto.ProjectAgentCandidateDtos.AgentCandidateInput;
import com.projectflow.dto.ProjectAgentCandidateDtos.SubmitAgentWorkResultRequest;
import com.projectflow.dto.ProjectUnderstandingDtos.ProjectUnderstandingSnapshotResponse;
import com.projectflow.entity.ProjectAgentCandidate;
import com.projectflow.entity.ProjectMemory;
import com.projectflow.entity.ProjectSpace;
import com.projectflow.repository.ProjectAgentCandidateRepository;
import com.projectflow.repository.ProjectFactRepository;
import com.projectflow.repository.ProjectMemoryRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.support.AppException;

class AgentWorkResultWriteTest {
    @TempDir Path projectRoot;

    @Test
    void workResultRereadsChangedFilesButKeepsAgentClaimsAsCandidates() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Files.createDirectories(projectRoot.resolve("src"));
        Files.writeString(projectRoot.resolve("src/MailService.java"), "class MailService { void retry() {} }");

        ProjectRepository projects = mock(ProjectRepository.class);
        ProjectMemoryRepository memories = mock(ProjectMemoryRepository.class);
        ProjectAgentCandidateRepository candidates = mock(ProjectAgentCandidateRepository.class);
        ProjectUnderstandingService understanding = mock(ProjectUnderstandingService.class);
        ProjectMemory memory = mock(ProjectMemory.class);
        ProjectUnderstandingSnapshotResponse snapshot = mock(ProjectUnderstandingSnapshotResponse.class);
        when(projects.findByIdAndUserId(projectId, userId)).thenReturn(Optional.of(mock(ProjectSpace.class)));
        when(memories.findByProjectId(projectId)).thenReturn(Optional.of(memory));
        when(memory.getLocalProjectPath()).thenReturn(projectRoot.toString());
        when(understanding.get(userId, projectId)).thenReturn(snapshot);
        when(snapshot.sourceRevision()).thenReturn("revision-1");
        when(candidates.save(any(ProjectAgentCandidate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SensitiveContentRedactor redactor = new SensitiveContentRedactor();
        ProjectAgentCandidateService service = new ProjectAgentCandidateService(
            projects, mock(ProjectFactRepository.class), candidates, memories, understanding, redactor,
            new LocalProjectPathGuard(), new LargeFileContentService(redactor), mock(LocalCommandExecutor.class)
        );
        SubmitAgentWorkResultRequest request = new SubmitAgentWorkResultRequest(
            List.of("src/MailService.java"),
            List.of("增加失败重试"),
            List.of("mvn test"),
            List.of("Agent 声称测试通过"),
            List.of(), List.of(), List.of(), List.of(), List.of(),
            List.of(new AgentCandidateInput(
                "ASSERTION", "重试能力已经实现", "INFERRED", List.of(), List.of()
            )),
            List.of(), List.of(), "revision-1", "test-agent"
        );

        var result = service.submitWorkResult(userId, projectId, request, "test-agent");

        assertThat(result.validationStatus()).isEqualTo("SOURCE_IDENTITY_REVALIDATED");
        assertThat(result.rereadEvidenceRefs()).singleElement()
            .asString().startsWith("file:src/MailService.java#sha256=");
        assertThat(result.candidates()).extracting(item -> item.epistemicStatus())
            .containsExactly("PROCESS_EVIDENCE", "INFERRED");
        assertThat(result.candidates()).allSatisfy(item -> assertThat(item.epistemicStatus())
            .isNotIn("OBSERVED", "VERIFIED"));
        assertThat(result.candidates().get(0).assertion()).doesNotContain(projectRoot.toString());
    }

    @Test
    void batchBoundaryRejectsDirectStrongFactBeforeAnyCandidateIsSaved() {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        ProjectRepository projects = mock(ProjectRepository.class);
        ProjectAgentCandidateRepository candidates = mock(ProjectAgentCandidateRepository.class);
        when(projects.findByIdAndUserId(projectId, userId)).thenReturn(Optional.of(mock(ProjectSpace.class)));
        SensitiveContentRedactor redactor = new SensitiveContentRedactor();
        ProjectAgentCandidateService service = new ProjectAgentCandidateService(
            projects, mock(ProjectFactRepository.class), candidates, mock(ProjectMemoryRepository.class),
            mock(ProjectUnderstandingService.class), redactor, new LocalProjectPathGuard(),
            new LargeFileContentService(redactor), mock(LocalCommandExecutor.class)
        );
        SubmitAgentWorkResultRequest request = new SubmitAgentWorkResultRequest(
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
            List.of(new AgentCandidateInput(
                "ASSERTION", "未经独立验证的完成声明", "VERIFIED", List.of(), List.of()
            )),
            List.of(), List.of(), "", "test-agent"
        );

        assertThatThrownBy(() -> service.submitWorkResult(userId, projectId, request, "test-agent"))
            .isInstanceOf(AppException.class);
        verify(candidates, never()).save(any());
    }
}
