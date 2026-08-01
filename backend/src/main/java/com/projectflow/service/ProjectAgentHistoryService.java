package com.projectflow.service;

import static com.projectflow.dto.ProjectAgentHistoryDtos.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.dto.ProjectMemoryGatewayDtos.MemorySearchResultResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.AnalysisToolEvidenceResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.ProjectEvidenceSourceResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.ProjectStructureIndexResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.ProjectUnderstandingSnapshotResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.StructureOccurrence;
import com.projectflow.dto.ProjectUnderstandingDtos.StructureSymbolNode;
import com.projectflow.entity.ProjectAgentCandidate;
import com.projectflow.entity.ProjectFact;
import com.projectflow.entity.ProjectFactEpistemicStatus;
import com.projectflow.entity.ProjectFactRecordStatus;
import com.projectflow.entity.ProjectSpace;
import com.projectflow.entity.ProjectStructureIndex;
import com.projectflow.repository.ProjectAgentCandidateRepository;
import com.projectflow.repository.ProjectFactOverviewRow;
import com.projectflow.repository.ProjectFactRepository;
import com.projectflow.repository.ProjectMemoryRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.repository.ProjectStructureIndexRepository;
import com.projectflow.repository.ProjectUnderstandingSnapshotRepository;
import com.projectflow.support.AppException;

