package com.projectflow.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projectflow.dto.AiOutputDtos.AiOutputRequest;
import com.projectflow.dto.AiOutputDtos.AiOutputResponse;
import com.projectflow.dto.AiOutputDtos.ModelUsageRecordResponse;
import com.projectflow.entity.AiOutput;
import com.projectflow.entity.AiOutputType;
import com.projectflow.entity.DevLog;
import com.projectflow.entity.ModelUsageRecord;
import com.projectflow.entity.ProjectChange;
import com.projectflow.entity.ProjectChangeStatus;
import com.projectflow.entity.ProjectSpace;
import com.projectflow.entity.TaskItem;
import com.projectflow.repository.AiOutputRepository;
import com.projectflow.repository.DevLogRepository;
import com.projectflow.repository.ModelUsageRecordRepository;
import com.projectflow.repository.ProjectChangeRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.repository.TaskRepository;
import com.projectflow.support.AppException;

@Service
public class AiOutputService {
    private final AiOutputRepository aiOutputRepository;
    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final DevLogRepository devLogRepository;
    private final ProjectChangeRepository projectChangeRepository;
    private final ModelUsageRecordRepository modelUsageRecordRepository;

    public AiOutputService(
        AiOutputRepository aiOutputRepository,
        ProjectRepository projectRepository,
        TaskRepository taskRepository,
        DevLogRepository devLogRepository,
        ProjectChangeRepository projectChangeRepository,
        ModelUsageRecordRepository modelUsageRecordRepository
    ) {
        this.aiOutputRepository = aiOutputRepository;
        this.projectRepository = projectRepository;
        this.taskRepository = taskRepository;
        this.devLogRepository = devLogRepository;
        this.projectChangeRepository = projectChangeRepository;
        this.modelUsageRecordRepository = modelUsageRecordRepository;
    }

    @Transactional
    public AiOutputResponse generate(UUID userId, UUID projectId, AiOutputRequest request) {
        long startedAt = System.nanoTime();
        ProjectSpace project = findOwnedProject(userId, projectId);
        List<TaskItem> tasks = taskRepository.findByProjectIdOrderByUpdatedAtDesc(project.getId());
        List<DevLog> logs = devLogRepository.findByProjectIdOrderByLogDateDescUpdatedAtDesc(project.getId());
        List<ProjectChange> acceptedChanges = projectChangeRepository.findByProjectIdOrderByCreatedAtDesc(project.getId()).stream()
            .filter(change -> change.getStatus() == ProjectChangeStatus.ACCEPTED)
            .toList();
        String title = title(project.getName(), request.type());
        String content = renderMarkdown(project, tasks, logs, acceptedChanges, request.type(), request.fromDate(), request.toDate());

        AiOutput output = new AiOutput(project.getId());
        output.update(request.type(), title, content, request.fromDate(), request.toDate(), "mock-provider");
        AiOutput savedOutput = aiOutputRepository.save(output);
        modelUsageRecordRepository.save(successUsageRecord(project.getId(), request.type(), content, acceptedChanges, startedAt));
        return toResponse(savedOutput);
    }

