package com.projectflow.controller;

import java.nio.file.Path;
import java.util.List;
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
import com.projectflow.dto.V33WorkflowDtos.GitHubLoginGuideResponse;
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

    // V3.3.3: GitHub 登录指引。不读取、不展示、不保存 token；只提供命令让用户在终端执行。
    @GetMapping("/login-guide")
    ApiResponse<GitHubLoginGuideResponse> loginGuide(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        projectRepository.findByIdAndUserId(projectId, user.id())
            .orElseThrow(() -> new AppException("PROJECT_NOT_FOUND", "Project was not found", HttpStatus.NOT_FOUND));
        ProjectMemory memory = memoryRepository.findByProjectId(projectId)
            .orElseThrow(() -> new AppException("PROJECT_PATH_REQUIRED", "Bind a local project path before checking GitHub", HttpStatus.BAD_REQUEST));
        Path root = localProjectPathGuard.requireGitProjectDirectory(memory.getLocalProjectPath()).path();
        GitHubStatusResponse current = gitHubCliService.inspect(root, false);
        List<String> instructions = List.of(
            "ProjectFlow 不读取、不展示、不保存 GitHub token。",
            "请在终端执行下方命令，完成浏览器授权后回到 ProjectFlow 点击「重新检查」。",
            "不要使用 gh auth status --show-token，也不要展示 token。",
            "刷新同步状态只读取远程提交信息，不会修改本地代码（不会 pull、merge、rebase）。"
        );
        String command = current.ghInstalled() ? "gh auth login --web --clipboard" : "";
        List<String> warnings = current.warnings();
        if (!current.ghInstalled()) {
            instructions = List.of(
                "未检测到 GitHub CLI。请先安装：https://cli.github.com/",
                "安装完成后回到 ProjectFlow 点击「重新检查」。"
            );
        }
        return ApiResponse.ok(new GitHubLoginGuideResponse(
            current.ghInstalled(),
            current.status(),
            command,
            instructions,
            warnings
        ));
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
