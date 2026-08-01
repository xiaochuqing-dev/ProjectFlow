package com.projectflow.controller;

import static com.projectflow.dto.ProjectAgentHistoryDtos.*;
import static com.projectflow.dto.ProjectAgentRevalidationDtos.*;

import java.util.UUID;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.projectflow.dto.ApiResponse;
import com.projectflow.dto.AuthDtos.AuthUser;
import com.projectflow.service.AuthService;
import com.projectflow.service.ProjectAgentHistoryService;
import com.projectflow.service.ProjectAgentRevalidationService;
import com.projectflow.service.ProjectMemoryAuditService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class ProjectAgentHistoryController {
    private final ProjectAgentHistoryService historyService;
    private final ProjectAgentRevalidationService revalidationService;
    private final ProjectMemoryAuditService audit;
    private final AuthService authService;

    public ProjectAgentHistoryController(
        ProjectAgentHistoryService historyService,
        ProjectAgentRevalidationService revalidationService,
        ProjectMemoryAuditService audit,
        AuthService authService
    ) {
        this.historyService = historyService;
        this.revalidationService = revalidationService;
        this.audit = audit;
        this.authService = authService;
    }

    @PostMapping("/projects/{projectId}/project-memory/revalidate")
    ApiResponse<AgentRevalidationResponse> revalidate(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @RequestHeader(value = "X-ProjectFlow-Caller", required = false) String caller,
        @PathVariable UUID projectId,
        @Valid @RequestBody AgentRevalidationRequest request
    ) {
        AuthUser user = authService.currentUser(authorization);
        long started = System.nanoTime();
        try {
            AgentRevalidationResponse result = revalidationService.revalidate(user.id(), projectId, request);
            audit.record(
                user.id(), projectId, "revalidate_project_evidence", result.verifiedEvidenceRefs().size(),
                elapsedMs(started), "SUCCESS", caller, "", "FACT,EVIDENCE,CONTEXT_PACKAGE",
                "action=" + result.action() + ",status=" + result.status()
            );
            return ApiResponse.ok(result);
        } catch (RuntimeException exception) {
            audit.record(
                user.id(), projectId, "revalidate_project_evidence", 0, elapsedMs(started),
                "ERROR", caller, "", "FACT,EVIDENCE,CONTEXT_PACKAGE", "action=redacted"
            );
            throw exception;
        }
    }

    @GetMapping("/project-memory/portfolio")
    ApiResponse<AgentProjectCatalogResponse> catalog(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @RequestHeader(value = "X-ProjectFlow-Caller", required = false) String caller
    ) {
        AuthUser user = authService.currentUser(authorization);
        return read(user, null, "list_agent_project_catalog", caller, "", "PROJECT", "",
            () -> historyService.catalog(user.id()), result -> result.items().size());
    }

    @GetMapping("/project-memory/portfolio/search")
    ApiResponse<PortfolioSearchResponse> search(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @RequestHeader(value = "X-ProjectFlow-Caller", required = false) String caller,
        @RequestParam String query,
        @RequestParam(defaultValue = "20") int size
    ) {
        AuthUser user = authService.currentUser(authorization);
        return read(user, null, "search_project_portfolio", caller, query, "FACT,TIMELINE,CAPABILITY",
            "size=" + size, () -> historyService.searchPortfolio(user.id(), query, size),
            result -> result.items().size());
    }

    @GetMapping("/projects/{projectId}/project-memory/evidence/{evidenceId}")
    ApiResponse<AgentEvidenceResponse> evidence(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @RequestHeader(value = "X-ProjectFlow-Caller", required = false) String caller,
        @PathVariable UUID projectId,
        @PathVariable String evidenceId
    ) {
        AuthUser user = authService.currentUser(authorization);
        return read(user, projectId, "get_project_evidence", caller, "", "EVIDENCE",
            "evidenceIdHash=" + ProjectMemoryAuditService.queryHash(evidenceId),
            () -> historyService.evidence(user.id(), projectId, evidenceId), ignored -> 1);
    }

    @GetMapping("/projects/{projectId}/project-memory/knowledge")
    ApiResponse<AgentKnowledgeResponse> knowledge(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @RequestHeader(value = "X-ProjectFlow-Caller", required = false) String caller,
        @PathVariable UUID projectId,
        @RequestParam(defaultValue = "100") int size
    ) {
        AuthUser user = authService.currentUser(authorization);
        return read(user, projectId, "get_project_knowledge", caller, "", "FACT",
            "size=" + size, () -> historyService.knowledge(user.id(), projectId, size),
            result -> result.items().size());
    }

    @GetMapping("/projects/{projectId}/project-memory/context-package")
    ApiResponse<AgentContextPackageResponse> contextPackage(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @RequestHeader(value = "X-ProjectFlow-Caller", required = false) String caller,
        @PathVariable UUID projectId,
        @RequestParam(defaultValue = "8000") int sizeBudget,
        @RequestParam(defaultValue = "") String taskDescription,
        @RequestParam(required = false) List<String> scope,
        @RequestParam(defaultValue = "CURRENT_SNAPSHOT") String revisionPreference,
        @RequestParam(defaultValue = "STANDARD") String evidenceDepth
    ) {
        AuthUser user = authService.currentUser(authorization);
        return read(user, projectId, "get_project_context_package", caller, taskDescription, "PROJECT,FACT,EVIDENCE",
            "budget=" + sizeBudget + ",scopeCount=" + (scope == null ? 0 : scope.size())
                + ",revisionPreference=" + revisionPreference + ",evidenceDepth=" + evidenceDepth,
            () -> historyService.contextPackage(
                user.id(), projectId, taskDescription, scope, revisionPreference, evidenceDepth, sizeBudget
            ),
            result -> result.currentStrongFacts().size() + result.declaredMaterial().size()
                + result.inferredCandidates().size() + result.conflicts().size());
    }

    private <T> ApiResponse<T> read(
        AuthUser user,
        UUID projectId,
        String operation,
        String caller,
        String query,
        String entityTypes,
        String filters,
        Supplier<T> action,
        Function<T, Integer> count
    ) {
        long started = System.nanoTime();
        try {
            T result = action.get();
            audit.record(user.id(), projectId, operation, count.apply(result), elapsedMs(started),
                "SUCCESS", caller, query, entityTypes, filters);
            return ApiResponse.ok(result);
        } catch (RuntimeException exception) {
            audit.record(user.id(), projectId, operation, 0, elapsedMs(started),
                "ERROR", caller, query, entityTypes, filters);
            throw exception;
        }
    }

    private long elapsedMs(long started) {
        return Math.max(0, (System.nanoTime() - started) / 1_000_000);
    }
}
