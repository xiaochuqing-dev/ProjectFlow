package com.projectflow.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.projectflow.dto.V2ProjectDtos.ModelCallDiagnosticsResponse;

public final class ProjectUnderstandingDtos {
    private ProjectUnderstandingDtos() {
    }

    public record ProjectUnderstandingRefreshRequest(
        String deadlineMode,
        Long maxAnalysisDurationSeconds,
        String qualityMode
    ) {
    }

    public record GitEvidenceResponse(
        boolean available,
        String branch,
        String head,
        long commitCount,
        String worktreeState,
        int submoduleCount
    ) {
    }

    public record RepositoryIntakeResponse(
        String classification,
        String scale,
        boolean accessible,
        long fileCount,
        long sourceFileCount,
        long totalBytes,
        long estimatedLoc,
        Map<String, Long> languageDistribution,
        List<String> manifestFiles,
        GitEvidenceResponse git,
        int nestedRepositoryCount,
        boolean monorepo,
        double generatedVendorRatio,
        double binaryRatio,
        double supportedStructureCoverage,
        boolean scanTruncated,
        String metricsSource,
        String sourceRevision,
        String contentHash,
        List<String> warnings
    ) {
    }

    public record StructureFileNode(
        String path,
        String language,
        long lines,
        long bytes,
        boolean generated,
        boolean binary
    ) {
    }

    public record StructureModuleNode(
        String id,
        String path,
        long fileCount,
        long sourceFileCount,
        long estimatedLoc,
        Map<String, Long> languages,
        List<String> evidenceRefs
    ) {
    }

    public record StructureRelation(
        String fromId,
        String toId,
        String type,
        List<String> evidenceRefs
    ) {
    }

    public record StructureEntryPoint(
        String path,
        String kind,
        String confidence,
        String evidenceRef
    ) {
    }

    public record StructureEvidence(
        String id,
        String kind,
        String path,
        String summary
    ) {
    }

    public record StructureCoverage(
        double fileInventory,
        double languageMetrics,
        double manifestCoverage,
        double symbolCoverage,
        String relationScope,
        double overall
    ) {
    }

    public record StructureDelta(
        String mode,
        long addedFileCount,
        long modifiedFileCount,
        long removedFileCount,
        long unchangedFileCount,
        boolean fullInventoryAvailable
    ) {
    }

    public record StructureSymbolNode(
        String id,
        String symbol,
        String displayName,
        String kind,
        String path,
        int startLine,
        int startCharacter,
        boolean external,
        String evidenceRef
    ) {
    }

    public record StructureOccurrence(
        String symbolId,
        String path,
        int startLine,
        int startCharacter,
        int endLine,
        int endCharacter,
        String role,
        String evidenceRef
    ) {
    }

    public record StructureImportantNode(
        String id,
        String nodeType,
        String label,
        String path,
        double score,
        List<String> evidenceRefs
    ) {
    }

    public record StructureFunctionalArea(
        String id,
        String label,
        String confidence,
        List<String> memberPaths,
        List<String> keySymbolIds,
        long relationCount,
        List<String> evidenceRefs,
        String namingSource
    ) {
    }

    public record StructureProviderDiagnostic(
        String provider,
        String status,
        String version,
        long durationMs,
        long indexBytes,
        long documentCount,
        long symbolCount,
        long definitionCount,
        long referenceCount,
        long relationCount,
        String message
    ) {
    }

    public record StructureMetrics(
        long fileCount,
        long estimatedLoc,
        long symbolCount,
        long definitionCount,
        long referenceCount,
        long relationCount,
        long functionalAreaCount,
        long indexTimeMs,
        long incrementalUpdateMs,
        long memoryPeakBytes,
        long indexSizeBytes,
        boolean cacheHit
    ) {
    }

    public record ProjectStructureIndexResponse(
        String indexVersion,
        String indexerSource,
        String sourceRevision,
        String contentHash,
        boolean cacheHit,
        long indexedFileCount,
        List<StructureFileNode> files,
        boolean fileSampleTruncated,
        List<StructureModuleNode> modules,
        List<StructureRelation> relations,
        List<StructureEntryPoint> entryPoints,
        List<String> manifests,
        Map<String, List<String>> engineeringSignals,
        List<StructureEvidence> evidence,
        StructureCoverage coverage,
        List<String> provenance,
        List<String> unsupportedAreas,
        List<StructureSymbolNode> symbols,
        List<StructureOccurrence> definitions,
        List<StructureOccurrence> references,
        List<StructureImportantNode> importantNodes,
        List<StructureFunctionalArea> functionalAreas,
        List<StructureProviderDiagnostic> providerDiagnostics,
        StructureMetrics metrics,
        StructureDelta delta,
        Instant indexedAt
    ) {
    }

