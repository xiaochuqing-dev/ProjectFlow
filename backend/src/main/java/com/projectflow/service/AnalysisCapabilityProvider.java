package com.projectflow.service;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import com.projectflow.dto.ProjectUnderstandingDtos.AdaptiveAnalysisPlanResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.AnalysisToolEvidenceResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.EvidenceSourceMapResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.ProjectStructureIndexResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.RepositoryIntakeResponse;
import com.projectflow.service.ProjectEvidenceDiscoveryService.PromptEvidence;

public interface AnalysisCapabilityProvider {
    boolean supports(String capability);

    CapabilityResult execute(CapabilityRequest request);

    record CapabilityRequest(
        String capability,
        Path projectRoot,
        RepositoryIntakeResponse intake,
        ProjectStructureIndexResponse structureIndex,
        EvidenceSourceMapResponse sourceMap,
        AdaptiveAnalysisPlanResponse plan,
        Set<String> allowedEvidenceIds,
        ExecutionBudget budget
    ) {
    }

    record ExecutionBudget(
        int maxItems,
        int maxCharsPerItem,
        int maxTotalChars,
        long timeoutMs
    ) {
    }

    record CapabilityResult(
        String status,
        List<AnalysisToolEvidenceResponse> evidence,
        List<PromptEvidence> promptEvidence,
        int selectedItemCount,
        int consumedChars,
        String message
    ) {
    }
}
