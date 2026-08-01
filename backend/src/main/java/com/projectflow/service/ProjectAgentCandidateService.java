package com.projectflow.service;

import static com.projectflow.dto.ProjectAgentCandidateDtos.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projectflow.dto.ProjectUnderstandingDtos.ProjectUnderstandingSnapshotResponse;
import com.projectflow.entity.ProjectAgentCandidate;
import com.projectflow.entity.ProjectFactEpistemicStatus;
import com.projectflow.repository.ProjectAgentCandidateRepository;
import com.projectflow.repository.ProjectFactRepository;
import com.projectflow.repository.ProjectMemoryRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.support.AppException;

@Service
public class ProjectAgentCandidateService {
    private static final Pattern COMMIT_REF = Pattern.compile("[0-9a-fA-F]{7,64}");
    private static final Pattern WINDOWS_ABSOLUTE = Pattern.compile("(?i)[A-Z]:[\\\\/][^\\s\"']+");
    private static final Pattern UNIX_ABSOLUTE = Pattern.compile("(?<![\\w.])/(?:[^\\s\"']+/)+[^\\s\"']*");

    private final ProjectRepository projectRepository;
    private final ProjectFactRepository factRepository;
    private final ProjectAgentCandidateRepository candidateRepository;
    private final ProjectMemoryRepository memoryRepository;
    private final ProjectUnderstandingService understandingService;
    private final SensitiveContentRedactor redactor;
    private final LocalProjectPathGuard pathGuard;
    private final LargeFileContentService largeFileContentService;
    private final LocalCommandExecutor commandExecutor;

    public ProjectAgentCandidateService(
        ProjectRepository projectRepository,
        ProjectFactRepository factRepository,
        ProjectAgentCandidateRepository candidateRepository,
        ProjectMemoryRepository memoryRepository,
        ProjectUnderstandingService understandingService,
        SensitiveContentRedactor redactor,
        LocalProjectPathGuard pathGuard,
        LargeFileContentService largeFileContentService,
        LocalCommandExecutor commandExecutor
    ) {
        this.projectRepository = projectRepository;
        this.factRepository = factRepository;
        this.candidateRepository = candidateRepository;
        this.memoryRepository = memoryRepository;
        this.understandingService = understandingService;
        this.redactor = redactor;
        this.pathGuard = pathGuard;
        this.largeFileContentService = largeFileContentService;
        this.commandExecutor = commandExecutor;
    }

    @Transactional
    public AgentCandidateResponse submit(
        UUID userId,
        UUID projectId,
        SubmitAgentCandidateRequest request,
        String caller
    ) {
        ownedProject(userId, projectId);
        ProjectFactEpistemicStatus epistemic = candidateStatus(request.epistemicStatus());
        List<String> evidenceRefs = validateEvidenceRefs(userId, projectId, request.evidenceRefs());
        String sourceAgentId = firstText(caller, request.sourceAgentId(), "unknown-agent");
        ProjectAgentCandidate candidate;
        try {
            candidate = new ProjectAgentCandidate(
                projectId,
                request.candidateType(),
                redactor.redact(request.assertion()),
                epistemic,
                evidenceRefs,
                request.currentness(),
                request.sourceRevision(),
                bounded(request.limitations(), 20).stream().map(redactor::redact).toList(),
                redactor.redact(sourceAgentId)
            );
        } catch (IllegalArgumentException exception) {
            throw new AppException("AGENT_CANDIDATE_INVALID", exception.getMessage(), HttpStatus.BAD_REQUEST);
        }
        return response(candidateRepository.save(candidate));
    }

