package com.projectflow.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.dto.V2ProjectDtos.CapabilityInterpretResponse;
import com.projectflow.dto.V2ProjectDtos.CapabilityAnalysisJobResult;
import com.projectflow.dto.V2ProjectDtos.ProjectAnalysisJobResponse;
import com.projectflow.dto.V2ProjectDtos.ProjectAnalysisResponse;
import com.projectflow.dto.V2ProjectDtos.ProjectFileAnalysisResponse;
import com.projectflow.dto.V2ProjectDtos.WorkSessionScanResponse;
import com.projectflow.entity.ProjectAnalysisJob;
import com.projectflow.entity.ProjectAnalysisJobStatus;
import com.projectflow.entity.ProjectAnalysisJobType;
import com.projectflow.entity.ProjectSpace;
import com.projectflow.repository.ProjectAnalysisJobRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.support.AppException;

@Service
public class ProjectAnalysisJobService {
    private static final List<ProjectAnalysisJobStatus> ACTIVE_STATUSES = List.of(
        ProjectAnalysisJobStatus.QUEUED,
        ProjectAnalysisJobStatus.RUNNING,
        ProjectAnalysisJobStatus.CANCEL_REQUESTED
    );

    private final ProjectAnalysisJobRepository jobRepository;
    private final ProjectRepository projectRepository;
    private final ProjectAnalysisJobRunner jobRunner;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    @Value("${projectflow.jobs.global-active-limit:20}")
    private int globalActiveLimit = 20;

    public ProjectAnalysisJobService(
        ProjectAnalysisJobRepository jobRepository,
        ProjectRepository projectRepository,
        ProjectAnalysisJobRunner jobRunner,
        ObjectMapper objectMapper,
        PlatformTransactionManager transactionManager
    ) {
        this.jobRepository = jobRepository;
        this.projectRepository = projectRepository;
        this.jobRunner = jobRunner;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(
            org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW
        );
    }

    public ProjectAnalysisJobResponse startProjectAnalysis(UUID userId, UUID projectId) {
        return startJob(userId, projectId, ProjectAnalysisJobType.PROJECT, null, null, null);
    }

    public ProjectAnalysisJobResponse startFileAnalysis(UUID userId, UUID projectId, String path) {
        String normalizedPath = path.trim();
        return startJob(userId, projectId, ProjectAnalysisJobType.FILE, normalizedPath, null, null);
    }

    public ProjectAnalysisJobResponse startCapabilityInterpret(UUID userId, UUID projectId, String capabilityFact) {
        String fact = capabilityFact == null ? "" : capabilityFact.trim();
        return startJob(userId, projectId, ProjectAnalysisJobType.CAPABILITY_INTERPRET, fact, null, null);
    }

    public ProjectAnalysisJobResponse startWorkSessionScan(UUID userId, UUID projectId) {
        return startJob(userId, projectId, ProjectAnalysisJobType.WORK_SESSION_SCAN, null, null, null);
    }

    public ProjectAnalysisJobResponse startProjectFactHistoryRebuild(UUID userId, UUID projectId, String upperBoundSha) {
        String upperBound = upperBoundSha == null ? "" : upperBoundSha.trim();
        return startJob(userId, projectId, ProjectAnalysisJobType.PROJECT_FACT_HISTORY_REBUILD, upperBound, null, null);
    }

    // V3.3.4: 能力分析异步任务。点击"分析项目能力"创建 job，后端异步执行；刷新/离开页面后可恢复。
    public ProjectAnalysisJobResponse startCapabilityCardAnalysis(UUID userId, UUID projectId) {
        return startJob(userId, projectId, ProjectAnalysisJobType.CAPABILITY_CARD_ANALYSIS, null, null, null);
    }

    public ProjectAnalysisJobResponse startTimelineRefresh(UUID userId, UUID projectId, String scope) {
        String normalizedScope = scope == null ? "" : scope.trim();
        return startJob(userId, projectId, ProjectAnalysisJobType.PROJECT_TIMELINE_REFRESH, normalizedScope, null, null);
    }

    public ProjectAnalysisJobResponse cancel(UUID userId, UUID jobId) {
        ProjectAnalysisJob job = transactionTemplate.execute(status -> {
            ProjectAnalysisJob current = findOwnedJob(userId, jobId);
            if (current.getStatus() == ProjectAnalysisJobStatus.QUEUED) current.markCancelled();
            else if (current.getStatus() == ProjectAnalysisJobStatus.RUNNING) current.requestCancellation();
            return jobRepository.save(current);
        });
        return toResponse(job);
    }

