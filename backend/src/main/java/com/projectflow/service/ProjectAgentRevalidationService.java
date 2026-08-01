package com.projectflow.service;

import static com.projectflow.dto.ProjectAgentRevalidationDtos.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projectflow.dto.ProjectAgentHistoryDtos.AgentContextPackageResponse;
import com.projectflow.dto.ProjectAgentHistoryDtos.AgentEvidenceResponse;
import com.projectflow.entity.ProjectFact;
import com.projectflow.entity.ProjectSpace;
import com.projectflow.repository.ProjectFactRepository;
import com.projectflow.repository.ProjectMemoryRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.support.AppException;

@Service
public class ProjectAgentRevalidationService {
    private static final Pattern COMMIT = Pattern.compile("[0-9a-fA-F]{7,64}");

    private final ProjectRepository projectRepository;
    private final ProjectMemoryRepository memoryRepository;
    private final ProjectFactRepository factRepository;
    private final ProjectAgentHistoryService historyService;
    private final LocalProjectPathGuard pathGuard;
    private final LargeFileContentService contentService;
    private final LocalCommandExecutor commandExecutor;
    private final SensitiveContentRedactor redactor;

    public ProjectAgentRevalidationService(
        ProjectRepository projectRepository,
        ProjectMemoryRepository memoryRepository,
        ProjectFactRepository factRepository,
        ProjectAgentHistoryService historyService,
        LocalProjectPathGuard pathGuard,
        LargeFileContentService contentService,
        LocalCommandExecutor commandExecutor,
        SensitiveContentRedactor redactor
    ) {
        this.projectRepository = projectRepository;
        this.memoryRepository = memoryRepository;
        this.factRepository = factRepository;
        this.historyService = historyService;
        this.pathGuard = pathGuard;
        this.contentService = contentService;
        this.commandExecutor = commandExecutor;
        this.redactor = redactor;
    }

    @Transactional(readOnly = true)
    public AgentRevalidationResponse revalidate(
        UUID userId,
        UUID projectId,
        AgentRevalidationRequest request
    ) {
        ownedProject(userId, projectId);
        String action = normalizeAction(request.action());
        return switch (action) {
            case "VERIFY_FACT" -> verifyFact(userId, projectId, request);
            case "REFRESH_EVIDENCE" -> refreshEvidence(userId, projectId, request, false);
            case "REREAD_RANGE" -> refreshEvidence(userId, projectId, request, true);
            case "VALIDATE_CURRENTNESS" -> validateCurrentness(userId, projectId, request);
            case "RESOLVE_PACKAGE_LATEST" -> resolvePackage(userId, projectId, request);
            default -> throw new AppException(
                "AGENT_REVALIDATION_ACTION_INVALID", "不支持的局部复验动作", HttpStatus.BAD_REQUEST
            );
        };
    }

    private AgentRevalidationResponse verifyFact(
        UUID userId,
        UUID projectId,
        AgentRevalidationRequest request
    ) {
        UUID factId = factId(request.targetId());
        ProjectFact fact = factRepository.findByIdAndProjectId(factId, projectId)
            .orElseThrow(() -> new AppException("FACT_NOT_FOUND", "事实不存在或不属于该项目", HttpStatus.NOT_FOUND));
        Path root = projectRoot(projectId);
        List<String> verified = new ArrayList<>();
        List<String> limitations = new ArrayList<>();
        RevalidatedRangeResponse firstRange = null;
        for (String evidenceRef : fact.getEvidenceRefs().stream().limit(30).toList()) {
            if (evidenceRef.startsWith("commit:")) {
                String commit = evidenceRef.substring("commit:".length()).strip();
                if (verifyCommit(root, commit)) verified.add("commit:" + commit);
                else limitations.add("未确认 commit Evidence：" + commit);
                continue;
            }
            EvidenceTarget target = resolveTarget(userId, projectId, root, evidenceRef);
            if (target == null) {
                limitations.add("当前局部复验不支持或找不到 Evidence：" + evidenceRef);
                continue;
            }
            RangeRead read = readTarget(target, 1, 80, 4_000, "FACT_VERIFY");
            if (read.validated()) {
                verified.add(evidenceRef);
                if (firstRange == null) firstRange = read.range();
            } else {
                limitations.addAll(read.limitations());
            }
        }
        String currentRevision = currentRevision(root);
        String currentness = revisionCurrentness(fact.getRevision(), currentRevision);
        boolean success = !verified.isEmpty();
        return response(
            projectId, "VERIFY_FACT", "fact:" + factId, success ? "SUCCEEDED" : "PARTIAL",
            fact.getRevision(), currentRevision, currentness,
            success ? "LOCAL_EVIDENCE_REVALIDATED" : "INSUFFICIENT_REVALIDATION_EVIDENCE",
            verified, null, firstRange, null, limitations
        );
    }

