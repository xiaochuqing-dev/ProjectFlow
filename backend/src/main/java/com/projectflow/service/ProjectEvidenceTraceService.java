package com.projectflow.service;

import static com.projectflow.dto.ProjectMemoryGatewayDtos.*;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projectflow.entity.ChangeBatch;
import com.projectflow.entity.ProjectCapabilityFact;
import com.projectflow.entity.ProjectFact;
import com.projectflow.entity.ProjectFactAgentResultRef;
import com.projectflow.entity.ProjectFactCommitRef;
import com.projectflow.entity.ProjectFactFileRef;
import com.projectflow.repository.ChangeBatchRepository;
import com.projectflow.repository.ProjectCapabilityFactRepository;
import com.projectflow.repository.ProjectFactAgentResultRefRepository;
import com.projectflow.repository.ProjectFactCommitRefRepository;
import com.projectflow.repository.ProjectFactFileRefRepository;
import com.projectflow.repository.ProjectFactRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.support.AppException;

/** 只负责从 ProjectFact 追溯安全证据引用，不暴露 diff、绝对路径或模型原文。 */
@Service
public class ProjectEvidenceTraceService {
    private static final String FACT_TRUTH = "FACTUAL_SOURCE";
    private static final Pattern WINDOWS_ABSOLUTE = Pattern.compile("(?i)^[a-z]:[\\\\/].*");

    private final ProjectRepository projectRepository;
    private final ProjectFactRepository factRepository;
    private final ProjectFactCommitRefRepository commitRepository;
    private final ProjectFactFileRefRepository fileRepository;
    private final ProjectFactAgentResultRefRepository agentRepository;
    private final ChangeBatchRepository batchRepository;
    private final ProjectCapabilityFactRepository capabilityFactRepository;

    public ProjectEvidenceTraceService(
        ProjectRepository projectRepository,
        ProjectFactRepository factRepository,
        ProjectFactCommitRefRepository commitRepository,
        ProjectFactFileRefRepository fileRepository,
        ProjectFactAgentResultRefRepository agentRepository,
        ChangeBatchRepository batchRepository,
        ProjectCapabilityFactRepository capabilityFactRepository
    ) {
        this.projectRepository = projectRepository;
        this.factRepository = factRepository;
        this.commitRepository = commitRepository;
        this.fileRepository = fileRepository;
        this.agentRepository = agentRepository;
        this.batchRepository = batchRepository;
        this.capabilityFactRepository = capabilityFactRepository;
    }

    @Transactional(readOnly = true)
    public MemoryFactTraceResponse trace(UUID userId, UUID projectId, UUID factId, String detailLevel) {
        projectRepository.findByIdAndUserId(projectId, userId)
            .orElseThrow(() -> new AppException("PROJECT_NOT_FOUND", "项目不存在", HttpStatus.NOT_FOUND));
        ProjectFact fact = factRepository.findByIdAndProjectId(factId, projectId)
            .orElseThrow(() -> new AppException("PROJECT_FACT_NOT_FOUND", "项目事实不存在", HttpStatus.NOT_FOUND));
        boolean detailed = "detailed".equalsIgnoreCase(safe(detailLevel));
        int limit = detailed ? 100 : 20;
        ChangeBatch batch = fact.getBatchId() == null ? null : batchRepository.findById(fact.getBatchId()).orElse(null);
        List<ProjectFactCommitRef> commitRefs = commitRepository.findByFactId(factId);
        List<ProjectFactFileRef> fileRefs = fileRepository.findByFactId(factId);
        List<ProjectFactAgentResultRef> agentRefs = agentRepository.findByFactId(factId);
        List<String> commits = limit(commitRefs.stream().map(ProjectFactCommitRef::getCommitSha).toList(), limit);
        List<String> files = limit(fileRefs.stream().map(ProjectFactFileRef::getFilePath)
            .map(this::safeReference).filter(value -> !value.isBlank()).toList(), limit);
        List<String> agents = limit(agentRefs.stream().map(ProjectFactAgentResultRef::getAgentResultRef)
            .map(this::safeReference).filter(value -> !value.isBlank()).toList(), limit);
        List<String> evidence = limit(fact.getEvidenceRefs().stream().map(this::safeEvidence)
            .filter(value -> !value.isBlank()).toList(), limit);
        boolean truncated = commitRefs.size() > commits.size() || fileRefs.size() > files.size()
            || agentRefs.size() > agents.size() || fact.getEvidenceRefs().size() > evidence.size();
        List<UUID> related = relatedCapabilities(projectId, factId);
        return new MemoryFactTraceResponse(
            projectId, factId, text(fact.getTitle(), detailed), text(fact.getSummary(), detailed),
            new MemoryTimeResponse(
                fact.getOccurredFrom(), fact.getOccurredTo(), eventAt(fact), fact.getCreatedAt(),
                batch == null ? null : firstNonNull(batch.getScanFinishedAt(), batch.getScanStartedAt()), null
            ), fact.getRecordStatus().name(), text(fact.getAttentionReason(), detailed), fact.getBatchId(),
            batch == null ? "" : batch.getBranchName(), batch == null ? "" : batch.getScanType(), commits, files,
            agents, evidence, related, truncated, FACT_TRUTH
        );
    }

    private List<UUID> relatedCapabilities(UUID projectId, UUID factId) {
        Map<UUID, LinkedHashSet<UUID>> grouped = new LinkedHashMap<>();
        for (ProjectCapabilityFact link : capabilityFactRepository.findByProjectIdAndFactIdIn(projectId, List.of(factId))) {
            grouped.computeIfAbsent(link.getFactId(), ignored -> new LinkedHashSet<>()).add(link.getCapabilityId());
        }
        return grouped.getOrDefault(factId, new LinkedHashSet<>()).stream().toList();
    }

    private String safeReference(String value) {
        String normalized = safe(value).replace('\\', '/');
        if (normalized.isBlank()) return "";
        if (WINDOWS_ABSOLUTE.matcher(normalized).matches() || normalized.startsWith("/") || normalized.startsWith("//")) {
            String[] parts = normalized.split("/");
            String leaf = parts.length == 0 ? "" : parts[parts.length - 1];
            return leaf.isBlank() ? "[路径已隐藏]" : "[路径已隐藏]/" + leaf;
        }
        if (List.of(normalized.split("/")).contains("..")) return "[越界路径已隐藏]";
        return text(normalized, false);
    }

    private String safeEvidence(String value) {
        String safe = safe(value);
        int separator = safe.indexOf(':');
        if (separator <= 0) return safeReference(safe);
        return safe.substring(0, separator + 1) + safeReference(safe.substring(separator + 1));
    }

    private String text(String value, boolean detailed) {
        String safe = safe(value);
        int max = detailed ? 4_000 : 600;
        return safe.length() <= max ? safe : safe.substring(0, max - 1) + "…";
    }

    private static String safe(String value) { return value == null ? "" : value.trim(); }
    private static Instant eventAt(ProjectFact fact) { return fact.getOccurredTo() == null ? fact.getOccurredFrom() : fact.getOccurredTo(); }
    private static Instant firstNonNull(Instant first, Instant second) { return first == null ? second : first; }
    private static <T> List<T> limit(Collection<T> values, int size) { return values.stream().limit(size).toList(); }
}
