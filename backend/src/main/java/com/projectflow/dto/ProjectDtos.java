package com.projectflow.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.projectflow.entity.ProjectStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class ProjectDtos {
    private ProjectDtos() {
    }

    public record ProjectRequest(
        @NotBlank @Size(max = 160) String name,
        @Size(max = 5000) String description,
        @NotNull ProjectStatus status,
        List<@Size(max = 80) String> techStack,
        @Size(max = 500) String repoUrl,
        LocalDate startDate,
        LocalDate endDate
    ) {
    }

    public record ProjectResponse(
        UUID id,
        String name,
        String description,
        ProjectStatus status,
        List<String> techStack,
        String repoUrl,
        LocalDate startDate,
        LocalDate endDate,
        Instant createdAt,
        Instant updatedAt
    ) {
    }
}
