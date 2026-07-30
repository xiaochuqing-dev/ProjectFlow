package com.projectflow.service;

import static com.projectflow.dto.ProjectAgentHistoryDtos.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.dto.ProjectMemoryGatewayDtos.MemorySearchResultResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.AnalysisToolEvidenceResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.ProjectEvidenceSourceResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.ProjectUnderstandingSnapshotResponse;
import com.projectflow.entity.ProjectAgentCandidate;
import com.projectflow.entity.ProjectFact;
import com.projectflow.entity.ProjectFactEpistemicStatus;
import com.projectflow.entity.ProjectFactRecordStatus;
import com.projectflow.entity.ProjectSpace;
import com.projectflow.repository.ProjectAgentCandidateRepository;
import com.projectflow.repository.ProjectFactOverviewRow;
import com.projectflow.repository.ProjectFactRepository;
import com.projectflow.repository.ProjectMemoryRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.repository.ProjectUnderstandingSnapshotRepository;
import com.projectflow.support.AppException;

@Service
public class ProjectAgentHistoryService {
    private static final String PACKAGE_VERSION = "projectflow-agent-context-v1";
    private static final int MAX_PROJECTS = 100;

    private final ProjectRepository projectRepository;
    private final ProjectMemoryRepository memoryRepository;
    private final ProjectFactRepository factRepository;
    private final ProjectAgentCandidateRepository candidateRepository;
    private final ProjectUnderstandingSnapshotRepository understandingRepository;
    private final ProjectMemorySearchService memorySearchService;
    private final ObjectMapper objectMapper;

    public ProjectAgentHistoryService(
        ProjectRepository projectRepository,
        ProjectMemoryRepository memoryRepository,
        ProjectFactRepository factRepository,
        ProjectAgentCandidateRepository candidateRepository,
        ProjectUnderstandingSnapshotRepository understandingRepository,
        ProjectMemorySearchService memorySearchService,
        ObjectMapper objectMapper
    ) {
        this.projectRepository = projectRepository;
        this.memoryRepository = memoryRepository;
        this.factRepository = factRepository;
        this.candidateRepository = candidateRepository;
        this.understandingRepository = understandingRepository;
        this.memorySearchService = memorySearchService;
        this.objectMapper = objectMapper;
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
        ProjectSpace project = ownedProject(userId, projectId);
        int budget = Math.max(2_000, Math.min(32_000, requestedBudget <= 0 ? 8_000 : requestedBudget));
        ProjectUnderstandingSnapshotResponse snapshot = snapshotOrNull(projectId);
        List<AgentKnowledgeItem> knowledge = new ArrayList<>(collectKnowledge(projectId, 200));
        List<AgentKnowledgeItem> strong = mutableByStatus(knowledge, Set.of("OBSERVED", "VERIFIED"));
        List<AgentKnowledgeItem> declared = mutableByStatus(knowledge, Set.of("DECLARED", "PROCESS_EVIDENCE"));
        List<AgentKnowledgeItem> inferred = mutableByStatus(knowledge, Set.of("INFERRED"));
        List<AgentKnowledgeItem> conflicts = mutableByStatus(knowledge, Set.of("CONFLICTED"));
        List<AgentKnowledgeItem> unknowns = mutableByStatus(knowledge, Set.of("UNKNOWN"));
        List<AgentEvidenceResponse> evidence = new ArrayList<>();
        if (snapshot != null && snapshot.sourceMap() != null) {
            snapshot.sourceMap().sources().stream().limit(30)
                .map(item -> evidence(projectId, snapshot.sourceRevision(), item))
                .forEach(evidence::add);
        }
        addSnapshotUnknowns(projectId, snapshot, unknowns, conflicts);
        List<AgentKnowledgeItem> verified = new ArrayList<>(strong.stream()
            .filter(item -> "VERIFIED".equals(item.epistemicStatus()))
            .limit(10).toList());
        List<String> limitations = new ArrayList<>();
        if (snapshot == null) limitations.add("尚无持久化 Project Understanding Snapshot");
        if (snapshot != null && snapshot.quality() != null) limitations.addAll(snapshot.quality().limitations());
        String history = snapshot == null || snapshot.historicalCoverage() == null
            ? "UNKNOWN"
            : "availability=" + snapshot.historicalCoverage().availability()
                + ",coverage=" + snapshot.historicalCoverage().overallCoverage()
                + ",from=" + snapshot.historicalCoverage().earliestEvidenceAt()
                + ",to=" + snapshot.historicalCoverage().latestEvidenceAt();
        List<String> provenance = new ArrayList<>();
        strong.forEach(item -> provenance.add(item.itemId()));
        evidence.forEach(item -> provenance.add(item.evidenceId()));
        if (snapshot != null) provenance.add("snapshot:" + snapshot.id());

        boolean truncated = false;
        AgentContextPackageResponse result;
        while (true) {
            result = packageResponse(
                project, snapshot, budget, strong, declared, inferred, conflicts, unknowns,
                evidence, verified, history, limitations, provenance, truncated
            );
            int length = jsonLength(result);
            if (length <= budget
                || !trimOne(evidence, declared, inferred, unknowns, conflicts, verified, strong, provenance)) {
                if (length > budget) limitations.add("固定来源元数据已达到最小包边界");
                result = packageResponse(
                    project, snapshot, budget, strong, declared, inferred, conflicts, unknowns,
                    evidence, verified, history, limitations, provenance, truncated || length > budget
                );
                break;
            }
            truncated = true;
        }
        int actual = jsonLength(result);
        return new AgentContextPackageResponse(
            result.packageVersion(), result.projectId(), result.projectName(), result.projectStatus(),
            result.sourceRevision(), result.generatedAt(), result.currentStrongFacts(), result.declaredMaterial(),
            result.inferredCandidates(), result.conflicts(), result.unknowns(), result.keyEvidence(),
            result.latestVerifiedChanges(), result.historicalCoverage(), result.limitations(), result.provenance(),
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
            fact.getEpistemicStatus().name(), fact.getCurrentness(), fact.getValidationStatus(),
            fact.getEvidenceRefs().stream().limit(30).toList(),
            fact.getLimitations().stream().limit(10).toList(), fact.getEffectiveAt()
        );
    }

