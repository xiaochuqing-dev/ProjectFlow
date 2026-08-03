package com.projectflow.controller;

import static com.projectflow.dto.ProjectHistoryDtos.*;

import java.time.Instant;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
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
import com.projectflow.dto.V2ProjectDtos.ProjectAnalysisJobResponse;
import com.projectflow.service.AuthService;
import com.projectflow.service.ProjectAnalysisJobService;
import com.projectflow.service.ProjectHistoryReadService;

@RestController
@RequestMapping("/api/projects/{projectId}/history")
public class ProjectHistoryController {
    private final ProjectHistoryReadService historyService;
    private final ProjectAnalysisJobService jobService;
    private final AuthService authService;

    public ProjectHistoryController(
        ProjectHistoryReadService historyService,
        ProjectAnalysisJobService jobService,
        AuthService authService
    ) {
        this.historyService = historyService;
        this.jobService = jobService;
        this.authService = authService;
    }

    @PostMapping("/refresh")
    ApiResponse<ProjectAnalysisJobResponse> refresh(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId,
        @RequestBody(required = false) HistoryRefreshRequest request
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(jobService.startProjectHistoryRefresh(user.id(), projectId, request != null && request.forceRequested()));
    }

    @GetMapping("/overview")
    ApiResponse<HistoryOverviewResponse> overview(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(historyService.overview(user.id(), projectId));
    }

    @GetMapping("/chapters")
    ApiResponse<HistoryChapterPageResponse> chapters(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(historyService.chapters(user.id(), projectId, page, size));
    }

    @GetMapping("/chapters/{chapterId}")
    ApiResponse<HistoryChapterDetailResponse> chapter(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId,
        @PathVariable String chapterId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(historyService.chapter(user.id(), projectId, chapterId));
    }

    @GetMapping("/stories")
    ApiResponse<HistoryStoryPageResponse> stories(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId,
        @RequestParam(required = false) String subject,
        @RequestParam(defaultValue = "false") boolean attentionOnly,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(historyService.stories(user.id(), projectId, subject, attentionOnly, from, to, page, size));
    }

    @GetMapping("/stories/{storyId}")
    ApiResponse<HistoryStoryDetailResponse> story(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId,
        @PathVariable String storyId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(historyService.story(user.id(), projectId, storyId));
    }

    @GetMapping("/threads")
    ApiResponse<EvolutionThreadPageResponse> threads(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId,
        @RequestParam(required = false) String subject,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(historyService.threads(user.id(), projectId, subject, page, size));
    }

    @GetMapping("/threads/{threadId}")
    ApiResponse<EvolutionThreadDetailResponse> thread(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId,
        @PathVariable String threadId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(historyService.thread(user.id(), projectId, threadId));
    }

    @GetMapping("/events")
    ApiResponse<HistoryEventPageResponse> events(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId,
        @RequestParam(required = false) String sourceType,
        @RequestParam(required = false) String category,
        @RequestParam(required = false) String transition,
        @RequestParam(required = false) String authority,
        @RequestParam(required = false) String epistemicStatus,
        @RequestParam(required = false) String rewriteState,
        @RequestParam(required = false) String subject,
        @RequestParam(defaultValue = "false") boolean attentionOnly,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(historyService.events(
            user.id(), projectId, sourceType, category, transition, authority, epistemicStatus, rewriteState,
            subject, attentionOnly, from, to, page, size
        ));
    }

    @GetMapping("/events/{eventId}")
    ApiResponse<HistoryEventResponse> event(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId,
        @PathVariable UUID eventId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(historyService.event(user.id(), projectId, eventId));
    }

    @GetMapping("/events/{eventId}/evidence")
    ApiResponse<HistoryEvidenceResponse> evidence(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId,
        @PathVariable UUID eventId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(historyService.evidence(user.id(), projectId, eventId));
    }

    @GetMapping("/filters")
    ApiResponse<HistoryFiltersResponse> filters(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        historyService.overview(user.id(), projectId);
        return ApiResponse.ok(historyService.filters());
    }
}
