package com.projectflow.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.http.HttpStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.projectflow.dto.V2ProjectDtos.WorkSessionCandidateResponse;
import com.projectflow.dto.V2ProjectDtos.WorkSessionPatchRequest;
import com.projectflow.dto.V2ProjectDtos.WorkSessionScanResponse;
import com.projectflow.dto.V2ProjectDtos.AgentSignatureFeedbackResponse;
import com.projectflow.entity.AgentSignatureFeedback;
import com.projectflow.entity.ProjectAnalysisJob;
import com.projectflow.entity.ProjectFactOrigin;
import com.projectflow.entity.ProjectMemory;
import com.projectflow.entity.ProjectSpace;
import com.projectflow.entity.WorkSession;
import com.projectflow.repository.AgentSignatureFeedbackRepository;
import com.projectflow.repository.ProjectAnalysisJobRepository;
import com.projectflow.repository.ProjectFactAgentResultRefRepository;
import com.projectflow.repository.ProjectMemoryRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.repository.WorkSessionRepository;
import com.projectflow.support.AppException;
import com.projectflow.dto.V33WorkflowDtos.ChangeBatchResponse;
import com.projectflow.dto.V33WorkflowDtos.DevelopmentSegmentResponse;
import com.projectflow.dto.V33WorkflowDtos.GitHubStatusResponse;
import com.projectflow.service.AnalysisInputSnapshot.AgentResultFacts;
import com.projectflow.service.AnalysisInputSnapshot.GitFacts;
import com.projectflow.service.AnalysisInputSnapshot.GitHubFacts;
import com.projectflow.service.AnalysisInputSnapshot.ScanScopeFacts;
import com.projectflow.service.AnalysisInputSnapshot.WorktreeFacts;
import com.projectflow.service.PendingChangeScanService.ScanPlan;
import com.projectflow.service.PendingChangeScanService.ScanDiagnostics;
import com.projectflow.service.PendingChangeScanService.SegmentDiagnostics;
import com.projectflow.service.DevelopmentSegmentationService.ChangeAtom;
import com.projectflow.service.DevelopmentSegmentationService.SegmentDraft;

@Service
public class WorkSessionScanService {
    private final ProjectRepository projectRepository;
    private final ProjectMemoryRepository memoryRepository;
    private final WorkSessionRepository workSessionRepository;
    private final AgentSignatureFeedbackRepository feedbackRepository;
    private final LocalProjectPathGuard localProjectPathGuard;
    private final PendingChangeScanService pendingChangeScanService;
    private final DevelopmentSegmentationService developmentSegmentationService;
    private final ModelSegmentEnricher modelSegmentEnricher;
    private final ProjectFactIngestionService projectFactIngestionService;
    private final GitHubCliService gitHubCliService;
    private final ProjectAnalysisJobRepository jobRepository;
    private final ProjectFactAgentResultRefRepository factAgentResultRefRepository;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    // V3.3.3: 阶段推进用独立事务提交，避免搭 scan 大事务的便车导致前端轮询看不到 stage 推进。
    private final TransactionTemplate stageTransactionTemplate;

    public WorkSessionScanService(
        ProjectRepository projectRepository,
        ProjectMemoryRepository memoryRepository,
        WorkSessionRepository workSessionRepository,
        AgentSignatureFeedbackRepository feedbackRepository,
        LocalProjectPathGuard localProjectPathGuard,
        PendingChangeScanService pendingChangeScanService,
        DevelopmentSegmentationService developmentSegmentationService,
        ModelSegmentEnricher modelSegmentEnricher,
        ProjectFactIngestionService projectFactIngestionService,
        GitHubCliService gitHubCliService,
        ProjectAnalysisJobRepository jobRepository,
        ProjectFactAgentResultRefRepository factAgentResultRefRepository,
        ObjectMapper objectMapper,
        ApplicationEventPublisher eventPublisher,
        PlatformTransactionManager transactionManager
    ) {
        this.projectRepository = projectRepository;
        this.memoryRepository = memoryRepository;
        this.workSessionRepository = workSessionRepository;
        this.feedbackRepository = feedbackRepository;
        this.localProjectPathGuard = localProjectPathGuard;
        this.pendingChangeScanService = pendingChangeScanService;
        this.developmentSegmentationService = developmentSegmentationService;
        this.modelSegmentEnricher = modelSegmentEnricher;
        this.projectFactIngestionService = projectFactIngestionService;
        this.gitHubCliService = gitHubCliService;
        this.jobRepository = jobRepository;
        this.factAgentResultRefRepository = factAgentResultRefRepository;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        this.stageTransactionTemplate = new TransactionTemplate(transactionManager);
        this.stageTransactionTemplate.setPropagationBehavior(
            org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW
        );
    }

    public WorkSessionScanResponse scan(UUID userId, UUID projectId) {
        return scan(userId, projectId, null);
    }

