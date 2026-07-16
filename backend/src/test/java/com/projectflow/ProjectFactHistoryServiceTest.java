package com.projectflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.projectflow.dto.V33WorkflowDtos.ChangeBatchResponse;
import com.projectflow.entity.EvidenceConfidence;
import com.projectflow.entity.ProjectFact;
import com.projectflow.entity.ProjectFactCommitRef;
import com.projectflow.entity.ProjectFactHistoryStatus;
import com.projectflow.entity.ProjectFactOrigin;
import com.projectflow.entity.ProjectFactRecordStatus;
import com.projectflow.entity.ProjectMemory;
import com.projectflow.entity.ProjectSpace;
import com.projectflow.entity.ProjectStatus;
import com.projectflow.repository.ProjectFactCommitRefRepository;
import com.projectflow.repository.ProjectFactCursorRepository;
import com.projectflow.repository.ProjectFactHistoryStateRepository;
import com.projectflow.repository.ProjectFactRepository;
import com.projectflow.repository.ProjectMemoryRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.service.ProjectFactHistoryService;
import com.projectflow.service.WorkSessionScanService;
import com.projectflow.support.AppException;

@SpringBootTest
@ActiveProfiles("test")
class ProjectFactHistoryServiceTest {
    @Autowired ProjectRepository projectRepository;
    @Autowired ProjectMemoryRepository memoryRepository;
    @Autowired ProjectFactRepository factRepository;
    @Autowired ProjectFactCommitRefRepository commitRefRepository;
    @Autowired ProjectFactHistoryStateRepository historyStateRepository;
    @Autowired ProjectFactCursorRepository factCursorRepository;
    @Autowired ProjectFactHistoryService historyService;

    @MockitoBean WorkSessionScanService workSessionScanService;

