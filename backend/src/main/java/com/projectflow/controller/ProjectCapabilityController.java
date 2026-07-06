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
import com.projectflow.dto.V33WorkflowDtos.CapabilityCardPatchRequest;
import com.projectflow.dto.V33WorkflowDtos.CapabilityCardResponse;
import com.projectflow.service.AuthService;
import com.projectflow.service.ProjectCapabilityService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class ProjectCapabilityController {
    private final ProjectCapabilityService capabilityService;
    private final AuthService authService;

    public ProjectCapabilityController(ProjectCapabilityService capabilityService, AuthService authService) {
        this.capabilityService = capabilityService;
        this.authService = authService;
    }

    @PostMapping("/projects/{projectId}/capabilities/analyze")
    ApiResponse<List<CapabilityCardResponse>> analyze(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(capabilityService.analyze(user.id(), projectId));
    }

    @GetMapping("/projects/{projectId}/capability-cards")
    ApiResponse<List<CapabilityCardResponse>> list(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(capabilityService.list(user.id(), projectId));
    }

    @PatchMapping("/capability-cards/{cardId}")
    ApiResponse<CapabilityCardResponse> patch(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID cardId,
        @Valid @RequestBody CapabilityCardPatchRequest request
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(capabilityService.patch(user.id(), cardId, request.action()));
    }
}
