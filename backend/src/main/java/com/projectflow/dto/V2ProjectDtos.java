package com.projectflow.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.projectflow.entity.AiSuggestionStatus;
import com.projectflow.entity.AiSuggestionType;
import com.projectflow.entity.MaterialSourceType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class V2ProjectDtos {
    private V2ProjectDtos() {
    }

    public record ProjectMaterialTextRequest(
        @NotNull MaterialSourceType sourceType,
        @NotBlank @Size(max = 500000) String content
    ) {
    }

    public record ProjectMaterialResponse(
        UUID id,
        UUID projectId,
        MaterialSourceType sourceType,
        String fileName,
        String content,
        String normalizedSummary,
        Instant createdAt,
        Instant updatedAt
    ) {
    }

    public record AnalyzeMaterialResponse(
        UUID materialId,
        String summary,
        List<AiSuggestionResponse> suggestions
    ) {
    }

    public record ProjectProfileResponse(
        String inferredProjectName,
        String summary,
        List<String> techStack,
        List<String> moduleStructure,
        String currentStage,
        boolean hasReadme,
        boolean hasTests,
        boolean hasStartScript,
        boolean hasDeployConfig,
        boolean looksEmptyShell,
        String mostImportantGap
    ) {
    }

    public record ProjectImportAnalyzeResponse(
        ProjectDtos.ProjectResponse project,
        ProjectMaterialResponse material,
        ProjectProfileResponse projectProfile,
        List<AiSuggestionResponse> suggestions,
        boolean modelEnhancementAvailable,
        boolean providerConfigured
    ) {
    }

    public record AiSuggestionResponse(
        UUID id,
        UUID projectId,
        UUID materialId,
        AiSuggestionType type,
        AiSuggestionStatus status,
        String title,
        String reason,
        Map<String, Object> payload,
        Instant createdAt,
        Instant updatedAt,
        Instant resolvedAt
    ) {
    }

    public record AiSuggestionPatchRequest(
        @NotBlank @Size(max = 180) String title,
        @NotBlank @Size(max = 5000) String reason,
        @NotNull Map<String, Object> payload
    ) {
    }

    public record ApplySuggestionsRequest(
        @NotNull List<UUID> suggestionIds
    ) {
    }

    public record ApplySuggestionsResponse(
        int appliedCount,
        ProjectMemoryResponse memory,
        ProjectSnapshotResponse snapshot,
        ProjectEvolutionRecordResponse evolutionRecord
    ) {
    }

    public record ProjectMemoryResponse(
        UUID id,
        UUID projectId,
        String positioning,
        String currentStage,
        String completedCapabilities,
        String inProgressCapabilities,
        String currentRisks,
        String technicalDecisions,
        String developerLearnings,
        String showcaseAssets,
        String nextStepSuggestions,
        String localProjectPath,
        Integer version,
        Instant createdAt,
        Instant updatedAt
    ) {
    }

    public record ProjectSnapshotResponse(
        UUID id,
        UUID projectId,
        String currentStage,
        String taskStatusSummary,
        String techStackSummary,
        String moduleCompletion,
        String riskSummary,
        String recentAchievements,
        String nextStepSuggestions,
        Integer memoryVersion,
        Instant createdAt
    ) {
    }

    public record ProjectEvolutionRecordResponse(
        UUID id,
        UUID projectId,
        UUID materialId,
        String summary,
        String detectedChanges,
        String keyAchievements,
        String keyIssues,
        String technicalDecisions,
        String developerLearnings,
        String nextSteps,
        Instant createdAt
    ) {
    }

    public record AgentBridgeRequest(
        @NotBlank @Size(max = 1000) String projectPath,
        @Size(max = 10000) String requirements
    ) {
    }

    public record AgentBridgeWriteResponse(
        String projectFlowDir,
        List<String> writtenFiles,
        String globalRule,
        boolean alreadyLinked
    ) {
    }

    public record AgentResultScanResponse(
        int importedResults,
        List<ProjectMaterialResponse> materials,
        List<AiSuggestionResponse> suggestions,
        List<String> warnings
    ) {
    }

    public record AgentTaskBriefResponse(
        UUID taskId,
        String taskDir,
        String briefPath,
        String resultPath,
        String statusPath,
        List<String> writtenFiles
    ) {
    }
}