    @Transactional(readOnly = true)
    public List<AiOutputResponse> list(UUID userId, UUID projectId) {
        ProjectSpace project = findOwnedProject(userId, projectId);
        return aiOutputRepository.findByProjectIdOrderByCreatedAtDesc(project.getId())
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public AiOutputResponse detail(UUID userId, UUID outputId) {
        AiOutput output = aiOutputRepository.findById(outputId)
            .orElseThrow(() -> new AppException("AI_OUTPUT_NOT_FOUND", "AI output was not found", HttpStatus.NOT_FOUND));
        if (projectRepository.findByIdAndUserId(output.getProjectId(), userId).isEmpty()) {
            throw new AppException("AI_OUTPUT_NOT_FOUND", "AI output was not found", HttpStatus.NOT_FOUND);
        }
        return toResponse(output);
    }

    @Transactional(readOnly = true)
    public List<ModelUsageRecordResponse> listModelUsageRecords(UUID userId, UUID projectId) {
        ProjectSpace project = findOwnedProject(userId, projectId);
        return modelUsageRecordRepository.findByProjectIdOrderByCreatedAtDesc(project.getId())
            .stream()
            .map(this::toResponse)
            .toList();
    }

    private ProjectSpace findOwnedProject(UUID userId, UUID projectId) {
        return projectRepository.findByIdAndUserId(projectId, userId)
            .orElseThrow(() -> new AppException("PROJECT_NOT_FOUND", "Project was not found", HttpStatus.NOT_FOUND));
    }

    private String title(String projectName, AiOutputType type) {
        return switch (type) {
            case WEEKLY_REPORT -> projectName + " 周报";
            case PROJECT_SUMMARY -> projectName + " 项目总结";
            case RESUME_BULLET -> projectName + " 简历要点";
            case README_SECTION -> projectName + " README 段落";
        };
    }

    private String renderMarkdown(
        ProjectSpace project,
        List<TaskItem> tasks,
        List<DevLog> logs,
        List<ProjectChange> acceptedChanges,
        AiOutputType type,
        LocalDate fromDate,
        LocalDate toDate
    ) {
        List<DevLog> scopedLogs = logs.stream()
            .filter(log -> fromDate == null || !log.getLogDate().isBefore(fromDate))
            .filter(log -> toDate == null || !log.getLogDate().isAfter(toDate))
            .toList();
        long doneTasks = tasks.stream().filter(task -> task.getStatus().name().equals("DONE")).count();
        long blockedLogs = scopedLogs.stream().filter(DevLog::isBlocked).count();

        return switch (type) {
            case WEEKLY_REPORT -> """
                # %s 周报

                ## 本周进展
                - 推进任务 %d 个，完成任务 %d 个。
                - 新增开发日志 %d 条，其中阻塞记录 %d 条。

                ## 关键记录
                %s

                ## 已确认变更
                %s

                ## 下周重点
                - 优先处理仍在进行中或待验收的任务。
                - 将关键日志和已确认变更整理为项目复盘材料。
                """.formatted(project.getName(), tasks.size(), doneTasks, scopedLogs.size(), blockedLogs, logBullets(scopedLogs), changeBullets(acceptedChanges));
            case PROJECT_SUMMARY -> """
                # %s 项目总结

                %s

                ## 技术栈
                %s

                ## 工程过程
                - 项目累计任务：%d
                - 项目累计日志：%d
                - 已完成任务：%d

                ## 已确认变更
                %s

                ## 可展示亮点
                %s
                """.formatted(project.getName(), safeText(project.getDescription()), String.join(", ", project.getTechStack()), tasks.size(), logs.size(), doneTasks, changeBullets(acceptedChanges), logBullets(logs));
            case RESUME_BULLET -> """
                # %s 简历要点

                - 独立设计并推进 %s，覆盖项目空间、任务流转、开发日志与复盘输出。
                - 基于 %d 条任务和 %d 条开发记录沉淀工程过程，支持将真实开发活动转化为作品集素材。
                - 基于 %d 条已确认变更输出项目成果，避免把未确认候选写成作品事实。
                - 通过结构化日志记录技术取舍、阻塞和验证结果，提升项目复盘与面试讲述的可信度。
                """.formatted(project.getName(), project.getName(), tasks.size(), logs.size(), acceptedChanges.size());
            case README_SECTION -> """
                ## %s

                %s

                ### Confirmed changes
                %s

                ### What it demonstrates
                - Project workflow modeling
                - Task state tracking
                - Structured development logging
                - AI-ready reflection outputs
                """.formatted(project.getName(), safeText(project.getDescription()), changeBullets(acceptedChanges));
        };
    }

    private String logBullets(List<DevLog> logs) {
        if (logs.isEmpty()) {
            return "- 暂无日志记录。";
        }
        return logs.stream()
            .limit(5)
            .map(log -> "- " + log.getLogDate() + "：" + log.getTitle())
            .reduce((first, second) -> first + "\n" + second)
            .orElse("- 暂无日志记录。");
    }

    private String changeBullets(List<ProjectChange> changes) {
        if (changes.isEmpty()) {
            return "- 暂无已确认变更。";
        }
        return changes.stream()
            .limit(8)
            .map(change -> "- " + change.getTitle() + "（来源：" + change.getSourceType() + "）")
            .reduce((first, second) -> first + "\n" + second)
            .orElse("- 暂无已确认变更。");
    }

    private String safeText(String value) {
        return value == null || value.isBlank() ? "暂无项目简介。" : value;
    }

    private ModelUsageRecord successUsageRecord(
        UUID projectId,
        AiOutputType type,
        String content,
        List<ProjectChange> acceptedChanges,
        long startedAt
    ) {
        int completionTokens = estimateTokens(content);
        return new ModelUsageRecord(
            projectId,
            "AI_OUTPUT_" + type.name(),
            "mock-provider",
            "local-template",
            0,
            completionTokens,
            true,
            Math.max(0, (System.nanoTime() - startedAt) / 1_000_000),
            "SUCCEEDED",
            null,
            null,
            qualityWarnings(content, acceptedChanges)
        );
    }

    private int estimateTokens(String content) {
        if (content == null || content.isBlank()) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(content.length() / 4.0));
    }

    private String qualityWarnings(String content, List<ProjectChange> acceptedChanges) {
        List<String> warnings = new java.util.ArrayList<>();
        if (content == null || content.chars().noneMatch(character -> Character.UnicodeScript.of(character) == Character.UnicodeScript.HAN)) {
            warnings.add("输出缺少中文内容");
        }
        if (!acceptedChanges.isEmpty() && !content.contains("来源：")) {
            warnings.add("已确认事实缺少来源标注");
        }
        if (content != null && (content.contains("未确认候选") || content.contains("候选事实"))) {
            warnings.add("输出疑似引用未确认事实");
        }
        return String.join("\n", warnings);
    }

    private AiOutputResponse toResponse(AiOutput output) {
        return new AiOutputResponse(
            output.getId(),
            output.getProjectId(),
            output.getType(),
            output.getTitle(),
            output.getContent(),
            output.getFromDate(),
            output.getToDate(),
            output.getProvider(),
            output.getCreatedAt(),
            output.getUpdatedAt()
        );
    }

    private ModelUsageRecordResponse toResponse(ModelUsageRecord record) {
        return new ModelUsageRecordResponse(
            record.getId(),
            record.getProjectId(),
            record.getOperation(),
            record.getProviderName(),
            record.getModelName(),
            record.getPromptTokens(),
            record.getCompletionTokens(),
            record.getTotalTokens(),
            record.isUsageEstimated(),
            record.getLatencyMs(),
            record.getStatus(),
            record.getErrorType(),
            record.getErrorMessage(),
            record.getQualityWarnings(),
            record.getCreatedAt()
        );
    }
}
