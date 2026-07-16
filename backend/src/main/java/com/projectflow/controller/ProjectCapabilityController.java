package com.projectflow.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.projectflow.dto.ApiResponse;
import com.projectflow.dto.AuthDtos.AuthUser;
import com.projectflow.dto.ProjectCapabilityDtos.CapabilityAttentionPageResponse;
import com.projectflow.dto.ProjectCapabilityDtos.CapabilityDetailResponse;
import com.projectflow.dto.ProjectCapabilityDtos.CapabilityEvolutionPageResponse;
import com.projectflow.dto.ProjectCapabilityDtos.CapabilityFactPageResponse;
import com.projectflow.dto.ProjectCapabilityDtos.CapabilityMapOverviewResponse;
import com.projectflow.dto.ProjectCapabilityDtos.CapabilityPageResponse;
import com.projectflow.dto.V2ProjectDtos.ProjectAnalysisJobResponse;
import com.projectflow.dto.V33WorkflowDtos.CapabilityCardPatchRequest;
import com.projectflow.dto.V33WorkflowDtos.CapabilityAnalysisOverviewResponse;
import com.projectflow.dto.V33WorkflowDtos.CapabilityCardResponse;
import com.projectflow.service.AuthService;
import com.projectflow.service.ProjectAnalysisJobService;
import com.projectflow.service.ProjectCapabilityService;
import com.projectflow.service.ProjectCapabilityMapService;
import com.projectflow.service.ProjectCapabilityQueryService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class ProjectCapabilityController {
    private final ProjectCapabilityService capabilityService;
    private final ProjectAnalysisJobService projectAnalysisJobService;
    private final ProjectCapabilityMapService capabilityMapService;
    private final ProjectCapabilityQueryService capabilityQueryService;
    private final AuthService authService;

    public ProjectCapabilityController(
        ProjectCapabilityService capabilityService,
        ProjectAnalysisJobService projectAnalysisJobService,
        ProjectCapabilityMapService capabilityMapService,
        ProjectCapabilityQueryService capabilityQueryService,
        AuthService authService
    ) {
        this.capabilityService = capabilityService;
        this.projectAnalysisJobService = projectAnalysisJobService;
        this.capabilityMapService = capabilityMapService;
        this.capabilityQueryService = capabilityQueryService;
        this.authService = authService;
    }

    @GetMapping("/projects/{projectId}/capability-map/overview")
    ApiResponse<CapabilityMapOverviewResponse> mapOverview(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(capabilityQueryService.overview(user.id(), projectId));
    }

    @GetMapping("/projects/{projectId}/capabilities")
    ApiResponse<CapabilityPageResponse> capabilities(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String maturity,
        @RequestParam(required = false) String search,
        @RequestParam(defaultValue = "lastEnhancedAt") String sort,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(capabilityQueryService.list(user.id(), projectId, status, maturity, search, sort, page, size));
    }

    @GetMapping("/project-capabilities/{capabilityId}")
    ApiResponse<CapabilityDetailResponse> capability(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID capabilityId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(capabilityQueryService.detail(user.id(), capabilityId));
    }

    @GetMapping("/project-capabilities/{capabilityId}/evolutions")
    ApiResponse<CapabilityEvolutionPageResponse> evolutions(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID capabilityId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(capabilityQueryService.evolutions(user.id(), capabilityId, page, size));
    }

    @GetMapping("/project-capabilities/{capabilityId}/facts")
    ApiResponse<CapabilityFactPageResponse> capabilityFacts(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID capabilityId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(capabilityQueryService.facts(user.id(), capabilityId, page, size));
    }

    @GetMapping("/projects/{projectId}/capability-map/changes")
    ApiResponse<CapabilityEvolutionPageResponse> changes(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(capabilityQueryService.changes(user.id(), projectId, page, size));
    }

    @GetMapping("/projects/{projectId}/capability-map/attention")
    ApiResponse<CapabilityAttentionPageResponse> attention(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(capabilityQueryService.attention(user.id(), projectId, page, size));
    }

    @PostMapping("/projects/{projectId}/capability-map/retry")
    ApiResponse<Map<String, String>> retryMap(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        capabilityMapService.retry(user.id(), projectId);
        return ApiResponse.ok(Map.of("status", "QUEUED"));
    }

    @PostMapping("/projects/{projectId}/capabilities/analyze")
    ApiResponse<List<CapabilityCardResponse>> analyze(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(capabilityService.analyze(user.id(), projectId));
    }

    // V3.3.4: 能力分析异步任务。刷新/离开页面不丢，完成后前端重新拉取 capability-cards。
    @PostMapping("/projects/{projectId}/capabilities/analyze/jobs")
    ApiResponse<ProjectAnalysisJobResponse> startAnalyzeJob(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(projectAnalysisJobService.startCapabilityCardAnalysis(user.id(), projectId));
    }

    @GetMapping("/projects/{projectId}/capability-cards")
    ApiResponse<List<CapabilityCardResponse>> list(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(capabilityService.list(user.id(), projectId));
    }

    @GetMapping("/projects/{projectId}/capabilities/overview")
    ApiResponse<CapabilityAnalysisOverviewResponse> overview(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(capabilityService.overview(user.id(), projectId));
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
