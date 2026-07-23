package com.projectflow.service;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projectflow.dto.ProjectDtos.ProjectRequest;
import com.projectflow.dto.ProjectDtos.ProjectResponse;
import com.projectflow.entity.ProjectSpace;
import com.projectflow.repository.AgentSignatureFeedbackRepository;
import com.projectflow.repository.AiOutputRepository;
import com.projectflow.repository.AiSuggestionRepository;
import com.projectflow.repository.DevLogRepository;
import com.projectflow.repository.EvidenceBundleRepository;
import com.projectflow.repository.ImportRecordRepository;
import com.projectflow.repository.ModelUsageRecordRepository;
import com.projectflow.repository.ProjectAnalysisJobRepository;
import com.projectflow.repository.ProjectAnalysisRecordRepository;
import com.projectflow.repository.ProjectChangeRepository;
import com.projectflow.repository.ProjectEvolutionRecordRepository;
import com.projectflow.repository.ProjectFactSourceRepository;
import com.projectflow.repository.ProjectMaterialRepository;
import com.projectflow.repository.ProjectMemoryRepository;
import com.projectflow.repository.ProjectMemoryReadAuditRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.repository.ProjectSnapshotRepository;
import com.projectflow.repository.ProjectStructureIndexRepository;
import com.projectflow.repository.ProjectUnderstandingSnapshotRepository;
import com.projectflow.repository.TaskRepository;
import com.projectflow.repository.WorkSessionRepository;
import com.projectflow.support.AppException;

@Service
public class ProjectService {
    private final ProjectRepository projectRepository;
    private final ProjectMaterialRepository materialRepository;
    private final AiSuggestionRepository suggestionRepository;
    private final ProjectMemoryRepository memoryRepository;
    private final ProjectSnapshotRepository snapshotRepository;
    private final ProjectEvolutionRecordRepository evolutionRepository;
    private final ProjectAnalysisRecordRepository analysisRecordRepository;
    private final ProjectAnalysisJobRepository analysisJobRepository;
    private final ProjectChangeRepository changeRepository;
    private final ProjectFactSourceRepository factSourceRepository;
    private final TaskRepository taskRepository;
    private final DevLogRepository devLogRepository;
    private final AiOutputRepository aiOutputRepository;
    private final ImportRecordRepository importRecordRepository;
    private final WorkSessionRepository workSessionRepository;
    private final EvidenceBundleRepository evidenceBundleRepository;
    private final AgentSignatureFeedbackRepository agentSignatureFeedbackRepository;
    private final ModelUsageRecordRepository modelUsageRecordRepository;
    private final ProjectMemoryReadAuditRepository memoryReadAuditRepository;
    private final ProjectStructureIndexRepository structureIndexRepository;
    private final ProjectUnderstandingSnapshotRepository understandingSnapshotRepository;

    public ProjectService(
        ProjectRepository projectRepository,
        ProjectMaterialRepository materialRepository,
        AiSuggestionRepository suggestionRepository,
        ProjectMemoryRepository memoryRepository,
        ProjectSnapshotRepository snapshotRepository,
        ProjectEvolutionRecordRepository evolutionRepository,
        ProjectAnalysisRecordRepository analysisRecordRepository,
        ProjectAnalysisJobRepository analysisJobRepository,
        ProjectChangeRepository changeRepository,
        ProjectFactSourceRepository factSourceRepository,
        TaskRepository taskRepository,
        DevLogRepository devLogRepository,
        AiOutputRepository aiOutputRepository,
        ImportRecordRepository importRecordRepository,
        WorkSessionRepository workSessionRepository,
        EvidenceBundleRepository evidenceBundleRepository,
        AgentSignatureFeedbackRepository agentSignatureFeedbackRepository,
        ModelUsageRecordRepository modelUsageRecordRepository,
        ProjectMemoryReadAuditRepository memoryReadAuditRepository,
        ProjectStructureIndexRepository structureIndexRepository,
        ProjectUnderstandingSnapshotRepository understandingSnapshotRepository
    ) {
        this.projectRepository = projectRepository;
        this.materialRepository = materialRepository;
        this.suggestionRepository = suggestionRepository;
        this.memoryRepository = memoryRepository;
        this.snapshotRepository = snapshotRepository;
        this.evolutionRepository = evolutionRepository;
        this.analysisRecordRepository = analysisRecordRepository;
        this.analysisJobRepository = analysisJobRepository;
        this.changeRepository = changeRepository;
        this.factSourceRepository = factSourceRepository;
        this.taskRepository = taskRepository;
        this.devLogRepository = devLogRepository;
        this.aiOutputRepository = aiOutputRepository;
        this.importRecordRepository = importRecordRepository;
        this.workSessionRepository = workSessionRepository;
        this.evidenceBundleRepository = evidenceBundleRepository;
        this.agentSignatureFeedbackRepository = agentSignatureFeedbackRepository;
        this.modelUsageRecordRepository = modelUsageRecordRepository;
        this.memoryReadAuditRepository = memoryReadAuditRepository;
        this.structureIndexRepository = structureIndexRepository;
        this.understandingSnapshotRepository = understandingSnapshotRepository;
    }

