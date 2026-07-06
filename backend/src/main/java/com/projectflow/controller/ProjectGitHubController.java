package com.projectflow.controller;

import java.nio.file.Path;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.projectflow.dto.ApiResponse;
import com.projectflow.dto.AuthDtos.AuthUser;
import com.projectflow.dto.V33WorkflowDtos.GitHubStatusResponse;
import com.projectflow.entity.ProjectMemory;
import com.projectflow.repository.ProjectMemoryRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.service.AuthService;
import com.projectflow.service.GitHubCliService;
import com.projectflow.service.LocalProjectPathGuard;
import com.projectflow.support.AppException;

@RestController
@RequestMapping("/api/projects/{projectId}/github")
public class ProjectGitHubController {
    private final ProjectRepository projectRepository;
    private final ProjectMemoryRepository memoryRepository;
    private final LocalProjectPathGuard localProjectPathGuard;
    private final GitHubCliService gitHubCliService;
    private final AuthService authService;

    public ProjectGitHubController(
        ProjectRepository projectRepository,
        ProjectMemoryRepository memoryRepository,
        LocalProjectPathGuard localProjectPathGuard,
        GitHubCliService gitHubCliService,
        AuthService authService
    ) {
        this.projectRepository = projectRepository;
        this.memoryRepository = memoryRepository;
        this.localProjectPathGuard = localProjectPathGuard;
        this.gitHubCliService = gitHubCliService;
        this.authService = authService;
    }

    @GetMapping("/status")
    ApiResponse<GitHubStatusResponse> status(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId
    ) {
        return ApiResponse.ok(inspect(authorizationHeader, projectId, false));
    }

    @PostMapping("/refresh")
    ApiResponse<GitHubStatusResponse> refresh(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId
    ) {
        return ApiResponse.ok(inspect(authorizationHeader, projectId, true));
    }

    private GitHubStatusResponse inspect(String authorizationHeader, UUID projectId, boolean refreshRemote) {
        AuthUser user = authService.currentUser(authorizationHeader);
        projectRepository.findByIdAndUserId(projectId, user.id())
            .orElseThrow(() -> new AppException("PROJECT_NOT_FOUND", "Project was not found", HttpStatus.NOT_FOUND));
        ProjectMemory memory = memoryRepository.findByProjectId(projectId)
            .orElseThrow(() -> new AppException("PROJECT_PATH_REQUIRED", "Bind a local project path before checking GitHub", HttpStatus.BAD_REQUEST));
        Path root = localProjectPathGuard.requireGitProjectDirectory(memory.getLocalProjectPath()).path();
        return gitHubCliService.inspect(root, refreshRemote);
    }
}
