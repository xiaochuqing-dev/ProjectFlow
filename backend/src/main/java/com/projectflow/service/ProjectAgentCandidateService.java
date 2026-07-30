package com.projectflow.service;

import static com.projectflow.dto.ProjectAgentCandidateDtos.*;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projectflow.dto.ProjectUnderstandingDtos.ProjectUnderstandingSnapshotResponse;
import com.projectflow.entity.ProjectAgentCandidate;
import com.projectflow.entity.ProjectFactEpistemicStatus;
import com.projectflow.repository.ProjectAgentCandidateRepository;
import com.projectflow.repository.ProjectFactRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.support.AppException;

@Service
public class ProjectAgentCandidateService {
    private final ProjectRepository projectRepository;
    private final ProjectFactRepository factRepository;
    private final ProjectAgentCandidateRepository candidateRepository;
    private final ProjectUnderstandingService understandingService;
    private final SensitiveContentRedactor redactor;

    public ProjectAgentCandidateService(
        ProjectRepository projectRepository,
        ProjectFactRepository factRepository,
        ProjectAgentCandidateRepository candidateRepository,
        ProjectUnderstandingService understandingService,
        SensitiveContentRedactor redactor
    ) {
        this.projectRepository = projectRepository;
        this.factRepository = factRepository;
        this.candidateRepository = candidateRepository;
        this.understandingService = understandingService;
        this.redactor = redactor;
    }

    @Transactional
    public AgentCandidateResponse submit(
        UUID userId,
        UUID projectId,
        SubmitAgentCandidateRequest request,
        String caller
    ) {
        ownedProject(userId, projectId);
        ProjectFactEpistemicStatus epistemic = candidateStatus(request.epistemicStatus());
        List<String> evidenceRefs = validateEvidenceRefs(userId, projectId, request.evidenceRefs());
        String sourceAgentId = firstText(caller, request.sourceAgentId(), "unknown-agent");
        ProjectAgentCandidate candidate;
        try {
            candidate = new ProjectAgentCandidate(
                projectId,
                request.candidateType(),
                redactor.redact(request.assertion()),
                epistemic,
                evidenceRefs,
                request.currentness(),
                request.sourceRevision(),
                bounded(request.limitations(), 20).stream().map(redactor::redact).toList(),
                redactor.redact(sourceAgentId)
            );
        } catch (IllegalArgumentException exception) {
            throw new AppException("AGENT_CANDIDATE_INVALID", exception.getMessage(), HttpStatus.BAD_REQUEST);
        }
        return response(candidateRepository.save(candidate));
    }

    @Transactional(readOnly = true)
    public AgentCandidatePageResponse list(UUID userId, UUID projectId, int page, int size) {
        ownedProject(userId, projectId);
        var result = candidateRepository.findByProjectIdOrderByCreatedAtDesc(
            projectId, PageRequest.of(Math.max(0, page), Math.max(1, Math.min(100, size <= 0 ? 20 : size)))
        );
        return new AgentCandidatePageResponse(
            result.getContent().stream().map(ProjectAgentCandidateService::response).toList(),
            result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages()
        );
    }

    private List<String> validateEvidenceRefs(UUID userId, UUID projectId, List<String> requested) {
        if (requested == null || requested.isEmpty()) return List.of();
        Set<String> allowed = new LinkedHashSet<>();
        try {
            ProjectUnderstandingSnapshotResponse snapshot = understandingService.get(userId, projectId);
            if (snapshot.sourceMap() != null) {
                snapshot.sourceMap().sources().forEach(source -> allowed.add(source.id()));
            }
            if (snapshot.analysisExecution() != null) {
                snapshot.analysisExecution().evidence().forEach(evidence -> allowed.add(evidence.id()));
            }
        } catch (AppException ignored) {
            // A project may not have an understanding snapshot yet.
        }
        List<String> result = new ArrayList<>();
        for (String raw : requested) {
            String value = raw == null ? "" : raw.strip();
            if (value.isBlank()) continue;
            if (value.startsWith("fact:")) {
                try {
                    UUID factId = UUID.fromString(value.substring("fact:".length()));
                    if (factRepository.findByIdAndProjectId(factId, projectId).isPresent()) {
                        result.add(value);
                        continue;
                    }
                } catch (RuntimeException ignored) {
                    // handled below
                }
            } else if (allowed.contains(value)) {
                result.add(value);
                continue;
            }
            throw new AppException(
                "AGENT_CANDIDATE_EVIDENCE_INVALID",
                "候选引用了未知或跨项目 Evidence ID",
                HttpStatus.BAD_REQUEST
            );
        }
        return List.copyOf(new LinkedHashSet<>(result));
    }

    private static ProjectFactEpistemicStatus candidateStatus(String value) {
        try {
            ProjectFactEpistemicStatus status = ProjectFactEpistemicStatus.valueOf(
                value == null ? "" : value.strip().toUpperCase(Locale.ROOT)
            );
            if (status.isStrongFact()) throw new IllegalArgumentException();
            return status;
        } catch (RuntimeException exception) {
            throw new AppException(
                "AGENT_CANDIDATE_STATUS_INVALID",
                "Agent 候选只能使用 DECLARED、INFERRED、CONFLICTED、UNKNOWN 或 PROCESS_EVIDENCE",
                HttpStatus.BAD_REQUEST
            );
        }
    }

    private void ownedProject(UUID userId, UUID projectId) {
        projectRepository.findByIdAndUserId(projectId, userId)
            .orElseThrow(() -> new AppException("PROJECT_NOT_FOUND", "项目不存在", HttpStatus.NOT_FOUND));
    }

    private static AgentCandidateResponse response(ProjectAgentCandidate value) {
        return new AgentCandidateResponse(
            value.getId(), value.getProjectId(), value.getCandidateType(), value.getAssertion(),
            value.getEpistemicStatus().name(), value.getEvidenceRefs(), value.getCurrentness(),
            value.getSourceRevision(), value.getLimitations(), value.getSourceAgentId(),
            value.getValidationStatus(), value.getCreatedAt()
        );
    }

    private static List<String> bounded(List<String> values, int limit) {
        return values == null ? List.of() : values.stream().filter(java.util.Objects::nonNull)
            .map(String::strip).filter(value -> !value.isBlank()).limit(limit).toList();
    }

    private static String firstText(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value.strip();
        return "";
    }
}
