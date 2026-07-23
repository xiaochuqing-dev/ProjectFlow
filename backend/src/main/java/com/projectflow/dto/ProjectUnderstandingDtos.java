package com.projectflow.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.projectflow.dto.V2ProjectDtos.ModelCallDiagnosticsResponse;

public final class ProjectUnderstandingDtos {
    private ProjectUnderstandingDtos() {
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
        List<String> planReasons
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
        ModelCallDiagnosticsResponse diagnostics
    ) {
    }
}
