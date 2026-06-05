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

import com.projectflow.dto.AiOutputDtos.AiOutputRequest;
import com.projectflow.dto.AiOutputDtos.AiOutputResponse;
import com.projectflow.dto.ApiResponse;
import com.projectflow.dto.AuthDtos.AuthUser;
import com.projectflow.service.AiOutputService;
import com.projectflow.service.AuthService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class AiOutputController {
    private final AiOutputService aiOutputService;
    private final AuthService authService;

    public AiOutputController(AiOutputService aiOutputService, AuthService authService) {
        this.aiOutputService = aiOutputService;
        this.authService = authService;
    }

    @PostMapping("/projects/{projectId}/ai-outputs")
    ApiResponse<AiOutputResponse> generate(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId,
        @Valid @RequestBody AiOutputRequest request
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(aiOutputService.generate(user.id(), projectId, request));
    }

    @GetMapping("/projects/{projectId}/ai-outputs")
    ApiResponse<List<AiOutputResponse>> list(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(aiOutputService.list(user.id(), projectId));
    }

    @GetMapping("/ai-outputs/{outputId}")
    ApiResponse<AiOutputResponse> detail(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID outputId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(aiOutputService.detail(user.id(), outputId));
    }
}
