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
        return new EvidenceSourceMapResponse(1, 0, 0, 0, 1, Map.of(), List.of(), List.of());
    }
}
