package com.projectflow.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import org.jgrapht.Graph;
import org.jgrapht.alg.clustering.LabelPropagationClustering;
import org.jgrapht.alg.scoring.PageRank;
import org.jgrapht.graph.AsUndirectedGraph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.projectflow.dto.ProjectUnderstandingDtos.ProjectStructureIndexResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.StructureCoverage;
import com.projectflow.dto.ProjectUnderstandingDtos.StructureEntryPoint;
import com.projectflow.dto.ProjectUnderstandingDtos.StructureEvidence;
import com.projectflow.dto.ProjectUnderstandingDtos.StructureFunctionalArea;
import com.projectflow.dto.ProjectUnderstandingDtos.StructureImportantNode;
import com.projectflow.dto.ProjectUnderstandingDtos.StructureMetrics;
import com.projectflow.dto.ProjectUnderstandingDtos.StructureOccurrence;
import com.projectflow.dto.ProjectUnderstandingDtos.StructureProviderDiagnostic;
import com.projectflow.dto.ProjectUnderstandingDtos.StructureRelation;
import com.projectflow.dto.ProjectUnderstandingDtos.StructureSymbolNode;
import com.sourcegraph.Scip;

/**
 * Consumes an existing SCIP index. Language-specific index production remains
 * owned by official SCIP indexers; ProjectFlow never parses language syntax here.
 */
@Service
public class ScipProjectStructureIndexer {
    static final String PROVIDER = "SCIP";

    @Value("${projectflow.understanding.scip.max-index-bytes:268435456}")
    private long maxIndexBytes = 268_435_456L;

    @Value("${projectflow.understanding.scip.max-documents:20000}")
    private int maxDocuments = 20_000;

    @Value("${projectflow.understanding.scip.max-symbols:50000}")
    private int maxSymbols = 50_000;

    @Value("${projectflow.understanding.scip.max-definitions:50000}")
    private int maxDefinitions = 50_000;

    @Value("${projectflow.understanding.scip.max-references:100000}")
    private int maxReferences = 100_000;

    @Value("${projectflow.understanding.scip.max-relations:100000}")
    private int maxRelations = 100_000;

    @Value("${projectflow.understanding.scip.max-functional-areas:100}")
    private int maxFunctionalAreas = 100;

    public ProjectStructureIndexResponse enhance(
        RepositoryIntakeService.ScanResult scan,
        ProjectStructureIndexResponse fallback
    ) {
        long startedAt = System.nanoTime();
        Optional<Path> candidate = locateIndex(scan.root());
        if (candidate.isEmpty()) {
            return withDiagnostic(
                fallback,
                diagnostic("NOT_FOUND", "", elapsedMs(startedAt), 0, 0, 0, 0, 0, 0,
                    "未发现 index.scip，继续使用 MANIFEST_FILESYSTEM fallback"),
                "未提供 SCIP 精确索引，Symbol、Definition 与 Reference 覆盖不可用"
            );
        }

        Path indexPath = candidate.get();
        try {
            if (Files.isSymbolicLink(indexPath)) {
                return withDiagnostic(
                    fallback,
                    diagnostic("REJECTED", "", elapsedMs(startedAt), 0, 0, 0, 0, 0, 0,
                        "index.scip 是符号链接，已拒绝读取"),
                    "SCIP 索引因路径安全检查未通过而未使用"
                );
            }
            long indexBytes = Files.size(indexPath);
            if (indexBytes <= 0 || indexBytes > maxIndexBytes) {
                return withDiagnostic(
                    fallback,
                    diagnostic("REJECTED", "", elapsedMs(startedAt), indexBytes, 0, 0, 0, 0, 0,
                        "index.scip 为空或超过安全上限"),
                    "SCIP 索引超过安全上限，已回退到 MANIFEST_FILESYSTEM"
                );
            }
            return parse(scan, fallback, indexPath, indexBytes, startedAt);
        } catch (Exception exception) {
            return withDiagnostic(
                fallback,
                diagnostic("FAILED", "", elapsedMs(startedAt), safeSize(indexPath), 0, 0, 0, 0, 0,
                    "SCIP 索引解析失败：" + safeMessage(exception)),
                "SCIP provider 失败，结构覆盖率已降级并保留 fallback"
            );
        }
    }

