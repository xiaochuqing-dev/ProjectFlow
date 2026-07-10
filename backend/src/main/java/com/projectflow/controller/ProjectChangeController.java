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
import com.projectflow.entity.SedimentAction;
import com.projectflow.service.AuthService;
import com.projectflow.service.ProjectChangeReviewService;
import com.projectflow.dto.V33WorkflowDtos.SedimentConfirmRequest;
import com.projectflow.dto.V33WorkflowDtos.SedimentConfirmationResponse;
import com.projectflow.dto.V33WorkflowDtos.SedimentImpactPreviewResponse;
import com.projectflow.service.ProjectSedimentService;
import com.projectflow.support.AppException;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api")
public class ProjectChangeController {
    private final ProjectChangeReviewService projectChangeReviewService;
    private final AuthService authService;
    private final ProjectSedimentService projectSedimentService;

    public ProjectChangeController(
        ProjectChangeReviewService projectChangeReviewService,
        AuthService authService,
        ProjectSedimentService projectSedimentService
    ) {
        this.projectChangeReviewService = projectChangeReviewService;
        this.authService = authService;
        this.projectSedimentService = projectSedimentService;
    }

    @PostMapping("/project-changes/{changeId}/confirm")
    ApiResponse<SedimentConfirmationResponse> confirmChange(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID changeId,
        @Valid @RequestBody SedimentConfirmRequest request
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        if (!projectSedimentService.isV33Change(user.id(), changeId)) {
            ProjectChangeResponse change = switch (request.action()) {
                case NEW_SEDIMENT -> projectChangeReviewService.acceptChange(user.id(), changeId);
                case IGNORE -> projectChangeReviewService.ignoreChange(user.id(), changeId);
                case MERGE_EXISTING, EVIDENCE_ONLY -> throw new AppException(
                    "V33_CHANGE_REQUIRED",
                    "合并已有沉淀和只补充证据需要开发推进段证据",
                    HttpStatus.BAD_REQUEST
                );
            };
            return ApiResponse.ok(new SedimentConfirmationResponse(
                change.id(), change.status().name(), null, "LEGACY_CHANGE", "兼容确认", "旧版建议已确认。",
                0, 0, false, false, false, ""
            ));
        }
        return ApiResponse.ok(projectSedimentService.confirm(user.id(), changeId, request.action(), request.targetSedimentId()));
    }

    @PostMapping("/project-changes/{changeId}/confirmation-preview")
    ApiResponse<SedimentImpactPreviewResponse> previewConfirmation(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID changeId,
        @Valid @RequestBody SedimentConfirmRequest request
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        if (!projectSedimentService.isV33Change(user.id(), changeId)) {
            throw new AppException("V33_CHANGE_REQUIRED", "旧版建议不支持项目沉淀后果预览。", HttpStatus.BAD_REQUEST);
        }
        return ApiResponse.ok(projectSedimentService.preview(user.id(), changeId, request.action(), request.targetSedimentId()));
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
        if (projectSedimentService.isV33Change(user.id(), changeId)) {
            projectSedimentService.confirm(user.id(), changeId, SedimentAction.NEW_SEDIMENT, null);
            return ApiResponse.ok(projectChangeReviewService.getChange(user.id(), changeId));
        }
        return ApiResponse.ok(projectChangeReviewService.acceptChange(user.id(), changeId));
    }

    @PostMapping("/project-changes/{changeId}/ignore")
    ApiResponse<ProjectChangeResponse> ignoreChange(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID changeId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        if (projectSedimentService.isV33Change(user.id(), changeId)) {
            projectSedimentService.confirm(user.id(), changeId, SedimentAction.IGNORE, null);
            return ApiResponse.ok(projectChangeReviewService.getChange(user.id(), changeId));
        }
        return ApiResponse.ok(projectChangeReviewService.ignoreChange(user.id(), changeId));
    }
}
