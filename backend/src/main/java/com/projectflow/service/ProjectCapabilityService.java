package com.projectflow.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Locale;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.projectflow.dto.V33WorkflowDtos.CapabilityCardAction;
import com.projectflow.dto.V33WorkflowDtos.CapabilityCardResponse;
import com.projectflow.entity.AiProvider;
import com.projectflow.entity.AiProviderType;
import com.projectflow.entity.CapabilityCardStatus;
import com.projectflow.entity.DevelopmentSegment;
import com.projectflow.entity.DevelopmentSegmentStatus;
import com.projectflow.entity.ProjectAnalysisJob;
import com.projectflow.entity.ProjectCapabilityCard;
import com.projectflow.repository.AiProviderRepository;
import com.projectflow.repository.DevelopmentSegmentRepository;
import com.projectflow.repository.ProjectAnalysisJobRepository;
import com.projectflow.repository.ProjectCapabilityCardRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.support.AppException;

@Service
public class ProjectCapabilityService {
    private final ProjectRepository projectRepository;
    private final DevelopmentSegmentRepository segmentRepository;
    private final ProjectCapabilityCardRepository cardRepository;
    private final AiProviderRepository providerRepository;
    private final ModelGatewayService modelGatewayService;
    private final ModelOutputAdapter outputAdapter;
    private final ProjectAnalysisJobRepository jobRepository;
    private final TransactionTemplate transactionTemplate;
    private final TransactionTemplate stageTransactionTemplate;

    public ProjectCapabilityService(
        ProjectRepository projectRepository,
        DevelopmentSegmentRepository segmentRepository,
        ProjectCapabilityCardRepository cardRepository,
        AiProviderRepository providerRepository,
        ModelGatewayService modelGatewayService,
        ModelOutputAdapter outputAdapter,
        ProjectAnalysisJobRepository jobRepository,
        PlatformTransactionManager transactionManager
    ) {
        this.projectRepository = projectRepository;
        this.segmentRepository = segmentRepository;
        this.cardRepository = cardRepository;
        this.providerRepository = providerRepository;
        this.modelGatewayService = modelGatewayService;
        this.outputAdapter = outputAdapter;
        this.jobRepository = jobRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.stageTransactionTemplate = new TransactionTemplate(transactionManager);
        this.stageTransactionTemplate.setPropagationBehavior(
            org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW
        );
    }

    public List<CapabilityCardResponse> analyze(UUID userId, UUID projectId) {
        return analyzeWithOutcome(userId, projectId, null).cards();
    }

    public List<CapabilityCardResponse> analyze(UUID userId, UUID projectId, UUID jobId) {
        return analyzeWithOutcome(userId, projectId, jobId).cards();
    }

    /** 模型等待期间不持有数据库事务；旧候选只在新结果可保存时原子替换。 */
    public CapabilityAnalysisOutcome analyzeWithOutcome(UUID userId, UUID projectId, UUID jobId) {
        advanceStage(jobId, "LOAD_EVIDENCE", "正在读取已确认沉淀和开发推进段");
        CapabilityInput input = transactionTemplate.execute(status -> loadInput(userId, projectId));
        if (input == null || input.sources().isEmpty()) {
            throw new AppException("CAPABILITY_EVIDENCE_REQUIRED", "请先确认至少一条项目沉淀，再分析项目能力", HttpStatus.BAD_REQUEST);
        }
        if (input.provider() == null) {
            throw new AppException("MODEL_NOT_CONFIGURED", "当前未配置模型，无法进行完整人话能力分析。请先在设置页配置模型。", HttpStatus.BAD_REQUEST);
        }
        recordInputSummary(jobId, input.sources().size());

        advanceStage(jobId, "MODEL_REQUEST", "正在调用模型分析项目能力（可能需要几分钟，任务会继续运行）");
        ModelDraftResult modelResult;
        try {
            modelResult = modelDrafts(input.provider(), input.sources(), jobId);
        } catch (ModelGatewayService.ModelResponseFormatException exception) {
            throw new CapabilityAnalysisException("MODEL_RESPONSE_PARSE", "模型已经返回，但结果无法解析，请重新分析。", exception);
        } catch (Exception exception) {
            throw new CapabilityAnalysisException("MODEL_REQUEST", "模型请求没有成功，请检查模型配置、网络或服务状态。", exception);
        }
        if (modelResult.drafts().isEmpty()) {
            throw new CapabilityAnalysisException("ITEM_VALIDATION", "模型结果中没有可用的能力卡片，旧候选已保留。", null);
        }

        advanceStage(jobId, "DATABASE_PERSIST", "正在原子替换未确认候选并保存能力卡片");
        List<CapabilityCardResponse> responses;
        try {
            responses = transactionTemplate.execute(status -> persistCandidates(projectId, input.provider(), modelResult.drafts()));
        } catch (Exception exception) {
            throw new CapabilityAnalysisException("DATABASE_PERSIST", "模型结果已生成，保存能力卡片时出现异常，旧候选已保留。", exception);
        }
        List<CapabilityCardResponse> safeResponses = responses == null ? List.of() : responses;
        long needsEvidence = safeResponses.stream().filter(card -> "NEEDS_EVIDENCE".equals(card.status())).count();
        boolean warnings = modelResult.diagnostics().repaired()
            || modelResult.diagnostics().discardedItems() > 0
            || modelResult.diagnostics().invalidSourceIndexes() > 0
            || needsEvidence > 0
            || safeResponses.size() < 3;
        return new CapabilityAnalysisOutcome(safeResponses, warnings, (int) needsEvidence, modelResult.diagnostics());
    }

