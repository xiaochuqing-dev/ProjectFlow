package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Explicit real-repository acceptance. It is skipped in the ordinary suite and
 * runs only when -Dprojectflow.benchmark.repo points to an existing checkout.
 */
class RepositoryIntakeBenchmarkTest {
    @Test
    void scansRealOpenSourceRepositoryWithinBoundedInventory() {
        String configuredPath = System.getProperty("projectflow.benchmark.repo", "");
        Assumptions.assumeTrue(!configuredPath.isBlank(), "real repository path was not supplied");
        Path root = Path.of(configuredPath).toAbsolutePath().normalize();
        Assumptions.assumeTrue(Files.isDirectory(root), "real repository path is unavailable");

        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        FixedCommandExecutor commands = new FixedCommandExecutor();
        SccCodeMetricsAdapter scc = new SccCodeMetricsAdapter(commands, mapper);
        RepositoryIntakeService intake = new RepositoryIntakeService(commands, scc, mapper);
        ReflectionTestUtils.setField(intake, "maxFiles", 250_000);
        ReflectionTestUtils.setField(intake, "maxFileDetails", 5_000);
        ReflectionTestUtils.setField(intake, "maxFileReadBytes", 8_388_608L);
        ReflectionTestUtils.setField(intake, "maxTotalReadBytes", 536_870_912L);
        ReflectionTestUtils.setField(intake, "maxSourceSampleBytes", 65_536L);
        ReflectionTestUtils.setField(intake, "maxSourceContentSamples", 1_500);
        ReflectionTestUtils.setField(intake, "smallLoc", 20_000L);
        ReflectionTestUtils.setField(intake, "mediumLoc", 100_000L);
        ReflectionTestUtils.setField(intake, "largeLoc", 500_000L);

        var discovery = new ProjectEvidenceDiscoveryService(new SensitiveContentRedactor());
        ReflectionTestUtils.setField(discovery, "maxCandidates", 500);
        ReflectionTestUtils.setField(discovery, "maxScoutEvidence", 80);
        ReflectionTestUtils.setField(discovery, "maxSampleChars", 1600);
        ReflectionTestUtils.setField(discovery, "maxSampleBytes", 8192);
        long startedAt = System.nanoTime();
        var scan = intake.scan(root);
        long scanElapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
        long indexStartedAt = System.nanoTime();
        var manifest = new ManifestFilesystemProjectStructureIndexer();
        var scip = new ScipProjectStructureIndexer();
        var index = new CompositeProjectStructureIndexer(manifest, scip).build(scan);
        long indexElapsedMs = (System.nanoTime() - indexStartedAt) / 1_000_000;
        long discoveryStartedAt = System.nanoTime();
        var evidence = discovery.discover(scan);
        long discoveryElapsedMs = (System.nanoTime() - discoveryStartedAt) / 1_000_000;
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;

        long warmStartedAt = System.nanoTime();
        var warmScan = intake.scan(root);
        var warmEvidence = discovery.discover(warmScan);
        long warmElapsedMs = (System.nanoTime() - warmStartedAt) / 1_000_000;
        long fingerprintStartedAt = System.nanoTime();
        String repeatFingerprint = intake.inventoryFingerprint(root);
        long fingerprintElapsedMs = (System.nanoTime() - fingerprintStartedAt) / 1_000_000;

        System.out.printf(
            "PROJECTFLOW_V371_BENCHMARK classification=%s scale=%s files=%d sourceFiles=%d loc=%d docs=%d discoveredEvidence=%d candidateEvidence=%d scoutEvidence=%d deepRead=%d skipped=%d selectedCategories=%d categoryCoverage=%.3f modules=%d symbols=%d definitions=%d references=%d relations=%d areas=%d structureEvidence=%d coverage=%.3f truncated=%s metrics=%s indexBytes=%d coldTimeMs=%d scanTimeMs=%d indexTimeMs=%d discoveryTimeMs=%d coldFilesRead=%d coldCacheHits=%d coldBytesRead=%d warmTimeMs=%d warmFilesRead=%d warmCacheHits=%d warmSampleCacheHits=%d incrementalUpdateMs=%d memoryPeakBytes=%d cacheHit=%s unsupported=%d fingerprintElapsedMs=%d modelRequests=0 inputTokens=0 outputTokens=0 totalTokens=0%n",
            scan.intake().classification(),
            scan.intake().scale(),
            scan.intake().fileCount(),
            scan.intake().sourceFileCount(),
            scan.intake().estimatedLoc(),
            evidence.documentCount(),
            evidence.sourceMap().discoveredEvidenceCount(),
            evidence.sourceMap().candidateEvidenceCount(),
            evidence.sourceMap().scoutEvidenceCount(),
            evidence.sourceMap().deepReadCount(),
            evidence.sourceMap().skippedCount(),
            evidence.sourceMap().diversityMetrics().selectedByCategory().size(),
            evidence.sourceMap().diversityMetrics().categoryCoverage(),
            index.modules().size(),
            index.symbols().size(),
            index.definitions().size(),
            index.references().size(),
            index.relations().size(),
            index.functionalAreas().size(),
            index.evidence().size(),
            index.coverage().overall(),
            scan.intake().scanTruncated(),
            scan.intake().metricsSource(),
            index.metrics().indexSizeBytes(),
            elapsedMs,
            scanElapsedMs,
            indexElapsedMs,
            discoveryElapsedMs,
            scan.ioMetrics().filesRead(),
            scan.ioMetrics().cacheHits(),
            scan.ioMetrics().bytesRead(),
            warmElapsedMs,
            warmScan.ioMetrics().filesRead(),
            warmScan.ioMetrics().cacheHits(),
            warmEvidence.sourceMap().diversityMetrics().sampleCacheHitCount(),
            index.metrics().incrementalUpdateMs(),
            index.metrics().memoryPeakBytes(),
            index.metrics().cacheHit(),
            index.unsupportedAreas().size(),
            fingerprintElapsedMs
        );
        assertThat(scan.intake().fileCount()).isPositive();
        assertThat(scan.intake().sourceFileCount()).isPositive();
        assertThat(index.files()).hasSizeLessThanOrEqualTo(5_000);
        assertThat(index.evidence()).hasSizeLessThanOrEqualTo(700);
        assertThat(evidence.sourceMap().scoutEvidenceCount()).isLessThanOrEqualTo(80);
        assertThat(scan.intake().fileCount()).isLessThanOrEqualTo(250_000);
        assertThat(repeatFingerprint).isEqualTo(scan.intake().contentHash());
    }
}
