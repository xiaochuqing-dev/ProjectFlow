package com.projectflow.service;

import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projectflow.dto.ProjectEvolutionBridgeDtos.EvolutionBridgePageResponse;
import com.projectflow.dto.ProjectEvolutionBridgeDtos.EvolutionBridgeResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.GitEvidenceResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.ProjectStructureIndexResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.StructureFunctionalArea;
import com.projectflow.dto.ProjectUnderstandingDtos.StructureModuleNode;
import com.projectflow.entity.ProjectEvolutionBridge;
import com.projectflow.entity.ProjectFact;
import com.projectflow.entity.ProjectFactRecordStatus;
import com.projectflow.repository.ProjectEvolutionBridgeRepository;
import com.projectflow.repository.ProjectFactRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.support.AppException;

@Service
public class ProjectEvolutionBridgeService {
    private static final Pattern COMMIT = Pattern.compile("^[0-9a-fA-F]{7,64}$");
    private static final int MAX_BRIDGES_PER_REFRESH = 20;
    private static final int MAX_CHANGED_PATHS = 500;

    private final ProjectRepository projectRepository;
    private final ProjectFactRepository factRepository;
    private final ProjectEvolutionBridgeRepository bridgeRepository;
    private final LocalCommandExecutor commandExecutor;

    public ProjectEvolutionBridgeService(
        ProjectRepository projectRepository,
        ProjectFactRepository factRepository,
        ProjectEvolutionBridgeRepository bridgeRepository,
        LocalCommandExecutor commandExecutor
    ) {
        this.projectRepository = projectRepository;
        this.factRepository = factRepository;
        this.bridgeRepository = bridgeRepository;
        this.commandExecutor = commandExecutor;
    }

    public BuildResult rebuild(
        UUID projectId,
        Path root,
        GitEvidenceResponse git,
        ProjectStructureIndexResponse previous,
        ProjectStructureIndexResponse current,
        Set<String> dirtyPaths
    ) {
        if (git == null || !git.available() || dirtyPaths == null || dirtyPaths.isEmpty()) {
            return new BuildResult(0, 0, "SKIPPED_NO_GIT_OR_DELTA");
        }
        int created = 0;
        int inspected = 0;
        for (ProjectFact fact : factRepository.findTop200ByProjectIdOrderByOccurredToDescCreatedAtDesc(projectId)) {
            if (created >= MAX_BRIDGES_PER_REFRESH) break;
            Set<String> factPaths = normalized(fact.getAffectedFiles());
            if (fact.getCommitRefs().isEmpty()) continue;
            if (!factPaths.isEmpty() && disjoint(factPaths, dirtyPaths)) continue;
            inspected++;
            BridgeCandidate candidate = candidate(root, fact, factPaths, dirtyPaths, previous, current);
            if (candidate == null
                || bridgeRepository.existsByProjectIdAndBridgeFingerprint(projectId, candidate.fingerprint())) {
                continue;
            }
            ProjectEvolutionBridge bridge = new ProjectEvolutionBridge(projectId, candidate.fingerprint());
            bridge.describe(
                fact.getOccurredTo(),
                candidate.beforeRevision(),
                candidate.afterRevision(),
                previous == null ? "NOT_INDEXED" : previous.indexVersion(),
                current.indexVersion(),
                bounded(fact.getTitle() + "：" + fact.getSummary(), 2_000),
                candidate.area().id(),
                candidate.area().label(),
                candidate.beforeState(),
                candidate.afterState(),
                candidate.epistemicStatus(),
                candidate.confidence(),
                List.of(fact.getId().toString()),
                List.of(candidate.afterRevision()),
                candidate.changedPaths(),
                candidate.evidenceRefs()
            );
            bridgeRepository.save(bridge);
            created++;
        }
        return new BuildResult(created, inspected, created > 0 ? "SUCCEEDED" : "NO_MATCHING_FACT");
    }

