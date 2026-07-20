package com.projectflow.controller;

import static com.projectflow.dto.ProjectMemoryGatewayDtos.*;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.projectflow.dto.ApiResponse;
import com.projectflow.dto.AuthDtos.AuthUser;
import com.projectflow.service.AuthService;
import com.projectflow.service.ProjectMemoryAuditService;
import com.projectflow.service.ProjectMemoryGatewayService;

@RestController
@RequestMapping("/api")
public class ProjectMemoryGatewayController {
    private final ProjectMemoryGatewayService gateway;
    private final ProjectMemoryAuditService audit;
    private final AuthService authService;

    public ProjectMemoryGatewayController(
        ProjectMemoryGatewayService gateway,
        ProjectMemoryAuditService audit,
        AuthService authService
    ) {
        this.gateway = gateway;
        this.audit = audit;
        this.authService = authService;
    }

    @GetMapping("/project-memory/projects")
    ApiResponse<MemoryProjectListResponse> projects(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @RequestHeader(value = "X-ProjectFlow-Caller", required = false) String caller
    ) {
        AuthUser user = authService.currentUser(authorization);
        return read(user, null, "list_projects", caller, "", "PROJECT", "", gateway::listProjects,
            result -> result.items().size());
    }

    @GetMapping("/projects/{projectId}/project-memory/snapshot")
    ApiResponse<ProjectSnapshotResponse> snapshot(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @RequestHeader(value = "X-ProjectFlow-Caller", required = false) String caller,
        @PathVariable UUID projectId
    ) {
        AuthUser user = authService.currentUser(authorization);
        return read(user, projectId, "get_project_snapshot", caller, "", "PROJECT", "detail=compact",
            ignored -> gateway.snapshot(user.id(), projectId), ignored -> 1);
    }

    @GetMapping("/projects/{projectId}/project-memory/recent-changes")
    ApiResponse<MemoryFactPageResponse> recentChanges(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @RequestHeader(value = "X-ProjectFlow-Caller", required = false) String caller,
        @PathVariable UUID projectId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
        @RequestParam(defaultValue = "true") boolean includeAttention,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size,
        @RequestParam(defaultValue = "compact") String detailLevel
    ) {
        AuthUser user = authService.currentUser(authorization);
        String filters = "from=" + from + ",to=" + to + ",attention=" + includeAttention
            + ",page=" + page + ",size=" + size + ",detail=" + detailLevel;
        return read(user, projectId, "get_recent_changes", caller, "", "FACT", filters,
            ignored -> gateway.recentChanges(user.id(), projectId, from, to, includeAttention, page, size, detailLevel),
            result -> result.items().size());
    }

    @GetMapping("/projects/{projectId}/project-memory/search")
    ApiResponse<MemorySearchResponse> search(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @RequestHeader(value = "X-ProjectFlow-Caller", required = false) String caller,
        @PathVariable UUID projectId,
        @RequestParam String query,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
        @RequestParam(required = false) String entityTypes,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size,
        @RequestParam(defaultValue = "compact") String detailLevel
    ) {
        AuthUser user = authService.currentUser(authorization);
        String filters = "from=" + from + ",to=" + to + ",page=" + page + ",size=" + size + ",detail=" + detailLevel;
        return read(user, projectId, "search_project_memory", caller, query, entityTypes, filters,
            ignored -> gateway.search(user.id(), projectId, query, from, to, entityTypes, page, size, detailLevel),
            result -> result.items().size());
    }

    @GetMapping("/projects/{projectId}/project-memory/timeline")
    ApiResponse<MemoryTimelineQueryResponse> timeline(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @RequestHeader(value = "X-ProjectFlow-Caller", required = false) String caller,
        @PathVariable UUID projectId,
        @RequestParam(defaultValue = "MONTH") String granularity,
        @RequestParam(required = false) String periodKey,
        @RequestParam(required = false) String from,
        @RequestParam(required = false) String to,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size,
        @RequestParam(defaultValue = "compact") String detailLevel
    ) {
        AuthUser user = authService.currentUser(authorization);
        String filters = "granularity=" + granularity + ",period=" + periodKey + ",from=" + from + ",to=" + to
            + ",page=" + page + ",size=" + size + ",detail=" + detailLevel;
        return read(user, projectId, "get_project_timeline", caller, "", "TIMELINE", filters,
            ignored -> gateway.timeline(user.id(), projectId, granularity, periodKey, from, to, page, size, detailLevel),
            ignored -> 1);
    }