    private CapabilityInput loadInput(UUID userId, UUID projectId) {
        ownedProject(userId, projectId);
        List<CapabilitySource> sources = segmentRepository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
            .filter(segment -> segment.getStatus() == DevelopmentSegmentStatus.CONFIRMED)
            .limit(24)
            .map(segment -> new CapabilitySource(
                segment.getId(), segment.getTitle(), segment.getPlainSummary(), segment.getAffectedFiles(), segment.getEvidenceRefs()
            )).toList();
        return new CapabilityInput(configuredProvider(userId), sources);
    }

    private List<CapabilityCardResponse> persistCandidates(UUID projectId, AiProvider provider, List<CardDraft> drafts) {
        cardRepository.deleteByProjectIdAndStatus(projectId, CapabilityCardStatus.CANDIDATE);
        cardRepository.deleteByProjectIdAndStatus(projectId, CapabilityCardStatus.NEEDS_EVIDENCE);
        List<ProjectCapabilityCard> cards = new ArrayList<>();
        for (CardDraft draft : drafts.stream().limit(8).toList()) {
            ProjectCapabilityCard card = new ProjectCapabilityCard(projectId);
            card.update(
                draft.name(), draft.summary(), draft.problemSolved(), draft.featureEntry(), draft.sourceRefs(), draft.evidenceRefs(),
                draft.readme(), draft.resume(), draft.interview(), "MODEL", provider.getName(), draft.warning()
            );
            cards.add(card);
        }
        return cardRepository.saveAll(cards).stream().map(this::response).toList();
    }

    // V3.3.4: 阶段推进。用独立事务提交，避免 analyze 主事务未提交时前端读到旧 stage。
    private void advanceStage(UUID jobId, String stage, String message) {
        if (jobId == null) return;
        stageTransactionTemplate.executeWithoutResult(status ->
            jobRepository.findById(jobId).ifPresent(job -> {
                job.advanceStage(stage, message);
                jobRepository.save(job);
            })
        );
    }

    private void recordInputSummary(UUID jobId, int confirmedSegmentCount) {
        if (jobId == null) return;
        try {
            String summary = "{\"confirmedSegments\":" + confirmedSegmentCount + "}";
            stageTransactionTemplate.executeWithoutResult(status ->
                jobRepository.findById(jobId).ifPresent(job -> {
                    job.recordInputSummary(summary);
                    jobRepository.save(job);
                })
            );
        } catch (Exception ignored) {
            // 输入摘要不是关键路径。
        }
    }

    @Transactional(readOnly = true)
    public List<CapabilityCardResponse> list(UUID userId, UUID projectId) {
        ownedProject(userId, projectId);
        return cardRepository.findByProjectIdOrderByCreatedAtDesc(projectId).stream().map(this::response).toList();
    }

    @Transactional
    public CapabilityCardResponse patch(UUID userId, UUID cardId, CapabilityCardAction action) {
        ProjectCapabilityCard card = cardRepository.findById(cardId)
            .orElseThrow(() -> new AppException("CAPABILITY_CARD_NOT_FOUND", "能力卡片不存在", HttpStatus.NOT_FOUND));
        ownedProject(userId, card.getProjectId());
        if (action == CapabilityCardAction.CONFIRM) card.confirm(); else card.ignore();
        return response(cardRepository.save(card));
    }

    private List<CardDraft> localDrafts(List<CapabilitySource> sources) {
        return sources.stream().limit(8).map(segment -> new CardDraft(
            DisplayContentSanitizer.sanitizeCapabilityName(segment.title()),
            DisplayContentSanitizer.sanitizeCapabilitySummary(segment.summary()),
            DisplayContentSanitizer.sanitizeCapabilitySummary(segment.summary()),
            entry(segment.affectedFiles()),
            List.of("segment:" + segment.id()), segment.evidenceRefs(),
            DisplayContentSanitizer.sanitizeCapabilitySummary(segment.title() + "：" + segment.summary()),
            DisplayContentSanitizer.sanitizeCapabilitySummary("基于可追溯证据完成" + segment.title()),
            DisplayContentSanitizer.sanitizeCapabilitySummary("可说明如何通过" + entry(segment.affectedFiles()) + "完成该能力，并展示提交与文件证据。"),
            "使用本地事实补全，请人工复核。"
        )).toList();
    }

