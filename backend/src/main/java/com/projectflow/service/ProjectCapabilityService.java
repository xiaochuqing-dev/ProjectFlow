package com.projectflow.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

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
    private final ProjectAnalysisJobRepository jobRepository;
    // V3.3.4: 阶段推进用独立事务提交，前端轮询可看到 stage 推进。
    private final TransactionTemplate stageTransactionTemplate;

    public ProjectCapabilityService(
        ProjectRepository projectRepository,
        DevelopmentSegmentRepository segmentRepository,
        ProjectCapabilityCardRepository cardRepository,
        AiProviderRepository providerRepository,
        ModelGatewayService modelGatewayService,
        ProjectAnalysisJobRepository jobRepository,
        PlatformTransactionManager transactionManager
    ) {
        this.projectRepository = projectRepository;
        this.segmentRepository = segmentRepository;
        this.cardRepository = cardRepository;
        this.providerRepository = providerRepository;
        this.modelGatewayService = modelGatewayService;
        this.jobRepository = jobRepository;
        this.stageTransactionTemplate = new TransactionTemplate(transactionManager);
        this.stageTransactionTemplate.setPropagationBehavior(
            org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW
        );
    }

    @Transactional
    public List<CapabilityCardResponse> analyze(UUID userId, UUID projectId) {
        return analyze(userId, projectId, null);
    }

    // V3.3.4: 接收 jobId 以推进异步任务阶段。jobId 为 null 时退化为同步调用。
    @Transactional
    public List<CapabilityCardResponse> analyze(UUID userId, UUID projectId, UUID jobId) {
        ownedProject(userId, projectId);
        advanceStage(jobId, "LOAD_EVIDENCE", "正在读取已确认沉淀和开发推进段");
        List<DevelopmentSegment> sources = segmentRepository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
            .filter(segment -> segment.getStatus() == DevelopmentSegmentStatus.CONFIRMED)
            .limit(24).toList();
        if (sources.isEmpty()) {
            throw new AppException("CAPABILITY_EVIDENCE_REQUIRED", "请先确认至少一条项目沉淀，再分析项目能力", HttpStatus.BAD_REQUEST);
        }
        AiProvider provider = configuredProvider(userId);
        // V3.3.3: 模型配置前置检查。未配置模型时不生成完整能力卡片，提示去配置模型。
        if (provider == null) {
            throw new AppException(
                "MODEL_NOT_CONFIGURED",
                "当前未配置模型，无法进行完整人话能力分析。请先在设置页配置模型，ProjectFlow 不会用低质量本地模板伪装成完整模型分析。",
                HttpStatus.BAD_REQUEST
            );
        }
        recordInputSummary(jobId, sources.size());
        cardRepository.deleteByProjectIdAndStatus(projectId, CapabilityCardStatus.CANDIDATE);
        cardRepository.deleteByProjectIdAndStatus(projectId, CapabilityCardStatus.NEEDS_EVIDENCE);

        advanceStage(jobId, "MODEL_CAPABILITY_ANALYSIS", "正在调用模型分析项目能力（可能需要几分钟，任务会继续运行）");
        String mode = "MODEL";
        String fallback = "";
        List<CardDraft> drafts;
        try {
            drafts = modelDrafts(provider, sources);
        } catch (Exception exception) {
            mode = "LOCAL_RULE";
            fallback = "模型能力分析失败，已基于确认沉淀与开发推进段生成候选能力，建议配置或检查模型后重新分析。";
            drafts = localDrafts(sources);
        }
        advanceStage(jobId, "PERSIST_CAPABILITY_CARDS", "正在保存能力卡片");
        List<ProjectCapabilityCard> cards = new ArrayList<>();
        for (CardDraft draft : drafts.stream().limit(8).toList()) {
            ProjectCapabilityCard card = new ProjectCapabilityCard(projectId);
            card.update(
                draft.name(), draft.summary(), draft.problemSolved(), draft.featureEntry(), draft.sourceRefs(), draft.evidenceRefs(),
                draft.readme(), draft.resume(), draft.interview(), mode, provider.getName(), fallback
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

    private List<CardDraft> localDrafts(List<DevelopmentSegment> sources) {
        // V3.3.4 小阶段修复：本地 fallback 也经过 DisplayContentSanitizer 清洗，避免原始路径 / 英文 commit 污染主视图。
        return sources.stream().limit(8).map(segment -> new CardDraft(
            DisplayContentSanitizer.sanitizeCapabilityName(segment.getTitle()),
            DisplayContentSanitizer.sanitizeCapabilitySummary(segment.getPlainSummary()),
            DisplayContentSanitizer.sanitizeUserVisibleValue(segment.getUserVisibleValue()),
            entry(segment.getAffectedFiles()),
            List.of("segment:" + segment.getId()), segment.getEvidenceRefs(),
            DisplayContentSanitizer.sanitizeCapabilitySummary(segment.getTitle() + "：" + segment.getPlainSummary()),
            DisplayContentSanitizer.sanitizeCapabilitySummary("基于可追溯证据完成" + segment.getTitle()),
            DisplayContentSanitizer.sanitizeCapabilitySummary("可说明如何通过" + entry(segment.getAffectedFiles()) + "完成该能力，并展示提交与文件证据。")
        )).toList();
    }

    private List<CardDraft> modelDrafts(AiProvider provider, List<DevelopmentSegment> sources) throws Exception {
        Set<String> allowedEvidence = new LinkedHashSet<>();
        StringBuilder facts = new StringBuilder();
        for (DevelopmentSegment source : sources) {
            allowedEvidence.addAll(source.getEvidenceRefs());
            facts.append("SEGMENT ").append(source.getId()).append(" | ").append(source.getTitle()).append(" | ")
                .append(source.getPlainSummary()).append(" | entry=").append(entry(source.getAffectedFiles())).append(" | evidence=")
                .append(String.join(",", source.getEvidenceRefs())).append('\n');
        }
        JsonNode json = modelGatewayService.callJson(provider, """
            基于全部确认开发推进段整体分析 ProjectFlow 项目能力。返回严格 JSON：
            {"capabilities":[{"name":"","summary":"","problemSolved":"","featureEntry":"","sourceRefs":[],"evidenceRefs":[],"readme":"","resume":"","interview":""}]}
            生成 3 到 8 张具体且不重复的卡片，不得发明来源或证据。
            能力名称必须贴合作品真实功能（如"扫描指纹复用稳定分析结果""待整理变更归并为开发推进段""GitHub 状态与本地 Git 多来源证据整合"），禁止泛化模板名（如"项目资产沉淀能力""技术理解能力"），禁止直接复读 commit message。
            所有用户可见字段（name、summary、problemSolved、featureEntry、readme、resume、interview）必须使用简体中文人话；技术名、文件路径、类名可保留原文但不能成为主标题。
            事实：
            """ + facts, 8_000);
        JsonNode values = json.path("capabilities");
        if (!values.isArray() || values.size() < 3 || values.size() > 8) throw new IllegalArgumentException("capabilities must contain 3 to 8 cards");
        List<CardDraft> result = new ArrayList<>();
        for (JsonNode value : values) {
            List<String> evidence = strings(value.path("evidenceRefs")).stream().filter(allowedEvidence::contains).toList();
            if (evidence.isEmpty()) throw new IllegalArgumentException("capability evidence is invalid");
            // V3.3.4 小阶段修复：模型输出也必须经过 DisplayContentSanitizer 清洗，不能直接信任。
            result.add(new CardDraft(
                DisplayContentSanitizer.sanitizeCapabilityName(required(value, "name")),
                DisplayContentSanitizer.sanitizeCapabilitySummary(required(value, "summary")),
                DisplayContentSanitizer.sanitizeCapabilitySummary(required(value, "problemSolved")),
                DisplayContentSanitizer.sanitizeCapabilitySummary(required(value, "featureEntry")),
                strings(value.path("sourceRefs")), evidence,
                DisplayContentSanitizer.sanitizeCapabilitySummary(required(value, "readme")),
                DisplayContentSanitizer.sanitizeCapabilitySummary(required(value, "resume")),
                DisplayContentSanitizer.sanitizeCapabilitySummary(required(value, "interview"))
            ));
        }
        return result;
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

    private record CardDraft(
        String name, String summary, String problemSolved, String featureEntry, List<String> sourceRefs, List<String> evidenceRefs,
        String readme, String resume, String interview
    ) {
    }
}
