package com.projectflow.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.projectflow.entity.DevLogCategory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class MarkdownImportDtos {
    private MarkdownImportDtos() {
    }

    public record MarkdownPreviewRequest(
        @NotNull UUID projectId,
        @NotBlank @Size(max = 20000) String markdown
    ) {
    }

    public record MarkdownConfirmRequest(
        @NotNull UUID projectId,
        UUID taskId,
        @NotBlank @Size(max = 20000) String markdown
    ) {
    }

    public record MarkdownPreviewResponse(
        Map<String, String> frontMatter,
        String title,
        String content,
        DevLogCategory category,
        LocalDate logDate,
        Integer minutesSpent,
        boolean blocked,
        List<String> tags,
        List<String> warnings
    ) {
    }

    public record ImportRecordResponse(
        UUID id,
        UUID projectId,
        UUID devLogId,
        String title,
        String source,
        List<String> warnings,
        Instant createdAt
    ) {
    }
}
