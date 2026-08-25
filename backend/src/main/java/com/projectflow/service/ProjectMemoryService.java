package com.projectflow.service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.projectflow.dto.V2ProjectDtos.CapabilityCandidate;
import com.projectflow.dto.V2ProjectDtos.CapabilityInterpretRequest;
import com.projectflow.dto.V2ProjectDtos.CapabilityInterpretResponse;
import com.projectflow.dto.V2ProjectDtos.ModelCallDiagnosticsResponse;
import com.projectflow.dto.V2ProjectDtos.ProjectFactSourceResponse;
import com.projectflow.dto.V2ProjectDtos.ProjectLocalPathRequest;
import com.projectflow.dto.V2ProjectDtos.ProjectMemoryResponse;
import com.projectflow.dto.V2ProjectDtos.ProjectMemoryUpdateRequest;
import com.projectflow.entity.AiProvider;
import com.projectflow.entity.AiProviderType;
import com.projectflow.entity.ProjectChange;
import com.projectflow.entity.ProjectFactSource;
import com.projectflow.entity.ProjectFactSourceType;
import com.projectflow.entity.ProjectMemory;
import com.projectflow.entity.ProjectSpace;
import com.projectflow.repository.AiProviderRepository;
import com.projectflow.repository.ProjectFactSourceRepository;
import com.projectflow.repository.ProjectMemoryRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.support.AppException;

@Service
public class ProjectMemoryService {
    private static final Logger log = LoggerFactory.getLogger(ProjectMemoryService.class);
    private final ProjectRepository projectRepository;
    private final ProjectMemoryRepository memoryRepository;
    private final ProjectFactSourceRepository factSourceRepository;
    private final LocalProjectPathGuard localProjectPathGuard;
    private final AiProviderRepository aiProviderRepository;
    private final ModelGatewayService modelGatewayService;

