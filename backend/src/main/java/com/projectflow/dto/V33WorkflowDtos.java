package com.projectflow.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.projectflow.entity.SedimentAction;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class V33WorkflowDtos {
    private V33WorkflowDtos() {
    }

    public record ChangeBatchResponse(
        UUID id,
        UUID projectId,
        Instant scanStartedAt,
        Instant scanFinishedAt,
        String baseCommitSha,
        String headCommitSha,
        String branchName,
        int newCommitCount,
        int changedFileCount,
        int agentResultCount,
        int segmentCount,
        String status,
        List<String> warnings,
        boolean firstScan,
        String scanFingerprint,
        boolean worktreeDirty,
        String githubStatus,
        String remoteRelation,
        String segmentationMode,
        String modelStatus,
        String modelProvider,
        String fallbackReason,
        long gitScanMs,
        long modelSegmentMs,
        long githubInspectMs,
        long totalScanMs,
        String analysisScope
    ) {
    }

    public record DevelopmentSegmentResponse(
        UUID id,
        UUID projectId,
        UUID batchId,
        String title,
        String plainSummary,
        List<String> mainChanges,
        String userVisibleValue,
        List<String> includedCommitRefs,
        List<String> includedAgentResultRefs,
        List<String> affectedFiles,
        List<String> evidenceRefs,
        String confidence,
        String status,
        Instant createdAt,
        Instant updatedAt,
        String generationMode,
        String modelProvider,
        String fallbackReason,
        String qualityStatus,
        String qualityReason,
        List<String> commitUrls,
        List<String> uncertainties
    ) {
    }

    public record SedimentConfirmRequest(
        @NotNull SedimentAction action,
        UUID targetSedimentId
    ) {
    }

    public record ProjectSedimentPatchRequest(
        @Size(max = 20_000) String developerNotes
    ) {
    }

    public record ProjectSedimentResponse(
        UUID id,
        UUID projectId,
        String title,
        String summary,
        String problemSolved,
        String sedimentType,
        String status,
        List<String> sourceSegmentIds,
        List<String> evidenceRefs,
        String developerNotes,
        boolean legacyTruncated,
        Instant createdAt,
        Instant updatedAt
    ) {
    }

    public record SedimentConfirmationResponse(
        UUID changeId,
        String changeStatus,
        ProjectSedimentResponse sediment,
        String batchStatus,
        String actionLabel,
        String resultMessage,
        int evidenceAdded,
        int filesAdded,
        boolean summaryUpdated,
        boolean affectsConfirmedCapabilities,
        boolean usedByNextCapabilityAnalysis,
        String sedimentPath
    ) {
    }

    public record SedimentImpactPreviewResponse(
        UUID changeId,
        SedimentAction action,
        String actionLabel,
        String recommendationReason,
        UUID targetSedimentId,
        String targetTitle,
        String targetSummary,
        Instant targetUpdatedAt,
        int evidenceToAdd,
        int filesToAdd,
        boolean summaryWillUpdate,
        boolean affectsConfirmedCapabilities,
        boolean usedByNextCapabilityAnalysis,
        List<String> updatedFields,
        String consequence
    ) {
    }

    public record CapabilityCardResponse(
        UUID id,
        UUID projectId,
        String name,
        String summary,
        String problemSolved,
        String featureEntry,
        List<String> sourceRefs,
        List<String> evidenceRefs,
        String readmeExpression,
        String resumeExpression,
        String interviewExpression,
        String status,
        String generationMode,
        String modelProvider,
        String fallbackReason,
        UUID analysisJobId,
        boolean legacyResult,
        boolean legacyTruncated,
        Instant createdAt,
        Instant updatedAt
    ) {
    }

    public record CapabilityCardPatchRequest(@NotNull CapabilityCardAction action) {
    }

    public enum CapabilityCardAction {
        CONFIRM,
        IGNORE
    }

    public record AgentBridgeHealthResponse(
        boolean pathAccessible,
        boolean sameGitRepository,
        boolean protocolExists,
        boolean resultsDirectoryExists,
        boolean agentsFileExists,
        boolean entryRulePresent,
        String protocolVersion,
        List<String> detectedRuleFiles,
        List<String> warnings
    ) {
    }

    public record GitHubStatusResponse(
        boolean ghInstalled,
        boolean ghAuthenticated,
        boolean repoDetected,
        String nameWithOwner,
        String url,
        String defaultBranch,
        String currentBranch,
        String visibility,
        String primaryLanguage,
        String remoteUrl,
        String commitUrlTemplate,
        String status,
        String remoteRelation,
        int localAhead,
        int remoteAhead,
        List<String> warnings
    ) {
    }

    // V3.3.3: GitHub 登录指引。ProjectFlow 不读取、不展示、不保存 token。
    public record GitHubLoginGuideResponse(
        boolean ghInstalled,
        String status,
        String command,
        List<String> instructions,
        List<String> warnings
    ) {
    }

    // V3.3.4: 打开登录终端结果。opened=false 时前端回退到复制命令。
    public record GitHubOpenTerminalResponse(
        boolean opened,
        String command,
        String platform,
        List<String> warnings
    ) {
    }
}