    @Transactional(readOnly = true)
    public EvolutionBridgePageResponse list(UUID userId, UUID projectId, int page, int size) {
        projectRepository.findByIdAndUserId(projectId, userId)
            .orElseThrow(() -> new AppException("PROJECT_NOT_FOUND", "项目不存在", HttpStatus.NOT_FOUND));
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(100, size <= 0 ? 20 : size));
        Page<ProjectEvolutionBridge> source = bridgeRepository
            .findByProjectIdOrderByOccurredAtDescCreatedAtDesc(projectId, PageRequest.of(safePage, safeSize));
        return new EvolutionBridgePageResponse(
            source.getContent().stream().map(this::response).toList(),
            source.getNumber(),
            source.getSize(),
            source.getTotalElements(),
            source.getTotalPages(),
            source.hasNext()
        );
    }

    private BridgeCandidate candidate(
        Path root,
        ProjectFact fact,
        Set<String> factPaths,
        Set<String> dirtyPaths,
        ProjectStructureIndexResponse previous,
        ProjectStructureIndexResponse current
    ) {
        for (String rawCommit : fact.getCommitRefs()) {
            String commit = rawCommit == null ? "" : rawCommit.trim();
            if (!COMMIT.matcher(commit).matches()) continue;
            String verified = output(root, List.of("git", "rev-parse", "--verify", commit + "^{commit}"), 5);
            if (!COMMIT.matcher(verified).matches()) continue;
            String parent = output(root, List.of("git", "rev-parse", verified + "^"), 5);
            if (!COMMIT.matcher(parent).matches()) continue;
            Set<String> actualChanged = changedPaths(root, parent, verified);
            if (!factPaths.isEmpty()) actualChanged.retainAll(factPaths);
            actualChanged.retainAll(dirtyPaths);
            if (actualChanged.isEmpty()) continue;
            StructureFunctionalArea area = area(current, previous, actualChanged);
            boolean beforeIndexed = previous != null && revisionMatches(previous.sourceRevision(), parent);
            boolean afterIndexed = revisionMatches(current.sourceRevision(), verified);
            String beforeState = "Git revision " + shortSha(parent)
                + (beforeIndexed
                    ? "，对应已持久化结构索引 " + previous.indexVersion()
                    : "，该 revision 的深结构快照未持久化");
            String afterState = "Git revision " + shortSha(verified)
                + (afterIndexed ? "，对应结构索引 " + current.indexVersion() : "，已映射到当前结构索引")
                + "；影响 " + area.label() + "，匹配 " + actualChanged.size() + " 个事实文件";
            String epistemic = beforeIndexed && afterIndexed && !"UNASSIGNED".equals(area.id())
                ? "OBSERVED" : "INFERRED";
            String confidence = fact.getRecordStatus() == ProjectFactRecordStatus.RECORDED
                && !"UNASSIGNED".equals(area.id()) ? "HIGH" : "MEDIUM";
            LinkedHashSet<String> evidence = new LinkedHashSet<>(fact.getEvidenceRefs());
            evidence.add("git:" + verified);
            evidence.addAll(area.evidenceRefs());
            String fingerprint = sha256(String.join("|", List.of(
                fact.getId().toString(), parent, verified, area.id(), current.indexVersion()
            )));
            return new BridgeCandidate(
                fingerprint,
                parent,
                verified,
                area,
                beforeState,
                afterState,
                epistemic,
                confidence,
                actualChanged.stream().sorted().limit(MAX_CHANGED_PATHS).toList(),
                evidence.stream().limit(100).toList()
            );
        }
        return null;
    }

    private Set<String> changedPaths(Path root, String before, String after) {
        String output = output(root, List.of(
            "git", "diff-tree", "--no-commit-id", "--name-status", "-r",
            "--diff-filter=ACDMRT", before, after
        ), 10);
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String line : output.lines().limit(MAX_CHANGED_PATHS).toList()) {
            String[] parts = line.split("\\t");
            if (parts.length < 2) continue;
            for (int index = 1; index < parts.length; index++) {
                String path = normalize(parts[index]);
                if (!path.isBlank()) result.add(path);
            }
        }
        return result;
    }

    private static StructureFunctionalArea area(
        ProjectStructureIndexResponse current,
        ProjectStructureIndexResponse previous,
        Set<String> paths
    ) {
        List<StructureFunctionalArea> candidates = new ArrayList<>();
        if (current.functionalAreas() != null) candidates.addAll(current.functionalAreas());
        if (previous != null && previous.functionalAreas() != null) candidates.addAll(previous.functionalAreas());
        return candidates.stream()
            .map(area -> new AreaMatch(area, area.memberPaths().stream().filter(paths::contains).count()))
            .filter(match -> match.matches() > 0)
            .sorted(Comparator.comparingLong(AreaMatch::matches).reversed()
                .thenComparing(match -> match.area().id()))
            .map(AreaMatch::area)
            .findFirst()
            .orElseGet(() -> moduleArea(current, previous, paths));
    }

    private static StructureFunctionalArea moduleArea(
        ProjectStructureIndexResponse current,
        ProjectStructureIndexResponse previous,
        Set<String> paths
    ) {
        List<StructureModuleNode> modules = new ArrayList<>();
        if (current.modules() != null) modules.addAll(current.modules());
        if (previous != null && previous.modules() != null) modules.addAll(previous.modules());
        return modules.stream()
            .map(module -> new ModuleMatch(module, paths.stream().filter(path -> inModule(path, module.path())).count()))
            .filter(match -> match.matches() > 0)
            .sorted(Comparator.comparingLong(ModuleMatch::matches).reversed()
                .thenComparing(match -> match.module().id()))
            .map(match -> new StructureFunctionalArea(
                match.module().id(),
                "结构模块 " + match.module().path(),
                "MEDIUM",
                paths.stream().filter(path -> inModule(path, match.module().path())).sorted().limit(200).toList(),
                List.of(),
                0,
                match.module().evidenceRefs().stream().limit(20).toList(),
                "MANIFEST_FILESYSTEM_MODULE"
            ))
            .findFirst()
            .orElseGet(() -> new StructureFunctionalArea(
                "UNASSIGNED", "未形成可证实的结构区域", "LOW",
                paths.stream().sorted().limit(200).toList(), List.of(), 0, List.of(), "UNAVAILABLE"
            ));
    }

    private static boolean inModule(String path, String modulePath) {
        if (path == null || modulePath == null || modulePath.isBlank()) return false;
        String module = normalize(modulePath);
        return !module.isBlank() && (path.equals(module) || path.startsWith(module + "/"));
    }

    private EvolutionBridgeResponse response(ProjectEvolutionBridge value) {
        return new EvolutionBridgeResponse(
            value.getId(),
            value.getProjectId(),
            value.getOccurredAt(),
            value.getBeforeRevision(),
            value.getAfterRevision(),
            value.getBeforeStructureVersion(),
            value.getAfterStructureVersion(),
            value.getMeaningfulChange(),
            value.getAffectedAreaId(),
            value.getAffectedAreaLabel(),
            value.getBeforeState(),
            value.getAfterState(),
            value.getEpistemicStatus(),
            value.getConfidence(),
            value.getSourceFactIds().stream().map(UUID::fromString).toList(),
            value.getSourceCommitRefs(),
            value.getChangedPaths(),
            value.getEvidenceRefs(),
            value.getGenerationVersion(),
            value.getCreatedAt()
        );
    }

    private String output(Path root, List<String> command, int timeoutSeconds) {
        LocalCommandExecutor.CommandResult result = commandExecutor.execute(root, command, Duration.ofSeconds(timeoutSeconds));
        return result.timedOut() || result.exitCode() != 0 ? "" : result.output().trim();
    }

    private static boolean revisionMatches(String sourceRevision, String commit) {
        return sourceRevision != null && commit != null
            && sourceRevision.toLowerCase(Locale.ROOT).contains(commit.toLowerCase(Locale.ROOT));
    }

    private static Set<String> normalized(List<String> paths) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (paths != null) paths.stream().map(ProjectEvolutionBridgeService::normalize)
            .filter(path -> !path.isBlank()).forEach(result::add);
        return result;
    }

    private static String normalize(String path) {
        if (path == null) return "";
        String value = path.trim().replace('\\', '/');
        while (value.startsWith("./")) value = value.substring(2);
        return value.startsWith("/") || value.contains("../") ? "" : value;
    }

    private static boolean disjoint(Set<String> left, Set<String> right) {
        return left.stream().noneMatch(right::contains);
    }

    private static String shortSha(String value) {
        return value == null || value.length() <= 12 ? value : value.substring(0, 12);
    }

    private static String bounded(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))
            );
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public record BuildResult(int createdCount, int inspectedFactCount, String status) {
    }

    private record AreaMatch(StructureFunctionalArea area, long matches) {
    }

    private record ModuleMatch(StructureModuleNode module, long matches) {
    }

    private record BridgeCandidate(
        String fingerprint,
        String beforeRevision,
        String afterRevision,
        StructureFunctionalArea area,
        String beforeState,
        String afterState,
        String epistemicStatus,
        String confidence,
        List<String> changedPaths,
        List<String> evidenceRefs
    ) {
    }
}