    public ProjectMemoryService(
        ProjectRepository projectRepository,
        ProjectMemoryRepository memoryRepository,
        ProjectFactSourceRepository factSourceRepository,
        LocalProjectPathGuard localProjectPathGuard,
        AiProviderRepository aiProviderRepository,
        ModelGatewayService modelGatewayService
    ) {
        this.projectRepository = projectRepository;
        this.memoryRepository = memoryRepository;
        this.factSourceRepository = factSourceRepository;
        this.localProjectPathGuard = localProjectPathGuard;
        this.aiProviderRepository = aiProviderRepository;
        this.modelGatewayService = modelGatewayService;
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

    public CapabilityInterpretResponse interpretCapability(UUID userId, UUID projectId, CapabilityInterpretRequest request) {
        ProjectSpace project = findOwnedProject(userId, projectId);
        ProjectMemory memory = memoryRepository.findByProjectId(project.getId()).orElseGet(() -> initialMemory(project));
        AiProvider provider = configuredProvider(userId);
        if (provider == null) {
            return new CapabilityInterpretResponse(true, "LOCAL_RULE", "未配置可用模型，已用本地规则生成候选解读。", localCandidate(request.capabilityFact(), memory), null);
        }
        try {
            String prompt = capabilityInterpretPrompt(project.getName(), memory, request.capabilityFact());
            ModelGatewayService.StructuredModelResponse response = modelGatewayService.callStructured(
                provider, prompt, ModelTaskType.CAPABILITY_INTERPRETATION
            );
            JsonNode json = response.parsed().root();
            return new CapabilityInterpretResponse(false, "MODEL", "模型已生成候选解读，采纳后才进入正式项目资产。", modelCandidate(json), diagnostics(response.diagnostics()));
        } catch (Exception exception) {
            log.warn("Capability interpretation fell back to local rules: projectId={}, provider={}, error={}",
                projectId, provider.getName(), exception.getMessage());
            return new CapabilityInterpretResponse(true, "LOCAL_RULE", "模型不可用，已用本地规则生成候选解读。" + modelGatewayService.failureMessage(exception), localCandidate(request.capabilityFact(), memory), diagnostics(exception));
        }
    }

    private ModelCallDiagnosticsResponse diagnostics(Exception exception) {
        if (exception instanceof ModelGatewayService.ModelResponseFormatException failure) return diagnostics(failure.diagnostics());
        return null;
    }

    private ModelCallDiagnosticsResponse diagnostics(ModelGatewayService.ModelCallDiagnostics value) {
        if (value == null) return null;
        return new ModelCallDiagnosticsResponse(
            value.providerName(), value.modelName(), value.finishReason(), value.promptTokens(), value.completionTokens(),
            value.totalTokens(), value.usageSource(), value.providerMaxTokens(), value.taskPolicyMaxTokens(), value.effectiveMaxTokens(),
            value.providerTemperature(), value.effectiveTemperature(), value.timeoutSeconds(), value.latencyMs(), value.contentPresent(),
            value.reasoningPresent(), value.reasoningLength(), value.truncated(), value.compactRetryAttempted(),
            value.compactRetrySucceeded(), value.requestCount(), value.jsonRepaired(), value.partialResult(), value.recoveredItems(),
            value.entryPoint(), value.taskType(), value.capabilityProfile(), value.inputSize(), value.promptSize(),
            value.recommendedTemperature(), value.temperatureSent(), value.temperatureDecision(), value.maxTokenDecision(),
            value.retryType(), value.reasoningBudgetExhausted(), value.schemaMatched(), value.failureStage(), value.failureCode()
        );
    }

    private AiProvider configuredProvider(UUID userId) {
        return aiProviderRepository.findByUserIdOrderByDefaultEnabledDescUpdatedAtDesc(userId)
            .stream()
            .filter(provider -> provider.getType() != AiProviderType.MOCK)
            .filter(AiProvider::isDefaultEnabled)
            .filter(AiProvider::hasConfiguredCredential)
            .findFirst()
            .orElse(null);
    }

    private String capabilityInterpretPrompt(String projectName, ProjectMemory memory, String capabilityFact) {
        return """
            你是 ProjectFlow 的能力解读助手。只依据下方项目资产和这条能力事实生成候选解读，所有自然语言必须使用简体中文。
            返回严格 JSON，字段为：summary, problem, value, readme, resume, interview。

            质量要求：
            1. summary 用一句话说明这个能力是什么。
            2. problem 说明这个能力解决了什么工程问题。
            3. value 说明为什么这个能力对项目长期维护或复用有价值。
            4. readme 是可直接用于 README 项目亮点的一句表达。
            5. resume 是可直接用于简历项目经历的一句表达。
            6. interview 是面试时可以展开讲解的一个要点。
            7. 技术名、文件路径和代码标识符保留原文。

            项目名称：%s
            项目定位：%s
            技术决策：%s
            能力事实：%s
            """.formatted(
                projectName,
                truncate(memory.getPositioning(), 300),
                truncate(memory.getTechnicalDecisions(), 300),
                truncate(capabilityFact, 500)
            );
    }

    private CapabilityCandidate modelCandidate(JsonNode json) {
        return new CapabilityCandidate(
            text(json, "summary"),
            text(json, "problem"),
            text(json, "value"),
            text(json, "readme"),
            text(json, "resume"),
            text(json, "interview")
        );
    }

    private CapabilityCandidate localCandidate(String capabilityFact, ProjectMemory memory) {
        String name = capabilityFact == null || capabilityFact.isBlank() ? "项目能力" : capabilityFact.replaceAll("^[-•\\d.\\s]+", "").split("[，。；:：]")[0];
        return new CapabilityCandidate(
            "已沉淀“" + name + "”相关能力。",
            "帮助用户把这条开发成果整理成可解释、可复用的项目能力。",
            "它说明项目已经能把真实开发活动沉淀成后续可复用的工程资产。",
            "沉淀了“" + name + "”能力，可结合项目资产和开发证据用于 README 项目亮点。",
            "在项目中落地“" + name + "”，负责把开发过程整理成可追溯、可复用的工程资产。",
            "可围绕“" + name + "”展开面试讲解：遇到的工程问题、采取的做法、产出的可复用资产。"
        );
    }

    private String text(JsonNode json, String field) {
        String value = json.path(field).asText("");
        return value == null || value.isBlank() ? "" : value.trim();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "暂无";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "...";
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
            appendCapabilityLines(memory.getCompletedCapabilities(), newLogs),
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
        String capabilityLine = acceptedCapabilitySummary(change);

        switch (change.getChangeKind()) {
            case RISK -> risks.add(defaultText(change.getRiskNotes(), summaryLine));
            case DECISION -> decisions.add(defaultText(change.getDecisionNotes(), summaryLine));
            case LEARNING -> learnings.add(defaultText(change.getLearningNotes(), summaryLine));
            case ASSET -> assets.add(defaultText(change.getAssetCandidates(), summaryLine));
            default -> completed.add(capabilityLine);
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
            recordFactSource(project.getId(), "completedCapabilities", capabilityLine, ProjectFactSourceType.ACCEPTED_CHANGE, sourceId, true);
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
            defaultText(project.getDescription(), project.getName() + " 的长期项目资产"),
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

    private String appendCapabilityLines(String existing, List<String> additions) {
        return appendLines(existing, additions.stream()
            .map(this::removeRawPathEvidence)
            .filter(item -> !item.isBlank())
            .toList());
    }

    private String cleanMemoryLine(String value) {
        return value == null ? "" : value.trim().replaceFirst("^(?:[-*]\\s*)+", "").trim();
    }

    private String acceptedCapabilitySummary(ProjectChange change) {
        String summary = removeRawPathEvidence(defaultText(change.getSummary(), change.getDetails()));
        if (!summary.isBlank()) {
            return summary;
        }
        return cleanMemoryLine(change.getTitle());
    }

    private String removeRawPathEvidence(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String withoutFileTail = value
            .replaceAll("(?s)(代表文件包括|涉及文件|影响文件)[:：]?.*$", "")
            .trim();
        return String.join("\n", withoutFileTail.lines()
            .map(this::cleanMemoryLine)
            .filter(line -> !line.isBlank())
            .filter(line -> !isRawPathLine(line))
            .toList());
    }

    private boolean isRawPathLine(String value) {
        String normalized = cleanMemoryLine(value).replace('\\', '/');
        if (!normalized.contains("/")) {
            return false;
        }
        return normalized.matches("(?i)^(backend|frontend|src|app|apps|services|docs|test|tests|scripts|config|docker|\\.github|\\.vscode|\\.projectflow)/.*")
            || normalized.matches("(?i).*\\.(java|kt|js|jsx|ts|tsx|vue|py|go|rs|php|cs|rb|sql|graphql|properties|ya?ml|xml|md|txt|css|scss|html|json|toml|gradle|ps1|bat)$");
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