    public WorkSessionScanResponse scan(UUID userId, UUID projectId, UUID jobId) {
        long scanStarted = System.nanoTime();
        ensureJobActive(jobId);
        ProjectSpace project = projectRepository.findByIdAndUserId(projectId, userId)
            .orElseThrow(() -> new AppException("PROJECT_NOT_FOUND", "Project was not found", HttpStatus.NOT_FOUND));
        ProjectMemory memory = memoryRepository.findByProjectId(project.getId())
            .orElseThrow(() -> new AppException("PROJECT_PATH_REQUIRED", "Bind a local project path before scanning", HttpStatus.BAD_REQUEST));
        Path projectRoot = localProjectPathGuard.requireGitProjectDirectory(memory.getLocalProjectPath()).path();

        List<String> warnings = new ArrayList<>();
        advanceStage(jobId, "GIT_SCAN", "正在读取本地 Git 提交与工作区变化");
        ensureJobActive(jobId);
        long gitStarted = System.nanoTime();
        String branchName = runGit(projectRoot, warnings, "branch", "--show-current").trim();
        ScanPlan scanPlan = pendingChangeScanService.prepare(projectRoot, project.getId(), branchName);
        warnings.addAll(scanPlan.warnings());
        GitEvidence evidence = readPendingCommits(project.getId(), projectRoot, branchName, warnings, scanPlan.gitLogArguments());
        GitEvidence uncommittedEvidence = readUncommittedEvidence(project.getId(), projectRoot, branchName, warnings);
        WorktreeFacts worktreeFacts = readWorktreeFacts(projectRoot);
        List<ChangeAtom> agentResultAtoms = uncoveredAgentResultAtoms(
            project.getId(), readAgentResultAtoms(projectRoot, warnings)
        );
        WorkSessionCandidateResponse uncommitted = uncommittedEvidence.hasChanges() ? uncommittedEvidence.toResponse() : null;
        long gitScanMs = elapsedMs(gitStarted);
        ensureJobActive(jobId);

        advanceStage(jobId, "GITHUB_INSPECT", "正在检查 GitHub 状态");
        long githubStarted = System.nanoTime();
        GitHubStatusResponse github = gitHubCliService.inspect(projectRoot);
        long githubInspectMs = elapsedMs(githubStarted);
        warnings.addAll(github.warnings());
        ensureJobActive(jobId);
        List<WorkSessionCandidateResponse> sessions = new ArrayList<>();
        if (uncommitted != null) {
            sessions.add(uncommitted);
        }
        if (evidence.hasChanges()) {
            sessions.add(evidence.toResponse());
        }
        List<WorkSessionCandidateResponse> persistedSessions = sessions.stream()
            .map(candidate -> saveCandidate(project.getId(), candidate))
            .toList();
        List<ChangeAtom> atoms = new ArrayList<>(evidence.toAtoms());
        atoms.addAll(uncommittedEvidence.toAtoms());
        atoms.addAll(agentResultAtoms);
        if (evidence.commitCount() == 0 && atoms.isEmpty()) {
            var latest = pendingChangeScanService.findLatest(project.getId());
            if (latest != null) {
                warnings.add("扫描指纹未变化：当前没有新的提交、工作区变化或 Agent result，已保留最近项目记录。");
                return new WorkSessionScanResponse(
                    project.getId(), projectRoot.toString(), branchName, Instant.now(), persistedSessions, warnings,
                    latest.batch(), latest.segments(), false
                );
            }
        }
        boolean worktreeDirty = uncommitted != null;
        String fingerprint = fingerprint(
            project.getId(), scanPlan, projectRoot, atoms, modelSegmentEnricher.configurationKey(userId), github.status(), github.remoteRelation()
        );
        var reusable = pendingChangeScanService.findReusable(project.getId(), fingerprint);
        if (reusable != null) {
            warnings.add("扫描指纹未变化，已复用已有开发推进段。");
            advanceStage(jobId, "PERSIST_FACTS", "正在幂等补写项目事实");
            projectFactIngestionService.ingestBatch(
                project.getId(), reusable.batch().id(), ProjectFactOrigin.INCREMENTAL_SCAN, true
            );
            ChangeBatchResponse reusableBatch = pendingChangeScanService.getBatch(reusable.batch().id());
            requestHistoryRebuild(userId, project.getId(), reusableBatch.headCommitSha());
            advanceStage(jobId, "SUCCEEDED", "分析完成（复用已有结果）");
            return new WorkSessionScanResponse(
                project.getId(), projectRoot.toString(), branchName, Instant.now(), persistedSessions, warnings,
                reusableBatch, reusable.segments(), scanPlan.firstScan()
            );
        }
        // V3.3.3: 构建分析输入快照，把多来源证据整理成结构化事实交给模型。
        AnalysisInputSnapshot snapshot = buildSnapshot(scanPlan, evidence, worktreeFacts, github, agentResultAtoms, modelSegmentEnricher.configurationKey(userId));
        recordInputSummary(jobId, snapshot);

        List<SegmentDraft> drafts = developmentSegmentationService.group(atoms);
        advanceStage(jobId, "MODEL_ENRICH", "正在调用模型分析开发推进段（可能需要几分钟，任务会继续运行）");
        ensureJobActive(jobId);
        long modelStarted = System.nanoTime();
        var enrichment = modelSegmentEnricher.enrichWithDiagnostics(userId, atoms, drafts, snapshot);
        long modelSegmentMs = elapsedMs(modelStarted);
        drafts = enrichment.segments();
        if (!enrichment.fallbackReason().isBlank()) warnings.add(enrichment.fallbackReason());
        for (String qw : enrichment.qualityWarnings()) warnings.add(qw);
        ensureJobActive(jobId);

        advanceStage(jobId, "PERSIST", "正在保存开发推进段并自动记录项目事实");
        ensureJobActive(jobId);
        long totalScanMs = elapsedMs(scanStarted);
        String analysisScopeJson = buildAnalysisScopeJson(snapshot, enrichment, worktreeDirty, github);
        ChangeBatchResponse batch = pendingChangeScanService.persist(
            project.getId(), scanPlan, evidence.commitCount(), evidence.changedFileCount(), agentResultAtoms.size(), warnings,
            new ScanDiagnostics(
                fingerprint, worktreeDirty, github.status(), github.remoteRelation(), enrichment.mode(), enrichment.modelStatus(),
                enrichment.providerName(), enrichment.fallbackReason(), gitScanMs, modelSegmentMs, githubInspectMs, totalScanMs, analysisScopeJson
            )
        );
        List<DevelopmentSegmentResponse> segments = pendingChangeScanService.persistSegments(
            project.getId(), batch.id(), drafts,
            // 任务级模型问题只在 batch 展示一次，不复制到每个本地事实摘要卡片。
            new SegmentDiagnostics(enrichment.mode(), enrichment.providerName(), "", github.status(), github.commitUrlTemplate()),
            atomTimes(atoms)
        );
        ensureJobActive(jobId);
        batch = pendingChangeScanService.finish(batch.id(), segments.size(), elapsedMs(scanStarted));
        projectFactIngestionService.ingestBatch(project.getId(), batch.id(), ProjectFactOrigin.INCREMENTAL_SCAN, true);
        batch = pendingChangeScanService.getBatch(batch.id());
        requestHistoryRebuild(userId, project.getId(), batch.headCommitSha());
        advanceStage(jobId, "SUCCEEDED", "分析完成");
        return new WorkSessionScanResponse(
            project.getId(),
            projectRoot.toString(),
            branchName,
            Instant.now(),
            persistedSessions,
            warnings,
            batch,
            segments,
            scanPlan.firstScan()
        );
    }

