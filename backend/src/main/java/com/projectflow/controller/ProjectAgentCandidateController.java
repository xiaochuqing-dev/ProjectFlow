package com.projectflow.controller;

import static com.projectflow.dto.ProjectAgentCandidateDtos.*;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.projectflow.dto.ApiResponse;
import com.projectflow.dto.AuthDtos.AuthUser;
import com.projectflow.service.AuthService;
import com.projectflow.service.ProjectAgentCandidateService;
import com.projectflow.service.ProjectMemoryAuditService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/projects/{projectId}/agent-candidates")
public class ProjectAgentCandidateController {
    private final ProjectAgentCandidateService candidateService;
    private final ProjectMemoryAuditService auditService;
    private final AuthService authService;

    public ProjectAgentCandidateController(
        ProjectAgentCandidateService candidateService,
        ProjectMemoryAuditService auditService,
        AuthService authService
    ) {
        this.candidateService = candidateService;
        this.auditService = auditService;
        this.authService = authService;
    }

    @PostMapping
    ApiResponse<AgentCandidateResponse> submit(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @RequestHeader(value = "X-ProjectFlow-Caller", required = false) String caller,
        @PathVariable UUID projectId,
        @Valid @RequestBody SubmitAgentCandidateRequest request
    ) {
        AuthUser user = authService.currentUser(authorization);
        return ApiResponse.ok(candidateService.submit(user.id(), projectId, request, caller));
    }

    @PostMapping("/work-results")
    ApiResponse<AgentWorkResultResponse> submitWorkResult(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @RequestHeader(value = "X-ProjectFlow-Caller", required = false) String caller,
        @PathVariable UUID projectId,
        @Valid @RequestBody SubmitAgentWorkResultRequest request
    ) {
        AuthUser user = authService.currentUser(authorization);
        long started = System.nanoTime();
        try {
            AgentWorkResultResponse result = candidateService.submitWorkResult(
                user.id(), projectId, request, caller
            );
            auditService.record(
                user.id(), projectId, "submit_agent_work_result", result.candidates().size(),
                elapsedMs(started), "SUCCESS", caller, "", "AGENT_CANDIDATE",
                "changedFiles=" + result.changedFiles().size() + ",validation=" + result.validationStatus()
            );
            return ApiResponse.ok(result);
        } catch (RuntimeException exception) {
            auditService.record(
                user.id(), projectId, "submit_agent_work_result", 0, elapsedMs(started),
                "ERROR", caller, "", "AGENT_CANDIDATE", "workResult=true"
            );
            throw exception;
        }
    }

    @GetMapping
    ApiResponse<AgentCandidatePageResponse> list(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @PathVariable UUID projectId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        AuthUser user = authService.currentUser(authorization);
        return ApiResponse.ok(candidateService.list(user.id(), projectId, page, size));
    }

    private static long elapsedMs(long started) {
        return Math.max(0, (System.nanoTime() - started) / 1_000_000);
    }
}
