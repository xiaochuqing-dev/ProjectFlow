package com.projectflow.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.entity.AiProvider;
import com.projectflow.entity.ProjectCapability;
import com.projectflow.entity.ProjectCapabilityAttention;
import com.projectflow.entity.ProjectCapabilityAttentionStatus;
import com.projectflow.entity.ProjectCapabilityEvolution;
import com.projectflow.entity.ProjectCapabilityEvolutionType;
import com.projectflow.entity.ProjectCapabilityFact;
import com.projectflow.entity.ProjectCapabilityFactClassification;
import com.projectflow.entity.ProjectCapabilityFactCoverage;
import com.projectflow.entity.ProjectCapabilityMapState;
import com.projectflow.entity.ProjectCapabilityMapStatus;
import com.projectflow.entity.ProjectCapabilityMaturity;
import com.projectflow.entity.ProjectCapabilityRelationRole;
import com.projectflow.entity.ProjectCapabilityStatus;
import com.projectflow.entity.ProjectFact;
import com.projectflow.entity.ProjectFactHistoryStatus;
import com.projectflow.entity.ProjectFactRecordStatus;
import com.projectflow.entity.ProjectTimelineSummary;
import com.projectflow.entity.ProjectTimelineTheme;
import com.projectflow.entity.TimelineGranularity;
import com.projectflow.repository.AiProviderRepository;
import com.projectflow.repository.CapabilityFactStatsRow;
import com.projectflow.repository.ProjectAnalysisJobRepository;
import com.projectflow.repository.ProjectCapabilityAttentionRepository;
import com.projectflow.repository.ProjectCapabilityEvolutionRepository;
import com.projectflow.repository.ProjectCapabilityFactCoverageRepository;
import com.projectflow.repository.ProjectCapabilityFactRepository;
import com.projectflow.repository.ProjectCapabilityMapStateRepository;
import com.projectflow.repository.ProjectCapabilityRepository;
import com.projectflow.repository.ProjectFactHistoryStateRepository;
import com.projectflow.repository.ProjectFactRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.repository.ProjectTimelineSummaryRepository;
import com.projectflow.repository.ProjectTimelineThemeRepository;
import com.projectflow.repository.TimelineFactVersionRow;
import com.projectflow.support.AppException;

@Service
public class ProjectCapabilityMapService {
    static final int FACT_CHUNK_SIZE = 120;
    static final int GENERATION_VERSION = 1;
    private static final List<String> PROHIBITED_TEXT = List.of("下一步", "路线图", "未来计划", "后续建议", "应该继续");
    private static final Set<String> PROHIBITED_FIELDS = Set.of("maturity", "maturityLevel", "maturityScore", "confidenceScore", "reasoning");

    private final ProjectRepository projectRepository;
    private final ProjectFactRepository factRepository;
    private final ProjectFactHistoryStateRepository historyRepository;
    private final ProjectCapabilityRepository capabilityRepository;
    private final ProjectCapabilityEvolutionRepository evolutionRepository;
    private final ProjectCapabilityFactRepository capabilityFactRepository;
    private final ProjectCapabilityFactCoverageRepository coverageRepository;
    private final ProjectCapabilityAttentionRepository attentionRepository;
    private final ProjectCapabilityMapStateRepository stateRepository;
    private final ProjectTimelineSummaryRepository timelineSummaryRepository;
    private final ProjectTimelineThemeRepository timelineThemeRepository;
    private final ProjectAnalysisJobRepository jobRepository;
    private final AiProviderRepository providerRepository;
    private final ModelGatewayService modelGateway;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;

