package com.projectflow.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projectflow.dto.V2ProjectDtos.EvidenceBundleResponse;
import com.projectflow.dto.V2ProjectDtos.ProjectChangeResponse;
import com.projectflow.entity.EvidenceBundle;
import com.projectflow.entity.ProjectChange;
import com.projectflow.entity.ProjectChangeImpactLevel;
import com.projectflow.entity.ProjectChangeKind;
import com.projectflow.entity.ProjectChangeSourceType;
import com.projectflow.entity.ProjectChangeStatus;
import com.projectflow.repository.EvidenceBundleRepository;
import com.projectflow.repository.ProjectChangeRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.support.AppException;

@Service
public class EvidenceDraftChangeService {
    private static final int PROJECT_CHANGE_TITLE_MAX_LENGTH = 180;

    private final ProjectRepository projectRepository;
    private final EvidenceBundleRepository evidenceBundleRepository;
    private final ProjectChangeRepository projectChangeRepository;
    private final ProjectChangeSchemaRepairService projectChangeSchemaRepairService;

    public EvidenceDraftChangeService(
        ProjectRepository projectRepository,
        EvidenceBundleRepository evidenceBundleRepository,
        ProjectChangeRepository projectChangeRepository,
        ProjectChangeSchemaRepairService projectChangeSchemaRepairService
    ) {
        this.projectRepository = projectRepository;
        this.evidenceBundleRepository = evidenceBundleRepository;
        this.projectChangeRepository = projectChangeRepository;
        this.projectChangeSchemaRepairService = projectChangeSchemaRepairService;
    }

    @Transactional
    public ProjectChangeResponse draftChange(UUID userId, UUID evidenceBundleId) {
        EvidenceBundle bundle = evidenceBundleRepository.findById(evidenceBundleId)
            .orElseThrow(() -> new AppException("EVIDENCE_BUNDLE_NOT_FOUND", "Evidence bundle was not found", HttpStatus.NOT_FOUND));
        projectRepository.findByIdAndUserId(bundle.getProjectId(), userId)
            .orElseThrow(() -> new AppException("PROJECT_NOT_FOUND", "Project was not found", HttpStatus.NOT_FOUND));
        EvidenceBundleResponse evidence = EvidenceBundleResponseFactory.toResponse(bundle, "READY_FOR_CHANGE", "GENERATE_CHANGE", null);
        projectChangeSchemaRepairService.ensureEvidenceBundleSourceTypeAllowed();
        ProjectChange change = projectChangeRepository.findBySourceTypeAndSourceRef(ProjectChangeSourceType.EVIDENCE_BUNDLE, evidenceBundleId.toString())
            .orElseGet(() -> new ProjectChange(bundle.getProjectId(), null));
        if (change.getStatus() == ProjectChangeStatus.ACCEPTED
            || change.getStatus() == ProjectChangeStatus.IGNORED
            || change.getStatus() == ProjectChangeStatus.MERGED) {
            return toResponse(change);
        }
        change.update(
            ProjectChangeSourceType.EVIDENCE_BUNDLE,
            evidenceBundleId.toString(),
            null,
            inferKind(evidence.files()),
            evidence.changedFiles() >= 3 ? ProjectChangeImpactLevel.MAJOR : ProjectChangeImpactLevel.MINOR,
            title(evidence),
            summary(evidence),
            details(evidence),
            bulletLines(evidence.files()),
            evidence.taskIntent(),
            "未采集测试证据。",
            "未采集构建证据。",
            riskNotes(evidence),
            decisionNotes(evidence),
            "",
            assetCandidates(evidence)
        );
        return toResponse(projectChangeRepository.save(change));
    }

    private ProjectChangeKind inferKind(List<String> files) {
        boolean onlyDocs = !files.isEmpty() && files.stream().allMatch(file -> file.endsWith(".md") || file.startsWith("docs/"));
        if (onlyDocs) {
            return ProjectChangeKind.DOCS;
        }
        boolean hasTest = files.stream().anyMatch(file -> file.contains("test") || file.contains("spec"));
        if (hasTest) {
            return ProjectChangeKind.TEST;
        }
        return ProjectChangeKind.CAPABILITY;
    }

    private String title(EvidenceBundleResponse evidence) {
        String base = cleanTaskIntent(evidence.taskIntent().isBlank() ? evidence.branchName() : evidence.taskIntent());
        if (base.isBlank()) {
            base = "整理本轮开发成果";
        }
        if (base.startsWith("实现") || base.startsWith("完成") || base.startsWith("优化") || base.startsWith("修复") || base.startsWith("调整")) {
            base = "更新项目能力：" + base;
        }
        return trimToMax(base, PROJECT_CHANGE_TITLE_MAX_LENGTH);
    }

    private String summary(EvidenceBundleResponse evidence) {
        String intent = cleanTaskIntent(evidence.taskIntent());
        String moduleText = modules(evidence.files()).isEmpty() ? "项目相关模块" : String.join("、", modules(evidence.files()));
        String fileText = evidence.files().stream()
            .filter(file -> !isRuntimeArtifact(file))
            .limit(2)
            .map(this::compactPath)
            .reduce((first, second) -> first + "、" + second)
            .orElse("暂无代表文件");
        if (intent.isBlank()) {
            return "本次整理了 %s 的开发变化，代表文件包括 %s。完整文件、测试和构建证据保留在证据页中，采纳后写入项目档案。".formatted(moduleText, fileText);
        }
        return "本次%s，主要影响 %s，代表文件包括 %s。完整文件、测试和构建证据保留在证据页中，采纳后写入项目档案。".formatted(
            normalizeIntentSentence(intent),
            moduleText,
            fileText
        );
    }

