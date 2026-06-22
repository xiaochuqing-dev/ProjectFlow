package com.projectflow.service;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projectflow.dto.V2ProjectDtos.EvidenceBundleResponse;
import com.projectflow.dto.V2ProjectDtos.ProjectChangeResponse;
import com.projectflow.entity.EvidenceBundle;
import com.projectflow.entity.ProjectChange;
import com.projectflow.entity.ProjectChangeImpactLevel;
import com.projectflow.entity.ProjectChangeKind;
import com.projectflow.entity.ProjectChangeSourceType;
import com.projectflow.entity.ProjectChangeStatus;
import com.projectflow.repository.EvidenceBundleRepository;
import com.projectflow.repository.ProjectChangeRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.support.AppException;

@Service
public class EvidenceDraftChangeService {
    private static final int PROJECT_CHANGE_TITLE_MAX_LENGTH = 180;

    private final ProjectRepository projectRepository;
    private final EvidenceBundleRepository evidenceBundleRepository;
    private final ProjectChangeRepository projectChangeRepository;
    private final ProjectChangeSchemaRepairService projectChangeSchemaRepairService;

    public EvidenceDraftChangeService(
        ProjectRepository projectRepository,
        EvidenceBundleRepository evidenceBundleRepository,
        ProjectChangeRepository projectChangeRepository,
        ProjectChangeSchemaRepairService projectChangeSchemaRepairService
    ) {
        this.projectRepository = projectRepository;
        this.evidenceBundleRepository = evidenceBundleRepository;
        this.projectChangeRepository = projectChangeRepository;
        this.projectChangeSchemaRepairService = projectChangeSchemaRepairService;
    }

    @Transactional
    public ProjectChangeResponse draftChange(UUID userId, UUID evidenceBundleId) {
        EvidenceBundle bundle = evidenceBundleRepository.findById(evidenceBundleId)
            .orElseThrow(() -> new AppException("EVIDENCE_BUNDLE_NOT_FOUND", "Evidence bundle was not found", HttpStatus.NOT_FOUND));
        projectRepository.findByIdAndUserId(bundle.getProjectId(), userId)
            .orElseThrow(() -> new AppException("PROJECT_NOT_FOUND", "Project was not found", HttpStatus.NOT_FOUND));
        EvidenceBundleResponse evidence = bundle.toResponse();
        projectChangeSchemaRepairService.ensureEvidenceBundleSourceTypeAllowed();
        ProjectChange change = projectChangeRepository.findBySourceTypeAndSourceRef(ProjectChangeSourceType.EVIDENCE_BUNDLE, evidenceBundleId.toString())
            .orElseGet(() -> new ProjectChange(bundle.getProjectId(), null));
        if (change.getStatus() == ProjectChangeStatus.ACCEPTED
            || change.getStatus() == ProjectChangeStatus.IGNORED
            || change.getStatus() == ProjectChangeStatus.MERGED) {
            return toResponse(change);
        }
        change.update(
            ProjectChangeSourceType.EVIDENCE_BUNDLE,
            evidenceBundleId.toString(),
            null,
            inferKind(evidence.files()),
            evidence.changedFiles() >= 3 ? ProjectChangeImpactLevel.MAJOR : ProjectChangeImpactLevel.MINOR,
            title(evidence),
            summary(evidence),
            details(evidence),
            bulletLines(evidence.files()),
            evidence.taskIntent(),
            "未采集测试证据。",
            "未采集构建证据。",
            evidence.agentClaims().isEmpty() ? "没有 Agent Claim，仅基于客观证据生成保守候选。" : "",
            "",
            "",
            ""
        );
        return toResponse(projectChangeRepository.save(change));
    }

    private ProjectChangeKind inferKind(List<String> files) {
        boolean onlyDocs = !files.isEmpty() && files.stream().allMatch(file -> file.endsWith(".md") || file.startsWith("docs/"));
        if (onlyDocs) {
            return ProjectChangeKind.DOCS;
        }
        boolean hasTest = files.stream().anyMatch(file -> file.contains("test") || file.contains("spec"));
        if (hasTest) {
            return ProjectChangeKind.TEST;
        }
        return ProjectChangeKind.CAPABILITY;
    }

    private String title(EvidenceBundleResponse evidence) {
        String base = evidence.taskIntent().isBlank() ? evidence.branchName() : evidence.taskIntent();
        return trimToMax("Evidence Bundle 候选变更：" + base, PROJECT_CHANGE_TITLE_MAX_LENGTH);
    }

    private String summary(EvidenceBundleResponse evidence) {
        return "基于 Evidence Bundle 生成的保守候选：%d 个文件，+%d/-%d 行，归因 %s，置信度 %s。".formatted(
            evidence.changedFiles(),
            evidence.addedLines(),
            evidence.deletedLines(),
            evidence.agentType(),
            evidence.attributionConfidence()
        );
    }

    private String details(EvidenceBundleResponse evidence) {
        return String.join("\n", evidence.objectiveEvidence());
    }

    private String bulletLines(List<String> values) {
        if (values.isEmpty()) {
            return "- 未记录";
        }
        return String.join("\n", values.stream().map(value -> "- " + value).toList());
    }

    private String trimToMax(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 3).trim() + "...";
    }

    private ProjectChangeResponse toResponse(ProjectChange change) {
        return new ProjectChangeResponse(
            change.getId(),
            change.getProjectId(),
            change.getMaterialId(),
            change.getLinkedSuggestionId(),
            change.getSourceType(),
            change.getSourceRef(),
            change.getChangeKind(),
            change.getImpactLevel(),
            change.getStatus(),
            change.getTitle(),
            change.getSummary(),
            change.getDetails(),
            change.getAffectedFiles(),
            change.getRelatedTasks(),
            change.getTestEvidence(),
            change.getBuildEvidence(),
            change.getRiskNotes(),
            change.getDecisionNotes(),
            change.getLearningNotes(),
            change.getAssetCandidates(),
            change.getCreatedAt(),
            change.getUpdatedAt(),
            change.getReviewedAt()
        );
    }
}
