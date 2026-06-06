package com.projectflow.controller;

import java.util.List;
import java.util.UUID;

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
import com.projectflow.dto.V2ProjectDtos.ProjectImportAnalyzeResponse;
import com.projectflow.dto.V2ProjectDtos.ProjectMaterialResponse;
import com.projectflow.dto.V2ProjectDtos.ProjectMaterialTextRequest;
import com.projectflow.dto.V2ProjectDtos.ProjectMemoryResponse;
import com.projectflow.dto.V2ProjectDtos.ProjectSnapshotResponse;
import com.projectflow.entity.MaterialSourceType;
import com.projectflow.service.AuthService;
import com.projectflow.service.ProjectIntelligenceService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class ProjectIntelligenceController {
    private final ProjectIntelligenceService projectIntelligenceService;
    private final AuthService authService;

    public ProjectIntelligenceController(ProjectIntelligenceService projectIntelligenceService, AuthService authService) {
        this.projectIntelligenceService = projectIntelligenceService;
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

    @PostMapping("/projects/{projectId}/materials/text")
    ApiResponse<ProjectMaterialResponse> createTextMaterial(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId,
        @Valid @RequestBody ProjectMaterialTextRequest request
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(projectIntelligenceService.createTextMaterial(user.id(), projectId, request.sourceType(), request.content()));
    }

    @PostMapping("/projects/{projectId}/materials/file")
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
    ApiResponse<AnalyzeMaterialResponse> analyzeMaterial(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID materialId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(projectIntelligenceService.analyzeMaterial(user.id(), materialId));
    }

    @GetMapping("/projects/{projectId}/suggestions")
    ApiResponse<List<AiSuggestionResponse>> listSuggestions(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(projectIntelligenceService.listSuggestions(user.id(), projectId));
    }

    @PatchMapping("/ai-suggestions/{suggestionId}")
    ApiResponse<AiSuggestionResponse> updateSuggestion(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID suggestionId,
        @Valid @RequestBody AiSuggestionPatchRequest request
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(projectIntelligenceService.updateSuggestion(user.id(), suggestionId, request));
    }

    @PostMapping("/ai-suggestions/{suggestionId}/ignore")
    ApiResponse<AiSuggestionResponse> ignoreSuggestion(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID suggestionId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(projectIntelligenceService.ignoreSuggestion(user.id(), suggestionId));
    }

    @PostMapping("/projects/{projectId}/suggestions/apply")
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
        return ApiResponse.ok(projectIntelligenceService.getMemory(user.id(), projectId));
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
