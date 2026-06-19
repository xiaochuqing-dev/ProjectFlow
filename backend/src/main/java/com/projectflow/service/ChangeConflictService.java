package com.projectflow.service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projectflow.dto.V2ProjectDtos.ChangeConflictResponse;
import com.projectflow.entity.EvidenceBundle;
import com.projectflow.entity.ProjectSpace;
import com.projectflow.repository.EvidenceBundleRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.support.AppException;

@Service
public class ChangeConflictService {
    private final ProjectRepository projectRepository;
    private final EvidenceBundleRepository evidenceBundleRepository;

    public ChangeConflictService(ProjectRepository projectRepository, EvidenceBundleRepository evidenceBundleRepository) {
        this.projectRepository = projectRepository;
        this.evidenceBundleRepository = evidenceBundleRepository;
    }

    @Transactional(readOnly = true)
    public List<ChangeConflictResponse> list(UUID userId, UUID projectId) {
        ProjectSpace project = projectRepository.findByIdAndUserId(projectId, userId)
            .orElseThrow(() -> new AppException("PROJECT_NOT_FOUND", "Project was not found", HttpStatus.NOT_FOUND));
        LinkedHashMap<String, List<EvidenceBundle>> byFile = new LinkedHashMap<>();
        for (EvidenceBundle bundle : evidenceBundleRepository.findByProjectIdOrderByUpdatedAtDesc(project.getId())) {
            for (String file : bundle.getFiles()) {
                byFile.computeIfAbsent(file, ignored -> new ArrayList<>()).add(bundle);
            }
        }
        return byFile.entrySet().stream()
            .filter(entry -> entry.getValue().stream().map(EvidenceBundle::getWorkSessionId).distinct().count() > 1)
            .map(entry -> toConflict(project.getId(), entry.getKey(), entry.getValue()))
            .toList();
    }

    private ChangeConflictResponse toConflict(UUID projectId, String file, List<EvidenceBundle> bundles) {
        List<UUID> bundleIds = bundles.stream().map(EvidenceBundle::getId).distinct().toList();
        String seed = projectId + ":" + file + ":" + bundleIds;
        return new ChangeConflictResponse(
            UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString(),
            projectId,
            "FILE_OVERLAP",
            file,
            moduleName(file),
            "MEDIUM",
            "PENDING",
            "多个 Evidence Bundle 覆盖同一文件，需要人工确认是否为连续修改或冲突。",
            bundleIds
        );
    }

    private String moduleName(String file) {
        String normalized = file.replace("\\", "/");
        int slash = normalized.indexOf('/');
        return slash > 0 ? normalized.substring(0, slash) : normalized;
    }
}