    @Transactional
    public AgentWorkResultResponse submitWorkResult(
        UUID userId,
        UUID projectId,
        SubmitAgentWorkResultRequest request,
        String caller
    ) {
        ownedProject(userId, projectId);
        for (AgentCandidateInput input : safe(request.candidateFacts())) {
            candidateStatus(input.epistemicStatus());
        }
        Path projectRoot = boundProjectRoot(projectId);
        List<String> changedFiles = normalizedPaths(request.changedFiles());
        List<String> limitations = new ArrayList<>(bounded(request.knownLimitations(), 30));
        limitations.addAll(bounded(request.unresolvedItems(), 30).stream()
            .map(value -> "未解决：" + safeProcessText(value)).toList());
        List<String> baseEvidence = new ArrayList<>(validateEvidenceRefs(userId, projectId, request.evidenceRefs()));
        List<String> rereadEvidence = new ArrayList<>();
        int expectedReads = 0;
        int completedReads = 0;
        for (String relative : changedFiles) {
            if (redactor.isSensitivePath(relative)) {
                rereadEvidence.add("sensitive-file-metadata:" + shortHash(relative));
                limitations.add("敏感路径只验证 metadata，未读取内容：" + relative);
                continue;
            }
            expectedReads++;
            if (projectRoot == null) {
                limitations.add("项目未绑定可读取目录，无法复验 changed file：" + relative);
                continue;
            }
            Path target = projectRoot.resolve(relative).normalize();
            if (!target.startsWith(projectRoot) || !Files.isRegularFile(target)) {
                limitations.add("changed file 不存在或超出项目边界：" + relative);
                continue;
            }
            var contentMap = largeFileContentService.analyze(target, 1_500);
            if (contentMap.binary()) {
                rereadEvidence.add("file-metadata:" + relative + "#sha256=" + contentMap.sourceHash());
                limitations.add("二进制 changed file 只验证 metadata：" + relative);
            } else if (!contentMap.sourceHash().isBlank()) {
                rereadEvidence.add("file:" + relative + "#sha256=" + contentMap.sourceHash());
                completedReads++;
            }
        }
        List<String> validatedCommits = validateCommitRefs(projectRoot, request.commitRefs(), limitations);
        rereadEvidence.addAll(validatedCommits);
        baseEvidence.addAll(rereadEvidence);

        String persistedRevision = currentPersistedRevision(userId, projectId);
        String requestedRevision = bounded(request.sourceRevision(), 180);
        String sourceRevision = requestedRevision.isBlank() ? persistedRevision : requestedRevision;
        if (!requestedRevision.isBlank() && !persistedRevision.isBlank()
            && !requestedRevision.equals(persistedRevision)) {
            limitations.add("Agent 提交 Revision 与当前持久化 Revision 不一致，结果必须重新验证");
        }
        String validationStatus = expectedReads > 0 && completedReads == expectedReads
            && (requestedRevision.isBlank() || persistedRevision.isBlank() || requestedRevision.equals(persistedRevision))
                ? "SOURCE_IDENTITY_REVALIDATED"
                : completedReads > 0 || !validatedCommits.isEmpty()
                    ? "PARTIAL_SOURCE_VALIDATION"
                    : "PENDING_ENGINEERING_VALIDATION";
        String sourceAgentId = redactor.redact(firstText(caller, request.sourceAgentId(), "unknown-agent"));
        ProjectAgentCandidate workResult = new ProjectAgentCandidate(
            projectId,
            "WORK_RESULT",
            workResultAssertion(request, changedFiles, validatedCommits),
            ProjectFactEpistemicStatus.PROCESS_EVIDENCE,
            List.copyOf(new LinkedHashSet<>(baseEvidence)),
            persistedRevision.isBlank() || persistedRevision.equals(sourceRevision) ? "CURRENT" : "POSSIBLY_STALE",
            sourceRevision,
            limitations.stream().map(redactor::redact).limit(30).toList(),
            sourceAgentId
        );
        workResult.markValidationStatus(validationStatus);
        workResult = candidateRepository.save(workResult);

        List<ProjectAgentCandidate> saved = new ArrayList<>();
        saved.add(workResult);
        String workEvidence = "agent-result:" + workResult.getId();
        for (AgentCandidateInput input : safe(request.candidateFacts())) {
            ProjectFactEpistemicStatus status = candidateStatus(input.epistemicStatus());
            List<String> refs = validateCandidateRefs(
                userId, projectId, input.evidenceRefs(), Set.copyOf(baseEvidence)
            );
            if (refs.isEmpty()) refs = List.of(workEvidence);
            ProjectAgentCandidate candidate = new ProjectAgentCandidate(
                projectId, input.candidateType(), redactor.redact(input.assertion()), status, refs,
                "UNKNOWN", sourceRevision,
                bounded(input.limitations(), 20).stream().map(redactor::redact).toList(), sourceAgentId
            );
            saved.add(candidateRepository.save(candidate));
        }
        for (String conflict : bounded(request.candidateConflicts(), 30)) {
            saved.add(candidateRepository.save(new ProjectAgentCandidate(
                projectId, "CONFLICT_REPORT", redactor.redact(conflict), ProjectFactEpistemicStatus.CONFLICTED,
                List.of(workEvidence), "UNKNOWN", sourceRevision, List.of(), sourceAgentId
            )));
        }
        for (String resolution : bounded(request.candidateUnknownResolutions(), 30)) {
            saved.add(candidateRepository.save(new ProjectAgentCandidate(
                projectId, "UNKNOWN_RESOLUTION", redactor.redact(resolution), ProjectFactEpistemicStatus.UNKNOWN,
                List.of(workEvidence), "UNKNOWN", sourceRevision,
                List.of("Agent 提交的是 UNKNOWN resolution candidate，仍需工程复验"), sourceAgentId
            )));
        }
        return new AgentWorkResultResponse(
            projectId, workResult.getId(), changedFiles, List.copyOf(new LinkedHashSet<>(rereadEvidence)),
            sourceRevision, validationStatus, saved.stream().map(ProjectAgentCandidateService::response).toList(),
            List.copyOf(new LinkedHashSet<>(limitations)), workResult.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public AgentCandidatePageResponse list(UUID userId, UUID projectId, int page, int size) {
        ownedProject(userId, projectId);
        var result = candidateRepository.findByProjectIdOrderByCreatedAtDesc(
            projectId, PageRequest.of(Math.max(0, page), Math.max(1, Math.min(100, size <= 0 ? 20 : size)))
        );
        return new AgentCandidatePageResponse(
            result.getContent().stream().map(ProjectAgentCandidateService::response).toList(),
            result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages()
        );
    }

    private Path boundProjectRoot(UUID projectId) {
        return memoryRepository.findByProjectId(projectId)
            .map(value -> value.getLocalProjectPath())
            .filter(value -> value != null && !value.isBlank())
            .map(value -> {
                try {
                    return pathGuard.requireProjectDirectory(value).path().normalize();
                } catch (AppException exception) {
                    return null;
                }
            })
            .orElse(null);
    }

    private List<String> normalizedPaths(List<String> values) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String raw : safe(values)) {
            String value = raw == null ? "" : raw.strip().replace('\\', '/');
            if (value.isBlank()) continue;
            if (value.startsWith("/") || value.matches("^[A-Za-z]:/.*") || value.contains("../")) {
                throw new AppException(
                    "AGENT_WORK_RESULT_PATH_INVALID", "changedFiles 只能包含项目内相对路径", HttpStatus.BAD_REQUEST
                );
            }
            result.add(value.length() <= 300 ? value : value.substring(0, 300));
        }
        return List.copyOf(result);
    }

