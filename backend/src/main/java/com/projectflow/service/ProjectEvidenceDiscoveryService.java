package com.projectflow.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.projectflow.dto.ProjectUnderstandingDtos.EvidenceSourceMapResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.EvidenceDiversityMetrics;
import com.projectflow.dto.ProjectUnderstandingDtos.ProjectEvidenceSourceResponse;
import com.projectflow.service.RepositoryIntakeService.ScanResult;
import com.projectflow.service.RepositoryIntakeService.ScannedFile;

@Service
public class ProjectEvidenceDiscoveryService {
    private static final Set<String> DOCUMENT_EXTENSIONS = Set.of(
        "md", "mdx", "txt", "rst", "adoc", "asciidoc"
    );
    private static final Set<String> CONFIG_EXTENSIONS = Set.of(
        "json", "yaml", "yml", "toml", "xml", "properties", "ini", "conf"
    );
    private static final List<String> GUARANTEED_CATEGORIES = List.of(
        "MANIFEST", "CI_CD", "TEST", "MIGRATION", "INFRA", "PRODUCT_CONTEXT",
        "AGENT_CONTEXT", "AGENT_RESULT", "README", "UNKNOWN_DOCUMENT", "ADR",
        "CHANGELOG", "CONFIG", "BUILD", "LICENSE"
    );
    private static final int MAX_SAMPLE_CACHE_ROOTS = 8;

    private final SensitiveContentRedactor redactor;
    private final LargeFileContentService largeFileContentService;
    private final Map<Path, Map<String, CachedSample>> sampleCaches = new ConcurrentHashMap<>();

    @Value("${projectflow.understanding.max-evidence-candidates:500}")
    private int maxCandidates;

    @Value("${projectflow.understanding.max-scout-evidence:80}")
    private int maxScoutEvidence;

    @Value("${projectflow.understanding.max-evidence-sample-chars:1600}")
    private int maxSampleChars;

    @Value("${projectflow.understanding.max-evidence-sample-bytes:8192}")
    private int maxSampleBytes;

    @Value("${projectflow.understanding.large-file-threshold-bytes:262144}")
    private long largeFileThresholdBytes;

    @Autowired
    public ProjectEvidenceDiscoveryService(
        SensitiveContentRedactor redactor,
        LargeFileContentService largeFileContentService
    ) {
        this.redactor = redactor;
        this.largeFileContentService = largeFileContentService;
    }

    public ProjectEvidenceDiscoveryService(SensitiveContentRedactor redactor) {
        this(redactor, new LargeFileContentService(redactor));
    }