    public ProjectCapabilityMapService(
        ProjectRepository projectRepository,
        ProjectFactRepository factRepository,
        ProjectFactHistoryStateRepository historyRepository,
        ProjectCapabilityRepository capabilityRepository,
        ProjectCapabilityEvolutionRepository evolutionRepository,
        ProjectCapabilityFactRepository capabilityFactRepository,
        ProjectCapabilityFactCoverageRepository coverageRepository,
        ProjectCapabilityAttentionRepository attentionRepository,
        ProjectCapabilityMapStateRepository stateRepository,
        ProjectTimelineSummaryRepository timelineSummaryRepository,
        ProjectTimelineThemeRepository timelineThemeRepository,
        ProjectAnalysisJobRepository jobRepository,
        AiProviderRepository providerRepository,
        ModelGatewayService modelGateway,
        ObjectMapper objectMapper,
        ApplicationEventPublisher eventPublisher,
        PlatformTransactionManager transactionManager
    ) {
        this.projectRepository = projectRepository;
        this.factRepository = factRepository;
        this.historyRepository = historyRepository;
        this.capabilityRepository = capabilityRepository;
        this.evolutionRepository = evolutionRepository;
        this.capabilityFactRepository = capabilityFactRepository;
        this.coverageRepository = coverageRepository;
        this.attentionRepository = attentionRepository;
        this.stateRepository = stateRepository;
        this.timelineSummaryRepository = timelineSummaryRepository;
        this.timelineThemeRepository = timelineThemeRepository;
        this.jobRepository = jobRepository;
        this.providerRepository = providerRepository;
        this.modelGateway = modelGateway;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(
            org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void factsCommitted(ProjectFactsCommittedEvent event) {
        markDirty(event.projectId(), false);
        projectRepository.findById(event.projectId()).ifPresent(project ->
            eventPublisher.publishEvent(new ProjectCapabilityRefreshRequestedEvent(project.getUserId(), project.getId()))
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void modelConfigured(ModelProviderConfiguredEvent event) {
        projectRepository.findByUserIdOrderByUpdatedAtDesc(event.userId()).forEach(project -> {
            if (factRepository.countByProjectId(project.getId()) == 0) return;
            markDirty(project.getId(), true);
            eventPublisher.publishEvent(new ProjectCapabilityRefreshRequestedEvent(event.userId(), project.getId()));
        });
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(320)
    public void bootstrapExistingFacts() {
        projectRepository.findAll().forEach(project -> {
            if (factRepository.countByProjectId(project.getId()) == 0) return;
            boolean changed = markDirty(project.getId(), false);
            if (changed) eventPublisher.publishEvent(new ProjectCapabilityRefreshRequestedEvent(project.getUserId(), project.getId()));
        });
    }

    public boolean markDirty(UUID projectId, boolean force) {
        List<TimelineFactVersionRow> versions = factRepository.capabilityVersions(projectId);
        if (versions.isEmpty()) return false;
        String fingerprint = sourceFingerprint(projectId, versions);
        Instant latest = versions.stream().map(TimelineFactVersionRow::updatedAt).filter(java.util.Objects::nonNull)
            .max(Comparator.naturalOrder()).orElse(null);
        Boolean changed = transactionTemplate.execute(status -> {
            ProjectCapabilityMapState state = stateRepository.findLockedByProjectId(projectId).orElse(null);
            if (state == null) state = new ProjectCapabilityMapState(projectId);
            boolean sameReady = fingerprint.equals(state.getSourceFactFingerprint())
                && state.getStatus() == ProjectCapabilityMapStatus.READY;
            if (sameReady && !force) return false;
            state.markDirty(versions.size(), fingerprint, latest);
            stateRepository.save(state);
            return true;
        });
        return Boolean.TRUE.equals(changed);
    }

    public String nextDirtyScope(UUID projectId) {
        if (historyRepository.findByProjectId(projectId)
            .map(state -> state.getStatus() == ProjectFactHistoryStatus.RUNNING).orElse(false)) return "";
        ProjectCapabilityMapState state = stateRepository.findByProjectId(projectId).orElse(null);
        if (state == null) return "";
        boolean eligible = state.getStatus() == ProjectCapabilityMapStatus.DIRTY
            || (state.getStatus() == ProjectCapabilityMapStatus.READY_STALE && state.getErrorCode().isBlank());
        return eligible ? new RefreshScope(projectId, state.getSourceFactFingerprint()).encode() : "";
    }

    public void markQueued(String encodedScope, UUID jobId) {
        RefreshScope scope = RefreshScope.parse(encodedScope);
        transactionTemplate.executeWithoutResult(status -> stateRepository.findLockedByProjectId(scope.projectId()).ifPresent(state -> {
            if (state.getSourceFactFingerprint().equals(scope.fingerprint())
                && (state.getStatus() == ProjectCapabilityMapStatus.DIRTY || state.getStatus() == ProjectCapabilityMapStatus.READY_STALE)) {
                state.markQueued(jobId);
                stateRepository.save(state);
            }
        }));
    }

    public CapabilityRefreshOutcome refresh(UUID userId, UUID projectId, UUID jobId, String encodedScope) throws Exception {
        RefreshScope scope = RefreshScope.parse(encodedScope);
        if (!projectId.equals(scope.projectId())) throw new IllegalArgumentException("Capability scope project mismatch");
        projectRepository.findByIdAndUserId(projectId, userId)
            .orElseThrow(() -> new AppException("PROJECT_NOT_FOUND", "项目不存在", HttpStatus.NOT_FOUND));
        Prepared prepared = transactionTemplate.execute(status -> {
            ProjectCapabilityMapState state = stateRepository.findLockedByProjectId(projectId).orElse(null);
            if (state == null || !scope.fingerprint().equals(state.getSourceFactFingerprint())) return null;
            state.markGenerating(jobId);
            stateRepository.save(state);
            return new Prepared(state.getLatestSuccessfulAt() == null, state.getSourceFactCount(), state.getLastProcessedFactAt());
        });
        if (prepared == null) return new CapabilityRefreshOutcome("{\"status\":\"STALE_JOB_SKIPPED\"}", List.of(), false);

        AiProvider provider = providerRepository.findFirstByUserIdAndDefaultEnabledTrueOrderByUpdatedAtDesc(userId).orElse(null);
        if (provider == null) {
            transactionTemplate.executeWithoutResult(status -> stateRepository.findLockedByProjectId(projectId).ifPresent(state -> {
                if (scope.fingerprint().equals(state.getSourceFactFingerprint())) {
                    state.markWaitingForModel();
                    stateRepository.save(state);
                }
            }));
            return new CapabilityRefreshOutcome("{\"status\":\"WAITING_FOR_MODEL\"}", List.of(), false);
        }

        List<ModelGatewayService.ModelCallDiagnostics> diagnostics = new ArrayList<>();
        try {
            CapabilityPlan plan = buildPlan(provider, projectId, prepared.bootstrap(), diagnostics);
            ApplyResult applied = transactionTemplate.execute(status -> applyPlan(projectId, jobId, scope, provider, plan, prepared));
            if (applied == null) throw new CapabilityCoverageException("Capability map changed while applying generated result");
            String result = objectMapper.writeValueAsString(Map.of(
                "status", "READY",
                "sourceFactCount", applied.sourceFactCount(),
                "coveredFactCount", applied.coveredFactCount(),
                "capabilityCount", applied.capabilityCount(),
                "operationCount", plan.operations().size(),
                "requestCount", diagnostics.stream().mapToInt(ModelGatewayService.ModelCallDiagnostics::requestCount).sum()
            ));
            return new CapabilityRefreshOutcome(result, List.copyOf(diagnostics), true);
        } catch (CancellationException exception) {
            markFailed(scope, jobId, "CAPABILITY_REFRESH_CANCELLED", "能力地图刷新已取消，旧能力地图和事实保持不变");
            throw exception;
        } catch (Exception exception) {
            markFailed(scope, jobId, "CAPABILITY_REFRESH_FAILED", safeMessage(exception));
            throw exception;
        }
    }

    public void retry(UUID userId, UUID projectId) {
        projectRepository.findByIdAndUserId(projectId, userId)
            .orElseThrow(() -> new AppException("PROJECT_NOT_FOUND", "项目不存在", HttpStatus.NOT_FOUND));
        ProjectCapabilityMapState state = stateRepository.findByProjectId(projectId).orElse(null);
        if (state == null || state.getStatus() == ProjectCapabilityMapStatus.READY) {
            throw new AppException("CAPABILITY_MAP_RETRY_NOT_REQUIRED", "当前能力地图不需要重试", HttpStatus.CONFLICT);
        }
        markDirty(projectId, true);
        eventPublisher.publishEvent(new ProjectCapabilityRefreshRequestedEvent(userId, projectId));
    }

    private CapabilityPlan buildPlan(
        AiProvider provider, UUID projectId, boolean bootstrap,
        List<ModelGatewayService.ModelCallDiagnostics> diagnostics
    ) throws Exception {
        List<ProjectCapability> existing = capabilityRepository.findByProjectIdAndStatusOrderByFirstFormedAtAsc(
            projectId, ProjectCapabilityStatus.ACTIVE
        );
        Map<String, CapabilityContext> contexts = new LinkedHashMap<>();
        existing.forEach(capability -> contexts.put(capability.getId().toString(), CapabilityContext.from(capability)));
        List<CapabilityOperation> operations = new ArrayList<>();
        List<FactClassification> classifications = new ArrayList<>();
        List<ProjectFact> sourceFacts = new ArrayList<>();
        int pageIndex = 0;
        Page<ProjectFact> page;
        do {
            ModelCancellationContext.throwIfCancelled();
            page = bootstrap
                ? factRepository.findByProjectIdOrderByTimelineEventAtAscCreatedAtAsc(projectId, PageRequest.of(pageIndex++, FACT_CHUNK_SIZE))
                : factRepository.findCapabilityFactsNeedingCoverage(projectId, PageRequest.of(pageIndex++, FACT_CHUNK_SIZE));
            if (page.isEmpty()) continue;
            List<ProjectFact> chunkFacts = page.getContent();
            ParsedChunk chunk = callChunk(provider, projectId, bootstrap, contexts, chunkFacts, diagnostics);
            operations.addAll(chunk.operations());
            classifications.addAll(chunk.classifications());
            sourceFacts.addAll(chunkFacts);
            for (CapabilityOperation operation : chunk.operations()) {
                if (operation.type() == OperationType.NEW_CAPABILITY) {
                    contexts.put(operation.temporaryKey(), CapabilityContext.from(operation));
                } else if (operation.type() == OperationType.ENHANCE_CAPABILITY) {
                    CapabilityContext current = contexts.get(operation.capabilityId());
                    if (current != null) contexts.put(operation.capabilityId(), current.enhance(operation));
                }
            }
        } while (page.hasNext());
        return new CapabilityPlan(List.copyOf(operations), List.copyOf(classifications), List.copyOf(sourceFacts));
    }

    private ParsedChunk callChunk(
        AiProvider provider, UUID projectId, boolean bootstrap, Map<String, CapabilityContext> contexts,
        List<ProjectFact> facts, List<ModelGatewayService.ModelCallDiagnostics> diagnostics
    ) throws Exception {
        List<String> allowedFactIds = facts.stream().map(ProjectFact::getId).map(UUID::toString).toList();
        List<Map<String, Object>> compactFacts = facts.stream().map(this::compactFact).toList();
        List<Map<String, Object>> capabilityContext = contexts.entrySet().stream().map(entry -> Map.<String, Object>of(
            "capabilityId", entry.getKey(),
            "canonicalName", entry.getValue().name(),
            "problemSolved", entry.getValue().problem(),
            "productAreas", entry.getValue().areas(),
            "factCount", entry.getValue().factCount()
        )).toList();
        String prompt = """
            你正在维护 ProjectFlow 的全生命周期项目能力地图。ProjectFact 是唯一事实来源，Timeline 仅提供时间上下文。
            项目能力必须是多个真实事实长期证明的稳定工程能力，不是单个 commit、时间段 Theme、泛化技能、评价或未来计划。
            必须把每个 ALLOWED_FACT_ID 恰好分类一次：进入 operation.factIds、noCapabilityChangeFactIds 或 attentionFacts.factId。
            这里的“分类”不是挑选代表事实：不得只返回重要、最近或最相关的少数 ID。所有未进入 operation 的 ID 都必须逐字复制到 noCapabilityChangeFactIds 或 attentionFacts。
            只可引用 EXISTING_CAPABILITIES_JSON 中的 capabilityId。新增能力必须使用 NEW_CAPABILITY 和本次响应内唯一 temporaryKey，禁止自行生成数据库 UUID。
            operation type 只能是 NEW_CAPABILITY、ENHANCE_CAPABILITY、ADD_EVIDENCE、MERGE_CAPABILITY。
            MERGE_CAPABILITY 必须同时给 capabilityId 与 mergeIntoCapabilityId；名称相似本身不足以合并。
            维护性、文档性或普通修复事实若不证明能力变化，应进入 noCapabilityChangeFactIds，不要制造伪能力。
            禁止下一步、建议、优先级、路线图、未来能力、maturity 数值或 reasoning。
            返回严格 JSON：
            {"operations":[{"type":"NEW_CAPABILITY","temporaryKey":"TMP-1","capabilityId":"","mergeIntoCapabilityId":"","canonicalName":"中文能力名","summary":"当前能力摘要","problemSolved":"解决的问题","longTermValue":"长期价值","productAreas":["产品区域"],"factIds":["uuid"],"evolutionTitle":"演进标题","evolutionSummary":"演进摘要","readmeExpression":"","resumeExpression":"","interviewExpression":""}],"noCapabilityChangeFactIds":[],"attentionFacts":[{"factId":"uuid","reason":"无法自动处理的明确冲突"}]}。
            """
            + "\nSCOPE=" + (bootstrap ? "BOOTSTRAP_FULL_HISTORY" : "INCREMENTAL_NEW_FACTS")
            + "\nPROJECT_ID=" + projectId
            + "\nEXPECTED_FACT_CLASSIFICATION_COUNT=" + allowedFactIds.size()
            + "\nALLOWED_FACT_IDS_JSON=" + objectMapper.writeValueAsString(allowedFactIds)
            + "\nEXISTING_CAPABILITIES_JSON=" + objectMapper.writeValueAsString(capabilityContext)
            + "\nTIMELINE_CONTEXT_JSON=" + objectMapper.writeValueAsString(timelineContext(projectId, facts))
            + "\nFACTS_JSON=" + objectMapper.writeValueAsString(compactFacts);
        ModelTaskType task = bootstrap ? ModelTaskType.PROJECT_CAPABILITY_MAP_BOOTSTRAP : ModelTaskType.PROJECT_CAPABILITY_MAP_INCREMENTAL;
        ModelGatewayService.StructuredModelResponse response = modelGateway.callStructured(provider, prompt, task);
        diagnostics.add(response.diagnostics());
        try {
            return parseChunk(response.parsed().root(), new LinkedHashSet<>(allowedFactIds), new LinkedHashSet<>(contexts.keySet()));
        } catch (CapabilityCoverageException exception) {
            if (!exception.getMessage().startsWith("Capability model omitted ")) throw exception;
            String repairPrompt = prompt
                + "\nCOVERAGE_REPAIR_REQUIRED=上一响应遗漏了来源事实。请从头返回完整 JSON；operations、noCapabilityChangeFactIds、attentionFacts 的 fact ID 并集必须与 ALLOWED_FACT_IDS_JSON 完全相等，数量必须为 "
                + allowedFactIds.size() + "，不得省略、采样、概括或重复。";
            ModelGatewayService.StructuredModelResponse repaired = modelGateway.callStructured(provider, repairPrompt, task);
            diagnostics.add(repaired.diagnostics());
            return parseChunk(repaired.parsed().root(), new LinkedHashSet<>(allowedFactIds), new LinkedHashSet<>(contexts.keySet()));
        }
    }

    static ParsedChunk parseChunk(JsonNode root, Set<String> allowedFactIds, Set<String> existingCapabilityIds) {
        if (root == null || !root.isObject()) throw new CapabilityCoverageException("Capability model output is not an object");
        rejectProhibited(root);
        JsonNode operationNodes = root.path("operations");
        if (!operationNodes.isArray()) throw new CapabilityCoverageException("Capability operations are missing");
        Set<String> assigned = new LinkedHashSet<>();
        Set<String> knownCapabilities = new LinkedHashSet<>(existingCapabilityIds);
        List<CapabilityOperation> operations = new ArrayList<>();
        for (JsonNode node : operationNodes) {
            OperationType type;
            try {
                type = OperationType.valueOf(text(node, "type").toUpperCase(Locale.ROOT));
            } catch (RuntimeException exception) {
                throw new CapabilityCoverageException("Capability operation type is invalid");
            }
            String capabilityId = text(node, "capabilityId");
            String temporaryKey = text(node, "temporaryKey");
            String mergeInto = text(node, "mergeIntoCapabilityId");
            if (type == OperationType.NEW_CAPABILITY) {
                if (temporaryKey.isBlank() || knownCapabilities.contains(temporaryKey)) {
                    throw new CapabilityCoverageException("New capability temporary key is missing or duplicated");
                }
                knownCapabilities.add(temporaryKey);
            } else {
                if (!knownCapabilities.contains(capabilityId)) {
                    throw new CapabilityCoverageException("Capability model returned an unknown capability ID");
                }
                if (type == OperationType.MERGE_CAPABILITY && (!knownCapabilities.contains(mergeInto) || mergeInto.equals(capabilityId))) {
                    throw new CapabilityCoverageException("Capability merge target is unknown or invalid");
                }
            }
            List<String> factIds = validatedFactIds(node.path("factIds"), allowedFactIds, assigned);
            if (factIds.isEmpty()) throw new CapabilityCoverageException("Capability operation must classify at least one source fact");
            operations.add(new CapabilityOperation(
                type, capabilityId, temporaryKey, mergeInto, text(node, "canonicalName"), text(node, "summary"),
                text(node, "problemSolved"), text(node, "longTermValue"), strings(node.path("productAreas")),
                factIds, text(node, "evolutionTitle"), text(node, "evolutionSummary"),
                text(node, "readmeExpression"), text(node, "resumeExpression"), text(node, "interviewExpression")
            ));
        }
        List<FactClassification> classifications = new ArrayList<>();
        for (String id : validatedFactIds(root.path("noCapabilityChangeFactIds"), allowedFactIds, assigned)) {
            classifications.add(new FactClassification(id, ProjectCapabilityFactClassification.NO_CAPABILITY_CHANGE, ""));
        }
        JsonNode attention = root.path("attentionFacts");
        if (!attention.isArray()) throw new CapabilityCoverageException("Capability attention classification is missing");
        for (JsonNode item : attention) {
            String id = text(item, "factId");
            if (!allowedFactIds.contains(id)) throw new CapabilityCoverageException("Capability model returned an unknown fact ID");
            if (!assigned.add(id)) throw new CapabilityCoverageException("Capability model classified a fact more than once");
            String reason = text(item, "reason");
            if (reason.isBlank()) throw new CapabilityCoverageException("Capability attention reason is empty");
            classifications.add(new FactClassification(id, ProjectCapabilityFactClassification.NEEDS_ATTENTION, reason));
        }
        if (!assigned.equals(allowedFactIds)) {
            Set<String> missing = new LinkedHashSet<>(allowedFactIds);
            missing.removeAll(assigned);
            throw new CapabilityCoverageException("Capability model omitted " + missing.size() + " source facts");
        }
        return new ParsedChunk(List.copyOf(operations), List.copyOf(classifications));
    }

    private ApplyResult applyPlan(
        UUID projectId, UUID jobId, RefreshScope scope, AiProvider provider,
        CapabilityPlan plan, Prepared prepared
    ) {
        ProjectCapabilityMapState state = stateRepository.findLockedByProjectId(projectId).orElse(null);
        if (state == null || !scope.fingerprint().equals(state.getSourceFactFingerprint())) return null;
        Map<UUID, ProjectFact> facts = new LinkedHashMap<>();
        plan.sourceFacts().forEach(fact -> facts.put(fact.getId(), fact));
        Map<String, ProjectCapability> temporaryCapabilities = new HashMap<>();
        Set<UUID> affectedCapabilities = new LinkedHashSet<>();

        for (CapabilityOperation operation : plan.operations()) {
            List<ProjectFact> operationFacts = operation.factIds().stream().map(UUID::fromString).map(facts::get).toList();
            if (operation.type() == OperationType.MERGE_CAPABILITY) {
                applyMergeOrAttention(projectId, jobId, scope.fingerprint(), operation, operationFacts, affectedCapabilities);
                continue;
            }
            ProjectCapability capability;
            ProjectCapabilityEvolutionType evolutionType;
            ProjectCapabilityRelationRole relationRole;
            int versionBefore;
            if (operation.type() == OperationType.NEW_CAPABILITY) {
                String identity = stableIdentity(projectId, operation);
                capability = capabilityRepository.findByProjectIdAndStableIdentityKey(projectId, identity).orElse(null);
                if (capability == null) {
                    capability = new ProjectCapability(projectId, identity, sha256(identity + "\n" + scope.fingerprint()));
                    capability.initialize(
                        fallback(operation.name(), "根据项目事实形成的能力"), operation.summary(), operation.problem(),
                        operation.value(), operation.areas(), occurredAt(operationFacts), "MODEL", provider.getName(),
                        provider.getModelName(), jobId
                    );
                    versionBefore = 0;
                    evolutionType = ProjectCapabilityEvolutionType.NEW_CAPABILITY;
                    relationRole = ProjectCapabilityRelationRole.FORMATION;
                } else {
                    capability.addAlias(operation.name());
                    versionBefore = capability.getCurrentVersion();
                    capability.enhance(operation.name(), operation.summary(), operation.problem(), operation.value(), operation.areas(),
                        occurredAt(operationFacts), provider.getName(), provider.getModelName(), jobId, true);
                    evolutionType = ProjectCapabilityEvolutionType.ENHANCE_CAPABILITY;
                    relationRole = ProjectCapabilityRelationRole.ENHANCEMENT;
                }
                capability.updateExpressions(operation.readme(), operation.resume(), operation.interview());
                capability = capabilityRepository.save(capability);
                temporaryCapabilities.put(operation.temporaryKey(), capability);
            } else {
                capability = resolveCapability(projectId, operation.capabilityId(), temporaryCapabilities);
                versionBefore = capability.getCurrentVersion();
                boolean semantic = operation.type() == OperationType.ENHANCE_CAPABILITY;
                capability.enhance(operation.name(), operation.summary(), operation.problem(), operation.value(), operation.areas(),
                    semantic ? occurredAt(operationFacts) : null, provider.getName(), provider.getModelName(), jobId, semantic);
                capability.updateExpressions(operation.readme(), operation.resume(), operation.interview());
                capability = capabilityRepository.save(capability);
                evolutionType = semantic ? ProjectCapabilityEvolutionType.ENHANCE_CAPABILITY : ProjectCapabilityEvolutionType.ADD_EVIDENCE;
                relationRole = semantic ? ProjectCapabilityRelationRole.ENHANCEMENT : ProjectCapabilityRelationRole.EVIDENCE;
            }
            String operationFingerprint = operationFingerprint(projectId, scope.fingerprint(), operation, capability.getId());
            ProjectCapabilityEvolution evolution = evolutionRepository.findByProjectIdAndOperationFingerprint(projectId, operationFingerprint).orElse(null);
            if (evolution == null) {
                evolution = new ProjectCapabilityEvolution(
                    projectId, capability.getId(), evolutionType, versionBefore, capability.getCurrentVersion(),
                    fallback(operation.evolutionTitle(), capability.getCanonicalName()),
                    fallback(operation.evolutionSummary(), operation.summary()), occurredAt(operationFacts), operationFingerprint
                );
                evolution.attachSourceStats(
                    operationFacts.size(), distinctBatches(operationFacts), timelinePeriods(operationFacts), jobId,
                    provider.getName(), provider.getModelName()
                );
                evolution = evolutionRepository.save(evolution);
            }
            linkFacts(projectId, capability, evolution, operationFacts, relationRole, scope.fingerprint());
            affectedCapabilities.add(capability.getId());
        }

        for (FactClassification classification : plan.classifications()) {
            ProjectFact fact = facts.get(UUID.fromString(classification.factId()));
            upsertCoverage(projectId, fact, classification.classification(), null, null, classification.reason());
            if (classification.classification() == ProjectCapabilityFactClassification.NEEDS_ATTENTION) {
                addAttention(projectId, "FACT_CLASSIFICATION", classification.reason(), fact.getId(), null, null,
                    sha256(projectId + "\nFACT\n" + fact.getId() + "\n" + classification.reason()), jobId);
            }
        }

        coverageRepository.flush();
        long sourceCount = factRepository.countByProjectId(projectId);
        long coveredCount = coverageRepository.countByProjectId(projectId);
        if (coveredCount != sourceCount) {
            throw new CapabilityCoverageException("Capability coverage is incomplete: " + coveredCount + "/" + sourceCount);
        }
        affectedCapabilities.forEach(id -> recomputeCapability(projectId, id));
        int assigned = Math.toIntExact(coverageRepository.countByProjectIdAndClassification(
            projectId, ProjectCapabilityFactClassification.CONTRIBUTES_TO_CAPABILITY
        ));
        int noChange = Math.toIntExact(coverageRepository.countByProjectIdAndClassification(
            projectId, ProjectCapabilityFactClassification.NO_CAPABILITY_CHANGE
        ));
        int attention = Math.toIntExact(coverageRepository.countByProjectIdAndClassification(
            projectId, ProjectCapabilityFactClassification.NEEDS_ATTENTION
        ));
        String completedFingerprint = sourceFingerprint(projectId, factRepository.capabilityVersions(projectId));
        state.complete(
            Math.toIntExact(sourceCount), Math.toIntExact(coveredCount), assigned, noChange, attention,
            completedFingerprint, prepared.latestFactAt(), jobId
        );
        stateRepository.save(state);
        return new ApplyResult(
            Math.toIntExact(sourceCount), Math.toIntExact(coveredCount),
            capabilityRepository.countByProjectIdAndStatus(projectId, ProjectCapabilityStatus.ACTIVE)
        );
    }

    private void applyMergeOrAttention(
        UUID projectId, UUID jobId, String scopeFingerprint, CapabilityOperation operation,
        List<ProjectFact> facts, Set<UUID> affected
    ) {
        ProjectCapability source = resolveCapability(projectId, operation.capabilityId(), Map.of());
        ProjectCapability target = resolveCapability(projectId, operation.mergeIntoCapabilityId(), Map.of());
        if (!safeAutomaticMerge(projectId, source, target)) {
            String reason = "能力名称或证据相似，但不足以安全自动合并，已保留两项长期能力";
            addAttention(projectId, "HIGH_RISK_MERGE", reason, null, source.getId(), target.getId(),
                sha256(projectId + "\nMERGE\n" + source.getId() + "\n" + target.getId()), jobId);
            for (ProjectFact fact : facts) {
                upsertCoverage(projectId, fact, ProjectCapabilityFactClassification.NEEDS_ATTENTION, null, null, reason);
            }
            return;
        }
        int before = target.getCurrentVersion();
        target.addAlias(source.getCanonicalName());
        source.getAliases().forEach(target::addAlias);
        target.enhance(target.getCanonicalName(), fallback(operation.summary(), target.getCurrentSummary()), target.getProblemSolved(),
            target.getLongTermValue(), target.getProductAreas(), occurredAt(facts), target.getModelProvider(), target.getModelName(), jobId, true);
        target = capabilityRepository.save(target);
        String fingerprint = operationFingerprint(projectId, scopeFingerprint, operation, target.getId());
        ProjectCapabilityEvolution evolution = evolutionRepository.findByProjectIdAndOperationFingerprint(projectId, fingerprint).orElse(null);
        if (evolution == null) {
            evolution = new ProjectCapabilityEvolution(
                projectId, target.getId(), ProjectCapabilityEvolutionType.MERGE_CAPABILITY, before, target.getCurrentVersion(),
                fallback(operation.evolutionTitle(), "合并重复能力"), fallback(operation.evolutionSummary(), operation.summary()),
                occurredAt(facts), fingerprint
            );
            evolution.markMergeSource(source.getId());
            evolution.attachSourceStats(facts.size(), distinctBatches(facts), timelinePeriods(facts), jobId, target.getModelProvider(), target.getModelName());
            evolution = evolutionRepository.save(evolution);
        }
        for (ProjectCapabilityFact sourceLink : capabilityFactRepository.findByProjectIdAndCapabilityId(projectId, source.getId())) {
            if (!capabilityFactRepository.existsByCapabilityIdAndFactId(target.getId(), sourceLink.getFactId())) {
                capabilityFactRepository.save(new ProjectCapabilityFact(
                    projectId, target.getId(), sourceLink.getFactId(), ProjectCapabilityRelationRole.EVIDENCE, evolution.getId()
                ));
            }
        }
        linkFacts(projectId, target, evolution, facts, ProjectCapabilityRelationRole.ENHANCEMENT, scopeFingerprint);
        source.markMerged(target.getId());
        capabilityRepository.save(source);
        affected.add(source.getId());
        affected.add(target.getId());
    }

    boolean safeAutomaticMerge(UUID projectId, ProjectCapability source, ProjectCapability target) {
        if (source == null || target == null || source.getId().equals(target.getId())) return false;
        if (source.getStatus() != ProjectCapabilityStatus.ACTIVE || target.getStatus() != ProjectCapabilityStatus.ACTIVE) return false;
        if (source.getLegacyCardId() != null || target.getLegacyCardId() != null) return false;
        if (!normalize(source.getProblemSolved()).equals(normalize(target.getProblemSolved()))) return false;
        if (!new LinkedHashSet<>(source.getProductAreas()).equals(new LinkedHashSet<>(target.getProductAreas()))) return false;
        Set<UUID> sourceFacts = capabilityFactRepository.findByProjectIdAndCapabilityId(projectId, source.getId()).stream()
            .map(ProjectCapabilityFact::getFactId).collect(java.util.stream.Collectors.toSet());
        long overlap = capabilityFactRepository.findByProjectIdAndCapabilityId(projectId, target.getId()).stream()
            .map(ProjectCapabilityFact::getFactId).filter(sourceFacts::contains).count();
        return overlap >= 2;
    }

    private void linkFacts(
        UUID projectId, ProjectCapability capability, ProjectCapabilityEvolution evolution,
        List<ProjectFact> facts, ProjectCapabilityRelationRole role, String sourceFingerprint
    ) {
        for (ProjectFact fact : facts) {
            if (!projectId.equals(fact.getProjectId())) throw new CapabilityCoverageException("Cross-project capability fact is invalid");
            if (!capabilityFactRepository.existsByCapabilityIdAndFactId(capability.getId(), fact.getId())) {
                capabilityFactRepository.save(new ProjectCapabilityFact(projectId, capability.getId(), fact.getId(), role, evolution.getId()));
            }
            upsertCoverage(
                projectId, fact, ProjectCapabilityFactClassification.CONTRIBUTES_TO_CAPABILITY,
                capability.getId(), evolution.getId(), ""
            );
        }
    }

    private void upsertCoverage(
        UUID projectId, ProjectFact fact, ProjectCapabilityFactClassification classification,
        UUID capabilityId, UUID evolutionId, String reason
    ) {
        if (fact == null || !projectId.equals(fact.getProjectId())) throw new CapabilityCoverageException("Unknown or cross-project fact");
        String fingerprint = sha256(fact.getId() + "\n" + fact.getUpdatedAt());
        ProjectCapabilityFactCoverage coverage = coverageRepository.findByProjectIdAndFactId(projectId, fact.getId())
            .orElseGet(() -> new ProjectCapabilityFactCoverage(projectId, fact.getId(), fingerprint, fact.getUpdatedAt()));
        coverage.refreshSource(fingerprint, fact.getUpdatedAt());
        coverage.classify(classification, capabilityId, evolutionId, reason);
        coverageRepository.save(coverage);
    }

    private void recomputeCapability(UUID projectId, UUID capabilityId) {
        ProjectCapability capability = capabilityRepository.findByIdAndProjectId(capabilityId, projectId).orElse(null);
        if (capability == null || capability.getStatus() == ProjectCapabilityStatus.MERGED) return;
        CapabilityFactStatsRow stats = capabilityFactRepository.summarize(projectId, capabilityId, ProjectFactRecordStatus.NEEDS_ATTENTION);
        if (stats == null) return;
        int facts = stats.safeFactCount();
        int batches = stats.safeBatchCount();
        int commits = Math.toIntExact(capabilityFactRepository.countDistinctCommits(projectId, capabilityId));
        int evolutions = Math.toIntExact(evolutionRepository.countByCapabilityId(capabilityId));
        ProjectCapabilityMaturity maturity = maturity(facts, batches, commits, stats.safeEvidenceCount(), stats.safeAttentionCount(), evolutions, stats.earliestAt(), stats.latestAt());
        long days = stats.earliestAt() == null || stats.latestAt() == null ? 0 : Math.max(0, Duration.between(stats.earliestAt(), stats.latestAt()).toDays());
        String reason = "由 " + facts + " 条事实、" + batches + " 个批次、" + commits + " 个提交、"
            + evolutions + " 次演进和 " + days + " 天跨度支持"
            + (stats.safeAttentionCount() > 0 ? "；其中 " + stats.safeAttentionCount() + " 条事实仍需关注" : "");
        capability.updateStatistics(
            facts, batches, commits, stats.safeEvidenceCount(), stats.safeAttentionCount(), evolutions,
            maturity, reason, stats.earliestAt(), stats.latestAt()
        );
        capabilityRepository.save(capability);
    }

    static ProjectCapabilityMaturity maturity(
        int facts, int batches, int commits, int evidence, int attention, int evolutions, Instant earliest, Instant latest
    ) {
        long days = earliest == null || latest == null ? 0 : Math.max(0, Duration.between(earliest, latest).toDays());
        if (facts < 2 || batches < 2) return ProjectCapabilityMaturity.FORMING;
        if (attention == 0 && facts >= 8 && batches >= 3 && commits >= 5 && evidence >= facts * 2 && evolutions >= 4 && days >= 180) {
            return ProjectCapabilityMaturity.LONG_TERM_STABLE;
        }
        if (attention == 0 && evolutions >= 3 && days >= 30) return ProjectCapabilityMaturity.CONTINUOUSLY_ENHANCED;
        return ProjectCapabilityMaturity.FORMED;
    }

    static int plannedChunkCount(int factCount) {
        return factCount <= 0 ? 0 : (factCount + FACT_CHUNK_SIZE - 1) / FACT_CHUNK_SIZE;
    }

    private Map<String, Object> compactFact(ProjectFact fact) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("factId", fact.getId());
        value.put("title", bounded(fact.getTitle(), 180));
        value.put("summary", bounded(fact.getSummary(), 800));
        value.put("userVisibleValue", bounded(fact.getUserVisibleValue(), 500));
        value.put("occurredAt", fact.getTimelineEventAt() == null ? "" : fact.getTimelineEventAt().toString());
        value.put("monthKey", fact.getTimelineMonthKey());
        value.put("recordStatus", fact.getRecordStatus().name());
        value.put("qualityStatus", fact.getQualityStatus());
        value.put("commitCount", fact.getCommitCount());
        value.put("agentResultCount", fact.getAgentResultCount());
        value.put("affectedFileCount", fact.getAffectedFileCount());
        value.put("evidenceCount", fact.getEvidenceCount());
        return value;
    }

    private List<Map<String, Object>> timelineContext(UUID projectId, List<ProjectFact> facts) {
        List<String> months = facts.stream().map(ProjectFact::getTimelineMonthKey).filter(value -> !value.isBlank()).distinct().toList();
        if (months.isEmpty()) return List.of();
        List<ProjectTimelineSummary> summaries = timelineSummaryRepository.findByProjectIdAndGranularityAndPeriodKeyIn(
            projectId, TimelineGranularity.MONTH, months
        );
        List<UUID> summaryIds = summaries.stream().map(ProjectTimelineSummary::getId).toList();
        Map<UUID, List<String>> themes = new HashMap<>();
        if (!summaryIds.isEmpty()) {
            timelineThemeRepository.findBySummaryIdInOrderBySortOrderAsc(summaryIds).forEach(theme ->
                themes.computeIfAbsent(theme.getSummaryId(), ignored -> new ArrayList<>()).add(theme.getTitle())
            );
        }
        return summaries.stream().map(summary -> Map.<String, Object>of(
            "monthKey", summary.getPeriodKey(),
            "status", summary.getStatus().name(),
            "summary", bounded(summary.getSummary(), 600),
            "themes", themes.getOrDefault(summary.getId(), List.of())
        )).toList();
    }

    private String sourceFingerprint(UUID projectId, List<TimelineFactVersionRow> versions) {
        String mergeState = capabilityRepository.findByProjectIdOrderByCreatedAtAsc(projectId).stream()
            .map(capability -> capability.getId() + ":" + capability.getStatus() + ":" + capability.getMergedIntoCapabilityId())
            .reduce((left, right) -> left + "|" + right).orElse("");
        String facts = versions.stream().map(row -> row.id() + ":" + row.updatedAt()).reduce((left, right) -> left + "|" + right).orElse("");
        return sha256(projectId + "\nV" + GENERATION_VERSION + "\n" + facts + "\n" + mergeState);
    }

    private ProjectCapability resolveCapability(UUID projectId, String reference, Map<String, ProjectCapability> temporary) {
        ProjectCapability provisional = temporary.get(reference);
        if (provisional != null) return provisional;
        UUID id;
        try { id = UUID.fromString(reference); }
        catch (RuntimeException exception) { throw new CapabilityCoverageException("Unknown capability reference"); }
        ProjectCapability capability = capabilityRepository.findByIdAndProjectId(id, projectId).orElse(null);
        if (capability == null) throw new CapabilityCoverageException("Unknown or cross-project capability ID");
        return capability;
    }

    private String stableIdentity(UUID projectId, CapabilityOperation operation) {
        String semantic = normalize(operation.problem()) + "\n"
            + operation.areas().stream().map(ProjectCapabilityMapService::normalize).sorted().reduce((a, b) -> a + "|" + b).orElse("")
            + "\n" + normalize(operation.name());
        return sha256(projectId + "\n" + semantic);
    }

    private String operationFingerprint(UUID projectId, String sourceFingerprint, CapabilityOperation operation, UUID capabilityId) {
        String facts = operation.factIds().stream().sorted().reduce((a, b) -> a + "|" + b).orElse("");
        return sha256(projectId + "\n" + sourceFingerprint + "\n" + operation.type() + "\n" + capabilityId + "\n" + facts);
    }

    private void addAttention(
        UUID projectId, String type, String reason, UUID factId, UUID sourceId, UUID targetId,
        String fingerprint, UUID jobId
    ) {
        if (attentionRepository.findByProjectIdAndAttentionFingerprint(projectId, fingerprint).isEmpty()) {
            attentionRepository.save(new ProjectCapabilityAttention(projectId, type, reason, factId, sourceId, targetId, fingerprint, jobId));
        }
    }

    private void markFailed(RefreshScope scope, UUID jobId, String code, String message) {
        transactionTemplate.executeWithoutResult(status -> stateRepository.findLockedByProjectId(scope.projectId()).ifPresent(state -> {
            if (scope.fingerprint().equals(state.getSourceFactFingerprint())) {
                state.markFailed(code, message, jobId);
                stateRepository.save(state);
            }
        }));
    }

    private static List<String> validatedFactIds(JsonNode node, Set<String> allowed, Set<String> assigned) {
        if (!node.isArray()) throw new CapabilityCoverageException("Capability fact classification must be an array");
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isTextual()) throw new CapabilityCoverageException("Capability fact ID must be text");
            String id = item.asText().trim();
            if (!allowed.contains(id)) throw new CapabilityCoverageException("Capability model returned an unknown fact ID");
            if (!assigned.add(id)) throw new CapabilityCoverageException("Capability model classified a fact more than once");
            values.add(id);
        }
        return values;
    }

    private static void rejectProhibited(JsonNode node) {
        if (node == null) return;
        if (node.isObject()) {
            node.fieldNames().forEachRemaining(field -> {
                if (PROHIBITED_FIELDS.contains(field)) throw new CapabilityCoverageException("Capability model returned an authoritative maturity or private field");
            });
            node.elements().forEachRemaining(ProjectCapabilityMapService::rejectProhibited);
        } else if (node.isArray()) {
            node.elements().forEachRemaining(ProjectCapabilityMapService::rejectProhibited);
        } else if (node.isTextual()) {
            String value = node.asText();
            if (PROHIBITED_TEXT.stream().anyMatch(value::contains)) throw new CapabilityCoverageException("Capability model returned planning content");
        }
    }

    private static List<String> strings(JsonNode node) {
        if (!node.isArray()) return List.of();
        List<String> result = new ArrayList<>();
        node.forEach(item -> { if (item.isTextual() && !item.asText().isBlank()) result.add(item.asText().trim()); });
        return List.copyOf(result);
    }
    private static String text(JsonNode node, String field) { return node == null ? "" : node.path(field).asText("").trim(); }
    private static String fallback(String value, String fallback) { return value == null || value.isBlank() ? fallback : value.trim(); }
    private static String normalize(String value) { return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[\\s\\p{Punct}]+", ""); }
    private static String bounded(String value, int max) { String safe = value == null ? "" : value.trim(); return safe.length() <= max ? safe : safe.substring(0, max); }
    private static String safeMessage(Exception exception) { String value = exception.getMessage(); return bounded(value == null || value.isBlank() ? "能力地图刷新失败" : value, 900); }
    private static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 is unavailable", exception); }
    }
    private static Instant occurredAt(List<ProjectFact> facts) {
        return facts.stream().map(fact -> fact.getTimelineEventAt() != null ? fact.getTimelineEventAt() : fact.getOccurredTo())
            .filter(java.util.Objects::nonNull).max(Comparator.naturalOrder()).orElse(Instant.now());
    }
    private static int distinctBatches(List<ProjectFact> facts) {
        return (int) facts.stream().map(ProjectFact::getBatchId).filter(java.util.Objects::nonNull).distinct().count();
    }
    private static List<String> timelinePeriods(List<ProjectFact> facts) {
        return facts.stream().map(ProjectFact::getTimelineMonthKey).filter(value -> !value.isBlank()).distinct().sorted().toList();
    }

    public record CapabilityRefreshOutcome(String resultJson, List<ModelGatewayService.ModelCallDiagnostics> diagnostics, boolean ready) {
    }
    private record Prepared(boolean bootstrap, int sourceFactCount, Instant latestFactAt) {
    }
    private record ApplyResult(int sourceFactCount, int coveredFactCount, long capabilityCount) {
    }
    record ParsedChunk(List<CapabilityOperation> operations, List<FactClassification> classifications) {
    }
    private record CapabilityPlan(List<CapabilityOperation> operations, List<FactClassification> classifications, List<ProjectFact> sourceFacts) {
    }
    record FactClassification(String factId, ProjectCapabilityFactClassification classification, String reason) {
    }
    enum OperationType { NEW_CAPABILITY, ENHANCE_CAPABILITY, ADD_EVIDENCE, MERGE_CAPABILITY }
    record CapabilityOperation(
        OperationType type, String capabilityId, String temporaryKey, String mergeIntoCapabilityId,
        String name, String summary, String problem, String value, List<String> areas, List<String> factIds,
        String evolutionTitle, String evolutionSummary, String readme, String resume, String interview
    ) {
    }
    private record CapabilityContext(String name, String problem, List<String> areas, int factCount) {
        static CapabilityContext from(ProjectCapability capability) {
            return new CapabilityContext(capability.getCanonicalName(), capability.getProblemSolved(), capability.getProductAreas(), capability.getSourceFactCount());
        }
        static CapabilityContext from(CapabilityOperation operation) {
            return new CapabilityContext(operation.name(), operation.problem(), operation.areas(), operation.factIds().size());
        }
        CapabilityContext enhance(CapabilityOperation operation) {
            return new CapabilityContext(
                fallback(operation.name(), name), fallback(operation.problem(), problem),
                operation.areas().isEmpty() ? areas : operation.areas(), factCount + operation.factIds().size()
            );
        }
    }
    private record RefreshScope(UUID projectId, String fingerprint) {
        String encode() { return projectId + "|" + fingerprint; }
        static RefreshScope parse(String value) {
            String[] parts = value == null ? new String[0] : value.split("\\|", -1);
            if (parts.length != 2) throw new IllegalArgumentException("Invalid capability refresh scope");
            return new RefreshScope(UUID.fromString(parts[0]), parts[1]);
        }
    }
    public static class CapabilityCoverageException extends RuntimeException {
        public CapabilityCoverageException(String message) { super(message); }
    }
}
