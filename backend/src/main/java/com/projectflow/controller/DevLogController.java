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
import com.projectflow.dto.DevLogDtos.DevLogRequest;
import com.projectflow.dto.DevLogDtos.DevLogResponse;
import com.projectflow.service.AuthService;
import com.projectflow.service.DevLogService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class DevLogController {
    private final DevLogService devLogService;
    private final AuthService authService;

    public DevLogController(DevLogService devLogService, AuthService authService) {
        this.devLogService = devLogService;
        this.authService = authService;
    }

    @GetMapping("/projects/{projectId}/dev-logs")
    ApiResponse<List<DevLogResponse>> list(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(devLogService.list(user.id(), projectId));
    }

    @PostMapping("/projects/{projectId}/dev-logs")
    ApiResponse<DevLogResponse> create(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId,
        @Valid @RequestBody DevLogRequest request
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(devLogService.create(user.id(), projectId, request));
    }

    @GetMapping("/dev-logs/{logId}")
    ApiResponse<DevLogResponse> detail(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID logId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(devLogService.detail(user.id(), logId));
    }
}
