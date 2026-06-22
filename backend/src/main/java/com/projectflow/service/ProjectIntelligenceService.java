package com.projectflow.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.dto.V2ProjectDtos.AiSuggestionPatchRequest;
import com.projectflow.dto.V2ProjectDtos.AiSuggestionResponse;
import com.projectflow.dto.V2ProjectDtos.AnalyzeMaterialResponse;
import com.projectflow.dto.V2ProjectDtos.ApplySuggestionsResponse;
import com.projectflow.dto.V2ProjectDtos.ProjectImportAnalyzeResponse;
import com.projectflow.dto.V2ProjectDtos.ProjectEvolutionRecordResponse;
import com.projectflow.dto.V2ProjectDtos.ProjectProfileResponse;
import com.projectflow.dto.V2ProjectDtos.ProjectSnapshotResponse;
import com.projectflow.dto.ProjectDtos.ProjectResponse;
import com.projectflow.service.ProjectZipScanService.ZipProjectScan;
import com.projectflow.entity.AiSuggestion;
import com.projectflow.entity.AiSuggestionStatus;
import com.projectflow.entity.AiSuggestionType;
import com.projectflow.entity.DevLog;
import com.projectflow.entity.DevLogCategory;
import com.projectflow.entity.MaterialSourceType;
import com.projectflow.entity.ProjectEvolutionRecord;
import com.projectflow.entity.ProjectChange;
import com.projectflow.entity.ProjectFactSourceType;
import com.projectflow.entity.ProjectMaterial;
import com.projectflow.entity.ProjectMemory;
import com.projectflow.entity.ProjectSnapshot;
import com.projectflow.entity.ProjectSpace;
import com.projectflow.entity.ProjectStatus;
import com.projectflow.entity.TaskItem;
import com.projectflow.entity.TaskPriority;
import com.projectflow.entity.TaskStatus;
import com.projectflow.repository.AiSuggestionRepository;
import com.projectflow.repository.DevLogRepository;
import com.projectflow.repository.ProjectEvolutionRecordRepository;
import com.projectflow.repository.ProjectChangeRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.repository.ProjectSnapshotRepository;
import com.projectflow.repository.TaskRepository;
import com.projectflow.support.AppException;

@Service
public class ProjectIntelligenceService {
    private final ProjectRepository projectRepository;
    private final AiSuggestionRepository suggestionRepository;
    private final ProjectSnapshotRepository snapshotRepository;
    private final ProjectEvolutionRecordRepository evolutionRepository;
    private final ProjectChangeRepository changeRepository;
    private final TaskRepository taskRepository;
    private final DevLogRepository devLogRepository;
    private final ObjectMapper objectMapper;
    private final ProjectMemoryService projectMemoryService;
    private final ProjectMaterialService projectMaterialService;
    private final ProjectZipScanService projectZipScanService;

    public ProjectIntelligenceService(
        ProjectRepository projectRepository,
        AiSuggestionRepository suggestionRepository,
        ProjectSnapshotRepository snapshotRepository,
        ProjectEvolutionRecordRepository evolutionRepository,
        ProjectChangeRepository changeRepository,
        TaskRepository taskRepository,
        DevLogRepository devLogRepository,
        ObjectMapper objectMapper,
        ProjectMemoryService projectMemoryService,
        ProjectMaterialService projectMaterialService,
        ProjectZipScanService projectZipScanService
    ) {
        this.projectRepository = projectRepository;
        this.suggestionRepository = suggestionRepository;
        this.snapshotRepository = snapshotRepository;
        this.evolutionRepository = evolutionRepository;
        this.changeRepository = changeRepository;
        this.taskRepository = taskRepository;
        this.devLogRepository = devLogRepository;
        this.objectMapper = objectMapper;
        this.projectMemoryService = projectMemoryService;
        this.projectMaterialService = projectMaterialService;
        this.projectZipScanService = projectZipScanService;
    }

