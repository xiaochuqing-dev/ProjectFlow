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
import com.projectflow.dto.V2ProjectDtos.ProjectChangePatchRequest;
import com.projectflow.dto.V2ProjectDtos.ProjectChangeResponse;
import com.projectflow.service.AuthService;
import com.projectflow.service.ProjectChangeReviewService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class ProjectChangeController {
    private final ProjectChangeReviewService projectChangeReviewService;
    private final AuthService authService;

    public ProjectChangeController(ProjectChangeReviewService projectChangeReviewService, AuthService authService) {
        this.projectChangeReviewService = projectChangeReviewService;
        this.authService = authService;
    }

    @GetMapping("/projects/{projectId}/changes")
    ApiResponse<List<ProjectChangeResponse>> listChanges(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(projectChangeReviewService.listChanges(user.id(), projectId));
    }

    @GetMapping("/project-changes/{changeId}")
    ApiResponse<ProjectChangeResponse> getChange(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID changeId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(projectChangeReviewService.getChange(user.id(), changeId));
    }

    @PatchMapping("/project-changes/{changeId}")
    ApiResponse<ProjectChangeResponse> updateChange(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID changeId,
        @Valid @RequestBody ProjectChangePatchRequest request
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(projectChangeReviewService.updateChange(user.id(), changeId, request));
    }

    @PostMapping("/project-changes/{changeId}/accept")
    ApiResponse<ProjectChangeResponse> acceptChange(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID changeId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(projectChangeReviewService.acceptChange(user.id(), changeId));
    }

    @PostMapping("/project-changes/{changeId}/ignore")
    ApiResponse<ProjectChangeResponse> ignoreChange(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID changeId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(projectChangeReviewService.ignoreChange(user.id(), changeId));
    }
}
