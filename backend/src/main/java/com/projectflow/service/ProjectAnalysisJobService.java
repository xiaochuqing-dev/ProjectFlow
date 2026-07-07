package com.projectflow.service;

import java.util.List;
import java.util.UUID;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.dto.V2ProjectDtos.CapabilityInterpretResponse;
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
        ProjectAnalysisJobStatus.RUNNING
    );

    private final ProjectAnalysisJobRepository jobRepository;
    private final ProjectRepository projectRepository;
    private final ProjectAnalysisJobRunner jobRunner;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

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
    }

    public ProjectAnalysisJobResponse startProjectAnalysis(UUID userId, UUID projectId) {
        StartJobResult result = transactionTemplate.execute(status -> {
            findOwnedProject(userId, projectId);
            java.util.Optional<ProjectAnalysisJob> active = activeJob(projectId, ProjectAnalysisJobType.PROJECT, null);
            return active
                .map(job -> new StartJobResult(job, false))
                .orElseGet(() -> new StartJobResult(jobRepository.save(new ProjectAnalysisJob(projectId, userId, ProjectAnalysisJobType.PROJECT, null)), true));
        });
        if (result.created()) {
            jobRunner.execute(result.job().getId());
        }
        return toResponse(result.job());
    }

    public ProjectAnalysisJobResponse startFileAnalysis(UUID userId, UUID projectId, String path) {
        String normalizedPath = path.trim();
        StartJobResult result = transactionTemplate.execute(status -> {
            findOwnedProject(userId, projectId);
            java.util.Optional<ProjectAnalysisJob> active = activeJob(projectId, ProjectAnalysisJobType.FILE, normalizedPath);
            return active
                .map(job -> new StartJobResult(job, false))
                .orElseGet(() -> new StartJobResult(jobRepository.save(new ProjectAnalysisJob(projectId, userId, ProjectAnalysisJobType.FILE, normalizedPath)), true));
        });
        if (result.created()) {
            jobRunner.execute(result.job().getId());
        }
        return toResponse(result.job());
    }

    public ProjectAnalysisJobResponse startCapabilityInterpret(UUID userId, UUID projectId, String capabilityFact) {
        String fact = capabilityFact == null ? "" : capabilityFact.trim();
        StartJobResult result = transactionTemplate.execute(status -> {
            findOwnedProject(userId, projectId);
            java.util.Optional<ProjectAnalysisJob> active = activeJob(projectId, ProjectAnalysisJobType.CAPABILITY_INTERPRET, fact);
            return active
                .map(job -> new StartJobResult(job, false))
                .orElseGet(() -> new StartJobResult(jobRepository.save(new ProjectAnalysisJob(projectId, userId, ProjectAnalysisJobType.CAPABILITY_INTERPRET, fact)), true));
        });
        if (result.created()) {
            jobRunner.execute(result.job().getId());
        }
        return toResponse(result.job());
    }

    public ProjectAnalysisJobResponse startWorkSessionScan(UUID userId, UUID projectId) {
        StartJobResult result = transactionTemplate.execute(status -> {
            findOwnedProject(userId, projectId);
            java.util.Optional<ProjectAnalysisJob> active = activeJob(projectId, ProjectAnalysisJobType.WORK_SESSION_SCAN, null);
            return active
                .map(job -> new StartJobResult(job, false))
                .orElseGet(() -> new StartJobResult(jobRepository.save(new ProjectAnalysisJob(projectId, userId, ProjectAnalysisJobType.WORK_SESSION_SCAN, null)), true));
        });
        if (result.created()) {
            jobRunner.execute(result.job().getId());
        }
        return toResponse(result.job());
    }

    @Transactional(readOnly = true)
    public ProjectAnalysisJobResponse getJob(UUID userId, UUID jobId) {
        return toResponse(findOwnedJob(userId, jobId));
    }

    @Transactional(readOnly = true)
    public List<ProjectAnalysisJobResponse> listProjectJobs(UUID userId, UUID projectId) {
        ProjectSpace project = findOwnedProject(userId, projectId);
        return jobRepository.findByProjectIdOrderByCreatedAtDesc(project.getId())
            .stream()
            .map(this::toResponse)
            .toList();
    }

    // 服务重启后，残留的 QUEUED/RUNNING 任务已无法继续执行（异步线程已消失）。
    // 不自动重跑——用户没有点击就不应触发分析。把它们标记为 FAILED，前端展示中断状态，
    // 用户可自行重新点击分析按钮。
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void markInterruptedJobsFailed() {
        jobRepository.findAll().stream()
            .filter(job -> ACTIVE_STATUSES.contains(job.getStatus()))
            .forEach(job -> {
                job.markFailed("服务重启，分析任务已中断，请重新点击分析。");
                jobRepository.save(job);
            });
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

    private ProjectAnalysisJobResponse toResponse(ProjectAnalysisJob job) {
        ProjectAnalysisResponse projectResult = null;
        ProjectFileAnalysisResponse fileResult = null;
        CapabilityInterpretResponse capabilityInterpretResult = null;
        WorkSessionScanResponse workSessionScanResult = null;
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
            job.getRecordId(),
            job.getCreatedAt(),
            job.getUpdatedAt(),
            job.getStartedAt(),
            job.getCompletedAt(),
            job.getStage(),
            job.getStageMessage(),
            job.getCurrentStepStartedAt(),
            job.getInputSummary()
        );
    }

    private boolean containsProjectNoise(ProjectAnalysisResponse response) {
        return containsProjectNoise(response.summary())
            || containsProjectNoise(response.architecture())
            || response.modules().stream().anyMatch(this::containsProjectNoise)
            || response.risks().stream().anyMatch(this::containsProjectNoise)
            || response.importantFiles().stream().anyMatch(this::containsProjectNoise)
            || response.evidence().stream().anyMatch(this::containsProjectNoise)
            || response.limitations().stream().anyMatch(this::containsProjectNoise);
    }

    private boolean containsProjectNoise(ProjectFileAnalysisResponse response) {
        return containsProjectNoise(response.path())
            || containsProjectNoise(response.role())
            || containsProjectNoise(response.summary())
            || containsProjectNoise(response.riskNotes())
            || response.evidence().stream().anyMatch(this::containsProjectNoise)
            || response.relatedFiles().stream().anyMatch(this::containsProjectNoise)
            || containsProjectNoise(response.limitations());
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
