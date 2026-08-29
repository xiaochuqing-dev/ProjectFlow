package com.projectflow.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projectflow.dto.ProjectDtos.ProjectResponse;
import com.projectflow.dto.V2ProjectDtos.DashboardBootstrapResponse;
import com.projectflow.dto.V2ProjectDtos.DashboardJobSummary;
import com.projectflow.dto.V2ProjectDtos.DashboardMemorySummary;
import com.projectflow.dto.V2ProjectDtos.DashboardProjectAnalysisSummary;
import com.projectflow.dto.V2ProjectDtos.DashboardProviderAvailability;
import com.projectflow.dto.V2ProjectDtos.WorkSessionScanResponse;
import com.projectflow.entity.AiProvider;
import com.projectflow.entity.ChangeBatch;
import com.projectflow.entity.ProjectAnalysisJobStatus;
import com.projectflow.entity.ProjectAnalysisJobType;
import com.projectflow.entity.ProjectAnalysisRecordType;
import com.projectflow.entity.ProjectChangeStatus;
import com.projectflow.entity.ProjectSpace;
import com.projectflow.repository.AiProviderRepository;
import com.projectflow.repository.ChangeBatchRepository;
import com.projectflow.repository.DevelopmentSegmentRepository;
import com.projectflow.repository.ProjectAnalysisJobRepository;
import com.projectflow.repository.ProjectAnalysisRecordRepository;
import com.projectflow.repository.ProjectChangeRepository;
import com.projectflow.repository.ProjectMemoryRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.repository.WorkSessionRepository;
import com.projectflow.support.AppException;

@Service
public class DashboardBootstrapService {
    private static final List<ProjectAnalysisJobStatus> SUCCESS_STATUSES = List.of(
        ProjectAnalysisJobStatus.SUCCEEDED,
        ProjectAnalysisJobStatus.SUCCEEDED_WITH_WARNINGS
    );

    private final ProjectRepository projectRepository;
    private final ProjectMemoryRepository memoryRepository;
    private final ProjectAnalysisJobRepository jobRepository;
    private final ProjectAnalysisRecordRepository analysisRecordRepository;
    private final ChangeBatchRepository batchRepository;
    private final DevelopmentSegmentRepository segmentRepository;
    private final ProjectChangeRepository changeRepository;
    private final WorkSessionRepository workSessionRepository;
    private final AiProviderRepository providerRepository;

    public DashboardBootstrapService(
        ProjectRepository projectRepository,
        ProjectMemoryRepository memoryRepository,
        ProjectAnalysisJobRepository jobRepository,
        ProjectAnalysisRecordRepository analysisRecordRepository,
        ChangeBatchRepository batchRepository,
        DevelopmentSegmentRepository segmentRepository,
        ProjectChangeRepository changeRepository,
        WorkSessionRepository workSessionRepository,
        AiProviderRepository providerRepository
    ) {
        this.projectRepository = projectRepository;
        this.memoryRepository = memoryRepository;
        this.jobRepository = jobRepository;
        this.analysisRecordRepository = analysisRecordRepository;
        this.batchRepository = batchRepository;
        this.segmentRepository = segmentRepository;
        this.changeRepository = changeRepository;
        this.workSessionRepository = workSessionRepository;
        this.providerRepository = providerRepository;
    }

    @Transactional(readOnly = true)
    public DashboardBootstrapResponse load(UUID userId, UUID projectId) {
        ProjectSpace project = projectRepository.findByIdAndUserId(projectId, userId)
            .orElseThrow(() -> new AppException("PROJECT_NOT_FOUND", "Project was not found", HttpStatus.NOT_FOUND));
        var memory = memoryRepository.getByProjectId(projectId).orElse(null);
        var latestJob = jobRepository.findFirstByProjectIdAndJobTypeAndStatusInOrderByCreatedAtDesc(
            projectId,
            ProjectAnalysisJobType.WORK_SESSION_SCAN,
            SUCCESS_STATUSES
        ).orElse(null);
        ChangeBatch batch = batchRepository.findFirstByProjectIdOrderByScanStartedAtDesc(projectId).orElse(null);
        var segments = batch == null ? List.<com.projectflow.dto.V33WorkflowDtos.DevelopmentSegmentResponse>of()
            : segmentRepository.findByBatchIdOrderByCreatedAtAsc(batch.getId()).stream()
                .map(PendingChangeScanService::toSegmentResponse)
                .toList();
        var sessions = workSessionRepository.findTop20ByProjectIdOrderByEndTimeDesc(projectId).stream()
            .map(WorkSessionScanService::toWorkSessionResponse)
            .toList();
        Instant scannedAt = batch == null
            ? sessions.stream().findFirst().map(item -> item.endTime()).orElse(project.getUpdatedAt())
            : batch.getScanFinishedAt() == null ? batch.getScanStartedAt() : batch.getScanFinishedAt();
        String branchName = batch == null
            ? sessions.stream().findFirst().map(item -> item.branchName()).orElse("")
            : batch.getBranchName();
        WorkSessionScanResponse scan = new WorkSessionScanResponse(
            projectId,
            memory == null ? "" : safe(memory.getLocalProjectPath()),
            safe(branchName),
            scannedAt,
            sessions,
            batch == null ? List.of() : batch.getWarnings(),
            batch == null ? null : PendingChangeScanService.toBatchResponse(batch),
            segments,
            batch != null && batch.isFirstScan()
        );
        long pending = changeRepository.countByProjectIdAndSourceBatchIdIsNotNullAndStatusIn(
            projectId,
            List.of(ProjectChangeStatus.PENDING, ProjectChangeStatus.EDITED)
        );
        var latestAnalysis = analysisRecordRepository.findFirstByProjectIdAndRecordTypeOrderByCreatedAtDesc(
            projectId,
            ProjectAnalysisRecordType.PROJECT
        ).orElse(null);
        AiProvider provider = providerRepository.findFirstByUserIdAndDefaultEnabledTrueOrderByUpdatedAtDesc(userId).orElse(null);

        return new DashboardBootstrapResponse(
            projectResponse(project),
            memory == null ? new DashboardMemorySummary("", "", "", project.getUpdatedAt()) : new DashboardMemorySummary(
                safe(memory.getPositioning()), safe(memory.getCurrentStage()), safe(memory.getLocalProjectPath()), memory.getUpdatedAt()
            ),
            latestJob == null ? null : new DashboardJobSummary(
                latestJob.getId(), latestJob.getStatus().name(), latestJob.getCreatedAt(), latestJob.getUpdatedAt(), latestJob.getCompletedAt()
            ),
            scan,
            pending,
            latestAnalysis == null ? null : new DashboardProjectAnalysisSummary(
                latestAnalysis.getId(), safe(latestAnalysis.getSummary()), safe(latestAnalysis.getAnalysisSource()),
                latestAnalysis.isModelUsed(), safe(latestAnalysis.getProviderName()), safe(latestAnalysis.getConfidence()),
                latestAnalysis.getCreatedAt()
            ),
            new DashboardProviderAvailability(
                provider != null && provider.hasConfiguredCredential(),
                provider == null ? "" : safe(provider.getName()),
                provider == null ? "" : safe(provider.getModelName())
            ),
            Instant.now()
        );
    }

    private ProjectResponse projectResponse(ProjectSpace project) {
        return new ProjectResponse(
            project.getId(), project.getName(), project.getDescription(), project.getStatus(), project.getTechStack(),
            project.getRepoUrl(), project.getStartDate(), project.getEndDate(), project.getCreatedAt(), project.getUpdatedAt()
        );
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