    /** Processes one explicit, oldest-to-newest history chunk. */
    public HistoryChunkResult scanHistoryChunk(
        UUID userId,
        UUID projectId,
        UUID jobId,
        List<String> commitShas
    ) {
        if (commitShas == null || commitShas.isEmpty()) {
            throw new IllegalArgumentException("History chunk requires commits");
        }
        long started = System.nanoTime();
        ensureJobActive(jobId);
        ProjectSpace project = projectRepository.findByIdAndUserId(projectId, userId)
            .orElseThrow(() -> new AppException("PROJECT_NOT_FOUND", "Project was not found", HttpStatus.NOT_FOUND));
        ProjectMemory memory = memoryRepository.findByProjectId(projectId)
            .orElseThrow(() -> new AppException("PROJECT_PATH_REQUIRED", "Bind a local project path before scanning", HttpStatus.BAD_REQUEST));
        Path root = localProjectPathGuard.requireGitProjectDirectory(memory.getLocalProjectPath()).path();
        if ("none".equals(modelSegmentEnricher.configurationKey(userId))) {
            throw new AppException("MODEL_NOT_CONFIGURED", "配置模型后才能补齐项目历史记忆", HttpStatus.BAD_REQUEST);
        }

        List<String> warnings = new ArrayList<>();
        warnings.add("这是自动历史事实重建批次，不影响当前增量事实游标。");
        advanceStage(jobId, "HISTORY_GIT_SCAN", "正在读取一个有界历史提交批次");
        String branch = runGit(root, warnings, "branch", "--show-current").trim();
        List<String> args = new ArrayList<>(List.of(
            "log", "--no-walk=unsorted", "--numstat",
            "--pretty=format:__PF_COMMIT__%x09%H%x09%an%x09%aI%x09%s"
        ));
        args.addAll(commitShas);
        long gitStarted = System.nanoTime();
        GitEvidence evidence = readPendingCommits(projectId, root, branch, warnings, args);
        long gitScanMs = elapsedMs(gitStarted);
        List<ChangeAtom> atoms = new ArrayList<>(evidence.toAtoms());
        ensureJobActive(jobId);

        GitHubStatusResponse github = new GitHubStatusResponse(
            false, false, false, "", "", "", branch, "", "", "", "",
            "NOT_USED", "unknown", 0, 0, List.of()
        );
        WorktreeFacts worktree = new WorktreeFacts(false, false, false, false, List.of(), List.of(), List.of(), false);
        ScanPlan plan = new ScanPlan(
            commitShas.get(0), commitShas.get(commitShas.size() - 1), branch, false, args, warnings
        );
        String modelKey = modelSegmentEnricher.configurationKey(userId);
        String fingerprint = historyFingerprint(projectId, commitShas, modelKey);
        var reusable = pendingChangeScanService.findReusable(projectId, fingerprint);
        if (reusable != null) {
            projectFactIngestionService.ingestBatch(projectId, reusable.batch().id(), ProjectFactOrigin.HISTORY_BACKFILL, false);
            pendingChangeScanService.markScanType(reusable.batch().id(), "HISTORY_BACKFILL");
            return new HistoryChunkResult(
                pendingChangeScanService.getBatch(reusable.batch().id()), reusable.segments(), true
            );
        }

        AnalysisInputSnapshot snapshot = buildSnapshot(plan, evidence, worktree, github, List.of(), modelKey);
        recordInputSummary(jobId, snapshot);
        List<SegmentDraft> fallback = developmentSegmentationService.group(atoms);
        advanceStage(jobId, "HISTORY_MODEL_ENRICH", "正在用已配置模型归并历史开发推进段");
        long modelStarted = System.nanoTime();
        var enrichment = modelSegmentEnricher.enrichWithDiagnostics(userId, atoms, fallback, snapshot);
        long modelMs = elapsedMs(modelStarted);
        ensureJobActive(jobId);
        if (!"MODEL".equals(enrichment.mode())) {
            throw new AppException(
                "HISTORY_MODEL_UNAVAILABLE",
                enrichment.fallbackReason().isBlank() ? "历史事实模型分析未成功，本批已暂停" : enrichment.fallbackReason(),
                HttpStatus.BAD_GATEWAY
            );
        }
        warnings.addAll(enrichment.qualityWarnings());
        if (!enrichment.fallbackReason().isBlank()) warnings.add(enrichment.fallbackReason());
        String scope = buildAnalysisScopeJson(snapshot, enrichment, false, github);

        advanceStage(jobId, "HISTORY_PERSIST", "正在持久化历史开发推进段与项目事实");
        ChangeBatchResponse batch = pendingChangeScanService.persist(
            projectId, plan, evidence.commitCount(), evidence.changedFileCount(), 0, warnings,
            new ScanDiagnostics(
                fingerprint, false, "NOT_USED", "unknown", enrichment.mode(), enrichment.modelStatus(),
                enrichment.providerName(), enrichment.fallbackReason(), gitScanMs, modelMs, 0, elapsedMs(started), scope
            )
        );
        pendingChangeScanService.markScanType(batch.id(), "HISTORY_BACKFILL");
        List<DevelopmentSegmentResponse> segments = pendingChangeScanService.persistSegments(
            projectId, batch.id(), enrichment.segments(),
            new SegmentDiagnostics(enrichment.mode(), enrichment.providerName(), enrichment.fallbackReason(), "NOT_USED", ""),
            atomTimes(atoms)
        );
        ensureJobActive(jobId);
        pendingChangeScanService.finish(batch.id(), segments.size(), elapsedMs(started));
        projectFactIngestionService.ingestBatch(projectId, batch.id(), ProjectFactOrigin.HISTORY_BACKFILL, false);
        return new HistoryChunkResult(pendingChangeScanService.getBatch(batch.id()), segments, false);
    }

    private Map<String, Instant> atomTimes(List<ChangeAtom> atoms) {
        return atoms.stream()
            .filter(atom -> atom.id() != null && atom.occurredAt() != null)
            .collect(java.util.stream.Collectors.toMap(
                ChangeAtom::id,
                ChangeAtom::occurredAt,
                (left, right) -> left.isBefore(right) ? left : right,
                LinkedHashMap::new
            ));
    }

    private void requestHistoryRebuild(UUID userId, UUID projectId, String upperBoundSha) {
        boolean configured = !"none".equals(modelSegmentEnricher.configurationKey(userId));
        eventPublisher.publishEvent(new ProjectFactHistoryRequestedEvent(
            userId, projectId, upperBoundSha == null ? "" : upperBoundSha, configured
        ));
    }

