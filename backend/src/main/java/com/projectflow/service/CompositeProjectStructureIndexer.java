package com.projectflow.service;

import java.time.Instant;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import com.projectflow.dto.ProjectUnderstandingDtos.ProjectStructureIndexResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.StructureMetrics;

@Service
@Primary
public class CompositeProjectStructureIndexer implements ProjectStructureIndexer {
    static final String INDEX_VERSION = "structure-v2";

    private final ManifestFilesystemProjectStructureIndexer fallback;
    private final ScipProjectStructureIndexer scip;

    public CompositeProjectStructureIndexer(
        ManifestFilesystemProjectStructureIndexer fallback,
        ScipProjectStructureIndexer scip
    ) {
        this.fallback = fallback;
        this.scip = scip;
    }

    @Override
    public ProjectStructureIndexResponse build(RepositoryIntakeService.ScanResult scan) {
        long startedAt = System.nanoTime();
        ProjectStructureIndexResponse value = scip.enhance(scan, fallback.build(scan));
        long elapsed = Math.max(0, (System.nanoTime() - startedAt) / 1_000_000);
        StructureMetrics source = value.metrics();
        StructureMetrics metrics = new StructureMetrics(
            source.fileCount(),
            source.estimatedLoc(),
            source.symbolCount(),
            source.definitionCount(),
            source.referenceCount(),
            source.relationCount(),
            source.functionalAreaCount(),
            elapsed,
            source.incrementalUpdateMs(),
            source.memoryPeakBytes(),
            source.indexSizeBytes(),
            false
        );
        return new ProjectStructureIndexResponse(
            INDEX_VERSION,
            value.indexerSource(),
            value.sourceRevision(),
            value.contentHash(),
            false,
            value.indexedFileCount(),
            value.files(),
            value.fileSampleTruncated(),
            value.modules(),
            value.relations(),
            value.entryPoints(),
            value.manifests(),
            value.engineeringSignals(),
            value.evidence(),
            value.coverage(),
            value.provenance(),
            value.unsupportedAreas(),
            value.symbols(),
            value.definitions(),
            value.references(),
            value.importantNodes(),
            value.functionalAreas(),
            value.providerDiagnostics(),
            metrics,
            value.delta(),
            Instant.now()
        );
    }
}