    private ModelDraftResult modelDrafts(AiProvider provider, List<CapabilitySource> sources, UUID jobId) throws Exception {
        Map<String, CapabilitySource> sourceMap = new java.util.LinkedHashMap<>();
        StringBuilder facts = new StringBuilder();
        for (int index = 0; index < sources.size(); index++) {
            CapabilitySource source = sources.get(index);
            String sourceIndex = "S" + (index + 1);
            sourceMap.put(sourceIndex, source);
            facts.append(sourceIndex).append(" | ").append(source.title()).append(" | ")
                .append(truncate(source.summary(), 200)).append(" | entry=").append(entry(source.affectedFiles())).append('\n');
        }
        ModelGatewayService.StructuredModelResponse response = modelGatewayService.callStructured(provider, """
            基于全部确认开发推进段整体分析 ProjectFlow 项目能力。返回 JSON：
            {"capabilities":[{"name":"","summary":"","problemSolved":"","featureEntry":"","sourceIndexes":["S1"],"readme":"","resume":"","interview":""}]}
            生成具体且不重复的卡片，最多 8 张。来源只填写给定的 S 编号，不要复制内部 ID、提交哈希或 evidenceRefs。
            能力名称必须贴合作品真实功能（如"扫描指纹复用稳定分析结果""待整理变更归并为开发推进段""GitHub 状态与本地 Git 多来源证据整合"），禁止泛化模板名（如"项目资产沉淀能力""技术理解能力"），禁止直接复读 commit message。
            所有用户可见字段（name、summary、problemSolved、featureEntry、readme、resume、interview）必须使用简体中文人话；技术名、文件路径、类名可保留原文但不能成为主标题。
            事实：
            """ + facts, 4_000);
        advanceStage(jobId, "MODEL_RESPONSE_RECEIVED", "模型已返回结果，正在解析与归一化");
        List<JsonNode> values = outputAdapter.items(response.parsed().root(), "capabilities", "capabilityCards", "cards", "items", "results");
        advanceStage(jobId, "MODEL_OUTPUT_NORMALIZE", "正在逐项修复字段并绑定来源证据");
        advanceStage(jobId, "ITEM_VALIDATION", "正在逐项校验能力卡片，局部异常不会影响其他有效项");
        List<CardDraft> result = new ArrayList<>();
        Set<String> names = new LinkedHashSet<>();
        int discarded = 0;
        int invalidIndexes = 0;
        for (JsonNode value : values) {
            if (!value.isObject()) {
                discarded++;
                continue;
            }
            String rawName = outputAdapter.text(value, "", "name", "title", "capabilityName");
            String rawSummary = outputAdapter.text(value, "", "summary", "description", "plainSummary");
            if (rawName.isBlank() && rawSummary.isBlank()) {
                discarded++;
                continue;
            }
            String name = DisplayContentSanitizer.sanitizeCapabilityName(rawName.isBlank() ? rawSummary : rawName);
            String dedupeKey = name.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
            if (!names.add(dedupeKey)) {
                discarded++;
                continue;
            }
            List<String> requestedIndexes = outputAdapter.strings(value, "sourceIndexes", "sourceIds", "sources");
            List<CapabilitySource> boundSources = requestedIndexes.stream().map(sourceMap::get).filter(java.util.Objects::nonNull).distinct().toList();
            invalidIndexes += Math.max(0, requestedIndexes.size() - boundSources.size());
            List<String> sourceRefs = boundSources.stream().map(source -> "segment:" + source.id()).toList();
            List<String> evidence = boundSources.stream().flatMap(source -> source.evidenceRefs().stream()).distinct().toList();
            String warning = evidence.isEmpty() ? "这张卡片缺少可绑定证据，需要补充证据。"
                : requestedIndexes.size() > boundSources.size() ? "部分来源编号无效，已保留可绑定证据，请人工复核。" : "";
            String summary = DisplayContentSanitizer.sanitizeCapabilitySummary(rawSummary.isBlank() ? rawName : rawSummary);
            result.add(new CardDraft(
                name,
                summary,
                DisplayContentSanitizer.sanitizeCapabilitySummary(outputAdapter.text(value, summary, "problemSolved", "problem", "value")),
                DisplayContentSanitizer.sanitizeCapabilitySummary(outputAdapter.text(value, "项目核心流程", "featureEntry", "entry", "entryPoint")),
                sourceRefs, evidence,
                DisplayContentSanitizer.sanitizeCapabilitySummary(outputAdapter.text(value, summary, "readme", "readmeExpression")),
                DisplayContentSanitizer.sanitizeCapabilitySummary(outputAdapter.text(value, summary, "resume", "resumeExpression")),
                DisplayContentSanitizer.sanitizeCapabilitySummary(outputAdapter.text(value, summary, "interview", "interviewExpression")),
                warning
            ));
            if (result.size() == 8) break;
        }
        advanceStage(jobId, "EVIDENCE_BINDING", "已根据来源编号恢复真实证据引用");
        advanceStage(jobId, "CONTENT_SANITIZE", "正在清洗用户可见内容并整理警告");
        return new ModelDraftResult(result, new CapabilityDiagnostics(
            true, response.parsed().repaired(), values.size(), discarded + Math.max(0, values.size() - result.size() - discarded),
            invalidIndexes, (int) result.stream().filter(draft -> draft.evidenceRefs().isEmpty()).count(), ""
        ));
    }

