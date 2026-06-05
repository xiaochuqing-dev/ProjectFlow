package com.projectflow.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.projectflow.dto.ApiResponse;
import com.projectflow.dto.AuthDtos.AuthUser;
import com.projectflow.dto.DevLogDtos.DevLogResponse;
import com.projectflow.dto.MarkdownImportDtos.ImportRecordResponse;
import com.projectflow.dto.MarkdownImportDtos.MarkdownConfirmRequest;
import com.projectflow.dto.MarkdownImportDtos.MarkdownPreviewRequest;
import com.projectflow.dto.MarkdownImportDtos.MarkdownPreviewResponse;
import com.projectflow.service.AuthService;
import com.projectflow.service.MarkdownImportService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class MarkdownImportController {
    private final MarkdownImportService markdownImportService;
    private final AuthService authService;

    public MarkdownImportController(MarkdownImportService markdownImportService, AuthService authService) {
        this.markdownImportService = markdownImportService;
        this.authService = authService;
    }

    @PostMapping("/imports/preview")
    ApiResponse<MarkdownPreviewResponse> preview(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @Valid @RequestBody MarkdownPreviewRequest request
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(markdownImportService.preview(user.id(), request));
    }

    @PostMapping("/imports/confirm")
    ApiResponse<DevLogResponse> confirm(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @Valid @RequestBody MarkdownConfirmRequest request
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(markdownImportService.confirm(user.id(), request));
    }

    @GetMapping("/projects/{projectId}/imports")
    ApiResponse<List<ImportRecordResponse>> list(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(markdownImportService.list(user.id(), projectId));
    }
}
