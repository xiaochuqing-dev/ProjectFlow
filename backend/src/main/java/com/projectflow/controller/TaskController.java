package com.projectflow.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.projectflow.dto.ApiResponse;
import com.projectflow.dto.AuthDtos.AuthUser;
import com.projectflow.dto.TaskDtos.TaskRequest;
import com.projectflow.dto.TaskDtos.TaskResponse;
import com.projectflow.dto.TaskDtos.TaskStatusRequest;
import com.projectflow.service.AuthService;
import com.projectflow.service.TaskService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class TaskController {
    private final TaskService taskService;
    private final AuthService authService;

    public TaskController(TaskService taskService, AuthService authService) {
        this.taskService = taskService;
        this.authService = authService;
    }

    @GetMapping("/projects/{projectId}/tasks")
    ApiResponse<List<TaskResponse>> list(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(taskService.list(user.id(), projectId));
    }

    @PostMapping("/projects/{projectId}/tasks")
    ApiResponse<TaskResponse> create(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId,
        @Valid @RequestBody TaskRequest request
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(taskService.create(user.id(), projectId, request));
    }

    @GetMapping("/tasks/{taskId}")
    ApiResponse<TaskResponse> detail(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID taskId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(taskService.detail(user.id(), taskId));
    }

    @PatchMapping("/tasks/{taskId}/status")
    ApiResponse<TaskResponse> updateStatus(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID taskId,
        @Valid @RequestBody TaskStatusRequest request
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(taskService.updateStatus(user.id(), taskId, request.status()));
    }
}
