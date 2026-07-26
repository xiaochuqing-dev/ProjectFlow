package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.projectflow.dto.ProjectUnderstandingDtos.EvidenceSourceMapResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.GitEvidenceResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.RepositoryIntakeResponse;
import com.projectflow.repository.ProjectFactCommitRefRepository;

class HistoricalCoverageServiceTest {
    @TempDir
    Path root;

    @Test
    void shortGitHistoryStaysEarlyAndUsesRealFactCoverage() throws Exception {
        git("init");
        git("config", "user.email", "test@example.com");
        git("config", "user.name", "Test");
        for (int index = 1; index <= 3; index++) {
            Files.writeString(root.resolve("file.txt"), "version " + index);
            git("add", "file.txt");
            git("commit", "-m", "change " + index);
        }
        ProjectFactCommitRefRepository refs = mock(ProjectFactCommitRefRepository.class);
        UUID projectId = UUID.randomUUID();
        when(refs.countDistinctCommitShaByProjectId(projectId)).thenReturn(2L);
        HistoricalCoverageService service = new HistoricalCoverageService(new FixedCommandExecutor(), refs);

        var result = service.analyze(projectId, root, intake(true, 3), emptySourceMap());

        assertThat(result.coverage().availability()).isEqualTo("SHORT_GIT_HISTORY");
        assertThat(result.coverage().coveredCommitCount()).isEqualTo(2);
        assertThat(result.coverage().earliestEvidenceAt()).isNotNull();
        assertThat(result.evolutionPreview().mode()).isEqualTo("EARLY_PROJECT");
        assertThat(result.evolutionPreview().milestoneCandidateCount()).isEqualTo(3);
    }

    @Test
    void noGitDoesNotInventHistory() {
        HistoricalCoverageService service = new HistoricalCoverageService(
            new FixedCommandExecutor(),
            mock(ProjectFactCommitRefRepository.class)
        );

        var result = service.analyze(UUID.randomUUID(), root, intake(false, 0), emptySourceMap());

        assertThat(result.coverage().historyAvailable()).isFalse();
        assertThat(result.coverage().overallCoverage()).isZero();
        assertThat(result.evolutionPreview().mode()).isEqualTo("CURRENT_STATE_ONLY");
    }

    @Test
    void thousandCommitsWithoutFactsOrTagsReportsOnlyMetadataCoverage() {
        LocalCommandExecutor commands = (directory, command, timeout) -> {
            if (command.contains("--max-parents=0")) {
                return new LocalCommandExecutor.CommandResult(0, "2024-01-01T00:00:00Z", false);
            }
            if (command.contains("show")) {
                return new LocalCommandExecutor.CommandResult(0, "2026-07-01T00:00:00Z", false);
            }
            if (command.contains("tag")) {
                return new LocalCommandExecutor.CommandResult(0, "", false);
            }
            return new LocalCommandExecutor.CommandResult(0, "2026-07\n".repeat(1000), false);
        };
        ProjectFactCommitRefRepository refs = mock(ProjectFactCommitRefRepository.class);
        UUID projectId = UUID.randomUUID();
        when(refs.countDistinctCommitShaByProjectId(projectId)).thenReturn(0L);
        when(refs.countByTimelineMonths(projectId, List.of("2026-07"))).thenReturn(List.of());
        HistoricalCoverageService service = new HistoricalCoverageService(commands, refs);

        var result = service.analyze(projectId, root, intake(true, 1000), emptySourceMap());

        assertThat(result.coverage().overallCoverage()).isEqualTo(0.25);
        assertThat(result.coverage().breakdown().gitMetadataCoverage()).isEqualTo(1);
        assertThat(result.coverage().breakdown().factCoverage()).isZero();
        assertThat(result.coverage().breakdown().tagAnchorCoverage()).isZero();
        assertThat(result.coverage().breakdown().periods()).singleElement()
            .satisfies(period -> {
                assertThat(period.commitCount()).isEqualTo(1000);
                assertThat(period.confidence()).isEqualTo(0.25);
                assertThat(period.limitation()).contains("只有 Git 元数据");
            });
    }

    private void git(String... arguments) throws Exception {
        List<String> command = new java.util.ArrayList<>();
        command.add("git");
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true).start();
        assertThat(process.waitFor()).isZero();
    }

    private static RepositoryIntakeResponse intake(boolean git, long commits) {
        return new RepositoryIntakeResponse(
            git ? "SMALL" : "CODE_NO_GIT",
            "SMALL",
            true,
            1,
            1,
            10,
            1,
            Map.of("Text", 1L),
            List.of(),
            new GitEvidenceResponse(git, git ? "master" : "", git ? "head" : "", commits, "CLEAN", 0),
            0,
            false,
            0,
            0,
            1,
            false,
            "TEST",
            "revision",
            "hash",
            List.of()
        );
    }

    private static EvidenceSourceMapResponse emptySourceMap() {
        return new EvidenceSourceMapResponse(1, 0, 0, 0, 1, Map.of(), List.of(), List.of(), null);
    }
}
