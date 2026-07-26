package com.projectflow.service;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.function.BiConsumer;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.dto.ProjectUnderstandingDtos.AdaptiveAnalysisPlanResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.AnalysisExecutionResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.ContextPackingDiagnostics;
import com.projectflow.dto.ProjectUnderstandingDtos.DynamicProjectProfileResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.EvidenceSourceMapResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.EvolutionPreviewResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.HistoricalCoverageResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.ProjectStructureIndexResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.ProjectUnderstandingSnapshotResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.RepositoryIntakeResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.SemanticScoutResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.StructureEvidence;
import com.projectflow.dto.ProjectUnderstandingDtos.StructureDelta;
import com.projectflow.dto.ProjectUnderstandingDtos.UnderstandingClaim;
import com.projectflow.dto.ProjectUnderstandingDtos.UnderstandingAnalysisMetrics;
import com.projectflow.dto.ProjectUnderstandingDtos.UnderstandingEvidenceCoverage;
import com.projectflow.dto.ProjectUnderstandingDtos.UnderstandingQuality;
import com.projectflow.dto.ProjectUnderstandingDtos.UnderstandingSection;
import com.projectflow.dto.V2ProjectDtos.ModelCallDiagnosticsResponse;
import com.projectflow.entity.AiProvider;
import com.projectflow.entity.AiProviderType;
import com.projectflow.entity.ProjectMemory;
import com.projectflow.entity.ProjectSpace;
import com.projectflow.entity.ProjectStructureIndex;
import com.projectflow.entity.ProjectUnderstandingSnapshot;
import com.projectflow.repository.AiProviderRepository;
import com.projectflow.repository.ProjectMemoryRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.repository.ProjectStructureIndexRepository;
import com.projectflow.repository.ProjectUnderstandingSnapshotRepository;
import com.projectflow.service.HistoricalCoverageService.HistoricalAnalysis;
import com.projectflow.service.AnalysisExecutionCoordinator.ExecutionOutcome;
import com.projectflow.service.FinalProfileSynthesisService.SynthesisResult;
import com.projectflow.service.ProjectEvidenceDiscoveryService.DiscoveryResult;
import com.projectflow.service.RepositoryIntakeService.ScanResult;
import com.projectflow.service.SemanticScoutService.ScoutResult;
import com.projectflow.support.AppException;

@Service
public class ProjectUnderstandingService {
    private static final String MODEL_ANALYSIS_VERSION = "understanding-v5";

    private final ProjectRepository projectRepository;
    private final ProjectMemoryRepository memoryRepository;
    private final AiProviderRepository providerRepository;
    private final ProjectStructureIndexRepository structureRepository;
    private final ProjectUnderstandingSnapshotRepository understandingRepository;
    private final LocalProjectPathGuard pathGuard;
    private final RepositoryIntakeService intakeService;
    private final ProjectStructureIndexer structureIndexer;
    private final ProjectEvolutionBridgeService evolutionBridgeService;
    private final ProjectEvidenceDiscoveryService evidenceDiscoveryService;
    private final HistoricalCoverageService historicalCoverageService;
    private final AdaptiveAnalysisPlanner analysisPlanner;
    private final SemanticScoutService semanticScoutService;
    private final AnalysisExecutionCoordinator executionCoordinator;
    private final FinalProfileSynthesisService finalSynthesisService;
    private final DynamicProjectProfileSynthesizer profileSynthesizer;
    private final ObjectMapper objectMapper;

    public ProjectUnderstandingService(
        ProjectRepository projectRepository,
        ProjectMemoryRepository memoryRepository,
        AiProviderRepository providerRepository,
        ProjectStructureIndexRepository structureRepository,
        ProjectUnderstandingSnapshotRepository understandingRepository,
        LocalProjectPathGuard pathGuard,
        RepositoryIntakeService intakeService,
        ProjectStructureIndexer structureIndexer,
        ProjectEvolutionBridgeService evolutionBridgeService,
        ProjectEvidenceDiscoveryService evidenceDiscoveryService,
        HistoricalCoverageService historicalCoverageService,
        AdaptiveAnalysisPlanner analysisPlanner,
        SemanticScoutService semanticScoutService,
        AnalysisExecutionCoordinator executionCoordinator,
        FinalProfileSynthesisService finalSynthesisService,
        DynamicProjectProfileSynthesizer profileSynthesizer,
        ObjectMapper objectMapper
    ) {
        this.projectRepository = projectRepository;
        this.memoryRepository = memoryRepository;
        this.providerRepository = providerRepository;
        this.structureRepository = structureRepository;
        this.understandingRepository = understandingRepository;
        this.pathGuard = pathGuard;
        this.intakeService = intakeService;
        this.structureIndexer = structureIndexer;
        this.evolutionBridgeService = evolutionBridgeService;
        this.evidenceDiscoveryService = evidenceDiscoveryService;
        this.historicalCoverageService = historicalCoverageService;
        this.analysisPlanner = analysisPlanner;
        this.semanticScoutService = semanticScoutService;
        this.executionCoordinator = executionCoordinator;
        this.finalSynthesisService = finalSynthesisService;
        this.profileSynthesizer = profileSynthesizer;
        this.objectMapper = objectMapper;
    }

    public RefreshOutcome refresh(UUID userId, UUID projectId) {
        return refresh(userId, projectId, (stage, message) -> { });
    }