    public record AdaptiveAnalysisPlanResponse(
        List<String> deterministicCapabilities,
        String structureProvider,
        String semanticMode,
        int maxModelRequests,
        int maxModelInputTokens,
        int maxModelTotalTokens,
        long maxDurationMs,
        boolean hierarchical,
        String historicalMode,
        double expectedCoverage,
        List<String> unavailableCapabilities,
        List<String> planReasons,
        List<String> detectedProjectShapes,
        List<String> applicableDimensions,
        List<String> skippedDimensions,
        List<String> evidencePriorities,
        List<String> toolsToInvoke,
        List<String> deepReadTargets,
        String historicalStrategy,
        String structureStrategy,
        Map<String, Integer> semanticBudgets,
        List<String> expectedOutputs,
        String confidence,
        List<String> eligibleCapabilities,
        List<String> eligibleViews,
        List<ToolSelectionRationale> toolSelectionRationales
    ) {
    }

    public record ProjectEvidenceSourceResponse(
        String id,
        String category,
        String sourceType,
        String locator,
        String semanticRole,
        String importance,
        String currentness,
        String confidence,
        String deepReadStatus,
        String summary,
        List<String> evidenceRefs
    ) {
    }

    public record EvidenceSourceMapResponse(
        long discoveredEvidenceCount,
        int candidateEvidenceCount,
        int scoutEvidenceCount,
        int deepReadCount,
        long skippedCount,
        Map<String, Long> categoryCounts,
        List<ProjectEvidenceSourceResponse> sources,
        List<String> warnings,
        EvidenceDiversityMetrics diversityMetrics
    ) {
    }

    public record EvidenceDiversityMetrics(
        Map<String, Integer> selectedByCategory,
        int quotaDropCount,
        int duplicateCompressionCount,
        double categoryCoverage,
        int currentEvidenceCount,
        int historicalEvidenceCount,
        int sampleCacheHitCount
    ) {
    }

    public record ProjectShapeHypothesis(
        String shape,
        String confidence,
        List<String> evidenceRefs,
        String reason
    ) {
    }

    public record EvidenceSourceAssessment(
        String evidenceId,
        String semanticRole,
        String importance,
        String currentness,
        boolean shouldDeepRead,
        boolean shouldSkip,
        String reason,
        String informationGap,
        List<String> affectedDimensions,
        String confidence
    ) {
    }

    public record SemanticToolRequest(
        String capability,
        String informationGap,
        String expectedEvidenceValue,
        List<String> targetEvidenceIds,
        String whyExistingEvidenceIsInsufficient
    ) {
    }

    public record ToolSelectionRationale(
        String capability,
        String informationGap,
        String expectedEvidenceValue,
        List<String> targetEvidenceIds,
        String whyExistingEvidenceIsInsufficient,
        boolean eligible
    ) {
    }

    public record SemanticScoutResponse(
        List<ProjectShapeHypothesis> projectShapeHypotheses,
        List<EvidenceSourceAssessment> evidenceSourceAssessments,
        List<String> applicableDimensions,
        List<String> recommendedToolCalls,
        List<String> unknowns,
        List<String> skipCandidates,
        List<String> potentialConflicts,
        List<String> currentnessWarnings,
        List<SemanticToolRequest> toolRequests,
        boolean modelUsed
    ) {
    }

    public record DynamicProfileSection(
        String id,
        String type,
        String title,
        String summary,
        List<UnderstandingClaim> claims,
        String confidence,
        String epistemicStatus,
        int displayPriority,
        String applicabilityReason
    ) {
    }

    public record DynamicProjectProfileResponse(
        String summary,
        List<String> projectShapes,
        List<String> applicableViews,
        List<String> unavailableViews,
        List<DynamicProfileSection> sections,
        double evidenceCoverage,
        String confidence,
        List<String> unknowns
    ) {
    }

    public record HistoricalCoverageResponse(
        boolean historyAvailable,
        String availability,
        Instant earliestEvidenceAt,
        Instant latestEvidenceAt,
        long gitCommitCount,
        long coveredCommitCount,
        int tagCount,
        int releaseCount,
        int documentHistoryEvidenceCount,
        int agentEvidenceCount,
        List<String> coveredPeriods,
        List<String> gapPeriods,
        Map<String, String> confidenceByPeriod,
        double overallCoverage,
        List<String> limitations,
        HistoricalCoverageBreakdown breakdown
    ) {
    }

    public record HistoricalCoverageBreakdown(
        double gitMetadataCoverage,
        double factCoverage,
        double tagAnchorCoverage,
        double documentHistoryCoverage,
        double agentEvidenceCoverage,
        double structuralSnapshotCoverage,
        double remoteCollaborationCoverage,
        int sampledCommitCount,
        boolean commitSampleTruncated,
        List<HistoricalPeriodCoverage> periods
    ) {
    }

