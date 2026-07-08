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
import com.projectflow.entity.DevelopmentSegmentStatus;
import com.projectflow.entity.ProjectReviewCursor;
import com.projectflow.repository.ChangeBatchRepository;
import com.projectflow.repository.DevelopmentSegmentRepository;
import com.projectflow.repository.ProjectReviewCursorRepository;
import com.projectflow.service.DevelopmentSegmentationService.SegmentDraft;

@Service
public class PendingChangeScanService {
    private static final int FIRST_SCAN_COMMIT_LIMIT = 30;
    private static final int FALLBACK_COMMIT_LIMIT = 200;
    // V3.3.4 小阶段修复：range scan 安全阀。cursor 过期时 base..head 可能返回大量提交，
    // 加 max-count 防止上游产出过多 atom 导致模型 prompt 过大而调用失败。
    private static final int RANGE_SCAN_COMMIT_LIMIT = 120;
    private static final long COMMAND_TIMEOUT_SECONDS = 15;

    private final ProjectReviewCursorRepository cursorRepository;
    private final ChangeBatchRepository batchRepository;
    private final DevelopmentSegmentRepository segmentRepository;
    private final SegmentQualityGate qualityGate;

    public PendingChangeScanService(
        ProjectReviewCursorRepository cursorRepository,
        ChangeBatchRepository batchRepository,
        DevelopmentSegmentRepository segmentRepository,
        SegmentQualityGate qualityGate
    ) {
        this.cursorRepository = cursorRepository;
        this.batchRepository = batchRepository;
        this.segmentRepository = segmentRepository;
        this.qualityGate = qualityGate;
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
                List.of("log", base + ".." + head, "--max-count=" + RANGE_SCAN_COMMIT_LIMIT, "--numstat", "--pretty=format:__PF_COMMIT__%x09%H%x09%an%x09%aI%x09%s"),
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
    public ChangeBatchResponse persist(UUID projectId, ScanPlan plan, int commitCount, int changedFileCount, int agentResultCount, List<String> allWarnings, ScanDiagnostics diagnostics) {
        ChangeBatch batch = batchRepository
            .findFirstByProjectIdAndScanFingerprintOrderByScanStartedAtDesc(projectId, diagnostics.scanFingerprint())
            .orElseGet(() -> new ChangeBatch(projectId, plan.baseCommitSha(), plan.headCommitSha(), plan.branchName(), plan.firstScan()));
        batch.complete(commitCount, changedFileCount, agentResultCount, allWarnings);
        batch.updateDiagnostics(
            diagnostics.scanFingerprint(), diagnostics.worktreeDirty(), diagnostics.githubStatus(), diagnostics.remoteRelation(),
            diagnostics.segmentationMode(), diagnostics.modelStatus(), diagnostics.modelProvider(), diagnostics.fallbackReason(),
            diagnostics.gitScanMs(), diagnostics.modelSegmentMs(), diagnostics.githubInspectMs(), diagnostics.totalScanMs()
        );
        // V3.3.3: 记录分析口径（哪些来源参与、是否有未提交/未同步内容、证据缺口等）。
        if (diagnostics.analysisScope() != null && !diagnostics.analysisScope().isBlank()) {
            batch.recordAnalysisScope(diagnostics.analysisScope());
        }
        return toResponse(batchRepository.save(batch));
    }

    @Transactional(readOnly = true)
    public List<DevelopmentSegmentResponse> listSegments(UUID batchId) {
        return segmentRepository.findByBatchIdOrderByCreatedAtAsc(batchId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public List<DevelopmentSegmentResponse> persistSegments(UUID projectId, UUID batchId, List<SegmentDraft> drafts, SegmentDiagnostics diagnostics) {
        List<DevelopmentSegment> existing = segmentRepository.findByBatchIdOrderByCreatedAtAsc(batchId);
        if (!existing.isEmpty()) {
            return existing.stream().map(this::toResponse).toList();
        }
        List<DevelopmentSegment> segments = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        for (SegmentDraft draft : drafts) {
            var quality = qualityGate.evaluate(draft, titles);
            titles.add(draft.title());
            DevelopmentSegment segment = new DevelopmentSegment(projectId, batchId);
            // V3.3.3: PASS→PENDING（待确认），其余状态→NEEDS_REVIEW。保留模型结果，不丢弃。
            DevelopmentSegmentStatus segmentStatus = "PASS".equals(quality.status())
                ? DevelopmentSegmentStatus.PENDING
                : DevelopmentSegmentStatus.NEEDS_REVIEW;
            segment.updateContent(
                draft.title(),
                draft.plainSummary(),
                draft.mainChanges(),
                draft.userVisibleValue(),
                draft.includedAtomIds().stream().filter(id -> !id.startsWith("agent:")).toList(),
                draft.includedAtomIds().stream().filter(id -> id.startsWith("agent:")).map(id -> "agent-result:" + id.substring(6)).toList(),
                draft.affectedFiles(),
                draft.evidenceRefs(),
                draft.confidence(),
                segmentStatus
            );
            List<String> urls = draft.evidenceRefs().stream()
                .filter(ref -> ref.startsWith("commit:"))
                .map(ref -> diagnostics.commitUrlTemplate().isBlank() ? "" : diagnostics.commitUrlTemplate().replace("{sha}", ref.substring(7)))
                .filter(url -> !url.isBlank()).toList();
            List<String> uncertainties = new ArrayList<>();
            if (!diagnostics.fallbackReason().isBlank()) uncertainties.add(diagnostics.fallbackReason());
            if (!"CONNECTED".equals(diagnostics.githubStatus())) uncertainties.add("GitHub 远程证据未完整接入");
            // V3.3.3: 把质量理由作为不确定性提示，让用户知道为什么需要复核。
            if (!quality.reason().isBlank()) uncertainties.add(quality.reason());
            segment.updateAnalysis(diagnostics.generationMode(), diagnostics.modelProvider(), diagnostics.fallbackReason(), quality.status(), quality.reason(), urls, uncertainties);
            segments.add(segment);
        }
        return segmentRepository.saveAll(segments).stream().map(this::toResponse).toList();
    }

    @Transactional
    public ChangeBatchResponse updateSegmentCount(UUID batchId, int segmentCount) {
        ChangeBatch batch = batchRepository.findById(batchId).orElseThrow();
        batch.updateSegmentCount(segmentCount);
        return toResponse(batchRepository.save(batch));
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
            batch.getWarnings(), batch.isFirstScan(), batch.getScanFingerprint(), batch.isWorktreeDirty(),
            batch.getGithubStatus(), batch.getRemoteRelation(), batch.getSegmentationMode(), batch.getModelStatus(),
            batch.getModelProvider(), batch.getFallbackReason(), batch.getGitScanMs(), batch.getModelSegmentMs(),
            batch.getGithubInspectMs(), batch.getTotalScanMs(), batch.getAnalysisScope()
        );
    }

    private DevelopmentSegmentResponse toResponse(DevelopmentSegment segment) {
        return new DevelopmentSegmentResponse(
            segment.getId(), segment.getProjectId(), segment.getBatchId(), segment.getTitle(), segment.getPlainSummary(),
            segment.getMainChanges(), segment.getUserVisibleValue(), segment.getIncludedCommitRefs(),
            segment.getIncludedAgentResultRefs(), segment.getAffectedFiles(), segment.getEvidenceRefs(),
            segment.getConfidence().name(), segment.getStatus().name(), segment.getCreatedAt(), segment.getUpdatedAt(),
            segment.getGenerationMode(), segment.getModelProvider(), segment.getFallbackReason(), segment.getQualityStatus(),
            segment.getQualityReason(), segment.getCommitUrls(), segment.getUncertainties()
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

    @Transactional
    public ChangeBatchResponse finish(UUID batchId, int segmentCount, long totalScanMs) {
        ChangeBatch batch = batchRepository.findById(batchId).orElseThrow();
        batch.updateSegmentCount(segmentCount);
        batch.updateTotalScanMs(totalScanMs);
        return toResponse(batchRepository.save(batch));
    }

    @Transactional(readOnly = true)
    public ReusableScan findReusable(UUID projectId, String fingerprint) {
        return batchRepository.findFirstByProjectIdAndScanFingerprintOrderByScanStartedAtDesc(projectId, fingerprint)
            .map(batch -> new ReusableScan(toResponse(batch), listSegments(batch.getId())))
            .filter(value -> !value.segments().isEmpty())
            .orElse(null);
    }

    public record ScanDiagnostics(
        String scanFingerprint,
        boolean worktreeDirty,
        String githubStatus,
        String remoteRelation,
        String segmentationMode,
        String modelStatus,
        String modelProvider,
        String fallbackReason,
        long gitScanMs,
        long modelSegmentMs,
        long githubInspectMs,
        long totalScanMs,
        String analysisScope
    ) {
        // 兼容旧构造：无 analysisScope。
        public ScanDiagnostics(
            String scanFingerprint, boolean worktreeDirty, String githubStatus, String remoteRelation,
            String segmentationMode, String modelStatus, String modelProvider, String fallbackReason,
            long gitScanMs, long modelSegmentMs, long githubInspectMs, long totalScanMs
        ) {
            this(scanFingerprint, worktreeDirty, githubStatus, remoteRelation, segmentationMode, modelStatus, modelProvider, fallbackReason, gitScanMs, modelSegmentMs, githubInspectMs, totalScanMs, "");
        }
    }

    public record SegmentDiagnostics(
        String generationMode,
        String modelProvider,
        String fallbackReason,
        String githubStatus,
        String commitUrlTemplate
    ) {
    }

    public record ReusableScan(ChangeBatchResponse batch, List<DevelopmentSegmentResponse> segments) {
    }

    private record CommandResult(int exitCode, String output) {
    }
}
