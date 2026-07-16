package com.projectflow.service;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
public class ProjectCapabilityJobScheduler {
    private final ProjectCapabilityMapService mapService;
    private final ProjectAnalysisJobService jobService;

    public ProjectCapabilityJobScheduler(ProjectCapabilityMapService mapService, ProjectAnalysisJobService jobService) {
        this.mapService = mapService;
        this.jobService = jobService;
    }

    @EventListener
    public void request(ProjectCapabilityRefreshRequestedEvent event) {
        String scope = mapService.nextDirtyScope(event.projectId());
        if (scope.isBlank()) return;
        var job = jobService.startCapabilityMapRefresh(event.userId(), event.projectId(), scope);
        mapService.markQueued(scope, job.id());
    }
}