    private static AgentKnowledgeItem candidateItem(ProjectAgentCandidate candidate) {
        return new AgentKnowledgeItem(
            candidate.getProjectId(), "candidate:" + candidate.getId(), bounded(candidate.getAssertion(), 600),
            candidate.getEpistemicStatus().name(), candidate.getCurrentness(), candidate.getValidationStatus(),
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
            unknowns.add(derivedItem(projectId, "snapshot:unknown:" + index++, value, "UNKNOWN"));
        }
        if (snapshot.semanticScout() != null) {
            index = 0;
            for (String value : safe(snapshot.semanticScout().potentialConflicts())) {
                conflicts.add(derivedItem(projectId, "snapshot:conflict:" + index++, value, "CONFLICTED"));
            }
        }
    }

    private static AgentKnowledgeItem derivedItem(
        UUID projectId,
        String id,
        String statement,
        String status
    ) {
        return new AgentKnowledgeItem(
            projectId, id, bounded(statement, 500), status, "UNKNOWN",
            "DERIVED_FROM_PERSISTED_UNDERSTANDING", List.of(), List.of(), null
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

    private ProjectSpace ownedProject(UUID userId, UUID projectId) {
        return projectRepository.findByIdAndUserId(projectId, userId)
            .orElseThrow(() -> new AppException("PROJECT_NOT_FOUND", "项目不存在", HttpStatus.NOT_FOUND));
    }

    private AgentContextPackageResponse packageResponse(
        ProjectSpace project,
        ProjectUnderstandingSnapshotResponse snapshot,
        int budget,
        List<AgentKnowledgeItem> strong,
        List<AgentKnowledgeItem> declared,
        List<AgentKnowledgeItem> inferred,
        List<AgentKnowledgeItem> conflicts,
        List<AgentKnowledgeItem> unknowns,
        List<AgentEvidenceResponse> evidence,
        List<AgentKnowledgeItem> verified,
        String history,
        List<String> limitations,
        List<String> provenance,
        boolean truncated
    ) {
        return new AgentContextPackageResponse(
            PACKAGE_VERSION, project.getId(), project.getName(), project.getStatus().name(),
            snapshot == null ? "" : snapshot.sourceRevision(), Instant.now(),
            List.copyOf(strong), List.copyOf(declared), List.copyOf(inferred),
            List.copyOf(conflicts), List.copyOf(unknowns), List.copyOf(evidence),
            List.copyOf(verified), history, List.copyOf(new LinkedHashSet<>(limitations)),
            List.copyOf(new LinkedHashSet<>(provenance)), budget, 0, truncated
        );
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
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 10);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
