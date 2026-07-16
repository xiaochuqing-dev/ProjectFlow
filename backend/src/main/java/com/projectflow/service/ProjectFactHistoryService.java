package com.projectflow.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.projectflow.entity.ProjectFactHistoryState;
import com.projectflow.entity.ProjectFactHistoryStatus;
import com.projectflow.entity.ProjectMemory;
import com.projectflow.entity.ProjectSpace;
import com.projectflow.repository.ProjectFactCommitRefRepository;
import com.projectflow.repository.ProjectFactHistoryStateRepository;
import com.projectflow.repository.ProjectMemoryRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.support.AppException;

@Service
public class ProjectFactHistoryService {
    static final int HISTORY_CHUNK_COMMIT_LIMIT = 25;
    private static final long GIT_TIMEOUT_SECONDS = 30;

    private final ProjectRepository projectRepository;
    private final ProjectMemoryRepository memoryRepository;
    private final LocalProjectPathGuard pathGuard;
    private final ProjectFactCommitRefRepository commitRefRepository;
    private final ProjectFactHistoryStateRepository stateRepository;
    private final WorkSessionScanService workSessionScanService;
    private final TransactionTemplate transactionTemplate;

    public ProjectFactHistoryService(
        ProjectRepository projectRepository,
        ProjectMemoryRepository memoryRepository,
        LocalProjectPathGuard pathGuard,
        ProjectFactCommitRefRepository commitRefRepository,
        ProjectFactHistoryStateRepository stateRepository,
        WorkSessionScanService workSessionScanService,
        PlatformTransactionManager transactionManager
    ) {
        this.projectRepository = projectRepository;
        this.memoryRepository = memoryRepository;
        this.pathGuard = pathGuard;
        this.commitRefRepository = commitRefRepository;
        this.stateRepository = stateRepository;
        this.workSessionScanService = workSessionScanService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /** Registers a cheap request; it never scans Git or invokes a model. */
    public boolean registerRequest(UUID projectId, String upperBoundSha, boolean modelConfigured) {
        Boolean shouldStart = transactionTemplate.execute(status -> {
            ProjectFactHistoryState state = stateRepository.findLockedByProjectId(projectId)
                .orElseGet(() -> new ProjectFactHistoryState(projectId));
            boolean alreadyComplete = state.getStatus() == ProjectFactHistoryStatus.COMPLETED
                && !state.getHeadSnapshotSha().isBlank()
                && state.getHeadSnapshotSha().equals(upperBoundSha);
            if (!modelConfigured) {
                state.markWaitingForModel(upperBoundSha);
                stateRepository.save(state);
                return false;
            }
            if (alreadyComplete) return false;
            if (state.getStatus() != ProjectFactHistoryStatus.RUNNING) {
                state.initialize(
                    upperBoundSha, state.getTotalCommitCount(), state.getCoveredCommitCount(), true
                );
                stateRepository.save(state);
            }
            return true;
        });
        return Boolean.TRUE.equals(shouldStart);
    }

    /** Executes exactly one bounded history chunk outside a long database transaction. */
    public HistoryStepResult processNextChunk(UUID userId, UUID projectId, UUID jobId, String requestedUpperBound) {
        ProjectSpace project = projectRepository.findByIdAndUserId(projectId, userId)
            .orElseThrow(() -> new AppException("PROJECT_NOT_FOUND", "项目不存在", HttpStatus.NOT_FOUND));
        ProjectMemory memory = memoryRepository.findByProjectId(project.getId())
            .orElseThrow(() -> new AppException("PROJECT_PATH_REQUIRED", "请先绑定本地 Git 项目", HttpStatus.BAD_REQUEST));
        Path root = pathGuard.requireGitProjectDirectory(memory.getLocalProjectPath()).path();
        String upperBound = requestedUpperBound == null || requestedUpperBound.isBlank()
            ? git(root, "rev-parse", "HEAD").trim()
            : requestedUpperBound.trim();
        List<String> allCommits = gitLines(root, "rev-list", "--reverse", upperBound);
        if (allCommits.isEmpty()) {
            throw new AppException("HISTORY_GIT_EMPTY", "无法读取项目 Git 历史", HttpStatus.BAD_REQUEST);
        }

        try {
            StateSnapshot before = stateSnapshot(projectId, upperBound);
            CoveragePlan plan = plan(allCommits, before.lastProcessedCommitSha(), coveredCommits(projectId));
            if (plan.uncovered().isEmpty()) {
                complete(projectId, upperBound, allCommits.size(), allCommits.size());
                return new HistoryStepResult(upperBound, null, 0, allCommits.size(), allCommits.size(), false, "");
            }

            List<String> chunk = plan.uncovered().stream().limit(HISTORY_CHUNK_COMMIT_LIMIT).toList();
            markRunning(projectId, upperBound, allCommits.size(), plan.coveredCount());
            WorkSessionScanService.HistoryChunkResult scan = null;
            boolean skippedNoChanges = false;
            try {
                scan = workSessionScanService.scanHistoryChunk(userId, projectId, jobId, chunk);
            } catch (AppException exception) {
                if (!"HISTORY_NO_ANALYZABLE_CHANGES".equals(exception.getCode())) throw exception;
                skippedNoChanges = true;
            }

            Set<String> coveredAfter = coveredCommits(projectId);
            CoveragePlan after = plan(allCommits, chunk.get(chunk.size() - 1), coveredAfter);
            int coveredCount = allCommits.size() - after.uncovered().size();
            UUID batchId = scan == null ? null : scan.batch().id();
            recordChunk(
                projectId, upperBound, chunk.get(chunk.size() - 1), batchId, allCommits.size(), coveredCount
            );
            return new HistoryStepResult(
                upperBound, batchId, chunk.size(), allCommits.size(), coveredCount,
                !after.uncovered().isEmpty(), skippedNoChanges
                    ? "{\"skippedNoAnalyzableChanges\":true}"
                    : scan.batch().analysisScope()
            );
        } catch (CancellationException exception) {
            pause(projectId, "CANCELLED", "历史事实补齐已暂停，已完成的事实仍然保留。");
            throw exception;
        } catch (RuntimeException exception) {
            pause(projectId, "CHUNK_FAILED", safeMessage(exception));
            throw exception;
        }
    }

    public void pauseAfterInterruption(UUID projectId, String code, String summary) {
        pause(projectId, code, summary);
    }

    private StateSnapshot stateSnapshot(UUID projectId, String upperBound) {
        return transactionTemplate.execute(status -> {
            ProjectFactHistoryState state = stateRepository.findLockedByProjectId(projectId)
                .orElseGet(() -> new ProjectFactHistoryState(projectId));
            if (state.getHeadSnapshotSha().isBlank()) {
                state.initialize(upperBound, 0, 0, true);
                stateRepository.save(state);
            }
            return new StateSnapshot(state.getLastProcessedCommitSha());
        });
    }

    private void markRunning(UUID projectId, String upperBound, int total, int covered) {
        transactionTemplate.executeWithoutResult(status -> {
            ProjectFactHistoryState state = stateRepository.findLockedByProjectId(projectId)
                .orElseGet(() -> new ProjectFactHistoryState(projectId));
            if (state.getHeadSnapshotSha().isBlank() || !state.getHeadSnapshotSha().equals(upperBound)) {
                state.initialize(upperBound, total, covered, true);
            }
            state.markRunning(total, covered);
            stateRepository.save(state);
        });
    }

    private void recordChunk(
        UUID projectId,
        String upperBound,
        String lastCommit,
        UUID batchId,
        int total,
        int covered
    ) {
        transactionTemplate.executeWithoutResult(status -> {
            ProjectFactHistoryState state = stateRepository.findLockedByProjectId(projectId)
                .orElseGet(() -> new ProjectFactHistoryState(projectId));
            if (state.getHeadSnapshotSha().isBlank()) state.initialize(upperBound, total, covered, true);
            state.recordChunk(lastCommit, batchId, total, covered);
            stateRepository.save(state);
        });
    }

    private void complete(UUID projectId, String upperBound, int total, int covered) {
        transactionTemplate.executeWithoutResult(status -> {
            ProjectFactHistoryState state = stateRepository.findLockedByProjectId(projectId)
                .orElseGet(() -> new ProjectFactHistoryState(projectId));
            if (state.getHeadSnapshotSha().isBlank()) state.initialize(upperBound, total, covered, true);
            state.markCompleted(total, covered);
            stateRepository.save(state);
        });
    }

    private void pause(UUID projectId, String code, String summary) {
        transactionTemplate.executeWithoutResult(status -> stateRepository.findLockedByProjectId(projectId).ifPresent(state -> {
            state.markPaused(code, summary);
            stateRepository.save(state);
        }));
    }

    private CoveragePlan plan(List<String> all, String checkpoint, Set<String> factCovered) {
        int checkpointIndex = checkpoint == null || checkpoint.isBlank() ? -1 : all.indexOf(checkpoint);
        List<String> uncovered = new ArrayList<>();
        for (int index = 0; index < all.size(); index++) {
            String commit = all.get(index);
            if (index <= checkpointIndex || factCovered.contains(commit.toLowerCase())) continue;
            uncovered.add(commit);
        }
        return new CoveragePlan(uncovered, all.size() - uncovered.size());
    }

    private Set<String> coveredCommits(UUID projectId) {
        Set<String> result = new HashSet<>();
        commitRefRepository.findDistinctCommitShasByProjectId(projectId).stream()
            .filter(java.util.Objects::nonNull)
            .map(String::toLowerCase)
            .forEach(result::add);
        return result;
    }

    private List<String> gitLines(Path root, String... arguments) {
        return git(root, arguments).lines().map(String::trim).filter(value -> !value.isBlank()).toList();
    }

    private String git(Path root, String... arguments) {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(arguments));
        try {
            Process process = new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true).start();
            CompletableFuture<byte[]> outputFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    return process.getInputStream().readAllBytes();
                } catch (IOException exception) {
                    throw new java.util.concurrent.CompletionException(exception);
                }
            });
            boolean finished = process.waitFor(GIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                outputFuture.cancel(true);
                throw new IllegalStateException("Git history command timed out");
            }
            String output = new String(outputFuture.join(), StandardCharsets.UTF_8);
            if (process.exitValue() != 0) throw new IllegalStateException(output.isBlank() ? "Git history command failed" : output.trim());
            return output;
        } catch (IOException exception) {
            throw new IllegalStateException("Git history command could not run", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CancellationException("Git history command was interrupted");
        }
    }

    private String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "历史事实批次失败，可从 checkpoint 重试。" : message;
    }

    public record HistoryStepResult(
        String upperBoundSha,
        UUID batchId,
        int processedCommitCount,
        int totalCommitCount,
        int coveredCommitCount,
        boolean hasMore,
        String diagnosticsJson
    ) {
    }

    private record StateSnapshot(String lastProcessedCommitSha) {
    }

    private record CoveragePlan(List<String> uncovered, int coveredCount) {
    }
}