    private List<String> validateCommitRefs(Path projectRoot, List<String> values, List<String> limitations) {
        List<String> result = new ArrayList<>();
        for (String raw : bounded(values, 30)) {
            String value = raw.strip();
            if (!COMMIT_REF.matcher(value).matches()) {
                limitations.add("忽略无法验证的 commit ref：" + safeProcessText(value));
                continue;
            }
            if (projectRoot == null || !Files.isDirectory(projectRoot.resolve(".git"))) {
                limitations.add("本地 Git 不可用，commit ref 仍是 Agent 声明：" + value);
                continue;
            }
            LocalCommandExecutor.CommandResult check = commandExecutor.execute(
                projectRoot, List.of("git", "cat-file", "-e", value + "^{commit}"), Duration.ofSeconds(3)
            );
            if (!check.timedOut() && check.exitCode() == 0) result.add("commit:" + value.toLowerCase(Locale.ROOT));
            else limitations.add("本地 Git 未确认 commit ref：" + value);
        }
        return List.copyOf(result);
    }

    private String currentPersistedRevision(UUID userId, UUID projectId) {
        try {
            ProjectUnderstandingSnapshotResponse snapshot = understandingService.get(userId, projectId);
            return bounded(snapshot.sourceRevision(), 180);
        } catch (AppException exception) {
            return "";
        }
    }

    private String workResultAssertion(
        SubmitAgentWorkResultRequest request,
        List<String> changedFiles,
        List<String> validatedCommits
    ) {
        List<String> parts = new ArrayList<>();
        if (!changedFiles.isEmpty()) parts.add("changedFiles=" + String.join("、", changedFiles.stream().limit(30).toList()));
        addProcessPart(parts, "claimedBehavior", request.claimedBehaviors(), 12);
        addProcessPart(parts, "executedCommands", request.executedCommands(), 12);
        addProcessPart(parts, "testResults", request.testResults(), 12);
        if (!validatedCommits.isEmpty()) parts.add("validatedCommits=" + String.join("、", validatedCommits));
        addProcessPart(parts, "pullRequests", request.pullRequestRefs(), 12);
        addProcessPart(parts, "knownLimitations", request.knownLimitations(), 12);
        addProcessPart(parts, "unresolvedItems", request.unresolvedItems(), 12);
        String assertion = "Agent 工作结果（PROCESS_EVIDENCE）：" + String.join("；", parts);
        if (parts.isEmpty()) assertion += "未提交可验证的工作结果字段";
        return bounded(redactor.redact(assertion), 4_000);
    }

    private static void addProcessPart(List<String> parts, String label, List<String> values, int limit) {
        List<String> safeValues = bounded(values, limit).stream().map(ProjectAgentCandidateService::safeProcessText).toList();
        if (!safeValues.isEmpty()) parts.add(label + "=" + String.join(" | ", safeValues));
    }

