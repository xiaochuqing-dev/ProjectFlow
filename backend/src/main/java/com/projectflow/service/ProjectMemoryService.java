package com.projectflow.service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projectflow.dto.V2ProjectDtos.ProjectFactSourceResponse;
import com.projectflow.dto.V2ProjectDtos.ProjectLocalPathRequest;
import com.projectflow.dto.V2ProjectDtos.ProjectMemoryResponse;
import com.projectflow.dto.V2ProjectDtos.ProjectMemoryUpdateRequest;
import com.projectflow.entity.ProjectChange;
import com.projectflow.entity.ProjectFactSource;
import com.projectflow.entity.ProjectFactSourceType;
import com.projectflow.entity.ProjectMemory;
import com.projectflow.entity.ProjectSpace;
import com.projectflow.repository.ProjectFactSourceRepository;
import com.projectflow.repository.ProjectMemoryRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.support.AppException;

@Service
public class ProjectMemoryService {
    private final ProjectRepository projectRepository;
    private final ProjectMemoryRepository memoryRepository;
    private final ProjectFactSourceRepository factSourceRepository;
    private final LocalProjectPathGuard localProjectPathGuard;

    public ProjectMemoryService(
        ProjectRepository projectRepository,
        ProjectMemoryRepository memoryRepository,
        ProjectFactSourceRepository factSourceRepository,
        LocalProjectPathGuard localProjectPathGuard
    ) {
        this.projectRepository = projectRepository;
        this.memoryRepository = memoryRepository;
        this.factSourceRepository = factSourceRepository;
        this.localProjectPathGuard = localProjectPathGuard;
    }

    @Transactional(readOnly = true)
    public ProjectMemoryResponse getMemory(UUID userId, UUID projectId) {
        ProjectSpace project = findOwnedProject(userId, projectId);
        ProjectMemory memory = memoryRepository.findByProjectId(project.getId()).orElseGet(() -> initialMemory(project));
        return toMemoryResponse(memory);
    }

    @Transactional(readOnly = true)
    public List<ProjectFactSourceResponse> listFactSources(UUID userId, UUID projectId) {
        ProjectSpace project = findOwnedProject(userId, projectId);
        return factSourceRepository.findByProjectIdOrderByUpdatedAtDesc(project.getId())
            .stream()
            .map(this::toFactSourceResponse)
            .toList();
    }

    @Transactional
    public ProjectMemoryResponse updateMemory(UUID userId, UUID projectId, ProjectMemoryUpdateRequest request) {
        ProjectSpace project = findOwnedProject(userId, projectId);
        ProjectMemory memory = memoryRepository.findByProjectId(project.getId()).orElseGet(() -> initialMemory(project));
        memory.update(
            cleanMemoryText(request.positioning(), "暂无项目定位。"),
            cleanMemoryText(request.currentStage(), project.getStatus().name()),
            cleanMemoryText(request.completedCapabilities(), "暂无已确认能力。"),
            cleanMemoryText(request.inProgressCapabilities(), "暂无进行中能力。"),
            cleanMemoryText(request.currentRisks(), "暂无已确认风险。"),
            cleanMemoryText(request.technicalDecisions(), "暂无技术决策记录。"),
            cleanMemoryText(request.developerLearnings(), "暂无开发者收获。"),
            cleanMemoryText(request.showcaseAssets(), "暂无可展示成果。"),
            cleanMemoryText(request.nextStepSuggestions(), "暂无下一步建议。")
        );
        ProjectMemory saved = memoryRepository.save(memory);
        recordFactSources(project.getId(), ProjectFactSourceType.USER_MANUAL, null, true, Map.of(
            "positioning", saved.getPositioning(),
            "currentStage", saved.getCurrentStage(),
            "completedCapabilities", saved.getCompletedCapabilities(),
            "inProgressCapabilities", saved.getInProgressCapabilities(),
            "currentRisks", saved.getCurrentRisks(),
            "technicalDecisions", saved.getTechnicalDecisions(),
            "developerLearnings", saved.getDeveloperLearnings(),
            "showcaseAssets", saved.getShowcaseAssets(),
            "nextStepSuggestions", saved.getNextStepSuggestions()
        ));
        return toMemoryResponse(saved);
    }

