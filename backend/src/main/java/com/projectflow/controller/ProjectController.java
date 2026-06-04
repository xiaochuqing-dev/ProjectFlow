package com.projectflow.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.projectflow.dto.ApiResponse;
import com.projectflow.dto.AuthDtos.AuthUser;
import com.projectflow.dto.ProjectDtos.ProjectRequest;
import com.projectflow.dto.ProjectDtos.ProjectResponse;
import com.projectflow.service.AuthService;
import com.projectflow.service.ProjectService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {
    private final ProjectService projectService;
    private final AuthService authService;

    public ProjectController(ProjectService projectService, AuthService authService) {
        this.projectService = projectService;
        this.authService = authService;
    }

    @GetMapping
    ApiResponse<List<ProjectResponse>> list(@RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(projectService.list(user.id()));
    }

    @PostMapping
    ApiResponse<ProjectResponse> create(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @Valid @RequestBody ProjectRequest request
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(projectService.create(user.id(), request));
    }

    @GetMapping("/{projectId}")
    ApiResponse<ProjectResponse> detail(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(projectService.detail(user.id(), projectId));
    }

    @PutMapping("/{projectId}")
    ApiResponse<ProjectResponse> update(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId,
        @Valid @RequestBody ProjectRequest request
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(projectService.update(user.id(), projectId, request));
    }
}
