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
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.projectflow.dto.V2ProjectDtos.WorkSessionCandidateResponse;
import com.projectflow.dto.V2ProjectDtos.WorkSessionPatchRequest;
import com.projectflow.dto.V2ProjectDtos.WorkSessionScanResponse;
import com.projectflow.dto.V2ProjectDtos.AgentSignatureFeedbackResponse;
import com.projectflow.entity.AgentSignatureFeedback;
import com.projectflow.entity.ProjectMemory;
import com.projectflow.entity.ProjectSpace;
import com.projectflow.entity.WorkSession;
import com.projectflow.repository.AgentSignatureFeedbackRepository;
import com.projectflow.repository.ProjectMemoryRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.repository.WorkSessionRepository;
import com.projectflow.support.AppException;
import com.projectflow.dto.V33WorkflowDtos.ChangeBatchResponse;
import com.projectflow.dto.V33WorkflowDtos.DevelopmentSegmentResponse;
import com.projectflow.dto.V33WorkflowDtos.GitHubStatusResponse;
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
    private final ProjectSedimentService projectSedimentService;
    private final GitHubCliService gitHubCliService;
    private final ObjectMapper objectMapper;

    public WorkSessionScanService(
        ProjectRepository projectRepository,
        ProjectMemoryRepository memoryRepository,
        WorkSessionRepository workSessionRepository,
        AgentSignatureFeedbackRepository feedbackRepository,
        LocalProjectPathGuard localProjectPathGuard,
        PendingChangeScanService pendingChangeScanService,
        DevelopmentSegmentationService developmentSegmentationService,
        ModelSegmentEnricher modelSegmentEnricher,
        ProjectSedimentService projectSedimentService,
        GitHubCliService gitHubCliService,
        ObjectMapper objectMapper
    ) {
        this.projectRepository = projectRepository;
        this.memoryRepository = memoryRepository;
        this.workSessionRepository = workSessionRepository;
        this.feedbackRepository = feedbackRepository;
        this.localProjectPathGuard = localProjectPathGuard;
        this.pendingChangeScanService = pendingChangeScanService;
        this.developmentSegmentationService = developmentSegmentationService;
        this.modelSegmentEnricher = modelSegmentEnricher;
        this.projectSedimentService = projectSedimentService;
        this.gitHubCliService = gitHubCliService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public WorkSessionScanResponse scan(UUID userId, UUID projectId) {
        long scanStarted = System.nanoTime();
        ProjectSpace project = projectRepository.findByIdAndUserId(projectId, userId)
            .orElseThrow(() -> new AppException("PROJECT_NOT_FOUND", "Project was not found", HttpStatus.NOT_FOUND));
        ProjectMemory memory = memoryRepository.findByProjectId(project.getId())
            .orElseThrow(() -> new AppException("PROJECT_PATH_REQUIRED", "Bind a local project path before scanning", HttpStatus.BAD_REQUEST));
        Path projectRoot = localProjectPathGuard.requireGitProjectDirectory(memory.getLocalProjectPath()).path();

        List<String> warnings = new ArrayList<>();
        long gitStarted = System.nanoTime();
        String branchName = runGit(projectRoot, warnings, "branch", "--show-current").trim();
        ScanPlan scanPlan = pendingChangeScanService.prepare(projectRoot, project.getId(), branchName);
        warnings.addAll(scanPlan.warnings());
        GitEvidence evidence = readPendingCommits(project.getId(), projectRoot, branchName, warnings, scanPlan.gitLogArguments());
        GitEvidence uncommittedEvidence = readUncommittedEvidence(project.getId(), projectRoot, branchName, warnings);
        List<ChangeAtom> agentResultAtoms = readAgentResultAtoms(projectRoot, warnings);
        WorkSessionCandidateResponse uncommitted = uncommittedEvidence.hasChanges() ? uncommittedEvidence.toResponse() : null;
        long gitScanMs = elapsedMs(gitStarted);
        long githubStarted = System.nanoTime();
        GitHubStatusResponse github = gitHubCliService.inspect(projectRoot);
        long githubInspectMs = elapsedMs(githubStarted);
        warnings.addAll(github.warnings());
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
        boolean worktreeDirty = uncommitted != null;
        String fingerprint = fingerprint(
            project.getId(), scanPlan, projectRoot, atoms, modelSegmentEnricher.configurationKey(userId), github.status(), github.remoteRelation()
        );
        var reusable = pendingChangeScanService.findReusable(project.getId(), fingerprint);
        if (reusable != null) {
            warnings.add("扫描指纹未变化，已复用已有开发推进段。");
            return new WorkSessionScanResponse(
                project.getId(), projectRoot.toString(), branchName, Instant.now(), persistedSessions, warnings,
                reusable.batch(), reusable.segments(), scanPlan.firstScan()
            );
        }
        List<SegmentDraft> drafts = developmentSegmentationService.group(atoms);
        long modelStarted = System.nanoTime();
        var enrichment = modelSegmentEnricher.enrichWithDiagnostics(userId, atoms, drafts);
        long modelSegmentMs = elapsedMs(modelStarted);
        drafts = enrichment.segments();
        if (!enrichment.fallbackReason().isBlank()) warnings.add(enrichment.fallbackReason());
        long totalScanMs = elapsedMs(scanStarted);
        ChangeBatchResponse batch = pendingChangeScanService.persist(
            project.getId(), scanPlan, evidence.commitCount(), evidence.changedFileCount(), agentResultAtoms.size(), warnings,
            new ScanDiagnostics(
                fingerprint, worktreeDirty, github.status(), github.remoteRelation(), enrichment.mode(), enrichment.modelStatus(),
                enrichment.providerName(), enrichment.fallbackReason(), gitScanMs, modelSegmentMs, githubInspectMs, totalScanMs
            )
        );
        List<DevelopmentSegmentResponse> segments = pendingChangeScanService.persistSegments(
            project.getId(), batch.id(), drafts,
            new SegmentDiagnostics(enrichment.mode(), enrichment.providerName(), enrichment.fallbackReason(), github.status(), github.commitUrlTemplate())
        );
        projectSedimentService.createSuggestions(project.getId(), segments);
        batch = pendingChangeScanService.finish(batch.id(), segments.size(), elapsedMs(scanStarted));
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

    @Transactional(readOnly = true)
    public List<WorkSessionCandidateResponse> list(UUID userId, UUID projectId) {
        ProjectSpace project = projectRepository.findByIdAndUserId(projectId, userId)
            .orElseThrow(() -> new AppException("PROJECT_NOT_FOUND", "Project was not found", HttpStatus.NOT_FOUND));
        return workSessionRepository.findByProjectIdOrderByEndTimeDesc(project.getId()).stream()
            .map(this::toWorkSessionResponse)
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

    private WorkSessionCandidateResponse toWorkSessionResponse(WorkSession session) {
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
            atomIds, "v3.3.2", modelConfig, githubStatus, remoteRelation);
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
            return new ChangeAtom(
                commit.hash(),
                commit.subject(),
                commit.time(),
                List.copyOf(modules),
                List.copyOf(files),
                refs,
                List.of("commit=" + commit.subject(), "files=" + String.join(",", files.stream().limit(8).toList())),
                "WORKTREE".equals(commit.hash()) ? "WORKTREE" : "GIT_COMMIT"
            );
        }
    }

    private record FileStat(int added, int deleted) {
        private FileStat merge(FileStat other) {
            return new FileStat(added + other.added, deleted + other.deleted);
        }
    }
}
