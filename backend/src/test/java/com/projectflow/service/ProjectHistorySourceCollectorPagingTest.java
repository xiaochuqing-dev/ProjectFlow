package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.data.domain.Page;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.entity.ProjectHistoryEvent.Category;
import com.projectflow.entity.ProjectMemory;
import com.projectflow.entity.ProjectSpace;
import com.projectflow.repository.ProjectAgentCandidateRepository;
import com.projectflow.repository.ProjectFactRepository;
import com.projectflow.repository.ProjectHistoryEventRepository;
import com.projectflow.repository.ProjectMemoryRepository;
import com.projectflow.repository.ProjectRepository;

class ProjectHistorySourceCollectorPagingTest {
    @TempDir Path root;

    @Test
    void oversizedPageIsRereadWithoutDroppingFilesOrTheOldestCommit() throws Exception {
        List<String> commits = List.of(commit(3, 600, 95), commit(2, 600, 95), commit(1, 1, 10));
        assertThat(String.join("", commits).length()).isGreaterThan(100_000);
        List<Integer> requestedPages = new ArrayList<>();
        var result = collect(commits, requestedPages);
        assertThat(result.complete()).isTrue();
        assertThat(result.readGitCommits()).isEqualTo(3);
        assertThat(result.events()).filteredOn(event -> event.category() == Category.FILE_CHANGE).hasSize(1_201);
        assertThat(result.events()).filteredOn(event -> event.category() == Category.COMMIT).hasSize(3);
        assertThat(result.events()).anySatisfy(event -> assertThat(event.sourceRevision()).isEqualTo(sha(1)));
        assertThat(result.limitations()).anyMatch(value -> value.contains("缩小读取范围"));
        assertThat(requestedPages).containsExactly(3, 1, 1, 1);
    }

    @Test
    void singleCommitFileBudgetRemainsExplicitlyIncomplete() throws Exception {
        var result = collect(List.of(commit(1, 1_020, 10)), new ArrayList<>());
        assertThat(result.complete()).isFalse();
        assertThat(result.readGitCommits()).isEqualTo(1);
        assertThat(result.events()).filteredOn(event -> event.category() == Category.FILE_CHANGE).hasSize(1_000);
        assertThat(result.events()).filteredOn(event -> event.category() == Category.COMMIT)
            .allSatisfy(event -> assertThat(event.limitations()).anyMatch(value -> value.contains("单提交上限")));
    }

    @Test
    void oversizedSingleCommitKeepsTheCoverageGapAndNeverInventsAPartialPath() throws Exception {
        String oversized = commit(2, 800, 180);
        List<Integer> requestedPages = new ArrayList<>();
        var result = collect(List.of(oversized, commit(1, 1, 10)), requestedPages);
        assertThat(result.complete()).isFalse();
        assertThat(result.readGitCommits()).isEqualTo(2);
        assertThat(result.limitations()).anyMatch(value -> value.contains("输出上限"));
        assertThat(result.events()).filteredOn(event -> event.category() == Category.FILE_CHANGE)
            .allSatisfy(event -> assertThat(event.affectedPaths()).allMatch(path -> path.endsWith(".txt")));
        assertThat(result.events()).anySatisfy(event -> assertThat(event.sourceRevision()).isEqualTo(sha(1)));
        assertThat(requestedPages).containsExactly(2, 1, 1);
    }

    private ProjectHistorySourceCollector.CollectionOutcome collect(List<String> commits, List<Integer> pageRequests)
        throws Exception {
        Files.createDirectories(root.resolve(".git"));
        UUID userId = UUID.randomUUID(), projectId = UUID.randomUUID();
        ProjectSpace project = mock(ProjectSpace.class);
        when(project.getId()).thenReturn(projectId);
        when(project.getCreatedAt()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
        when(project.getUpdatedAt()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
        ProjectMemory memory = new ProjectMemory(projectId);
        memory.rememberLocalProjectPath(root.toString());
        ProjectRepository projects = mock(ProjectRepository.class);
        ProjectMemoryRepository memories = mock(ProjectMemoryRepository.class);
        ProjectFactRepository facts = mock(ProjectFactRepository.class);
        ProjectAgentCandidateRepository candidates = mock(ProjectAgentCandidateRepository.class);
        when(projects.findByIdAndUserId(projectId, userId)).thenReturn(Optional.of(project));
        when(memories.findByProjectId(projectId)).thenReturn(Optional.of(memory));
        when(facts.findByProjectIdOrderByOccurredFromAscCreatedAtAsc(any(), any())).thenReturn(Page.empty());
        when(candidates.findByProjectIdOrderByCreatedAtDesc(any(), any())).thenReturn(Page.empty());
        LocalCommandExecutor executor = (directory, command, timeout) -> {
            String output = "";
            if (command.contains("--verify")) output = sha(commits.size());
            else if (command.contains("--is-shallow-repository")) output = "false";
            else if (command.contains("--count")) output = Integer.toString(commits.size());
            else if (command.contains("log") && command.contains("--name-status")) {
                int count = argument(command, "--max-count="), skip = argument(command, "--skip=");
                pageRequests.add(count);
                output = String.join("", commits.subList(skip, Math.min(commits.size(), skip + count)));
            }
            return new LocalCommandExecutor.CommandResult(0, output.substring(0, Math.min(output.length(), 100_000)), false);
        };
        return new ProjectHistorySourceCollector(projects, memories, facts, candidates, new LocalProjectPathGuard(),
            executor, new SensitiveContentRedactor(), new ObjectMapper(), mock(ProjectHistoryEventRepository.class))
            .collect(userId, projectId);
    }

    private static int argument(List<String> command, String prefix) {
        return Integer.parseInt(command.stream().filter(value -> value.startsWith(prefix)).findFirst().orElseThrow()
            .substring(prefix.length()));
    }

    private static String sha(int number) { return String.format("%040x", number); }

    private static String commit(int number, int fileCount, int pathWidth) {
        StringBuilder result = new StringBuilder("__PF_COMMIT__\t" + sha(number) + "\t\t2026-01-0" + number
            + "T00:00:00Z\tFixture\tRecord review observations\t\n");
        for (int i = 0; i < fileCount; i++) result.append("A\tartifacts/").append(number).append('/').append(i)
            .append('-').append("r".repeat(pathWidth)).append(".txt\n");
        return result.toString();
    }
}
