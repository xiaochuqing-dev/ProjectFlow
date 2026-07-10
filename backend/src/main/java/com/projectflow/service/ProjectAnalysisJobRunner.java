package com.projectflow.service;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.dto.V2ProjectDtos.CapabilityInterpretRequest;
import com.projectflow.dto.V2ProjectDtos.CapabilityInterpretResponse;
import com.projectflow.dto.V2ProjectDtos.CapabilityAnalysisJobResult;
import com.projectflow.dto.V2ProjectDtos.ProjectAnalysisResponse;
import com.projectflow.dto.V2ProjectDtos.ProjectFileAnalysisRequest;
import com.projectflow.dto.V2ProjectDtos.ProjectFileAnalysisResponse;
import com.projectflow.dto.V2ProjectDtos.WorkSessionScanResponse;
import com.projectflow.entity.ModelUsageRecord;
import com.projectflow.entity.ProjectAnalysisJob;
import com.projectflow.entity.ProjectAnalysisJobStatus;
import com.projectflow.entity.ProjectAnalysisJobType;
import com.projectflow.repository.ModelUsageRecordRepository;
import com.projectflow.repository.ProjectAnalysisJobRepository;

@Service
public class ProjectAnalysisJobRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProjectAnalysisJobRunner.class);

    private final ProjectAnalysisJobRepository jobRepository;
    private final ModelUsageRecordRepository modelUsageRecordRepository;
    private final ProjectAnalysisService projectAnalysisService;
    private final ProjectAnalysisRecordService projectAnalysisRecordService;
    private final ProjectMemoryService projectMemoryService;
    private final WorkSessionScanService workSessionScanService;
    private final ProjectCapabilityService projectCapabilityService;
    private final ObjectMapper objectMapper;

    public ProjectAnalysisJobRunner(
        ProjectAnalysisJobRepository jobRepository,
        ModelUsageRecordRepository modelUsageRecordRepository,
        ProjectAnalysisService projectAnalysisService,
        ProjectAnalysisRecordService projectAnalysisRecordService,
        ProjectMemoryService projectMemoryService,
        WorkSessionScanService workSessionScanService,
        ProjectCapabilityService projectCapabilityService,
        ObjectMapper objectMapper
    ) {
        this.jobRepository = jobRepository;
        this.modelUsageRecordRepository = modelUsageRecordRepository;
        this.projectAnalysisService = projectAnalysisService;
        this.projectAnalysisRecordService = projectAnalysisRecordService;
        this.projectMemoryService = projectMemoryService;
        this.workSessionScanService = workSessionScanService;
        this.projectCapabilityService = projectCapabilityService;
        this.objectMapper = objectMapper;
    }

    @Async
    public void execute(UUID jobId) {
        ProjectAnalysisJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null || job.getStatus() == ProjectAnalysisJobStatus.SUCCEEDED
            || job.getStatus() == ProjectAnalysisJobStatus.SUCCEEDED_WITH_WARNINGS
            || job.getStatus() == ProjectAnalysisJobStatus.FAILED) {
            return;
        }

        long startedAt = System.nanoTime();
        try {
            job.markRunning();
            jobRepository.save(job);

            if (job.getJobType() == ProjectAnalysisJobType.PROJECT) {
                ProjectAnalysisResponse result = projectAnalysisService.runProjectAnalysis(job.getUserId(), job.getProjectId());
                UUID recordId = projectAnalysisRecordService.createProjectAnalysisRecord(job.getUserId(), job.getProjectId(), result);
                String resultJson = objectMapper.writeValueAsString(result);
                markSucceeded(jobId, resultJson, recordId);
                recordUsage(job, "PROJECT_ANALYSIS", result.providerName(), result.modelUsed(), resultJson, startedAt);
            } else if (job.getJobType() == ProjectAnalysisJobType.CAPABILITY_INTERPRET) {
                CapabilityInterpretResponse result = projectMemoryService.interpretCapability(
                    job.getUserId(),
                    job.getProjectId(),
                    new CapabilityInterpretRequest(job.getFilePath() == null ? "" : job.getFilePath())
                );
                String resultJson = objectMapper.writeValueAsString(result);
                markSucceeded(jobId, resultJson, null);
                recordUsage(job, "CAPABILITY_INTERPRET", result.source(), !result.degraded(), resultJson, startedAt);
            } else if (job.getJobType() == ProjectAnalysisJobType.WORK_SESSION_SCAN) {
                WorkSessionScanResponse result = workSessionScanService.scan(job.getUserId(), job.getProjectId(), job.getId());
                String resultJson = objectMapper.writeValueAsString(result);
                markSucceeded(jobId, resultJson, null);
            } else if (job.getJobType() == ProjectAnalysisJobType.CAPABILITY_CARD_ANALYSIS) {
                // V3.3.4: 能力分析异步化。卡片由 service 持久化，job 记录阶段与完成状态。
                // resultJson 只存简要摘要，前端完成后重新拉取 capability-cards。
                var outcome = projectCapabilityService.analyzeWithOutcome(job.getUserId(), job.getProjectId(), job.getId());
                var diagnostics = outcome.diagnostics();
                String resultJson = objectMapper.writeValueAsString(new CapabilityAnalysisJobResult(
                    outcome.cards().size(), outcome.needsEvidenceCount(), diagnostics.rawResponsePresent(), diagnostics.repaired(),
                    diagnostics.recognizedItems(), diagnostics.discardedItems(), diagnostics.invalidSourceIndexes()
                ));
                if (outcome.hasWarnings()) {
                    java.util.List<String> warningParts = new java.util.ArrayList<>();
                    if (outcome.needsEvidenceCount() > 0) warningParts.add(outcome.needsEvidenceCount() + " 张卡片需要补充证据");
                    if (diagnostics.repaired()) warningParts.add("模型返回格式已自动修复");
                    if (diagnostics.discardedItems() > 0) warningParts.add(diagnostics.discardedItems() + " 个无效或重复项已过滤");
                    if (diagnostics.invalidSourceIndexes() > 0) warningParts.add(diagnostics.invalidSourceIndexes() + " 个无效来源编号已忽略");
                    if (outcome.cards().size() < 3) warningParts.add("本次生成的有效能力较少");
                    String warning = "能力分析已完成，" + String.join("；", warningParts) + "。";
                    markSucceededWithWarnings(jobId, resultJson, warning);
                } else {
                    markSucceeded(jobId, resultJson, null);
                }
            } else {
                ProjectFileAnalysisResponse result = projectAnalysisService.analyzeProjectFile(
                    job.getUserId(),
                    job.getProjectId(),
                    new ProjectFileAnalysisRequest(job.getFilePath())
                );
                UUID recordId = projectAnalysisRecordService.createFileAnalysisRecord(job.getUserId(), job.getProjectId(), result);
                String resultJson = objectMapper.writeValueAsString(result);
                markSucceeded(jobId, resultJson, recordId);
                recordUsage(job, "FILE_ANALYSIS", result.providerName(), result.modelUsed(), resultJson, startedAt);
            }
        } catch (Exception exception) {
            LOGGER.warn("Project analysis job failed: jobId={}", jobId, exception);
            String failureStage = exception instanceof ProjectCapabilityService.CapabilityAnalysisException capabilityException
                ? capabilityException.stage() : null;
            markFailed(jobId, safeErrorMessage(exception), failureStage);
            recordFailedUsage(job, exception, startedAt);
        }
    }

    private void markSucceeded(UUID jobId, String resultJson, UUID recordId) {
        jobRepository.findById(jobId).ifPresent(job -> {
            job.markSucceeded(resultJson, recordId);
            jobRepository.save(job);
        });
    }

    private void markFailed(UUID jobId, String message) {
        markFailed(jobId, message, null);
    }

    private void markFailed(UUID jobId, String message, String failureStage) {
        jobRepository.findById(jobId).ifPresent(job -> {
            if (failureStage != null && !failureStage.isBlank()) job.advanceStage(failureStage, message);
            job.markFailed(message);
            jobRepository.save(job);
        });
    }

    private void markSucceededWithWarnings(UUID jobId, String resultJson, String warning) {
        jobRepository.findById(jobId).ifPresent(job -> {
            job.markSucceededWithWarnings(resultJson, null, warning);
            jobRepository.save(job);
        });
    }

    private void recordUsage(
        ProjectAnalysisJob job,
        String operation,
        String providerName,
        boolean modelUsed,
        String resultJson,
        long startedAt
    ) {
        modelUsageRecordRepository.save(new ModelUsageRecord(
            job.getProjectId(),
            operation,
            safeProviderName(providerName),
            modelUsed ? "configured-model" : "local-rule",
            0,
            estimateTokens(resultJson),
            true,
            latencyMs(startedAt),
            "SUCCEEDED",
            null,
            null,
            qualityWarnings(resultJson)
        ));
    }

    private void recordFailedUsage(ProjectAnalysisJob job, Exception exception, long startedAt) {
        String operation = switch (job.getJobType()) {
            case PROJECT -> "PROJECT_ANALYSIS";
            case CAPABILITY_INTERPRET -> "CAPABILITY_INTERPRET";
            case WORK_SESSION_SCAN -> "WORK_SESSION_SCAN";
            case CAPABILITY_CARD_ANALYSIS -> "CAPABILITY_CARD_ANALYSIS";
            case FILE -> "FILE_ANALYSIS";
        };
        modelUsageRecordRepository.save(new ModelUsageRecord(
            job.getProjectId(),
            operation,
            "unknown",
            "unknown",
            0,
            0,
            true,
            latencyMs(startedAt),
            "FAILED",
            exception.getClass().getSimpleName(),
            safeErrorMessage(exception),
            null
        ));
    }

    private String safeProviderName(String providerName) {
        return providerName == null || providerName.isBlank() ? "local-rule" : providerName;
    }

    private int estimateTokens(String content) {
        if (content == null || content.isBlank()) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(content.length() / 4.0));
    }

    private long latencyMs(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000);
    }

    private String qualityWarnings(String content) {
        if (content == null || content.chars().noneMatch(character -> Character.UnicodeScript.of(character) == Character.UnicodeScript.HAN)) {
            return "分析结果缺少中文内容";
        }
        return null;
    }

    private String safeErrorMessage(Exception exception) {
        if (exception instanceof JsonProcessingException) {
            return "分析结果保存失败，请重新运行。";
        }
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "分析任务执行失败：" + exception.getClass().getSimpleName();
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
