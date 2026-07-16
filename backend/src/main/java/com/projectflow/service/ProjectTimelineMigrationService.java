package com.projectflow.service;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projectflow.entity.ProjectFact;
import com.projectflow.entity.ProjectFactFileRef;
import com.projectflow.repository.ProjectFactFileRefRepository;
import com.projectflow.repository.ProjectFactRepository;

@Service
public class ProjectTimelineMigrationService {
    private final ProjectFactRepository factRepository;
    private final ProjectFactFileRefRepository fileRefRepository;
    private final TimelinePeriodResolver resolver;

    public ProjectTimelineMigrationService(
        ProjectFactRepository factRepository,
        ProjectFactFileRefRepository fileRefRepository,
        TimelinePeriodResolver resolver
    ) {
        this.factRepository = factRepository;
        this.fileRefRepository = fileRefRepository;
        this.resolver = resolver;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(200)
    @Transactional
    public void backfillAssignmentsAndFileRefs() {
        int page = 0;
        while (true) {
            var facts = factRepository.findAll(PageRequest.of(page, 200, Sort.by("id")));
            for (ProjectFact fact : facts.getContent()) {
                TimelinePeriodResolver.Assignment assignment = resolver.assign(fact);
                fact.assignTimeline(assignment.eventAt(), assignment.dayKey(), assignment.weekKey(), assignment.monthKey());
                for (String value : fact.getAffectedFiles()) {
                    String path = value == null ? "" : value.trim().replace('\\', '/');
                    if (!path.isBlank() && path.length() <= 1_000
                        && !fileRefRepository.existsByFactIdAndFilePath(fact.getId(), path)) {
                        fileRefRepository.save(new ProjectFactFileRef(fact.getProjectId(), fact.getId(), path));
                    }
                }
            }
            factRepository.saveAll(facts.getContent());
            if (!facts.hasNext()) break;
            page++;
        }
    }
}