    private AiProvider configuredProvider(UUID userId) {
        return providerRepository.findByUserIdOrderByDefaultEnabledDescUpdatedAtDesc(userId).stream()
            .filter(provider -> provider.getType() != AiProviderType.MOCK)
            .filter(provider -> provider.getApiKey() != null && !provider.getApiKey().isBlank()).findFirst().orElse(null);
    }

    private String required(JsonNode node, String field) {
        String value = node.path(field).asText("").trim();
        if (value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value;
    }

    private List<String> strings(JsonNode node) {
        if (!node.isArray()) return List.of();
        List<String> result = new ArrayList<>();
        node.forEach(value -> { if (value.isTextual() && !value.asText().isBlank()) result.add(value.asText().trim()); });
        return result;
    }

    private String entry(List<String> files) {
        if (files.stream().anyMatch(file -> file.contains("dashboard"))) return "工作台 / 分析新变化";
        if (files.stream().anyMatch(file -> file.contains("capabil"))) return "能力与成果 / 分析项目能力";
        if (files.stream().anyMatch(file -> file.contains("project-changes"))) return "沉淀确认详情";
        return files.isEmpty() ? "项目核心流程" : files.get(0);
    }

    private void ownedProject(UUID userId, UUID projectId) {
        projectRepository.findByIdAndUserId(projectId, userId)
            .orElseThrow(() -> new AppException("PROJECT_NOT_FOUND", "项目不存在", HttpStatus.NOT_FOUND));
    }

    private CapabilityCardResponse response(ProjectCapabilityCard card) {
        return new CapabilityCardResponse(
            card.getId(), card.getProjectId(), card.getName(), card.getSummary(), card.getProblemSolved(), card.getFeatureEntry(),
            card.getSourceRefs(), card.getEvidenceRefs(), card.getReadmeExpression(), card.getResumeExpression(), card.getInterviewExpression(),
            card.getStatus().name(), card.getGenerationMode(), card.getModelProvider(), card.getFallbackReason(), card.getCreatedAt(), card.getUpdatedAt()
        );
    }

    private String trim(String value, int max) { return value.length() <= max ? value : value.substring(0, max); }
    private String truncate(String value, int max) { return value == null ? "" : (value.length() <= max ? value : value.substring(0, max) + "…"); }

    private record CardDraft(
        String name, String summary, String problemSolved, String featureEntry, List<String> sourceRefs, List<String> evidenceRefs,
        String readme, String resume, String interview, String warning
    ) {
    }

    private record CapabilityInput(AiProvider provider, List<CapabilitySource> sources) {
    }

    private record CapabilitySource(
        UUID id, String title, String summary, List<String> affectedFiles, List<String> evidenceRefs
    ) {
    }

    private record ModelDraftResult(List<CardDraft> drafts, CapabilityDiagnostics diagnostics) {
    }

    public record CapabilityDiagnostics(
        boolean rawResponsePresent,
        boolean repaired,
        int recognizedItems,
        int discardedItems,
        int invalidSourceIndexes,
        int needsEvidenceItems,
        String failureStage
    ) {
    }

    public record CapabilityAnalysisOutcome(
        List<CapabilityCardResponse> cards,
        boolean hasWarnings,
        int needsEvidenceCount,
        CapabilityDiagnostics diagnostics
    ) {
    }

    public static final class CapabilityAnalysisException extends RuntimeException {
        private final String stage;

        public CapabilityAnalysisException(String stage, String message, Throwable cause) {
            super(message, cause);
            this.stage = stage;
        }

        public String stage() {
            return stage;
        }
    }
}
