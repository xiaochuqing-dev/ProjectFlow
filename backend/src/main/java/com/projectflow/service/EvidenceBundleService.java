package com.projectflow.service;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projectflow.dto.V2ProjectDtos.EvidenceBundleResponse;
import com.projectflow.entity.EvidenceBundle;
import com.projectflow.entity.ProjectChange;
import com.projectflow.entity.ProjectChangeSourceType;
import com.projectflow.entity.ProjectChangeStatus;
import com.projectflow.entity.ProjectSpace;
import com.projectflow.entity.WorkSession;
import com.projectflow.repository.EvidenceBundleRepository;
import com.projectflow.repository.ProjectChangeRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.repository.WorkSessionRepository;
import com.projectflow.support.AppException;

@Service
public class EvidenceBundleService {
    private final ProjectRepository projectRepository;
    private final WorkSessionRepository workSessionRepository;
    private final EvidenceBundleRepository evidenceBundleRepository;
    private final ProjectChangeRepository projectChangeRepository;

    public EvidenceBundleService(
        ProjectRepository projectRepository,
        WorkSessionRepository workSessionRepository,
        EvidenceBundleRepository evidenceBundleRepository,
        ProjectChangeRepository projectChangeRepository
    ) {
        this.projectRepository = projectRepository;
        this.workSessionRepository = workSessionRepository;
        this.evidenceBundleRepository = evidenceBundleRepository;
        this.projectChangeRepository = projectChangeRepository;
    }

    @Transactional
    public EvidenceBundleResponse createFromWorkSession(UUID userId, UUID workSessionId) {
        WorkSession session = workSessionRepository.findById(workSessionId)
            .orElseThrow(() -> new AppException("WORK_SESSION_NOT_FOUND", "Work session was not found", HttpStatus.NOT_FOUND));
        projectRepository.findByIdAndUserId(session.getProjectId(), userId)
            .orElseThrow(() -> new AppException("PROJECT_NOT_FOUND", "Project was not found", HttpStatus.NOT_FOUND));
        EvidenceBundle bundle = evidenceBundleRepository.findByWorkSessionId(workSessionId)
            .orElseGet(() -> new EvidenceBundle(session.getProjectId(), workSessionId));
        bundle.updateFromWorkSession(session.toResponse());
        return toResponse(evidenceBundleRepository.save(bundle));
    }

    @Transactional(readOnly = true)
    public List<EvidenceBundleResponse> list(UUID userId, UUID projectId) {
        ProjectSpace project = projectRepository.findByIdAndUserId(projectId, userId)
            .orElseThrow(() -> new AppException("PROJECT_NOT_FOUND", "Project was not found", HttpStatus.NOT_FOUND));
        return evidenceBundleRepository.findByProjectIdOrderByUpdatedAtDesc(project.getId()).stream()
            .map(this::toResponse)
            .toList();
    }

    private EvidenceBundleResponse toResponse(EvidenceBundle bundle) {
        return projectChangeRepository.findBySourceTypeAndSourceRef(ProjectChangeSourceType.EVIDENCE_BUNDLE, bundle.getId().toString())
            .map(change -> bundle.toResponse(status(change), nextAction(change), change.getId()))
            .orElseGet(() -> bundle.toResponse("READY_FOR_CHANGE", "GENERATE_CHANGE", null));
    }

    private String status(ProjectChange change) {
        if (change.getStatus() == ProjectChangeStatus.ACCEPTED || change.getStatus() == ProjectChangeStatus.MERGED) {
            return "CHANGE_ACCEPTED";
        }
        if (change.getStatus() == ProjectChangeStatus.IGNORED) {
            return "ARCHIVED";
        }
        return "CHANGE_DRAFTED";
    }

    private String nextAction(ProjectChange change) {
        if (change.getStatus() == ProjectChangeStatus.ACCEPTED || change.getStatus() == ProjectChangeStatus.MERGED) {
            return "VIEW_MEMORY";
        }
        if (change.getStatus() == ProjectChangeStatus.IGNORED) {
            return "NO_ACTION";
        }
        return "REVIEW_CHANGE";
    }
}