    private List<String> validateCandidateRefs(
        UUID userId,
        UUID projectId,
        List<String> requested,
        Set<String> workEvidence
    ) {
        if (requested == null || requested.isEmpty()) return List.of();
        List<String> normal = new ArrayList<>();
        for (String raw : requested) {
            String value = raw == null ? "" : raw.strip();
            if (value.isBlank()) continue;
            if (workEvidence.contains(value)) normal.add(value);
            else normal.addAll(validateEvidenceRefs(userId, projectId, List.of(value)));
        }
        return List.copyOf(new LinkedHashSet<>(normal));
    }

    private static String safeProcessText(String value) {
        if (value == null) return "";
        String safe = WINDOWS_ABSOLUTE.matcher(value.strip()).replaceAll("[ABSOLUTE_PATH_REDACTED]");
        safe = UNIX_ABSOLUTE.matcher(safe).replaceAll("[ABSOLUTE_PATH_REDACTED]");
        return safe.length() <= 1_000 ? safe : safe.substring(0, 1_000);
    }

    private static String shortHash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 10);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private List<String> validateEvidenceRefs(UUID userId, UUID projectId, List<String> requested) {
        if (requested == null || requested.isEmpty()) return List.of();
        Set<String> allowed = new LinkedHashSet<>();
        try {
            ProjectUnderstandingSnapshotResponse snapshot = understandingService.get(userId, projectId);
            if (snapshot.sourceMap() != null) {
                snapshot.sourceMap().sources().forEach(source -> allowed.add(source.id()));
            }
            if (snapshot.analysisExecution() != null) {
                snapshot.analysisExecution().evidence().forEach(evidence -> allowed.add(evidence.id()));
            }
        } catch (AppException ignored) {
            // A project may not have an understanding snapshot yet.
        }
        List<String> result = new ArrayList<>();
        for (String raw : requested) {
            String value = raw == null ? "" : raw.strip();
            if (value.isBlank()) continue;
            if (value.startsWith("fact:")) {
                try {
                    UUID factId = UUID.fromString(value.substring("fact:".length()));
                    if (factRepository.findByIdAndProjectId(factId, projectId).isPresent()) {
                        result.add(value);
                        continue;
                    }
                } catch (RuntimeException ignored) {
                    // handled below
                }
            } else if (allowed.contains(value)) {
                result.add(value);
                continue;
            }
            throw new AppException(
                "AGENT_CANDIDATE_EVIDENCE_INVALID",
                "候选引用了未知或跨项目 Evidence ID",
                HttpStatus.BAD_REQUEST
            );
        }
        return List.copyOf(new LinkedHashSet<>(result));
    }

    private static ProjectFactEpistemicStatus candidateStatus(String value) {
        try {
            ProjectFactEpistemicStatus status = ProjectFactEpistemicStatus.valueOf(
                value == null ? "" : value.strip().toUpperCase(Locale.ROOT)
            );
            if (status.isStrongFact()) throw new IllegalArgumentException();
            return status;
        } catch (RuntimeException exception) {
            throw new AppException(
                "AGENT_CANDIDATE_STATUS_INVALID",
                "Agent 候选只能使用 DECLARED、INFERRED、CONFLICTED、UNKNOWN 或 PROCESS_EVIDENCE",
                HttpStatus.BAD_REQUEST
            );
        }
    }

    private void ownedProject(UUID userId, UUID projectId) {
        projectRepository.findByIdAndUserId(projectId, userId)
            .orElseThrow(() -> new AppException("PROJECT_NOT_FOUND", "项目不存在", HttpStatus.NOT_FOUND));
    }

    private static AgentCandidateResponse response(ProjectAgentCandidate value) {
        return new AgentCandidateResponse(
            value.getId(), value.getProjectId(), value.getCandidateType(), value.getAssertion(),
            value.getEpistemicStatus().name(), value.getEvidenceRefs(), value.getCurrentness(),
            value.getSourceRevision(), value.getLimitations(), value.getSourceAgentId(),
            value.getValidationStatus(), value.getCreatedAt()
        );
    }

    private static List<String> bounded(List<String> values, int limit) {
        return values == null ? List.of() : values.stream().filter(java.util.Objects::nonNull)
            .map(String::strip).filter(value -> !value.isBlank()).limit(limit).toList();
    }

    private static String bounded(String value, int limit) {
        if (value == null) return "";
        String safe = value.strip();
        return safe.length() <= limit ? safe : safe.substring(0, limit);
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static String firstText(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value.strip();
        return "";
    }
}