@Service
public class ProjectAgentHistoryService {
    private static final String PACKAGE_VERSION = "projectflow-agent-context-v2";
    private static final String PACKAGE_REVISION_ALGORITHM = "sha256-canonical-v1";
    private static final int MAX_PROJECTS = 100;
    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}_-]{2,}");

    private final ProjectRepository projectRepository;
    private final ProjectMemoryRepository memoryRepository;
    private final ProjectFactRepository factRepository;
    private final ProjectAgentCandidateRepository candidateRepository;
    private final ProjectUnderstandingSnapshotRepository understandingRepository;
    private final ProjectStructureIndexRepository structureRepository;
    private final ProjectMemorySearchService memorySearchService;
    private final ObjectMapper objectMapper;
    private final SensitiveContentRedactor redactor;

    @Autowired
    public ProjectAgentHistoryService(
        ProjectRepository projectRepository,
        ProjectMemoryRepository memoryRepository,
        ProjectFactRepository factRepository,
        ProjectAgentCandidateRepository candidateRepository,
        ProjectUnderstandingSnapshotRepository understandingRepository,
        ProjectStructureIndexRepository structureRepository,
        ProjectMemorySearchService memorySearchService,
        ObjectMapper objectMapper,
        SensitiveContentRedactor redactor
    ) {
        this.projectRepository = projectRepository;
        this.memoryRepository = memoryRepository;
        this.factRepository = factRepository;
        this.candidateRepository = candidateRepository;
        this.understandingRepository = understandingRepository;
        this.structureRepository = structureRepository;
        this.memorySearchService = memorySearchService;
        this.objectMapper = objectMapper;
        this.redactor = redactor;
    }

    /** Compatibility constructor for focused unit tests. */
    public ProjectAgentHistoryService(
        ProjectRepository projectRepository,
        ProjectMemoryRepository memoryRepository,
        ProjectFactRepository factRepository,
        ProjectAgentCandidateRepository candidateRepository,
        ProjectUnderstandingSnapshotRepository understandingRepository,
        ProjectMemorySearchService memorySearchService,
        ObjectMapper objectMapper
    ) {
        this(
            projectRepository, memoryRepository, factRepository, candidateRepository,
            understandingRepository, null, memorySearchService, objectMapper,
            new SensitiveContentRedactor()
        );
    }

    @Transactional(readOnly = true)
    public AgentProjectCatalogResponse catalog(UUID userId) {
        List<AgentProjectCatalogItem> items = projectRepository.findByUserIdOrderByUpdatedAtDesc(userId)
            .stream().limit(MAX_PROJECTS).map(this::catalogItem).toList();
        return new AgentProjectCatalogResponse(items, items.size(), Instant.now());
    }

    @Transactional(readOnly = true)
    public PortfolioSearchResponse searchPortfolio(UUID userId, String query, int size) {
        String normalized = query == null ? "" : query.strip();
        if (normalized.isBlank() || normalized.length() > 500) {
            throw new AppException("INVALID_QUERY", "跨项目查询必须为 1 到 500 个字符", HttpStatus.BAD_REQUEST);
        }
        int limit = Math.max(1, Math.min(100, size <= 0 ? 20 : size));
        List<ProjectSpace> projects = projectRepository.findByUserIdOrderByUpdatedAtDesc(userId)
            .stream().limit(MAX_PROJECTS).toList();
        List<PortfolioSearchItem> items = new ArrayList<>();
        for (ProjectSpace project : projects) {
            if (items.size() >= limit) break;
            var result = memorySearchService.search(
                userId, project.getId(), normalized, null, null, null, 0,
                Math.min(10, limit - items.size()), "compact"
            );
            for (MemorySearchResultResponse item : result.items()) {
                ProjectFact fact = "FACT".equals(item.entityType())
                    ? factRepository.findByIdAndProjectId(item.entityId(), project.getId()).orElse(null)
                    : null;
                items.add(new PortfolioSearchItem(
                    project.getId(), project.getName(), item.entityType(), item.entityId(),
                    item.title(), item.summary(),
                    fact == null ? item.truthLayer() : fact.getEpistemicStatus().name(),
                    fact == null ? "DERIVED" : fact.getCurrentness(),
                    item.truthLayer(), item.occurredAt()
                ));
                if (items.size() >= limit) break;
            }
        }
        boolean truncated = items.size() >= limit;
        return new PortfolioSearchResponse(normalized, List.copyOf(items), projects.size(), truncated);
    }

    @Transactional(readOnly = true)
    public AgentEvidenceResponse evidence(UUID userId, UUID projectId, String evidenceId) {
        ownedProject(userId, projectId);
        ProjectUnderstandingSnapshotResponse snapshot = snapshot(projectId);
        String requested = evidenceId == null ? "" : evidenceId.strip();
        if (snapshot.sourceMap() != null) {
            for (ProjectEvidenceSourceResponse source : snapshot.sourceMap().sources()) {
                if (source.id().equals(requested)) return evidence(projectId, snapshot.sourceRevision(), source);
            }
        }
        if (snapshot.analysisExecution() != null) {
            for (AnalysisToolEvidenceResponse source : snapshot.analysisExecution().evidence()) {
                if (source.id().equals(requested)) {
                    return new AgentEvidenceResponse(
                        projectId, source.id(), source.category(), source.sourceType(), "",
                        "TOOL_RESULT", "UNKNOWN", "CURRENT", "HIGH", "BOUNDED_PROVIDER_RESULT",
                        bounded(source.summary(), 1_000), source.evidenceRefs(), snapshot.sourceRevision()
                    );
                }
            }
        }
        throw new AppException("EVIDENCE_NOT_FOUND", "Evidence 不存在或不属于该项目", HttpStatus.NOT_FOUND);
    }

    @Transactional(readOnly = true)
    public AgentKnowledgeResponse knowledge(UUID userId, UUID projectId, int size) {
        ownedProject(userId, projectId);
        int limit = Math.max(1, Math.min(300, size <= 0 ? 100 : size));
        List<AgentKnowledgeItem> items = collectKnowledge(projectId, limit);
        return new AgentKnowledgeResponse(
            projectId, items,
            count(items, "OBSERVED") + count(items, "VERIFIED"),
            count(items, "DECLARED"),
            count(items, "INFERRED"),
            count(items, "CONFLICTED"),
            count(items, "UNKNOWN"),
            count(items, "PROCESS_EVIDENCE"),
            items.size() >= limit
        );
    }

    @Transactional(readOnly = true)
    public AgentContextPackageResponse contextPackage(UUID userId, UUID projectId, int requestedBudget) {
        return contextPackage(
            userId, projectId, "", List.of(), "CURRENT_SNAPSHOT", "STANDARD", requestedBudget
        );
    }

    @Transactional(readOnly = true)
    public AgentContextPackageResponse contextPackage(
        UUID userId,
        UUID projectId,
        String taskDescription,
        List<String> requestedScope,
        String revisionPreference,
        String requestedEvidenceDepth,
        int requestedBudget
    ) {
        ProjectSpace project = ownedProject(userId, projectId);
        int budget = Math.max(4_000, Math.min(32_000, requestedBudget <= 0 ? 8_000 : requestedBudget));
        String task = bounded(redactor.redact(taskDescription), 1_000);
        List<String> scope = normalizedScope(requestedScope);
        String revisionMode = revisionPreference(revisionPreference);
        String evidenceDepth = evidenceDepth(requestedEvidenceDepth);
        DepthLimits limits = depthLimits(evidenceDepth);
        ProjectUnderstandingSnapshotResponse snapshot = snapshotOrNull(projectId);
        ProjectStructureIndexResponse structure = structureOrNull(projectId);
        List<AgentKnowledgeItem> knowledge = new ArrayList<>(collectKnowledge(projectId, 300));
        List<AgentKnowledgeItem> snapshotUnknowns = new ArrayList<>();
        List<AgentKnowledgeItem> snapshotConflicts = new ArrayList<>();
        addSnapshotUnknowns(projectId, snapshot, snapshotUnknowns, snapshotConflicts);
        knowledge.addAll(snapshotUnknowns);
        knowledge.addAll(snapshotConflicts);
        int availableKnowledgeCount = knowledge.size();
        Set<String> terms = semanticTerms(task);
        knowledge = knowledge.stream()
            .sorted(Comparator
                .comparingInt((AgentKnowledgeItem item) -> knowledgeScore(item, terms, scope)).reversed()
                .thenComparing(AgentKnowledgeItem::effectiveAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(AgentKnowledgeItem::itemId))
            .filter(item -> task.isBlank()
                || knowledgeScore(item, terms, scope) > 0
                || Set.of("CONFLICTED", "UNKNOWN").contains(item.epistemicStatus()))
            .limit(limits.knowledgeItems())
            .toList();
        List<AgentKnowledgeItem> strong = mutableByStatus(knowledge, Set.of("OBSERVED", "VERIFIED"));
        List<AgentKnowledgeItem> declared = mutableByStatus(knowledge, Set.of("DECLARED", "PROCESS_EVIDENCE"));
        List<AgentKnowledgeItem> inferred = mutableByStatus(knowledge, Set.of("INFERRED"));
        List<AgentKnowledgeItem> conflicts = mutableByStatus(knowledge, Set.of("CONFLICTED"));
        List<AgentKnowledgeItem> unknowns = mutableByStatus(knowledge, Set.of("UNKNOWN"));
        List<AgentEvidenceResponse> availableEvidence = new ArrayList<>();
        if (snapshot != null && snapshot.sourceMap() != null) {
            snapshot.sourceMap().sources().stream().limit(100)
                .map(item -> evidence(projectId, snapshot.sourceRevision(), item))
                .forEach(availableEvidence::add);
        }
        if (snapshot != null && snapshot.analysisExecution() != null) {
            snapshot.analysisExecution().evidence().stream().limit(60).map(item -> new AgentEvidenceResponse(
                projectId, item.id(), item.category(), item.sourceType(), "", "TOOL_RESULT", "UNKNOWN",
                "CURRENT", "HIGH", "BOUNDED_PROVIDER_RESULT", bounded(item.summary(), 1_000),
                safe(item.evidenceRefs()), snapshot.sourceRevision()
            )).forEach(availableEvidence::add);
        }
        int availableEvidenceCount = availableEvidence.size();
        Set<String> referencedEvidence = knowledge.stream().flatMap(item -> item.evidenceRefs().stream())
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<AgentEvidenceResponse> evidence = availableEvidence.stream()
            .sorted(Comparator
                .comparingInt((AgentEvidenceResponse item) -> evidenceScore(item, terms, scope, referencedEvidence)).reversed()
                .thenComparing(AgentEvidenceResponse::evidenceId))
            .filter(item -> task.isBlank()
                || referencedEvidence.contains(item.evidenceId())
                || evidenceScore(item, terms, scope, referencedEvidence) > 0)
            .limit(limits.evidenceItems())
            .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        List<AgentKnowledgeItem> verified = new ArrayList<>(strong.stream()
            .filter(item -> "VERIFIED".equals(item.epistemicStatus()))
            .limit(10).toList());
        List<AgentKnowledgeItem> historical = new ArrayList<>(knowledge.stream()
            .filter(item -> item.effectiveAt() != null)
            .sorted(Comparator.comparing(AgentKnowledgeItem::effectiveAt).reversed())
            .limit(limits.historicalItems())
            .toList());
        List<AgentSourceRangeResponse> ranges = new ArrayList<>(relatedRanges(
            structure,
            evidence,
            terms,
            scope,
            snapshot == null ? "" : snapshot.sourceRevision(),
            limits.rangeItems()
        ));
        List<String> limitations = new ArrayList<>();
        if (snapshot == null) limitations.add("尚无持久化 Project Understanding Snapshot");
        if (snapshot != null && snapshot.quality() != null) limitations.addAll(snapshot.quality().limitations());
        if (!task.isBlank() && knowledge.isEmpty() && evidence.isEmpty()) {
            limitations.add("持久化材料中未找到与任务词面或已有关联相匹配的内容；不能据此断言相关内容不存在");
        }
        if (snapshot != null && snapshot.semanticScout() != null
            && snapshot.semanticScout().contractDiagnostics() != null
            && "FAILED_DEGRADED".equals(snapshot.semanticScout().contractDiagnostics().status())) {
            limitations.add("上次 Semantic Scout 存在契约覆盖缺口；相关模型结论必须重新验证");
        }
        String history = snapshot == null || snapshot.historicalCoverage() == null
            ? "UNKNOWN"
            : "availability=" + snapshot.historicalCoverage().availability()
                + ",coverage=" + snapshot.historicalCoverage().overallCoverage()
                + ",from=" + snapshot.historicalCoverage().earliestEvidenceAt()
                + ",to=" + snapshot.historicalCoverage().latestEvidenceAt();
        List<String> unreadScope = unreadScope(scope, evidence, ranges, snapshot, knowledge.size(), availableKnowledgeCount);
        List<String> deepReadTargets = evidence.stream()
            .filter(item -> !"COMPLETED".equalsIgnoreCase(item.deepReadStatus())
                || !"CURRENT".equalsIgnoreCase(item.currentness())
                || Set.of("DOC", "README", "ADR", "AGENT_RESULT", "UNKNOWN_DOCUMENT")
                    .contains(item.category()))
            .map(AgentEvidenceResponse::evidenceId)
            .distinct()
            .limit(limits.deepReadTargets())
            .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        List<String> provenance = new ArrayList<>();
        strong.forEach(item -> provenance.add(item.itemId()));
        evidence.forEach(item -> provenance.add(item.evidenceId()));
        if (snapshot != null) provenance.add("snapshot:" + snapshot.id());
        String semanticContractStatus = snapshot == null || snapshot.semanticScout() == null
            || snapshot.semanticScout().contractDiagnostics() == null
                ? "NOT_AVAILABLE"
                : snapshot.semanticScout().contractDiagnostics().status();
        AgentCoverageDisclosureResponse coverage = new AgentCoverageDisclosureResponse(
            task.isBlank() ? "GENERAL_PERSISTED_CONTEXT" : "TASK_RELEVANT_PERSISTED_CONTEXT",
            knowledge.size(), availableKnowledgeCount, evidence.size(), availableEvidenceCount,
            ranges.size(), knowledge.size() < availableKnowledgeCount || evidence.size() < availableEvidenceCount,
            semanticContractStatus
        );
        Instant generatedAt = Instant.now();

        boolean truncated = false;
        AgentContextPackageResponse result;
        while (true) {
            result = packageResponse(
                project, snapshot, budget, generatedAt, task, scope, revisionMode, evidenceDepth,
                strong, declared, inferred, conflicts, unknowns, evidence, verified, historical,
                ranges, history, coverage, unreadScope, limitations, deepReadTargets, provenance,
                "PERSISTED_ONLY", truncated
            );
            int length = jsonLength(result);
            if (length <= budget
                || !trimOne(
                    evidence, ranges, historical, declared, inferred, verified, strong,
                    deepReadTargets, provenance
                )) {
                if (length > budget) limitations.add("固定来源元数据已达到最小包边界");
                result = packageResponse(
                    project, snapshot, budget, generatedAt, task, scope, revisionMode, evidenceDepth,
                    strong, declared, inferred, conflicts, unknowns, evidence, verified, historical,
                    ranges, history, coverage, unreadScope, limitations, deepReadTargets, provenance,
                    "PERSISTED_ONLY", truncated || length > budget
                );
                break;
            }
            truncated = true;
        }
        int actual = jsonLength(result);
        return new AgentContextPackageResponse(
            result.packageVersion(), result.packageRevision(), result.projectId(), result.projectName(),
            result.projectStatus(), result.sourceRevision(), result.generatedAt(), result.taskDescription(),
            result.requestedScope(), result.revisionPreference(), result.requestedEvidenceDepth(),
            result.currentStrongFacts(), result.declaredMaterial(), result.inferredCandidates(), result.conflicts(),
            result.unknowns(), result.keyEvidence(), result.latestVerifiedChanges(),
            result.relevantHistoricalRecords(), result.relatedRanges(), result.trustGuidance(),
            result.historicalCoverage(), result.coverageDisclosure(), result.unreadScope(), result.limitations(),
            result.suggestedDeepReadTargets(), result.provenance(), result.generationMetadata(),
            result.sizeBudget(), actual, result.truncated()
        );
    }

    private AgentProjectCatalogItem catalogItem(ProjectSpace project) {
        ProjectUnderstandingSnapshotResponse snapshot = snapshotOrNull(project.getId());
        ProjectFactOverviewRow facts = factRepository.summarize(
            project.getId(), ProjectFactRecordStatus.RECORDED, ProjectFactRecordStatus.NEEDS_ATTENTION
        );
        long strong = factRepository.findTop200ByProjectIdOrderByOccurredToDescCreatedAtDesc(project.getId()).stream()
            .filter(item -> item.getRecordStatus() == ProjectFactRecordStatus.RECORDED)
            .filter(item -> item.getEpistemicStatus().isStrongFact()).count();
        List<ProjectAgentCandidate> candidates = candidateRepository.findTop100ByProjectIdOrderByCreatedAtDesc(project.getId());
        int unknown = snapshot == null ? 0 : safe(snapshot.unknowns()).size();
        int conflict = snapshot == null || snapshot.semanticScout() == null
            ? 0 : safe(snapshot.semanticScout().potentialConflicts()).size();
        unknown += (int) candidates.stream().filter(item -> item.getEpistemicStatus() == ProjectFactEpistemicStatus.UNKNOWN).count();
        conflict += (int) candidates.stream().filter(item -> item.getEpistemicStatus() == ProjectFactEpistemicStatus.CONFLICTED).count();
        double coverage = snapshot == null || snapshot.dynamicProfile() == null
            ? 0 : snapshot.dynamicProfile().evidenceCoverage();
        boolean localBinding = memoryRepository.findByProjectId(project.getId())
            .map(value -> !value.getLocalProjectPath().isBlank()).orElse(false);
        String safeSourceId = localBinding ? "local:" + shortHash(project.getId().toString())
            : project.getRepoUrl() == null || project.getRepoUrl().isBlank()
                ? "unbound:" + shortHash(project.getId().toString())
                : "remote:" + shortHash(project.getRepoUrl());
        return new AgentProjectCatalogItem(
            project.getId(), project.getName(), project.getStatus().name(), safeSourceId,
            snapshot == null ? null : snapshot.id(),
            snapshot == null ? "" : snapshot.sourceRevision(),
            snapshot == null ? null : snapshot.analyzedAt(),
            facts == null ? null : facts.earliestOccurredAt(),
            facts == null ? null : facts.latestOccurredAt(),
            coverage, strong, candidates.size(), unknown, conflict
        );
    }

    private List<AgentKnowledgeItem> collectKnowledge(UUID projectId, int limit) {
        List<AgentKnowledgeItem> items = new ArrayList<>();
        for (ProjectFact fact : factRepository.findTop200ByProjectIdOrderByOccurredToDescCreatedAtDesc(projectId)) {
            if (items.size() >= limit) break;
            items.add(factItem(fact));
        }
        if (items.size() < limit) {
            for (ProjectAgentCandidate candidate : candidateRepository.findTop100ByProjectIdOrderByCreatedAtDesc(projectId)) {
                if (items.size() >= limit) break;
                items.add(candidateItem(candidate));
            }
        }
        return List.copyOf(items);
    }

    private static AgentKnowledgeItem factItem(ProjectFact fact) {
        return new AgentKnowledgeItem(
            fact.getProjectId(), "fact:" + fact.getId(), bounded(fact.getStatement(), 600),
            fact.getEpistemicStatus().name(), fact.getCurrentness(), fact.getRevision(), fact.getValidationStatus(),
            fact.getEvidenceRefs().stream().limit(30).toList(),
            fact.getLimitations().stream().limit(10).toList(), fact.getEffectiveAt()
        );
    }

    private static AgentKnowledgeItem candidateItem(ProjectAgentCandidate candidate) {
        return new AgentKnowledgeItem(
            candidate.getProjectId(), "candidate:" + candidate.getId(), bounded(candidate.getAssertion(), 600),
            candidate.getEpistemicStatus().name(), candidate.getCurrentness(), candidate.getSourceRevision(),
            candidate.getValidationStatus(),
            candidate.getEvidenceRefs().stream().limit(30).toList(),
            candidate.getLimitations().stream().limit(10).toList(), candidate.getCreatedAt()
        );
    }

    private static void addSnapshotUnknowns(
        UUID projectId,
        ProjectUnderstandingSnapshotResponse snapshot,
        List<AgentKnowledgeItem> unknowns,
        List<AgentKnowledgeItem> conflicts
    ) {
        if (snapshot == null) return;
        int index = 0;
        for (String value : safe(snapshot.unknowns())) {
            unknowns.add(derivedItem(
                projectId, "snapshot:unknown:" + index++, value, "UNKNOWN", snapshot.sourceRevision()
            ));
        }
        if (snapshot.semanticScout() != null) {
            index = 0;
            for (String value : safe(snapshot.semanticScout().potentialConflicts())) {
                conflicts.add(derivedItem(
                    projectId, "snapshot:conflict:" + index++, value, "CONFLICTED", snapshot.sourceRevision()
                ));
            }
        }
    }

    private static AgentKnowledgeItem derivedItem(
        UUID projectId,
        String id,
        String statement,
        String status,
        String sourceRevision
    ) {
        return new AgentKnowledgeItem(
            projectId, id, bounded(statement, 500), status, "UNKNOWN",
            sourceRevision, "DERIVED_FROM_PERSISTED_UNDERSTANDING", List.of(), List.of(), null
        );
    }

    private static AgentEvidenceResponse evidence(
        UUID projectId,
        String sourceRevision,
        ProjectEvidenceSourceResponse source
    ) {
        return new AgentEvidenceResponse(
            projectId, source.id(), source.category(), source.sourceType(), safeRelative(source.locator()),
            source.semanticRole(), source.importance(), source.currentness(), source.confidence(),
            source.deepReadStatus(), bounded(source.summary(), 1_000),
            source.evidenceRefs().stream().limit(30).toList(), sourceRevision
        );
    }

    private ProjectUnderstandingSnapshotResponse snapshot(UUID projectId) {
        ProjectUnderstandingSnapshotResponse value = snapshotOrNull(projectId);
        if (value == null) {
            throw new AppException(
                "PROJECT_UNDERSTANDING_NOT_FOUND",
                "项目尚无持久化理解结果",
                HttpStatus.NOT_FOUND
            );
        }
        return value;
    }

    private ProjectUnderstandingSnapshotResponse snapshotOrNull(UUID projectId) {
        return understandingRepository.findByProjectId(projectId).map(entity -> {
            try {
                return objectMapper.readValue(entity.getSnapshotJson(), ProjectUnderstandingSnapshotResponse.class);
            } catch (JsonProcessingException exception) {
                return null;
            }
        }).orElse(null);
    }

    private ProjectStructureIndexResponse structureOrNull(UUID projectId) {
        if (structureRepository == null) return null;
        ProjectStructureIndex entity = structureRepository.findByProjectId(projectId).orElse(null);
        if (entity == null) return null;
        try {
            return objectMapper.readValue(entity.getIndexJson(), ProjectStructureIndexResponse.class);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private ProjectSpace ownedProject(UUID userId, UUID projectId) {
        return projectRepository.findByIdAndUserId(projectId, userId)
            .orElseThrow(() -> new AppException("PROJECT_NOT_FOUND", "项目不存在", HttpStatus.NOT_FOUND));
    }

    private AgentContextPackageResponse packageResponse(
        ProjectSpace project,
        ProjectUnderstandingSnapshotResponse snapshot,
        int budget,
        Instant generatedAt,
        String taskDescription,
        List<String> requestedScope,
        String revisionPreference,
        String evidenceDepth,
        List<AgentKnowledgeItem> strong,
        List<AgentKnowledgeItem> declared,
        List<AgentKnowledgeItem> inferred,
        List<AgentKnowledgeItem> conflicts,
        List<AgentKnowledgeItem> unknowns,
        List<AgentEvidenceResponse> evidence,
        List<AgentKnowledgeItem> verified,
        List<AgentKnowledgeItem> historical,
        List<AgentSourceRangeResponse> ranges,
        String history,
        AgentCoverageDisclosureResponse coverage,
        List<String> unreadScope,
        List<String> limitations,
        List<String> deepReadTargets,
        List<String> provenance,
        String revisionValidation,
        boolean truncated
    ) {
        String sourceRevision = snapshot == null ? "" : snapshot.sourceRevision();
        AgentTrustGuidanceResponse trust = trustGuidance(
            strong, declared, inferred, conflicts, unknowns, sourceRevision,
            snapshot != null && snapshot.quality() != null
                && "FAILED_DEGRADED".equals(snapshot.quality().semanticStatus())
        );
        AgentCoverageDisclosureResponse currentCoverage = new AgentCoverageDisclosureResponse(
            coverage.mode(),
            strong.size() + declared.size() + inferred.size() + conflicts.size() + unknowns.size(),
            coverage.availableKnowledgeCount(), evidence.size(), coverage.availableEvidenceCount(),
            ranges.size(), coverage.partial() || truncated, coverage.semanticContractStatus()
        );
        String packageRevision = packageRevision(
            project, sourceRevision, taskDescription, requestedScope, revisionPreference, evidenceDepth,
            strong, declared, inferred, conflicts, unknowns, evidence, ranges, unreadScope, limitations
        );
        return new AgentContextPackageResponse(
            PACKAGE_VERSION, packageRevision, project.getId(), project.getName(), project.getStatus().name(),
            sourceRevision, generatedAt, taskDescription, List.copyOf(requestedScope), revisionPreference,
            evidenceDepth,
            List.copyOf(strong), List.copyOf(declared), List.copyOf(inferred),
            List.copyOf(conflicts), List.copyOf(unknowns), List.copyOf(evidence),
            List.copyOf(verified), List.copyOf(historical), List.copyOf(ranges), trust,
            history, currentCoverage, List.copyOf(new LinkedHashSet<>(unreadScope)),
            List.copyOf(new LinkedHashSet<>(limitations)),
            List.copyOf(new LinkedHashSet<>(deepReadTargets)),
            List.copyOf(new LinkedHashSet<>(provenance)),
            new AgentContextGenerationMetadata(
                taskDescription.isBlank() ? "PERSISTED_RELATION_ORDER" : "PERSISTED_TASK_RELATION_RANKING",
                false, PACKAGE_REVISION_ALGORITHM, revisionValidation, evidenceDepth, generatedAt
            ),
            budget, 0, truncated
        );
    }

    private String packageRevision(
        ProjectSpace project,
        String sourceRevision,
        String taskDescription,
        List<String> requestedScope,
        String revisionPreference,
        String evidenceDepth,
        List<AgentKnowledgeItem> strong,
        List<AgentKnowledgeItem> declared,
        List<AgentKnowledgeItem> inferred,
        List<AgentKnowledgeItem> conflicts,
        List<AgentKnowledgeItem> unknowns,
        List<AgentEvidenceResponse> evidence,
        List<AgentSourceRangeResponse> ranges,
        List<String> unreadScope,
        List<String> limitations
    ) {
        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("version", PACKAGE_VERSION);
        canonical.put("projectId", project.getId());
        canonical.put("sourceRevision", sourceRevision);
        canonical.put("task", normalize(taskDescription));
        canonical.put("scope", requestedScope);
        canonical.put("revisionPreference", revisionPreference);
        canonical.put("evidenceDepth", evidenceDepth);
        canonical.put("strong", knowledgeIdentity(strong));
        canonical.put("declared", knowledgeIdentity(declared));
        canonical.put("inferred", knowledgeIdentity(inferred));
        canonical.put("conflicts", knowledgeIdentity(conflicts));
        canonical.put("unknowns", knowledgeIdentity(unknowns));
        canonical.put("evidence", evidence.stream().map(item -> List.of(
            item.evidenceId(), item.sourceRevision(), item.currentness(), item.deepReadStatus()
        )).toList());
        canonical.put("ranges", ranges.stream().map(item -> List.of(
            item.evidenceId(), item.locator(), item.startLine(), item.endLine(), item.sourceRevision()
        )).toList());
        canonical.put("unreadScope", unreadScope);
        canonical.put("limitations", List.copyOf(new LinkedHashSet<>(limitations)));
        try {
            return "sha256:" + sha256(objectMapper.writeValueAsString(canonical));
        } catch (JsonProcessingException exception) {
            return "sha256:" + sha256(canonical.toString());
        }
    }

    private static List<List<String>> knowledgeIdentity(List<AgentKnowledgeItem> items) {
        return items.stream().map(item -> List.of(
            item.itemId(), item.epistemicStatus(), item.currentness(), item.sourceRevision(), item.validationStatus()
        )).toList();
    }

    private static AgentTrustGuidanceResponse trustGuidance(
        List<AgentKnowledgeItem> strong,
        List<AgentKnowledgeItem> declared,
        List<AgentKnowledgeItem> inferred,
        List<AgentKnowledgeItem> conflicts,
        List<AgentKnowledgeItem> unknowns,
        String currentRevision,
        boolean degraded
    ) {
        List<String> reusable = new ArrayList<>();
        List<String> quick = new ArrayList<>();
        List<String> must = new ArrayList<>();
        for (AgentKnowledgeItem item : strong) {
            boolean revisionMatches = item.sourceRevision().isBlank() || currentRevision.isBlank()
                || item.sourceRevision().equals(currentRevision);
            boolean validated = item.validationStatus().toUpperCase(Locale.ROOT).contains("VALID")
                || item.validationStatus().toUpperCase(Locale.ROOT).contains("PASS");
            if (!degraded && "VERIFIED".equals(item.epistemicStatus())
                && "CURRENT".equalsIgnoreCase(item.currentness()) && revisionMatches && validated) {
                reusable.add(item.itemId());
            } else if (!degraded && "OBSERVED".equals(item.epistemicStatus())
                && "CURRENT".equalsIgnoreCase(item.currentness()) && revisionMatches) {
                quick.add(item.itemId());
            } else {
                must.add(item.itemId());
            }
        }
        java.util.stream.Stream.of(declared, inferred, conflicts, unknowns)
            .flatMap(List::stream).map(AgentKnowledgeItem::itemId).forEach(must::add);
        return new AgentTrustGuidanceResponse(
            List.copyOf(new LinkedHashSet<>(reusable)),
            List.copyOf(new LinkedHashSet<>(quick)),
            List.copyOf(new LinkedHashSet<>(must)),
            List.of(
                "current VERIFIED 且 Revision、验证状态一致时通常可作为历史状态采用",
                "任务关键 OBSERVED、源码位置和部分覆盖内容需要快速核验",
                "DECLARED、INFERRED、CONFLICTED、UNKNOWN、旧 Revision 与 degraded 结果必须重新验证",
                "准备修改的源码和原始材料仍需由 Agent 直接读取"
            )
        );
    }

    private static List<AgentSourceRangeResponse> relatedRanges(
        ProjectStructureIndexResponse structure,
        List<AgentEvidenceResponse> evidence,
        Set<String> terms,
        List<String> scope,
        String sourceRevision,
        int limit
    ) {
        LinkedHashMap<String, AgentSourceRangeResponse> result = new LinkedHashMap<>();
        Pattern rangePattern = Pattern.compile("(?i)(?:lines?|行)\\s*(\\d+)\\s*[-–]\\s*(\\d+)");
        for (AgentEvidenceResponse item : evidence) {
            if (result.size() >= limit) break;
            String locator = safeRelative(item.locator());
            if (locator.isBlank()) continue;
            Matcher matcher = rangePattern.matcher(item.summary());
            long start = matcher.find() ? parseLong(matcher.group(1)) : 0;
            long end = start > 0 ? parseLong(matcher.group(2)) : 0;
            result.putIfAbsent(locator + ":" + start + ":" + end, new AgentSourceRangeResponse(
                item.evidenceId(), locator, start > 0 ? "PERSISTED_EVIDENCE_RANGE" : "FILE",
                start, end, 0, 0, item.sourceRevision(), item.currentness(),
                start > 0 ? "RE_READ_RANGE_BEFORE_EDIT" : "READ_SOURCE_BEFORE_EDIT"
            ));
        }
        if (structure == null || structure.definitions() == null || result.size() >= limit) {
            return List.copyOf(result.values());
        }
        Map<String, StructureSymbolNode> symbols = structure.symbols() == null ? Map.of()
            : structure.symbols().stream().collect(java.util.stream.Collectors.toMap(
                StructureSymbolNode::id, value -> value, (left, right) -> left, LinkedHashMap::new
            ));
        structure.definitions().stream()
            .sorted(Comparator
                .comparingInt((StructureOccurrence item) -> structureScore(item, symbols.get(item.symbolId()), terms, scope))
                .reversed()
                .thenComparing(StructureOccurrence::path)
                .thenComparingInt(StructureOccurrence::startLine))
            .filter(item -> terms.isEmpty()
                || structureScore(item, symbols.get(item.symbolId()), terms, scope) > 0)
            .limit(limit * 2L)
            .forEach(item -> {
                if (result.size() >= limit) return;
                String locator = safeRelative(item.path());
                if (locator.isBlank()) return;
                StructureSymbolNode symbol = symbols.get(item.symbolId());
                String evidenceId = item.evidenceRef() == null || item.evidenceRef().isBlank()
                    ? "structure:" + item.symbolId()
                    : item.evidenceRef();
                String hint = symbol == null || symbol.displayName() == null || symbol.displayName().isBlank()
                    ? "RE_READ_RANGE_BEFORE_EDIT"
                    : "RE_READ_" + bounded(symbol.displayName(), 60) + "_BEFORE_EDIT";
                result.putIfAbsent(locator + ":" + item.startLine() + ":" + item.endLine(),
                    new AgentSourceRangeResponse(
                        evidenceId, locator, "DEFINITION", item.startLine(), item.endLine(), 0, 0,
                        sourceRevision, "CURRENT", hint
                    ));
            });
        return List.copyOf(result.values());
    }

    private static int structureScore(
        StructureOccurrence occurrence,
        StructureSymbolNode symbol,
        Set<String> terms,
        List<String> scope
    ) {
        int score = textScore(occurrence.path(), terms) * 5 + scopeScore(occurrence.path(), scope) * 8;
        if (symbol != null) {
            score += textScore(symbol.symbol(), terms) * 5;
            score += textScore(symbol.displayName(), terms) * 5;
            score += textScore(symbol.kind(), terms);
        }
        return score;
    }

    private static int knowledgeScore(AgentKnowledgeItem item, Set<String> terms, List<String> scope) {
        if (terms.isEmpty() && scope.isEmpty()) return 1;
        int score = textScore(item.statement(), terms) * 5;
        score += textScore(String.join(" ", item.evidenceRefs()), terms) * 3;
        score += textScore(String.join(" ", item.limitations()), terms);
        score += item.evidenceRefs().stream().mapToInt(value -> scopeScore(value, scope)).sum() * 6;
        if ("VERIFIED".equals(item.epistemicStatus())) score += 2;
        if (Set.of("CONFLICTED", "UNKNOWN").contains(item.epistemicStatus())) score += 1;
        return score;
    }

    private static int evidenceScore(
        AgentEvidenceResponse item,
        Set<String> terms,
        List<String> scope,
        Set<String> referencedEvidence
    ) {
        if (terms.isEmpty() && scope.isEmpty()) return referencedEvidence.contains(item.evidenceId()) ? 3 : 1;
        int score = textScore(item.summary(), terms) * 4;
        score += textScore(item.locator(), terms) * 6;
        score += textScore(item.semanticRole() + " " + item.category() + " " + item.sourceType(), terms) * 2;
        score += scopeScore(item.locator(), scope) * 8;
        if (referencedEvidence.contains(item.evidenceId())) score += 20;
        return score;
    }

    private static int textScore(String value, Set<String> terms) {
        if (value == null || value.isBlank() || terms.isEmpty()) return 0;
        String normalized = normalize(value);
        int score = 0;
        for (String term : terms) {
            if (normalized.contains(term)) score += Math.min(4, Math.max(1, term.length() / 2));
        }
        return score;
    }

    private static int scopeScore(String value, List<String> scope) {
        String relative = safeRelative(value == null ? "" : value.replaceFirst("^[a-z-]+:", ""));
        if (relative.isBlank()) return 0;
        for (String requested : scope) {
            if (relative.equals(requested) || relative.startsWith(requested + "/")
                || requested.startsWith(relative + "/")) return 1;
        }
        return 0;
    }

    private static Set<String> semanticTerms(String value) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        String normalized = normalize(value).replaceAll("([a-z])([A-Z])", "$1 $2");
        Matcher matcher = TOKEN.matcher(normalized);
        while (matcher.find() && result.size() < 120) {
            String token = matcher.group().toLowerCase(Locale.ROOT);
            result.add(token);
            if (containsCjk(token)) {
                for (int width = 2; width <= 3; width++) {
                    for (int start = 0; start + width <= token.length() && result.size() < 120; start++) {
                        result.add(token.substring(start, start + width));
                    }
                }
            } else {
                for (String part : token.split("[_-]+")) if (part.length() >= 2) result.add(part);
            }
        }
        return Set.copyOf(result);
    }

    private static boolean containsCjk(String value) {
        return value.codePoints().anyMatch(code -> Character.UnicodeScript.of(code) == Character.UnicodeScript.HAN);
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
            .replace('\\', '/').replaceAll("\\s+", " ").strip();
    }

    private static List<String> normalizedScope(List<String> values) {
        if (values == null || values.isEmpty()) return List.of();
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String raw : values) {
            if (raw == null) continue;
            for (String part : raw.split(",")) {
                String relative = safeRelative(part);
                if (relative.isBlank() && !part.isBlank()) {
                    throw new AppException("INVALID_CONTEXT_SCOPE", "Context scope 必须是项目内相对路径", HttpStatus.BAD_REQUEST);
                }
                if (!relative.isBlank()) result.add(bounded(relative, 200));
                if (result.size() >= 20) break;
            }
        }
        return List.copyOf(result);
    }

    private static String revisionPreference(String value) {
        String normalized = value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "", "CURRENT", "CURRENT_SNAPSHOT" -> "CURRENT_SNAPSHOT";
            case "LATEST", "LATEST_AVAILABLE" -> "LATEST_AVAILABLE";
            case "ANY", "ANY_PERSISTED" -> "ANY_PERSISTED";
            default -> throw new AppException(
                "INVALID_REVISION_PREFERENCE", "不支持的 Context revision preference", HttpStatus.BAD_REQUEST
            );
        };
    }

    private static String evidenceDepth(String value) {
        String normalized = value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "", "STANDARD" -> "STANDARD";
            case "COMPACT", "DEEP" -> normalized;
            default -> throw new AppException(
                "INVALID_EVIDENCE_DEPTH", "Evidence depth 只能是 COMPACT、STANDARD 或 DEEP", HttpStatus.BAD_REQUEST
            );
        };
    }

    private static DepthLimits depthLimits(String value) {
        return switch (value) {
            case "COMPACT" -> new DepthLimits(24, 12, 12, 12, 8);
            case "DEEP" -> new DepthLimits(120, 48, 40, 40, 24);
            default -> new DepthLimits(60, 24, 24, 24, 16);
        };
    }

    private static List<String> unreadScope(
        List<String> scope,
        List<AgentEvidenceResponse> evidence,
        List<AgentSourceRangeResponse> ranges,
        ProjectUnderstandingSnapshotResponse snapshot,
        int matchedKnowledge,
        int availableKnowledge
    ) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String requested : scope) {
            boolean matched = evidence.stream().anyMatch(item -> scopeScore(item.locator(), List.of(requested)) > 0)
                || ranges.stream().anyMatch(item -> scopeScore(item.locator(), List.of(requested)) > 0);
            if (!matched) result.add("未在持久化 Evidence/Structure 中解析 scope：" + requested);
        }
        if (snapshot != null && snapshot.intake() != null && snapshot.intake().scanTruncated()) {
            result.add("项目 intake 达到扫描上限，未读范围保持 UNKNOWN");
        }
        if (snapshot != null && snapshot.sourceMap() != null) {
            safe(snapshot.sourceMap().warnings()).stream().limit(8).forEach(result::add);
            evidence.stream().filter(item -> item.summary().toLowerCase(Locale.ROOT).contains("unread")
                || item.summary().toLowerCase(Locale.ROOT).contains("partial="))
                .map(item -> item.evidenceId() + " 存在未读或部分覆盖范围")
                .limit(8).forEach(result::add);
        }
        if (matchedKnowledge < availableKnowledge) result.add("Context Package 只返回预算内的任务相关知识子集");
        return List.copyOf(result);
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (RuntimeException exception) {
            return 0;
        }
    }

    private int jsonLength(Object value) {
        try {
            return objectMapper.writeValueAsString(value).length();
        } catch (JsonProcessingException exception) {
            return Integer.MAX_VALUE;
        }
    }

    @SafeVarargs
    private static boolean trimOne(List<?>... lists) {
        for (List<?> list : lists) {
            if (!list.isEmpty()) {
                list.remove(list.size() - 1);
                return true;
            }
        }
        return false;
    }

    private static List<AgentKnowledgeItem> mutableByStatus(List<AgentKnowledgeItem> items, Set<String> statuses) {
        return new ArrayList<>(items.stream().filter(item -> statuses.contains(item.epistemicStatus())).toList());
    }

    private static int count(List<AgentKnowledgeItem> items, String status) {
        return (int) items.stream().filter(item -> status.equals(item.epistemicStatus())).count();
    }

    private static String safeRelative(String value) {
        if (value == null || value.isBlank()) return "";
        String normalized = value.strip().replace('\\', '/');
        if (normalized.startsWith("/") || normalized.matches("^[A-Za-z]:/.*") || normalized.contains("../")) return "";
        return normalized;
    }

    private static String bounded(String value, int max) {
        if (value == null) return "";
        String safe = value.strip();
        return safe.length() <= max ? safe : safe.substring(0, max);
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static String shortHash(String value) {
        return sha256(value).substring(0, 20);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private record DepthLimits(
        int knowledgeItems,
        int evidenceItems,
        int historicalItems,
        int rangeItems,
        int deepReadTargets
    ) {
    }
}