    private ProjectStructureIndexResponse parse(
        RepositoryIntakeService.ScanResult scan,
        ProjectStructureIndexResponse fallback,
        Path indexPath,
        long indexBytes,
        long startedAt
    ) throws IOException {
        long memoryBefore = usedMemory();
        Scip.Index index;
        try (InputStream input = Files.newInputStream(indexPath)) {
            index = Scip.Index.parseFrom(input);
        }
        ModelCancellationContext.throwIfCancelled();

        List<Scip.Document> documents = index.getDocumentsList();
        boolean documentTruncated = documents.size() > maxDocuments;
        List<Scip.Document> boundedDocuments = documents.stream().limit(maxDocuments).toList();
        Map<String, Scip.SymbolInformation> information = new LinkedHashMap<>();
        Map<String, DefinitionLocation> definitionBySymbol = new LinkedHashMap<>();
        Map<String, String> evidenceByPath = new LinkedHashMap<>();
        Map<String, StructureEvidence> evidence = new LinkedHashMap<>();
        fallback.evidence().forEach(item -> evidence.put(item.id(), item));

        List<StructureOccurrence> definitions = new ArrayList<>();
        Set<String> indexedLanguages = new LinkedHashSet<>();
        long occurrenceCount = 0;
        boolean occurrenceTruncated = false;

        for (Scip.Document document : boundedDocuments) {
            ModelCancellationContext.throwIfCancelled();
            String path = safeRelativePath(document.getRelativePath());
            if (path.isBlank()) continue;
            String evidenceRef = evidenceFor(path, evidenceByPath, evidence);
            if (!document.getLanguage().isBlank()) indexedLanguages.add(normalizeLanguage(document.getLanguage()));
            for (Scip.SymbolInformation item : document.getSymbolsList()) {
                if (information.size() >= maxSymbols) break;
                if (!item.getSymbol().isBlank()) information.putIfAbsent(item.getSymbol(), item);
            }
            for (Scip.Occurrence occurrence : document.getOccurrencesList()) {
                occurrenceCount++;
                if (definitions.size() >= maxDefinitions) {
                    occurrenceTruncated = true;
                    continue;
                }
                if (isRole(occurrence, Scip.SymbolRole.Definition_VALUE) && !occurrence.getSymbol().isBlank()) {
                    Range range = range(occurrence);
                    String symbolId = symbolId(occurrence.getSymbol());
                    definitionBySymbol.putIfAbsent(
                        occurrence.getSymbol(),
                        new DefinitionLocation(symbolId, path, range.startLine(), range.startCharacter(), evidenceRef)
                    );
                    definitions.add(new StructureOccurrence(
                        symbolId, path, range.startLine(), range.startCharacter(),
                        range.endLine(), range.endCharacter(), "DEFINITION", evidenceRef
                    ));
                }
            }
        }

        List<StructureSymbolNode> symbols = definitionBySymbol.entrySet().stream()
            .limit(maxSymbols)
            .map(entry -> symbol(entry.getKey(), entry.getValue(), information.get(entry.getKey())))
            .toList();
        Map<String, StructureSymbolNode> symbolById = new HashMap<>();
        symbols.forEach(item -> symbolById.put(item.id(), item));

        List<StructureOccurrence> references = new ArrayList<>();
        List<Dependency> dependencies = new ArrayList<>();
        Set<String> dependencyKeys = new HashSet<>();
        for (Scip.Document document : boundedDocuments) {
            ModelCancellationContext.throwIfCancelled();
            String path = safeRelativePath(document.getRelativePath());
            if (path.isBlank()) continue;
            String evidenceRef = evidenceFor(path, evidenceByPath, evidence);
            for (Scip.Occurrence occurrence : document.getOccurrencesList()) {
                if (references.size() >= maxReferences || dependencies.size() >= maxRelations) {
                    occurrenceTruncated = true;
                    break;
                }
                if (occurrence.getSymbol().isBlank() || isRole(occurrence, Scip.SymbolRole.Definition_VALUE)) continue;
                DefinitionLocation target = definitionBySymbol.get(occurrence.getSymbol());
                if (target == null) continue;
                Range range = range(occurrence);
                String role = isRole(occurrence, Scip.SymbolRole.Import_VALUE) ? "IMPORT" : "REFERENCE";
                references.add(new StructureOccurrence(
                    target.symbolId(), path, range.startLine(), range.startCharacter(),
                    range.endLine(), range.endCharacter(), role, evidenceRef
                ));
                if (!path.equals(target.path())) {
                    String key = path + "\0" + target.path() + "\0" + role;
                    if (dependencyKeys.add(key)) {
                        dependencies.add(new Dependency(path, target.path(), role, evidenceRef, target.evidenceRef()));
                    }
                }
            }
        }

        Graph<String, DefaultEdge> graph = dependencyGraph(definitionBySymbol.values(), dependencies);
        Map<String, Double> scores = graph.vertexSet().isEmpty()
            ? Map.of()
            : new PageRank<>(graph).getScores();
        List<StructureImportantNode> importantNodes = importantNodes(scores, evidenceByPath);
        List<StructureFunctionalArea> areas = functionalAreas(
            graph, dependencies, scores, symbols, evidenceByPath
        );
        List<StructureRelation> relations = mergeRelations(fallback.relations(), dependencies);
        List<StructureEntryPoint> entryPoints = mergeEntryPoints(fallback.entryPoints(), symbols);

        boolean truncated = documentTruncated || occurrenceTruncated
            || information.size() >= maxSymbols
            || dependencies.size() >= maxRelations;
        double symbolCoverage = scan.intake().sourceFileCount() == 0
            ? 1.0
            : round((double) boundedDocuments.size() / scan.intake().sourceFileCount());
        if (truncated) symbolCoverage = Math.min(symbolCoverage, 0.8);
        double overall = round(
            fallback.coverage().fileInventory() * 0.25
                + fallback.coverage().languageMetrics() * 0.15
                + fallback.coverage().manifestCoverage() * 0.15
                + symbolCoverage * 0.45
        );
        StructureCoverage coverage = new StructureCoverage(
            fallback.coverage().fileInventory(),
            fallback.coverage().languageMetrics(),
            fallback.coverage().manifestCoverage(),
            symbolCoverage,
            "SCIP definition/reference occurrence 与文件级依赖；不声明运行时调用图",
            overall
        );
        List<String> unsupported = unsupportedAreas(
            fallback.unsupportedAreas(), scan.intake().languageDistribution().keySet(), indexedLanguages, truncated
        );
        List<String> provenance = new ArrayList<>(fallback.provenance());
        provenance.add("SCIP language-agnostic code intelligence index");
        provenance.add("JGraphT PageRank 与 Label Propagation");
        String version = toolVersion(index);
        long elapsed = elapsedMs(startedAt);
        long memoryDelta = Math.max(0, usedMemory() - memoryBefore);
        List<StructureProviderDiagnostic> diagnostics = new ArrayList<>(fallback.providerDiagnostics());
        diagnostics.add(diagnostic(
            truncated ? "TRUNCATED" : "SUCCEEDED",
            version,
            elapsed,
            indexBytes,
            boundedDocuments.size(),
            symbols.size(),
            definitions.size(),
            references.size(),
            dependencies.size(),
            truncated ? "达到一个或多个 SCIP 安全上限，未覆盖部分保持未知" : "已消费官方 SCIP protobuf"
        ));
        StructureMetrics metrics = new StructureMetrics(
            scan.intake().fileCount(),
            scan.intake().estimatedLoc(),
            symbols.size(),
            definitions.size(),
            references.size(),
            relations.size(),
            areas.size(),
            elapsed,
            -1,
            memoryDelta,
            indexBytes,
            false
        );
        return new ProjectStructureIndexResponse(
            fallback.indexVersion(),
            "MANIFEST_FILESYSTEM+SCIP",
            fallback.sourceRevision(),
            fallback.contentHash(),
            false,
            fallback.indexedFileCount(),
            fallback.files(),
            fallback.fileSampleTruncated(),
            fallback.modules(),
            relations,
            entryPoints,
            fallback.manifests(),
            fallback.engineeringSignals(),
            List.copyOf(evidence.values()),
            coverage,
            List.copyOf(provenance),
            unsupported,
            symbols,
            List.copyOf(definitions),
            List.copyOf(references),
            importantNodes,
            areas,
            List.copyOf(diagnostics),
            metrics,
            fallback.delta(),
            Instant.now()
        );
    }

