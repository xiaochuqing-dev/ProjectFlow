package com.projectflow.service;

import java.util.List;
import java.util.UUID;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.dto.V2ProjectDtos.ProjectAnalysisJobResponse;
import com.projectflow.dto.V2ProjectDtos.ProjectAnalysisResponse;
import com.projectflow.dto.V2ProjectDtos.ProjectFileAnalysisResponse;
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

    public ProjectAnalysisJobService(
        ProjectAnalysisJobRepository jobRepository,
        ProjectRepository projectRepository,
        ProjectAnalysisJobRunner jobRunner,
        ObjectMapper objectMapper
    ) {
        this.jobRepository = jobRepository;
        this.projectRepository = projectRepository;
        this.jobRunner = jobRunner;
        this.objectMapper = objectMapper;
    }

    public ProjectAnalysisJobResponse startProjectAnalysis(UUID userId, UUID projectId) {
        findOwnedProject(userId, projectId);
        java.util.Optional<ProjectAnalysisJob> active = activeJob(projectId, ProjectAnalysisJobType.PROJECT, null);
        if (active.isPresent()) {
            return toResponse(active.get());
        }
        ProjectAnalysisJob job = jobRepository.save(new ProjectAnalysisJob(projectId, userId, ProjectAnalysisJobType.PROJECT, null));
        jobRunner.execute(job.getId());
        return toResponse(job);
    }

    public ProjectAnalysisJobResponse startFileAnalysis(UUID userId, UUID projectId, String path) {
        findOwnedProject(userId, projectId);
        String normalizedPath = path.trim();
        java.util.Optional<ProjectAnalysisJob> active = activeJob(projectId, ProjectAnalysisJobType.FILE, normalizedPath);
        if (active.isPresent()) {
            return toResponse(active.get());
        }
        ProjectAnalysisJob job = jobRepository.save(new ProjectAnalysisJob(projectId, userId, ProjectAnalysisJobType.FILE, normalizedPath));
        jobRunner.execute(job.getId());
        return toResponse(job);
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

    @EventListener(ApplicationReadyEvent.class)
    public void resumeInterruptedJobs() {
        jobRepository.findAll().stream()
            .filter(job -> ACTIVE_STATUSES.contains(job.getStatus()))
            .forEach(job -> jobRunner.execute(job.getId()));
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
        if (job.getResultJson() != null && !job.getResultJson().isBlank()) {
            try {
                if (job.getJobType() == ProjectAnalysisJobType.PROJECT) {
                    projectResult = objectMapper.readValue(job.getResultJson(), ProjectAnalysisResponse.class);
                } else {
                    fileResult = objectMapper.readValue(job.getResultJson(), ProjectFileAnalysisResponse.class);
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
            job.getErrorMessage(),
            job.getRecordId(),
            job.getCreatedAt(),
            job.getUpdatedAt(),
            job.getStartedAt(),
            job.getCompletedAt()
        );
    }
}
