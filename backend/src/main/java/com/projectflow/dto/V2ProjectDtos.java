package com.projectflow.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.projectflow.dto.V33WorkflowDtos.ChangeBatchResponse;
import com.projectflow.dto.V33WorkflowDtos.DevelopmentSegmentResponse;

import com.projectflow.entity.AiSuggestionStatus;
import com.projectflow.entity.AiSuggestionType;
import com.projectflow.entity.MaterialSourceType;
import com.projectflow.entity.ProjectChangeImpactLevel;
import com.projectflow.entity.ProjectChangeKind;
import com.projectflow.entity.ProjectChangeSourceType;
import com.projectflow.entity.ProjectChangeStatus;
import com.projectflow.entity.ProjectFactSourceType;
import com.projectflow.entity.ProjectAnalysisRecordType;
import com.projectflow.entity.ProjectAnalysisJobStatus;
import com.projectflow.entity.ProjectAnalysisJobType;

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

    public record ProjectAnalysisResponse(
        String summary,
        String architecture,
        List<String> modules,
        List<String> risks,
        List<String> importantFiles,
        List<String> evidence,
        List<String> limitations,
        boolean providerConfigured,
        boolean modelUsed,
        String providerName,
        String analysisSource,
        String confidence,
        String message
    ) {
    }

    public record ProjectFileAnalysisRequest(
        @NotBlank @Size(max = 1000) String path
    ) {
    }

    public record ProjectFileAnalysisResponse(
        String path,
        String fileType,
        String role,
        String summary,
        String importance,
        String riskLevel,
        String riskNotes,
        List<String> evidence,
        List<String> relatedFiles,
        String limitations,
        boolean providerConfigured,
        boolean modelUsed,
        String providerName,
        String analysisSource,
        String confidence,
        String message
    ) {
    }

    public record ProjectAnalysisJobResponse(
        UUID id,
        UUID projectId,
        ProjectAnalysisJobType jobType,
        String filePath,
        ProjectAnalysisJobStatus status,
        ProjectAnalysisResponse projectResult,
        ProjectFileAnalysisResponse fileResult,
        CapabilityInterpretResponse capabilityInterpretResult,
        WorkSessionScanResponse workSessionScanResult,
        String errorMessage,
        UUID recordId,
        Instant createdAt,
        Instant updatedAt,
        Instant startedAt,
        Instant completedAt
    ) {
    }

    public record ProjectAnalysisRecordResponse(
        UUID id,
        UUID projectId,
        ProjectAnalysisRecordType recordType,
        String filePath,
        String summary,
        String details,
        String analysisSource,
        boolean modelUsed,
        String providerName,
        String confidence,
        Instant createdAt
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

    public record ProjectMemoryUpdateRequest(
        @NotNull @Size(max = 10000) String positioning,
        @NotNull @Size(max = 5000) String currentStage,
        @NotNull @Size(max = 20000) String completedCapabilities,
        @NotNull @Size(max = 20000) String inProgressCapabilities,
        @NotNull @Size(max = 20000) String currentRisks,
        @NotNull @Size(max = 20000) String technicalDecisions,
        @NotNull @Size(max = 20000) String developerLearnings,
        @NotNull @Size(max = 20000) String showcaseAssets,
        @NotNull @Size(max = 20000) String nextStepSuggestions
    ) {
    }

    public record ProjectLocalPathRequest(
        @NotBlank @Size(max = 1000) String localProjectPath
    ) {
    }

    public record CapabilityInterpretRequest(
        @NotBlank @Size(max = 2000) String capabilityFact
    ) {
    }

    public record CapabilityInterpretResponse(
        boolean degraded,
        String source,
        String message,
        CapabilityCandidate candidate
    ) {
    }

    public record CapabilityCandidate(
        String summary,
        String problem,
        String value,
        String readme,
        String resume,
        String interview
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

    public record WorkSessionScanResponse(
        UUID projectId,
        String projectPath,
        String branchName,
        Instant scannedAt,
        List<WorkSessionCandidateResponse> sessions,
        List<String> warnings,
        ChangeBatchResponse batch,
        List<DevelopmentSegmentResponse> segments,
        boolean firstScan
    ) {
    }

    public record WorkSessionCandidateResponse(
        String sessionId,
        UUID projectId,
        String agentType,
        String agentName,
        String taskIntent,
        String branchName,
        String baseCommit,
        Instant startTime,
        Instant endTime,
        String attributionConfidence,
        String detectionMethod,
        int changedFiles,
        int addedLines,
        int deletedLines,
        List<String> affectedModules,
        List<String> evidence,
        List<String> files
    ) {
    }

    public record WorkSessionPatchRequest(
        @Size(max = 40) String agentType,
        @Size(max = 1000) String taskIntent
    ) {
    }

    public record EvidenceBundleResponse(
        UUID id,
        UUID projectId,
        UUID workSessionId,
        String agentType,
        String taskIntent,
        String branchName,
        String attributionConfidence,
        int changedFiles,
        int addedLines,
        int deletedLines,
        List<String> files,
        List<String> objectiveEvidence,
        List<String> agentClaims,
        List<EvidenceSourceResponse> sources,
        String status,
        String nextAction,
        UUID changeId,
        Instant createdAt,
        Instant updatedAt
    ) {
    }

    public record EvidenceSourceResponse(
        String sourceType,
        String sourceRef,
        String summary
    ) {
    }

    public record AgentSignatureFeedbackResponse(
        UUID id,
        UUID projectId,
        String agentName,
        String originalAgentType,
        String correctedAgentType,
        String correctedTaskIntent,
        String scope,
        Instant createdAt,
        Instant updatedAt
    ) {
    }

    public record ChangeConflictResponse(
        String id,
        UUID projectId,
        String conflictType,
        String filePath,
        String moduleName,
        String severity,
        String status,
        String summary,
        List<UUID> evidenceBundleIds
    ) {
    }

    public record ContextSyncResponse(
        UUID projectId,
        String contextPath,
        List<String> writtenFiles,
        Instant syncedAt
    ) {
    }

    public record ProjectChangeResponse(
        UUID id,
        UUID projectId,
        UUID materialId,
        UUID linkedSuggestionId,
        ProjectChangeSourceType sourceType,
        String sourceRef,
        ProjectChangeKind changeKind,
        ProjectChangeImpactLevel impactLevel,
        ProjectChangeStatus status,
        String title,
        String summary,
        String details,
        String affectedFiles,
        String relatedTasks,
        String testEvidence,
        String buildEvidence,
        String riskNotes,
        String decisionNotes,
        String learningNotes,
        String assetCandidates,
        Instant createdAt,
        Instant updatedAt,
        Instant reviewedAt,
        UUID developmentSegmentId,
        String suggestedAction,
        UUID targetSedimentId,
        String problemSolved,
        List<String> evidenceRefs,
        String confidence,
        boolean needsUserReview
    ) {
    }

    public record ProjectChangePatchRequest(
        @NotNull ProjectChangeKind changeKind,
        @NotNull ProjectChangeImpactLevel impactLevel,
        @NotBlank @Size(max = 180) String title,
        @NotBlank @Size(max = 10000) String summary,
        @NotNull @Size(max = 20000) String details,
        @NotNull @Size(max = 20000) String affectedFiles,
        @NotNull @Size(max = 20000) String relatedTasks,
        @NotNull @Size(max = 20000) String testEvidence,
        @NotNull @Size(max = 20000) String buildEvidence,
        @NotNull @Size(max = 20000) String riskNotes,
        @NotNull @Size(max = 20000) String decisionNotes,
        @NotNull @Size(max = 20000) String learningNotes,
        @NotNull @Size(max = 20000) String assetCandidates
    ) {
    }

    public record ProjectFactSourceResponse(
        UUID id,
        UUID projectId,
        String fieldKey,
        String value,
        ProjectFactSourceType sourceType,
        UUID sourceId,
        String confidence,
        boolean confirmedByUser,
        Instant createdAt,
        Instant updatedAt
    ) {
    }
}
