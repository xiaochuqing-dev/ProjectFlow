package com.projectflow.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.projectflow.dto.ApiResponse;
import com.projectflow.dto.AuthDtos.AuthUser;
import com.projectflow.dto.V33WorkflowDtos.ProjectSedimentPatchRequest;
import com.projectflow.dto.V33WorkflowDtos.ProjectSedimentResponse;
import com.projectflow.dto.V33WorkflowDtos.SedimentReviewBatchDetailResponse;
import com.projectflow.dto.V33WorkflowDtos.SedimentReviewBatchResponse;
import com.projectflow.service.AuthService;
import com.projectflow.service.ProjectSedimentService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class ProjectSedimentController {
    private final ProjectSedimentService sedimentService;
    private final AuthService authService;

    public ProjectSedimentController(ProjectSedimentService sedimentService, AuthService authService) {
        this.sedimentService = sedimentService;
        this.authService = authService;
    }

    @GetMapping("/projects/{projectId}/sediments")
    ApiResponse<List<ProjectSedimentResponse>> list(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(sedimentService.list(user.id(), projectId));
    }

    @GetMapping("/projects/{projectId}/sediment-review-batches")
    ApiResponse<List<SedimentReviewBatchResponse>> listReviewBatches(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(sedimentService.listReviewBatches(user.id(), projectId));
    }

    @GetMapping("/sediment-review-batches/{batchId}")
    ApiResponse<SedimentReviewBatchDetailResponse> getReviewBatch(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID batchId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(sedimentService.getReviewBatch(user.id(), batchId));
    }

    @GetMapping("/project-sediments/{sedimentId}")
    ApiResponse<ProjectSedimentResponse> get(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID sedimentId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(sedimentService.get(user.id(), sedimentId));
    }

    @PatchMapping("/project-sediments/{sedimentId}")
    ApiResponse<ProjectSedimentResponse> patch(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID sedimentId,
        @Valid @RequestBody ProjectSedimentPatchRequest request
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(sedimentService.patch(user.id(), sedimentId, request));
    }
}
