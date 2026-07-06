package com.projectflow.service;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projectflow.dto.V2ProjectDtos.ProjectChangePatchRequest;
import com.projectflow.dto.V2ProjectDtos.ProjectChangeResponse;
import com.projectflow.entity.ProjectChange;
import com.projectflow.entity.ProjectChangeStatus;
import com.projectflow.entity.ProjectEvolutionRecord;
import com.projectflow.entity.ProjectSpace;
import com.projectflow.repository.ProjectChangeRepository;
import com.projectflow.repository.ProjectEvolutionRecordRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.support.AppException;

@Service
public class ProjectChangeReviewService {
    private final ProjectRepository projectRepository;
    private final ProjectChangeRepository changeRepository;
    private final ProjectEvolutionRecordRepository evolutionRepository;
    private final ProjectMemoryService projectMemoryService;
    private final ProjectIntelligenceService projectIntelligenceService;

    public ProjectChangeReviewService(
        ProjectRepository projectRepository,
        ProjectChangeRepository changeRepository,
        ProjectEvolutionRecordRepository evolutionRepository,
        ProjectMemoryService projectMemoryService,
        ProjectIntelligenceService projectIntelligenceService
    ) {
        this.projectRepository = projectRepository;
        this.changeRepository = changeRepository;
        this.evolutionRepository = evolutionRepository;
        this.projectMemoryService = projectMemoryService;
        this.projectIntelligenceService = projectIntelligenceService;
    }

    @Transactional(readOnly = true)
    public List<ProjectChangeResponse> listChanges(UUID userId, UUID projectId) {
        ProjectSpace project = findOwnedProject(userId, projectId);
        return changeRepository.findByProjectIdOrderByCreatedAtDesc(project.getId())
            .stream()
            .map(this::toChangeResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public ProjectChangeResponse getChange(UUID userId, UUID changeId) {
        return toChangeResponse(findOwnedChange(userId, changeId));
    }

    @Transactional
    public ProjectChangeResponse updateChange(UUID userId, UUID changeId, ProjectChangePatchRequest request) {
        ProjectChange change = findOwnedChange(userId, changeId);
        change.update(
            change.getSourceType(),
            change.getSourceRef(),
            change.getLinkedSuggestionId(),
            request.changeKind(),
            request.impactLevel(),
            request.title().trim(),
            request.summary().trim(),
            cleanMemoryText(request.details(), ""),
            cleanMemoryText(request.affectedFiles(), ""),
            cleanMemoryText(request.relatedTasks(), ""),
            cleanMemoryText(request.testEvidence(), ""),
            cleanMemoryText(request.buildEvidence(), ""),
            cleanMemoryText(request.riskNotes(), ""),
            cleanMemoryText(request.decisionNotes(), ""),
            cleanMemoryText(request.learningNotes(), ""),
            cleanMemoryText(request.assetCandidates(), "")
        );
        return toChangeResponse(change);
    }

    @Transactional
    public ProjectChangeResponse acceptChange(UUID userId, UUID changeId) {
        ProjectChange change = findOwnedChange(userId, changeId);
        ProjectSpace project = findOwnedProject(userId, change.getProjectId());
        if (change.getLinkedSuggestionId() != null && change.getStatus() == ProjectChangeStatus.PENDING) {
            projectIntelligenceService.applySuggestions(userId, change.getProjectId(), List.of(change.getLinkedSuggestionId()));
        }
        projectMemoryService.applyAcceptedChange(project, change);
        change.markAccepted();
        recordAcceptedChangeEvolution(project, change);
        return toChangeResponse(change);
    }

    @Transactional
    public ProjectChangeResponse ignoreChange(UUID userId, UUID changeId) {
        ProjectChange change = findOwnedChange(userId, changeId);
        change.markIgnored();
        return toChangeResponse(change);
    }

    private ProjectChange findOwnedChange(UUID userId, UUID changeId) {
        ProjectChange change = changeRepository.findById(changeId)
            .orElseThrow(() -> new AppException("PROJECT_CHANGE_NOT_FOUND", "Project change was not found", HttpStatus.NOT_FOUND));
        findOwnedProject(userId, change.getProjectId());
        return change;
    }

    private ProjectSpace findOwnedProject(UUID userId, UUID projectId) {
        return projectRepository.findByIdAndUserId(projectId, userId)
            .orElseThrow(() -> new AppException("PROJECT_NOT_FOUND", "Project was not found", HttpStatus.NOT_FOUND));
    }

    private ProjectChangeResponse toChangeResponse(ProjectChange change) {
        return new ProjectChangeResponse(
            change.getId(),
            change.getProjectId(),
            change.getMaterialId(),
            change.getLinkedSuggestionId(),
            change.getSourceType(),
            change.getSourceRef(),
            change.getChangeKind(),
            change.getImpactLevel(),
            change.getStatus(),
            change.getTitle(),
            change.getSummary(),
            change.getDetails(),
            change.getAffectedFiles(),
            change.getRelatedTasks(),
            change.getTestEvidence(),
            change.getBuildEvidence(),
            change.getRiskNotes(),
            change.getDecisionNotes(),
            change.getLearningNotes(),
            change.getAssetCandidates(),
            change.getCreatedAt(),
            change.getUpdatedAt(),
            change.getReviewedAt(),
            change.getDevelopmentSegmentId(),
            change.getSuggestedAction() == null ? null : change.getSuggestedAction().name(),
            change.getTargetSedimentId(),
            change.getProblemSolved(),
            change.getEvidenceRefs(),
            change.getEvidenceConfidence() == null ? null : change.getEvidenceConfidence().name(),
            change.isNeedsUserReview()
        );
    }

    private String cleanMemoryText(String value, String fallback) {
        if (value == null || value.trim().isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private void recordAcceptedChangeEvolution(ProjectSpace project, ProjectChange change) {
        ProjectEvolutionRecord record = new ProjectEvolutionRecord(project.getId(), change.getMaterialId());
        record.update(
            "采纳结构化变更：" + change.getTitle(),
            defaultText(change.getSummary(), change.getDetails()),
            defaultText(change.getAssetCandidates(), change.getSummary()),
            defaultText(change.getRiskNotes(), "暂无新增风险。"),
            defaultText(change.getDecisionNotes(), "暂无新增技术决策。"),
            defaultText(change.getLearningNotes(), "暂无新增经验沉淀。"),
            defaultText(change.getRelatedTasks(), "继续根据项目档案安排下一步。")
        );
        evolutionRepository.save(record);
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
