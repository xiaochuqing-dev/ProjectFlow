package com.projectflow.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.projectflow.dto.ApiResponse;
import com.projectflow.dto.AuthDtos.AuthUser;
import com.projectflow.dto.ProjectEvolutionBridgeDtos.EvolutionBridgePageResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.ProjectStructureIndexResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.ProjectUnderstandingRefreshRequest;
import com.projectflow.dto.ProjectUnderstandingDtos.ProjectUnderstandingSnapshotResponse;
import com.projectflow.dto.V2ProjectDtos.ProjectAnalysisJobResponse;
import com.projectflow.service.AuthService;
import com.projectflow.service.ProjectAnalysisJobService;
import com.projectflow.service.ProjectEvolutionBridgeService;
import com.projectflow.service.ProjectUnderstandingService;

@RestController
@RequestMapping("/api/projects/{projectId}")
public class ProjectUnderstandingController {
    private final ProjectUnderstandingService understandingService;
    private final ProjectAnalysisJobService jobService;
    private final ProjectEvolutionBridgeService evolutionBridgeService;
    private final AuthService authService;

    public ProjectUnderstandingController(
        ProjectUnderstandingService understandingService,
        ProjectAnalysisJobService jobService,
        ProjectEvolutionBridgeService evolutionBridgeService,
        AuthService authService
    ) {
        this.understandingService = understandingService;
        this.jobService = jobService;
        this.evolutionBridgeService = evolutionBridgeService;
        this.authService = authService;
    }

    @PostMapping("/understanding/refresh")
    ApiResponse<ProjectAnalysisJobResponse> refresh(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId,
        @RequestBody(required = false) ProjectUnderstandingRefreshRequest request
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(jobService.startProjectUnderstandingRefresh(user.id(), projectId, request));
    }

    @GetMapping("/understanding")
    ApiResponse<ProjectUnderstandingSnapshotResponse> get(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(understandingService.get(user.id(), projectId));
    }

    @GetMapping("/structure-index")
    ApiResponse<ProjectStructureIndexResponse> structureIndex(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(understandingService.getStructureIndex(user.id(), projectId));
    }

    @GetMapping("/evolution-bridges")
    ApiResponse<EvolutionBridgePageResponse> evolutionBridges(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(evolutionBridgeService.list(user.id(), projectId, page, size));
    }
}
