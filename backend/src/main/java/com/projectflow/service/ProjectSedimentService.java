package com.projectflow.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projectflow.dto.V33WorkflowDtos.DevelopmentSegmentResponse;
import com.projectflow.dto.V33WorkflowDtos.ProjectSedimentPatchRequest;
import com.projectflow.dto.V33WorkflowDtos.ProjectSedimentResponse;
import com.projectflow.dto.V33WorkflowDtos.SedimentConfirmationResponse;
import com.projectflow.dto.V33WorkflowDtos.SedimentImpactPreviewResponse;
import com.projectflow.entity.ChangeBatch;
import com.projectflow.entity.DevelopmentSegment;
import com.projectflow.entity.DevelopmentSegmentStatus;
import com.projectflow.entity.ProjectChange;
import com.projectflow.entity.ProjectChangeImpactLevel;
import com.projectflow.entity.ProjectChangeKind;
import com.projectflow.entity.ProjectChangeSourceType;
import com.projectflow.entity.ProjectChangeStatus;
import com.projectflow.entity.ProjectReviewCursor;
import com.projectflow.entity.ProjectSediment;
import com.projectflow.entity.ProjectSpace;
import com.projectflow.entity.SedimentAction;
import com.projectflow.repository.ChangeBatchRepository;
import com.projectflow.repository.DevelopmentSegmentRepository;
import com.projectflow.repository.ProjectChangeRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.repository.ProjectReviewCursorRepository;
import com.projectflow.repository.ProjectSedimentRepository;
import com.projectflow.support.AppException;

@Service
public class ProjectSedimentService {
    private final ProjectRepository projectRepository;
    private final ProjectChangeRepository changeRepository;
    private final DevelopmentSegmentRepository segmentRepository;
    private final ChangeBatchRepository batchRepository;
    private final ProjectSedimentRepository sedimentRepository;
    private final ProjectReviewCursorRepository cursorRepository;
    private final ProjectChangeSchemaRepairService schemaRepairService;
    private final SedimentSuggestionPolicy suggestionPolicy;

    public ProjectSedimentService(
        ProjectRepository projectRepository,
        ProjectChangeRepository changeRepository,
        DevelopmentSegmentRepository segmentRepository,
        ChangeBatchRepository batchRepository,
        ProjectSedimentRepository sedimentRepository,
        ProjectReviewCursorRepository cursorRepository,
        ProjectChangeSchemaRepairService schemaRepairService,
        SedimentSuggestionPolicy suggestionPolicy
    ) {
        this.projectRepository = projectRepository;
        this.changeRepository = changeRepository;
        this.segmentRepository = segmentRepository;
        this.batchRepository = batchRepository;
        this.sedimentRepository = sedimentRepository;
        this.cursorRepository = cursorRepository;
        this.schemaRepairService = schemaRepairService;
        this.suggestionPolicy = suggestionPolicy;
    }

    @Transactional
    public void createSuggestions(UUID projectId, List<DevelopmentSegmentResponse> segments) {
        schemaRepairService.ensureEvidenceBundleSourceTypeAllowed();
        List<SedimentSuggestionPolicy.ExistingSediment> existing = sedimentRepository.findByProjectIdOrderByUpdatedAtDesc(projectId).stream()
            .map(item -> new SedimentSuggestionPolicy.ExistingSediment(item.getId(), item.getTitle(), item.getProblemSolved()))
            .toList();
        for (DevelopmentSegmentResponse segment : segments) {
            if (changeRepository.findByDevelopmentSegmentId(segment.id()).isPresent()) {
                continue;
            }
            ProjectChange change = new ProjectChange(projectId, null);
            List<String> evidenceRefs = new ArrayList<>(segment.evidenceRefs());
            segment.commitUrls().forEach(url -> evidenceRefs.add("url:" + url));
            change.update(
                ProjectChangeSourceType.DEVELOPMENT_SEGMENT,
                segment.id().toString(),
                null,
                inferKind(segment.affectedFiles()),
                segment.affectedFiles().size() >= 10 ? ProjectChangeImpactLevel.MAJOR : ProjectChangeImpactLevel.MINOR,
                trim(segment.title(), 180),
                segment.plainSummary(),
                String.join("\n", segment.mainChanges()),
                String.join("\n", segment.affectedFiles()),
                "",
                evidenceOfType(evidenceRefs, "test"),
                evidenceOfType(evidenceRefs, "build"),
                "",
                "",
                "",
                segment.userVisibleValue()
            );
            var suggestion = suggestionPolicy.suggest(segment.title(), segment.userVisibleValue(), segment.affectedFiles(), evidenceRefs, existing);
            change.updateSedimentSuggestion(
                segment.id(), suggestion.action(), suggestion.targetSedimentId(), segment.userVisibleValue(), suggestion.reason(),
                evidenceRefs, com.projectflow.entity.EvidenceConfidence.valueOf(segment.confidence()), true
            );
            changeRepository.save(change);
        }
    }

