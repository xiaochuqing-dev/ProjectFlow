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
        output.update(request.type(), title, content, request.fromDate(), request.toDate(), "local-template");
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
        List<TaskItem> activeTasks = tasks.stream().filter(task -> !task.getStatus().name().equals("DONE")).limit(8).toList();
        String period = periodLabel(fromDate, toDate);
        String sources = sourceSummary(scopedLogs, acceptedChanges, tasks);

        return switch (type) {
            case WEEKLY_REPORT -> """
                # %s 周报

                ## 范围
                - 时间：%s
                - 来源：%s

                ## 已确认进展
                %s

                ## 关键日志证据
                %s

                ## 风险与阻塞
                %s

                ## 可复用成果素材
                %s

                ## 下周重点
                %s
                """.formatted(
                    project.getName(),
                    period,
                    sources,
                    changeBullets(acceptedChanges),
                    logBullets(scopedLogs),
                    riskBullets(scopedLogs, acceptedChanges, blockedLogs),
                    assetBullets(acceptedChanges),
                    taskBullets(activeTasks, "暂无待推进任务。")
                );
            case PROJECT_SUMMARY -> """
                # %s 项目总结

                %s

                ## 技术栈
                %s

                ## 工程证据
                - 项目累计任务：%d
                - 项目累计日志：%d
                - 已完成任务：%d
                - 已确认变更：%d

                ## 已确认变更
                %s

                ## 最近日志
                %s

                ## 当前风险
                %s
                """.formatted(
                    project.getName(),
                    safeText(project.getDescription()),
                    project.getTechStack().isEmpty() ? "暂无技术栈标签。" : String.join(", ", project.getTechStack()),
                    tasks.size(),
                    logs.size(),
                    doneTasks,
                    acceptedChanges.size(),
                    changeBullets(acceptedChanges),
                    logBullets(logs),
                    riskBullets(logs, acceptedChanges, logs.stream().filter(DevLog::isBlocked).count())
                );
            case RESUME_BULLET -> """
                # %s 简历要点

                %s
                - 基于 %d 条任务、%d 条开发记录和 %d 条已确认变更沉淀项目证据，输出时区分候选与已确认事实。
                - 通过结构化日志记录技术取舍、阻塞和验证结果，提升项目复盘与面试讲述的可信度。
                """.formatted(project.getName(), resumeChangeBullets(acceptedChanges, project.getName()), tasks.size(), logs.size(), acceptedChanges.size());
            case README_SECTION -> """
                ## %s

                %s

                ### Confirmed changes
                %s

                ### Evidence
                %s

                ### What it demonstrates
                %s
                """.formatted(
                    project.getName(),
                    safeText(project.getDescription()),
                    changeBullets(acceptedChanges),
                    sources,
                    assetBullets(acceptedChanges)
                );
        };
    }

    private String logBullets(List<DevLog> logs) {
        if (logs.isEmpty()) {
            return "- 暂无日志记录。";
        }
        return logs.stream()
            .limit(5)
            .map(log -> "- " + log.getLogDate() + "：" + log.getTitle() + logDetail(log))
            .reduce((first, second) -> first + "\n" + second)
            .orElse("- 暂无日志记录。");
    }

    private String changeBullets(List<ProjectChange> changes) {
        if (changes.isEmpty()) {
            return "- 暂无已确认变更。";
        }
        return changes.stream()
            .limit(8)
            .map(change -> "- " + change.getTitle() + "（" + change.getChangeKind() + "/" + change.getImpactLevel() + "，来源：" + change.getSourceType() + "）" + changeSummary(change))
            .reduce((first, second) -> first + "\n" + second)
            .orElse("- 暂无已确认变更。");
    }

    private String taskBullets(List<TaskItem> tasks, String fallback) {
        if (tasks.isEmpty()) {
            return "- " + fallback;
        }
        return tasks.stream()
            .map(task -> "- " + task.getTitle() + "（" + task.getStatus() + "/" + task.getPriority() + "）")
            .reduce((first, second) -> first + "\n" + second)
            .orElse("- " + fallback);
    }

    private String riskBullets(List<DevLog> logs, List<ProjectChange> changes, long blockedLogs) {
        List<String> risks = new java.util.ArrayList<>();
        logs.stream()
            .filter(DevLog::isBlocked)
            .limit(4)
            .forEach(log -> risks.add(log.getLogDate() + " 阻塞：" + log.getTitle()));
        changes.stream()
            .map(ProjectChange::getRiskNotes)
            .filter(value -> value != null && !value.isBlank())
            .limit(4)
            .forEach(risks::add);
        if (risks.isEmpty()) {
            return blockedLogs > 0 ? "- 存在阻塞记录，但缺少具体风险说明。" : "- 暂无已确认风险或阻塞。";
        }
        return risks.stream()
            .map(value -> "- " + compact(value))
            .reduce((first, second) -> first + "\n" + second)
            .orElse("- 暂无已确认风险或阻塞。");
    }

    private String assetBullets(List<ProjectChange> changes) {
        List<String> assets = changes.stream()
            .map(ProjectChange::getAssetCandidates)
            .filter(value -> value != null && !value.isBlank())
            .limit(6)
            .toList();
        if (assets.isEmpty()) {
            return "- 暂无已确认成果素材；先在变更审查中确认可展示内容。";
        }
        return assets.stream()
            .map(value -> "- " + compact(value))
            .reduce((first, second) -> first + "\n" + second)
            .orElse("- 暂无已确认成果素材。");
    }

    private String resumeChangeBullets(List<ProjectChange> changes, String projectName) {
        if (changes.isEmpty()) {
            return "- 独立设计并推进 " + projectName + "，覆盖项目空间、任务流转、开发日志与复盘输出。";
        }
        return changes.stream()
            .limit(3)
            .map(change -> "- " + compact(change.getSummary()) + "（证据：" + change.getTitle() + "）")
            .reduce((first, second) -> first + "\n" + second)
            .orElse("- 独立设计并推进 " + projectName + "。");
    }

    private String sourceSummary(List<DevLog> logs, List<ProjectChange> changes, List<TaskItem> tasks) {
        return "每日回顾 " + logs.size() + " 条，已确认变更 " + changes.size() + " 条，任务 " + tasks.size() + " 条。";
    }

    private String periodLabel(LocalDate fromDate, LocalDate toDate) {
        if (fromDate == null && toDate == null) {
            return "全部已记录周期";
        }
        return (fromDate == null ? "开始" : fromDate.toString()) + " 至 " + (toDate == null ? "当前" : toDate.toString());
    }

    private String logDetail(DevLog log) {
        List<String> parts = new java.util.ArrayList<>();
        if (log.getMinutesSpent() != null && log.getMinutesSpent() > 0) {
            parts.add(log.getMinutesSpent() + " 分钟");
        }
        if (log.isBlocked()) {
            parts.add("阻塞");
        }
        if (!log.getTags().isEmpty()) {
            parts.add("标签：" + String.join(", ", log.getTags()));
        }
        return parts.isEmpty() ? "" : "（" + String.join("，", parts) + "）";
    }

    private String changeSummary(ProjectChange change) {
        String summary = compact(change.getSummary());
        return summary.isBlank() ? "" : "：" + summary;
    }

    private String compact(String value) {
        if (value == null) {
            return "";
        }
        String compacted = value.replace("\r", "").replace("\n", " ").trim();
        return compacted.length() > 180 ? compacted.substring(0, 180) + "..." : compacted;
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
            "local-template",
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
