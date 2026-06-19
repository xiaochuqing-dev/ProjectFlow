package com.projectflow.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projectflow.dto.V2ProjectDtos.ContextSyncResponse;
import com.projectflow.entity.ProjectChange;
import com.projectflow.entity.ProjectChangeStatus;
import com.projectflow.entity.ProjectMemory;
import com.projectflow.entity.ProjectSpace;
import com.projectflow.repository.ProjectChangeRepository;
import com.projectflow.repository.ProjectMemoryRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.support.AppException;

@Service
public class ProjectContextSyncService {
    private final ProjectRepository projectRepository;
    private final ProjectMemoryRepository memoryRepository;
    private final ProjectChangeRepository changeRepository;

    public ProjectContextSyncService(
        ProjectRepository projectRepository,
        ProjectMemoryRepository memoryRepository,
        ProjectChangeRepository changeRepository
    ) {
        this.projectRepository = projectRepository;
        this.memoryRepository = memoryRepository;
        this.changeRepository = changeRepository;
    }

    @Transactional(readOnly = true)
    public ContextSyncResponse sync(UUID userId, UUID projectId) {
        ProjectSpace project = projectRepository.findByIdAndUserId(projectId, userId)
            .orElseThrow(() -> new AppException("PROJECT_NOT_FOUND", "Project was not found", HttpStatus.NOT_FOUND));
        ProjectMemory memory = memoryRepository.findByProjectId(project.getId())
            .orElseThrow(() -> new AppException("PROJECT_PATH_REQUIRED", "Bind a local project path before syncing context", HttpStatus.BAD_REQUEST));
        Path projectRoot = resolveProjectRoot(memory.getLocalProjectPath());
        Path contextPath = projectRoot.resolve(".projectflow").resolve("context").resolve("projectflow-context.md");
        try {
            Files.createDirectories(contextPath.getParent());
            Files.writeString(contextPath, contextContent(project, memory), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new AppException("CONTEXT_SYNC_FAILED", "ProjectFlow context could not be written", HttpStatus.BAD_REQUEST);
        }
        return new ContextSyncResponse(
            project.getId(),
            contextPath.toString(),
            List.of(".projectflow/context/projectflow-context.md"),
            Instant.now()
        );
    }

    private String contextContent(ProjectSpace project, ProjectMemory memory) {
        List<ProjectChange> acceptedChanges = changeRepository.findByProjectIdOrderByCreatedAtDesc(project.getId()).stream()
            .filter(change -> change.getStatus() == ProjectChangeStatus.ACCEPTED)
            .limit(8)
            .toList();
        return """
            # Confirmed ProjectFlow Context

            ProjectId: %s
            Project: %s
            SyncedAt: %s

            ## Positioning
            %s

            ## Current Stage
            %s

            ## Confirmed Capabilities
            %s

            ## Current Risks
            %s

            ## Technical Decisions
            %s

            ## Recent Confirmed Changes
            %s

            ## Agent Instruction
            Use this file as confirmed project context. Do not treat missing items as nonexistent; ask or inspect the repository when needed.
            """.formatted(
            project.getId(),
            project.getName(),
            Instant.now(),
            defaultText(memory.getPositioning(), project.getDescription()),
            defaultText(memory.getCurrentStage(), project.getStatus().name()),
            defaultText(memory.getCompletedCapabilities(), "暂无已确认能力。"),
            defaultText(memory.getCurrentRisks(), "暂无已确认风险。"),
            defaultText(memory.getTechnicalDecisions(), "暂无已确认技术决策。"),
            acceptedChanges.isEmpty() ? "- 暂无已确认变更。" : String.join("\n", acceptedChanges.stream().map(this::changeLine).toList())
        );
    }

    private String changeLine(ProjectChange change) {
        return "- %s：%s".formatted(change.getTitle(), change.getSummary());
    }

    private Path resolveProjectRoot(String projectPath) {
        if (projectPath == null || projectPath.isBlank()) {
            throw new AppException("PROJECT_PATH_REQUIRED", "Bind a local project path before syncing context", HttpStatus.BAD_REQUEST);
        }
        Path path = Path.of(projectPath).toAbsolutePath().normalize();
        if (path.getParent() == null || path.equals(path.getRoot())) {
            throw new AppException("PROJECT_PATH_TOO_BROAD", "Project folder path is too broad", HttpStatus.BAD_REQUEST);
        }
        if (!Files.isDirectory(path)) {
            throw new AppException("PROJECT_PATH_NOT_FOUND", "Project folder path was not found", HttpStatus.BAD_REQUEST);
        }
        return path;
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
