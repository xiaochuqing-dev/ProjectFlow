package com.projectflow.service;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
public class ProjectTimelineJobScheduler {
    private final ProjectTimelineSummaryService summaryService;
    private final ProjectAnalysisJobService jobService;

    public ProjectTimelineJobScheduler(
        ProjectTimelineSummaryService summaryService,
        ProjectAnalysisJobService jobService
    ) {
        this.summaryService = summaryService;
        this.jobService = jobService;
    }

    @EventListener
    public void request(ProjectTimelineRefreshRequestedEvent event) {
        String scope = summaryService.nextDirtyScope(event.projectId());
        if (scope.isBlank()) return;
        var job = jobService.startTimelineRefresh(event.userId(), event.projectId(), scope);
        summaryService.markQueued(scope, job.id());
    }
}
