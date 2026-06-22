package com.projectflow.service;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projectflow.dto.V2ProjectDtos.ProjectAnalysisRecordResponse;
import com.projectflow.dto.V2ProjectDtos.ProjectAnalysisResponse;
import com.projectflow.dto.V2ProjectDtos.ProjectFileAnalysisResponse;
import com.projectflow.entity.ProjectAnalysisRecord;
import com.projectflow.entity.ProjectAnalysisRecordType;
import com.projectflow.entity.ProjectSpace;
import com.projectflow.repository.ProjectAnalysisRecordRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.support.AppException;

@Service
public class ProjectAnalysisRecordService {
    private final ProjectRepository projectRepository;
    private final ProjectAnalysisRecordRepository analysisRecordRepository;

    public ProjectAnalysisRecordService(ProjectRepository projectRepository, ProjectAnalysisRecordRepository analysisRecordRepository) {
        this.projectRepository = projectRepository;
        this.analysisRecordRepository = analysisRecordRepository;
    }

    @Transactional
    public UUID createProjectAnalysisRecord(UUID userId, UUID projectId, ProjectAnalysisResponse response) {
        ProjectSpace project = findOwnedProject(userId, projectId);
        ProjectAnalysisRecord record = new ProjectAnalysisRecord(project.getId(), ProjectAnalysisRecordType.PROJECT);
        record.update(
            null,
            response.summary(),
            projectAnalysisDetails(response),
            response.analysisSource(),
            response.modelUsed(),
            response.providerName(),
            response.confidence()
        );
        return analysisRecordRepository.save(record).getId();
    }

    @Transactional
    public UUID createFileAnalysisRecord(UUID userId, UUID projectId, ProjectFileAnalysisResponse response) {
        ProjectSpace project = findOwnedProject(userId, projectId);
        ProjectAnalysisRecord record = new ProjectAnalysisRecord(project.getId(), ProjectAnalysisRecordType.FILE);
        record.update(
            response.path(),
            response.summary(),
            fileAnalysisDetails(response),
            response.analysisSource(),
            response.modelUsed(),
            response.providerName(),
            response.confidence()
        );
        return analysisRecordRepository.save(record).getId();
    }

    @Transactional(readOnly = true)
    public List<ProjectAnalysisRecordResponse> listAnalysisRecords(UUID userId, UUID projectId) {
        ProjectSpace project = findOwnedProject(userId, projectId);
        return analysisRecordRepository.findByProjectIdOrderByCreatedAtDesc(project.getId())
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public ProjectAnalysisRecordResponse detail(UUID userId, UUID recordId) {
        return toResponse(findOwnedAnalysisRecord(userId, recordId));
    }

    @Transactional
    public void delete(UUID userId, UUID recordId) {
        analysisRecordRepository.delete(findOwnedAnalysisRecord(userId, recordId));
    }

    private ProjectSpace findOwnedProject(UUID userId, UUID projectId) {
        return projectRepository.findByIdAndUserId(projectId, userId)
            .orElseThrow(() -> new AppException("PROJECT_NOT_FOUND", "Project was not found", HttpStatus.NOT_FOUND));
    }

    private ProjectAnalysisRecord findOwnedAnalysisRecord(UUID userId, UUID recordId) {
        ProjectAnalysisRecord record = analysisRecordRepository.findById(recordId)
            .orElseThrow(() -> new AppException("PROJECT_ANALYSIS_RECORD_NOT_FOUND", "Analysis record was not found", HttpStatus.NOT_FOUND));
        findOwnedProject(userId, record.getProjectId());
        return record;
    }

    private String projectAnalysisDetails(ProjectAnalysisResponse response) {
        return String.join("\n\n",
            "架构判断：\n" + response.architecture(),
            "模块：\n" + joinOrNone(response.modules()),
            "风险：\n" + joinOrNone(response.risks()),
            "重要文件：\n" + joinOrNone(response.importantFiles()),
            "分析证据：\n" + joinOrNone(response.evidence()),
            "分析局限：\n" + joinOrNone(response.limitations()),
            "说明：\n" + response.message()
        );
    }

    private String fileAnalysisDetails(ProjectFileAnalysisResponse response) {
        return String.join("\n\n",
            "路径：\n" + response.path(),
            "文件类型：\n" + localizedAnalysisCode(response.fileType()),
            "职责：\n" + response.role(),
            "重要性：\n" + localizedAnalysisCode(response.importance()),
            "风险等级：\n" + localizedAnalysisCode(response.riskLevel()),
            "风险说明：\n" + response.riskNotes(),
            "分析证据：\n" + joinOrNone(response.evidence()),
            "关联文件：\n" + joinOrNone(response.relatedFiles()),
            "分析局限：\n" + response.limitations(),
            "说明：\n" + response.message()
        );
    }

    private String joinOrNone(List<String> values) {
        return values.isEmpty() ? "暂无" : String.join("\n", values.stream().map(value -> "- " + value).toList());
    }

    private String localizedAnalysisCode(String value) {
        return switch (value.toLowerCase()) {
            case "source" -> "源码";
            case "test" -> "测试";
            case "config" -> "配置";
            case "docs" -> "文档";
            case "script" -> "脚本";
            case "asset" -> "资源";
            case "build" -> "构建产物";
            case "env" -> "环境配置";
            case "critical" -> "核心";
            case "important" -> "重要";
            case "normal" -> "一般";
            case "high" -> "高";
            case "medium" -> "中";
            case "low" -> "低";
            case "none" -> "未发现";
            default -> value;
        };
    }

    private ProjectAnalysisRecordResponse toResponse(ProjectAnalysisRecord record) {
        return new ProjectAnalysisRecordResponse(
            record.getId(),
            record.getProjectId(),
            record.getRecordType(),
            record.getFilePath(),
            record.getSummary(),
            record.getDetails(),
            record.getAnalysisSource(),
            record.isModelUsed(),
            record.getProviderName(),
            record.getConfidence(),
            record.getCreatedAt()
        );
    }
}
