package com.projectflow.controller;

import java.util.Map;
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
import com.projectflow.dto.ProjectTimelineDtos.TimelineLifecycleResponse;
import com.projectflow.dto.ProjectTimelineDtos.TimelineOverviewResponse;
import com.projectflow.dto.ProjectTimelineDtos.TimelinePeriodDetailResponse;
import com.projectflow.dto.ProjectTimelineDtos.TimelinePeriodPageResponse;
import com.projectflow.dto.ProjectTimelineDtos.TimelineRetryRequest;
import com.projectflow.dto.ProjectTimelineDtos.TimelineThemeFactsResponse;
import com.projectflow.entity.TimelineGranularity;
import com.projectflow.service.AuthService;
import com.projectflow.service.ProjectTimelineService;
import com.projectflow.service.ProjectTimelineSummaryService;

@RestController
@RequestMapping("/api/projects/{projectId}/timeline")
public class ProjectTimelineController {
    private final ProjectTimelineService timelineService;
    private final ProjectTimelineSummaryService summaryService;
    private final AuthService authService;

    public ProjectTimelineController(
        ProjectTimelineService timelineService,
        ProjectTimelineSummaryService summaryService,
        AuthService authService
    ) {
        this.timelineService = timelineService;
        this.summaryService = summaryService;
        this.authService = authService;
    }

    @GetMapping("/overview")
    ApiResponse<TimelineOverviewResponse> overview(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(timelineService.overview(user.id(), projectId));
    }

    @GetMapping("/periods")
    ApiResponse<TimelinePeriodPageResponse> periods(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId,
        @RequestParam TimelineGranularity granularity,
        @RequestParam(required = false) String from,
        @RequestParam(required = false) String to,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(timelineService.periods(user.id(), projectId, granularity, from, to, page, size));
    }

    @GetMapping("/periods/{granularity}/{periodKey}")
    ApiResponse<TimelinePeriodDetailResponse> period(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId,
        @PathVariable TimelineGranularity granularity,
        @PathVariable String periodKey,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(timelineService.period(user.id(), projectId, granularity, periodKey, page, size));
    }

    @GetMapping("/themes/{themeId}/facts")
    ApiResponse<TimelineThemeFactsResponse> themeFacts(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId,
        @PathVariable UUID themeId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(timelineService.themeFacts(user.id(), projectId, themeId, page, size));
    }

    @GetMapping("/lifecycle")
    ApiResponse<TimelineLifecycleResponse> lifecycle(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        return ApiResponse.ok(timelineService.lifecycle(user.id(), projectId));
    }

    @PostMapping("/retry")
    ApiResponse<Map<String, String>> retry(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @PathVariable UUID projectId,
        @RequestBody TimelineRetryRequest request
    ) {
        AuthUser user = authService.currentUser(authorizationHeader);
        TimelineGranularity granularity;
        try {
            granularity = TimelineGranularity.valueOf(request.granularity().trim().toUpperCase());
        } catch (RuntimeException exception) {
            throw new com.projectflow.support.AppException(
                "INVALID_TIMELINE_PERIOD", "无效的项目历程粒度", org.springframework.http.HttpStatus.BAD_REQUEST
            );
        }
        summaryService.retry(user.id(), projectId, granularity, request.periodKey());
        return ApiResponse.ok(Map.of("status", "QUEUED", "granularity", granularity.name(), "periodKey", request.periodKey()));
    }
}