    @Transactional
    public ProjectMemoryResponse updateLocalProjectPath(UUID userId, UUID projectId, ProjectLocalPathRequest request) {
        ProjectSpace project = findOwnedProject(userId, projectId);
        Path projectRoot = localProjectPathGuard.requireProjectDirectory(request.localProjectPath()).path();
        ProjectMemory memory = memoryRepository.findByProjectId(project.getId()).orElseGet(() -> initialMemory(project));
        memory.rememberLocalProjectPath(projectRoot.toString());
        return toMemoryResponse(memoryRepository.save(memory));
    }

    ProjectMemory appendFromSuggestionApplication(
        ProjectSpace project,
        String positioning,
        String stage,
        List<String> newTasks,
        List<String> newLogs,
        List<String> risks,
        List<String> decisions,
        List<String> learnings,
        List<String> assets,
        List<String> nextSteps
    ) {
        ProjectMemory memory = memoryRepository.findByProjectId(project.getId()).orElseGet(() -> initialMemory(project));
        memory.update(
            defaultText(positioning, memory.getPositioning()),
            defaultText(stage, memory.getCurrentStage()),
            appendLines(memory.getCompletedCapabilities(), newLogs),
            appendLines(memory.getInProgressCapabilities(), newTasks),
            appendLines(memory.getCurrentRisks(), risks),
            appendLines(memory.getTechnicalDecisions(), decisions),
            appendLines(memory.getDeveloperLearnings(), learnings),
            appendLines(memory.getShowcaseAssets(), assets),
            appendLines(memory.getNextStepSuggestions(), nextSteps)
        );
        return memoryRepository.save(memory);
    }

    ProjectMemory applyAcceptedChange(ProjectSpace project, ProjectChange change) {
        List<String> completed = new ArrayList<>();
        List<String> risks = new ArrayList<>();
        List<String> decisions = new ArrayList<>();
        List<String> learnings = new ArrayList<>();
        List<String> assets = new ArrayList<>();
        String summaryLine = defaultText(change.getSummary(), change.getDetails());

        switch (change.getChangeKind()) {
            case RISK -> risks.add(defaultText(change.getRiskNotes(), summaryLine));
            case DECISION -> decisions.add(defaultText(change.getDecisionNotes(), summaryLine));
            case LEARNING -> learnings.add(defaultText(change.getLearningNotes(), summaryLine));
            case ASSET -> assets.add(defaultText(change.getAssetCandidates(), summaryLine));
            default -> completed.add(summaryLine);
        }
        if (change.getRiskNotes() != null && !change.getRiskNotes().isBlank()) {
            risks.add(change.getRiskNotes());
        }
        if (change.getDecisionNotes() != null && !change.getDecisionNotes().isBlank()) {
            decisions.add(change.getDecisionNotes());
        }
        if (change.getLearningNotes() != null && !change.getLearningNotes().isBlank()) {
            learnings.add(change.getLearningNotes());
        }
        if (change.getAssetCandidates() != null && !change.getAssetCandidates().isBlank()) {
            assets.add(change.getAssetCandidates());
        }

        ProjectMemory memory = appendFromSuggestionApplication(project, null, null, List.of(), completed, risks, decisions, learnings, assets, List.of());
        UUID sourceId = change.getId();
        if (!completed.isEmpty()) {
            recordFactSource(project.getId(), "completedCapabilities", summaryLine, ProjectFactSourceType.ACCEPTED_CHANGE, sourceId, true);
        }
        if (!risks.isEmpty()) {
            recordFactSource(project.getId(), "currentRisks", String.join("\n", risks), ProjectFactSourceType.ACCEPTED_CHANGE, sourceId, true);
        }
        if (!decisions.isEmpty()) {
            recordFactSource(project.getId(), "technicalDecisions", String.join("\n", decisions), ProjectFactSourceType.ACCEPTED_CHANGE, sourceId, true);
        }
        if (!learnings.isEmpty()) {
            recordFactSource(project.getId(), "developerLearnings", String.join("\n", learnings), ProjectFactSourceType.ACCEPTED_CHANGE, sourceId, true);
        }
        if (!assets.isEmpty()) {
            recordFactSource(project.getId(), "showcaseAssets", String.join("\n", assets), ProjectFactSourceType.ACCEPTED_CHANGE, sourceId, true);
        }
        return memory;
    }