    private AgentRevalidationResponse refreshEvidence(
        UUID userId,
        UUID projectId,
        AgentRevalidationRequest request,
        boolean explicitRange
    ) {
        String evidenceId = firstText(request.evidenceId(), request.targetId());
        if (evidenceId.isBlank()) {
            throw new AppException("EVIDENCE_ID_REQUIRED", "局部复验需要 Evidence ID", HttpStatus.BAD_REQUEST);
        }
        AgentEvidenceResponse persisted = historyService.evidence(userId, projectId, evidenceId);
        Path root = projectRoot(projectId);
        EvidenceTarget target = target(root, persisted.evidenceId(), persisted.locator());
        if (target == null) {
            return response(
                projectId, explicitRange ? "REREAD_RANGE" : "REFRESH_EVIDENCE", evidenceId, "UNAVAILABLE",
                persisted.sourceRevision(), currentRevision(root), "UNKNOWN", "SOURCE_LOCATOR_UNAVAILABLE",
                List.of(), persisted, null, null, List.of("Evidence 没有可安全复读的项目内相对 locator")
            );
        }
        long start = explicitRange ? Math.max(1, value(request.startLine(), 1)) : 1;
        long requestedEnd = explicitRange ? Math.max(start, value(request.endLine(), start + 79)) : start + 79;
        long end = Math.min(requestedEnd, start + 199);
        int maxChars = Math.max(256, Math.min(16_000, request.maxChars() == null ? 4_000 : request.maxChars()));
        RangeRead read = readTarget(target, start, end, maxChars, explicitRange ? "AGENT_RANGE" : "EVIDENCE_REFRESH");
        String currentRevision = currentRevision(root);
        String currentness = read.validated()
            ? revisionCurrentness(persisted.sourceRevision(), currentRevision)
            : "UNKNOWN";
        AgentEvidenceResponse refreshed = read.validated() ? new AgentEvidenceResponse(
            projectId, persisted.evidenceId(), persisted.category(), persisted.sourceType(), persisted.locator(),
            persisted.semanticRole(), persisted.importance(), currentness, persisted.confidence(),
            "LOCALLY_REVALIDATED_RANGE", "局部复读完成；sourceHash=" + read.range().sourceHash()
                + "；lines=" + read.range().startLine() + "-" + read.range().endLine(),
            persisted.evidenceRefs(), currentRevision.isBlank() ? persisted.sourceRevision() : currentRevision
        ) : persisted;
        return response(
            projectId, explicitRange ? "REREAD_RANGE" : "REFRESH_EVIDENCE", evidenceId,
            read.validated() ? "SUCCEEDED" : "UNAVAILABLE", persisted.sourceRevision(), currentRevision,
            currentness, read.validated() ? "LOCAL_SOURCE_RANGE_REVALIDATED" : "SOURCE_READ_FAILED",
            read.validated() ? List.of(evidenceId) : List.of(), refreshed, read.range(), null, read.limitations()
        );
    }