    public ProjectAnalysisJobResponse retry(UUID userId, UUID jobId) {
        ProjectAnalysisJob previous = findOwnedJob(userId, jobId);
        if (ACTIVE_STATUSES.contains(previous.getStatus())) return toResponse(previous);
        if (previous.getStatus() == ProjectAnalysisJobStatus.SUCCEEDED
            || previous.getStatus() == ProjectAnalysisJobStatus.SUCCEEDED_WITH_WARNINGS) {
            throw new AppException("ANALYSIS_JOB_ALREADY_SUCCEEDED", "任务已成功，无需重试", HttpStatus.CONFLICT);
        }
        return startJob(
            userId,
            previous.getProjectId(),
            previous.getJobType(),
            previous.getFilePath(),
            previous.getId(),
            "USER_RETRY"
        );
    }

    private ProjectAnalysisJobResponse startJob(
        UUID userId,
        UUID projectId,
        ProjectAnalysisJobType type,
        String input,
        UUID retriedFromJobId,
        String retryReason
    ) {
        String fingerprint = fingerprint(type, input);
        StartJobResult result = transactionTemplate.execute(status -> {
            findOwnedProjectForUpdate(userId, projectId);
            java.util.Optional<ProjectAnalysisJob> active = jobRepository
                .findFirstByProjectIdAndJobTypeAndInputFingerprintAndStatusInOrderByCreatedAtDesc(
                    projectId, type, fingerprint, ACTIVE_STATUSES
                );
            if (active.isPresent()) return new StartJobResult(active.get(), false);
            // 兼容 V3.3.6 尚无指纹的活动任务。retry、重新分析和普通创建都不能绕过该检查。
            active = activeJob(projectId, type, input);
            if (active.isPresent()) return new StartJobResult(active.get(), false);
            long activeCount = jobRepository.countByStatusIn(ACTIVE_STATUSES);
            ProjectAnalysisJob job = new ProjectAnalysisJob(projectId, userId, type, input);
            // projectId and type are already persisted and participate in active-job lookup.
            // Keeping the idempotency token bounded avoids PostgreSQL rejecting long enum names.
            job.configureExecution(fingerprint, type + ":" + fingerprint, (int) activeCount);
            if (type == ProjectAnalysisJobType.PROJECT_TIMELINE_REFRESH) {
                job.configureBudgets(48, 400_000, 600_000L);
            }
            if (retriedFromJobId != null) job.configureRetry(retriedFromJobId, retryReason);
            if (activeCount >= globalActiveLimit) {
                job.markRejected("当前分析任务过多，请稍后重试。未发起模型请求。");
            }
            return new StartJobResult(jobRepository.save(job), activeCount < globalActiveLimit);
        });
        if (result.created()) enqueue(result.job());
        return toResponse(jobRepository.findById(result.job().getId()).orElse(result.job()));
    }

    private void enqueue(ProjectAnalysisJob job) {
        try {
            jobRunner.execute(job.getId());
        } catch (RuntimeException exception) {
            transactionTemplate.executeWithoutResult(status -> jobRepository.findById(job.getId()).ifPresent(current -> {
                if (!current.isTerminal()) {
                    current.markRejected("执行队列已满，请稍后重试。未发起模型请求。");
                    jobRepository.save(current);
                }
            }));
        }
    }

    @Transactional(readOnly = true)
    public ProjectAnalysisJobResponse getJob(UUID userId, UUID jobId) {
        return toResponse(findOwnedJob(userId, jobId));
    }

    @Transactional
    public ProjectAnalysisJobResponse acknowledgeFailure(UUID userId, UUID jobId) {
        ProjectAnalysisJob job = findOwnedJob(userId, jobId);
        if (job.getStatus() == ProjectAnalysisJobStatus.FAILED) {
            job.acknowledgeFailure();
            jobRepository.save(job);
        }
        return toResponse(job);
    }

