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
