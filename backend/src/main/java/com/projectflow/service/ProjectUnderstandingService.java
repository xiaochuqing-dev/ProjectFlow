package com.projectflow.service;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.dto.ProjectUnderstandingDtos.AdaptiveAnalysisPlanResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.ProjectStructureIndexResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.ProjectUnderstandingSnapshotResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.RepositoryIntakeResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.StructureEvidence;
import com.projectflow.dto.ProjectUnderstandingDtos.StructureDelta;
import com.projectflow.dto.ProjectUnderstandingDtos.UnderstandingClaim;
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
import com.projectflow.support.AppException;

@Service
public class ProjectUnderstandingService {
    private static final String MODEL_ANALYSIS_VERSION = "understanding-v1";
    private static final List<String> SECTION_NAMES = List.of(
        "identity", "technology", "structure", "architecture", "capabilities", "engineeringState"
    );

    private final ProjectRepository projectRepository;
    private final ProjectMemoryRepository memoryRepository;
    private final AiProviderRepository providerRepository;
    private final ProjectStructureIndexRepository structureRepository;
    private final ProjectUnderstandingSnapshotRepository understandingRepository;
    private final LocalProjectPathGuard pathGuard;
    private final RepositoryIntakeService intakeService;
    private final ProjectStructureIndexer structureIndexer;
    private final ModelGatewayService modelGateway;
    private final ObjectMapper objectMapper;

    @Value("${projectflow.understanding.max-model-prompt-chars:48000}")
    private int maxModelPromptChars;