    public DiscoveryResult discover(ScanResult scan) {
        Map<String, Long> categoryCounts = new LinkedHashMap<>();
        List<Candidate> candidates = new ArrayList<>();
        for (ScannedFile file : scan.fileDetails()) {
            String category = category(file);
            categoryCounts.merge(category, 1L, Long::sum);
            if (isCandidate(file, category)) {
                candidates.add(new Candidate(file, category, sourceType(file, category), score(file, category)));
            }
        }
        candidates.sort(
            Comparator.comparingInt(Candidate::score).reversed()
                .thenComparing(candidate -> candidate.file().path())
        );

        int candidateCount = candidates.size();
        int mappedLimit = Math.min(Math.max(1, maxCandidates), candidates.size());
        Selection mappedSelection = selectDiverse(candidates, mappedLimit);
        int scoutLimit = Math.min(Math.max(0, maxScoutEvidence), mappedSelection.items().size());
        Selection scoutSelection = selectDiverse(mappedSelection.items(), scoutLimit);
        Set<String> scoutPaths = scoutSelection.items().stream()
            .map(candidate -> candidate.file().path())
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<ProjectEvidenceSourceResponse> sources = new ArrayList<>();
        List<PromptEvidence> promptEvidence = new ArrayList<>();
        sources.add(new ProjectEvidenceSourceResponse(
            "intake:scan",
            "OTHER",
            "REPOSITORY_INTAKE",
            "",
            "CURRENT_PROJECT_INVENTORY",
            "UNKNOWN",
            "CURRENT",
            "HIGH",
            "DETERMINISTIC",
            "有界目录盘点、规模、语言和安全扫描结果",
            List.of("intake:scan")
        ));
        if (scan.intake().git().available()) {
            categoryCounts.merge("GIT", 1L, Long::sum);
            sources.add(new ProjectEvidenceSourceResponse(
                "git:summary",
                "GIT",
                "LOCAL_GIT",
                ".git",
                "HISTORY_EVIDENCE",
                "UNKNOWN",
                "CURRENT",
                "HIGH",
                "DETERMINISTIC",
                "本地 Git 可用，共 " + scan.intake().git().commitCount() + " 次提交",
                List.of("git:summary")
            ));
        }

        int sampleCacheHits = 0;
        for (Candidate candidate : mappedSelection.items()) {
            String id = evidenceId(candidate.file().path());
            SampleRead sampleRead = scoutPaths.contains(candidate.file().path())
                ? readSample(scan, candidate.file())
                : new SampleRead("", false);
            String sample = sampleRead.value();
            if (sampleRead.cacheHit()) sampleCacheHits++;
            boolean sampled = !sample.isBlank();
            ProjectEvidenceSourceResponse source = new ProjectEvidenceSourceResponse(
                id,
                candidate.category(),
                candidate.sourceType(),
                candidate.file().path(),
                deterministicRole(candidate.category()),
                "UNKNOWN",
                deterministicCurrentness(candidate.category()),
                "MEDIUM",
                sampled ? "SAMPLED_BOUNDED" : "METADATA_ONLY",
                summary(candidate, sample),
                List.of(id)
            );
            sources.add(source);
            if (sampled) {
                promptEvidence.add(new PromptEvidence(
                    id,
                    candidate.category(),
                    candidate.sourceType(),
                    candidate.file().path(),
                    source.summary(),
                    sample
                ));
            }
        }
        Map<String, CachedSample> rootCache = sampleCaches.get(scan.root().toAbsolutePath().normalize());
        if (rootCache != null) rootCache.keySet().retainAll(scan.inventorySignatures().keySet());

        long discovered = scan.intake().fileCount() + (scan.intake().git().available() ? 1 : 0);
        long skipped = Math.max(0, scan.intake().fileCount() - candidateCount);
        List<String> warnings = new ArrayList<>();
        if (scan.fileDetailsTruncated()) {
            warnings.add("Evidence Discovery 只使用有界文件详情，未展开的文件保留为未知。");
        }
        if (candidateCount > mappedSelection.items().size()) {
            warnings.add("候选来源超过安全上限，Source Map 只保留高价值和多类型有界样本。");
        }
        if (mappedSelection.duplicateCount() + scoutSelection.duplicateCount() > 0) {
            warnings.add("相同类别、文件名和规模的重复候选已压缩，避免同类材料挤占上下文。");
        }
        Map<String, Integer> selectedByCategory = new LinkedHashMap<>();
        mappedSelection.items().forEach(candidate -> selectedByCategory.merge(candidate.category(), 1, Integer::sum));
        long currentEvidence = mappedSelection.items().stream()
            .filter(candidate -> !Set.of("CHANGELOG", "AGENT_RESULT", "ADR").contains(candidate.category()))
            .count();
        long historicalEvidence = mappedSelection.items().stream()
            .filter(candidate -> Set.of("CHANGELOG", "AGENT_RESULT", "ADR").contains(candidate.category()))
            .count();
        long candidateCategoryCount = candidates.stream().map(Candidate::category).distinct().count();
        double categoryCoverage = candidateCategoryCount == 0
            ? 1
            : round((double) selectedByCategory.size() / candidateCategoryCount);
        EvidenceDiversityMetrics diversity = new EvidenceDiversityMetrics(
            Map.copyOf(selectedByCategory),
            Math.max(0, candidateCount - mappedSelection.items().size()),
            mappedSelection.duplicateCount() + scoutSelection.duplicateCount(),
            categoryCoverage,
            Math.toIntExact(currentEvidence),
            Math.toIntExact(historicalEvidence),
            sampleCacheHits
        );
        EvidenceSourceMapResponse sourceMap = new EvidenceSourceMapResponse(
            discovered,
            candidateCount,
            promptEvidence.size(),
            0,
            skipped,
            Map.copyOf(categoryCounts),
            List.copyOf(sources),
            List.copyOf(warnings),
            diversity
        );
        long documentCount = categoryCounts.entrySet().stream()
            .filter(entry -> isDocumentCategory(entry.getKey()))
            .mapToLong(Map.Entry::getValue)
            .sum();
        return new DiscoveryResult(sourceMap, List.copyOf(promptEvidence), documentCount);
    }

