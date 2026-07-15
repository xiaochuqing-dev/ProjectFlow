package com.projectflow.controller;

import java.time.Instant;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.projectflow.dto.ApiResponse;
import com.projectflow.dto.AuthDtos.AuthUser;
import com.projectflow.dto.ProjectFactDtos.FactMemoryOverviewResponse;
import com.projectflow.dto.ProjectFactDtos.ProjectFactDetailResponse;
import com.projectflow.dto.ProjectFactDtos.ProjectFactHistoryStateResponse;
import com.projectflow.dto.ProjectFactDtos.ProjectFactPageResponse;
import com.projectflow.dto.ProjectFactDtos.ProjectRecordBatchDetailResponse;
import com.projectflow.dto.ProjectFactDtos.ProjectRecordBatchPageResponse;
import com.projectflow.entity.ProjectFactRecordStatus;
import com.projectflow.service.AuthService;
import com.projectflow.service.ProjectFactService;

@RestController
@RequestMapping("/api")
public class ProjectFactController {
    private final ProjectFactService factService;
    private final AuthService authService;

    public ProjectFactController(ProjectFactService factService, AuthService authService) {
        this.factService = factService;
        this.authService = authService;
    }

    @GetMapping("/projects/{projectId}/facts")
    ApiResponse<ProjectFactPageResponse> listFacts(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
        @RequestParam(required = false) UUID batchId,
        @RequestParam(required = false) ProjectFactRecordStatus recordStatus,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(factService.listFacts(user.id(), projectId, from, to, batchId, recordStatus, page, size));
    }

    @GetMapping("/project-facts/{factId}")
    ApiResponse<ProjectFactDetailResponse> getFact(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID factId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(factService.getFact(user.id(), factId));
    }

    @GetMapping("/projects/{projectId}/fact-memory-overview")
    ApiResponse<FactMemoryOverviewResponse> overview(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(factService.overview(user.id(), projectId));
    }

    @GetMapping("/projects/{projectId}/fact-history-state")
    ApiResponse<ProjectFactHistoryStateResponse> historyState(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(factService.historyState(user.id(), projectId));
    }

    @GetMapping("/projects/{projectId}/project-record-batches")
    ApiResponse<ProjectRecordBatchPageResponse> listBatches(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "40") int size
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(factService.listRecordBatches(user.id(), projectId, page, size));
    }

    @GetMapping("/project-record-batches/{batchId}")
    ApiResponse<ProjectRecordBatchDetailResponse> getBatch(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID batchId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "200") int size
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(factService.getRecordBatch(user.id(), batchId, page, size));
    }
}