    public ProjectUnderstandingService(
        ProjectRepository projectRepository,
        ProjectMemoryRepository memoryRepository,
        AiProviderRepository providerRepository,
        ProjectStructureIndexRepository structureRepository,
        ProjectUnderstandingSnapshotRepository understandingRepository,
        LocalProjectPathGuard pathGuard,
        RepositoryIntakeService intakeService,
        ProjectStructureIndexer structureIndexer,
        ModelGatewayService modelGateway,
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
        this.modelGateway = modelGateway;
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
            && current.getStructureIndexVersion().equals(ManifestFilesystemProjectStructureIndexer.INDEX_VERSION)
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
        RepositoryIntakeService.ScanResult scan = intakeService.scan(root);
        ModelCancellationContext.throwIfCancelled();

        progress.accept("STRUCTURE_INDEX", "正在建立可复用的结构索引与证据编号");
        ProjectStructureIndex previousIndex = structureRepository.findByProjectId(projectId).orElse(null);
        ProjectStructureIndexResponse index = withDelta(
            structureIndexer.build(scan),
            calculateDelta(readInventory(previousIndex), scan.inventorySignatures(), scan.intake().scanTruncated(), previousIndex != null)
        );
        persistStructure(projectId, scan.intake(), index, scan.inventorySignatures());
        ModelCancellationContext.throwIfCancelled();

        AdaptiveAnalysisPlanResponse plan = plan(scan.intake(), index, provider != null);
        ProjectUnderstandingSnapshotResponse deterministic = deterministicSnapshot(project, scan.intake(), index, plan);

        boolean semanticEligible = provider != null
            && scan.intake().sourceFileCount() > 0
            && !"UNKNOWN_NON_CODE".equals(scan.intake().classification());
        if (!semanticEligible) {
            progress.accept("PERSIST_UNDERSTANDING", "正在保存确定性项目理解");
            ProjectUnderstandingSnapshotResponse saved = persistSnapshot(projectId, deterministic);
            return new RefreshOutcome(saved, false, false);
        }

        progress.accept("UNDERSTANDING_MODEL", "正在对有界结构证据进行一次语义归纳");
        try {
            String prompt = buildPrompt(project, scan.intake(), index);
            ModelGatewayService.StructuredModelResponse response = modelGateway.callStructured(
                provider,
                prompt,
                ModelTaskType.PROJECT_UNDERSTANDING_SNAPSHOT
            );
            ProjectUnderstandingSnapshotResponse enriched = mergeModel(
                deterministic,
                response.parsed().root(),
                index,
                diagnostics(response.diagnostics())
            );
            progress.accept("PERSIST_UNDERSTANDING", "正在校验证据并保存当前理解");
            ProjectUnderstandingSnapshotResponse saved = persistSnapshot(projectId, enriched);
            return new RefreshOutcome(saved, false, true);
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

    private AdaptiveAnalysisPlanResponse plan(
        RepositoryIntakeResponse intake,
        ProjectStructureIndexResponse index,
        boolean providerConfigured
    ) {
        boolean semantic = providerConfigured && intake.sourceFileCount() > 0
            && !"UNKNOWN_NON_CODE".equals(intake.classification());
        boolean hierarchical = "LARGE".equals(intake.scale())
            || "HUGE".equals(intake.scale())
            || "MONOREPO".equals(intake.scale())
            || "HUGE_MONOREPO".equals(intake.classification());
        List<String> unavailable = new ArrayList<>(index.unsupportedAreas());
        if (!intake.git().available()) unavailable.add("没有 Git 历史，无法给出能力演进时间线");
        if (!providerConfigured) unavailable.add("没有可用默认模型，语义能力与架构判断保持未知");
        String semanticMode;
        if ("EMPTY".equals(intake.classification())) semanticMode = "SKIPPED_EMPTY";
        else if ("UNKNOWN_NON_CODE".equals(intake.classification())) semanticMode = "SKIPPED_NON_CODE";
        else if (!providerConfigured) semanticMode = "UNAVAILABLE";
        else semanticMode = "ONE_PASS_BOUNDED";
        List<String> reasons = new ArrayList<>();
        reasons.add("先复用确定性结构索引，再决定是否调用模型");
        reasons.add(hierarchical ? "规模较大，模型只接收压缩后的模块级证据" : "规模可控，模型接收有界结构证据");
        if (!intake.git().available()) reasons.add("非 Git 项目仅理解当前状态，不伪造历史");
        return new AdaptiveAnalysisPlanResponse(
            List.of("目录与文件盘点", "语言与 LOC 统计", "manifest/workspace 识别", "工程化信号识别", "证据编号"),
            "MANIFEST_FILESYSTEM",
            semanticMode,
            semantic ? 3 : 0,
            semantic ? 12_000 : 0,
            semantic ? 40_000 : 0,
            semantic ? 600_000L : 120_000L,
            hierarchical,
            intake.git().available() ? "CURRENT_GIT_STATE_ONLY" : "UNAVAILABLE",
            index.coverage().overall(),
            List.copyOf(unavailable),
            List.copyOf(reasons)
        );
    }

    private ProjectUnderstandingSnapshotResponse deterministicSnapshot(
        ProjectSpace project,
        RepositoryIntakeResponse intake,
        ProjectStructureIndexResponse index,
        AdaptiveAnalysisPlanResponse plan
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
            "识别到 " + index.modules().size() + " 个一级结构模块和 " + index.entryPoints().size() + " 个候选入口。",
            claim("structure-1", "当前只确认目录包含关系，不把文件邻近误报为调用关系。", "OBSERVED", confidence(index), baseRefs)
        );
        UnderstandingSection architecture = section(
            "当前结构索引未提供可靠调用图、继承图和运行时边界，架构细节保持未知。",
            null
        );
        UnderstandingSection capabilities = section(
            "尚未基于源码证据确认稳定业务能力；需要模型语义归纳或后续语义索引补充。",
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
        if (intake.scanTruncated()) unknowns.add("扫描达到安全上限，未覆盖的目录内容保持未知");
        String semanticStatus = switch (plan.semanticMode()) {
            case "SKIPPED_EMPTY", "SKIPPED_NON_CODE" -> "NOT_APPLICABLE";
            case "UNAVAILABLE" -> "MODEL_UNAVAILABLE";
            default -> "PENDING";
        };
        List<UnderstandingClaim> allClaims = claims(identity, technology, structure, architecture, capabilities, engineering);
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
            null
        );
    }

    private ProjectUnderstandingSnapshotResponse mergeModel(
        ProjectUnderstandingSnapshotResponse base,
        JsonNode root,
        ProjectStructureIndexResponse index,
        ModelCallDiagnosticsResponse diagnostics
    ) {
        Set<String> allowedEvidence = new LinkedHashSet<>();
        index.evidence().forEach(item -> allowedEvidence.add(item.id()));
        Map<String, UnderstandingSection> sections = new LinkedHashMap<>();
        for (String name : SECTION_NAMES) {
            sections.put(name, parseSection(name, root.path(name), allowedEvidence));
        }
        LinkedHashSet<String> mergedUnknowns = new LinkedHashSet<>(base.unknowns());
        mergedUnknowns.addAll(boundedTexts(root.path("unknowns"), 20, 300));
        List<String> unknowns = List.copyOf(mergedUnknowns);
        UnderstandingSection identity = prefer(sections.get("identity"), base.identity());
        UnderstandingSection technology = prefer(sections.get("technology"), base.technology());
        UnderstandingSection structure = prefer(sections.get("structure"), base.structure());
        UnderstandingSection architecture = prefer(sections.get("architecture"), base.architecture());
        UnderstandingSection capabilities = prefer(sections.get("capabilities"), base.capabilities());
        UnderstandingSection engineering = prefer(sections.get("engineeringState"), base.engineeringState());
        List<UnderstandingClaim> allClaims = claims(
            identity,
            technology,
            structure,
            architecture,
            capabilities,
            engineering
        );
        UnderstandingQuality quality = new UnderstandingQuality(
            "SUCCEEDED",
            allClaims.isEmpty() ? "LOW" : confidence(index),
            true,
            false,
            mergeLimitations(base.quality().limitations(), invalidEvidenceLimitation(root, allowedEvidence))
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
            base.analysisPlan(),
            Instant.now(),
            base.sourceRevision(),
            base.structureIndexVersion(),
            base.modelAnalysisVersion(),
            "CURRENT",
            diagnostics
        );
    }

    private UnderstandingSection parseSection(String name, JsonNode node, Set<String> allowedEvidence) {
        if (!node.isObject()) return new UnderstandingSection("", List.of());
        String summary = bounded(node.path("summary").asText("").trim(), 1200);
        List<UnderstandingClaim> claims = new ArrayList<>();
        if (node.path("claims").isArray()) {
            int index = 0;
            for (JsonNode claim : node.path("claims")) {
                if (index >= 12) break;
                String text = bounded(claim.path("text").asText("").trim(), 600);
                List<String> refs = boundedTexts(claim.path("evidenceRefs"), 12, 100).stream()
                    .filter(allowedEvidence::contains)
                    .distinct()
                    .toList();
                if (!text.isBlank() && !refs.isEmpty()) {
                    claims.add(new UnderstandingClaim(
                        "model-" + name + "-" + (++index),
                        text,
                        "INFERRED",
                        normalizeConfidence(claim.path("confidence").asText("MEDIUM")),
                        refs
                    ));
                }
            }
        }
        return claims.isEmpty()
            ? new UnderstandingSection("", List.of())
            : new UnderstandingSection(summary, List.copyOf(claims));
    }

    private String buildPrompt(
        ProjectSpace project,
        RepositoryIntakeResponse intake,
        ProjectStructureIndexResponse index
    ) throws JsonProcessingException {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("projectName", bounded(project.getName(), 200));
        context.put("classification", intake.classification());
        context.put("scale", intake.scale());
        context.put("fileCount", intake.fileCount());
        context.put("sourceFileCount", intake.sourceFileCount());
        context.put("estimatedLoc", intake.estimatedLoc());
        context.put("languages", intake.languageDistribution());
        context.put("manifests", intake.manifestFiles().stream().limit(100).toList());
        context.put("gitAvailable", intake.git().available());
        context.put("modules", index.modules().stream().limit(250).toList());
        context.put("entryPoints", index.entryPoints().stream().limit(100).toList());
        context.put("engineeringSignals", index.engineeringSignals());
        context.put("evidence", index.evidence().stream().limit(700).toList());
        context.put("coverage", index.coverage());
        context.put("dirtySet", index.delta());
        context.put("unsupportedAreas", index.unsupportedAreas());
        String compact = bounded(objectMapper.writeValueAsString(context), Math.max(8_000, maxModelPromptChars));
        return """
            你是项目理解器。只根据下面的确定性结构证据总结当前项目，不得把文件名、README 宣传或目录邻近当作已实现事实。
            每条判断都必须引用 evidence 中真实存在的 id；无法证明就放入 unknowns。不要输出下一步、路线图或优先级。
            observed 事实已由系统生成；你生成的所有 claims 都会被标记为 INFERRED。
            只返回 JSON，结构为：
            {"identity":{"summary":"","claims":[{"text":"","confidence":"HIGH|MEDIUM|LOW","evidenceRefs":["id"]}]},
            "technology":{"summary":"","claims":[]},"structure":{"summary":"","claims":[]},
            "architecture":{"summary":"","claims":[]},"capabilities":{"summary":"","claims":[]},
            "engineeringState":{"summary":"","claims":[]},"unknowns":[]}
            每个 section 最多 12 条 claims。证据上下文：
            """ + compact;
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

    private static UnderstandingSection prefer(UnderstandingSection candidate, UnderstandingSection fallback) {
        if (candidate == null) return fallback;
        boolean emptySummary = candidate.summary() == null || candidate.summary().isBlank();
        boolean emptyClaims = candidate.claims() == null || candidate.claims().isEmpty();
        return emptySummary && emptyClaims ? fallback : candidate;
    }

    private static String invalidEvidenceLimitation(JsonNode root, Set<String> allowedEvidence) {
        for (String section : SECTION_NAMES) {
            JsonNode claims = root.path(section).path("claims");
            if (!claims.isArray()) continue;
            for (JsonNode claim : claims) {
                for (JsonNode ref : claim.path("evidenceRefs")) {
                    if (!allowedEvidence.contains(ref.asText())) {
                        return "模型返回的未知证据引用已过滤";
                    }
                }
            }
        }
        return "";
    }

    private static List<String> mergeLimitations(List<String> base, String extra) {
        LinkedHashSet<String> values = new LinkedHashSet<>(base == null ? List.of() : base);
        if (extra != null && !extra.isBlank()) values.add(extra);
        return List.copyOf(values);
    }

    private static List<String> boundedTexts(JsonNode node, int maxItems, int maxLength) {
        if (!node.isArray()) return List.of();
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            String value = bounded(item.asText("").trim(), maxLength);
            if (!value.isBlank()) values.add(value);
            if (values.size() >= maxItems) break;
        }
        return List.copyOf(values);
    }

    private static String normalizeConfidence(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "HIGH", "MEDIUM", "LOW" -> normalized;
            default -> "MEDIUM";
        };
    }

    private static String confidence(ProjectStructureIndexResponse index) {
        if (index.coverage().overall() >= 0.8) return "HIGH";
        if (index.coverage().overall() >= 0.5) return "MEDIUM";
        return "LOW";
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

    private static String bounded(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
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

    private static ProjectUnderstandingSnapshotResponse withCacheHit(ProjectUnderstandingSnapshotResponse value) {
        UnderstandingQuality quality = new UnderstandingQuality(
            value.quality().semanticStatus(),
            value.quality().confidence(),
            value.quality().modelUsed(),
            true,
            value.quality().limitations()
        );
        return copy(value, value.id(), value.currentStatus(), quality);
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
            currentStatus, value.diagnostics()
        );
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