    @Test
    void processesOneHundredFiveCommitsOldestFirstInBoundedRestartSafeChunks() throws Exception {
        Path repository = createGitHistory(105);
        try {
            UUID userId = UUID.randomUUID();
            ProjectSpace project = new ProjectSpace(userId);
            project.update("105 commit history", "bounded history test", ProjectStatus.BUILDING, List.of("Git"), "", LocalDate.now(), null);
            project = projectRepository.saveAndFlush(project);
            ProjectMemory memory = new ProjectMemory(project.getId());
            memory.update("History", "Backfill", "", "", "", "", "", "", "");
            memory.rememberLocalProjectPath(repository.toAbsolutePath().normalize().toString());
            memoryRepository.saveAndFlush(memory);

            List<String> all = git(repository, "rev-list", "--reverse", "HEAD").lines().filter(line -> !line.isBlank()).toList();
            List<String> alreadyCovered = all.subList(100, 105);
            persistCoveredFact(project.getId(), alreadyCovered, "covered-recent");

            AtomicInteger calls = new AtomicInteger();
            List<List<String>> processedChunks = new ArrayList<>();
            UUID projectId = project.getId();
            when(workSessionScanService.scanHistoryChunk(eq(userId), eq(projectId), any(), anyList()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    List<String> commits = List.copyOf((List<String>) invocation.getArgument(3));
                    processedChunks.add(commits);
                    int call = calls.incrementAndGet();
                    persistCoveredFact(projectId, commits, "history-chunk-" + call);
                    UUID batchId = UUID.randomUUID();
                    ChangeBatchResponse batch = batch(projectId, batchId, commits);
                    return new WorkSessionScanService.HistoryChunkResult(batch, List.of(), false);
                });

            String head = all.get(all.size() - 1);
            ProjectFactHistoryService.HistoryStepResult result;
            do {
                result = historyService.processNextChunk(userId, projectId, null, head);
            } while (result.hasMore());

            assertThat(processedChunks).hasSize(4).allSatisfy(chunk -> assertThat(chunk).hasSize(25));
            assertThat(processedChunks.stream().flatMap(List::stream).toList()).containsExactlyElementsOf(all.subList(0, 100));
            assertThat(processedChunks.stream().flatMap(List::stream).toList()).doesNotContainAnyElementsOf(alreadyCovered);
            assertThat(result.totalCommitCount()).isEqualTo(105);
            assertThat(result.coveredCommitCount()).isEqualTo(105);
            var state = historyStateRepository.findByProjectId(projectId).orElseThrow();
            assertThat(state.getStatus()).isEqualTo(ProjectFactHistoryStatus.COMPLETED);
            assertThat(state.getCompletedChunkCount()).isEqualTo(4);
            assertThat(state.getRemainingCommitCount()).isZero();
            assertThat(factCursorRepository.findByProjectId(projectId)).isEmpty();

            long factsBeforeRestart = factRepository.countByProjectId(projectId);
            var afterRestart = historyService.processNextChunk(userId, projectId, null, head);
            assertThat(afterRestart.processedCommitCount()).isZero();
            assertThat(calls.get()).isEqualTo(4);
            assertThat(factRepository.countByProjectId(projectId)).isEqualTo(factsBeforeRestart);
            assertThat(commitRefRepository.countDistinctCommitShaByProjectId(projectId)).isEqualTo(105);
        } finally {
            Files.walk(repository).sorted(java.util.Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        }
    }

    @Test
    void checkpointsAHistoryCommitWithoutAnalyzableChangesInsteadOfPausingForever() throws Exception {
        Path repository = createGitHistory(1);
        try {
            UUID userId = UUID.randomUUID();
            ProjectSpace project = new ProjectSpace(userId);
            project.update("No-change history", "history boundary test", ProjectStatus.BUILDING, List.of("Git"), "", LocalDate.now(), null);
            project = projectRepository.saveAndFlush(project);
            ProjectMemory memory = new ProjectMemory(project.getId());
            memory.update("History", "Backfill", "", "", "", "", "", "", "");
            memory.rememberLocalProjectPath(repository.toAbsolutePath().normalize().toString());
            memoryRepository.saveAndFlush(memory);

            UUID projectId = project.getId();
            when(workSessionScanService.scanHistoryChunk(eq(userId), eq(projectId), any(), anyList()))
                .thenThrow(new AppException(
                    "HISTORY_NO_ANALYZABLE_CHANGES",
                    "该历史提交批次没有可记录的代码变化，已作为历史边界跳过。",
                    HttpStatus.UNPROCESSABLE_ENTITY
                ));

            String head = git(repository, "rev-parse", "HEAD").trim();
            var result = historyService.processNextChunk(userId, projectId, null, head);

            assertThat(result.batchId()).isNull();
            assertThat(result.processedCommitCount()).isEqualTo(1);
            assertThat(result.coveredCommitCount()).isEqualTo(1);
            assertThat(result.hasMore()).isFalse();
            assertThat(result.diagnosticsJson()).contains("skippedNoAnalyzableChanges");
            var state = historyStateRepository.findByProjectId(projectId).orElseThrow();
            assertThat(state.getStatus()).isEqualTo(ProjectFactHistoryStatus.COMPLETED);
            assertThat(state.getLastProcessedCommitSha()).isEqualTo(head);
            assertThat(state.getRemainingCommitCount()).isZero();
            assertThat(factRepository.countByProjectId(projectId)).isZero();
        } finally {
            Files.walk(repository).sorted(java.util.Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        }
    }

    private void persistCoveredFact(UUID projectId, List<String> commits, String label) {
        String fingerprint = String.format("%064x", Math.abs(label.hashCode()) + 1L);
        ProjectFact fact = new ProjectFact(projectId, null, null, ProjectFactOrigin.HISTORY_BACKFILL, fingerprint);
        fact.updateContent(
            label, "历史提交已形成项目事实", List.of("按有界批次处理历史提交"), "项目早期事实可读取",
            Instant.parse("2025-01-01T00:00:00Z"), Instant.parse("2025-01-01T00:00:00Z"),
            commits, List.of(), List.of(), List.of("src/history.txt"),
            commits.stream().map(commit -> "commit:" + commit).toList(), "MODEL", "PASS",
            EvidenceConfidence.HIGH, ProjectFactRecordStatus.RECORDED, ""
        );
        fact = factRepository.saveAndFlush(fact);
        List<ProjectFactCommitRef> refs = new ArrayList<>();
        for (String commit : commits) refs.add(new ProjectFactCommitRef(projectId, fact.getId(), commit));
        commitRefRepository.saveAllAndFlush(refs);
    }

    private ChangeBatchResponse batch(UUID projectId, UUID batchId, List<String> commits) {
        Instant occurred = Instant.parse("2025-01-01T00:00:00Z");
        return new ChangeBatchResponse(
            batchId, projectId, occurred, occurred, commits.get(0), commits.get(commits.size() - 1), "master",
            commits.size(), 1, 0, 1, 1, 0, occurred, occurred, "HISTORY_BACKFILL", "FACTS_RECORDED",
            List.of(), false, "history-test", false, "NOT_USED", "unknown", "MODEL", "SUCCESS",
            "Fixed model", "", 1, 1, 0, 2, "{}"
        );
    }

    private Path createGitHistory(int count) throws Exception {
        Path root = Files.createTempDirectory("projectflow-history-105-");
        git(root, "init", "-b", "master");
        git(root, "config", "user.email", "history@example.com");
        git(root, "config", "user.name", "ProjectFlow History Test");
        git(root, "config", "gc.auto", "0");
        git(root, "config", "gc.autoDetach", "false");
        git(root, "config", "maintenance.auto", "false");
        Path file = root.resolve("src/history.txt");
        Files.createDirectories(file.getParent());
        for (int index = 0; index < count; index += 1) {
            Files.writeString(file, "commit " + index + System.lineSeparator(), StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            git(root, "add", ".");
            git(root, "commit", "-m", "history " + index);
        }
        return root;
    }

    private String git(Path root, String... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.waitFor() != 0) throw new AssertionError(String.join(" ", command) + " failed: " + output);
        return output;
    }
}
