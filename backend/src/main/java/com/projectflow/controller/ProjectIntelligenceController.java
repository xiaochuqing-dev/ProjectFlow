package com.projectflow.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.projectflow.dto.ApiResponse;
import com.projectflow.dto.AuthDtos.AuthUser;
import com.projectflow.dto.V2ProjectDtos.AiSuggestionPatchRequest;
import com.projectflow.dto.V2ProjectDtos.AiSuggestionResponse;
import com.projectflow.dto.V2ProjectDtos.AnalyzeMaterialResponse;
import com.projectflow.dto.V2ProjectDtos.ApplySuggestionsRequest;
import com.projectflow.dto.V2ProjectDtos.ApplySuggestionsResponse;
import com.projectflow.dto.V2ProjectDtos.ProjectEvolutionRecordResponse;
import com.projectflow.dto.V2ProjectDtos.ProjectChangePatchRequest;
import com.projectflow.dto.V2ProjectDtos.ProjectChangeResponse;
import com.projectflow.dto.V2ProjectDtos.ProjectFactSourceResponse;
import com.projectflow.dto.V2ProjectDtos.ProjectAnalysisRecordResponse;
import com.projectflow.dto.V2ProjectDtos.ProjectAnalysisJobResponse;
import com.projectflow.dto.V2ProjectDtos.ProjectFileAnalysisRequest;
import com.projectflow.dto.V2ProjectDtos.ProjectImportAnalyzeResponse;
import com.projectflow.dto.V2ProjectDtos.ProjectLocalPathRequest;
import com.projectflow.dto.V2ProjectDtos.ProjectMaterialResponse;
import com.projectflow.dto.V2ProjectDtos.ProjectMaterialTextRequest;
import com.projectflow.dto.V2ProjectDtos.ProjectMemoryResponse;
import com.projectflow.dto.V2ProjectDtos.ProjectMemoryUpdateRequest;
import com.projectflow.dto.V2ProjectDtos.ProjectSnapshotResponse;
import com.projectflow.entity.MaterialSourceType;
import com.projectflow.service.AuthService;
import com.projectflow.service.ProjectChangeReviewService;
import com.projectflow.service.ProjectIntelligenceService;
import com.projectflow.service.ProjectAnalysisJobService;
import com.projectflow.service.ProjectAnalysisRecordService;
import com.projectflow.service.ProjectMemoryService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class ProjectIntelligenceController {
    private final ProjectIntelligenceService projectIntelligenceService;
    private final ProjectAnalysisJobService projectAnalysisJobService;
    private final ProjectAnalysisRecordService projectAnalysisRecordService;
    private final ProjectMemoryService projectMemoryService;
    private final ProjectChangeReviewService projectChangeReviewService;
    private final AuthService authService;

    public ProjectIntelligenceController(
        ProjectIntelligenceService projectIntelligenceService,
        ProjectAnalysisJobService projectAnalysisJobService,
        ProjectAnalysisRecordService projectAnalysisRecordService,
        ProjectMemoryService projectMemoryService,
        ProjectChangeReviewService projectChangeReviewService,
        AuthService authService
    ) {
        this.projectIntelligenceService = projectIntelligenceService;
        this.projectAnalysisJobService = projectAnalysisJobService;
        this.projectAnalysisRecordService = projectAnalysisRecordService;
        this.projectMemoryService = projectMemoryService;
        this.projectChangeReviewService = projectChangeReviewService;
        this.authService = authService;
    }

    @PostMapping("/project-imports/zip")
    ApiResponse<ProjectImportAnalyzeResponse> importProjectZip(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @RequestParam(value = "projectId", required = false) UUID projectId,
        @RequestPart("file") MultipartFile file
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(projectIntelligenceService.importProjectZip(user.id(), projectId, file));
    }

    @GetMapping("/projects/{projectId}/materials")
    ApiResponse<List<ProjectMaterialResponse>> listMaterials(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(projectIntelligenceService.listMaterials(user.id(), projectId));
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

    @GetMapping("/analysis-jobs/{jobId}")
    ApiResponse<ProjectAnalysisJobResponse> analysisJob(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID jobId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(projectAnalysisJobService.getJob(user.id(), jobId));
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

    // Legacy V2 material/suggestion endpoints remain for historical data compatibility.
    // The primary V3.2 intake flow is project zip import -> Evidence/ProjectChange review.
    @PostMapping("/projects/{projectId}/materials/text")
    @Deprecated(since = "3.2", forRemoval = false)
    ApiResponse<ProjectMaterialResponse> createTextMaterial(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId,
        @Valid @RequestBody ProjectMaterialTextRequest request
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(projectIntelligenceService.createTextMaterial(user.id(), projectId, request.sourceType(), request.content()));
    }

    @PostMapping("/projects/{projectId}/materials/file")
    @Deprecated(since = "3.2", forRemoval = false)
    ApiResponse<ProjectMaterialResponse> createFileMaterial(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId,
        @RequestParam(value = "sourceType", required = false) MaterialSourceType sourceType,
        @RequestPart("file") MultipartFile file
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(projectIntelligenceService.createFileMaterial(user.id(), projectId, sourceType, file));
    }

    @PostMapping("/projects/{projectId}/materials/zip")
    @Deprecated(since = "3.2", forRemoval = false)
    ApiResponse<ProjectMaterialResponse> createZipMaterial(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId,
        @RequestPart("file") MultipartFile file
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(projectIntelligenceService.createZipMaterial(user.id(), projectId, file));
    }

    @GetMapping("/project-materials/{materialId}")
    ApiResponse<ProjectMaterialResponse> materialDetail(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID materialId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(projectIntelligenceService.materialDetail(user.id(), materialId));
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

    @GetMapping("/projects/{projectId}/suggestions")
    @Deprecated(since = "3.2", forRemoval = false)
    ApiResponse<List<AiSuggestionResponse>> listSuggestions(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(projectIntelligenceService.listSuggestions(user.id(), projectId));
    }

    @PatchMapping("/ai-suggestions/{suggestionId}")
    @Deprecated(since = "3.2", forRemoval = false)
    ApiResponse<AiSuggestionResponse> updateSuggestion(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID suggestionId,
        @Valid @RequestBody AiSuggestionPatchRequest request
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(projectIntelligenceService.updateSuggestion(user.id(), suggestionId, request));
    }

    @PostMapping("/ai-suggestions/{suggestionId}/ignore")
    @Deprecated(since = "3.2", forRemoval = false)
    ApiResponse<AiSuggestionResponse> ignoreSuggestion(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID suggestionId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(projectIntelligenceService.ignoreSuggestion(user.id(), suggestionId));
    }

    @PostMapping("/projects/{projectId}/suggestions/apply")
    @Deprecated(since = "3.2", forRemoval = false)
    ApiResponse<ApplySuggestionsResponse> applySuggestions(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId,
        @Valid @RequestBody ApplySuggestionsRequest request
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(projectIntelligenceService.applySuggestions(user.id(), projectId, request.suggestionIds()));
    }

    @GetMapping("/projects/{projectId}/memory")
    ApiResponse<ProjectMemoryResponse> getMemory(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(projectMemoryService.getMemory(user.id(), projectId));
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

    @GetMapping("/projects/{projectId}/snapshots")
    ApiResponse<List<ProjectSnapshotResponse>> listSnapshots(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(projectIntelligenceService.listSnapshots(user.id(), projectId));
    }

    @GetMapping("/projects/{projectId}/evolution-records")
    ApiResponse<List<ProjectEvolutionRecordResponse>> listEvolutionRecords(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(projectIntelligenceService.listEvolutionRecords(user.id(), projectId));
    }
}