    private ProjectStructureIndexResponse withDiagnostic(
        ProjectStructureIndexResponse value,
        StructureProviderDiagnostic diagnostic,
        String limitation
    ) {
        List<StructureProviderDiagnostic> diagnostics = new ArrayList<>(value.providerDiagnostics());
        diagnostics.add(diagnostic);
        LinkedHashSet<String> unsupported = new LinkedHashSet<>(value.unsupportedAreas());
        unsupported.add(limitation);
        return new ProjectStructureIndexResponse(
            value.indexVersion(), value.indexerSource(), value.sourceRevision(), value.contentHash(),
            value.cacheHit(), value.indexedFileCount(), value.files(), value.fileSampleTruncated(),
            value.modules(), value.relations(), value.entryPoints(), value.manifests(),
            value.engineeringSignals(), value.evidence(), value.coverage(), value.provenance(),
            List.copyOf(unsupported), value.symbols(), value.definitions(), value.references(),
            value.importantNodes(), value.functionalAreas(), List.copyOf(diagnostics), value.metrics(),
            value.delta(), value.indexedAt()
        );
    }

    private static Graph<String, DefaultEdge> dependencyGraph(
        java.util.Collection<DefinitionLocation> definitions,
        List<Dependency> dependencies
    ) {
        Graph<String, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);
        definitions.forEach(item -> graph.addVertex(item.path()));
        for (Dependency dependency : dependencies) {
            graph.addVertex(dependency.from());
            graph.addVertex(dependency.to());
            if (!dependency.from().equals(dependency.to()) && !graph.containsEdge(dependency.from(), dependency.to())) {
                graph.addEdge(dependency.from(), dependency.to());
            }
        }
        return graph;
    }

    private List<StructureFunctionalArea> functionalAreas(
        Graph<String, DefaultEdge> graph,
        List<Dependency> dependencies,
        Map<String, Double> scores,
        List<StructureSymbolNode> symbols,
        Map<String, String> evidenceByPath
    ) {
        if (graph.edgeSet().isEmpty()) return List.of();
        List<Set<String>> clusters = new LabelPropagationClustering<>(
            new AsUndirectedGraph<>(graph), 100, new Random(0)
        ).getClustering().getClusters();
        Map<String, List<StructureSymbolNode>> symbolsByPath = new HashMap<>();
        symbols.forEach(symbol -> symbolsByPath.computeIfAbsent(symbol.path(), ignored -> new ArrayList<>()).add(symbol));
        List<Set<String>> ordered = clusters.stream()
            .filter(cluster -> cluster.size() > 1)
            .sorted(Comparator.<Set<String>>comparingInt(Set::size).reversed()
                .thenComparing(cluster -> cluster.stream().sorted().findFirst().orElse("")))
            .limit(maxFunctionalAreas)
            .toList();
        List<StructureFunctionalArea> result = new ArrayList<>();
        int position = 0;
        for (Set<String> cluster : ordered) {
            List<String> members = cluster.stream()
                .sorted(Comparator.comparingDouble((String path) -> scores.getOrDefault(path, 0.0)).reversed()
                    .thenComparing(path -> path))
                .limit(200)
                .toList();
            List<String> keySymbols = members.stream()
                .flatMap(path -> symbolsByPath.getOrDefault(path, List.of()).stream())
                .sorted(Comparator.comparing(StructureSymbolNode::kind).thenComparing(StructureSymbolNode::displayName))
                .map(StructureSymbolNode::id)
                .distinct()
                .limit(12)
                .toList();
            long relationCount = dependencies.stream()
                .filter(item -> cluster.contains(item.from()) && cluster.contains(item.to()))
                .count();
            List<String> refs = members.stream()
                .map(evidenceByPath::get)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .limit(12)
                .toList();
            result.add(new StructureFunctionalArea(
                "area:" + stableId(String.join("\0", cluster.stream().sorted().toList())),
                "待语义命名区域 " + (++position),
                "MEDIUM",
                members,
                keySymbols,
                relationCount,
                refs,
                "JGRAPHT_LABEL_PROPAGATION"
            ));
        }
        return List.copyOf(result);
    }

    private static List<StructureImportantNode> importantNodes(
        Map<String, Double> scores,
        Map<String, String> evidenceByPath
    ) {
        return scores.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
            .limit(100)
            .map(entry -> new StructureImportantNode(
                "file:" + stableId(entry.getKey()),
                "FILE",
                entry.getKey(),
                entry.getKey(),
                round(entry.getValue()),
                evidenceByPath.containsKey(entry.getKey()) ? List.of(evidenceByPath.get(entry.getKey())) : List.of()
            ))
            .toList();
    }

    private List<StructureRelation> mergeRelations(
        List<StructureRelation> fallback,
        List<Dependency> dependencies
    ) {
        List<StructureRelation> result = new ArrayList<>(fallback);
        dependencies.stream().limit(Math.max(0, maxRelations - result.size())).forEach(item -> result.add(
            new StructureRelation(
                "file:" + stableId(item.from()),
                "file:" + stableId(item.to()),
                "IMPORT".equals(item.role()) ? "IMPORTS" : "REFERENCES",
                List.of(item.sourceEvidence(), item.targetEvidence()).stream().distinct().toList()
            )
        ));
        return List.copyOf(result);
    }

    private static List<StructureEntryPoint> mergeEntryPoints(
        List<StructureEntryPoint> fallback,
        List<StructureSymbolNode> symbols
    ) {
        List<StructureEntryPoint> result = new ArrayList<>(fallback);
        Set<String> keys = new HashSet<>();
        fallback.forEach(item -> keys.add(item.path() + "\0" + item.kind()));
        symbols.stream()
            .filter(item -> "main".equalsIgnoreCase(item.displayName())
                || item.displayName().endsWith("Application"))
            .limit(100)
            .forEach(item -> {
                String key = item.path() + "\0SYMBOL_ENTRY";
                if (keys.add(key)) {
                    result.add(new StructureEntryPoint(
                        item.path(), "SYMBOL_ENTRY", "HIGH", item.evidenceRef()
                    ));
                }
            });
        return List.copyOf(result);
    }

    private static StructureSymbolNode symbol(
        String raw,
        DefinitionLocation definition,
        Scip.SymbolInformation information
    ) {
        String displayName = information == null ? symbolTail(raw) : information.getDisplayName();
        String kind = information == null ? "UnspecifiedKind" : information.getKind().name();
        return new StructureSymbolNode(
            definition.symbolId(),
            bounded(raw, 1000),
            bounded(displayName.isBlank() ? symbolTail(raw) : displayName, 200),
            kind,
            definition.path(),
            definition.line(),
            definition.character(),
            false,
            definition.evidenceRef()
        );
    }

    private static List<String> unsupportedAreas(
        List<String> fallback,
        Set<String> repositoryLanguages,
        Set<String> indexedLanguages,
        boolean truncated
    ) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        fallback.stream()
            .filter(item -> !item.contains("未安装或未配置语义索引器"))
            .forEach(values::add);
        List<String> missing = repositoryLanguages.stream()
            .map(ScipProjectStructureIndexer::normalizeLanguage)
            .filter(language -> !indexedLanguages.contains(language))
            .sorted()
            .toList();
        if (!missing.isEmpty()) values.add("SCIP 未覆盖的语言或区域：" + String.join("、", missing));
        values.add("SCIP 提供静态 definition/reference，不等同于完整运行时调用图");
        if (truncated) values.add("SCIP index 达到安全上限，未读取部分保持未知");
        return List.copyOf(values);
    }

    private static String evidenceFor(
        String path,
        Map<String, String> evidenceByPath,
        Map<String, StructureEvidence> evidence
    ) {
        return evidenceByPath.computeIfAbsent(path, value -> {
            if (evidence.size() >= 2_500) return "intake:scan";
            String id = "scip:" + stableId(value);
            evidence.put(id, new StructureEvidence(id, "SCIP_DOCUMENT", value, "SCIP definition/reference 文档"));
            return id;
        });
    }

    private static StructureProviderDiagnostic diagnostic(
        String status,
        String version,
        long durationMs,
        long indexBytes,
        long documents,
        long symbols,
        long definitions,
        long references,
        long relations,
        String message
    ) {
        return new StructureProviderDiagnostic(
            PROVIDER, status, version, durationMs, indexBytes, documents,
            symbols, definitions, references, relations, bounded(message, 500)
        );
    }

    private static Optional<Path> locateIndex(Path root) {
        List<Path> candidates = List.of(root.resolve("index.scip"), root.resolve(".projectflow").resolve("index.scip"));
        return candidates.stream()
            .map(path -> path.toAbsolutePath().normalize())
            .filter(path -> path.startsWith(root.toAbsolutePath().normalize()))
            .filter(Files::isRegularFile)
            .findFirst();
    }

    private static String safeRelativePath(String value) {
        if (value == null || value.isBlank()) return "";
        String clean = value.trim().replace('\\', '/');
        if (clean.startsWith("/") || clean.matches("^[A-Za-z]:.*")) return "";
        try {
            Path normalized = Path.of(clean).normalize();
            String result = normalized.toString().replace('\\', '/');
            return normalized.isAbsolute() || result.equals("..") || result.startsWith("../") ? "" : result;
        } catch (RuntimeException exception) {
            return "";
        }
    }

    private static boolean isRole(Scip.Occurrence occurrence, int role) {
        return (occurrence.getSymbolRoles() & role) == role;
    }

    private static Range range(Scip.Occurrence occurrence) {
        List<Integer> values = occurrence.getRangeList();
        if (values.size() >= 4) return new Range(values.get(0), values.get(1), values.get(2), values.get(3));
        if (values.size() == 3) return new Range(values.get(0), values.get(1), values.get(0), values.get(2));
        return new Range(0, 0, 0, 0);
    }

    private static String toolVersion(Scip.Index index) {
        if (!index.hasMetadata() || !index.getMetadata().hasToolInfo()) return "";
        Scip.ToolInfo tool = index.getMetadata().getToolInfo();
        return bounded(tool.getName() + (tool.getVersion().isBlank() ? "" : " " + tool.getVersion()), 200);
    }

    private static String symbolTail(String symbol) {
        String clean = symbol == null ? "" : symbol;
        int end = Math.max(clean.lastIndexOf('#'), Math.max(clean.lastIndexOf('.'), clean.lastIndexOf('/')));
        String tail = end >= 0 && end + 1 < clean.length() ? clean.substring(end + 1) : clean;
        return tail.replaceAll("[()#.`]+$", "");
    }

    private static String normalizeLanguage(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "typescriptreact", "tsx" -> "typescript";
            case "javascriptreact", "jsx" -> "javascript";
            default -> normalized;
        };
    }

    private static String symbolId(String symbol) {
        return "symbol:" + stableId(symbol);
    }

    private static String stableId(String value) {
        try {
            byte[] bytes = java.security.MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(bytes, 0, 12);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static long elapsedMs(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000);
    }

    private static long usedMemory() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static long safeSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException exception) {
            return 0;
        }
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return bounded(message == null || message.isBlank() ? exception.getClass().getSimpleName() : message, 300);
    }

    private static String bounded(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static double round(double value) {
        return Math.round(Math.max(0, Math.min(1, value)) * 1_000_000.0) / 1_000_000.0;
    }

    private record Range(int startLine, int startCharacter, int endLine, int endCharacter) {
    }

    private record DefinitionLocation(
        String symbolId,
        String path,
        int line,
        int character,
        String evidenceRef
    ) {
    }

    private record Dependency(
        String from,
        String to,
        String role,
        String sourceEvidence,
        String targetEvidence
    ) {
    }
}