    @Transactional
    public ProjectImportAnalyzeResponse importProjectZip(UUID userId, UUID projectId, MultipartFile file) {
        ZipProjectScan scan = projectZipScanService.scan(file);
        ProjectSpace project = projectId == null
            ? findReusableImportedProject(userId, scan.profile()).orElseGet(() -> createImportedProject(userId, scan.profile()))
            : findOwnedProject(userId, projectId);
        ProjectMaterial material = projectMaterialService.saveMaterial(project.getId(), MaterialSourceType.PROJECT_ZIP, file.getOriginalFilename(), scan.content());
        List<AiSuggestion> suggestions = generateProjectProfileSuggestions(project, material, scan.profile());

        return new ProjectImportAnalyzeResponse(
            toProjectResponse(project),
            projectMaterialService.toMaterialResponse(material),
            scan.profile(),
            suggestions.stream().map(this::toSuggestionResponse).toList(),
            false,
            false
        );
    }

    @Transactional
    @Deprecated(since = "3.2", forRemoval = false)
    public AnalyzeMaterialResponse analyzeMaterial(UUID userId, UUID materialId) {
        ProjectMaterial material = projectMaterialService.findOwnedMaterial(userId, materialId);
        ProjectSpace project = findOwnedProjectById(material.getProjectId());
        ProjectSnapshot previousSnapshot = snapshotRepository.findFirstByProjectIdOrderByCreatedAtDesc(project.getId()).orElse(null);
        String summary = material.getNormalizedSummary();
        List<AiSuggestion> suggestions = generateMockSuggestions(project, material, previousSnapshot);
        return new AnalyzeMaterialResponse(
            material.getId(),
            summary,
            suggestions.stream().map(this::toSuggestionResponse).toList()
        );
    }

    @Transactional(readOnly = true)
    @Deprecated(since = "3.2", forRemoval = false)
    public List<AiSuggestionResponse> listSuggestions(UUID userId, UUID projectId) {
        ProjectSpace project = findOwnedProject(userId, projectId);
        return suggestionRepository.findByProjectIdOrderByCreatedAtDesc(project.getId())
            .stream()
            .map(this::toSuggestionResponse)
            .toList();
    }

    @Transactional
    @Deprecated(since = "3.2", forRemoval = false)
    public AiSuggestionResponse updateSuggestion(UUID userId, UUID suggestionId, AiSuggestionPatchRequest request) {
        AiSuggestion suggestion = findOwnedSuggestion(userId, suggestionId);
        suggestion.update(suggestion.getType(), request.title().trim(), request.reason().trim(), writePayload(request.payload()));
        return toSuggestionResponse(suggestion);
    }

    @Transactional
    @Deprecated(since = "3.2", forRemoval = false)
    public AiSuggestionResponse ignoreSuggestion(UUID userId, UUID suggestionId) {
        AiSuggestion suggestion = findOwnedSuggestion(userId, suggestionId);
        suggestion.markIgnored();
        return toSuggestionResponse(suggestion);
    }