    @Transactional
    public SedimentConfirmationResponse confirm(UUID userId, UUID changeId, SedimentAction action, UUID targetSedimentId) {
        ProjectChange change = ownedChange(userId, changeId);
        if (change.getDevelopmentSegmentId() == null) {
            throw new AppException("V33_CHANGE_REQUIRED", "This change does not belong to a development segment", HttpStatus.BAD_REQUEST);
        }
        if (change.getStatus() != ProjectChangeStatus.PENDING && change.getStatus() != ProjectChangeStatus.EDITED) {
            throw new AppException("CHANGE_ALREADY_REVIEWED", "Project change was already reviewed", HttpStatus.CONFLICT);
        }
        DevelopmentSegment segment = segmentRepository.findById(change.getDevelopmentSegmentId())
            .filter(value -> value.getProjectId().equals(change.getProjectId()))
            .orElseThrow(() -> new AppException("DEVELOPMENT_SEGMENT_NOT_FOUND", "Development segment was not found", HttpStatus.NOT_FOUND));
        validateEvidence(change, segment);

        ProjectSediment existingTarget = action == SedimentAction.MERGE_EXISTING || action == SedimentAction.EVIDENCE_ONLY
            ? requireTarget(change.getProjectId(), targetSedimentId) : null;
        int previousEvidenceCount = existingTarget == null ? 0 : existingTarget.getEvidenceRefs().size();
        boolean summaryUpdated = action == SedimentAction.NEW_SEDIMENT
            || (action == SedimentAction.MERGE_EXISTING && !existingTarget.getSummary().contains(change.getSummary().trim()));

        ProjectSediment sediment = switch (action) {
            case NEW_SEDIMENT -> createSediment(change, segment);
            case MERGE_EXISTING -> mergeSediment(change, segment, existingTarget, false);
            case EVIDENCE_ONLY -> mergeSediment(change, segment, existingTarget, true);
            case IGNORE -> null;
        };
        if (action == SedimentAction.IGNORE) {
            change.markIgnored();
            segment.markIgnored();
        } else if (action == SedimentAction.MERGE_EXISTING) {
            change.markMerged();
            segment.markConfirmed();
        } else {
            change.markAccepted();
            segment.markConfirmed();
        }
        change.updateSedimentSuggestion(
            segment.getId(), action, sediment == null ? null : sediment.getId(), change.getProblemSolved(), change.getSuggestionReason(),
            change.getEvidenceRefs(), change.getEvidenceConfidence(), false
        );
        String batchStatus = updateBatchAndCursor(segment.getBatchId());
        int evidenceAdded = sediment == null ? 0 : Math.max(0, sediment.getEvidenceRefs().size() - previousEvidenceCount);
        int filesAdded = action == SedimentAction.IGNORE ? 0 : affectedFiles(change).size();
        String actionLabel = actionLabel(action);
        String resultMessage = action == SedimentAction.IGNORE
            ? "已暂不沉淀，原始证据仍保留。"
            : completedActionLabel(action) + "《" + sediment.getTitle() + "》，新增 " + evidenceAdded + " 条证据、" + filesAdded + " 个涉及文件。";
        return new SedimentConfirmationResponse(
            change.getId(), change.getStatus().name(), sediment == null ? null : response(sediment), batchStatus,
            actionLabel, resultMessage, evidenceAdded, filesAdded, summaryUpdated, false, action != SedimentAction.IGNORE,
            sediment == null ? "" : "/project-sediments/" + sediment.getId()
        );
    }