    private void ensureJobActive(UUID jobId) {
        if (jobId == null) return;
        ProjectAnalysisJob job = jobRepository.findById(jobId).orElse(null);
        if (job != null && (job.isCancellationRequested() || job.isTerminal())) {
            throw new java.util.concurrent.CancellationException("分析任务已停止");
        }
    }

    // V3.3.3: 推进 job 阶段，让前端轮询时看到"现在在做什么"。
    // 用 REQUIRES_NEW 独立事务提交，避免 scan 主事务未提交时前端读到旧 stage（H2 READ_COMMITTED）。
    private void advanceStage(UUID jobId, String stage, String message) {
        if (jobId == null) return;
        stageTransactionTemplate.executeWithoutResult(status ->
            jobRepository.findById(jobId).ifPresent(job -> {
                job.advanceStage(stage, message);
                jobRepository.save(job);
            })
        );
    }

    private void recordInputSummary(UUID jobId, AnalysisInputSnapshot snapshot) {
        if (jobId == null) return;
        try {
            String summary = objectMapper.writeValueAsString(snapshot.scanScope());
            stageTransactionTemplate.executeWithoutResult(status ->
                jobRepository.findById(jobId).ifPresent(job -> {
                    job.recordInputSummary(summary);
                    jobRepository.save(job);
                })
            );
        } catch (Exception ignored) {
            // 输入摘要不是关键路径，失败不影响分析。
        }
    }

    // V3.3.3: 读取工作区事实（unstaged/staged/untracked）。
    private WorktreeFacts readWorktreeFacts(Path projectRoot) {
        String porcelain = runGit(projectRoot, new ArrayList<>(), "status", "--porcelain=v1", "--untracked-files=all");
        boolean hasUnstaged = false;
        boolean hasStaged = false;
        boolean hasUntracked = false;
        List<String> unstagedFiles = new ArrayList<>();
        List<String> stagedFiles = new ArrayList<>();
        List<String> untrackedFiles = new ArrayList<>();
        for (String line : porcelain.split("\\R")) {
            if (line.length() < 3) continue;
            char x = line.charAt(0);
            char y = line.charAt(1);
            String file = line.substring(3).trim();
            if (x == '?') {
                hasUntracked = true;
                untrackedFiles.add(file);
            } else {
                if (x != ' ' && x != '?') { hasStaged = true; stagedFiles.add(file); }
                if (y != ' ' && y != '?') { hasUnstaged = true; unstagedFiles.add(file); }
            }
        }
        boolean dirty = hasUnstaged || hasStaged || hasUntracked;
        boolean possiblyUnfinished = dirty;
        return new WorktreeFacts(hasUnstaged, hasStaged, hasUntracked, dirty,
            unstagedFiles.stream().limit(20).toList(), stagedFiles.stream().limit(20).toList(),
            untrackedFiles.stream().limit(20).toList(), possiblyUnfinished);
    }

    // V3.3.3: 构建多来源证据快照。
    private AnalysisInputSnapshot buildSnapshot(ScanPlan plan, GitEvidence evidence, WorktreeFacts worktree, GitHubStatusResponse github, List<ChangeAtom> agentAtoms, String modelKey) {
        GitFacts gitFacts = new GitFacts(
            plan.branchName(), plan.headCommitSha(), plan.baseCommitSha(), plan.firstScan(),
            evidence.commitCount(), evidence.commitSummaries().stream().limit(10).toList(),
            new ArrayList<>(evidence.files().keySet()).stream().limit(30).toList(),
            evidence.commitSummaries().stream().limit(6).toList()
        );
        boolean githubParticipated = github != null && "CONNECTED".equals(github.status());
        GitHubFacts githubFacts = new GitHubFacts(
            github.ghInstalled(), github.ghAuthenticated(), github.repoDetected(),
            github.nameWithOwner(), "", github.localAhead(), github.remoteAhead(),
            github.remoteRelation(), githubParticipated, github.status()
        );
        List<String> taskGoals = agentAtoms.stream().map(ChangeAtom::title).limit(8).toList();
        List<String> agentFiles = agentAtoms.stream().flatMap(a -> a.files().stream()).distinct().limit(30).toList();
        Set<String> gitFileSet = new LinkedHashSet<>(evidence.files().keySet());
        boolean overlaps = agentFiles.stream().anyMatch(gitFileSet::contains);
        boolean onlyAgent = !evidence.hasChanges() && !agentAtoms.isEmpty();
        AgentResultFacts agentFacts = new AgentResultFacts(agentAtoms.size(), taskGoals, agentFiles, overlaps, onlyAgent);
        // V3.3.4: evidenceGap 只在真实证据不足时标记，不再因为 GitHub 未参与就默认缺口。
        // GitHub 未登录/未安装/连接失败但本地证据充足 -> 不算缺口。
        String evidenceGapReason = computeEvidenceGapReason(evidence, agentAtoms, onlyAgent, worktree, github);
        boolean evidenceGap = evidenceGapReason != null;
        ScanScopeFacts scope = new ScanScopeFacts(
            evidence.commitCount(), evidence.changedFileCount(), agentAtoms.size(),
            worktree.worktreeDirty(), githubParticipated, !"none".equals(modelKey), evidenceGap,
            evidenceGapReason == null ? "" : evidenceGapReason
        );
        return new AnalysisInputSnapshot(gitFacts, worktree, githubFacts, agentFacts, scope);
    }

    // V3.3.4: 证据缺口基于真实条件判断，不因 GitHub 未参与默认缺口。
    // 返回缺口原因（无缺口返回 null），供口径展示和模型 prompt 使用。
    private String computeEvidenceGapReason(GitEvidence evidence, List<ChangeAtom> agentAtoms, boolean onlyAgent, WorktreeFacts worktree, GitHubStatusResponse github) {
        boolean hasCodeChanges = evidence.hasChanges() || worktree.worktreeDirty();
        boolean hasAgentResults = !agentAtoms.isEmpty();
        // 只有 Agent result，没有对应代码变化。
        if (onlyAgent) {
            return "只有 Agent result 但缺少对应代码变化";
        }
        // 代码有变化但缺少 Agent result / 解释证据。
        if (hasCodeChanges && !hasAgentResults) {
            return "代码有变化但缺少 Agent result 等解释证据";
        }
        // 只有未提交变化，且无提交/Agent result 解释。
        if (!evidence.hasChanges() && worktree.worktreeDirty() && !hasAgentResults) {
            return "只有未提交工作区变化，且无提交或 Agent result 解释";
        }
        // 远程领先但本地未同步。
        if ("remote_ahead".equals(github.remoteRelation())) {
            return "远程领先，本地未同步，分析可能不完整";
        }
        // 本地和远程分叉。
        if ("diverged".equals(github.remoteRelation())) {
            return "本地和远程已分叉，分析可能不完整";
        }
        return null;
    }