    private SampleRead readSample(ScanResult scan, ScannedFile file) {
        if (file.binary() || file.generated() || file.bytes() <= 0 || redactor.isSensitivePath(file.path())) {
            return new SampleRead("", false);
        }
        Path root = scan.root().toAbsolutePath().normalize();
        if (sampleCaches.size() >= MAX_SAMPLE_CACHE_ROOTS && !sampleCaches.containsKey(root)) {
            sampleCaches.keySet().stream().findFirst().ifPresent(sampleCaches::remove);
        }
        Map<String, CachedSample> cache = sampleCaches.computeIfAbsent(root, ignored -> new ConcurrentHashMap<>());
        String signature = scan.inventorySignatures().getOrDefault(file.path(), file.bytes() + ":unknown");
        CachedSample cached = cache.get(file.path());
        if (cached != null && cached.signature().equals(signature)) {
            return new SampleRead(cached.value(), true);
        }
        Path target = root.resolve(file.path()).normalize();
        if (!target.startsWith(root)) return new SampleRead("", false);
        if (file.bytes() >= Math.max(65_536, largeFileThresholdBytes)) {
            var contentMap = largeFileContentService.analyze(target, Math.max(2_000, maxSampleChars * 2));
            String value = sanitizeSample(largeFileContentService.toPromptText(contentMap, maxSampleChars));
            cache.put(file.path(), new CachedSample(signature, value));
            return new SampleRead(value, false);
        }
        try (var input = Files.newInputStream(target)) {
            byte[] bytes = input.readNBytes(Math.max(256, maxSampleBytes));
            for (byte value : bytes) {
                if (value == 0) return new SampleRead("", false);
            }
            String text = new String(bytes, StandardCharsets.UTF_8);
            String value = sanitizeSample(text);
            cache.put(file.path(), new CachedSample(signature, value));
            return new SampleRead(value, false);
        } catch (IOException ignored) {
            return new SampleRead("", false);
        }
    }

    private String sanitizeSample(String value) {
        List<String> kept = new ArrayList<>();
        int length = 0;
        for (String rawLine : value.lines().toList()) {
            String line = rawLine.strip();
            if (line.isBlank()) continue;
            line = redactor.redact(line);
            int remaining = Math.max(0, maxSampleChars - length);
            if (remaining == 0 || kept.size() >= 16) break;
            line = line.length() <= remaining ? line : line.substring(0, remaining);
            kept.add(line);
            length += line.length() + 1;
        }
        return String.join("\n", kept);
    }

    private static Selection selectDiverse(List<Candidate> input, int limit) {
        if (limit <= 0 || input.isEmpty()) return new Selection(List.of(), 0);
        List<Candidate> deduplicated = new ArrayList<>();
        Set<String> duplicateKeys = new LinkedHashSet<>();
        int duplicates = 0;
        for (Candidate candidate : input) {
            String name = candidate.file().path().substring(candidate.file().path().lastIndexOf('/') + 1)
                .toLowerCase(Locale.ROOT);
            String key = candidate.category() + ":" + name + ":" + candidate.file().bytes();
            if (!Set.of("MANIFEST", "CI_CD", "MIGRATION").contains(candidate.category())
                && !duplicateKeys.add(key)) {
                duplicates++;
                continue;
            }
            duplicateKeys.add(key);
            deduplicated.add(candidate);
        }

        List<Candidate> selected = new ArrayList<>();
        Map<String, Integer> categoryCounts = new LinkedHashMap<>();
        Map<String, Integer> moduleCounts = new LinkedHashMap<>();
        for (String category : GUARANTEED_CATEGORIES) {
            deduplicated.stream()
                .filter(candidate -> category.equals(candidate.category()))
                .filter(candidate -> !selected.contains(candidate))
                .findFirst()
                .ifPresent(candidate -> addSelected(selected, categoryCounts, moduleCounts, candidate));
            if (selected.size() >= limit) return new Selection(List.copyOf(selected), duplicates);
        }

        int categoryCap = Math.max(2, limit / 4);
        int moduleCap = Math.max(3, limit / 3);
        for (Candidate candidate : deduplicated) {
            if (selected.contains(candidate)) continue;
            if (categoryCounts.getOrDefault(candidate.category(), 0) >= categoryCap) continue;
            if (moduleCounts.getOrDefault(module(candidate.file().path()), 0) >= moduleCap) continue;
            addSelected(selected, categoryCounts, moduleCounts, candidate);
            if (selected.size() >= limit) break;
        }
        if (selected.size() < limit) {
            for (Candidate candidate : deduplicated) {
                if (selected.contains(candidate)) continue;
                addSelected(selected, categoryCounts, moduleCounts, candidate);
                if (selected.size() >= limit) break;
            }
        }
        return new Selection(List.copyOf(selected), duplicates);
    }