    private AgentRevalidationResponse validateCurrentness(
        UUID userId,
        UUID projectId,
        AgentRevalidationRequest request
    ) {
        Path root = projectRoot(projectId);
        String before = "";
        String targetId = firstText(request.evidenceId(), request.targetId());
        AgentEvidenceResponse evidence = null;
        if (!firstText(request.evidenceId()).isBlank()) {
            evidence = historyService.evidence(userId, projectId, request.evidenceId());
            before = evidence.sourceRevision();
        } else if (!targetId.isBlank()) {
            ProjectFact fact = factRepository.findByIdAndProjectId(factId(targetId), projectId)
                .orElseThrow(() -> new AppException("FACT_NOT_FOUND", "事实不存在或不属于该项目", HttpStatus.NOT_FOUND));
            before = fact.getRevision();
            targetId = "fact:" + fact.getId();
        }
        String current = currentRevision(root);
        String currentness = revisionCurrentness(before, current);
        List<String> limitations = new ArrayList<>();
        if (current.isBlank()) limitations.add("当前项目没有可用本地 Git Revision；需要按 Evidence range 复验");
        if (before.isBlank()) limitations.add("持久化对象没有绑定 source revision");
        return response(
            projectId, "VALIDATE_CURRENTNESS", targetId, "UNKNOWN".equals(currentness) ? "PARTIAL" : "SUCCEEDED",
            before, current, currentness,
            "POSSIBLY_STALE".equals(currentness) ? "REVISION_MISMATCH" : "REVISION_CHECKED",
            List.of(), evidence, null, null, limitations
        );
    }

    private AgentRevalidationResponse resolvePackage(
        UUID userId,
        UUID projectId,
        AgentRevalidationRequest request
    ) {
        AgentContextPackageResponse context = historyService.contextPackage(
            userId, projectId, request.taskDescription(), request.scope(), request.revisionPreference(),
            request.evidenceDepth(), request.sizeBudget() == null ? 8_000 : request.sizeBudget()
        );
        Path root = projectRoot(projectId);
        String current = currentRevision(root);
        String currentness = revisionCurrentness(context.sourceRevision(), current);
        List<String> limitations = new ArrayList<>();
        if ("POSSIBLY_STALE".equals(currentness)) {
            limitations.add("持久化 Context Package Revision 与当前本地 Revision 不一致；包内强事实前提需局部复验");
        }
        if (current.isBlank()) {
            limitations.add("无法取得当前本地 Git Revision；返回的是持久化 Context Package");
        }
        return response(
            projectId, "RESOLVE_PACKAGE_LATEST", "context-package", "POSSIBLY_STALE".equals(currentness)
                ? "PARTIAL" : "SUCCEEDED",
            context.sourceRevision(), current, currentness,
            "POSSIBLY_STALE".equals(currentness) ? "PACKAGE_REVISION_MISMATCH" : "PACKAGE_REVISION_CHECKED",
            List.of(), null, null, context, limitations
        );
    }

    private EvidenceTarget resolveTarget(
        UUID userId,
        UUID projectId,
        Path root,
        String evidenceRef
    ) {
        if (evidenceRef.startsWith("source:") || evidenceRef.startsWith("tool:")) {
            try {
                AgentEvidenceResponse evidence = historyService.evidence(userId, projectId, evidenceRef);
                return target(root, evidenceRef, evidence.locator());
            } catch (AppException exception) {
                return null;
            }
        }
        if (evidenceRef.startsWith("file:")) {
            String locator = evidenceRef.substring("file:".length());
            int hash = locator.indexOf("#sha256=");
            if (hash >= 0) locator = locator.substring(0, hash);
            return target(root, evidenceRef, locator);
        }
        return null;
    }