    @Transactional
    @Deprecated(since = "3.2", forRemoval = false)
    public ApplySuggestionsResponse applySuggestions(UUID userId, UUID projectId, List<UUID> suggestionIds) {
        if (suggestionIds.isEmpty()) {
            throw new AppException("NO_SUGGESTIONS_SELECTED", "Select at least one suggestion to apply", HttpStatus.BAD_REQUEST);
        }
        ProjectSpace project = findOwnedProject(userId, projectId);
        List<AiSuggestion> suggestions = suggestionIds.stream()
            .map(id -> findOwnedSuggestion(userId, id))
            .filter(suggestion -> suggestion.getProjectId().equals(project.getId()))
            .toList();
        if (suggestions.size() != suggestionIds.size()) {
            throw new AppException("AI_SUGGESTION_NOT_FOUND", "AI suggestion was not found", HttpStatus.NOT_FOUND);
        }

        List<String> newTasks = new ArrayList<>();
        List<String> newLogs = new ArrayList<>();
        List<String> risks = new ArrayList<>();
        List<String> decisions = new ArrayList<>();
        List<String> learnings = new ArrayList<>();
        List<String> assets = new ArrayList<>();
        List<String> nextSteps = new ArrayList<>();
        String stage = null;
        String positioning = null;
        UUID materialId = null;

        for (AiSuggestion suggestion : suggestions) {
            if (suggestion.getStatus() != AiSuggestionStatus.PENDING) {
                continue;
            }
            materialId = materialId == null ? suggestion.getMaterialId() : materialId;
            Map<String, Object> payload = readPayload(suggestion.getPayload());
            switch (suggestion.getType()) {
                case CREATE_TASK -> {
                    TaskItem task = new TaskItem(project.getId());
                    task.update(
                        text(payload, "title", suggestion.getTitle()),
                        text(payload, "description", suggestion.getReason()),
                        TaskStatus.TODO,
                        TaskPriority.MEDIUM,
                        null,
                        List.of("ai-suggestion")
                    );
                    taskRepository.save(task);
                    newTasks.add(task.getTitle());
                    nextSteps.add(task.getTitle());
                }
                case CREATE_DEV_LOG -> {
                    DevLog log = new DevLog(project.getId());
                    log.update(
                        taskIdFromPayload(project, payload),
                        text(payload, "title", suggestion.getTitle()),
                        text(payload, "content", suggestion.getReason()),
                        DevLogCategory.REVIEW,
                        LocalDate.now(),
                        30,
                        false,
                        List.of("ai-suggestion")
                    );
                    devLogRepository.save(log);
                    newLogs.add(log.getTitle());
                }
                case UPDATE_PROJECT_MEMORY -> {
                    positioning = text(payload, "positioning", suggestion.getTitle());
                    stage = text(payload, "currentStage", stage);
                    addIfPresent(newLogs, payload, "completedCapabilities");
                    addIfPresent(nextSteps, payload, "nextStepSuggestions");
                    projectMemoryService.recordFactSources(project.getId(), ProjectFactSourceType.ACCEPTED_CHANGE, suggestion.getId(), true, Map.of(
                        "positioning", positioning,
                        "currentStage", defaultText(stage, ""),
                        "completedCapabilities", text(payload, "completedCapabilities", ""),
                        "nextStepSuggestions", text(payload, "nextStepSuggestions", "")
                    ));
                }
                case RECORD_TECHNICAL_DECISION -> {
                    decisions.add(suggestion.getTitle());
                    projectMemoryService.recordFactSource(project.getId(), "technicalDecisions", suggestion.getTitle(), ProjectFactSourceType.ACCEPTED_CHANGE, suggestion.getId(), true);
                }
                case RECORD_RISK -> {
                    risks.add(suggestion.getTitle());
                    projectMemoryService.recordFactSource(project.getId(), "currentRisks", suggestion.getTitle(), ProjectFactSourceType.ACCEPTED_CHANGE, suggestion.getId(), true);
                }
                case RECORD_DEVELOPER_LEARNING -> {
                    learnings.add(suggestion.getTitle());
                    projectMemoryService.recordFactSource(project.getId(), "developerLearnings", suggestion.getTitle(), ProjectFactSourceType.ACCEPTED_CHANGE, suggestion.getId(), true);
                }
                case UPDATE_CURRENT_STAGE -> {
                    stage = text(payload, "stage", suggestion.getTitle());
                    projectMemoryService.recordFactSource(project.getId(), "currentStage", stage, ProjectFactSourceType.ACCEPTED_CHANGE, suggestion.getId(), true);
                }
                case GENERATE_ASSET_SUMMARY -> {
                    assets.add(suggestion.getTitle());
                    projectMemoryService.recordFactSource(project.getId(), "showcaseAssets", suggestion.getTitle(), ProjectFactSourceType.ACCEPTED_CHANGE, suggestion.getId(), true);
                }
            }
            suggestion.markApplied();
            changeRepository.findByLinkedSuggestionId(suggestion.getId()).ifPresent(ProjectChange::markAccepted);
        }

        ProjectMemory memory = projectMemoryService.appendFromSuggestionApplication(project, positioning, stage, newTasks, newLogs, risks, decisions, learnings, assets, nextSteps);
        ProjectSnapshot snapshot = createSnapshot(project, memory);
        ProjectEvolutionRecord evolutionRecord = createEvolutionRecord(project, materialId, suggestions, newTasks, newLogs, risks, decisions, learnings, nextSteps);

        return new ApplySuggestionsResponse(
            (int) suggestions.stream().filter(suggestion -> suggestion.getStatus() == AiSuggestionStatus.APPLIED).count(),
            projectMemoryService.toMemoryResponse(memory),
            toSnapshotResponse(snapshot),
            toEvolutionResponse(evolutionRecord)
        );
    }

