package com.projectflow.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.projectflow.entity.DevLogCategory;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class DevLogDtos {
    private DevLogDtos() {
    }

    public record DevLogRequest(
        UUID taskId,
        @NotBlank @Size(max = 180) String title,
        @NotBlank @Size(max = 8000) String content,
        @NotNull DevLogCategory category,
        @NotNull LocalDate logDate,
        @NotNull @Min(0) Integer minutesSpent,
        boolean blocked,
        List<@Size(max = 60) String> tags
    ) {
    }

    public record DevLogResponse(
        UUID id,
        UUID projectId,
        UUID taskId,
        String title,
        String content,
        DevLogCategory category,
        LocalDate logDate,
        Integer minutesSpent,
        boolean blocked,
        List<String> tags,
        Instant createdAt,
        Instant updatedAt
    ) {
    }
}