    // V3.3.3: 构建分析口径 JSON，记录本次用了哪些来源。
    private String buildAnalysisScopeJson(AnalysisInputSnapshot snapshot, ModelSegmentEnricher.EnrichmentResult enrichment, boolean worktreeDirty, GitHubStatusResponse github) {
        try {
            var scope = snapshot.scanScope();
            var obj = objectMapper.createObjectNode();
            obj.put("localGit", "参与");
            obj.put("worktreeDiff", worktreeDirty ? "有" : "无");
            obj.put("unstaged", snapshot.worktree().hasUnstaged() ? "有" : "无");
            obj.put("staged", snapshot.worktree().hasStaged() ? "有" : "无");
            obj.put("untracked", snapshot.worktree().hasUntracked() ? "有" : "无");
            obj.put("agentResults", "读取 " + scope.inputAgentResultCount() + " 条");
            obj.put("github", scope.githubParticipated() ? "参与" : "未参与");
            obj.put("githubStatus", github.status());
            obj.put("githubRemoteRelation", github.remoteRelation());
            obj.put("model", "MODEL".equals(enrichment.mode()) ? "已使用" : "未配置/请求失败/未返回");
            obj.put("modelStatus", enrichment.modelStatus());
            obj.put("mergeMode", "MODEL".equals(enrichment.mode())
                ? (enrichment.qualityWarnings().isEmpty() ? "模型分析" : "模型草稿需复核")
                : "本地事实摘要");
            obj.put("hasUncommitted", worktreeDirty);
            obj.put("hasRemoteUnsynced", "remote_ahead".equals(github.remoteRelation()) || "diverged".equals(github.remoteRelation()));
            obj.put("evidenceGap", scope.evidenceGap());
            obj.put("evidenceGapReason", scope.evidenceGapReason());
            obj.put("inputCommits", scope.inputCommitCount());
            obj.put("inputFiles", scope.inputFileCount());
            var diagnostics = enrichment.modelDiagnostics();
            if (diagnostics != null) {
                obj.put("providerName", diagnostics.providerName());
                obj.put("modelName", diagnostics.modelName());
                obj.put("entryPoint", diagnostics.entryPoint());
                obj.put("taskType", diagnostics.taskType());
                obj.put("capabilityProfile", diagnostics.capabilityProfile());
                obj.put("finishReason", diagnostics.finishReason());
                obj.put("promptTokens", diagnostics.promptTokens());
                obj.put("completionTokens", diagnostics.completionTokens());
                obj.put("totalTokens", diagnostics.totalTokens());
                obj.put("providerMaxTokens", diagnostics.providerMaxTokens());
                obj.put("taskPolicyMaxTokens", diagnostics.taskPolicyMaxTokens());
                obj.put("effectiveMaxTokens", diagnostics.effectiveMaxTokens());
                obj.put("providerTemperature", diagnostics.providerTemperature());
                obj.put("recommendedTemperature", diagnostics.recommendedTemperature());
                obj.put("effectiveTemperature", diagnostics.effectiveTemperature());
                obj.put("temperatureSent", diagnostics.temperatureSent());
                obj.put("temperatureDecision", diagnostics.temperatureDecision());
                obj.put("maxTokenDecision", diagnostics.maxTokenDecision());
                obj.put("timeoutSeconds", diagnostics.timeoutSeconds());
                obj.put("requestLatencyMs", diagnostics.latencyMs());
                obj.put("modelContentPresent", diagnostics.contentPresent());
                obj.put("outputTruncated", diagnostics.truncated());
                obj.put("compactRetryAttempted", diagnostics.compactRetryAttempted());
                obj.put("compactRetrySucceeded", diagnostics.compactRetrySucceeded());
                obj.put("requestCount", diagnostics.requestCount());
                obj.put("transportRetryCount", diagnostics.transportRetryCount());
                obj.put("retryType", diagnostics.retryType());
                obj.put("reasoningBudgetExhausted", diagnostics.reasoningBudgetExhausted());
                obj.put("jsonRepaired", diagnostics.jsonRepaired());
                obj.put("schemaMatched", diagnostics.schemaMatched());
                obj.put("partialResult", diagnostics.partialResult());
                obj.put("recoveredItems", diagnostics.recoveredItems());
            }
            return objectMapper.writeValueAsString(obj);
        } catch (Exception exception) {
            return "";
        }
    }

    @Transactional(readOnly = true)
    public List<WorkSessionCandidateResponse> list(UUID userId, UUID projectId) {
        ProjectSpace project = projectRepository.findByIdAndUserId(projectId, userId)
            .orElseThrow(() -> new AppException("PROJECT_NOT_FOUND", "Project was not found", HttpStatus.NOT_FOUND));
        return workSessionRepository.findByProjectIdOrderByEndTimeDesc(project.getId()).stream()
            .map(WorkSessionScanService::toWorkSessionResponse)
            .toList();
    }

    @Transactional
    public WorkSessionCandidateResponse patch(UUID userId, UUID sessionId, WorkSessionPatchRequest request) {
        WorkSession session = workSessionRepository.findById(sessionId)
            .orElseThrow(() -> new AppException("WORK_SESSION_NOT_FOUND", "Work session was not found", HttpStatus.NOT_FOUND));
        projectRepository.findByIdAndUserId(session.getProjectId(), userId)
            .orElseThrow(() -> new AppException("PROJECT_NOT_FOUND", "Project was not found", HttpStatus.NOT_FOUND));
        WorkSessionCandidateResponse before = toWorkSessionResponse(session);
        session.correctAttribution(request.agentType(), request.taskIntent());
        WorkSessionCandidateResponse corrected = toWorkSessionResponse(workSessionRepository.save(session));
        saveFeedback(before, corrected);
        return corrected;
    }

