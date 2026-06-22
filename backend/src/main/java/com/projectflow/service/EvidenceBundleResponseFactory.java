package com.projectflow.service;

import java.util.List;
import java.util.UUID;

import com.projectflow.dto.V2ProjectDtos.EvidenceBundleResponse;
import com.projectflow.dto.V2ProjectDtos.EvidenceSourceResponse;
import com.projectflow.entity.EvidenceBundle;

final class EvidenceBundleResponseFactory {
    private EvidenceBundleResponseFactory() {
    }

    static EvidenceBundleResponse toResponse(EvidenceBundle bundle, String status, String nextAction, UUID changeId) {
        return new EvidenceBundleResponse(
            bundle.getId(),
            bundle.getProjectId(),
            bundle.getWorkSessionId(),
            bundle.getAgentType(),
            bundle.getTaskIntent(),
            bundle.getBranchName(),
            bundle.getAttributionConfidence(),
            bundle.getChangedFiles(),
            bundle.getAddedLines(),
            bundle.getDeletedLines(),
            bundle.getFiles(),
            bundle.getObjectiveEvidence(),
            bundle.getAgentClaims(),
            sourceResponses(bundle.getSourceLines()),
            status,
            nextAction,
            changeId,
            bundle.getCreatedAt(),
            bundle.getUpdatedAt()
        );
    }

    private static List<EvidenceSourceResponse> sourceResponses(List<String> sourceLines) {
        return sourceLines.stream()
            .map(line -> line.split("\\t", 3))
            .map(parts -> new EvidenceSourceResponse(
                parts.length > 0 ? parts[0] : "UNKNOWN",
                parts.length > 1 ? parts[1] : "",
                parts.length > 2 ? parts[2] : ""
            ))
            .toList();
    }
}
