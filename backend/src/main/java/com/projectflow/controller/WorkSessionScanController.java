package com.projectflow.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.projectflow.dto.ApiResponse;
import com.projectflow.dto.AuthDtos.AuthUser;
import com.projectflow.dto.V2ProjectDtos.AgentSignatureFeedbackResponse;
import com.projectflow.dto.V2ProjectDtos.ChangeConflictResponse;
import com.projectflow.dto.V2ProjectDtos.ContextSyncResponse;
import com.projectflow.dto.V2ProjectDtos.EvidenceBundleResponse;
import com.projectflow.dto.V2ProjectDtos.ProjectChangeResponse;
import com.projectflow.dto.V2ProjectDtos.WorkSessionCandidateResponse;
import com.projectflow.dto.V2ProjectDtos.WorkSessionPatchRequest;
import com.projectflow.dto.V2ProjectDtos.WorkSessionScanResponse;
import com.projectflow.service.AuthService;
import com.projectflow.service.ChangeConflictService;
import com.projectflow.service.EvidenceDraftChangeService;
import com.projectflow.service.EvidenceBundleService;
import com.projectflow.service.ProjectContextSyncService;
import com.projectflow.service.WorkSessionScanService;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api")
public class WorkSessionScanController {
    private final WorkSessionScanService workSessionScanService;
    private final EvidenceBundleService evidenceBundleService;
    private final EvidenceDraftChangeService evidenceDraftChangeService;
    private final ChangeConflictService changeConflictService;
    private final ProjectContextSyncService projectContextSyncService;
    private final AuthService authService;

    public WorkSessionScanController(
        WorkSessionScanService workSessionScanService,
        EvidenceBundleService evidenceBundleService,
        EvidenceDraftChangeService evidenceDraftChangeService,
        ChangeConflictService changeConflictService,
        ProjectContextSyncService projectContextSyncService,
        AuthService authService
    ) {
        this.workSessionScanService = workSessionScanService;
        this.evidenceBundleService = evidenceBundleService;
        this.evidenceDraftChangeService = evidenceDraftChangeService;
        this.changeConflictService = changeConflictService;
        this.projectContextSyncService = projectContextSyncService;
        this.authService = authService;
    }

    @PostMapping("/projects/{projectId}/scan")
    ApiResponse<WorkSessionScanResponse> scan(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(workSessionScanService.scan(user.id(), projectId));
    }

    @GetMapping("/projects/{projectId}/work-sessions")
    ApiResponse<List<WorkSessionCandidateResponse>> list(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(workSessionScanService.list(user.id(), projectId));
    }

    @PatchMapping("/work-sessions/{sessionId}")
    ApiResponse<WorkSessionCandidateResponse> patch(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID sessionId,
        @Valid @RequestBody WorkSessionPatchRequest request
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(workSessionScanService.patch(user.id(), sessionId, request));
    }

    @PostMapping("/work-sessions/{sessionId}/evidence-bundles")
    ApiResponse<EvidenceBundleResponse> createEvidenceBundle(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID sessionId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(evidenceBundleService.createFromWorkSession(user.id(), sessionId));
    }

    @GetMapping("/projects/{projectId}/evidence-bundles")
    ApiResponse<List<EvidenceBundleResponse>> listEvidenceBundles(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(evidenceBundleService.list(user.id(), projectId));
    }

    @GetMapping("/projects/{projectId}/agent-signature-feedback")
    ApiResponse<List<AgentSignatureFeedbackResponse>> listAgentSignatureFeedback(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(workSessionScanService.listFeedback(user.id(), projectId));
    }

    @GetMapping("/projects/{projectId}/change-conflicts")
    ApiResponse<List<ChangeConflictResponse>> listChangeConflicts(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(changeConflictService.list(user.id(), projectId));
    }

    @PostMapping("/evidence-bundles/{bundleId}/draft-changes")
    ApiResponse<ProjectChangeResponse> draftChange(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID bundleId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(evidenceDraftChangeService.draftChange(user.id(), bundleId));
    }

    @PostMapping("/projects/{projectId}/context/sync")
    ApiResponse<ContextSyncResponse> syncContext(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(projectContextSyncService.sync(user.id(), projectId));
    }
}