    @Transactional(readOnly = true)
    public SedimentImpactPreviewResponse preview(
        UUID userId,
        UUID changeId,
        SedimentAction action,
        UUID targetSedimentId
    ) {
        ProjectChange change = ownedChange(userId, changeId);
        ProjectSediment target = action == SedimentAction.MERGE_EXISTING || action == SedimentAction.EVIDENCE_ONLY
            ? requireTarget(change.getProjectId(), targetSedimentId) : null;
        int evidenceToAdd = target == null
            ? new HashSet<>(change.getEvidenceRefs()).size()
            : (int) change.getEvidenceRefs().stream().filter(ref -> !target.getEvidenceRefs().contains(ref)).distinct().count();
        boolean summaryWillUpdate = action == SedimentAction.NEW_SEDIMENT
            || (action == SedimentAction.MERGE_EXISTING && target != null && !target.getSummary().contains(change.getSummary().trim()));
        List<String> updatedFields = switch (action) {
            case NEW_SEDIMENT -> List.of("标题", "摘要", "解决的问题", "证据引用");
            case MERGE_EXISTING -> List.of("摘要", "证据引用", "来源开发推进段");
            case EVIDENCE_ONLY -> List.of("证据引用", "来源开发推进段");
            case IGNORE -> List.of();
        };
        String targetTitle = target == null ? change.getTitle() : target.getTitle();
        String consequence = action == SedimentAction.IGNORE
            ? "不会新建或修改项目沉淀，原始证据仍保留。"
            : actionLabel(action) + "《" + targetTitle + "》；下次能力分析会读取这次更新，已确认能力不会被直接改写。";
        return new SedimentImpactPreviewResponse(
            change.getId(), action, actionLabel(action), change.getSuggestionReason(),
            target == null ? null : target.getId(), targetTitle, target == null ? change.getSummary() : target.getSummary(),
            target == null ? null : target.getUpdatedAt(), evidenceToAdd, action == SedimentAction.IGNORE ? 0 : affectedFiles(change).size(),
            summaryWillUpdate, false, action != SedimentAction.IGNORE, updatedFields, consequence
        );
    }

    @Transactional(readOnly = true)
    public boolean isV33Change(UUID userId, UUID changeId) {
        return ownedChange(userId, changeId).getDevelopmentSegmentId() != null;
    }