    private static void addSelected(
        List<Candidate> selected,
        Map<String, Integer> categories,
        Map<String, Integer> modules,
        Candidate candidate
    ) {
        selected.add(candidate);
        categories.merge(candidate.category(), 1, Integer::sum);
        modules.merge(module(candidate.file().path()), 1, Integer::sum);
    }

    private static String module(String path) {
        int slash = path.indexOf('/');
        return slash < 0 ? "." : path.substring(0, slash);
    }

    private static double round(double value) {
        return Math.round(Math.max(0, Math.min(1, value)) * 1000.0) / 1000.0;
    }

    private static String summary(Candidate candidate, String sample) {
        if (sample.isBlank()) {
            return candidate.sourceType() + " 候选，大小 " + candidate.file().bytes() + " 字节";
        }
        String first = sample.lines().findFirst().orElse("").strip();
        if (first.length() > 180) first = first.substring(0, 180);
        return candidate.sourceType() + " 候选；有界内容信号：" + first;
    }

    private static boolean isCandidate(ScannedFile file, String category) {
        if (file.binary() || file.generated()) return false;
        if (file.manifest() || file.keyFile()) return true;
        return isDocumentCategory(category)
            || Set.of("CONFIG", "BUILD", "TEST", "CI_CD", "INFRA", "MIGRATION", "LICENSE").contains(category);
    }

    private static String category(ScannedFile file) {
        String lower = file.path().toLowerCase(Locale.ROOT);
        String name = lower.substring(lower.lastIndexOf('/') + 1);
        String extension = extension(name);
        if (file.binary()) return "BINARY";
        if (file.generated()) return "GENERATED";
        if (lower.startsWith(".projectflow/agent-results/")) return "AGENT_RESULT";
        if (name.startsWith("readme.")) return "README";
        if (name.startsWith("adr-") || lower.contains("/adr/") || lower.contains("/adrs/")) return "ADR";
        if (name.equals("project_context.md") || name.contains("product-context") || name.contains("project-context")) {
            return "PRODUCT_CONTEXT";
        }
        if (name.equals("agents.md") || name.equals("claude.md")) return "AGENT_CONTEXT";
        if (name.contains("changelog") || name.contains("history")) return "CHANGELOG";
        if (name.startsWith("license") || name.startsWith("copying")) return "LICENSE";
        if (lower.startsWith(".github/workflows/") || name.equals(".gitlab-ci.yml") || name.equals("jenkinsfile")) {
            return "CI_CD";
        }
        if (lower.contains("/migration") || lower.contains("/migrations/") || lower.contains("flyway")
            || lower.contains("liquibase")) return "MIGRATION";
        if (name.contains("dockerfile") || name.contains("compose") || lower.contains("/k8s/")
            || lower.contains("/terraform/")) return "INFRA";
        if (file.manifest()) return "MANIFEST";
        if (isTest(lower)) return "TEST";
        if (file.language() != null && !file.language().isBlank()) return "CODE";
        if (DOCUMENT_EXTENSIONS.contains(extension)) {
            return name.startsWith("readme") ? "README" : "UNKNOWN_DOCUMENT";
        }
        if (extension.isBlank() && file.bytes() > 0) return "UNKNOWN_DOCUMENT";
        if (CONFIG_EXTENSIONS.contains(extension)) return "CONFIG";
        if (name.endsWith(".lock") || name.equals("makefile") || name.endsWith(".gradle")) return "BUILD";
        return "OTHER";
    }

