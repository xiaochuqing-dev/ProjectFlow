package com.projectflow.service;

import com.projectflow.dto.ProjectUnderstandingDtos.ProjectStructureIndexResponse;

/**
 * Stable boundary for repository structure providers. V3.5 ships a deterministic
 * filesystem/manifest provider; SCIP or Tree-sitter sidecars can be added here
 * later without changing the understanding service.
 */
public interface ProjectStructureIndexer {
    ProjectStructureIndexResponse build(RepositoryIntakeService.ScanResult scan);
}
