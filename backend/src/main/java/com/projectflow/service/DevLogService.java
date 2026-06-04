package com.projectflow.service;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projectflow.dto.DevLogDtos.DevLogRequest;
import com.projectflow.dto.DevLogDtos.DevLogResponse;
import com.projectflow.entity.DevLog;
import com.projectflow.entity.ProjectSpace;
import com.projectflow.entity.TaskItem;
import com.projectflow.repository.DevLogRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.repository.TaskRepository;
import com.projectflow.support.AppException;

@Service
public class DevLogService {
    private final DevLogRepository devLogRepository;
    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;

    public DevLogService(DevLogRepository devLogRepository, ProjectRepository projectRepository, TaskRepository taskRepository) {
        this.devLogRepository = devLogRepository;
        this.projectRepository = projectRepository;
        this.taskRepository = taskRepository;
    }

    @Transactional
    public DevLogResponse create(UUID userId, UUID projectId, DevLogRequest request) {
        ProjectSpace project = findOwnedProject(userId, projectId);
        validateTask(project.getId(), request.taskId());

        DevLog devLog = new DevLog(project.getId());
        devLog.update(
            request.taskId(),
            request.title().trim(),
            request.content().trim(),
            request.category(),
            request.logDate(),
            request.minutesSpent(),
            request.blocked(),
            request.tags()
        );
        return toResponse(devLogRepository.save(devLog));
    }

    @Transactional(readOnly = true)
    public List<DevLogResponse> list(UUID userId, UUID projectId) {
        ProjectSpace project = findOwnedProject(userId, projectId);
        return devLogRepository.findByProjectIdOrderByLogDateDescUpdatedAtDesc(project.getId())
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public DevLogResponse detail(UUID userId, UUID logId) {
        return toResponse(findOwnedLog(userId, logId));
    }

    private ProjectSpace findOwnedProject(UUID userId, UUID projectId) {
        return projectRepository.findByIdAndUserId(projectId, userId)
            .orElseThrow(() -> new AppException("PROJECT_NOT_FOUND", "Project was not found", HttpStatus.NOT_FOUND));
    }

    private DevLog findOwnedLog(UUID userId, UUID logId) {
        DevLog devLog = devLogRepository.findById(logId)
            .orElseThrow(() -> new AppException("DEV_LOG_NOT_FOUND", "Dev log was not found", HttpStatus.NOT_FOUND));
        if (projectRepository.findByIdAndUserId(devLog.getProjectId(), userId).isEmpty()) {
            throw new AppException("DEV_LOG_NOT_FOUND", "Dev log was not found", HttpStatus.NOT_FOUND);
        }
        return devLog;
    }

    private void validateTask(UUID projectId, UUID taskId) {
        if (taskId == null) {
            return;
        }
        TaskItem task = taskRepository.findById(taskId)
            .orElseThrow(() -> new AppException("INVALID_DEV_LOG_TASK", "Task does not belong to this project", HttpStatus.BAD_REQUEST));
        if (!task.getProjectId().equals(projectId)) {
            throw new AppException("INVALID_DEV_LOG_TASK", "Task does not belong to this project", HttpStatus.BAD_REQUEST);
        }
    }

    private DevLogResponse toResponse(DevLog devLog) {
        return new DevLogResponse(
            devLog.getId(),
            devLog.getProjectId(),
            devLog.getTaskId(),
            devLog.getTitle(),
            devLog.getContent(),
            devLog.getCategory(),
            devLog.getLogDate(),
            devLog.getMinutesSpent(),
            devLog.isBlocked(),
            devLog.getTags(),
            devLog.getCreatedAt(),
            devLog.getUpdatedAt()
        );
    }
}