    @Transactional(readOnly = true)
    public List<ProjectSedimentResponse> list(UUID userId, UUID projectId) {
        ownedProject(userId, projectId);
        return sedimentRepository.findByProjectIdOrderByUpdatedAtDesc(projectId).stream().map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public ProjectSedimentResponse get(UUID userId, UUID sedimentId) {
        ProjectSediment sediment = sedimentRepository.findById(sedimentId)
            .orElseThrow(() -> new AppException("PROJECT_SEDIMENT_NOT_FOUND", "Project sediment was not found", HttpStatus.NOT_FOUND));
        ownedProject(userId, sediment.getProjectId());
        return response(sediment);
    }

    @Transactional
    public ProjectSedimentResponse patch(UUID userId, UUID sedimentId, ProjectSedimentPatchRequest request) {
        ProjectSediment sediment = sedimentRepository.findById(sedimentId)
            .orElseThrow(() -> new AppException("PROJECT_SEDIMENT_NOT_FOUND", "Project sediment was not found", HttpStatus.NOT_FOUND));
        ownedProject(userId, sediment.getProjectId());
        sediment.updateDeveloperNotes(request.developerNotes());
        return response(sediment);
    }

    private ProjectSediment createSediment(ProjectChange change, DevelopmentSegment segment) {
        ProjectSediment sediment = new ProjectSediment(change.getProjectId());
        sediment.updateCore(change.getTitle(), change.getSummary(), change.getProblemSolved(), change.getChangeKind().name(),
            List.of(segment.getId().toString()), change.getEvidenceRefs());
        return sedimentRepository.save(sediment);
    }

    private ProjectSediment mergeSediment(ProjectChange change, DevelopmentSegment segment, ProjectSediment target, boolean evidenceOnly) {
        if (evidenceOnly) {
            target.addEvidence(segment.getId().toString(), change.getEvidenceRefs());
        } else {
            target.merge(change.getSummary(), change.getProblemSolved(), segment.getId().toString(), change.getEvidenceRefs());
        }
        return sedimentRepository.save(target);
    }

    private ProjectSediment requireTarget(UUID projectId, UUID targetId) {
        if (targetId == null) {
            throw new AppException("TARGET_SEDIMENT_REQUIRED", "Target sediment is required", HttpStatus.BAD_REQUEST);
        }
        return sedimentRepository.findByIdAndProjectId(targetId, projectId)
            .orElseThrow(() -> new AppException("PROJECT_SEDIMENT_NOT_FOUND", "Project sediment was not found", HttpStatus.NOT_FOUND));
    }

    private String updateBatchAndCursor(UUID batchId) {
        ChangeBatch batch = batchRepository.findById(batchId)
            .orElseThrow(() -> new AppException("CHANGE_BATCH_NOT_FOUND", "Change batch was not found", HttpStatus.NOT_FOUND));
        List<DevelopmentSegment> segments = segmentRepository.findByBatchIdOrderByCreatedAtAsc(batchId);
        boolean allResolved = !segments.isEmpty() && segments.stream().allMatch(segment ->
            segment.getStatus() == DevelopmentSegmentStatus.CONFIRMED || segment.getStatus() == DevelopmentSegmentStatus.IGNORED
        );
        if (allResolved) {
            batch.markReviewed();
            ProjectReviewCursor cursor = cursorRepository.findByProjectId(batch.getProjectId())
                .orElseGet(() -> new ProjectReviewCursor(batch.getProjectId()));
            cursor.advance(batch.getHeadCommitSha(), Instant.now(), batch.getBranchName(), "", null);
            cursorRepository.save(cursor);
        } else {
            batch.markPartial();
        }
        return batch.getStatus().name();
    }

    private void validateEvidence(ProjectChange change, DevelopmentSegment segment) {
        Set<String> allowed = new HashSet<>(segment.getEvidenceRefs());
        List<String> valid = change.getEvidenceRefs().stream().filter(allowed::contains).toList();
        if (valid.isEmpty()) {
            throw new AppException("CHANGE_EVIDENCE_REQUIRED", "Suggestion has no valid evidence reference", HttpStatus.BAD_REQUEST);
        }
    }

    private ProjectChange ownedChange(UUID userId, UUID changeId) {
        ProjectChange change = changeRepository.findById(changeId)
            .orElseThrow(() -> new AppException("PROJECT_CHANGE_NOT_FOUND", "Project change was not found", HttpStatus.NOT_FOUND));
        ownedProject(userId, change.getProjectId());
        return change;
    }

    private ProjectSpace ownedProject(UUID userId, UUID projectId) {
        return projectRepository.findByIdAndUserId(projectId, userId)
            .orElseThrow(() -> new AppException("PROJECT_NOT_FOUND", "Project was not found", HttpStatus.NOT_FOUND));
    }

    private ProjectSedimentResponse response(ProjectSediment sediment) {
        return new ProjectSedimentResponse(
            sediment.getId(), sediment.getProjectId(), sediment.getTitle(), sediment.getSummary(), sediment.getProblemSolved(),
            sediment.getSedimentType(), sediment.getStatus(), sediment.getSourceSegmentIds(), sediment.getEvidenceRefs(),
            sediment.getDeveloperNotes(),
            DisplayContentSanitizer.isLikelyLegacyTruncated(sediment.getTitle())
                || DisplayContentSanitizer.isLikelyLegacyTruncated(sediment.getSummary())
                || DisplayContentSanitizer.isLikelyLegacyTruncated(sediment.getProblemSolved()),
            sediment.getCreatedAt(), sediment.getUpdatedAt()
        );
    }

    private String actionLabel(SedimentAction action) {
        return switch (action) {
            case NEW_SEDIMENT -> "新建并确认";
            case MERGE_EXISTING -> "合并并确认";
            case EVIDENCE_ONLY -> "补充证据并确认";
            case IGNORE -> "暂不沉淀";
        };
    }

    private String completedActionLabel(SedimentAction action) {
        return switch (action) {
            case NEW_SEDIMENT -> "已新建项目沉淀";
            case MERGE_EXISTING -> "已合并到项目沉淀";
            case EVIDENCE_ONLY -> "已补充项目沉淀证据";
            case IGNORE -> "已暂不沉淀";
        };
    }

    private Set<String> affectedFiles(ProjectChange change) {
        Set<String> files = new HashSet<>();
        if (change.getAffectedFiles() != null) {
            change.getAffectedFiles().lines().map(String::trim).filter(value -> !value.isBlank()).forEach(files::add);
        }
        change.getEvidenceRefs().stream().filter(ref -> ref.startsWith("file:")).map(ref -> ref.substring(5)).forEach(files::add);
        return files;
    }

    private ProjectChangeKind inferKind(List<String> files) {
        if (!files.isEmpty() && files.stream().allMatch(file -> file.toLowerCase().endsWith(".md"))) return ProjectChangeKind.DOCS;
        if (!files.isEmpty() && files.stream().allMatch(file -> file.toLowerCase().contains("test"))) return ProjectChangeKind.TEST;
        return ProjectChangeKind.CAPABILITY;
    }

    private String evidenceOfType(List<String> refs, String type) {
        return refs.stream().filter(ref -> ref.toLowerCase().contains(type)).findFirst().orElse("");
    }

    private String trim(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }
}