    private String details(EvidenceBundleResponse evidence) {
        List<String> modules = modules(evidence.files());
        String moduleLine = modules.isEmpty() ? "涉及模块：未识别。" : "涉及模块：" + String.join("、", modules) + "。";
        String evidenceLines = String.join("\n", evidence.objectiveEvidence());
        return moduleLine + "\n" + evidenceLines;
    }

    private String riskNotes(EvidenceBundleResponse evidence) {
        if (evidence.agentClaims().isEmpty()) {
            return "没有 Agent Claim，仅基于客观 Git 证据生成保守候选；采纳前建议确认业务含义。";
        }
        if (evidence.objectiveEvidence().stream().noneMatch(line -> line.contains("测试") || line.toLowerCase().contains("test"))) {
            return "证据中未看到明确测试结果，采纳时需要人工确认验证状态。";
        }
        return "";
    }

    private String decisionNotes(EvidenceBundleResponse evidence) {
        if (evidence.files().stream().anyMatch(file -> file.toLowerCase().contains("config") || file.endsWith(".yml") || file.endsWith(".yaml"))) {
            return "本次涉及配置或运行规则变化，采纳后可作为技术决策来源。";
        }
        return "";
    }

    private String assetCandidates(EvidenceBundleResponse evidence) {
        String intent = cleanTaskIntent(evidence.taskIntent());
        if (intent.isBlank()) {
            return "";
        }
        return "成果素材：" + normalizeIntentSentence(intent) + "。";
    }

    private String bulletLines(List<String> values) {
        if (values.isEmpty()) {
            return "- 未记录";
        }
        return String.join("\n", values.stream().map(value -> "- " + value).toList());
    }

    private List<String> modules(List<String> files) {
        LinkedHashSet<String> modules = new LinkedHashSet<>();
        files.stream()
            .filter(file -> !isRuntimeArtifact(file))
            .map(this::moduleName)
            .filter(value -> !value.isBlank())
            .forEach(modules::add);
        return modules.stream().limit(5).toList();
    }

    private String moduleName(String path) {
        String normalized = path.replace("\\", "/");
        if (normalized.startsWith("frontend/") || normalized.contains("/frontend/")) return "前端";
        if (normalized.startsWith("backend/") || normalized.contains("/backend/")) return "后端";
        if (normalized.startsWith("docs/") || normalized.endsWith(".md")) return "文档";
        if (normalized.contains("/test/") || normalized.contains("/tests/") || normalized.contains(".test.") || normalized.contains(".spec.")) return "测试";
        int slashIndex = normalized.indexOf('/');
        return slashIndex > 0 ? normalized.substring(0, slashIndex) : normalized;
    }

    private boolean isRuntimeArtifact(String path) {
        String lower = path.replace("\\", "/").toLowerCase();
        return lower.startsWith("node_modules/")
            || lower.contains("/node_modules/")
            || lower.startsWith(".next/")
            || lower.contains("/.next/")
            || lower.startsWith("target/")
            || lower.contains("/target/")
            || lower.startsWith("dist/")
            || lower.contains("/dist/")
            || lower.startsWith("build/")
            || lower.contains("/build/")
            || lower.startsWith(".git/")
            || lower.contains("/.git/")
            || lower.startsWith(".codex-run/")
            || lower.contains("/.codex-run/");
    }

    private String compactPath(String path) {
        String normalized = path.replace("\\", "/");
        if (normalized.length() <= 72) {
            return normalized;
        }
        int slashIndex = normalized.indexOf('/');
        int lastSlashIndex = normalized.lastIndexOf('/');
        if (slashIndex > 0 && lastSlashIndex > slashIndex) {
            return normalized.substring(0, slashIndex) + "/.../" + normalized.substring(lastSlashIndex + 1);
        }
        return "..." + normalized.substring(normalized.length() - 69);
    }

    private String cleanTaskIntent(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value
            .replaceFirst("^Uncommitted working tree changes[:：]?\\s*", "")
            .replaceFirst("^更新\\s+[^：:]+[：:]\\s*", "")
            .trim();
    }

    private String normalizeIntentSentence(String value) {
        String cleaned = cleanTaskIntent(value);
        if (cleaned.isBlank()) {
            return "记录本轮开发成果";
        }
        String withoutPeriod = cleaned.replaceAll("[。；;\\s]+$", "");
        if (withoutPeriod.startsWith("本次")) {
            return withoutPeriod;
        }
        return withoutPeriod;
    }

    private String trimToMax(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 3).trim() + "...";
    }

    private ProjectChangeResponse toResponse(ProjectChange change) {
        return new ProjectChangeResponse(
            change.getId(),
            change.getProjectId(),
            change.getMaterialId(),
            change.getLinkedSuggestionId(),
            change.getSourceType(),
            change.getSourceRef(),
            change.getChangeKind(),
            change.getImpactLevel(),
            change.getStatus(),
            change.getTitle(),
            change.getSummary(),
            change.getDetails(),
            change.getAffectedFiles(),
            change.getRelatedTasks(),
            change.getTestEvidence(),
            change.getBuildEvidence(),
            change.getRiskNotes(),
            change.getDecisionNotes(),
            change.getLearningNotes(),
            change.getAssetCandidates(),
            change.getCreatedAt(),
            change.getUpdatedAt(),
            change.getReviewedAt()
        );
    }
}
