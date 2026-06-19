package com.projectflow.service;

import java.util.UUID;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.dto.V2ProjectDtos.ProjectAnalysisResponse;
import com.projectflow.dto.V2ProjectDtos.ProjectFileAnalysisRequest;
import com.projectflow.dto.V2ProjectDtos.ProjectFileAnalysisResponse;
import com.projectflow.entity.ProjectAnalysisJob;
import com.projectflow.entity.ProjectAnalysisJobStatus;
import com.projectflow.entity.ProjectAnalysisJobType;
import com.projectflow.repository.ProjectAnalysisJobRepository;

@Service
public class ProjectAnalysisJobRunner {
    private final ProjectAnalysisJobRepository jobRepository;
    private final ProjectIntelligenceService projectIntelligenceService;
    private final ObjectMapper objectMapper;

    public ProjectAnalysisJobRunner(
        ProjectAnalysisJobRepository jobRepository,
        ProjectIntelligenceService projectIntelligenceService,
        ObjectMapper objectMapper
    ) {
        this.jobRepository = jobRepository;
        this.projectIntelligenceService = projectIntelligenceService;
        this.objectMapper = objectMapper;
    }

    @Async
    public void execute(UUID jobId) {
        ProjectAnalysisJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null || job.getStatus() == ProjectAnalysisJobStatus.SUCCEEDED || job.getStatus() == ProjectAnalysisJobStatus.FAILED) {
            return;
        }

        try {
            job.markRunning();
            jobRepository.save(job);

            if (job.getJobType() == ProjectAnalysisJobType.PROJECT) {
                ProjectAnalysisResponse result = projectIntelligenceService.runProjectAnalysis(job.getUserId(), job.getProjectId());
                UUID recordId = projectIntelligenceService.createProjectAnalysisRecord(job.getUserId(), job.getProjectId(), result);
                markSucceeded(jobId, objectMapper.writeValueAsString(result), recordId);
            } else {
                ProjectFileAnalysisResponse result = projectIntelligenceService.analyzeProjectFile(
                    job.getUserId(),
                    job.getProjectId(),
                    new ProjectFileAnalysisRequest(job.getFilePath())
                );
                UUID recordId = projectIntelligenceService.createFileAnalysisRecord(job.getUserId(), job.getProjectId(), result);
                markSucceeded(jobId, objectMapper.writeValueAsString(result), recordId);
            }
        } catch (Exception exception) {
            markFailed(jobId, safeErrorMessage(exception));
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
