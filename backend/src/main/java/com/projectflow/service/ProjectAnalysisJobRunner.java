package com.projectflow.service;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.dto.V2ProjectDtos.ProjectAnalysisResponse;
import com.projectflow.dto.V2ProjectDtos.ProjectFileAnalysisRequest;
import com.projectflow.dto.V2ProjectDtos.ProjectFileAnalysisResponse;
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
    private final ProjectIntelligenceService projectIntelligenceService;
    private final ObjectMapper objectMapper;

    public ProjectAnalysisJobRunner(
        ProjectAnalysisJobRepository jobRepository,
        ModelUsageRecordRepository modelUsageRecordRepository,
        ProjectIntelligenceService projectIntelligenceService,
        ObjectMapper objectMapper
    ) {
        this.jobRepository = jobRepository;
        this.modelUsageRecordRepository = modelUsageRecordRepository;
        this.projectIntelligenceService = projectIntelligenceService;
        this.objectMapper = objectMapper;
    }

    @Async
    public void execute(UUID jobId) {
        ProjectAnalysisJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null || job.getStatus() == ProjectAnalysisJobStatus.SUCCEEDED || job.getStatus() == ProjectAnalysisJobStatus.FAILED) {
            return;
        }

        long startedAt = System.nanoTime();
        try {
            job.markRunning();
            jobRepository.save(job);

            if (job.getJobType() == ProjectAnalysisJobType.PROJECT) {
                ProjectAnalysisResponse result = projectIntelligenceService.runProjectAnalysis(job.getUserId(), job.getProjectId());
                UUID recordId = projectIntelligenceService.createProjectAnalysisRecord(job.getUserId(), job.getProjectId(), result);
                String resultJson = objectMapper.writeValueAsString(result);
                markSucceeded(jobId, resultJson, recordId);
                recordUsage(job, "PROJECT_ANALYSIS", result.providerName(), result.modelUsed(), resultJson, startedAt);
            } else {
                ProjectFileAnalysisResponse result = projectIntelligenceService.analyzeProjectFile(
                    job.getUserId(),
                    job.getProjectId(),
                    new ProjectFileAnalysisRequest(job.getFilePath())
                );
                UUID recordId = projectIntelligenceService.createFileAnalysisRecord(job.getUserId(), job.getProjectId(), result);
                String resultJson = objectMapper.writeValueAsString(result);
                markSucceeded(jobId, resultJson, recordId);
                recordUsage(job, "FILE_ANALYSIS", result.providerName(), result.modelUsed(), resultJson, startedAt);
            }
        } catch (Exception exception) {
            LOGGER.warn("Project analysis job failed: jobId={}", jobId, exception);
            markFailed(jobId, safeErrorMessage(exception));
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
        jobRepository.findById(jobId).ifPresent(job -> {
            job.markFailed(message);
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
        modelUsageRecordRepository.save(new ModelUsageRecord(
            job.getProjectId(),
            job.getJobType() == ProjectAnalysisJobType.PROJECT ? "PROJECT_ANALYSIS" : "FILE_ANALYSIS",
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
