package com.projectflow.service;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
public class ProjectFactHistoryJobScheduler {
    private final ProjectFactHistoryService historyService;
    private final ProjectAnalysisJobService jobService;

    public ProjectFactHistoryJobScheduler(
        ProjectFactHistoryService historyService,
        ProjectAnalysisJobService jobService
    ) {
        this.historyService = historyService;
        this.jobService = jobService;
    }

    @EventListener
    public void request(ProjectFactHistoryRequestedEvent event) {
        boolean shouldStart = historyService.registerRequest(
            event.projectId(), event.upperBoundSha(), event.modelConfigured()
        );
        if (shouldStart) {
            jobService.startProjectFactHistoryRebuild(event.userId(), event.projectId(), event.upperBoundSha());
        }
    }
}
