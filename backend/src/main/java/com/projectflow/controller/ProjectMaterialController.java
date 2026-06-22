package com.projectflow.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
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
import com.projectflow.dto.V2ProjectDtos.ProjectMaterialResponse;
import com.projectflow.dto.V2ProjectDtos.ProjectMaterialTextRequest;
import com.projectflow.entity.MaterialSourceType;
import com.projectflow.service.AuthService;
import com.projectflow.service.ProjectMaterialService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class ProjectMaterialController {
    private final ProjectMaterialService projectMaterialService;
    private final AuthService authService;

    public ProjectMaterialController(ProjectMaterialService projectMaterialService, AuthService authService) {
        this.projectMaterialService = projectMaterialService;
        this.authService = authService;
    }

    @GetMapping("/projects/{projectId}/materials")
    ApiResponse<List<ProjectMaterialResponse>> listMaterials(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(projectMaterialService.listMaterials(user.id(), projectId));
    }

    @PostMapping("/projects/{projectId}/materials/text")
    @Deprecated(since = "3.2", forRemoval = false)
    ApiResponse<ProjectMaterialResponse> createTextMaterial(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId,
        @Valid @RequestBody ProjectMaterialTextRequest request
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(projectMaterialService.createTextMaterial(user.id(), projectId, request.sourceType(), request.content()));
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
        return ApiResponse.ok(projectMaterialService.createFileMaterial(user.id(), projectId, sourceType, file));
    }

    @PostMapping("/projects/{projectId}/materials/zip")
    @Deprecated(since = "3.2", forRemoval = false)
    ApiResponse<ProjectMaterialResponse> createZipMaterial(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId,
        @RequestPart("file") MultipartFile file
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(projectMaterialService.createZipMaterial(user.id(), projectId, file));
    }

    @GetMapping("/project-materials/{materialId}")
    ApiResponse<ProjectMaterialResponse> materialDetail(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID materialId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(projectMaterialService.materialDetail(user.id(), materialId));
    }
}