    void recordFactSources(
        UUID projectId,
        ProjectFactSourceType sourceType,
        UUID sourceId,
        boolean confirmedByUser,
        Map<String, String> values
    ) {
        values.forEach((fieldKey, value) -> recordFactSource(projectId, fieldKey, value, sourceType, sourceId, confirmedByUser));
    }

    void recordFactSource(
        UUID projectId,
        String fieldKey,
        String value,
        ProjectFactSourceType sourceType,
        UUID sourceId,
        boolean confirmedByUser
    ) {
        if (value == null || value.trim().isBlank()) {
            return;
        }
        ProjectFactSource source = factSourceRepository.findByProjectIdAndFieldKey(projectId, fieldKey)
            .orElseGet(() -> new ProjectFactSource(projectId, fieldKey));
        source.update(value.trim(), sourceType, sourceId, confirmedByUser ? "confirmed" : "inferred", confirmedByUser);
        factSourceRepository.save(source);
    }

    ProjectMemoryResponse toMemoryResponse(ProjectMemory memory) {
        return new ProjectMemoryResponse(
            memory.getId(),
            memory.getProjectId(),
            memory.getPositioning(),
            memory.getCurrentStage(),
            memory.getCompletedCapabilities(),
            memory.getInProgressCapabilities(),
            memory.getCurrentRisks(),
            memory.getTechnicalDecisions(),
            memory.getDeveloperLearnings(),
            memory.getShowcaseAssets(),
            memory.getNextStepSuggestions(),
            memory.getLocalProjectPath(),
            memory.getVersion(),
            memory.getCreatedAt(),
            memory.getUpdatedAt()
        );
    }

    private ProjectFactSourceResponse toFactSourceResponse(ProjectFactSource source) {
        return new ProjectFactSourceResponse(
            source.getId(),
            source.getProjectId(),
            source.getFieldKey(),
            source.getValue(),
            source.getSourceType(),
            source.getSourceId(),
            source.getConfidence(),
            source.isConfirmedByUser(),
            source.getCreatedAt(),
            source.getUpdatedAt()
        );
    }

    private ProjectMemory initialMemory(ProjectSpace project) {
        ProjectMemory memory = new ProjectMemory(project.getId());
        memory.update(
            defaultText(project.getDescription(), project.getName() + " 的长期项目档案"),
            project.getStatus().name(),
            "暂无已确认能力。",
            "暂无进行中能力。",
            "暂无已确认风险。",
            "暂无技术决策记录。",
            "暂无开发者收获。",
            "暂无可展示成果。",
            "先导入项目材料，生成并确认 AI 建议。"
        );
        return memory;
    }

    private ProjectSpace findOwnedProject(UUID userId, UUID projectId) {
        return projectRepository.findByIdAndUserId(projectId, userId)
            .orElseThrow(() -> new AppException("PROJECT_NOT_FOUND", "Project was not found", HttpStatus.NOT_FOUND));
    }

    private String cleanMemoryText(String value, String fallback) {
        if (value == null || value.trim().isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private String appendLines(String existing, List<String> additions) {
        if (additions.isEmpty()) {
            return existing;
        }
        LinkedHashMap<String, String> lines = new LinkedHashMap<>();
        if (existing != null && !existing.isBlank() && !existing.startsWith("暂无")) {
            existing.lines()
                .map(this::cleanMemoryLine)
                .filter(line -> !line.isBlank())
                .forEach(line -> lines.putIfAbsent(normalizeMemoryLine(line), line));
        }
        additions.stream()
            .flatMap(item -> item == null ? java.util.stream.Stream.<String>empty() : item.lines())
            .map(this::cleanMemoryLine)
            .filter(line -> !line.isBlank())
            .forEach(line -> lines.putIfAbsent(normalizeMemoryLine(line), line));
        return String.join("\n", lines.values().stream().map(item -> "- " + item).toList());
    }

    private String cleanMemoryLine(String value) {
        return value == null ? "" : value.trim().replaceFirst("^(?:[-*]\\s*)+", "").trim();
    }

    private String normalizeMemoryLine(String value) {
        return cleanMemoryLine(value)
            .replaceAll("\\s+", " ")
            .replace("：", ":")
            .toLowerCase();
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