    @Transactional(readOnly = true)
    public List<ProjectAnalysisJobResponse> listProjectJobs(UUID userId, UUID projectId) {
        ProjectSpace project = findOwnedProject(userId, projectId);
        return jobRepository.findByProjectIdOrderByCreatedAtDesc(project.getId())
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(100)
    public void recoverInterruptedJobs() {
        List<UUID> queued = transactionTemplate.execute(status -> jobRepository.findAll().stream()
            .filter(job -> ACTIVE_STATUSES.contains(job.getStatus()))
            .peek(job -> {
                if (job.getStatus() == ProjectAnalysisJobStatus.QUEUED) return;
                if (job.getStatus() == ProjectAnalysisJobStatus.CANCEL_REQUESTED) {
                    job.markCancelled();
                } else if (modelMayHaveBeenCalled(job.getStage())) {
                    job.markInterrupted(false, "服务重启时模型请求状态未知，未自动重发以避免重复计费。请确认后重新运行。");
                } else {
                    job.markInterrupted(true, "服务重启中断了任务，尚未产生模型费用，可以安全重新运行。");
                }
                jobRepository.save(job);
            })
            .filter(job -> job.getStatus() == ProjectAnalysisJobStatus.QUEUED)
            .map(ProjectAnalysisJob::getId)
            .toList());
        if (queued != null) queued.forEach(id -> jobRepository.findById(id).ifPresent(this::enqueue));
    }

    private boolean modelMayHaveBeenCalled(String stage) {
        if (stage == null) return false;
        return stage.contains("MODEL") || stage.contains("PERSIST") || stage.contains("DATABASE");
    }

    private java.util.Optional<ProjectAnalysisJob> activeJob(UUID projectId, ProjectAnalysisJobType type, String path) {
        return jobRepository.findFirstByProjectIdAndJobTypeAndFilePathAndStatusInOrderByCreatedAtDesc(
            projectId,
            type,
            path,
            ACTIVE_STATUSES
        );
    }

    private ProjectAnalysisJob findOwnedJob(UUID userId, UUID jobId) {
        ProjectAnalysisJob job = jobRepository.findById(jobId)
            .orElseThrow(() -> new AppException("PROJECT_ANALYSIS_JOB_NOT_FOUND", "分析任务不存在", HttpStatus.NOT_FOUND));
        if (!job.getUserId().equals(userId)) {
            throw new AppException("PROJECT_ANALYSIS_JOB_NOT_FOUND", "分析任务不存在", HttpStatus.NOT_FOUND);
        }
        return job;
    }

    private ProjectSpace findOwnedProject(UUID userId, UUID projectId) {
        return projectRepository.findByIdAndUserId(projectId, userId)
            .orElseThrow(() -> new AppException("PROJECT_NOT_FOUND", "项目不存在", HttpStatus.NOT_FOUND));
    }

    private ProjectSpace findOwnedProjectForUpdate(UUID userId, UUID projectId) {
        return projectRepository.findLockedByIdAndUserId(projectId, userId)
            .orElseThrow(() -> new AppException("PROJECT_NOT_FOUND", "项目不存在", HttpStatus.NOT_FOUND));
    }

    private String fingerprint(ProjectAnalysisJobType type, String input) {
        String normalized = type + "\n" + (input == null ? "" : input.trim());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 Java 环境不支持 SHA-256", exception);
        }
    }

