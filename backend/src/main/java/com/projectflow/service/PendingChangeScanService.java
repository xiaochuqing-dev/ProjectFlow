package com.projectflow.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projectflow.dto.V33WorkflowDtos.ChangeBatchResponse;
import com.projectflow.dto.V33WorkflowDtos.DevelopmentSegmentResponse;
import com.projectflow.entity.ChangeBatch;
import com.projectflow.entity.DevelopmentSegment;
import com.projectflow.entity.ProjectReviewCursor;
import com.projectflow.repository.ChangeBatchRepository;
import com.projectflow.repository.DevelopmentSegmentRepository;
import com.projectflow.repository.ProjectReviewCursorRepository;

@Service
public class PendingChangeScanService {
    private static final int FIRST_SCAN_COMMIT_LIMIT = 30;
    private static final int FALLBACK_COMMIT_LIMIT = 200;
    private static final long COMMAND_TIMEOUT_SECONDS = 15;

    private final ProjectReviewCursorRepository cursorRepository;
    private final ChangeBatchRepository batchRepository;
    private final DevelopmentSegmentRepository segmentRepository;

    public PendingChangeScanService(
        ProjectReviewCursorRepository cursorRepository,
        ChangeBatchRepository batchRepository,
        DevelopmentSegmentRepository segmentRepository
    ) {
        this.cursorRepository = cursorRepository;
        this.batchRepository = batchRepository;
        this.segmentRepository = segmentRepository;
    }

    public ScanPlan prepare(Path projectRoot, UUID projectId, String branchName) {
        String head = command(projectRoot, List.of("git", "rev-parse", "HEAD")).output().trim();
        ProjectReviewCursor cursor = cursorRepository.findByProjectId(projectId).orElse(null);
        List<String> warnings = new ArrayList<>();
        if (cursor == null || cursor.getLastReviewedCommitSha() == null || cursor.getLastReviewedCommitSha().isBlank()) {
            warnings.add("这是首次扫描，ProjectFlow 先整理最近 30 个提交。");
            return new ScanPlan(
                "",
                head,
                branchName,
                true,
                List.of("log", "--max-count=" + FIRST_SCAN_COMMIT_LIMIT, "--numstat", "--pretty=format:__PF_COMMIT__%x09%H%x09%an%x09%aI%x09%s"),
                warnings
            );
        }

        String base = cursor.getLastReviewedCommitSha();
        CommandResult ancestor = command(projectRoot, List.of("git", "merge-base", "--is-ancestor", base, "HEAD"));
        if (ancestor.exitCode() == 0) {
            return new ScanPlan(
                base,
                head,
                branchName,
                false,
                List.of("log", base + ".." + head, "--numstat", "--pretty=format:__PF_COMMIT__%x09%H%x09%an%x09%aI%x09%s"),
                warnings
            );
        }

        warnings.add("检测到提交历史变化，本次将按最近未整理时间重新扫描。");
        Instant since = cursor.getLastReviewedAt() == null ? Instant.now().minusSeconds(7 * 86_400L) : cursor.getLastReviewedAt();
        return new ScanPlan(
            base,
            head,
            branchName,
            false,
            List.of("log", "--since=" + since, "--max-count=" + FALLBACK_COMMIT_LIMIT, "--numstat", "--pretty=format:__PF_COMMIT__%x09%H%x09%an%x09%aI%x09%s"),
            warnings
        );
    }

    @Transactional
    public ChangeBatchResponse persist(UUID projectId, ScanPlan plan, int commitCount, int changedFileCount, int agentResultCount, List<String> allWarnings) {
        ChangeBatch batch = batchRepository
            .findFirstByProjectIdAndBranchNameAndBaseCommitShaAndHeadCommitShaOrderByScanStartedAtDesc(
                projectId, plan.branchName(), plan.baseCommitSha(), plan.headCommitSha()
            )
            .orElseGet(() -> new ChangeBatch(projectId, plan.baseCommitSha(), plan.headCommitSha(), plan.branchName(), plan.firstScan()));
        batch.complete(commitCount, changedFileCount, agentResultCount, allWarnings);
        return toResponse(batchRepository.save(batch));
    }

    @Transactional(readOnly = true)
    public List<DevelopmentSegmentResponse> listSegments(UUID batchId) {
        return segmentRepository.findByBatchIdOrderByCreatedAtAsc(batchId).stream().map(this::toResponse).toList();
    }

    private CommandResult command(Path projectRoot, List<String> command) {
        try {
            Process process = new ProcessBuilder(command)
                .directory(projectRoot.toFile())
                .redirectErrorStream(true)
                .start();
            boolean finished = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new CommandResult(-1, "");
            }
            return new CommandResult(process.exitValue(), new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException exception) {
            return new CommandResult(-1, "");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new CommandResult(-1, "");
        }
    }

    private ChangeBatchResponse toResponse(ChangeBatch batch) {
        return new ChangeBatchResponse(
            batch.getId(), batch.getProjectId(), batch.getScanStartedAt(), batch.getScanFinishedAt(),
            batch.getBaseCommitSha(), batch.getHeadCommitSha(), batch.getBranchName(), batch.getNewCommitCount(),
            batch.getChangedFileCount(), batch.getAgentResultCount(), batch.getSegmentCount(), batch.getStatus().name(),
            batch.getWarnings(), batch.isFirstScan()
        );
    }

    private DevelopmentSegmentResponse toResponse(DevelopmentSegment segment) {
        return new DevelopmentSegmentResponse(
            segment.getId(), segment.getProjectId(), segment.getBatchId(), segment.getTitle(), segment.getPlainSummary(),
            segment.getMainChanges(), segment.getUserVisibleValue(), segment.getIncludedCommitRefs(),
            segment.getIncludedAgentResultRefs(), segment.getAffectedFiles(), segment.getEvidenceRefs(),
            segment.getConfidence().name(), segment.getStatus().name(), segment.getCreatedAt(), segment.getUpdatedAt()
        );
    }

    public record ScanPlan(
        String baseCommitSha,
        String headCommitSha,
        String branchName,
        boolean firstScan,
        List<String> gitLogArguments,
        List<String> warnings
    ) {
    }

    private record CommandResult(int exitCode, String output) {
    }
}