    @Transactional(readOnly = true)
    public List<AgentSignatureFeedbackResponse> listFeedback(UUID userId, UUID projectId) {
        ProjectSpace project = projectRepository.findByIdAndUserId(projectId, userId)
            .orElseThrow(() -> new AppException("PROJECT_NOT_FOUND", "Project was not found", HttpStatus.NOT_FOUND));
        return feedbackRepository.findByProjectIdOrderByUpdatedAtDesc(project.getId()).stream()
            .map(this::toFeedbackResponse)
            .toList();
    }

    private WorkSessionCandidateResponse saveCandidate(UUID projectId, WorkSessionCandidateResponse candidate) {
        WorkSessionCandidateResponse candidateWithFeedback = applyFeedback(projectId, candidate);
        UUID sessionId = UUID.fromString(candidate.sessionId());
        WorkSession session = workSessionRepository.findById(sessionId)
            .orElseGet(() -> new WorkSession(sessionId, projectId));
        session.updateFromCandidate(
            candidateWithFeedback.agentType(),
            candidateWithFeedback.agentName(),
            candidateWithFeedback.taskIntent(),
            candidateWithFeedback.branchName(),
            candidateWithFeedback.baseCommit(),
            candidateWithFeedback.startTime(),
            candidateWithFeedback.endTime(),
            candidateWithFeedback.attributionConfidence(),
            candidateWithFeedback.detectionMethod(),
            candidateWithFeedback.changedFiles(),
            candidateWithFeedback.addedLines(),
            candidateWithFeedback.deletedLines(),
            candidateWithFeedback.affectedModules(),
            candidateWithFeedback.evidence(),
            candidateWithFeedback.files()
        );
        return toWorkSessionResponse(workSessionRepository.save(session));
    }

    static WorkSessionCandidateResponse toWorkSessionResponse(WorkSession session) {
        return new WorkSessionCandidateResponse(
            session.getId().toString(),
            session.getProjectId(),
            session.getAgentType(),
            session.getAgentName(),
            session.getTaskIntent(),
            session.getBranchName(),
            session.getBaseCommit(),
            session.getStartTime(),
            session.getEndTime(),
            session.getAttributionConfidence(),
            session.getDetectionMethod(),
            session.getChangedFiles(),
            session.getAddedLines(),
            session.getDeletedLines(),
            session.getAffectedModules(),
            session.getEvidence(),
            session.getFiles()
        );
    }

    private AgentSignatureFeedbackResponse toFeedbackResponse(AgentSignatureFeedback feedback) {
        return new AgentSignatureFeedbackResponse(
            feedback.getId(),
            feedback.getProjectId(),
            feedback.getAgentName(),
            feedback.getOriginalAgentType(),
            feedback.getCorrectedAgentType(),
            feedback.getCorrectedTaskIntent(),
            feedback.getScope(),
            feedback.getCreatedAt(),
            feedback.getUpdatedAt()
        );
    }

    private WorkSessionCandidateResponse applyFeedback(UUID projectId, WorkSessionCandidateResponse candidate) {
        if (!"UNKNOWN".equals(candidate.agentType())) {
            return candidate;
        }
        return feedbackRepository.findFirstByProjectIdAndAgentNameOrderByUpdatedAtDesc(projectId, candidate.agentName())
            .map(feedback -> new WorkSessionCandidateResponse(
                candidate.sessionId(),
                candidate.projectId(),
                feedback.getCorrectedAgentType(),
                candidate.agentName(),
                feedback.getCorrectedTaskIntent().isBlank() ? candidate.taskIntent() : feedback.getCorrectedTaskIntent(),
                candidate.branchName(),
                candidate.baseCommit(),
                candidate.startTime(),
                candidate.endTime(),
                candidate.attributionConfidence(),
                "USER_FEEDBACK",
                candidate.changedFiles(),
                candidate.addedLines(),
                candidate.deletedLines(),
                candidate.affectedModules(),
                candidate.evidence(),
                candidate.files()
            ))
            .orElse(candidate);
    }

    private void saveFeedback(WorkSessionCandidateResponse before, WorkSessionCandidateResponse corrected) {
        if (before.agentType().equals(corrected.agentType())) {
            return;
        }
        AgentSignatureFeedback feedback = new AgentSignatureFeedback(corrected.projectId(), corrected.agentName());
        feedback.update(before.agentType(), corrected.agentType(), corrected.taskIntent());
        feedbackRepository.save(feedback);
    }

    private GitEvidence readPendingCommits(
        UUID projectId,
        Path projectRoot,
        String branchName,
        List<String> warnings,
        List<String> gitLogArguments
    ) {
        String output = runGit(projectRoot, warnings, gitLogArguments.toArray(String[]::new));
        GitEvidence evidence = new GitEvidence(projectId, branchName);
        CommitCursor current = null;
        for (String rawLine : output.split("\\R")) {
            String line = rawLine.stripTrailing();
            if (line.isBlank()) {
                continue;
            }
            if (line.startsWith("__PF_COMMIT__")) {
                String[] parts = line.split("\\t", 5);
                current = parts.length >= 5 ? new CommitCursor(parts[1], parts[2], parseInstant(parts[3]), parts[4]) : null;
                if (current != null) {
                    evidence.addCommit(current);
                }
                continue;
            }
            if (current != null) {
                evidence.addNumstat(current, line);
            }
        }
        return evidence;
    }

    private GitEvidence readUncommittedEvidence(UUID projectId, Path projectRoot, String branchName, List<String> warnings) {
        String output = runGit(projectRoot, warnings, "diff", "--numstat", "HEAD", "--");
        GitEvidence evidence = new GitEvidence(projectId, branchName);
        CommitCursor cursor = new CommitCursor("WORKTREE", "Unknown", Instant.now(), "Uncommitted working tree changes");
        evidence.addCommit(cursor);
        for (String rawLine : output.split("\\R")) {
            String line = rawLine.stripTrailing();
            if (!line.isBlank()) {
                evidence.addNumstat(cursor, line);
            }
        }
        return evidence;
    }