    private EvidenceTarget target(Path root, String evidenceId, String locator) {
        if (root == null) return null;
        String relative = safeRelative(locator);
        if (relative.isBlank()) return null;
        Path file = root.resolve(relative).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) return null;
        return new EvidenceTarget(evidenceId, relative, file);
    }

    private RangeRead readTarget(EvidenceTarget target, long start, long end, int maxChars, String kind) {
        if (redactor.isSensitivePath(target.locator())) {
            return new RangeRead(false, null, List.of("敏感文件只允许 metadata，不返回内容"));
        }
        var sample = contentService.readRange(target.path(), start, end, kind, maxChars);
        if (sample.sourceHash().isBlank() || sample.startLine() <= 0) {
            return new RangeRead(false, null, List.of("项目内文件无法完成有界复读"));
        }
        return new RangeRead(true, new RevalidatedRangeResponse(
            target.evidenceId(), target.locator(), sample.kind(), sample.startLine(), sample.endLine(),
            sample.startByte(), sample.endByte(), sample.sourceHash(), sample.truncated(), sample.text()
        ), List.of());
    }

    private boolean verifyCommit(Path root, String commit) {
        if (root == null || !COMMIT.matcher(commit).matches()) return false;
        LocalCommandExecutor.CommandResult result = commandExecutor.execute(
            root, List.of("git", "cat-file", "-e", commit + "^{commit}"), Duration.ofSeconds(3)
        );
        return !result.timedOut() && result.exitCode() == 0;
    }

    private String currentRevision(Path root) {
        if (root == null) return "";
        LocalCommandExecutor.CommandResult result = commandExecutor.execute(
            root, List.of("git", "rev-parse", "--verify", "HEAD"), Duration.ofSeconds(3)
        );
        if (result.timedOut() || result.exitCode() != 0) return "";
        return result.output().lines().map(String::strip).filter(COMMIT.asMatchPredicate()).findFirst().orElse("");
    }

    private Path projectRoot(UUID projectId) {
        return memoryRepository.findByProjectId(projectId)
            .map(value -> value.getLocalProjectPath())
            .filter(value -> value != null && !value.isBlank())
            .map(value -> {
                try {
                    return pathGuard.requireProjectDirectory(value).path().normalize();
                } catch (AppException exception) {
                    return null;
                }
            }).orElse(null);
    }

    private ProjectSpace ownedProject(UUID userId, UUID projectId) {
        return projectRepository.findByIdAndUserId(projectId, userId)
            .orElseThrow(() -> new AppException("PROJECT_NOT_FOUND", "项目不存在", HttpStatus.NOT_FOUND));
    }

    private static UUID factId(String value) {
        String normalized = value == null ? "" : value.strip();
        if (normalized.startsWith("fact:")) normalized = normalized.substring("fact:".length());
        try {
            return UUID.fromString(normalized);
        } catch (RuntimeException exception) {
            throw new AppException("FACT_ID_INVALID", "Fact ID 无效", HttpStatus.BAD_REQUEST);
        }
    }

    private static String normalizeAction(String value) {
        return value == null ? "" : value.strip().toUpperCase(Locale.ROOT).replace('-', '_');
    }

    private static String revisionCurrentness(String before, String current) {
        if (before == null || before.isBlank() || current == null || current.isBlank()) return "UNKNOWN";
        return before.equals(current) || before.startsWith(current) || current.startsWith(before)
            ? "CURRENT" : "POSSIBLY_STALE";
    }

    private static String safeRelative(String value) {
        if (value == null || value.isBlank()) return "";
        String normalized = value.strip().replace('\\', '/');
        if (normalized.startsWith("/") || normalized.matches("^[A-Za-z]:/.*") || normalized.contains("../")) return "";
        return normalized;
    }

    private static long value(Long value, long fallback) {
        return value == null ? fallback : value;
    }

    private static String firstText(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value.strip();
        return "";
    }

    private static AgentRevalidationResponse response(
        UUID projectId,
        String action,
        String targetId,
        String status,
        String sourceRevisionBefore,
        String currentSourceRevision,
        String currentness,
        String validationStatus,
        List<String> verifiedEvidenceRefs,
        AgentEvidenceResponse evidence,
        RevalidatedRangeResponse range,
        AgentContextPackageResponse context,
        List<String> limitations
    ) {
        return new AgentRevalidationResponse(
            projectId, action, targetId, status, sourceRevisionBefore, currentSourceRevision,
            currentness, validationStatus, List.copyOf(new LinkedHashSet<>(verifiedEvidenceRefs)),
            evidence, range, context, List.copyOf(new LinkedHashSet<>(limitations)), Instant.now()
        );
    }

    private record EvidenceTarget(String evidenceId, String locator, Path path) {
    }

    private record RangeRead(
        boolean validated,
        RevalidatedRangeResponse range,
        List<String> limitations
    ) {
    }
}
