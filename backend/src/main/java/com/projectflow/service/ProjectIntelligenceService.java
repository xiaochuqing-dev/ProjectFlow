package com.projectflow.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.dto.V2ProjectDtos.AiSuggestionPatchRequest;
import com.projectflow.dto.V2ProjectDtos.AiSuggestionResponse;
import com.projectflow.dto.V2ProjectDtos.AnalyzeMaterialResponse;
import com.projectflow.dto.V2ProjectDtos.ApplySuggestionsResponse;
import com.projectflow.dto.V2ProjectDtos.ProjectImportAnalyzeResponse;
import com.projectflow.dto.V2ProjectDtos.ProjectEvolutionRecordResponse;
import com.projectflow.dto.V2ProjectDtos.ProjectAnalysisResponse;
import com.projectflow.dto.V2ProjectDtos.ProjectAnalysisRecordResponse;
import com.projectflow.dto.V2ProjectDtos.ProjectChangePatchRequest;
import com.projectflow.dto.V2ProjectDtos.ProjectChangeResponse;
import com.projectflow.dto.V2ProjectDtos.ProjectFactSourceResponse;
import com.projectflow.dto.V2ProjectDtos.ProjectFileAnalysisRequest;
import com.projectflow.dto.V2ProjectDtos.ProjectFileAnalysisResponse;
import com.projectflow.dto.V2ProjectDtos.ProjectLocalPathRequest;
import com.projectflow.dto.V2ProjectDtos.ProjectMaterialResponse;
import com.projectflow.dto.V2ProjectDtos.ProjectMemoryResponse;
import com.projectflow.dto.V2ProjectDtos.ProjectMemoryUpdateRequest;
import com.projectflow.dto.V2ProjectDtos.ProjectProfileResponse;
import com.projectflow.dto.V2ProjectDtos.ProjectSnapshotResponse;
import com.projectflow.dto.ProjectDtos.ProjectResponse;
import com.projectflow.entity.AiProvider;
import com.projectflow.entity.AiProviderType;
import com.projectflow.entity.AiSuggestion;
import com.projectflow.entity.AiSuggestionStatus;
import com.projectflow.entity.AiSuggestionType;
import com.projectflow.entity.DevLog;
import com.projectflow.entity.DevLogCategory;
import com.projectflow.entity.MaterialSourceType;
import com.projectflow.entity.ProjectEvolutionRecord;
import com.projectflow.entity.ProjectAnalysisRecord;
import com.projectflow.entity.ProjectAnalysisRecordType;
import com.projectflow.entity.ProjectChange;
import com.projectflow.entity.ProjectChangeStatus;
import com.projectflow.entity.ProjectFactSource;
import com.projectflow.entity.ProjectFactSourceType;
import com.projectflow.entity.ProjectMaterial;
import com.projectflow.entity.ProjectMemory;
import com.projectflow.entity.ProjectSnapshot;
import com.projectflow.entity.ProjectSpace;
import com.projectflow.entity.ProjectStatus;
import com.projectflow.entity.TaskItem;
import com.projectflow.entity.TaskPriority;
import com.projectflow.entity.TaskStatus;
import com.projectflow.repository.AiProviderRepository;
import com.projectflow.repository.AiSuggestionRepository;
import com.projectflow.repository.DevLogRepository;
import com.projectflow.repository.ProjectEvolutionRecordRepository;
import com.projectflow.repository.ProjectAnalysisRecordRepository;
import com.projectflow.repository.ProjectChangeRepository;
import com.projectflow.repository.ProjectFactSourceRepository;
import com.projectflow.repository.ProjectMaterialRepository;
import com.projectflow.repository.ProjectMemoryRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.repository.ProjectSnapshotRepository;
import com.projectflow.repository.TaskRepository;
import com.projectflow.support.AppException;