    public record HistoricalPeriodCoverage(
        String period,
        long commitCount,
        long factLinkedCommitCount,
        int tagAnchorCount,
        int documentEvidenceCount,
        int agentEvidenceCount,
        double confidence,
        boolean sampled,
        String limitation
    ) {
    }

    public record AnalysisToolEvidenceResponse(
        String id,
        String capability,
        String category,
        String sourceType,
        String summary,
        List<String> evidenceRefs
    ) {
    }

    public record AnalysisExecutionDiagnostic(
        String capability,
        String status,
        long durationMs,
        int selectedItemCount,
        int producedEvidenceCount,
        int consumedChars,
        String message
    ) {
    }

    public record SecondStageDecisionResponse(
        boolean secondStageTriggered,
        List<String> triggerReasons,
        List<String> skippedReasons,
        List<String> evidenceIds
    ) {
    }

    public record AnalysisExecutionResponse(
        String resultVersion,
        String cacheKey,
        String sourceRevision,
        List<String> requestedCapabilities,
        List<String> executedCapabilities,
        List<String> reusedCapabilities,
        List<AnalysisToolEvidenceResponse> evidence,
        List<AnalysisExecutionDiagnostic> diagnostics,
        long durationMs,
        boolean budgetExhausted,
        Instant producedAt,
        String invalidationReason,
        SecondStageDecisionResponse secondStageDecision
    ) {
    }

    public record ContextPackingDiagnostics(
        int maxChars,
        int totalChars,
        Map<String, Integer> selectedItems,
        Map<String, Integer> droppedItems,
        Map<String, Integer> charsBySection,
        List<String> truncationReasons,
        boolean validJson
    ) {
    }

    public record EvolutionPreviewResponse(
        String mode,
        String strategy,
        int milestoneCandidateCount,
        List<String> anchors,
        List<String> limitations
    ) {
    }

    public record UnderstandingAnalysisMetrics(
        long discoveredEvidenceCount,
        int candidateEvidenceCount,
        int scoutEvidenceCount,
        int deepReadCount,
        long skippedCount,
        int toolCallCount,
        int modelRequestCount,
        int inputTokens,
        int outputTokens,
        int totalTokens,
        long files,
        long loc,
        long docs,
        long commits,
        int tags,
        long scanTimeMs,
        long scoutTimeMs,
        long planTimeMs,
        long toolTimeMs,
        long synthesisTimeMs,
        long totalTimeMs,
        boolean cacheHit,
        double historicalCoverage,
        double structureCoverage,
        int inventoryFilesRead,
        int inventoryCacheHits,
        int sampleCacheHits
    ) {
    }

    public record UnderstandingClaim(
        String id,
        String text,
        String epistemicStatus,
        String confidence,
        List<String> evidenceRefs
    ) {
    }

    public record UnderstandingSection(
        String summary,
        List<UnderstandingClaim> claims
    ) {
    }

    public record UnderstandingEvidenceCoverage(
        int observedClaims,
        int inferredClaims,
        int explainedClaims,
        int evidenceBoundClaims,
        double intakeCoverage,
        double structureCoverage,
        List<String> evidenceKinds
    ) {
    }

    public record UnderstandingQuality(
        String semanticStatus,
        String confidence,
        boolean modelUsed,
        boolean cacheHit,
        List<String> limitations
    ) {
    }

    public record ProjectUnderstandingSnapshotResponse(
        UUID id,
        UUID projectId,
        String classification,
        String scale,
        UnderstandingSection identity,
        UnderstandingSection technology,
        UnderstandingSection structure,
        UnderstandingSection architecture,
        UnderstandingSection capabilities,
        UnderstandingSection engineeringState,
        UnderstandingEvidenceCoverage evidenceCoverage,
        UnderstandingQuality quality,
        List<String> unknowns,
        RepositoryIntakeResponse intake,
        AdaptiveAnalysisPlanResponse analysisPlan,
        Instant analyzedAt,
        String sourceRevision,
        String structureIndexVersion,
        String modelAnalysisVersion,
        String currentStatus,
        ModelCallDiagnosticsResponse diagnostics,
        EvidenceSourceMapResponse sourceMap,
        SemanticScoutResponse semanticScout,
        DynamicProjectProfileResponse dynamicProfile,
        HistoricalCoverageResponse historicalCoverage,
        EvolutionPreviewResponse evolutionPreview,
        AnalysisExecutionResponse analysisExecution,
        ContextPackingDiagnostics contextPacking,
        UnderstandingAnalysisMetrics analysisMetrics,
        String finalSynthesisStatus
    ) {
    }
}