    private List<ChangeAtom> readAgentResultAtoms(Path projectRoot, List<String> warnings) {
        Path resultsRoot = projectRoot.resolve(".projectflow/agent-results").toAbsolutePath().normalize();
        if (!Files.isDirectory(resultsRoot)) return List.of();
        List<ChangeAtom> atoms = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(resultsRoot, 3)) {
            for (Path path : paths.filter(Files::isRegularFile).filter(item -> item.getFileName().toString().equals("result.json")).sorted().limit(50).toList()) {
                Path normalized = path.toAbsolutePath().normalize();
                if (!normalized.startsWith(resultsRoot) || Files.size(normalized) > 1_000_000) continue;
                try {
                    JsonNode json = objectMapper.readTree(Files.readString(normalized, StandardCharsets.UTF_8));
                    List<String> changes = jsonStrings(json.path("actualChanges"), 6);
                    List<String> files = jsonStrings(json.path("keyFiles"), 40).stream().filter(this::safeRelativeEvidencePath).toList();
                    String relative = projectRoot.relativize(normalized).toString().replace('\\', '/');
                    String title = changes.isEmpty() ? json.path("taskGoal").asText("Agent result") : changes.get(0);
                    List<String> refs = new ArrayList<>();
                    refs.add("agent-result:" + relative);
                    files.forEach(file -> refs.add("file:" + file));
                    atoms.add(new ChangeAtom(
                        "agent:" + relative, title, Files.getLastModifiedTime(normalized).toInstant(),
                        files.stream().map(WorkSessionScanService::moduleName).distinct().toList(), files, refs,
                        changes, "AGENT_RESULT"
                    ));
                } catch (Exception exception) {
                    warnings.add("Agent result 无法解析，已跳过：" + projectRoot.relativize(normalized).toString().replace('\\', '/'));
                }
            }
        } catch (IOException exception) {
            warnings.add("Agent result 目录读取失败，本次继续使用本地 Git 分析。");
        }
        return atoms;
    }

    private List<String> jsonStrings(JsonNode value, int limit) {
        if (!value.isArray()) return List.of();
        List<String> result = new ArrayList<>();
        value.forEach(item -> {
            if (result.size() < limit && item.isTextual() && !item.asText().isBlank()) result.add(item.asText().trim());
        });
        return result;
    }

    private boolean safeRelativeEvidencePath(String value) {
        if (value == null || value.isBlank() || value.length() > 1_000) return false;
        String normalized = value.replace('\\', '/');
        return !normalized.startsWith("/") && !normalized.matches("^[A-Za-z]:.*")
            && Stream.of(normalized.split("/")).noneMatch(part -> part.equals(".."));
    }

    private String fingerprint(
        UUID projectId, ScanPlan plan, Path root, List<ChangeAtom> atoms, String modelConfig, String githubStatus, String remoteRelation
    ) {
        String worktree = runGit(root, new ArrayList<>(), "status", "--porcelain=v1", "--untracked-files=all");
        String atomIds = atoms.stream().map(ChangeAtom::id).sorted().reduce("", (left, right) -> left + "|" + right);
        String input = String.join("\n", projectId.toString(), plan.branchName(), plan.baseCommitSha(), plan.headCommitSha(), worktree,
            atomIds, "v3.3.3", modelConfig, githubStatus, remoteRelation);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private List<ChangeAtom> uncoveredAgentResultAtoms(UUID projectId, List<ChangeAtom> atoms) {
        Set<String> covered = new LinkedHashSet<>(factAgentResultRefRepository.findDistinctAgentResultRefsByProjectId(projectId));
        return atoms.stream().filter(atom -> {
            String id = atom.id() == null ? "" : atom.id();
            String ref = id.startsWith("agent:") ? "agent-result:" + id.substring(6) : "agent-result:" + id;
            return !covered.contains(ref);
        }).toList();
    }

    private String historyFingerprint(UUID projectId, List<String> commits, String modelConfig) {
        String input = String.join("\n", projectId.toString(), "HISTORY_BACKFILL", String.join("|", commits), modelConfig, "v3.4.0");
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private long elapsedMs(long started) {
        return Math.max(0, (System.nanoTime() - started) / 1_000_000L);
    }

    private String runGit(Path projectRoot, List<String> warnings, String... args) {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));
        try {
            Process process = new ProcessBuilder(command)
                .directory(projectRoot.toFile())
                .redirectErrorStream(true)
                .start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                warnings.add("Git command failed: git " + String.join(" ", args));
                return "";
            }
            return output;
        } catch (IOException exception) {
            warnings.add("Git command could not run: " + exception.getMessage());
            return "";
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            warnings.add("Git command was interrupted.");
            return "";
        }
    }

    private Instant parseInstant(String value) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            return Instant.now();
        }
    }

    private static boolean ignoredPath(String path) {
        String normalized = path.replace("\\", "/");
        return normalized.startsWith(".projectflow/")
            || normalized.startsWith(".codex-run/")
            || normalized.contains("/.codex-run/")
            || normalized.startsWith(".git/")
            || normalized.contains("/.git/")
            || normalized.contains("/node_modules/")
            || normalized.startsWith("node_modules/")
            || normalized.contains("/target/")
            || normalized.startsWith("target/")
            || normalized.contains("/.next/")
            || normalized.startsWith(".next/")
            || normalized.contains("/dist/")
            || normalized.startsWith("dist/")
            || normalized.contains("/build/")
            || normalized.startsWith("build/");
    }

    private static String moduleName(String path) {
        String normalized = path.replace("\\", "/");
        int slash = normalized.indexOf('/');
        return slash > 0 ? normalized.substring(0, slash) : normalized;
    }

    private record CommitCursor(String hash, String author, Instant time, String subject) {
    }

    private static final class GitEvidence {
        private final UUID projectId;
        private final String branchName;
        private final LinkedHashMap<String, FileStat> files = new LinkedHashMap<>();
        private final LinkedHashSet<String> modules = new LinkedHashSet<>();
        private final List<String> commitSummaries = new ArrayList<>();
        private final LinkedHashMap<String, AtomAccumulator> atoms = new LinkedHashMap<>();
        private Instant startTime;
        private Instant endTime;
        private String baseCommit = "";
        private String agentName = "Unknown";

        private GitEvidence(UUID projectId, String branchName) {
            this.projectId = projectId;
            this.branchName = branchName;
        }

        private void addCommit(CommitCursor commit) {
            if (baseCommit.isBlank()) {
                baseCommit = commit.hash();
                agentName = commit.author();
            }
            if (startTime == null || commit.time().isBefore(startTime)) {
                startTime = commit.time();
            }
            if (endTime == null || commit.time().isAfter(endTime)) {
                endTime = commit.time();
            }
            commitSummaries.add(commitSummary(commit));
            atoms.putIfAbsent(commit.hash(), new AtomAccumulator(commit));
        }

        private void addNumstat(CommitCursor commit, String line) {
            String[] parts = line.split("\\t");
            if (parts.length < 3 || ignoredPath(parts[2])) {
                return;
            }
            int added = parseCount(parts[0]);
            int deleted = parseCount(parts[1]);
            String file = parts[2];
            files.merge(file, new FileStat(added, deleted), FileStat::merge);
            modules.add(moduleName(file));
            atoms.computeIfAbsent(commit.hash(), ignored -> new AtomAccumulator(commit)).addFile(file);
            if (startTime == null || commit.time().isBefore(startTime)) {
                startTime = commit.time();
            }
            if (endTime == null || commit.time().isAfter(endTime)) {
                endTime = commit.time();
            }
        }

        private boolean hasChanges() {
            return !files.isEmpty();
        }

        // V3.3.3: 暴露给快照构建器（同包可见）。
        LinkedHashMap<String, FileStat> files() {
            return files;
        }

        List<String> commitSummaries() {
            return List.copyOf(commitSummaries);
        }

        private int commitCount() {
            return commitSummaries.size();
        }

        private int changedFileCount() {
            return files.size();
        }

        private List<ChangeAtom> toAtoms() {
            return atoms.values().stream()
                .filter(atom -> !atom.files.isEmpty())
                .map(AtomAccumulator::toAtom)
                .toList();
        }

        private WorkSessionCandidateResponse toResponse() {
            int addedLines = files.values().stream().mapToInt(FileStat::added).sum();
            int deletedLines = files.values().stream().mapToInt(FileStat::deleted).sum();
            String seed = projectId + ":" + branchName + ":" + baseCommit + ":" + files.keySet();
            List<String> evidence = evidenceLines(addedLines, deletedLines);
            return new WorkSessionCandidateResponse(
                UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString(),
                projectId,
                "UNKNOWN",
                agentName,
                taskIntent(),
                branchName,
                baseCommit,
                startTime == null ? Instant.now() : startTime,
                endTime == null ? Instant.now() : endTime,
                "MEDIUM",
                "GIT_EVIDENCE",
                files.size(),
                addedLines,
                deletedLines,
                List.copyOf(modules),
                evidence,
                List.copyOf(files.keySet())
            );
        }

        private String taskIntent() {
            String module = modules.stream().findFirst().orElse("项目");
            String subject = commitSummaries.stream()
                .findFirst()
                .map(summary -> summary.replaceFirst("^提交 [^：]+：", ""))
                .orElse("未提交工作区变更");
            return "更新 " + module + " 相关内容：" + subject;
        }

        private List<String> evidenceLines(int addedLines, int deletedLines) {
            List<String> lines = new ArrayList<>();
            lines.add("本轮 Git 变化：修改 " + files.size() + " 个文件，新增 " + addedLines + " 行，删除 " + deletedLines + " 行。");
            if (!modules.isEmpty()) {
                lines.add("主要涉及：" + String.join("、", modules.stream().limit(5).toList()) + "。");
            }
            if (!files.isEmpty()) {
                lines.add("代表文件：" + files.entrySet().stream()
                    .limit(5)
                    .map(entry -> compactPath(entry.getKey()) + "（+" + entry.getValue().added() + "/-" + entry.getValue().deleted() + "）")
                    .reduce((first, second) -> first + "；" + second)
                    .orElse("暂无文件明细。"));
            }
            if (!commitSummaries.isEmpty()) {
                lines.add("提交线索：" + String.join("；", commitSummaries.stream().limit(4).toList()));
            }
            return lines;
        }

        private static int parseCount(String value) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException exception) {
                return 0;
            }
        }

        private static String shortHash(String hash) {
            return hash.length() > 12 ? hash.substring(0, 12) : hash;
        }

        private static String commitSummary(CommitCursor commit) {
            if ("WORKTREE".equals(commit.hash())) {
                return "未提交工作区变更";
            }
            return "提交 " + shortHash(commit.hash()) + "：" + commit.subject();
        }

        private static String compactPath(String path) {
            String normalized = path.replace("\\", "/");
            String[] markers = {"/controller/", "/service/", "/repository/", "/entity/", "/dto/", "/app/"};
            for (String marker : markers) {
                int index = normalized.indexOf(marker);
                if (index >= 0) {
                    return normalized.substring(index + 1);
                }
            }
            return normalized.length() <= 80 ? normalized : "..." + normalized.substring(normalized.length() - 77);
        }
    }

    private static final class AtomAccumulator {
        private final CommitCursor commit;
        private final LinkedHashSet<String> files = new LinkedHashSet<>();
        private final LinkedHashSet<String> modules = new LinkedHashSet<>();

        private AtomAccumulator(CommitCursor commit) {
            this.commit = commit;
        }

        private void addFile(String file) {
            files.add(file);
            modules.add(moduleName(file));
        }

        private ChangeAtom toAtom() {
            List<String> refs = new ArrayList<>();
            refs.add("commit:" + commit.hash());
            files.forEach(file -> refs.add("file:" + file));
            // V3.3.4 小阶段修复：diffHints 只保留文件摘要，不再重复 commit subject（atom.title() 已有）。
            // 这能显著减小发给模型的 prompt 体积，从根源降低因 prompt 过大导致调用失败的概率。
            List<String> hints = files.isEmpty()
                ? List.of()
                : List.of("files=" + String.join(",", files.stream().limit(8).toList()));
            return new ChangeAtom(
                commit.hash(),
                commit.subject(),
                commit.time(),
                List.copyOf(modules),
                List.copyOf(files),
                refs,
                hints,
                "WORKTREE".equals(commit.hash()) ? "WORKTREE" : "GIT_COMMIT"
            );
        }
    }

    private record FileStat(int added, int deleted) {
        private FileStat merge(FileStat other) {
            return new FileStat(added + other.added, deleted + other.deleted);
        }
    }

    public record HistoryChunkResult(
        ChangeBatchResponse batch,
        List<DevelopmentSegmentResponse> segments,
        boolean reused
    ) {
    }
}