    private ProjectAnalysisJobResponse toResponse(ProjectAnalysisJob job) {
        ProjectAnalysisResponse projectResult = null;
        ProjectFileAnalysisResponse fileResult = null;
        CapabilityInterpretResponse capabilityInterpretResult = null;
        WorkSessionScanResponse workSessionScanResult = null;
        CapabilityAnalysisJobResult capabilityCardResult = null;
        String errorMessage = job.getErrorMessage();
        if (job.getResultJson() != null && !job.getResultJson().isBlank()) {
            try {
                if (job.getJobType() == ProjectAnalysisJobType.PROJECT) {
                    projectResult = objectMapper.readValue(job.getResultJson(), ProjectAnalysisResponse.class);
                    if (containsProjectNoise(projectResult)) {
                        projectResult = null;
                        errorMessage = "旧分析结果包含 .codex-run、old-git 或 Git 内部对象，已失效；请重新分析。";
                    }
                } else if (job.getJobType() == ProjectAnalysisJobType.CAPABILITY_INTERPRET) {
                    capabilityInterpretResult = objectMapper.readValue(job.getResultJson(), CapabilityInterpretResponse.class);
                } else if (job.getJobType() == ProjectAnalysisJobType.WORK_SESSION_SCAN) {
                    workSessionScanResult = objectMapper.readValue(job.getResultJson(), WorkSessionScanResponse.class);
                } else if (job.getJobType() == ProjectAnalysisJobType.CAPABILITY_CARD_ANALYSIS) {
                    var summary = objectMapper.readTree(job.getResultJson());
                    capabilityCardResult = new CapabilityAnalysisJobResult(
                        summary.path("cardCount").asInt(0), summary.path("needsEvidenceCount").asInt(0),
                        summary.path("rawResponsePresent").asBoolean(false), summary.path("repaired").asBoolean(false),
                        summary.path("recognizedItems").asInt(0), summary.path("discardedItems").asInt(0),
                        summary.path("invalidSourceIndexes").asInt(0), summary.path("providerName").asText(""),
                        summary.path("modelName").asText(""), summary.path("finishReason").asText(""),
                        summary.path("promptTokens").asInt(0), summary.path("completionTokens").asInt(0),
                        summary.path("totalTokens").asInt(0), summary.path("providerMaxTokens").asInt(0),
                        summary.path("taskPolicyMaxTokens").asInt(0), summary.path("effectiveMaxTokens").asInt(0),
                        summary.path("providerTemperature").asDouble(0), summary.path("effectiveTemperature").asDouble(0),
                        summary.path("timeoutSeconds").asLong(0), summary.path("requestLatencyMs").asLong(0),
                        summary.path("outputTruncated").asBoolean(false), summary.path("compactRetryAttempted").asBoolean(false),
                        summary.path("compactRetrySucceeded").asBoolean(false), summary.path("partialResult").asBoolean(false),
                        summary.path("recoveredItems").asInt(0), summary.path("capabilityProfile").asText(""),
                        summary.path("recommendedTemperature").asDouble(0), summary.path("temperatureSent").asBoolean(false),
                        summary.path("temperatureDecision").asText(""), summary.path("maxTokenDecision").asText(""),
                        summary.path("retryType").asText("NONE"), summary.path("reasoningBudgetExhausted").asBoolean(false),
                        summary.path("schemaMatched").asBoolean(false), summary.path("failureCode").asText(""),
                        summary.path("requestCount").asInt(0)
                    );
                } else {
                    fileResult = objectMapper.readValue(job.getResultJson(), ProjectFileAnalysisResponse.class);
                    if (containsProjectNoise(fileResult)) {
                        fileResult = null;
                        errorMessage = "旧文件分析结果包含工具目录噪声，已失效；请重新分析。";
                    }
                }
            } catch (JsonProcessingException exception) {
                throw new AppException("PROJECT_ANALYSIS_RESULT_INVALID", "分析结果无法读取，请重新运行", HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }
        return new ProjectAnalysisJobResponse(
            job.getId(),
            job.getProjectId(),
            job.getJobType(),
            job.getFilePath(),
            job.getStatus(),
            projectResult,
            fileResult,
            capabilityInterpretResult,
            workSessionScanResult,
            errorMessage,
            job.getWarningMessage(),
            job.getFailureStage(),
            capabilityCardResult,
            job.getRecordId(),
            job.getCreatedAt(),
            job.getUpdatedAt(),
            job.getStartedAt(),
            job.getCompletedAt(),
            job.getStage(),
            job.getStageMessage(),
            job.getCurrentStepStartedAt(),
            job.getInputSummary(),
            job.getDiagnosticsJson(),
            job.isModelReturned(),
            job.isFailureAcknowledged(),
            job.getQueuedAt(),
            job.getHeartbeatAt(),
            job.getCancellationRequestedAt(),
            job.getCancelledAt(),
            job.getAttemptCount(),
            job.getMaxAttempts(),
            job.getRequestCount(),
            job.getMaxRequestCount(),
            job.getPromptTokens(),
            job.getCompletionTokens(),
            job.getTotalTokens(),
            job.getMaxTotalTokens(),
            job.getElapsedMs(),
            job.getMaxDurationMs(),
            job.getIdempotencyKey(),
            job.getInputFingerprint(),
            job.getFailureCode(),
            job.getRestartRecoveryState(),
            job.getQueuePosition(),
            job.getRetriedFromJobId(),
            job.getRetryReason()
        );
    }

    private boolean containsProjectNoise(ProjectAnalysisResponse response) {
        return containsProjectNoise(response.summary())
            || containsProjectNoise(response.architecture())
            || safeList(response.modules()).stream().anyMatch(this::containsProjectNoise)
            || safeList(response.risks()).stream().anyMatch(this::containsProjectNoise)
            || safeList(response.importantFiles()).stream().anyMatch(this::containsProjectNoise)
            || safeList(response.evidence()).stream().anyMatch(this::containsProjectNoise)
            || safeList(response.limitations()).stream().anyMatch(this::containsProjectNoise);
    }

    private boolean containsProjectNoise(ProjectFileAnalysisResponse response) {
        return containsProjectNoise(response.path())
            || containsProjectNoise(response.role())
            || containsProjectNoise(response.summary())
            || containsProjectNoise(response.riskNotes())
            || safeList(response.evidence()).stream().anyMatch(this::containsProjectNoise)
            || safeList(response.relatedFiles()).stream().anyMatch(this::containsProjectNoise)
            || containsProjectNoise(response.limitations());
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }

    private boolean containsProjectNoise(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String lower = value.toLowerCase().replace("\\", "/");
        return lower.contains(".codex-run/")
            || lower.contains("old-git-")
            || lower.contains(".git/objects/")
            || lower.contains(".git/config")
            || lower.contains(".git/head");
    }

    private record StartJobResult(ProjectAnalysisJob job, boolean created) {
    }
}
