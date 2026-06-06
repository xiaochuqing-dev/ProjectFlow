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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.projectflow.dto.AiProviderDtos.AiProviderRequest;
import com.projectflow.dto.AiProviderDtos.AiProviderResponse;
import com.projectflow.dto.AiProviderDtos.ProviderTestResponse;
import com.projectflow.dto.ApiResponse;
import com.projectflow.dto.AuthDtos.AuthUser;
import com.projectflow.service.AiProviderService;
import com.projectflow.service.AuthService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/ai-providers")
public class AiProviderController {
    private final AiProviderService aiProviderService;
    private final AuthService authService;

    public AiProviderController(AiProviderService aiProviderService, AuthService authService) {
        this.aiProviderService = aiProviderService;
        this.authService = authService;
    }

    @GetMapping
    ApiResponse<List<AiProviderResponse>> list(@RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(aiProviderService.list(user.id()));
    }

    @PostMapping
    ApiResponse<AiProviderResponse> create(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @Valid @RequestBody AiProviderRequest request
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(aiProviderService.create(user.id(), request));
    }

    @PatchMapping("/{providerId}")
    ApiResponse<AiProviderResponse> update(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID providerId,
        @Valid @RequestBody AiProviderRequest request
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(aiProviderService.update(user.id(), providerId, request));
    }

    @DeleteMapping("/{providerId}")
    ApiResponse<Void> delete(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID providerId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        aiProviderService.delete(user.id(), providerId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{providerId}/test")
    ApiResponse<ProviderTestResponse> test(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID providerId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(aiProviderService.test(user.id(), providerId));
    }
}
