package com.projectflow.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.projectflow.dto.ApiResponse;
import com.projectflow.dto.AuthDtos.AuthUser;
import com.projectflow.dto.V2ProjectDtos.AnalyzeMaterialResponse;
import com.projectflow.dto.V2ProjectDtos.CapabilityInterpretRequest;
import com.projectflow.dto.V2ProjectDtos.ProjectAnalysisJobResponse;
import com.projectflow.dto.V2ProjectDtos.ProjectAnalysisRecordResponse;
import com.projectflow.dto.V2ProjectDtos.ProjectFileAnalysisRequest;
import com.projectflow.service.AuthService;
import com.projectflow.service.ProjectAnalysisJobService;
import com.projectflow.service.ProjectAnalysisRecordService;
import com.projectflow.service.ProjectIntelligenceService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class ProjectAnalysisController {
    private final ProjectAnalysisJobService projectAnalysisJobService;
    private final ProjectAnalysisRecordService projectAnalysisRecordService;
    private final ProjectIntelligenceService projectIntelligenceService;
    private final AuthService authService;

    public ProjectAnalysisController(
        ProjectAnalysisJobService projectAnalysisJobService,
        ProjectAnalysisRecordService projectAnalysisRecordService,
        ProjectIntelligenceService projectIntelligenceService,
        AuthService authService
    ) {
        this.projectAnalysisJobService = projectAnalysisJobService;
        this.projectAnalysisRecordService = projectAnalysisRecordService;
        this.projectIntelligenceService = projectIntelligenceService;
        this.authService = authService;
    }

    @PostMapping("/projects/{projectId}/analysis/run")
    ApiResponse<ProjectAnalysisJobResponse> runProjectAnalysis(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(projectAnalysisJobService.startProjectAnalysis(user.id(), projectId));
    }

    @PostMapping("/projects/{projectId}/files/analyze")
    ApiResponse<ProjectAnalysisJobResponse> analyzeProjectFile(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId,
        @Valid @RequestBody ProjectFileAnalysisRequest request
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(projectAnalysisJobService.startFileAnalysis(user.id(), projectId, request.path()));
    }

    @PostMapping("/projects/{projectId}/capabilities/interpret")
    ApiResponse<ProjectAnalysisJobResponse> interpretCapability(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId,
        @Valid @RequestBody CapabilityInterpretRequest request
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(projectAnalysisJobService.startCapabilityInterpret(user.id(), projectId, request.capabilityFact()));
    }

    @GetMapping("/analysis-jobs/{jobId}")
    ApiResponse<ProjectAnalysisJobResponse> analysisJob(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID jobId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(projectAnalysisJobService.getJob(user.id(), jobId));
    }

    @PostMapping("/analysis-jobs/{jobId}/acknowledge")
    ApiResponse<ProjectAnalysisJobResponse> acknowledgeAnalysisFailure(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID jobId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(projectAnalysisJobService.acknowledgeFailure(user.id(), jobId));
    }

    @PostMapping("/analysis-jobs/{jobId}/cancel")
    ApiResponse<ProjectAnalysisJobResponse> cancelAnalysisJob(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID jobId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(projectAnalysisJobService.cancel(user.id(), jobId));
    }

    @PostMapping("/analysis-jobs/{jobId}/retry")
    ApiResponse<ProjectAnalysisJobResponse> retryAnalysisJob(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID jobId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(projectAnalysisJobService.retry(user.id(), jobId));
    }

    @GetMapping("/projects/{projectId}/analysis-jobs")
    ApiResponse<List<ProjectAnalysisJobResponse>> listAnalysisJobs(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(projectAnalysisJobService.listProjectJobs(user.id(), projectId));
    }

    @GetMapping("/projects/{projectId}/analysis-records")
    ApiResponse<List<ProjectAnalysisRecordResponse>> listAnalysisRecords(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(projectAnalysisRecordService.listAnalysisRecords(user.id(), projectId));
    }

    @GetMapping("/project-analysis-records/{recordId}")
    ApiResponse<ProjectAnalysisRecordResponse> analysisRecordDetail(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID recordId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(projectAnalysisRecordService.detail(user.id(), recordId));
    }

    @DeleteMapping("/project-analysis-records/{recordId}")
    ApiResponse<Void> deleteAnalysisRecord(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID recordId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        projectAnalysisRecordService.delete(user.id(), recordId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/project-materials/{materialId}/analyze")
    @Deprecated(since = "3.2", forRemoval = false)
    ApiResponse<AnalyzeMaterialResponse> analyzeMaterial(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID materialId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(projectIntelligenceService.analyzeMaterial(user.id(), materialId));
    }
}