    public RefreshOutcome refresh(
        UUID userId,
        UUID projectId,
        BiConsumer<String, String> progress
    ) {
        long totalStarted = System.nanoTime();
        ProjectSpace project = ownedProject(userId, projectId);
        ProjectMemory memory = memoryRepository.findByProjectId(projectId)
            .orElseThrow(() -> new AppException(
                "PROJECT_PATH_REQUIRED",
                "请先在项目接入中绑定本地项目目录",
                HttpStatus.BAD_REQUEST
            ));
        Path root = pathGuard.requireProjectDirectory(memory.getLocalProjectPath()).path();

        AiProvider provider = configuredProvider(userId);
        ProjectUnderstandingSnapshot current = understandingRepository.findByProjectId(projectId).orElse(null);
        boolean semanticUpgradeRequired = current != null
            && provider != null
            && "MODEL_UNAVAILABLE".equals(current.getSemanticStatus());
        boolean cacheCandidate = current != null
            && current.getStructureIndexVersion().equals(CompositeProjectStructureIndexer.INDEX_VERSION)
            && current.getModelAnalysisVersion().equals(MODEL_ANALYSIS_VERSION)
            && "CURRENT".equals(current.getCurrentStatus())
            && !semanticUpgradeRequired;
        if (cacheCandidate) {
            progress.accept("INVENTORY_CHECK", "正在快速检查文件库存是否变化");
            String inventoryFingerprint = intakeService.inventoryFingerprint(root);
            if (current.getStructureHash().equals(inventoryFingerprint)) {
                progress.accept("CACHE_HIT", "项目内容未变化，复用最近一次完整理解");
                return new RefreshOutcome(withCacheHit(readSnapshot(current)), true, false);
            }
        }

        progress.accept("REPOSITORY_INTAKE", "正在盘点目录规模、语言、清单与 Git 可用性");
        long scanStarted = System.nanoTime();
        RepositoryIntakeService.ScanResult scan = intakeService.scan(root);
        long scanTimeMs = elapsedMs(scanStarted);
        ModelCancellationContext.throwIfCancelled();

        progress.accept("STRUCTURE_INDEX", "正在建立可复用的结构索引与证据编号");
        long toolStarted = System.nanoTime();
        ProjectStructureIndex previousIndex = structureRepository.findByProjectId(projectId).orElse(null);
        Map<String, String> previousInventory = readInventory(previousIndex);
        ProjectStructureIndexResponse previousStructure = readStructure(previousIndex);
        ProjectStructureIndexResponse index = withDelta(
            structureIndexer.build(scan),
            calculateDelta(previousInventory, scan.inventorySignatures(), scan.intake().scanTruncated(), previousIndex != null)
        );
        persistStructure(projectId, scan.intake(), index, scan.inventorySignatures());
        ModelCancellationContext.throwIfCancelled();

        progress.accept("EVIDENCE_DISCOVERY", "正在建立来源地图并抽取有界内容信号");
        DiscoveryResult discovery = evidenceDiscoveryService.discover(scan);
        ModelCancellationContext.throwIfCancelled();

        progress.accept("HISTORICAL_COVERAGE", "正在核对可用历史、事实覆盖和里程碑锚点");
        HistoricalAnalysis historical = historicalCoverageService.analyze(
            projectId,
            root,
            scan.intake(),
            discovery.sourceMap()
        );
        ModelCancellationContext.throwIfCancelled();

        progress.accept("EVOLUTION_BRIDGE", "正在用已有事实和真实 Git 提交连接结构演进");
        try {
            evolutionBridgeService.rebuild(
                projectId,
                root,
                scan.intake().git(),
                previousStructure,
                index,
                changedPaths(previousInventory, scan.inventorySignatures())
            );
        } catch (RuntimeException ignored) {
            progress.accept("EVOLUTION_BRIDGE_SKIPPED", "演进桥未更新，当前结构理解仍可继续");
        }
        ModelCancellationContext.throwIfCancelled();
        long toolTimeMs = elapsedMs(toolStarted);

        SemanticScoutResponse deterministicScout = analysisPlanner.deterministicScout(
            scan.intake(),
            discovery.sourceMap()
        );
        long planStarted = System.nanoTime();
        AdaptiveAnalysisPlanResponse plan = analysisPlanner.plan(
            scan.intake(),
            index,
            discovery.sourceMap(),
            historical.coverage(),
            deterministicScout,
            provider != null
        );
        long planTimeMs = elapsedMs(planStarted);
        boolean semanticEligible = provider != null
            && analysisPlanner.shouldUseSemanticModel(scan.intake(), discovery.sourceMap());
        if (!semanticEligible) {
            progress.accept("CAPABILITY_EXECUTION", "正在按分析计划执行固定参数工程能力");
            long executionStarted = System.nanoTime();
            ExecutionOutcome execution = executionCoordinator.execute(
                root,
                scan.intake(),
                index,
                discovery.sourceMap(),
                plan
            );
            toolTimeMs += elapsedMs(executionStarted);
            long synthesisStarted = System.nanoTime();
            DynamicProjectProfileResponse deterministicProfile = profileSynthesizer.synthesize(
                project,
                scan.intake(),
                index,
                execution.sourceMap(),
                historical.coverage(),
                deterministicScout,
                plan,
                null,
                execution.allowedEvidence()
            );
            long deterministicSynthesisMs = elapsedMs(synthesisStarted);
            ProjectUnderstandingSnapshotResponse deterministic = deterministicSnapshot(
                project,
                scan.intake(),
                index,
                plan,
                execution.sourceMap(),
                deterministicScout,
                deterministicProfile,
                historical.coverage(),
                historical.evolutionPreview(),
                execution.response(),
                null
            );
            progress.accept("PERSIST_UNDERSTANDING", "正在保存确定性项目理解");
            UnderstandingAnalysisMetrics metrics = metrics(
                execution.sourceMap(),
                discovery.documentCount(),
                historical,
                index,
                plan,
                execution.response(),
                List.of(),
                scan,
                scanTimeMs,
                0,
                planTimeMs,
                toolTimeMs,
                deterministicSynthesisMs,
                elapsedMs(totalStarted),
                false,
                0
            );
            ProjectUnderstandingSnapshotResponse saved = persistSnapshot(
                projectId,
                withAnalysisMetrics(deterministic, metrics)
            );
            return new RefreshOutcome(saved, false, false);
        }

        long synthesisStarted = System.nanoTime();
        DynamicProjectProfileResponse deterministicProfile = profileSynthesizer.synthesize(
            project,
            scan.intake(),
            index,
            discovery.sourceMap(),
            historical.coverage(),
            deterministicScout,
            plan,
            null,
            Set.of()
        );
        long deterministicSynthesisMs = elapsedMs(synthesisStarted);
        ProjectUnderstandingSnapshotResponse deterministic = deterministicSnapshot(
            project,
            scan.intake(),
            index,
            plan,
            discovery.sourceMap(),
            deterministicScout,
            deterministicProfile,
            historical.coverage(),
            historical.evolutionPreview(),
            null,
            null
        );

        progress.accept("SEMANTIC_SCOUT", "正在对压缩候选做一次语义分诊与项目形态判断");
        try {
            ScoutResult semantic = semanticScoutService.scout(
                provider,
                project,
                scan.intake(),
                index,
                discovery,
                historical.coverage()
            );
            long semanticPlanStarted = System.nanoTime();
            AdaptiveAnalysisPlanResponse semanticPlan = analysisPlanner.plan(
                scan.intake(),
                index,
                discovery.sourceMap(),
                historical.coverage(),
                semantic.scout(),
                true
            );
            planTimeMs += elapsedMs(semanticPlanStarted);
            progress.accept("CAPABILITY_EXECUTION", "正在按 Scout 与 Planner 选择执行固定参数工程能力");
            long executionStarted = System.nanoTime();
            ExecutionOutcome execution = executionCoordinator.execute(
                root,
                scan.intake(),
                index,
                discovery.sourceMap(),
                semanticPlan
            );
            toolTimeMs += elapsedMs(executionStarted);
            JsonNode finalRoot = semantic.root();
            ModelGatewayService.ModelCallDiagnostics finalDiagnostics = semantic.diagnostics();
            ContextPackingDiagnostics contextPacking = semantic.contextPacking();
            boolean invalidEvidenceFiltered = semantic.invalidEvidenceFiltered();
            long finalModelTimeMs = 0;
            int logicalModelRequests = 1;
            String finalSynthesisStatus = execution.highValueEvidenceProduced()
                ? "PENDING"
                : "SKIPPED_NO_HIGH_VALUE_EVIDENCE";
            List<ModelGatewayService.ModelCallDiagnostics> modelDiagnostics = new ArrayList<>();
            modelDiagnostics.add(semantic.diagnostics());
            if (execution.highValueEvidenceProduced() && semanticPlan.maxModelRequests() >= 2) {
                progress.accept("FINAL_SYNTHESIS", "新增工具证据已通过校验，正在进行第二阶段最终归纳");
                logicalModelRequests = 2;
                long finalModelStarted = System.nanoTime();
                try {
                    SynthesisResult finalSynthesis = finalSynthesisService.synthesize(
                        provider,
                        project,
                        scan.intake(),
                        index,
                        historical.coverage(),
                        semantic.scout(),
                        semanticPlan,
                        semantic.root(),
                        execution
                    );
                    finalRoot = finalSynthesis.root();
                    finalDiagnostics = finalSynthesis.diagnostics();
                    contextPacking = finalSynthesis.contextPacking();
                    invalidEvidenceFiltered |= finalSynthesis.invalidEvidenceFiltered();
                    finalModelTimeMs = finalSynthesis.durationMs();
                    modelDiagnostics.add(finalSynthesis.diagnostics());
                    finalSynthesisStatus = "SUCCEEDED";
                } catch (Exception finalException) {
                    finalModelTimeMs = elapsedMs(finalModelStarted);
                    ModelGatewayService.ModelCallDiagnostics failureDiagnostics = failureDiagnostics(finalException);
                    if (failureDiagnostics != null) modelDiagnostics.add(failureDiagnostics);
                    if (finalException instanceof InterruptedException) Thread.interrupted();
                    finalSynthesisStatus = "FAILED_DEGRADED";
                    progress.accept(
                        "FINAL_SYNTHESIS_DEGRADED",
                        "最终归纳失败，已保留第一阶段语义、已校验工具证据和当前降级档案"
                    );
                }
            }
            progress.accept("DYNAMIC_PROFILE", "正在校验证据并生成适用视图与动态项目档案");
            long semanticSynthesisStarted = System.nanoTime();
            DynamicProjectProfileResponse semanticProfile = profileSynthesizer.synthesize(
                project,
                scan.intake(),
                index,
                execution.sourceMap(),
                historical.coverage(),
                semantic.scout(),
                semanticPlan,
                finalRoot,
                execution.allowedEvidence()
            );
            long synthesisTimeMs = deterministicSynthesisMs + finalModelTimeMs + elapsedMs(semanticSynthesisStarted);
            ProjectUnderstandingSnapshotResponse enriched = mergeModel(
                deterministic,
                semantic.scout(),
                semanticPlan,
                semanticProfile,
                index,
                execution.sourceMap(),
                execution.response(),
                contextPacking,
                diagnostics(finalDiagnostics),
                invalidEvidenceFiltered,
                finalSynthesisStatus
            );
            UnderstandingAnalysisMetrics metrics = metrics(
                execution.sourceMap(),
                discovery.documentCount(),
                historical,
                index,
                semanticPlan,
                execution.response(),
                modelDiagnostics,
                scan,
                scanTimeMs,
                semantic.durationMs() + finalModelTimeMs,
                planTimeMs,
                toolTimeMs,
                synthesisTimeMs,
                elapsedMs(totalStarted),
                false,
                logicalModelRequests
            );
            progress.accept("PERSIST_UNDERSTANDING", "正在校验证据并保存当前理解");
            ProjectUnderstandingSnapshotResponse saved = persistSnapshot(
                projectId,
                withAnalysisMetrics(enriched, metrics)
            );
            return new RefreshOutcome(saved, false, true);
        } catch (CancellationException exception) {
            preservePreviousAsStale(current);
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            preservePreviousAsStale(current);
            throw new IllegalStateException("项目理解任务已中断；已保留上一次成功理解", exception);
        } catch (Exception exception) {
            if (current == null) {
                persistSnapshot(projectId, withModelFailure(deterministic));
            } else {
                preservePreviousAsStale(current);
            }
            throw new UnderstandingModelException(
                current == null
                    ? "模型语义归纳失败；结构索引和确定性理解已保存"
                    : "模型语义归纳失败；结构索引已更新，上一次成功理解已保留",
                exception
            );
        }
    }