    @Transactional
    public ProjectResponse create(UUID userId, ProjectRequest request) {
        ProjectSpace project = new ProjectSpace(userId);
        apply(project, request);
        return toResponse(projectRepository.save(project));
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> list(UUID userId) {
        return projectRepository.findByUserIdOrderByUpdatedAtDesc(userId)
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse detail(UUID userId, UUID projectId) {
        return toResponse(findOwned(userId, projectId));
    }

    @Transactional
    public ProjectResponse update(UUID userId, UUID projectId, ProjectRequest request) {
        ProjectSpace project = findOwned(userId, projectId);
        apply(project, request);
        return toResponse(project);
    }

    @Transactional
    public void delete(UUID userId, UUID projectId) {
        ProjectSpace project = findOwned(userId, projectId);
        UUID id = project.getId();
        evidenceBundleRepository.deleteByProjectId(id);
        workSessionRepository.deleteByProjectId(id);
        agentSignatureFeedbackRepository.deleteByProjectId(id);
        analysisJobRepository.deleteByProjectId(id);
        analysisRecordRepository.deleteByProjectId(id);
        modelUsageRecordRepository.deleteByProjectId(id);
        memoryReadAuditRepository.deleteByProjectId(id);
        understandingSnapshotRepository.deleteByProjectId(id);
        structureIndexRepository.deleteByProjectId(id);
        changeRepository.deleteByProjectId(id);
        suggestionRepository.deleteByProjectId(id);
        factSourceRepository.deleteByProjectId(id);
        snapshotRepository.deleteByProjectId(id);
        evolutionRepository.deleteByProjectId(id);
        memoryRepository.deleteByProjectId(id);
        materialRepository.deleteByProjectId(id);
        aiOutputRepository.deleteByProjectId(id);
        importRecordRepository.deleteByProjectId(id);
        taskRepository.deleteByProjectId(id);
        devLogRepository.deleteByProjectId(id);
        projectRepository.delete(project);
    }

    private ProjectSpace findOwned(UUID userId, UUID projectId) {
        return projectRepository.findByIdAndUserId(projectId, userId)
            .orElseThrow(() -> new AppException("PROJECT_NOT_FOUND", "Project was not found", HttpStatus.NOT_FOUND));
    }

    private void apply(ProjectSpace project, ProjectRequest request) {
        project.update(
            request.name().trim(),
            request.description(),
            request.status(),
            request.techStack(),
            request.repoUrl(),
            request.startDate(),
            request.endDate()
        );
    }

    private ProjectResponse toResponse(ProjectSpace project) {
        return new ProjectResponse(
            project.getId(),
            project.getName(),
            project.getDescription(),
            project.getStatus(),
            project.getTechStack(),
            project.getRepoUrl(),
            project.getStartDate(),
            project.getEndDate(),
            project.getCreatedAt(),
            project.getUpdatedAt()
        );
    }
}
