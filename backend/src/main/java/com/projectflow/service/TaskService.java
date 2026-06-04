package com.projectflow.service;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projectflow.dto.TaskDtos.TaskRequest;
import com.projectflow.dto.TaskDtos.TaskResponse;
import com.projectflow.entity.ProjectSpace;
import com.projectflow.entity.TaskItem;
import com.projectflow.entity.TaskStatus;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.repository.TaskRepository;
import com.projectflow.support.AppException;

@Service
public class TaskService {
    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;

    public TaskService(TaskRepository taskRepository, ProjectRepository projectRepository) {
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
    }

    @Transactional
    public TaskResponse create(UUID userId, UUID projectId, TaskRequest request) {
        ProjectSpace project = findOwnedProject(userId, projectId);
        TaskItem task = new TaskItem(project.getId());
        task.update(
            request.title().trim(),
            request.description(),
            request.status(),
            request.priority(),
            request.dueDate(),
            request.tags()
        );
        return toResponse(taskRepository.save(task));
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> list(UUID userId, UUID projectId) {
        ProjectSpace project = findOwnedProject(userId, projectId);
        return taskRepository.findByProjectIdOrderByUpdatedAtDesc(project.getId())
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public TaskResponse detail(UUID userId, UUID taskId) {
        return toResponse(findOwnedTask(userId, taskId));
    }

    @Transactional
    public TaskResponse updateStatus(UUID userId, UUID taskId, TaskStatus nextStatus) {
        TaskItem task = findOwnedTask(userId, taskId);
        if (!canMove(task.getStatus(), nextStatus)) {
            throw new AppException("INVALID_TASK_TRANSITION", "Task status transition is not allowed", HttpStatus.CONFLICT);
        }
        task.moveTo(nextStatus);
        return toResponse(task);
    }

    private ProjectSpace findOwnedProject(UUID userId, UUID projectId) {
        return projectRepository.findByIdAndUserId(projectId, userId)
            .orElseThrow(() -> new AppException("PROJECT_NOT_FOUND", "Project was not found", HttpStatus.NOT_FOUND));
    }

    private TaskItem findOwnedTask(UUID userId, UUID taskId) {
        TaskItem task = taskRepository.findById(taskId)
            .orElseThrow(() -> new AppException("TASK_NOT_FOUND", "Task was not found", HttpStatus.NOT_FOUND));
        if (projectRepository.findByIdAndUserId(task.getProjectId(), userId).isEmpty()) {
            throw new AppException("TASK_NOT_FOUND", "Task was not found", HttpStatus.NOT_FOUND);
        }
        return task;
    }

    private boolean canMove(TaskStatus currentStatus, TaskStatus nextStatus) {
        if (currentStatus == nextStatus) {
            return true;
        }
        return switch (currentStatus) {
            case BACKLOG -> nextStatus == TaskStatus.TODO;
            case TODO -> nextStatus == TaskStatus.IN_PROGRESS;
            case IN_PROGRESS -> nextStatus == TaskStatus.REVIEW;
            case REVIEW -> nextStatus == TaskStatus.DONE || nextStatus == TaskStatus.IN_PROGRESS;
            case DONE -> nextStatus == TaskStatus.REVIEW;
        };
    }

    private TaskResponse toResponse(TaskItem task) {
        return new TaskResponse(
            task.getId(),
            task.getProjectId(),
            task.getTitle(),
            task.getDescription(),
            task.getStatus(),
            task.getPriority(),
            task.getDueDate(),
            task.getTags(),
            task.getCreatedAt(),
            task.getUpdatedAt()
        );
    }
}