    public ProjectUnderstandingSnapshotResponse get(UUID userId, UUID projectId) {
        ownedProject(userId, projectId);
        ProjectUnderstandingSnapshot entity = understandingRepository.findByProjectId(projectId)
            .orElseThrow(() -> new AppException(
                "PROJECT_UNDERSTANDING_NOT_FOUND",
                "当前项目还没有理解快照，请先运行项目理解",
                HttpStatus.NOT_FOUND
            ));
        return readSnapshot(entity);
    }

    public ProjectStructureIndexResponse getStructureIndex(UUID userId, UUID projectId) {
        ownedProject(userId, projectId);
        ProjectStructureIndex entity = structureRepository.findByProjectId(projectId)
            .orElseThrow(() -> new AppException(
                "PROJECT_STRUCTURE_INDEX_NOT_FOUND",
                "当前项目还没有结构索引，请先运行项目理解",
                HttpStatus.NOT_FOUND
            ));
        try {
            return objectMapper.readValue(entity.getIndexJson(), ProjectStructureIndexResponse.class);
        } catch (JsonProcessingException exception) {
            throw new AppException(
                "PROJECT_STRUCTURE_INDEX_INVALID",
                "结构索引无法读取，请重新运行项目理解",
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    private ProjectUnderstandingSnapshotResponse deterministicSnapshot(
        ProjectSpace project,
        RepositoryIntakeResponse intake,
        ProjectStructureIndexResponse index,
        AdaptiveAnalysisPlanResponse plan,
        EvidenceSourceMapResponse sourceMap,
        SemanticScoutResponse semanticScout,
        DynamicProjectProfileResponse dynamicProfile,
        HistoricalCoverageResponse historicalCoverage,
        EvolutionPreviewResponse evolutionPreview,
        AnalysisExecutionResponse analysisExecution,
        ContextPackingDiagnostics contextPacking
    ) {
        Instant now = Instant.now();
        List<String> baseRefs = index.evidence().stream().map(StructureEvidence::id).limit(5).toList();
        List<String> intakeRef = List.of("intake:scan");
        String languageSummary = intake.languageDistribution().isEmpty()
            ? "未识别到受支持的源代码语言"
            : intake.languageDistribution().entrySet().stream()
                .limit(5)
                .map(entry -> entry.getKey() + " " + entry.getValue() + " 行")
                .reduce((left, right) -> left + "，" + right)
                .orElse("未识别到受支持的源代码语言");
        UnderstandingSection identity = section(
            project.getName() + " 当前被识别为 " + classificationLabel(intake.classification()) + "。",
            claim("identity-1", "扫描到 " + intake.fileCount() + " 个文件，其中 " + intake.sourceFileCount() + " 个源代码文件。", "OBSERVED", confidence(index), intakeRef)
        );
        UnderstandingSection technology = section(
            languageSummary + "。",
            intake.languageDistribution().isEmpty() ? null
                : claim("technology-1", "当前主要技术信号来自：" + languageSummary + "。", "OBSERVED", confidence(index), intakeRef)
        );
        UnderstandingSection structure = section(
            index.symbols().isEmpty()
                ? "识别到 " + index.modules().size() + " 个一级结构模块和 " + index.entryPoints().size() + " 个候选入口。"
                : "精确索引识别到 " + index.symbols().size() + " 个符号、" + index.definitions().size()
                    + " 个定义、" + index.references().size() + " 个引用和 " + index.functionalAreas().size() + " 个关系区域。",
            index.symbols().isEmpty()
                ? claim("structure-1", "当前只确认目录包含关系，不把文件邻近误报为调用关系。", "OBSERVED", confidence(index), baseRefs)
                : claim("structure-1", "当前代码关系来自 SCIP definition/reference，并由标准图算法排序和聚类。", "OBSERVED", confidence(index), structuralRefs(index, baseRefs))
        );
        UnderstandingSection architecture = section(
            index.functionalAreas().isEmpty()
                ? "当前结构索引未提供可靠代码关系区域，架构细节保持未知。"
                : "已形成 " + index.functionalAreas().size() + " 个由代码关系支持的结构区域；用户可读架构语义仍需有界归纳。",
            index.functionalAreas().isEmpty() ? null
                : claim(
                    "architecture-1",
                    "结构区域成员由 definition/reference 关系形成，不由 frontend、backend 等目录名直接决定。",
                    "OBSERVED",
                    confidence(index),
                    index.functionalAreas().stream().flatMap(item -> item.evidenceRefs().stream()).distinct().limit(12).toList()
                )
        );
        UnderstandingSection capabilities = section(
            index.functionalAreas().isEmpty()
                ? "尚未基于源码关系确认稳定业务能力；需要模型语义归纳或后续语义索引补充。"
                : "已准备关系区域、关键符号与入口证据；稳定业务能力名称仍只允许在证据约束下推断。",
            null
        );
        List<String> engineeringKinds = index.engineeringSignals().entrySet().stream()
            .filter(entry -> !entry.getValue().isEmpty())
            .map(Map.Entry::getKey)
            .toList();
        UnderstandingSection engineering = section(
            engineeringKinds.isEmpty()
                ? "未发现明确的测试、持续集成、质量或部署文件信号。"
                : "发现工程化信号：" + String.join("、", engineeringKinds) + "。",
            engineeringKinds.isEmpty() ? null
                : claim("engineering-1", "目录中存在 " + String.join("、", engineeringKinds) + " 相关文件。", "OBSERVED", confidence(index), baseRefs)
        );
        List<String> unknowns = new ArrayList<>(plan.unavailableCapabilities());
        unknowns.addAll(dynamicProfile.unknowns());
        if (intake.scanTruncated()) unknowns.add("扫描达到安全上限，未覆盖的目录内容保持未知");
        String semanticStatus = switch (plan.semanticMode()) {
            case "SKIPPED_EMPTY", "SKIPPED_NO_SUBSTANTIVE_EVIDENCE" -> "NOT_APPLICABLE";
            case "UNAVAILABLE" -> "MODEL_UNAVAILABLE";
            default -> "PENDING";
        };
        List<UnderstandingClaim> allClaims = claims(identity, technology, structure, architecture, capabilities, engineering);
        allClaims.addAll(profileClaims(dynamicProfile));
        UnderstandingEvidenceCoverage evidenceCoverage = coverage(allClaims, intake, index);
        UnderstandingQuality quality = new UnderstandingQuality(
            semanticStatus,
            confidence(index),
            false,
            false,
            List.copyOf(unknowns)
        );
        return new ProjectUnderstandingSnapshotResponse(
            null,
            project.getId(),
            intake.classification(),
            intake.scale(),
            identity,
            technology,
            structure,
            architecture,
            capabilities,
            engineering,
            evidenceCoverage,
            quality,
            List.copyOf(unknowns),
            intake,
            plan,
            now,
            intake.sourceRevision(),
            index.indexVersion(),
            MODEL_ANALYSIS_VERSION,
            "CURRENT",
            null,
            sourceMap,
            semanticScout,
            dynamicProfile,
            historicalCoverage,
            evolutionPreview,
            analysisExecution,
            contextPacking,
            null,
            "NOT_APPLICABLE"
        );
    }

    private ProjectUnderstandingSnapshotResponse mergeModel(
        ProjectUnderstandingSnapshotResponse base,
        SemanticScoutResponse semanticScout,
        AdaptiveAnalysisPlanResponse plan,
        DynamicProjectProfileResponse dynamicProfile,
        ProjectStructureIndexResponse index,
        EvidenceSourceMapResponse sourceMap,
        AnalysisExecutionResponse analysisExecution,
        ContextPackingDiagnostics contextPacking,
        ModelCallDiagnosticsResponse diagnostics,
        boolean invalidEvidenceFiltered,
        String finalSynthesisStatus
    ) {
        LinkedHashSet<String> mergedUnknowns = new LinkedHashSet<>(base.unknowns());
        mergedUnknowns.addAll(semanticScout.unknowns());
        mergedUnknowns.addAll(semanticScout.potentialConflicts());
        mergedUnknowns.addAll(semanticScout.currentnessWarnings());
        mergedUnknowns.addAll(dynamicProfile.unknowns());
        List<String> unknowns = List.copyOf(mergedUnknowns);
        UnderstandingSection identity = legacySection(
            dynamicProfile,
            List.of("IDENTITY", "CURRENT_STATE", "DOCUMENT_OVERVIEW"),
            base.identity()
        );
        UnderstandingSection technology = legacySection(
            dynamicProfile,
            List.of("TECHNOLOGY", "DEPENDENCIES"),
            base.technology()
        );
        UnderstandingSection structure = legacySection(
            dynamicProfile,
            List.of("CURRENT_STRUCTURE", "STRUCTURE", "ROUTES", "COMPONENTS"),
            base.structure()
        );
        UnderstandingSection architecture = legacySection(
            dynamicProfile,
            List.of("ARCHITECTURE"),
            base.architecture()
        );
        UnderstandingSection capabilities = legacySection(
            dynamicProfile,
            List.of("CAPABILITIES", "USER_CAPABILITIES", "PURPOSE"),
            base.capabilities()
        );
        UnderstandingSection engineering = legacySection(
            dynamicProfile,
            List.of("ENGINEERING_STATE", "BUILD_TEST_DEPLOY"),
            base.engineeringState()
        );
        List<UnderstandingClaim> allClaims = claims(
            identity,
            technology,
            structure,
            architecture,
            capabilities,
            engineering
        );
        allClaims.addAll(profileClaims(dynamicProfile));
        UnderstandingQuality quality = new UnderstandingQuality(
            "SUCCEEDED",
            allClaims.isEmpty() ? "LOW" : confidence(index),
            true,
            false,
            mergeLimitations(
                base.quality().limitations(),
                invalidEvidenceFiltered ? "模型返回的未知证据引用已过滤" : "",
                "FAILED_DEGRADED".equals(finalSynthesisStatus)
                    ? "最终归纳失败；当前结果保留第一阶段语义与已校验工具证据"
                    : ""
            )
        );
        return new ProjectUnderstandingSnapshotResponse(
            null,
            base.projectId(),
            base.classification(),
            base.scale(),
            identity,
            technology,
            structure,
            architecture,
            capabilities,
            engineering,
            coverage(allClaims, base.intake(), index),
            quality,
            unknowns,
            base.intake(),
            plan,
            Instant.now(),
            base.sourceRevision(),
            base.structureIndexVersion(),
            base.modelAnalysisVersion(),
            "CURRENT",
            diagnostics,
            sourceMap,
            semanticScout,
            dynamicProfile,
            base.historicalCoverage(),
            base.evolutionPreview(),
            analysisExecution,
            contextPacking,
            base.analysisMetrics(),
            finalSynthesisStatus
        );
    }

    private void persistStructure(
        UUID projectId,
        RepositoryIntakeResponse intake,
        ProjectStructureIndexResponse index,
        Map<String, String> inventory
    ) {
        try {
            ProjectStructureIndex entity = structureRepository.findByProjectId(projectId)
                .orElseGet(() -> new ProjectStructureIndex(projectId));
            entity.replace(
                intake.sourceRevision(),
                intake.contentHash(),
                index.indexVersion(),
                index.indexerSource(),
                objectMapper.writeValueAsString(intake),
                objectMapper.writeValueAsString(index),
                objectMapper.writeValueAsString(inventory)
            );
            structureRepository.save(entity);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("结构索引序列化失败", exception);
        }
    }

    private Map<String, String> readInventory(ProjectStructureIndex entity) {
        if (entity == null || entity.getInventoryJson() == null || entity.getInventoryJson().isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(
                entity.getInventoryJson(),
                objectMapper.getTypeFactory().constructMapType(Map.class, String.class, String.class)
            );
        } catch (JsonProcessingException exception) {
            return Map.of();
        }
    }

    private ProjectStructureIndexResponse readStructure(ProjectStructureIndex entity) {
        if (entity == null || entity.getIndexJson() == null || entity.getIndexJson().isBlank()) return null;
        try {
            return objectMapper.readValue(entity.getIndexJson(), ProjectStructureIndexResponse.class);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private static Set<String> changedPaths(
        Map<String, String> previous,
        Map<String, String> current
    ) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        current.entrySet().stream()
            .filter(entry -> !entry.getValue().equals(previous.get(entry.getKey())))
            .map(Map.Entry::getKey)
            .sorted()
            .limit(10_000)
            .forEach(result::add);
        previous.keySet().stream()
            .filter(path -> !current.containsKey(path))
            .sorted()
            .limit(Math.max(0, 10_000 - result.size()))
            .forEach(result::add);
        return result;
    }

    private static StructureDelta calculateDelta(
        Map<String, String> previous,
        Map<String, String> current,
        boolean truncated,
        boolean hadPreviousIndex
    ) {
        long added = 0;
        long modified = 0;
        long unchanged = 0;
        for (Map.Entry<String, String> entry : current.entrySet()) {
            String old = previous.get(entry.getKey());
            if (old == null) added++;
            else if (old.equals(entry.getValue())) unchanged++;
            else modified++;
        }
        long removed = previous.keySet().stream().filter(path -> !current.containsKey(path)).count();
        String mode = !hadPreviousIndex
            ? "INITIAL"
            : previous.isEmpty() ? "FULL_REBUILD_NO_INVENTORY" : "INCREMENTAL_DIRTY_SET";
        return new StructureDelta(mode, added, modified, removed, unchanged, !truncated);
    }

    private static ProjectStructureIndexResponse withDelta(
        ProjectStructureIndexResponse value,
        StructureDelta delta
    ) {
        return new ProjectStructureIndexResponse(
            value.indexVersion(),
            value.indexerSource(),
            value.sourceRevision(),
            value.contentHash(),
            value.cacheHit(),
            value.indexedFileCount(),
            value.files(),
            value.fileSampleTruncated(),
            value.modules(),
            value.relations(),
            value.entryPoints(),
            value.manifests(),
            value.engineeringSignals(),
            value.evidence(),
            value.coverage(),
            value.provenance(),
            value.unsupportedAreas(),
            value.symbols(),
            value.definitions(),
            value.references(),
            value.importantNodes(),
            value.functionalAreas(),
            value.providerDiagnostics(),
            value.metrics(),
            delta,
            value.indexedAt()
        );
    }

    private ProjectUnderstandingSnapshotResponse persistSnapshot(
        UUID projectId,
        ProjectUnderstandingSnapshotResponse response
    ) {
        try {
            ProjectUnderstandingSnapshot entity = understandingRepository.findByProjectId(projectId)
                .orElseGet(() -> new ProjectUnderstandingSnapshot(projectId));
            ProjectUnderstandingSnapshotResponse withId = withIdentity(response, entity.getId(), "CURRENT");
            entity.replace(
                response.sourceRevision(),
                response.intake().contentHash(),
                response.structureIndexVersion(),
                response.modelAnalysisVersion(),
                response.quality().semanticStatus(),
                objectMapper.writeValueAsString(withId),
                response.analyzedAt()
            );
            understandingRepository.save(entity);
            return withId;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("项目理解快照序列化失败", exception);
        }
    }

    private ProjectUnderstandingSnapshotResponse readSnapshot(ProjectUnderstandingSnapshot entity) {
        try {
            ProjectUnderstandingSnapshotResponse stored = objectMapper.readValue(
                entity.getSnapshotJson(),
                ProjectUnderstandingSnapshotResponse.class
            );
            return withIdentity(stored, entity.getId(), entity.getCurrentStatus());
        } catch (JsonProcessingException exception) {
            throw new AppException(
                "PROJECT_UNDERSTANDING_INVALID",
                "项目理解快照无法读取，请重新运行",
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    private void preservePreviousAsStale(ProjectUnderstandingSnapshot previous) {
        if (previous != null) {
            previous.markStale();
            understandingRepository.save(previous);
        }
    }

    private ProjectSpace ownedProject(UUID userId, UUID projectId) {
        return projectRepository.findByIdAndUserId(projectId, userId)
            .orElseThrow(() -> new AppException("PROJECT_NOT_FOUND", "项目不存在", HttpStatus.NOT_FOUND));
    }

    private AiProvider configuredProvider(UUID userId) {
        return providerRepository.findByUserIdOrderByDefaultEnabledDescUpdatedAtDesc(userId).stream()
            .filter(provider -> provider.getType() != AiProviderType.MOCK)
            .filter(AiProvider::isDefaultEnabled)
            .filter(provider -> provider.getApiKey() != null && !provider.getApiKey().isBlank())
            .findFirst()
            .orElse(null);
    }

    private static UnderstandingSection section(String summary, UnderstandingClaim claim) {
        return new UnderstandingSection(summary, claim == null ? List.of() : List.of(claim));
    }

    private static UnderstandingClaim claim(
        String id,
        String text,
        String status,
        String confidence,
        List<String> refs
    ) {
        return new UnderstandingClaim(id, text, status, confidence, refs == null ? List.of() : refs);
    }

    private static List<UnderstandingClaim> claims(UnderstandingSection... sections) {
        List<UnderstandingClaim> result = new ArrayList<>();
        for (UnderstandingSection section : sections) {
            if (section != null && section.claims() != null) result.addAll(section.claims());
        }
        return result;
    }

    private static UnderstandingEvidenceCoverage coverage(
        Collection<UnderstandingClaim> claims,
        RepositoryIntakeResponse intake,
        ProjectStructureIndexResponse index
    ) {
        int observed = 0;
        int inferred = 0;
        int explained = 0;
        int bound = 0;
        for (UnderstandingClaim claim : claims) {
            if ("OBSERVED".equals(claim.epistemicStatus())) observed++;
            else if ("EXPLAINED".equals(claim.epistemicStatus())) explained++;
            else inferred++;
            if (claim.evidenceRefs() != null && !claim.evidenceRefs().isEmpty()) bound++;
        }
        return new UnderstandingEvidenceCoverage(
            observed,
            inferred,
            explained,
            bound,
            intake.supportedStructureCoverage(),
            index.coverage().overall(),
            index.provenance()
        );
    }

    private static UnderstandingSection legacySection(
        DynamicProjectProfileResponse profile,
        List<String> typeMarkers,
        UnderstandingSection fallback
    ) {
        if (profile == null || profile.sections() == null) return fallback;
        return profile.sections().stream()
            .filter(section -> typeMarkers.stream().anyMatch(marker -> section.type().contains(marker)))
            .findFirst()
            .map(section -> new UnderstandingSection(section.summary(), section.claims()))
            .orElse(fallback);
    }

    private static List<UnderstandingClaim> profileClaims(DynamicProjectProfileResponse profile) {
        if (profile == null || profile.sections() == null) return List.of();
        return profile.sections().stream()
            .flatMap(section -> section.claims().stream())
            .toList();
    }

    private static List<String> mergeLimitations(List<String> base, String... extras) {
        LinkedHashSet<String> values = new LinkedHashSet<>(base == null ? List.of() : base);
        if (extras != null) {
            for (String extra : extras) {
                if (extra != null && !extra.isBlank()) values.add(extra);
            }
        }
        return List.copyOf(values);
    }

    private static String confidence(ProjectStructureIndexResponse index) {
        if (index.coverage().overall() >= 0.8) return "HIGH";
        if (index.coverage().overall() >= 0.5) return "MEDIUM";
        return "LOW";
    }

    private static List<String> structuralRefs(
        ProjectStructureIndexResponse index,
        List<String> fallback
    ) {
        List<String> refs = index.functionalAreas().stream()
            .flatMap(item -> item.evidenceRefs().stream())
            .distinct()
            .limit(12)
            .toList();
        return refs.isEmpty() ? fallback : refs;
    }

    private static String classificationLabel(String value) {
        return switch (value) {
            case "EMPTY" -> "空目录";
            case "UNKNOWN_NON_CODE" -> "暂未识别代码的目录";
            case "CODE_NO_GIT" -> "无 Git 的代码项目";
            case "HUGE_MONOREPO" -> "超大或多工作区项目";
            default -> value.toLowerCase(Locale.ROOT) + " 规模代码项目";
        };
    }

    private static ModelCallDiagnosticsResponse diagnostics(ModelGatewayService.ModelCallDiagnostics value) {
        if (value == null) return null;
        return new ModelCallDiagnosticsResponse(
            value.providerName(), value.modelName(), value.finishReason(), value.promptTokens(), value.completionTokens(),
            value.totalTokens(), value.usageSource(), value.providerMaxTokens(), value.taskPolicyMaxTokens(), value.effectiveMaxTokens(),
            value.providerTemperature(), value.effectiveTemperature(), value.timeoutSeconds(), value.latencyMs(),
            value.contentPresent(), value.reasoningPresent(), value.reasoningLength(), value.truncated(),
            value.compactRetryAttempted(), value.compactRetrySucceeded(), value.requestCount(), value.jsonRepaired(),
            value.partialResult(), value.recoveredItems(), value.entryPoint(), value.taskType(), value.capabilityProfile(),
            value.inputSize(), value.promptSize(), value.recommendedTemperature(), value.temperatureSent(),
            value.temperatureDecision(), value.maxTokenDecision(), value.retryType(), value.reasoningBudgetExhausted(),
            value.schemaMatched(), value.failureStage(), value.failureCode()
        );
    }

    private static ModelGatewayService.ModelCallDiagnostics failureDiagnostics(Throwable value) {
        Throwable current = value;
        while (current != null) {
            if (current instanceof ModelGatewayService.ModelResponseFormatException formatException) {
                return formatException.diagnostics();
            }
            current = current.getCause();
        }
        return null;
    }

    private static ProjectUnderstandingSnapshotResponse withCacheHit(ProjectUnderstandingSnapshotResponse value) {
        UnderstandingQuality quality = new UnderstandingQuality(
            value.quality().semanticStatus(),
            value.quality().confidence(),
            value.quality().modelUsed(),
            true,
            value.quality().limitations()
        );
        ProjectUnderstandingSnapshotResponse cached = copy(value, value.id(), value.currentStatus(), quality);
        UnderstandingAnalysisMetrics metrics = value.analysisMetrics();
        if (metrics == null) return cached;
        return withAnalysisMetrics(cached, new UnderstandingAnalysisMetrics(
            metrics.discoveredEvidenceCount(), metrics.candidateEvidenceCount(), metrics.scoutEvidenceCount(),
            metrics.deepReadCount(), metrics.skippedCount(), 0, 0, 0, 0, 0,
            metrics.files(), metrics.loc(), metrics.docs(), metrics.commits(), metrics.tags(),
            0, 0, 0, 0, 0, 0, true, metrics.historicalCoverage(), metrics.structureCoverage(),
            0, 0, metrics.sampleCacheHits()
        ));
    }

    private static ProjectUnderstandingSnapshotResponse withModelFailure(ProjectUnderstandingSnapshotResponse value) {
        UnderstandingQuality quality = new UnderstandingQuality(
            "MODEL_FAILED",
            value.quality().confidence(),
            false,
            false,
            mergeLimitations(value.quality().limitations(), "模型语义归纳失败，当前仅保留确定性结构理解")
        );
        return copy(value, value.id(), value.currentStatus(), quality);
    }

    private static ProjectUnderstandingSnapshotResponse withIdentity(
        ProjectUnderstandingSnapshotResponse value,
        UUID id,
        String currentStatus
    ) {
        return copy(value, id, currentStatus, value.quality());
    }

    private static ProjectUnderstandingSnapshotResponse copy(
        ProjectUnderstandingSnapshotResponse value,
        UUID id,
        String currentStatus,
        UnderstandingQuality quality
    ) {
        return new ProjectUnderstandingSnapshotResponse(
            id, value.projectId(), value.classification(), value.scale(), value.identity(), value.technology(),
            value.structure(), value.architecture(), value.capabilities(), value.engineeringState(),
            value.evidenceCoverage(), quality, value.unknowns(), value.intake(), value.analysisPlan(),
            value.analyzedAt(), value.sourceRevision(), value.structureIndexVersion(), value.modelAnalysisVersion(),
            currentStatus, value.diagnostics(), value.sourceMap(), value.semanticScout(), value.dynamicProfile(),
            value.historicalCoverage(), value.evolutionPreview(), value.analysisExecution(), value.contextPacking(),
            value.analysisMetrics(), value.finalSynthesisStatus()
        );
    }

    private static ProjectUnderstandingSnapshotResponse withAnalysisMetrics(
        ProjectUnderstandingSnapshotResponse value,
        UnderstandingAnalysisMetrics metrics
    ) {
        return new ProjectUnderstandingSnapshotResponse(
            value.id(), value.projectId(), value.classification(), value.scale(), value.identity(), value.technology(),
            value.structure(), value.architecture(), value.capabilities(), value.engineeringState(),
            value.evidenceCoverage(), value.quality(), value.unknowns(), value.intake(), value.analysisPlan(),
            value.analyzedAt(), value.sourceRevision(), value.structureIndexVersion(), value.modelAnalysisVersion(),
            value.currentStatus(), value.diagnostics(), value.sourceMap(), value.semanticScout(), value.dynamicProfile(),
            value.historicalCoverage(), value.evolutionPreview(), value.analysisExecution(), value.contextPacking(), metrics,
            value.finalSynthesisStatus()
        );
    }

    private static UnderstandingAnalysisMetrics metrics(
        EvidenceSourceMapResponse sourceMap,
        long documentCount,
        HistoricalAnalysis historical,
        ProjectStructureIndexResponse index,
        AdaptiveAnalysisPlanResponse plan,
        AnalysisExecutionResponse execution,
        List<ModelGatewayService.ModelCallDiagnostics> diagnostics,
        ScanResult scan,
        long scanTimeMs,
        long scoutTimeMs,
        long planTimeMs,
        long toolTimeMs,
        long synthesisTimeMs,
        long totalTimeMs,
        boolean cacheHit,
        int logicalModelRequests
    ) {
        List<ModelGatewayService.ModelCallDiagnostics> calls = diagnostics == null
            ? List.of()
            : diagnostics.stream().filter(java.util.Objects::nonNull).toList();
        return new UnderstandingAnalysisMetrics(
            sourceMap.discoveredEvidenceCount(),
            sourceMap.candidateEvidenceCount(),
            sourceMap.scoutEvidenceCount(),
            sourceMap.deepReadCount(),
            sourceMap.skippedCount(),
            execution == null ? 0 : execution.executedCapabilities().size(),
            Math.max(logicalModelRequests, calls.stream().mapToInt(value -> Math.max(1, value.requestCount())).sum()),
            calls.stream().mapToInt(ModelGatewayService.ModelCallDiagnostics::promptTokens).sum(),
            calls.stream().mapToInt(ModelGatewayService.ModelCallDiagnostics::completionTokens).sum(),
            calls.stream().mapToInt(ModelGatewayService.ModelCallDiagnostics::totalTokens).sum(),
            index.metrics().fileCount(),
            index.metrics().estimatedLoc(),
            documentCount,
            historical.coverage().gitCommitCount(),
            historical.coverage().tagCount(),
            scanTimeMs,
            scoutTimeMs,
            planTimeMs,
            toolTimeMs,
            synthesisTimeMs,
            totalTimeMs,
            cacheHit,
            historical.coverage().overallCoverage(),
            index.coverage().overall(),
            scan.ioMetrics().filesRead(),
            scan.ioMetrics().cacheHits(),
            sourceMap.diversityMetrics() == null ? 0 : sourceMap.diversityMetrics().sampleCacheHitCount()
        );
    }

    private static long elapsedMs(long started) {
        return Math.max(0, (System.nanoTime() - started) / 1_000_000);
    }

    public record RefreshOutcome(
        ProjectUnderstandingSnapshotResponse snapshot,
        boolean cacheHit,
        boolean modelUsed
    ) {
    }

    public static final class UnderstandingModelException extends RuntimeException {
        public UnderstandingModelException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
