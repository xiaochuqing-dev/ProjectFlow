package com.projectflow.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.projectflow.dto.ApiResponse;
import com.projectflow.dto.AuthDtos.AuthUser;
import com.projectflow.dto.V2ProjectDtos.CapabilityInterpretRequest;
import com.projectflow.dto.V2ProjectDtos.CapabilityInterpretResponse;
import com.projectflow.dto.V2ProjectDtos.ProjectFactSourceResponse;
import com.projectflow.dto.V2ProjectDtos.ProjectLocalPathRequest;
import com.projectflow.dto.V2ProjectDtos.ProjectMemoryResponse;
import com.projectflow.dto.V2ProjectDtos.ProjectMemoryUpdateRequest;
import com.projectflow.service.AuthService;
import com.projectflow.service.ProjectMemoryService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class ProjectMemoryController {
    private final ProjectMemoryService projectMemoryService;
    private final AuthService authService;

    public ProjectMemoryController(ProjectMemoryService projectMemoryService, AuthService authService) {
        this.projectMemoryService = projectMemoryService;
        this.authService = authService;
    }

    @GetMapping("/projects/{projectId}/memory")
    ApiResponse<ProjectMemoryResponse> getMemory(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(projectMemoryService.getMemory(user.id(), projectId));
    }

    @GetMapping("/projects/{projectId}/fact-sources")
    ApiResponse<List<ProjectFactSourceResponse>> listFactSources(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(projectMemoryService.listFactSources(user.id(), projectId));
    }

    @PatchMapping("/projects/{projectId}/memory")
    ApiResponse<ProjectMemoryResponse> updateMemory(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId,
        @Valid @RequestBody ProjectMemoryUpdateRequest request
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(projectMemoryService.updateMemory(user.id(), projectId, request));
    }

    @PatchMapping("/projects/{projectId}/memory/local-path")
    ApiResponse<ProjectMemoryResponse> updateLocalProjectPath(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId,
        @Valid @RequestBody ProjectLocalPathRequest request
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(projectMemoryService.updateLocalProjectPath(user.id(), projectId, request));
    }

    @PostMapping("/projects/{projectId}/memory/capabilities/interpret")
    ApiResponse<CapabilityInterpretResponse> interpretCapability(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId,
        @Valid @RequestBody CapabilityInterpretRequest request
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(projectMemoryService.interpretCapability(user.id(), projectId, request));
    }
}
