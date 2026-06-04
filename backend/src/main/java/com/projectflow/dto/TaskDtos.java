package com.projectflow.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.projectflow.entity.TaskPriority;
import com.projectflow.entity.TaskStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class TaskDtos {
    private TaskDtos() {
    }

    public record TaskRequest(
        @NotBlank @Size(max = 180) String title,
        @Size(max = 5000) String description,
        @NotNull TaskStatus status,
        @NotNull TaskPriority priority,
        LocalDate dueDate,
        List<@Size(max = 60) String> tags
    ) {
    }

    public record TaskStatusRequest(
        @NotNull TaskStatus status
    ) {
    }

    public record TaskResponse(
        UUID id,
        UUID projectId,
        String title,
        String description,
        TaskStatus status,
        TaskPriority priority,
        LocalDate dueDate,
        List<String> tags,
        Instant createdAt,
        Instant updatedAt
    ) {
    }
}
