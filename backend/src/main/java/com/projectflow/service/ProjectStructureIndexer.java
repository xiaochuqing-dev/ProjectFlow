package com.projectflow.service;

import com.projectflow.dto.ProjectUnderstandingDtos.ProjectStructureIndexResponse;

/**
 * Stable boundary for repository structure providers. V3.6 composes the bounded
 * filesystem/manifest fallback with an official SCIP protocol consumer. Future
 * providers remain replaceable without changing the understanding service.
 */
public interface ProjectStructureIndexer {
    ProjectStructureIndexResponse build(RepositoryIntakeService.ScanResult scan);
}
