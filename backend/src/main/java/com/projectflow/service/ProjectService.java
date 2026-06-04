package com.projectflow.service;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projectflow.dto.ProjectDtos.ProjectRequest;
import com.projectflow.dto.ProjectDtos.ProjectResponse;
import com.projectflow.entity.ProjectSpace;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.support.AppException;

@Service
public class ProjectService {
    private final ProjectRepository projectRepository;

    public ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
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