    @GetMapping("/projects/{projectId}/project-memory/capabilities")
    ApiResponse<MemoryCapabilityPageResponse> capabilities(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @RequestHeader(value = "X-ProjectFlow-Caller", required = false) String caller,
        @PathVariable UUID projectId,
        @RequestParam(defaultValue = "true") boolean activeOnly,
        @RequestParam(required = false) String maturity,
        @RequestParam(required = false) String search,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size,
        @RequestParam(defaultValue = "compact") String detailLevel
    ) {
        AuthUser user = authService.currentUser(authorization);
        String filters = "active=" + activeOnly + ",maturity=" + maturity + ",page=" + page + ",size=" + size
            + ",detail=" + detailLevel;
        return read(user, projectId, "list_project_capabilities", caller, search, "CAPABILITY", filters,
            ignored -> gateway.capabilities(user.id(), projectId, activeOnly, maturity, search, page, size, detailLevel),
            result -> result.items().size());
    }

    @GetMapping("/projects/{projectId}/project-memory/capabilities/{capabilityId}/evolution")
    ApiResponse<MemoryEvolutionPageResponse> evolution(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @RequestHeader(value = "X-ProjectFlow-Caller", required = false) String caller,
        @PathVariable UUID projectId,
        @PathVariable UUID capabilityId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size,
        @RequestParam(defaultValue = "compact") String detailLevel
    ) {
        AuthUser user = authService.currentUser(authorization);
        String filters = "capability=" + capabilityId + ",page=" + page + ",size=" + size + ",detail=" + detailLevel;
        return read(user, projectId, "get_capability_evolution", caller, "", "EVOLUTION", filters,
            ignored -> gateway.capabilityEvolution(user.id(), projectId, capabilityId, page, size, detailLevel),
            result -> result.items().size());
    }

    @GetMapping("/projects/{projectId}/project-memory/facts/{factId}/trace")
    ApiResponse<MemoryFactTraceResponse> trace(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @RequestHeader(value = "X-ProjectFlow-Caller", required = false) String caller,
        @PathVariable UUID projectId,
        @PathVariable UUID factId,
        @RequestParam(defaultValue = "compact") String detailLevel
    ) {
        AuthUser user = authService.currentUser(authorization);
        return read(user, projectId, "trace_project_fact", caller, "", "FACT", "detail=" + detailLevel,
            ignored -> gateway.traceFact(user.id(), projectId, factId, detailLevel), ignored -> 1);
    }

    @GetMapping("/projects/{projectId}/project-memory/brief")
    ApiResponse<MemoryBriefResponse> brief(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @RequestHeader(value = "X-ProjectFlow-Caller", required = false) String caller,
        @PathVariable UUID projectId,
        @RequestParam(defaultValue = "6000") int sizeBudget
    ) {
        AuthUser user = authService.currentUser(authorization);
        return read(user, projectId, "get_project_brief", caller, "", "PROJECT", "budget=" + sizeBudget,
            ignored -> gateway.brief(user.id(), projectId, sizeBudget), ignored -> 1);
    }

    private <T> ApiResponse<T> read(
        AuthUser user, UUID projectId, String operation, String caller, String query,
        String entityTypes, String filters, Function<UUID, T> action, Function<T, Integer> count
    ) {
        long started = System.nanoTime();
        try {
            T result = action.apply(user.id());
            audit.record(user.id(), projectId, operation, count.apply(result), elapsedMs(started),
                "SUCCESS", caller, query, entityTypes, filters);
            return ApiResponse.ok(result);
        } catch (RuntimeException exception) {
            audit.record(user.id(), projectId, operation, 0, elapsedMs(started),
                "ERROR", caller, query, entityTypes, filters);
            throw exception;
        }
    }

    private <T> ApiResponse<T> read(
        AuthUser user, UUID projectId, String operation, String caller, String query,
        String entityTypes, String filters, Supplier<T> action, Function<T, Integer> count
    ) {
        return read(user, projectId, operation, caller, query, entityTypes, filters, ignored -> action.get(), count);
    }

    private long elapsedMs(long started) { return Math.max(0, (System.nanoTime() - started) / 1_000_000); }
}
