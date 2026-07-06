package com.projectflow.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.projectflow.dto.ApiResponse;
import com.projectflow.dto.AuthDtos.AuthUser;
import com.projectflow.dto.V2ProjectDtos.AgentBridgeRequest;
import com.projectflow.dto.V2ProjectDtos.AgentBridgeWriteResponse;
import com.projectflow.dto.V2ProjectDtos.AgentResultScanResponse;
import com.projectflow.dto.V2ProjectDtos.AgentTaskBriefResponse;
import com.projectflow.service.AuthService;
import com.projectflow.service.ProjectAgentBridgeService;
import com.projectflow.service.AgentBridgeHealthService;
import com.projectflow.dto.V33WorkflowDtos.AgentBridgeHealthResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class ProjectAgentBridgeController {
    private final ProjectAgentBridgeService projectAgentBridgeService;
    private final AuthService authService;
    private final AgentBridgeHealthService agentBridgeHealthService;

    public ProjectAgentBridgeController(
        ProjectAgentBridgeService projectAgentBridgeService,
        AuthService authService,
        AgentBridgeHealthService agentBridgeHealthService
    ) {
        this.projectAgentBridgeService = projectAgentBridgeService;
        this.authService = authService;
        this.agentBridgeHealthService = agentBridgeHealthService;
    }

    @GetMapping("/projects/{projectId}/agent-bridge/health")
    ApiResponse<AgentBridgeHealthResponse> health(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(agentBridgeHealthService.health(user.id(), projectId));
    }

    @PostMapping("/projects/{projectId}/agent-bridge/protocol")
    ApiResponse<AgentBridgeWriteResponse> writeProtocol(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId,
        @Valid @RequestBody AgentBridgeRequest request
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(projectAgentBridgeService.writeProtocol(user.id(), projectId, request));
    }

    @PostMapping("/projects/{projectId}/agent-bridge/scan")
    ApiResponse<AgentResultScanResponse> scanAgentResults(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId,
        @Valid @RequestBody AgentBridgeRequest request
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(projectAgentBridgeService.scanAgentResults(user.id(), projectId, request));
    }

    @PostMapping("/projects/{projectId}/agent-bridge/tasks/{taskId}/brief")
    ApiResponse<AgentTaskBriefResponse> writeTaskBrief(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId,
        @PathVariable UUID taskId,
        @Valid @RequestBody AgentBridgeRequest request
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(projectAgentBridgeService.writeTaskBrief(user.id(), projectId, taskId, request));
    }
}
