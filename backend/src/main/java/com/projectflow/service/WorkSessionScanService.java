package com.projectflow.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

@Service
public class WorkSessionScanService {
    private final ProjectRepository projectRepository;
    private final ProjectMemoryRepository memoryRepository;
    private final WorkSessionRepository workSessionRepository;
    private final AgentSignatureFeedbackRepository feedbackRepository;
    private final LocalProjectPathGuard localProjectPathGuard;

    public WorkSessionScanService(
        ProjectRepository projectRepository,
        ProjectMemoryRepository memoryRepository,
        WorkSessionRepository workSessionRepository,
        AgentSignatureFeedbackRepository feedbackRepository,
        LocalProjectPathGuard localProjectPathGuard
    ) {
        this.projectRepository = projectRepository;
        this.memoryRepository = memoryRepository;
        this.workSessionRepository = workSessionRepository;
        this.feedbackRepository = feedbackRepository;
        this.localProjectPathGuard = localProjectPathGuard;
    }

    @Transactional
    public WorkSessionScanResponse scan(UUID userId, UUID projectId) {
        ProjectSpace project = projectRepository.findByIdAndUserId(projectId, userId)
            .orElseThrow(() -> new AppException("PROJECT_NOT_FOUND", "Project was not found", HttpStatus.NOT_FOUND));
        ProjectMemory memory = memoryRepository.findByProjectId(project.getId())
            .orElseThrow(() -> new AppException("PROJECT_PATH_REQUIRED", "Bind a local project path before scanning", HttpStatus.BAD_REQUEST));
        Path projectRoot = localProjectPathGuard.requireGitProjectDirectory(memory.getLocalProjectPath()).path();

        List<String> warnings = new ArrayList<>();
        String branchName = runGit(projectRoot, warnings, "branch", "--show-current").trim();
        GitEvidence evidence = readTodayCommits(project.getId(), projectRoot, branchName, warnings);
        WorkSessionCandidateResponse uncommitted = readUncommittedChanges(project.getId(), projectRoot, branchName, warnings);
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
        return new WorkSessionScanResponse(
            project.getId(),
            projectRoot.toString(),
            branchName,
            Instant.now(),
            persistedSessions,
            warnings
        );
    }

    @Transactional(readOnly = true)
    public List<WorkSessionCandidateResponse> list(UUID userId, UUID projectId) {
        ProjectSpace project = projectRepository.findByIdAndUserId(projectId, userId)
            .orElseThrow(() -> new AppException("PROJECT_NOT_FOUND", "Project was not found", HttpStatus.NOT_FOUND));
        return workSessionRepository.findByProjectIdOrderByEndTimeDesc(project.getId()).stream()
            .map(WorkSession::toResponse)
            .toList();
    }

    @Transactional
    public WorkSessionCandidateResponse patch(UUID userId, UUID sessionId, WorkSessionPatchRequest request) {
        WorkSession session = workSessionRepository.findById(sessionId)
            .orElseThrow(() -> new AppException("WORK_SESSION_NOT_FOUND", "Work session was not found", HttpStatus.NOT_FOUND));
        projectRepository.findByIdAndUserId(session.getProjectId(), userId)
            .orElseThrow(() -> new AppException("PROJECT_NOT_FOUND", "Project was not found", HttpStatus.NOT_FOUND));
        WorkSessionCandidateResponse before = session.toResponse();
        session.correctAttribution(request.agentType(), request.taskIntent());
        WorkSessionCandidateResponse corrected = workSessionRepository.save(session).toResponse();
        saveFeedback(before, corrected);
        return corrected;
    }

    @Transactional(readOnly = true)
    public List<AgentSignatureFeedbackResponse> listFeedback(UUID userId, UUID projectId) {
        ProjectSpace project = projectRepository.findByIdAndUserId(projectId, userId)
            .orElseThrow(() -> new AppException("PROJECT_NOT_FOUND", "Project was not found", HttpStatus.NOT_FOUND));
        return feedbackRepository.findByProjectIdOrderByUpdatedAtDesc(project.getId()).stream()
            .map(AgentSignatureFeedback::toResponse)
            .toList();
    }

    private WorkSessionCandidateResponse saveCandidate(UUID projectId, WorkSessionCandidateResponse candidate) {
        WorkSessionCandidateResponse candidateWithFeedback = applyFeedback(projectId, candidate);
        UUID sessionId = UUID.fromString(candidate.sessionId());
        WorkSession session = workSessionRepository.findById(sessionId)
            .orElseGet(() -> new WorkSession(sessionId, projectId));
        session.updateFromCandidate(candidateWithFeedback);
        return workSessionRepository.save(session).toResponse();
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

    private GitEvidence readTodayCommits(UUID projectId, Path projectRoot, String branchName, List<String> warnings) {
        String since = LocalDate.now(ZoneId.systemDefault()).atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime().toString();
        String output = runGit(
            projectRoot,
            warnings,
            "log",
            "--since=" + since,
            "--numstat",
            "--pretty=format:__PF_COMMIT__%x09%H%x09%an%x09%aI%x09%s"
        );
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

    private WorkSessionCandidateResponse readUncommittedChanges(UUID projectId, Path projectRoot, String branchName, List<String> warnings) {
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
        return evidence.hasChanges() ? evidence.toResponse() : null;
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
            || normalized.startsWith(".git/")
            || normalized.contains("/node_modules/")
            || normalized.startsWith("node_modules/")
            || normalized.contains("/target/")
            || normalized.startsWith("target/")
            || normalized.contains("/.next/")
            || normalized.startsWith(".next/");
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

    private record FileStat(int added, int deleted) {
        private FileStat merge(FileStat other) {
            return new FileStat(added + other.added, deleted + other.deleted);
        }
    }
}
