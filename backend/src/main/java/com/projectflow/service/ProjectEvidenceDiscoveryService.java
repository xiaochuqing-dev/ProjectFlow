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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.projectflow.dto.ProjectUnderstandingDtos.EvidenceSourceMapResponse;
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
    private static final List<String> SENSITIVE_MARKERS = List.of(
        "api_key", "apikey", "authorization", "password", "passwd", "secret", "access_token",
        "refresh_token", "private_key", "client_secret", "credential"
    );

    @Value("${projectflow.understanding.max-evidence-candidates:500}")
    private int maxCandidates;

    @Value("${projectflow.understanding.max-scout-evidence:80}")
    private int maxScoutEvidence;

    @Value("${projectflow.understanding.max-evidence-sample-chars:1600}")
    private int maxSampleChars;

    @Value("${projectflow.understanding.max-evidence-sample-bytes:8192}")
    private int maxSampleBytes;

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
        List<ProjectEvidenceSourceResponse> sources = new ArrayList<>();
        List<PromptEvidence> promptEvidence = new ArrayList<>();
        sources.add(new ProjectEvidenceSourceResponse(
            "intake:scan",
            "OTHER",
            "REPOSITORY_INTAKE",
            "",
            "CURRENT_PROJECT_INVENTORY",
            "HIGH",
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
                "HIGH",
                "CURRENT",
                "HIGH",
                "DETERMINISTIC",
                "本地 Git 可用，共 " + scan.intake().git().commitCount() + " 次提交",
                List.of("git:summary")
            ));
        }

        int mappedLimit = Math.min(Math.max(1, maxCandidates), candidates.size());
        int scoutLimit = Math.min(Math.max(0, maxScoutEvidence), mappedLimit);
        for (int index = 0; index < mappedLimit; index++) {
            Candidate candidate = candidates.get(index);
            String id = evidenceId(candidate.file().path());
            String sample = index < scoutLimit ? readSample(scan.root(), candidate.file()) : "";
            boolean sampled = !sample.isBlank();
            ProjectEvidenceSourceResponse source = new ProjectEvidenceSourceResponse(
                id,
                candidate.category(),
                candidate.sourceType(),
                candidate.file().path(),
                deterministicRole(candidate.category()),
                importance(candidate.score()),
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

        long discovered = scan.intake().fileCount() + (scan.intake().git().available() ? 1 : 0);
        long skipped = Math.max(0, scan.intake().fileCount() - candidateCount);
        List<String> warnings = new ArrayList<>();
        if (scan.fileDetailsTruncated()) {
            warnings.add("Evidence Discovery 只使用有界文件详情，未展开的文件保留为未知。");
        }
        if (candidateCount > mappedLimit) {
            warnings.add("候选来源超过安全上限，Source Map 只保留高价值和多类型有界样本。");
        }
        EvidenceSourceMapResponse sourceMap = new EvidenceSourceMapResponse(
            discovered,
            candidateCount,
            promptEvidence.size(),
            promptEvidence.size(),
            skipped,
            Map.copyOf(categoryCounts),
            List.copyOf(sources),
            List.copyOf(warnings)
        );
        long documentCount = categoryCounts.entrySet().stream()
            .filter(entry -> isDocumentCategory(entry.getKey()))
            .mapToLong(Map.Entry::getValue)
            .sum();
        return new DiscoveryResult(sourceMap, List.copyOf(promptEvidence), documentCount);
    }

    private String readSample(Path root, ScannedFile file) {
        if (file.binary() || file.generated() || file.bytes() <= 0) return "";
        Path target = root.resolve(file.path()).normalize();
        if (!target.startsWith(root.normalize())) return "";
        try (var input = Files.newInputStream(target)) {
            byte[] bytes = input.readNBytes(Math.max(256, maxSampleBytes));
            String text = new String(bytes, StandardCharsets.UTF_8);
            return sanitizeSample(text);
        } catch (IOException ignored) {
            return "";
        }
    }

    private String sanitizeSample(String value) {
        List<String> kept = new ArrayList<>();
        int length = 0;
        for (String rawLine : value.lines().toList()) {
            String line = rawLine.strip();
            if (line.isBlank()) continue;
            String lower = line.toLowerCase(Locale.ROOT);
            if (SENSITIVE_MARKERS.stream().anyMatch(lower::contains)) {
                line = "[已隐藏可能的敏感字段]";
            }
            int remaining = Math.max(0, maxSampleChars - length);
            if (remaining == 0 || kept.size() >= 16) break;
            line = line.length() <= remaining ? line : line.substring(0, remaining);
            kept.add(line);
            length += line.length() + 1;
        }
        return String.join("\n", kept);
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

    private static String importance(int score) {
        if (score >= 95) return "HIGH";
        if (score >= 70) return "MEDIUM";
        return "LOW";
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