    @Transactional(readOnly = true)
    public List<ProjectSnapshotResponse> listSnapshots(UUID userId, UUID projectId) {
        ProjectSpace project = findOwnedProject(userId, projectId);
        return snapshotRepository.findByProjectIdOrderByCreatedAtDesc(project.getId())
            .stream()
            .map(this::toSnapshotResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<ProjectEvolutionRecordResponse> listEvolutionRecords(UUID userId, UUID projectId) {
        ProjectSpace project = findOwnedProject(userId, projectId);
        return evolutionRepository.findByProjectIdOrderByCreatedAtDesc(project.getId())
            .stream()
            .map(this::toEvolutionResponse)
            .toList();
    }

    private List<AiSuggestion> generateMockSuggestions(ProjectSpace project, ProjectMaterial material, ProjectSnapshot previousSnapshot) {
        List<AiSuggestion> suggestions = new ArrayList<>();
        String content = material.getContent();
        String summary = material.getNormalizedSummary();
        String previous = previousSnapshot == null ? "暂无上一轮快照" : previousSnapshot.getRecentAchievements();

        suggestions.add(saveSuggestion(
            project.getId(),
            material.getId(),
            AiSuggestionType.UPDATE_PROJECT_MEMORY,
            "更新项目长期档案",
            "本轮材料可以补充项目定位、当前阶段和下一步建议，需确认后写入 Project Memory。",
            Map.of(
                "positioning", defaultText(project.getDescription(), project.getName() + " 项目过程整理与智能管理台"),
                "currentStage", inferStage(content),
                "completedCapabilities", summary,
                "nextStepSuggestions", inferNextStep(content)
            )
        ));
        suggestions.add(saveSuggestion(
            project.getId(),
            material.getId(),
            AiSuggestionType.CREATE_DEV_LOG,
            "沉淀本轮材料分析日志",
            "材料已进入 Project Material，建议记录一条开发过程日志，保留输入来源和分析摘要。",
            Map.of(
                "title", "整理 " + sourceLabel(material.getSourceType()) + " 材料",
                "content", "输入摘要：" + summary + "\n上一轮参考：" + previous
            )
        ));
        suggestions.add(saveSuggestion(
            project.getId(),
            material.getId(),
            AiSuggestionType.CREATE_TASK,
            inferTaskTitle(content),
            "AI 从材料中识别出可执行下一步，建议用户确认后进入任务看板。",
            Map.of(
                "title", inferTaskTitle(content),
                "description", inferNextStep(content)
            )
        ));
        if (containsAny(content, "风险", "阻塞", "失败", "bug", "error", "问题")) {
            suggestions.add(saveSuggestion(
                project.getId(),
                material.getId(),
                AiSuggestionType.RECORD_RISK,
                "跟踪本轮暴露的风险或阻塞",
                "材料中出现风险、阻塞或失败信号，建议进入 Project Memory 的风险区。",
                Map.of("risk", firstSentence(content))
            ));
        }
        if (containsAny(content, "决策", "架构", "provider", "snapshot", "memory", "模型")) {
            suggestions.add(saveSuggestion(
                project.getId(),
                material.getId(),
                AiSuggestionType.RECORD_TECHNICAL_DECISION,
                "记录本轮技术决策",
                "材料中出现架构或模型相关选择，建议沉淀为后续复盘可追溯的技术决策。",
                Map.of("decision", firstSentence(content))
            ));
        }
        if (containsAny(content, "验证", "测试", "收获", "learn", "复盘")) {
            suggestions.add(saveSuggestion(
                project.getId(),
                material.getId(),
                AiSuggestionType.RECORD_DEVELOPER_LEARNING,
                "记录开发者收获",
                "材料中包含验证或复盘信息，适合沉淀为作品集讲述素材。",
                Map.of("learning", firstSentence(content))
            ));
        }
        return suggestions;
    }

    private List<AiSuggestion> generateProjectProfileSuggestions(ProjectSpace project, ProjectMaterial material, ProjectProfileResponse profile) {
        List<AiSuggestion> suggestions = new ArrayList<>();
        suggestions.add(saveSuggestion(
            project.getId(),
            material.getId(),
            AiSuggestionType.UPDATE_PROJECT_MEMORY,
            "建立项目长期档案",
            "系统已从完整项目 zip 中提取项目画像，确认后写入 Project Memory。",
            Map.of(
                "positioning", profile.summary(),
                "currentStage", profile.currentStage(),
                "completedCapabilities", String.join("\n", profile.moduleStructure()),
                "nextStepSuggestions", profile.mostImportantGap()
            )
        ));
        suggestions.add(saveSuggestion(
            project.getId(),
            material.getId(),
            AiSuggestionType.CREATE_DEV_LOG,
            "记录首次完整项目导入",
            "完整项目 zip 已进入 Project Material，可作为后续项目演进对比的初始证据。",
            Map.of(
                "title", "导入完整项目 zip",
                "content", profile.summary() + "\n技术栈：" + String.join(", ", profile.techStack())
            )
        ));
        suggestions.add(saveSuggestion(
            project.getId(),
            material.getId(),
            AiSuggestionType.CREATE_TASK,
            profile.mostImportantGap(),
            "系统从项目结构中识别出当前最该补齐的能力，确认后进入任务看板。",
            Map.of(
                "title", profile.mostImportantGap(),
                "description", "基于完整项目 zip 的本地项目画像生成。"
            )
        ));
        if (profile.looksEmptyShell()) {
            suggestions.add(saveSuggestion(
                project.getId(),
                material.getId(),
                AiSuggestionType.RECORD_RISK,
                "项目结构可能仍偏空壳",
                "完整项目 zip 中缺少 README、测试、启动脚本或核心源码信号，建议优先补齐工程证据。",
                Map.of("risk", "项目结构可能仍偏空壳")
            ));
        }
        return suggestions;
    }

    private AiSuggestion saveSuggestion(UUID projectId, UUID materialId, AiSuggestionType type, String title, String reason, Map<String, Object> payload) {
        AiSuggestion suggestion = new AiSuggestion(projectId, materialId);
        suggestion.update(type, title, reason, writePayload(payload));
        return suggestionRepository.save(suggestion);
    }

    private String cleanMemoryText(String value, String fallback) {
        if (value == null || value.trim().isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private ProjectSnapshot createSnapshot(ProjectSpace project, ProjectMemory memory) {
        List<TaskItem> tasks = taskRepository.findByProjectIdOrderByUpdatedAtDesc(project.getId());
        ProjectSnapshot snapshot = new ProjectSnapshot(project.getId());
        snapshot.update(
            memory.getCurrentStage(),
            "任务总数 " + tasks.size() + "，完成 " + tasks.stream().filter(task -> task.getStatus() == TaskStatus.DONE).count() + "。",
            project.getTechStack().isEmpty() ? "暂无技术栈标签。" : String.join(", ", project.getTechStack()),
            memory.getCompletedCapabilities(),
            memory.getCurrentRisks(),
            memory.getCompletedCapabilities(),
            memory.getNextStepSuggestions(),
            memory.getVersion()
        );
        return snapshotRepository.save(snapshot);
    }

    private ProjectEvolutionRecord createEvolutionRecord(
        ProjectSpace project,
        UUID materialId,
        List<AiSuggestion> suggestions,
        List<String> newTasks,
        List<String> newLogs,
        List<String> risks,
        List<String> decisions,
        List<String> learnings,
        List<String> nextSteps
    ) {
        ProjectEvolutionRecord record = new ProjectEvolutionRecord(project.getId(), materialId);
        record.update(
            "确认并应用 " + suggestions.size() + " 条 AI 建议。",
            suggestions.stream().map(AiSuggestion::getTitle).reduce((first, second) -> first + "\n" + second).orElse("暂无变化。"),
            joinOrFallback(newLogs, "暂无新增成果。"),
            joinOrFallback(risks, "暂无关键问题。"),
            joinOrFallback(decisions, "暂无技术决策。"),
            joinOrFallback(learnings, "暂无开发者收获。"),
            joinOrFallback(nextSteps.isEmpty() ? newTasks : nextSteps, "暂无下一步建议。")
        );
        return evolutionRepository.save(record);
    }

    private ProjectSpace createImportedProject(UUID userId, ProjectProfileResponse profile) {
        ProjectSpace project = new ProjectSpace(userId);
        project.update(
            profile.inferredProjectName(),
            profile.summary(),
            ProjectStatus.BUILDING,
            profile.techStack(),
            "",
            LocalDate.now(),
            null
        );
        return projectRepository.save(project);
    }

    private java.util.Optional<ProjectSpace> findReusableImportedProject(UUID userId, ProjectProfileResponse profile) {
        String inferredName = profile.inferredProjectName();
        if (inferredName == null || inferredName.isBlank()) {
            return java.util.Optional.empty();
        }
        return projectRepository.findFirstByUserIdAndNameIgnoreCaseOrderByUpdatedAtDesc(userId, inferredName.trim());
    }

    private ProjectSpace findOwnedProject(UUID userId, UUID projectId) {
        return projectRepository.findByIdAndUserId(projectId, userId)
            .orElseThrow(() -> new AppException("PROJECT_NOT_FOUND", "Project was not found", HttpStatus.NOT_FOUND));
    }

    private ProjectSpace findOwnedProjectById(UUID projectId) {
        return projectRepository.findById(projectId)
            .orElseThrow(() -> new AppException("PROJECT_NOT_FOUND", "Project was not found", HttpStatus.NOT_FOUND));
    }

    private AiSuggestion findOwnedSuggestion(UUID userId, UUID suggestionId) {
        AiSuggestion suggestion = suggestionRepository.findById(suggestionId)
            .orElseThrow(() -> new AppException("AI_SUGGESTION_NOT_FOUND", "AI suggestion was not found", HttpStatus.NOT_FOUND));
        findOwnedProject(userId, suggestion.getProjectId());
        return suggestion;
    }

    private AiSuggestionResponse toSuggestionResponse(AiSuggestion suggestion) {
        return new AiSuggestionResponse(
            suggestion.getId(),
            suggestion.getProjectId(),
            suggestion.getMaterialId(),
            suggestion.getType(),
            suggestion.getStatus(),
            suggestion.getTitle(),
            suggestion.getReason(),
            readPayload(suggestion.getPayload()),
            suggestion.getCreatedAt(),
            suggestion.getUpdatedAt(),
            suggestion.getResolvedAt()
        );
    }

    private ProjectSnapshotResponse toSnapshotResponse(ProjectSnapshot snapshot) {
        return new ProjectSnapshotResponse(
            snapshot.getId(),
            snapshot.getProjectId(),
            snapshot.getCurrentStage(),
            snapshot.getTaskStatusSummary(),
            snapshot.getTechStackSummary(),
            snapshot.getModuleCompletion(),
            snapshot.getRiskSummary(),
            snapshot.getRecentAchievements(),
            snapshot.getNextStepSuggestions(),
            snapshot.getMemoryVersion(),
            snapshot.getCreatedAt()
        );
    }

    private ProjectEvolutionRecordResponse toEvolutionResponse(ProjectEvolutionRecord record) {
        return new ProjectEvolutionRecordResponse(
            record.getId(),
            record.getProjectId(),
            record.getMaterialId(),
            record.getSummary(),
            record.getDetectedChanges(),
            record.getKeyAchievements(),
            record.getKeyIssues(),
            record.getTechnicalDecisions(),
            record.getDeveloperLearnings(),
            record.getNextSteps(),
            record.getCreatedAt()
        );
    }

    private ProjectResponse toProjectResponse(ProjectSpace project) {
        return new ProjectResponse(
            project.getId(),
            project.getName(),
            project.getDescription(),
            project.getStatus(),
            project.getTechStack(),
            project.getRepoUrl(),
            project.getStartDate(),
            project.getEndDate(),
            project.getCreatedAt(),
            project.getUpdatedAt()
        );
    }

    private String writePayload(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (IOException exception) {
            throw new AppException("INVALID_AI_PAYLOAD", "AI suggestion payload could not be serialized", HttpStatus.BAD_REQUEST);
        }
    }

    private Map<String, Object> readPayload(String payload) {
        try {
            return objectMapper.readValue(payload, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (IOException exception) {
            return Map.of("raw", payload);
        }
    }

    private void addIfPresent(List<String> target, Map<String, Object> payload, String key) {
        String value = text(payload, key, "");
        if (!value.isBlank()) {
            target.add(value);
        }
    }

    private UUID taskIdFromPayload(ProjectSpace project, Map<String, Object> payload) {
        String taskId = text(payload, "taskId", "");
        if (taskId.isBlank()) {
            return null;
        }
        try {
            UUID id = UUID.fromString(taskId);
            return taskRepository.findById(id)
                .filter(task -> task.getProjectId().equals(project.getId()))
                .map(TaskItem::getId)
                .orElse(null);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String text(Map<String, Object> payload, String key, String fallback) {
        Object value = payload.get(key);
        return value == null || value.toString().isBlank() ? defaultText(fallback, "") : value.toString();
    }

    private String inferStage(String content) {
        if (containsAny(content, "V2", "AI", "provider", "材料", "suggestion")) {
            return "V2 Core 构建";
        }
        if (containsAny(content, "上线", "部署", "发布")) {
            return "部署准备";
        }
        if (containsAny(content, "测试", "验证", "验收")) {
            return "验证收敛";
        }
        return "持续开发";
    }

    private String inferTaskTitle(String content) {
        if (containsAny(content, "zip", "项目导入")) {
            return "完善轻量项目导入分析";
        }
        if (containsAny(content, "provider", "DeepSeek", "模型")) {
            return "完善 AI provider 配置与测试";
        }
        if (containsAny(content, "Dashboard", "驾驶舱")) {
            return "升级 Dashboard 项目驾驶舱";
        }
        return "确认并沉淀本轮 AI 建议闭环";
    }

    private String inferNextStep(String content) {
        String lower = content.toLowerCase();
        int index = Math.max(Math.max(lower.indexOf("下一步"), lower.indexOf("next")), lower.indexOf("todo"));
        if (index >= 0) {
            return truncate(content.substring(index).replace("\n", " "), 220);
        }
        return "确认 AI 建议后更新 Project Memory，并继续补齐材料输入到建议确认的闭环。";
    }

    private boolean containsAny(String content, String... needles) {
        String lower = content.toLowerCase();
        for (String needle : needles) {
            if (lower.contains(needle.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private String firstSentence(String content) {
        String normalized = content.replace("\r", "\n").replaceAll("\\s+", " ").trim();
        if (normalized.isBlank()) {
            return "";
        }
        int end = normalized.indexOf('。');
        if (end < 0) {
            end = normalized.indexOf('.');
        }
        if (end < 0) {
            end = Math.min(normalized.length(), 280);
        }
        return truncate(normalized.substring(0, Math.min(normalized.length(), end + 1)), 280);
    }

    private String joinOrFallback(List<String> values, String fallback) {
        return values.isEmpty() ? fallback : String.join("\n", values.stream().map(item -> "- " + item).toList());
    }

    private String sourceLabel(MaterialSourceType sourceType) {
        return switch (sourceType) {
            case AGENT_SUMMARY -> "agent 总结";
            case COMMIT_LOG -> "commit log";
            case DOCX_FILE -> "docx";
            case PROJECT_ZIP -> "项目 zip";
            case README_MARKDOWN -> "Markdown";
            default -> "项目";
        };
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

}