    private static String sourceType(ScannedFile file, String category) {
        return switch (category) {
            case "README" -> "README_CANDIDATE";
            case "ADR" -> "ADR_CANDIDATE";
            case "PRODUCT_CONTEXT" -> "PRODUCT_CONTEXT_CANDIDATE";
            case "AGENT_CONTEXT" -> "AGENT_CONTEXT_CANDIDATE";
            case "AGENT_RESULT" -> "AGENT_RESULT_CANDIDATE";
            case "UNKNOWN_DOCUMENT" -> "UNCLASSIFIED_TEXT_CANDIDATE";
            case "MANIFEST" -> "BUILD_OR_PACKAGE_MANIFEST";
            default -> category + "_CANDIDATE";
        };
    }

    private static String deterministicRole(String category) {
        return switch (category) {
            case "GIT", "CHANGELOG", "AGENT_RESULT" -> "HISTORY_CANDIDATE";
            case "README", "PRODUCT_CONTEXT", "AGENT_CONTEXT", "ADR", "UNKNOWN_DOCUMENT" -> "SEMANTIC_CANDIDATE";
            case "MANIFEST", "BUILD", "CONFIG" -> "ENGINEERING_CANDIDATE";
            default -> "EVIDENCE_CANDIDATE";
        };
    }

    private static String deterministicCurrentness(String category) {
        return switch (category) {
            case "CHANGELOG", "AGENT_RESULT" -> "HISTORICAL_OR_CURRENT";
            case "ADR" -> "UNKNOWN";
            default -> "UNKNOWN";
        };
    }

    private static int score(ScannedFile file, String category) {
        int value = switch (category) {
            case "PRODUCT_CONTEXT", "AGENT_CONTEXT", "AGENT_RESULT", "ADR" -> 100;
            case "README", "MANIFEST", "CHANGELOG" -> 95;
            case "UNKNOWN_DOCUMENT" -> 90;
            case "CI_CD", "MIGRATION", "INFRA", "TEST" -> 75;
            case "CONFIG", "BUILD", "LICENSE" -> 65;
            default -> 40;
        };
        if (file.keyFile()) value += 5;
        if (file.bytes() > 0 && file.bytes() <= 262_144) value += 3;
        return value;
    }

    private static boolean isDocumentCategory(String value) {
        return Set.of(
            "DOC", "README", "ADR", "PRODUCT_CONTEXT", "AGENT_CONTEXT",
            "AGENT_RESULT", "CHANGELOG", "UNKNOWN_DOCUMENT"
        ).contains(value);
    }

    private static boolean isTest(String lower) {
        return lower.startsWith("test/") || lower.startsWith("tests/") || lower.contains("/test/")
            || lower.contains("/tests/") || lower.contains("/__tests__/") || lower.contains(".test.")
            || lower.contains(".spec.") || lower.endsWith("test.java") || lower.endsWith("_test.py")
            || lower.endsWith("_test.go");
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 || dot == name.length() - 1 ? "" : name.substring(dot + 1);
    }

    private static String evidenceId(String relativePath) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(relativePath.getBytes(StandardCharsets.UTF_8));
            return "source:" + HexFormat.of().formatHex(digest, 0, 10);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 Java 环境不支持 SHA-256", exception);
        }
    }

    private record Candidate(ScannedFile file, String category, String sourceType, int score) {
    }

    private record Selection(List<Candidate> items, int duplicateCount) {
    }

    private record SampleRead(String value, boolean cacheHit) {
    }

    private record CachedSample(String signature, String value) {
    }

    public record PromptEvidence(
        String id,
        String category,
        String sourceType,
        String locator,
        String summary,
        String boundedSample
    ) {
    }

    public record DiscoveryResult(
        EvidenceSourceMapResponse sourceMap,
        List<PromptEvidence> promptEvidence,
        long documentCount
    ) {
    }
}