@Service
public class ProjectIntelligenceService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProjectIntelligenceService.class);
    private static final int MAX_MATERIAL_CHARS = 500_000;
    private static final int MAX_ZIP_ENTRIES = 220;
    private static final int MAX_FILE_SNIPPET_CHARS = 6_000;
    private static final int MAX_INDEXED_SNIPPET_CHARS = 160_000;
    private static final int MAX_MODEL_ATTEMPTS = 2;
    private static final int MODEL_ANALYSIS_MAX_TOKENS = 100_000;
    private static final Duration MODEL_REQUEST_TIMEOUT = Duration.ofSeconds(75);

    private final ProjectRepository projectRepository;
    private final AiProviderRepository aiProviderRepository;
    private final ProjectMaterialRepository materialRepository;
    private final AiSuggestionRepository suggestionRepository;
    private final ProjectMemoryRepository memoryRepository;
    private final ProjectSnapshotRepository snapshotRepository;
    private final ProjectEvolutionRecordRepository evolutionRepository;
    private final ProjectAnalysisRecordRepository analysisRecordRepository;
    private final ProjectChangeRepository changeRepository;
    private final ProjectFactSourceRepository factSourceRepository;
    private final TaskRepository taskRepository;
    private final DevLogRepository devLogRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public ProjectIntelligenceService(
        ProjectRepository projectRepository,
        AiProviderRepository aiProviderRepository,
        ProjectMaterialRepository materialRepository,
        AiSuggestionRepository suggestionRepository,
        ProjectMemoryRepository memoryRepository,
        ProjectSnapshotRepository snapshotRepository,
        ProjectEvolutionRecordRepository evolutionRepository,
        ProjectAnalysisRecordRepository analysisRecordRepository,
        ProjectChangeRepository changeRepository,
        ProjectFactSourceRepository factSourceRepository,
        TaskRepository taskRepository,
        DevLogRepository devLogRepository,
        ObjectMapper objectMapper
    ) {
        this.projectRepository = projectRepository;
        this.aiProviderRepository = aiProviderRepository;
        this.materialRepository = materialRepository;
        this.suggestionRepository = suggestionRepository;
        this.memoryRepository = memoryRepository;
        this.snapshotRepository = snapshotRepository;
        this.evolutionRepository = evolutionRepository;
        this.analysisRecordRepository = analysisRecordRepository;
        this.changeRepository = changeRepository;
        this.factSourceRepository = factSourceRepository;
        this.taskRepository = taskRepository;
        this.devLogRepository = devLogRepository;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();
    }

    @Transactional(readOnly = true)
    public List<ProjectMaterialResponse> listMaterials(UUID userId, UUID projectId) {
        ProjectSpace project = findOwnedProject(userId, projectId);
        return materialRepository.findByProjectIdOrderByCreatedAtDesc(project.getId())
            .stream()
            .map(this::toMaterialResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public ProjectAnalysisResponse runProjectAnalysis(UUID userId, UUID projectId) {
        ProjectSpace project = findOwnedProject(userId, projectId);
        ProjectMaterial zipMaterial = latestZipMaterial(project.getId());
        String analysisMaterial = sanitizeProjectMaterialForAnalysis(zipMaterial.getContent());
        ProjectAnalysisResponse fallback = localProjectAnalysis(project, analysisMaterial);
        AiProvider provider = configuredProvider(userId);
        if (provider == null) {
            return fallback;
        }
        try {
            String prompt = """
                你是 ProjectFlow 的项目架构分析器。只依据下方项目材料分析，所有自然语言必须使用简体中文。
                返回严格 JSON，字段为：
                summary, architecture, modules, risks, importantFiles, evidence, limitations, confidence。
                modules、risks、importantFiles、evidence、limitations 必须是字符串数组。

                质量要求：
                1. summary 用 3-5 句说明项目用途、主要技术组成和当前工程状态。
                2. architecture 必须结合真实目录或配置文件说明前端、后端、数据和部署关系。
                3. 每项风险必须写明证据文件和可能影响；无证据时不要猜测。
                4. evidence 至少列出 3 条“文件路径：观察到的事实”。
                5. limitations 明确列出材料缺失、未读取文件或无法确认的内容。
                6. 技术名、文件路径和代码标识符保留原文，禁止输出完整英文说明句。

                项目名称：%s
                已有描述：%s
                项目材料：
                %s
                """.formatted(project.getName(), project.getDescription(), truncate(analysisMaterial, 20_000));
            JsonNode json = callModelJson(provider, prompt, MODEL_ANALYSIS_MAX_TOKENS);
            return new ProjectAnalysisResponse(
                chineseTextOr(json, "summary", fallback.summary()),
                chineseTextOr(json, "architecture", fallback.architecture()),
                stringArrayOr(json, "modules", fallback.modules()),
                chineseStringArrayOr(json, "risks", fallback.risks()),
                stringArrayOr(json, "importantFiles", fallback.importantFiles()),
                chineseStringArrayOr(json, "evidence", fallback.evidence()),
                chineseStringArrayOr(json, "limitations", fallback.limitations()),
                true,
                true,
                provider.getName(),
                "MODEL_ANALYSIS",
                textOr(json, "confidence", "medium"),
                "模型已完成项目分析；结论已附带文件证据和分析局限。"
            );
        } catch (Exception exception) {
            LOGGER.warn(
                "Project model analysis fell back to local rules: projectId={}, provider={}, error={}",
                projectId,
                provider.getName(),
                exception.toString()
            );
            return new ProjectAnalysisResponse(
                fallback.summary(),
                fallback.architecture(),
                fallback.modules(),
                fallback.risks(),
                fallback.importantFiles(),
                fallback.evidence(),
                fallback.limitations(),
                true,
                false,
                provider.getName(),
                "LOCAL_RULE",
                fallback.confidence(),
                "模型分析失败，已保留本地规则结果。" + modelFailureMessage(exception)
            );
        }
    }

    @Transactional(readOnly = true)
    public ProjectFileAnalysisResponse analyzeProjectFile(UUID userId, UUID projectId, ProjectFileAnalysisRequest request) {
        ProjectSpace project = findOwnedProject(userId, projectId);
        ProjectMaterial zipMaterial = latestZipMaterial(project.getId());
        String analysisMaterial = sanitizeProjectMaterialForAnalysis(zipMaterial.getContent());
        List<String> paths = parseDirectoryTree(analysisMaterial);
        String requestedPath = request.path().trim();
        if (paths.stream().noneMatch(path -> path.equals(requestedPath))) {
            throw new AppException("PROJECT_FILE_NOT_FOUND", "Project file was not found in imported zip material", HttpStatus.NOT_FOUND);
        }

        String fileContent = extractIndexedFileContent(analysisMaterial, requestedPath);
        AiProvider provider = configuredProvider(userId);
        ProjectFileAnalysisResponse fallback = localFileAnalysis(
            requestedPath,
            fileContent,
            provider != null,
            provider == null ? null : provider.getName(),
            "LOCAL_RULE",
            "已使用本地规则生成基础解释。"
        );
        if (provider == null) {
            return fallback;
        }
        if (isSensitivePath(requestedPath)) {
            return localFileAnalysis(requestedPath, "", true, provider.getName(), "LOCAL_RULE", "敏感文件不会发送给模型，已使用本地规则解释。");
        }
        try {
            String prompt = """
                你是 ProjectFlow 的文件分析器。所有自然语言必须使用简体中文。
                返回严格 JSON，字段为：
                path, fileType, role, summary, importance, riskLevel, riskNotes,
                evidence, relatedFiles, limitations, confidence。
                evidence 和 relatedFiles 必须是字符串数组。

                质量要求：
                1. role 说明该文件在当前项目中的具体职责，不写通用模板话术。
                2. summary 必须引用可见的类名、依赖、配置项、函数或文本事实。
                3. riskNotes 说明证据、影响和建议检查点；没有风险证据时明确写“未发现明确风险证据”。
                4. evidence 至少列出 2 条代码或配置事实；没有文件内容时只能依据路径，并在 limitations 中说明。
                5. relatedFiles 只填写项目材料中真实存在的相关路径。
                6. 技术名、路径和代码标识符保留原文，禁止输出完整英文说明句。

                项目：%s
                文件路径：%s
                文件内容：
                %s

                项目结构摘要：
                %s
                """.formatted(
                    project.getName(),
                    requestedPath,
                    fileContent.isBlank() ? "[未索引到文件内容，只能依据路径分析]" : truncate(fileContent, MAX_FILE_SNIPPET_CHARS),
                    fileStructureContext(analysisMaterial, requestedPath)
                );
            JsonNode json = callModelJson(provider, prompt, MODEL_ANALYSIS_MAX_TOKENS);
            return new ProjectFileAnalysisResponse(
                requestedPath,
                textOr(json, "fileType", fallback.fileType()),
                chineseTextOr(json, "role", fallback.role()),
                chineseTextOr(json, "summary", fallback.summary()),
                textOr(json, "importance", fallback.importance()),
                textOr(json, "riskLevel", fallback.riskLevel()),
                chineseTextOr(json, "riskNotes", fallback.riskNotes()),
                chineseStringArrayOr(json, "evidence", fallback.evidence()),
                stringArrayOr(json, "relatedFiles", fallback.relatedFiles()),
                chineseTextOr(json, "limitations", fallback.limitations()),
                true,
                true,
                provider.getName(),
                "MODEL_ANALYSIS",
                textOr(json, "confidence", "medium"),
                "模型已根据已索引的文件内容生成中文解释。"
            );
        } catch (Exception exception) {
            LOGGER.warn(
                "File model analysis fell back to local rules: projectId={}, path={}, provider={}, error={}",
                projectId,
                requestedPath,
                provider.getName(),
                exception.toString()
            );
            return localFileAnalysis(
                requestedPath,
                fileContent,
                true,
                provider.getName(),
                "LOCAL_RULE",
                "模型分析失败，已使用本地规则解释。" + modelFailureMessage(exception)
            );
        }
    }

    @Transactional
    public UUID createProjectAnalysisRecord(UUID userId, UUID projectId, ProjectAnalysisResponse response) {
        ProjectSpace project = findOwnedProject(userId, projectId);
        ProjectAnalysisRecord record = new ProjectAnalysisRecord(project.getId(), ProjectAnalysisRecordType.PROJECT);
        record.update(
            null,
            response.summary(),
            projectAnalysisDetails(response),
            response.analysisSource(),
            response.modelUsed(),
            response.providerName(),
            response.confidence()
        );
        return analysisRecordRepository.save(record).getId();
    }

    @Transactional
    public UUID createFileAnalysisRecord(UUID userId, UUID projectId, ProjectFileAnalysisResponse response) {
        ProjectSpace project = findOwnedProject(userId, projectId);
        ProjectAnalysisRecord record = new ProjectAnalysisRecord(project.getId(), ProjectAnalysisRecordType.FILE);
        record.update(
            response.path(),
            response.summary(),
            fileAnalysisDetails(response),
            response.analysisSource(),
            response.modelUsed(),
            response.providerName(),
            response.confidence()
        );
        return analysisRecordRepository.save(record).getId();
    }

    @Transactional(readOnly = true)
    public List<ProjectAnalysisRecordResponse> listAnalysisRecords(UUID userId, UUID projectId) {
        ProjectSpace project = findOwnedProject(userId, projectId);
        return analysisRecordRepository.findByProjectIdOrderByCreatedAtDesc(project.getId())
            .stream()
            .map(this::toAnalysisRecordResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public ProjectAnalysisRecordResponse analysisRecordDetail(UUID userId, UUID recordId) {
        return toAnalysisRecordResponse(findOwnedAnalysisRecord(userId, recordId));
    }

    @Transactional
    public void deleteAnalysisRecord(UUID userId, UUID recordId) {
        analysisRecordRepository.delete(findOwnedAnalysisRecord(userId, recordId));
    }

    @Transactional
    public ProjectMaterialResponse createTextMaterial(UUID userId, UUID projectId, MaterialSourceType sourceType, String content) {
        ProjectSpace project = findOwnedProject(userId, projectId);
        return toMaterialResponse(saveMaterial(project.getId(), sourceType, null, content));
    }

    @Transactional
    public ProjectMaterialResponse createFileMaterial(UUID userId, UUID projectId, MaterialSourceType sourceType, MultipartFile file) {
        ProjectSpace project = findOwnedProject(userId, projectId);
        String fileName = cleanFileName(file.getOriginalFilename());
        String content = readUploadedFile(fileName, file);
        MaterialSourceType detectedType = sourceType == null || sourceType == MaterialSourceType.OTHER
            ? detectFileSourceType(fileName)
            : sourceType;
        return toMaterialResponse(saveMaterial(project.getId(), detectedType, fileName, content));
    }

    @Transactional
    public ProjectMaterialResponse createZipMaterial(UUID userId, UUID projectId, MultipartFile file) {
        ProjectSpace project = findOwnedProject(userId, projectId);
        String content = summarizeZip(file);
        return toMaterialResponse(saveMaterial(project.getId(), MaterialSourceType.PROJECT_ZIP, cleanFileName(file.getOriginalFilename()), content));
    }

    @Transactional
    public ProjectImportAnalyzeResponse importProjectZip(UUID userId, UUID projectId, MultipartFile file) {
        ZipProjectScan scan = scanZip(file);
        ProjectSpace project = projectId == null
            ? findReusableImportedProject(userId, scan.profile()).orElseGet(() -> createImportedProject(userId, scan.profile()))
            : findOwnedProject(userId, projectId);
        ProjectMaterial material = saveMaterial(project.getId(), MaterialSourceType.PROJECT_ZIP, cleanFileName(file.getOriginalFilename()), scan.content());
        List<AiSuggestion> suggestions = generateProjectProfileSuggestions(project, material, scan.profile());

        return new ProjectImportAnalyzeResponse(
            toProjectResponse(project),
            toMaterialResponse(material),
            scan.profile(),
            suggestions.stream().map(this::toSuggestionResponse).toList(),
            false,
            false
        );
    }

    @Transactional(readOnly = true)
    public ProjectMaterialResponse materialDetail(UUID userId, UUID materialId) {
        return toMaterialResponse(findOwnedMaterial(userId, materialId));
    }

    @Transactional
    public AnalyzeMaterialResponse analyzeMaterial(UUID userId, UUID materialId) {
        ProjectMaterial material = findOwnedMaterial(userId, materialId);
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
    public List<AiSuggestionResponse> listSuggestions(UUID userId, UUID projectId) {
        ProjectSpace project = findOwnedProject(userId, projectId);
        return suggestionRepository.findByProjectIdOrderByCreatedAtDesc(project.getId())
            .stream()
            .map(this::toSuggestionResponse)
            .toList();
    }

    @Transactional
    public AiSuggestionResponse updateSuggestion(UUID userId, UUID suggestionId, AiSuggestionPatchRequest request) {
        AiSuggestion suggestion = findOwnedSuggestion(userId, suggestionId);
        suggestion.update(suggestion.getType(), request.title().trim(), request.reason().trim(), writePayload(request.payload()));
        return toSuggestionResponse(suggestion);
    }

    @Transactional
    public AiSuggestionResponse ignoreSuggestion(UUID userId, UUID suggestionId) {
        AiSuggestion suggestion = findOwnedSuggestion(userId, suggestionId);
        suggestion.markIgnored();
        return toSuggestionResponse(suggestion);
    }

    @Transactional
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
                    recordFactSources(project.getId(), ProjectFactSourceType.ACCEPTED_CHANGE, suggestion.getId(), true, Map.of(
                        "positioning", positioning,
                        "currentStage", defaultText(stage, ""),
                        "completedCapabilities", text(payload, "completedCapabilities", ""),
                        "nextStepSuggestions", text(payload, "nextStepSuggestions", "")
                    ));
                }
                case RECORD_TECHNICAL_DECISION -> {
                    decisions.add(suggestion.getTitle());
                    recordFactSource(project.getId(), "technicalDecisions", suggestion.getTitle(), ProjectFactSourceType.ACCEPTED_CHANGE, suggestion.getId(), true);
                }
                case RECORD_RISK -> {
                    risks.add(suggestion.getTitle());
                    recordFactSource(project.getId(), "currentRisks", suggestion.getTitle(), ProjectFactSourceType.ACCEPTED_CHANGE, suggestion.getId(), true);
                }
                case RECORD_DEVELOPER_LEARNING -> {
                    learnings.add(suggestion.getTitle());
                    recordFactSource(project.getId(), "developerLearnings", suggestion.getTitle(), ProjectFactSourceType.ACCEPTED_CHANGE, suggestion.getId(), true);
                }
                case UPDATE_CURRENT_STAGE -> {
                    stage = text(payload, "stage", suggestion.getTitle());
                    recordFactSource(project.getId(), "currentStage", stage, ProjectFactSourceType.ACCEPTED_CHANGE, suggestion.getId(), true);
                }
                case GENERATE_ASSET_SUMMARY -> {
                    assets.add(suggestion.getTitle());
                    recordFactSource(project.getId(), "showcaseAssets", suggestion.getTitle(), ProjectFactSourceType.ACCEPTED_CHANGE, suggestion.getId(), true);
                }
            }
            suggestion.markApplied();
            changeRepository.findByLinkedSuggestionId(suggestion.getId()).ifPresent(ProjectChange::markAccepted);
        }

        ProjectMemory memory = updateMemory(project, positioning, stage, newTasks, newLogs, risks, decisions, learnings, assets, nextSteps);
        ProjectSnapshot snapshot = createSnapshot(project, memory);
        ProjectEvolutionRecord evolutionRecord = createEvolutionRecord(project, materialId, suggestions, newTasks, newLogs, risks, decisions, learnings, nextSteps);

        return new ApplySuggestionsResponse(
            (int) suggestions.stream().filter(suggestion -> suggestion.getStatus() == AiSuggestionStatus.APPLIED).count(),
            toMemoryResponse(memory),
            toSnapshotResponse(snapshot),
            toEvolutionResponse(evolutionRecord)
        );
    }

    @Transactional(readOnly = true)
    public ProjectMemoryResponse getMemory(UUID userId, UUID projectId) {
        ProjectSpace project = findOwnedProject(userId, projectId);
        ProjectMemory memory = memoryRepository.findByProjectId(project.getId()).orElseGet(() -> initialMemory(project));
        return toMemoryResponse(memory);
    }

    @Transactional(readOnly = true)
    public List<ProjectChangeResponse> listChanges(UUID userId, UUID projectId) {
        ProjectSpace project = findOwnedProject(userId, projectId);
        return changeRepository.findByProjectIdOrderByCreatedAtDesc(project.getId())
            .stream()
            .map(this::toChangeResponse)
            .toList();
    }

    @Transactional
    public ProjectChangeResponse updateChange(UUID userId, UUID changeId, ProjectChangePatchRequest request) {
        ProjectChange change = findOwnedChange(userId, changeId);
        change.update(
            change.getSourceType(),
            change.getSourceRef(),
            change.getLinkedSuggestionId(),
            request.changeKind(),
            request.impactLevel(),
            request.title().trim(),
            request.summary().trim(),
            cleanMemoryText(request.details(), ""),
            cleanMemoryText(request.affectedFiles(), ""),
            cleanMemoryText(request.relatedTasks(), ""),
            cleanMemoryText(request.testEvidence(), ""),
            cleanMemoryText(request.buildEvidence(), ""),
            cleanMemoryText(request.riskNotes(), ""),
            cleanMemoryText(request.decisionNotes(), ""),
            cleanMemoryText(request.learningNotes(), ""),
            cleanMemoryText(request.assetCandidates(), "")
        );
        return toChangeResponse(change);
    }

    @Transactional
    public ProjectChangeResponse acceptChange(UUID userId, UUID changeId) {
        ProjectChange change = findOwnedChange(userId, changeId);
        ProjectSpace project = findOwnedProject(userId, change.getProjectId());
        if (change.getLinkedSuggestionId() != null && change.getStatus() == ProjectChangeStatus.PENDING) {
            applySuggestions(userId, change.getProjectId(), List.of(change.getLinkedSuggestionId()));
        }
        applyAcceptedChangeToMemory(project, change);
        change.markAccepted();
        return toChangeResponse(change);
    }

    @Transactional
    public ProjectChangeResponse ignoreChange(UUID userId, UUID changeId) {
        ProjectChange change = findOwnedChange(userId, changeId);
        change.markIgnored();
        return toChangeResponse(change);
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
        Path projectRoot = resolveLocalProjectRoot(request.localProjectPath());
        ProjectMemory memory = memoryRepository.findByProjectId(project.getId()).orElseGet(() -> initialMemory(project));
        memory.rememberLocalProjectPath(projectRoot.toString());
        return toMemoryResponse(memoryRepository.save(memory));
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

    private ProjectMaterial saveMaterial(UUID projectId, MaterialSourceType sourceType, String fileName, String content) {
        String normalized = normalizeContent(content);
        ProjectMaterial material = new ProjectMaterial(projectId);
        material.update(sourceType, fileName, truncate(content.trim(), MAX_MATERIAL_CHARS), normalized);
        return materialRepository.save(material);
    }

    private ProjectAnalysisResponse localProjectAnalysis(ProjectSpace project, String materialContent) {
        List<String> paths = parseDirectoryTree(materialContent);
        List<String> modules = paths.stream()
            .map(this::moduleName)
            .distinct()
            .limit(12)
            .toList();
        List<String> importantFiles = paths.stream()
            .filter(this::isImportantProjectFile)
            .limit(12)
            .toList();
        String readmeTitle = extractReadmeTitle(materialContent);
        String summary = !readmeTitle.isBlank()
            ? readmeTitle + "：已导入 " + paths.size() + " 个文件信号，当前使用本地规则生成基础项目画像。"
            : project.getName() + "：已导入 " + paths.size() + " 个文件信号，当前使用本地规则生成基础项目画像。";
        List<String> risks = new ArrayList<>();
        risks.add("模型深度分析尚未完成；当前结论只来自目录树、文件名和关键配置规则。");
        if (paths.stream().anyMatch(path -> path.toLowerCase().contains("docker-compose"))) {
            risks.add("存在部署配置文件，后续需要确认端口、凭据来源和服务依赖。");
        }
        if (paths.stream().noneMatch(path -> path.toLowerCase().contains("test"))) {
            risks.add("未识别测试文件，工程质量证据可能不足。");
        }
        List<String> evidence = new ArrayList<>();
        if (!importantFiles.isEmpty()) {
            importantFiles.stream()
                .limit(6)
                .forEach(path -> evidence.add(path + "：已在导入项目中识别，可作为架构判断依据。"));
        }
        if (evidence.isEmpty()) {
            evidence.add("目录树：已识别 " + paths.size() + " 个可分析文件路径。");
        }
        List<String> limitations = List.of(
            "当前结果未使用模型，仅依据目录、文件名和已索引文本生成。",
            "未索引的二进制文件、生成产物和敏感文件未参与分析。"
        );
        return new ProjectAnalysisResponse(
            summary,
            modules.isEmpty() ? "尚未识别模块。请先导入完整项目 zip。" : "识别到模块：" + String.join("、", modules) + "。点击模块可进入文件理解页集中查看。",
            modules,
            risks,
            importantFiles,
            evidence,
            limitations,
            false,
            false,
            null,
            "LOCAL_RULE",
            "medium",
            "未配置可用模型，已使用本地规则生成基础项目画像。"
        );
    }

    private ProjectFileAnalysisResponse localFileAnalysis(
        String path,
        String fileContent,
        boolean providerConfigured,
        String providerName,
        String analysisSource,
        String message
    ) {
        String fileType = inferFileType(path);
        String role = inferFileRole(path, fileType);
        String riskLevel = inferFileRiskLevel(path, fileType);
        List<String> evidence = new ArrayList<>();
        evidence.add(path + "：文件路径和扩展名已从导入项目中确认。");
        if (!fileContent.isBlank()) {
            String firstSignal = fileContent.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .findFirst()
                .orElse("");
            if (!firstSignal.isBlank()) {
                evidence.add("文件内容首个有效信号：" + truncate(firstSignal, 180));
            }
        }
        return new ProjectFileAnalysisResponse(
            path,
            fileType,
            role,
            role + (fileContent.isBlank()
                ? "。当前未索引到文件正文，只能依据路径和文件类型给出基础判断。"
                : "。已读取导入时保存的安全文本片段，可供模型进一步解释。"),
            inferFileImportance(path, fileType),
            riskLevel,
            inferFileRiskNotes(path, fileType, riskLevel),
            evidence,
            List.of(),
            fileContent.isBlank()
                ? "导入材料中没有该文件正文；重新导入项目 zip 后可补充安全文本片段。"
                : "本地规则未执行完整语义分析，代码调用关系仍需模型或开发者确认。",
            providerConfigured,
            false,
            providerName,
            analysisSource,
            "medium",
            message
        );
    }

    private ProjectMaterial latestZipMaterial(UUID projectId) {
        return materialRepository.findByProjectIdOrderByCreatedAtDesc(projectId)
            .stream()
            .filter(material -> material.getSourceType() == MaterialSourceType.PROJECT_ZIP)
            .findFirst()
            .orElseThrow(() -> new AppException("PROJECT_ZIP_NOT_FOUND", "Import a project zip before running project analysis", HttpStatus.BAD_REQUEST));
    }

    private AiProvider configuredProvider(UUID userId) {
        return aiProviderRepository.findByUserIdOrderByDefaultEnabledDescUpdatedAtDesc(userId)
            .stream()
            .filter(provider -> provider.getType() != AiProviderType.MOCK)
            .filter(provider -> provider.getApiKey() != null && !provider.getApiKey().isBlank())
            .findFirst()
            .orElse(null);
    }

    private JsonNode callModelJson(AiProvider provider, String prompt, int outputTokenLimit) throws IOException, InterruptedException {
        Map<String, Object> body = Map.of(
            "model", provider.getModelName(),
            "messages", List.of(
                Map.of(
                    "role",
                    "system",
                    "content",
                    "只返回合法 JSON，不要 Markdown 代码块。所有自然语言字段必须使用简体中文；技术名、文件路径和代码标识符保留原文。"
                ),
                Map.of("role", "user", "content", prompt)
            ),
            "temperature", Math.min(provider.getTemperature(), 0.3),
            "max_tokens", Math.min(provider.getMaxTokens(), outputTokenLimit)
        );
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(provider.getBaseUrl() + "/chat/completions"))
            .timeout(MODEL_REQUEST_TIMEOUT)
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + provider.getApiKey())
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
            .build();
        for (int attempt = 1; attempt <= MAX_MODEL_ATTEMPTS; attempt++) {
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return parseModelJson(response.body());
                }
                if (attempt < MAX_MODEL_ATTEMPTS && isTransientModelStatus(response.statusCode())) {
                    pauseBeforeRetry(attempt);
                    continue;
                }
                throw new NonRetryableModelException("model HTTP " + response.statusCode());
            } catch (HttpTimeoutException exception) {
                if (attempt >= MAX_MODEL_ATTEMPTS) {
                    throw exception;
                }
                pauseBeforeRetry(attempt);
            } catch (IOException exception) {
                if (exception instanceof NonRetryableModelException || attempt >= MAX_MODEL_ATTEMPTS) {
                    throw exception;
                }
                pauseBeforeRetry(attempt);
            }
        }
        throw new IOException("model request failed");
    }

    private JsonNode parseModelJson(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        String content = root.at("/choices/0/message/content").asText("");
        if (content.isBlank()) {
            throw new IOException("empty model content");
        }
        return objectMapper.readTree(extractJsonObject(content));
    }

    private boolean isTransientModelStatus(int statusCode) {
        return statusCode == 429 || statusCode == 500 || statusCode == 502 || statusCode == 503 || statusCode == 504;
    }

    private void pauseBeforeRetry(int attempt) throws InterruptedException {
        Thread.sleep(400L * attempt);
    }

    private String modelFailureMessage(Exception exception) {
        if (exception instanceof HttpTimeoutException) {
            return "模型请求在 " + MODEL_REQUEST_TIMEOUT.toSeconds() + " 秒内未完成。";
        }
        String message = exception.getMessage();
        return message == null || message.isBlank()
            ? "失败类型：" + exception.getClass().getSimpleName()
            : "原因：" + truncate(message, 180);
    }

    private String fileStructureContext(String materialContent, String requestedPath) {
        String targetModule = moduleName(requestedPath);
        LinkedHashSet<String> relatedPaths = new LinkedHashSet<>();
        parseDirectoryTree(materialContent).stream()
            .filter(path -> moduleName(path).equals(targetModule))
            .limit(50)
            .forEach(relatedPaths::add);
        parseDirectoryTree(materialContent).stream()
            .filter(this::isImportantProjectFile)
            .limit(20)
            .forEach(relatedPaths::add);
        return relatedPaths.isEmpty()
            ? "[未识别到相关项目结构]"
            : truncate(String.join("\n", relatedPaths.stream().map(path -> "- " + path).toList()), 6_000);
    }

    private static final class NonRetryableModelException extends IOException {
        private NonRetryableModelException(String message) {
            super(message);
        }
    }

    private String extractJsonObject(String content) throws IOException {
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IOException("model content is not JSON");
        }
        return content.substring(start, end + 1);
    }

    private String textOr(JsonNode json, String field, String fallback) {
        JsonNode value = json.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            return fallback;
        }
        return value.asText();
    }

    private String chineseTextOr(JsonNode json, String field, String fallback) {
        String value = textOr(json, field, fallback);
        return containsChinese(value) ? value : fallback;
    }

    private List<String> stringArrayOr(JsonNode json, String field, List<String> fallback) {
        JsonNode value = json.get(field);
        if (value == null || !value.isArray() || value.isEmpty()) {
            return fallback;
        }
        List<String> result = new ArrayList<>();
        value.forEach(item -> {
            if (!item.asText("").isBlank()) {
                result.add(item.asText());
            }
        });
        return result.isEmpty() ? fallback : result;
    }

    private List<String> chineseStringArrayOr(JsonNode json, String field, List<String> fallback) {
        List<String> values = stringArrayOr(json, field, fallback);
        if (values == fallback) {
            return fallback;
        }
        List<String> chineseValues = values.stream()
            .filter(this::containsChinese)
            .toList();
        return chineseValues.isEmpty() ? fallback : chineseValues;
    }

    private boolean containsChinese(String value) {
        return value != null && value.codePoints()
            .anyMatch(codePoint -> codePoint >= 0x4E00 && codePoint <= 0x9FFF);
    }

    private List<String> parseDirectoryTree(String content) {
        List<String> result = new ArrayList<>();
        boolean inTree = false;
        for (String line : content.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.equals("## Directory tree")) {
                inTree = true;
                continue;
            }
            if (inTree && trimmed.startsWith("## ")) {
                break;
            }
            if (inTree && trimmed.startsWith("- ")) {
                String path = trimmed.substring(2);
                if (!isProjectNoisePath(path)) {
                    result.add(path);
                }
            }
        }
        return result;
    }

    private String sanitizeProjectMaterialForAnalysis(String content) {
        StringBuilder sanitized = new StringBuilder();
        boolean skippingNoiseBlock = false;
        for (String line : content.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("### ")) {
                String path = trimmed.substring(4).trim();
                skippingNoiseBlock = isProjectNoisePath(path) || lineContainsProjectNoise(path);
            } else if (trimmed.startsWith("## ")) {
                skippingNoiseBlock = false;
            }
            if (skippingNoiseBlock || lineContainsProjectNoise(line)) {
                continue;
            }
            sanitized.append(line).append('\n');
        }
        return sanitized.toString().trim();
    }

    private boolean lineContainsProjectNoise(String value) {
        String lower = value.toLowerCase().replace("\\", "/");
        return lower.contains(".codex-run/")
            || lower.contains("old-git-")
            || lower.contains(".git/objects/")
            || lower.contains(".git/config")
            || lower.contains(".git/head");
    }

    private String extractReadmeTitle(String content) {
        for (String line : content.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("# ") && !trimmed.equals("# Project zip summary")) {
                return trimmed.substring(2).trim();
            }
        }
        return "";
    }

    private String moduleName(String path) {
        String lower = path.toLowerCase();
        if (looksLikeFrontendPath(lower)) {
            return "frontend";
        }
        if (looksLikeBackendPath(lower)) {
            return "backend";
        }
        if (lower.startsWith("docs/") || lower.endsWith("readme.md") || lower.endsWith("agents.md")) {
            return "docs";
        }
        if (lower.contains(".vscode/") || lower.contains(".github/") || lower.contains("docker") || lower.contains(".env")) {
            return "config";
        }
        if (lower.endsWith(".bat") || lower.endsWith(".ps1") || lower.endsWith(".sh") || lower.contains("start-")) {
            return "scripts";
        }
        return path.contains("/") ? path.substring(0, path.indexOf('/')) : "root";
    }

    private boolean isImportantProjectFile(String path) {
        String lower = path.toLowerCase();
        return lower.endsWith("package.json")
            || lower.endsWith("pom.xml")
            || lower.endsWith("build.gradle")
            || lower.endsWith("build.gradle.kts")
            || lower.endsWith("settings.gradle")
            || lower.endsWith("pyproject.toml")
            || lower.endsWith("requirements.txt")
            || lower.endsWith("go.mod")
            || lower.endsWith("cargo.toml")
            || lower.endsWith("composer.json")
            || lower.endsWith(".csproj")
            || lower.endsWith("docker-compose.yml")
            || lower.endsWith("readme.md")
            || lower.endsWith("page.tsx")
            || lower.endsWith("app.tsx")
            || lower.endsWith("main.py")
            || lower.contains("/controller/")
            || lower.contains("/service/");
    }

    private String inferFileType(String path) {
        String lower = path.toLowerCase();
        if (lower.contains(".env")) return "env";
        if (lower.endsWith(".md") || lower.endsWith(".mdx")) return "docs";
        if (lower.endsWith(".json") || lower.endsWith(".yml") || lower.endsWith(".yaml") || lower.endsWith(".toml") || lower.endsWith(".xml")) return "config";
        if (lower.endsWith(".bat") || lower.endsWith(".ps1") || lower.endsWith(".sh")) return "script";
        if (isTestPath(lower)) return "test";
        if (isSourceCodePath(lower)) return "source";
        if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".svg")) return "asset";
        return "unknown";
    }

    private String inferFileRole(String path, String fileType) {
        String lower = path.toLowerCase();
        if (lower.endsWith("page.tsx")) return "页面入口";
        if (lower.endsWith("layout.tsx")) return "应用布局";
        if (lower.contains("/controller/")) return "后端接口层";
        if (lower.contains("/service/")) return "业务服务层";
        if (lower.contains("/repository/")) return "数据访问层";
        if (lower.endsWith("package.json") || lower.endsWith("pom.xml")) return "依赖与构建配置";
        if (fileType.equals("config")) return "工程配置";
        if (fileType.equals("docs")) return "项目说明文档";
        if (fileType.equals("test")) return "测试或验收证据";
        if (fileType.equals("script")) return "本地脚本";
        return "项目文件";
    }

    private String inferFileImportance(String path, String fileType) {
        String lower = path.toLowerCase();
        if (lower.endsWith("package.json") || lower.endsWith("pom.xml") || lower.endsWith("docker-compose.yml") || lower.endsWith("layout.tsx") || lower.endsWith("page.tsx")) {
            return "critical";
        }
        if (fileType.equals("config") || fileType.equals("script") || lower.contains("/controller/") || lower.contains("/service/")) {
            return "important";
        }
        return "normal";
    }

    private String inferFileRiskLevel(String path, String fileType) {
        String lower = path.toLowerCase();
        if ((fileType.equals("env") && !lower.endsWith(".env.example")) || lower.endsWith(".pem") || lower.endsWith(".key")) {
            return "high";
        }
        if (lower.contains("security") || lower.contains("auth") || lower.contains("jwt") || lower.contains("docker-compose")) {
            return "medium";
        }
        return "none";
    }

    private String inferFileRiskNotes(String path, String fileType, String riskLevel) {
        String lower = path.toLowerCase();
        if (riskLevel.equals("none")) {
            return "未识别明显风险。";
        }
        if (fileType.equals("env") && !lower.endsWith(".env.example")) {
            return "疑似环境变量或敏感配置文件，默认不发送给模型。";
        }
        if (lower.contains("auth") || lower.contains("jwt") || lower.contains("security")) {
            return "认证或安全相关文件，后续改动需要重点审查。";
        }
        if (lower.contains("docker-compose")) {
            return "部署配置会影响本地和生产运行方式，建议确认端口、凭据和服务依赖。";
        }
        return "需要模型或用户进一步确认风险。";
    }

    private boolean isProjectNoisePath(String path) {
        String lower = path.toLowerCase();
        return lower.startsWith(".codex-run/")
            || lower.contains("/.codex-run/")
            || lower.contains("/old-git-")
            || lower.startsWith(".git/")
            || lower.contains("/.git/")
            || lower.startsWith("node_modules/")
            || lower.contains("/node_modules/")
            || lower.startsWith(".venv/")
            || lower.contains("/.venv/")
            || lower.startsWith("venv/")
            || lower.contains("/venv/")
            || lower.contains("/__pycache__/")
            || lower.contains("/.pytest_cache/")
            || lower.contains("/.mypy_cache/")
            || lower.contains("/.ruff_cache/")
            || lower.contains("/coverage/")
            || lower.contains("/dist/")
            || lower.contains("/build/")
            || lower.contains("/target/")
            || lower.contains("/.next/")
            || lower.contains("/.turbo/");
    }

    private boolean looksLikeFrontendPath(String lowerPath) {
        return lowerPath.startsWith("frontend/")
            || lowerPath.startsWith("web/")
            || lowerPath.startsWith("client/")
            || lowerPath.startsWith("ui/")
            || lowerPath.startsWith("apps/web/")
            || lowerPath.startsWith("apps/frontend/")
            || lowerPath.startsWith("packages/web/")
            || lowerPath.startsWith("packages/ui/")
            || lowerPath.contains("/src/app/")
            || lowerPath.contains("/src/components/")
            || lowerPath.endsWith("page.tsx")
            || lowerPath.endsWith("app.tsx")
            || lowerPath.endsWith("vite.config.ts")
            || lowerPath.endsWith("vite.config.js")
            || lowerPath.endsWith("next.config.ts")
            || lowerPath.endsWith("next.config.js");
    }

    private boolean looksLikeBackendPath(String lowerPath) {
        return lowerPath.startsWith("backend/")
            || lowerPath.startsWith("server/")
            || lowerPath.startsWith("api/")
            || lowerPath.startsWith("services/api/")
            || lowerPath.startsWith("services/server/")
            || lowerPath.startsWith("services/worker/")
            || lowerPath.contains("/src/main/")
            || lowerPath.contains("/controller/")
            || lowerPath.contains("/service/")
            || lowerPath.endsWith("pom.xml")
            || lowerPath.endsWith("build.gradle")
            || lowerPath.endsWith("build.gradle.kts")
            || lowerPath.endsWith("pyproject.toml")
            || lowerPath.endsWith("requirements.txt")
            || lowerPath.endsWith("go.mod")
            || lowerPath.endsWith("main.py");
    }

    private boolean isTestPath(String lowerPath) {
        return lowerPath.startsWith("test/")
            || lowerPath.startsWith("tests/")
            || lowerPath.startsWith("spec/")
            || lowerPath.contains("/test/")
            || lowerPath.contains("/tests/")
            || lowerPath.contains("/spec/")
            || lowerPath.contains("/__tests__/")
            || lowerPath.contains(".test.")
            || lowerPath.contains(".spec.")
            || lowerPath.endsWith("_test.py")
            || lowerPath.endsWith("test_main.py");
    }

    private boolean isSourceCodePath(String lowerPath) {
        return lowerPath.contains("/src/")
            || lowerPath.startsWith("src/")
            || lowerPath.contains("/app/")
            || lowerPath.endsWith(".java")
            || lowerPath.endsWith(".kt")
            || lowerPath.endsWith(".ts")
            || lowerPath.endsWith(".tsx")
            || lowerPath.endsWith(".js")
            || lowerPath.endsWith(".jsx")
            || lowerPath.endsWith(".vue")
            || lowerPath.endsWith(".py")
            || lowerPath.endsWith(".go")
            || lowerPath.endsWith(".rs")
            || lowerPath.endsWith(".php")
            || lowerPath.endsWith(".cs")
            || lowerPath.endsWith(".rb");
    }

    private boolean isSensitivePath(String path) {
        String lower = path.toLowerCase();
        return (lower.contains(".env") && !lower.endsWith(".env.example"))
            || lower.endsWith(".pem")
            || lower.endsWith(".key")
            || lower.contains("secret");
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

    private ProjectMemory updateMemory(
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

    private ProjectMemory applyAcceptedChangeToMemory(ProjectSpace project, ProjectChange change) {
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

        ProjectMemory memory = updateMemory(project, null, null, List.of(), completed, risks, decisions, learnings, assets, List.of());
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

    private String cleanMemoryText(String value, String fallback) {
        if (value == null || value.trim().isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private void recordFactSources(
        UUID projectId,
        ProjectFactSourceType sourceType,
        UUID sourceId,
        boolean confirmedByUser,
        Map<String, String> values
    ) {
        values.forEach((fieldKey, value) -> recordFactSource(projectId, fieldKey, value, sourceType, sourceId, confirmedByUser));
    }

    private void recordFactSource(
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

    private ProjectChange findOwnedChange(UUID userId, UUID changeId) {
        ProjectChange change = changeRepository.findById(changeId)
            .orElseThrow(() -> new AppException("PROJECT_CHANGE_NOT_FOUND", "Project change was not found", HttpStatus.NOT_FOUND));
        findOwnedProject(userId, change.getProjectId());
        return change;
    }

    private ProjectAnalysisRecord findOwnedAnalysisRecord(UUID userId, UUID recordId) {
        ProjectAnalysisRecord record = analysisRecordRepository.findById(recordId)
            .orElseThrow(() -> new AppException("PROJECT_ANALYSIS_RECORD_NOT_FOUND", "Project analysis record was not found", HttpStatus.NOT_FOUND));
        findOwnedProject(userId, record.getProjectId());
        return record;
    }

    private ProjectSpace findOwnedProjectById(UUID projectId) {
        return projectRepository.findById(projectId)
            .orElseThrow(() -> new AppException("PROJECT_NOT_FOUND", "Project was not found", HttpStatus.NOT_FOUND));
    }

    private Path resolveLocalProjectRoot(String projectPath) {
        if (projectPath == null || projectPath.isBlank()) {
            throw new AppException("PROJECT_PATH_REQUIRED", "Project folder path is required", HttpStatus.BAD_REQUEST);
        }
        Path path = Path.of(projectPath).toAbsolutePath().normalize();
        if (path.getParent() == null || path.equals(path.getRoot())) {
            throw new AppException("PROJECT_PATH_TOO_BROAD", "Project folder path is too broad", HttpStatus.BAD_REQUEST);
        }
        if (!Files.isDirectory(path)) {
            throw new AppException("PROJECT_PATH_NOT_FOUND", "Project folder path was not found", HttpStatus.BAD_REQUEST);
        }
        return path;
    }

    private ProjectMaterial findOwnedMaterial(UUID userId, UUID materialId) {
        ProjectMaterial material = materialRepository.findById(materialId)
            .orElseThrow(() -> new AppException("PROJECT_MATERIAL_NOT_FOUND", "Project material was not found", HttpStatus.NOT_FOUND));
        findOwnedProject(userId, material.getProjectId());
        return material;
    }

    private AiSuggestion findOwnedSuggestion(UUID userId, UUID suggestionId) {
        AiSuggestion suggestion = suggestionRepository.findById(suggestionId)
            .orElseThrow(() -> new AppException("AI_SUGGESTION_NOT_FOUND", "AI suggestion was not found", HttpStatus.NOT_FOUND));
        findOwnedProject(userId, suggestion.getProjectId());
        return suggestion;
    }

    private String readUploadedFile(String fileName, MultipartFile file) {
        try {
            byte[] bytes = file.getBytes();
            if (fileName.endsWith(".docx")) {
                return extractDocxText(bytes);
            }
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new AppException("MATERIAL_READ_FAILED", "Project material could not be read", HttpStatus.BAD_REQUEST);
        }
    }

    private String summarizeZip(MultipartFile file) {
        return scanZip(file).content();
    }

    private ZipProjectScan scanZip(MultipartFile file) {
        try {
            return scanZip(file, StandardCharsets.UTF_8);
        } catch (AppException utf8Exception) {
            if (!"ZIP_READ_FAILED".equals(utf8Exception.getCode())) {
                throw utf8Exception;
            }
            return scanZip(file, Charset.forName("GBK"));
        }
    }

    private ZipProjectScan scanZip(MultipartFile file, Charset charset) {
        try (ZipInputStream zipInputStream = new ZipInputStream(file.getInputStream(), charset)) {
            StringBuilder tree = new StringBuilder("# Project zip summary\n\n## Directory tree\n");
            StringBuilder keyFiles = new StringBuilder("\n## Key files\n");
            StringBuilder fileSnippets = new StringBuilder("\n## File snippets\n");
            List<String> moduleStructure = new ArrayList<>();
            Set<String> techStack = new LinkedHashSet<>();
            Map<String, String> keyFileContent = new LinkedHashMap<>();
            String rootName = "";
            boolean hasReadme = false;
            boolean hasTests = false;
            boolean hasStartScript = false;
            boolean hasDeployConfig = false;
            boolean hasSource = false;
            int count = 0;
            ZipEntry entry;

            while ((entry = zipInputStream.getNextEntry()) != null && count < MAX_ZIP_ENTRIES) {
                String name = entry.getName().replace("\\", "/");
                if (entry.isDirectory() || shouldSkipZipEntry(name)) {
                    continue;
                }
                if (rootName.isBlank()) {
                    rootName = firstPathSegment(name);
                }

                String relativeName = stripRoot(name, rootName);
                count++;
                tree.append("- ").append(relativeName).append("\n");
                moduleStructure.add(relativeName);

                String lowerRelativeName = relativeName.toLowerCase();
                hasReadme = hasReadme || lowerRelativeName.endsWith("readme.md");
                hasTests = hasTests || isTestPath(lowerRelativeName);
                hasStartScript = hasStartScript || lowerRelativeName.startsWith("start-") || lowerRelativeName.endsWith(".bat") || lowerRelativeName.contains("package.json");
                hasDeployConfig = hasDeployConfig || lowerRelativeName.endsWith("docker-compose.yml") || lowerRelativeName.contains("docker/");
                hasSource = hasSource || isSourceCodePath(lowerRelativeName);

                if (!isSensitivePath(relativeName) && (isKeyZipFile(relativeName) || isIndexableTextFile(relativeName))) {
                    String content = readSafeZipText(zipInputStream);
                    if (isKeyZipFile(relativeName)) {
                        keyFileContent.put(relativeName, content);
                        keyFiles.append("\n### ").append(relativeName).append("\n");
                        keyFiles.append(content).append("\n");
                        detectTechStack(relativeName, content, techStack);
                    } else if (fileSnippets.length() < MAX_INDEXED_SNIPPET_CHARS) {
                        fileSnippets.append("\n### ").append(relativeName).append("\n");
                        fileSnippets.append(content).append("\n");
                    }
                }
            }

            ProjectProfileResponse profile = buildProjectProfile(
                rootName,
                keyFileContent,
                new ArrayList<>(techStack),
                moduleStructure,
                hasReadme,
                hasTests,
                hasStartScript,
                hasDeployConfig,
                hasSource
            );
            return new ZipProjectScan(tree.append(keyFiles).append(fileSnippets).toString(), profile);
        } catch (IOException exception) {
            throw new AppException("ZIP_READ_FAILED", "项目 zip 无法读取；如果是中文路径或旧压缩工具生成的 zip，ProjectFlow 会自动尝试 GBK 编码。若仍失败，请重新压缩为标准 zip。", HttpStatus.BAD_REQUEST);
        }
    }

    private ProjectProfileResponse buildProjectProfile(
        String rootName,
        Map<String, String> keyFileContent,
        List<String> techStack,
        List<String> moduleStructure,
        boolean hasReadme,
        boolean hasTests,
        boolean hasStartScript,
        boolean hasDeployConfig,
        boolean hasSource
    ) {
        String inferredName = inferProjectName(rootName, keyFileContent);
        boolean looksEmptyShell = !hasSource || !hasReadme || moduleStructure.size() < 4;
        String currentStage = hasTests && hasDeployConfig ? "工程化完善中" : "项目导入梳理";
        String mostImportantGap = inferMostImportantGap(hasReadme, hasTests, hasStartScript, hasDeployConfig, looksEmptyShell);
        String summary = "%s 已完成完整项目 zip 导入，识别到 %d 个结构条目。".formatted(inferredName, moduleStructure.size());
        return new ProjectProfileResponse(
            inferredName,
            summary,
            techStack,
            moduleStructure.stream().limit(80).toList(),
            currentStage,
            hasReadme,
            hasTests,
            hasStartScript,
            hasDeployConfig,
            looksEmptyShell,
            mostImportantGap
        );
    }

    private String inferProjectName(String rootName, Map<String, String> keyFileContent) {
        if (rootName != null && !rootName.isBlank()) {
            return rootName;
        }
        for (Map.Entry<String, String> entry : keyFileContent.entrySet()) {
            if (entry.getKey().endsWith("package.json")) {
                String name = extractJsonString(entry.getValue(), "name");
                if (!name.isBlank()) {
                    return name;
                }
            }
        }
        for (Map.Entry<String, String> entry : keyFileContent.entrySet()) {
            if (entry.getKey().endsWith("pom.xml")) {
                String artifactId = extractXmlTag(entry.getValue(), "artifactId");
                if (!artifactId.isBlank()) {
                    return artifactId;
                }
            }
        }
        for (Map.Entry<String, String> entry : keyFileContent.entrySet()) {
            if (entry.getKey().toLowerCase().endsWith("readme.md")) {
                String heading = entry.getValue().lines()
                    .filter(line -> line.startsWith("# "))
                    .map(line -> line.substring(2).trim())
                    .findFirst()
                    .orElse("");
                if (!heading.isBlank()) {
                    return heading;
                }
            }
        }
        return "Imported Project " + LocalDate.now();
    }

    private String inferMostImportantGap(boolean hasReadme, boolean hasTests, boolean hasStartScript, boolean hasDeployConfig, boolean looksEmptyShell) {
        if (looksEmptyShell) {
            return "补齐项目核心源码和结构证据";
        }
        if (!hasReadme) {
            return "补齐 README 项目说明";
        }
        if (!hasTests) {
            return "补齐关键测试路径";
        }
        if (!hasStartScript) {
            return "补齐本地启动脚本";
        }
        if (!hasDeployConfig) {
            return "补齐部署配置说明";
        }
        return "确认项目画像并规划下一轮开发";
    }

    private void detectTechStack(String relativeName, String content, Set<String> techStack) {
        String lowerName = relativeName.toLowerCase();
        String lowerContent = content.toLowerCase();
        if (lowerName.endsWith("package.json")) {
            techStack.add("Node.js");
            if (lowerContent.contains("\"next\"")) {
                techStack.add("Next.js");
            }
            if (lowerContent.contains("\"react\"")) {
                techStack.add("React");
            }
            if (lowerContent.contains("\"vue\"")) {
                techStack.add("Vue");
            }
            if (lowerContent.contains("\"vite\"")) {
                techStack.add("Vite");
            }
            if (lowerContent.contains("\"express\"")) {
                techStack.add("Express");
            }
            if (lowerContent.contains("\"nestjs\"") || lowerContent.contains("@nestjs/")) {
                techStack.add("NestJS");
            }
        }
        if (lowerName.endsWith("pom.xml")) {
            if (lowerContent.contains("spring-boot")) {
                techStack.add("Spring Boot");
            }
            techStack.add("Java");
        }
        if (lowerName.endsWith("build.gradle") || lowerName.endsWith("build.gradle.kts")) {
            if (lowerContent.contains("springframework.boot") || lowerContent.contains("spring-boot")) {
                techStack.add("Spring Boot");
            }
            techStack.add("Java");
            techStack.add("Gradle");
        }
        if (lowerName.endsWith("pyproject.toml") || lowerName.endsWith("requirements.txt")) {
            techStack.add("Python");
            if (lowerContent.contains("fastapi")) {
                techStack.add("FastAPI");
            }
            if (lowerContent.contains("django")) {
                techStack.add("Django");
            }
            if (lowerContent.contains("flask")) {
                techStack.add("Flask");
            }
        }
        if (lowerName.endsWith("go.mod")) {
            techStack.add("Go");
            if (lowerContent.contains("gin-gonic") || lowerContent.contains("gin ")) {
                techStack.add("Gin");
            }
        }
        if (lowerName.endsWith("cargo.toml")) {
            techStack.add("Rust");
        }
        if (lowerName.endsWith("composer.json")) {
            techStack.add("PHP");
            if (lowerContent.contains("laravel")) {
                techStack.add("Laravel");
            }
        }
        if (lowerName.endsWith(".csproj")) {
            techStack.add(".NET");
        }
        if (lowerName.endsWith("docker-compose.yml")) {
            techStack.add("Docker Compose");
            if (lowerContent.contains("postgres")) {
                techStack.add("PostgreSQL");
            }
            if (lowerContent.contains("redis")) {
                techStack.add("Redis");
            }
        }
    }

    private String extractJsonString(String content, String key) {
        String marker = "\"" + key + "\"";
        int keyIndex = content.indexOf(marker);
        if (keyIndex < 0) {
            return "";
        }
        int colonIndex = content.indexOf(':', keyIndex);
        int firstQuote = content.indexOf('"', colonIndex + 1);
        int secondQuote = content.indexOf('"', firstQuote + 1);
        if (colonIndex < 0 || firstQuote < 0 || secondQuote < 0) {
            return "";
        }
        return content.substring(firstQuote + 1, secondQuote).trim();
    }

    private String extractXmlTag(String content, String tagName) {
        String open = "<" + tagName + ">";
        String close = "</" + tagName + ">";
        int openIndex = content.indexOf(open);
        int closeIndex = content.indexOf(close, openIndex + open.length());
        if (openIndex < 0 || closeIndex < 0) {
            return "";
        }
        return content.substring(openIndex + open.length(), closeIndex).trim();
    }

    private String extractDocxText(byte[] bytes) throws IOException {
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    String xml = new String(zipInputStream.readAllBytes(), StandardCharsets.UTF_8);
                    return xml
                        .replaceAll("</w:p>", "\n")
                        .replaceAll("<[^>]+>", "")
                        .replace("&lt;", "<")
                        .replace("&gt;", ">")
                        .replace("&amp;", "&")
                        .trim();
                }
            }
        }
        throw new AppException("DOCX_READ_FAILED", "DOCX content could not be extracted", HttpStatus.BAD_REQUEST);
    }

    private boolean shouldSkipZipEntry(String name) {
        String lower = name.toLowerCase();
        return isProjectNoisePath(lower)
            || lower.contains("/.git/")
            || lower.contains("/node_modules/")
            || lower.contains("/target/")
            || lower.contains("/dist/")
            || lower.contains("/.next/")
            || lower.contains("/build/")
            || lower.contains("/logs/")
            || lower.contains("/coverage/")
            || lower.contains("/.turbo/")
            || lower.contains("/.venv/")
            || lower.contains("/venv/")
            || lower.contains("/__pycache__/")
            || lower.contains("/.pytest_cache/")
            || lower.contains("/.mypy_cache/")
            || lower.contains("/.ruff_cache/")
            || lower.contains("/vendor/")
            || lower.endsWith(".log")
            || lower.contains(".next-dev.")
            || lower.endsWith(".env")
            || lower.endsWith(".png")
            || lower.endsWith(".jpg")
            || lower.endsWith(".jpeg")
            || lower.endsWith(".gif")
            || lower.endsWith(".mp4")
            || lower.endsWith(".zip")
            || lower.endsWith(".jar");
    }

    private boolean isIndexableTextFile(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".java")
            || lower.endsWith(".kt")
            || lower.endsWith(".js")
            || lower.endsWith(".jsx")
            || lower.endsWith(".ts")
            || lower.endsWith(".tsx")
            || lower.endsWith(".vue")
            || lower.endsWith(".py")
            || lower.endsWith(".go")
            || lower.endsWith(".rs")
            || lower.endsWith(".php")
            || lower.endsWith(".cs")
            || lower.endsWith(".rb")
            || lower.endsWith(".sql")
            || lower.endsWith(".graphql")
            || lower.endsWith(".properties")
            || lower.endsWith(".yaml")
            || lower.endsWith(".yml")
            || lower.endsWith(".xml")
            || lower.endsWith(".md")
            || lower.endsWith(".txt")
            || lower.endsWith(".css")
            || lower.endsWith(".scss")
            || lower.endsWith(".html");
    }

    private String readSafeZipText(ZipInputStream zipInputStream) throws IOException {
        int byteLimit = MAX_FILE_SNIPPET_CHARS * 4;
        byte[] bytes = zipInputStream.readNBytes(byteLimit);
        String content = new String(bytes, StandardCharsets.UTF_8);
        return sanitizeIndexedContent(truncate(content, MAX_FILE_SNIPPET_CHARS));
    }

    private String sanitizeIndexedContent(String content) {
        return content
            .replaceAll("(?im)^([\\w.-]*(?:api[_-]?key|secret|password|token)[\\w.-]*\\s*[:=]\\s*).+$", "$1[REDACTED]")
            .replaceAll("(?i)(authorization\\s*[:=]\\s*bearer\\s+)[^\\s\"']+", "$1[REDACTED]")
            .replaceAll("(?s)-----BEGIN [^-]+ PRIVATE KEY-----.*?-----END [^-]+ PRIVATE KEY-----", "[REDACTED PRIVATE KEY]");
    }

    private boolean isKeyZipFile(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith("readme.md")
            || lower.endsWith("package.json")
            || lower.endsWith("pom.xml")
            || lower.endsWith("build.gradle")
            || lower.endsWith("build.gradle.kts")
            || lower.endsWith("settings.gradle")
            || lower.endsWith("gradle.properties")
            || lower.endsWith("pyproject.toml")
            || lower.endsWith("requirements.txt")
            || lower.endsWith("poetry.lock")
            || lower.endsWith("go.mod")
            || lower.endsWith("cargo.toml")
            || lower.endsWith("composer.json")
            || lower.endsWith(".csproj")
            || lower.endsWith("docker-compose.yml")
            || lower.endsWith("tsconfig.json")
            || lower.endsWith("jsconfig.json")
            || lower.endsWith("next.config.ts")
            || lower.endsWith("next.config.js")
            || lower.endsWith("vite.config.ts")
            || lower.endsWith("vite.config.js")
            || lower.endsWith(".env.example")
            || lower.startsWith("docs/");
    }

    private String extractIndexedFileContent(String materialContent, String path) {
        String marker = "### " + path + "\n";
        int start = materialContent.indexOf(marker);
        if (start < 0) {
            return "";
        }
        int contentStart = start + marker.length();
        int nextSection = materialContent.indexOf("\n### ", contentStart);
        String content = nextSection < 0
            ? materialContent.substring(contentStart)
            : materialContent.substring(contentStart, nextSection);
        return truncate(content.trim(), MAX_FILE_SNIPPET_CHARS);
    }

    private String firstPathSegment(String path) {
        int slashIndex = path.indexOf('/');
        return slashIndex < 0 ? "" : path.substring(0, slashIndex);
    }

    private String stripRoot(String path, String rootName) {
        if (rootName == null || rootName.isBlank()) {
            return path;
        }
        String prefix = rootName + "/";
        return path.startsWith(prefix) ? path.substring(prefix.length()) : path;
    }

    private MaterialSourceType detectFileSourceType(String fileName) {
        if (fileName.endsWith(".docx")) {
            return MaterialSourceType.DOCX_FILE;
        }
        if (fileName.endsWith(".md")) {
            return MaterialSourceType.README_MARKDOWN;
        }
        if (fileName.endsWith(".json") || fileName.endsWith(".log")) {
            return MaterialSourceType.JSON_LOG;
        }
        return MaterialSourceType.TEXT_FILE;
    }

    private String projectAnalysisDetails(ProjectAnalysisResponse response) {
        return String.join("\n\n",
            "架构判断：\n" + response.architecture(),
            "模块：\n" + joinOrNone(response.modules()),
            "风险：\n" + joinOrNone(response.risks()),
            "重要文件：\n" + joinOrNone(response.importantFiles()),
            "分析证据：\n" + joinOrNone(response.evidence()),
            "分析局限：\n" + joinOrNone(response.limitations()),
            "说明：\n" + response.message()
        );
    }

    private String fileAnalysisDetails(ProjectFileAnalysisResponse response) {
        return String.join("\n\n",
            "路径：\n" + response.path(),
            "文件类型：\n" + localizedAnalysisCode(response.fileType()),
            "职责：\n" + response.role(),
            "重要性：\n" + localizedAnalysisCode(response.importance()),
            "风险等级：\n" + localizedAnalysisCode(response.riskLevel()),
            "风险说明：\n" + response.riskNotes(),
            "分析证据：\n" + joinOrNone(response.evidence()),
            "关联文件：\n" + joinOrNone(response.relatedFiles()),
            "分析局限：\n" + response.limitations(),
            "说明：\n" + response.message()
        );
    }

    private String joinOrNone(List<String> values) {
        return values.isEmpty() ? "暂无" : String.join("\n", values.stream().map(value -> "- " + value).toList());
    }

    private String localizedAnalysisCode(String value) {
        return switch (value.toLowerCase()) {
            case "source" -> "源码";
            case "test" -> "测试";
            case "config" -> "配置";
            case "docs" -> "文档";
            case "script" -> "脚本";
            case "asset" -> "资源";
            case "build" -> "构建产物";
            case "env" -> "环境配置";
            case "critical" -> "核心";
            case "important" -> "重要";
            case "normal" -> "一般";
            case "high" -> "高";
            case "medium" -> "中";
            case "low" -> "低";
            case "none" -> "未发现";
            default -> value;
        };
    }

    private ProjectAnalysisRecordResponse toAnalysisRecordResponse(ProjectAnalysisRecord record) {
        return new ProjectAnalysisRecordResponse(
            record.getId(),
            record.getProjectId(),
            record.getRecordType(),
            record.getFilePath(),
            record.getSummary(),
            record.getDetails(),
            record.getAnalysisSource(),
            record.isModelUsed(),
            record.getProviderName(),
            record.getConfidence(),
            record.getCreatedAt()
        );
    }

    private ProjectMaterialResponse toMaterialResponse(ProjectMaterial material) {
        return new ProjectMaterialResponse(
            material.getId(),
            material.getProjectId(),
            material.getSourceType(),
            material.getFileName(),
            material.getContent(),
            material.getNormalizedSummary(),
            material.getCreatedAt(),
            material.getUpdatedAt()
        );
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

    private ProjectChangeResponse toChangeResponse(ProjectChange change) {
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

    private ProjectMemoryResponse toMemoryResponse(ProjectMemory memory) {
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

    private String normalizeContent(String content) {
        String trimmed = truncate(content.trim(), MAX_MATERIAL_CHARS);
        return firstSentence(trimmed).isBlank() ? "已保存项目材料，等待 AI 解析。" : firstSentence(trimmed);
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
        String joined = String.join("\n", lines.values().stream().map(item -> "- " + item).toList());
        if (existing == null || existing.isBlank() || existing.startsWith("暂无")) {
            return joined;
        }
        return joined;
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

    private String cleanFileName(String fileName) {
        return fileName == null ? "uploaded-material" : fileName.replace("\\", "/").substring(fileName.replace("\\", "/").lastIndexOf('/') + 1).toLowerCase();
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private record ZipProjectScan(
        String content,
        ProjectProfileResponse profile
    ) {
    }
}
