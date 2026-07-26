package com.projectflow.service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;

import org.springframework.stereotype.Service;

import com.projectflow.dto.ProjectUnderstandingDtos.AdaptiveAnalysisPlanResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.AnalysisExecutionDiagnostic;
import com.projectflow.dto.ProjectUnderstandingDtos.AnalysisExecutionResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.AnalysisToolEvidenceResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.EvidenceSourceMapResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.ProjectEvidenceSourceResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.ProjectStructureIndexResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.RepositoryIntakeResponse;
import com.projectflow.service.AnalysisCapabilityProvider.CapabilityRequest;
import com.projectflow.service.AnalysisCapabilityProvider.CapabilityResult;
import com.projectflow.service.AnalysisCapabilityProvider.ExecutionBudget;
import com.projectflow.service.ProjectEvidenceDiscoveryService.PromptEvidence;

@Service
public class AnalysisExecutionCoordinator {
    private static final String RESULT_VERSION = "analysis-execution-v1";
    private static final int MAX_EXECUTED_CAPABILITIES = 8;
    private static final int MAX_TOTAL_RESULT_CHARS = 80_000;
    private static final Set<String> REUSED_CAPABILITIES = Set.of("FILESYSTEM", "SCIP");

    private final List<AnalysisCapabilityProvider> providers;
    private final SensitiveContentRedactor redactor;

    public AnalysisExecutionCoordinator(
        List<AnalysisCapabilityProvider> providers,
        SensitiveContentRedactor redactor
    ) {
        this.providers = List.copyOf(providers);
        this.redactor = redactor;
    }

    public ExecutionOutcome execute(
        Path projectRoot,
        RepositoryIntakeResponse intake,
        ProjectStructureIndexResponse index,
        EvidenceSourceMapResponse sourceMap,
        AdaptiveAnalysisPlanResponse plan
    ) {
        long started = System.nanoTime();
        Set<String> allowed = new LinkedHashSet<>();
        sourceMap.sources().stream().map(ProjectEvidenceSourceResponse::id).forEach(allowed::add);
        index.evidence().forEach(item -> allowed.add(item.id()));
        allowed.add("intake:scan");
        List<String> requested = plan.toolsToInvoke().stream()
            .map(AnalysisExecutionCoordinator::normalized)
            .filter(value -> !value.isBlank())
            .distinct()
            .limit(20)
            .toList();
        List<String> executed = new ArrayList<>();
        List<String> reused = new ArrayList<>();
        List<AnalysisToolEvidenceResponse> evidence = new ArrayList<>();
        List<PromptEvidence> promptEvidence = new ArrayList<>();
        List<AnalysisExecutionDiagnostic> diagnostics = new ArrayList<>();
        int consumedChars = 0;
        boolean budgetExhausted = false;

        for (String capability : requested) {
            ModelCancellationContext.throwIfCancelled();
            if (REUSED_CAPABILITIES.contains(capability)) {
                reused.add(capability);
                diagnostics.add(new AnalysisExecutionDiagnostic(
                    capability,
                    "REUSED",
                    0,
                    0,
                    0,
                    0,
                    capability + " 复用本轮已生成的确定性结构证据"
                ));
                continue;
            }
            if (executed.size() >= MAX_EXECUTED_CAPABILITIES || consumedChars >= MAX_TOTAL_RESULT_CHARS) {
                budgetExhausted = true;
                diagnostics.add(new AnalysisExecutionDiagnostic(
                    capability, "SKIPPED_BUDGET", 0, 0, 0, 0, "执行预算已用尽"
                ));
                continue;
            }
            AnalysisCapabilityProvider provider = providers.stream()
                .filter(candidate -> candidate.supports(capability))
                .findFirst()
                .orElse(null);
            if (provider == null) {
                diagnostics.add(new AnalysisExecutionDiagnostic(
                    capability, "UNAVAILABLE", 0, 0, 0, 0, "没有已注册且可用的 Capability Provider"
                ));
                continue;
            }
            long capabilityStarted = System.nanoTime();
            try {
                CapabilityResult result = provider.execute(new CapabilityRequest(
                    capability,
                    projectRoot.toAbsolutePath().normalize(),
                    intake,
                    index,
                    sourceMap,
                    plan,
                    Set.copyOf(allowed),
                    budget(capability, MAX_TOTAL_RESULT_CHARS - consumedChars)
                ));
                List<AnalysisToolEvidenceResponse> validEvidence = validateEvidence(
                    result.evidence(),
                    allowed,
                    capability
                );
                Set<String> validIds = validEvidence.stream()
                    .map(AnalysisToolEvidenceResponse::id)
                    .collect(java.util.stream.Collectors.toSet());
                List<PromptEvidence> validPrompt = result.promptEvidence().stream()
                    .filter(item -> validIds.contains(item.id()))
                    .map(this::sanitizePromptEvidence)
                    .toList();
                validEvidence.forEach(item -> allowed.add(item.id()));
                evidence.addAll(validEvidence);
                promptEvidence.addAll(validPrompt);
                consumedChars += validPrompt.stream().mapToInt(item -> item.boundedSample().length()).sum();
                executed.add(capability);
                diagnostics.add(new AnalysisExecutionDiagnostic(
                    capability,
                    result.status(),
                    elapsedMs(capabilityStarted),
                    result.selectedItemCount(),
                    validEvidence.size(),
                    result.consumedChars(),
                    bounded(redactor.redact(result.message()), 400)
                ));
            } catch (CancellationException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                diagnostics.add(new AnalysisExecutionDiagnostic(
                    capability,
                    "FAILED",
                    elapsedMs(capabilityStarted),
                    0,
                    0,
                    0,
                    "工具失败，已回退到确定性项目档案"
                ));
            }
        }

        EvidenceSourceMapResponse mergedSourceMap = mergeSourceMap(sourceMap, evidence);
        AnalysisExecutionResponse response = new AnalysisExecutionResponse(
            RESULT_VERSION,
            cacheKey(intake.sourceRevision(), requested),
            intake.sourceRevision(),
            requested,
            List.copyOf(executed),
            List.copyOf(reused),
            List.copyOf(evidence),
            List.copyOf(diagnostics),
            elapsedMs(started),
            budgetExhausted,
            Instant.now(),
            ""
        );
        return new ExecutionOutcome(
            response,
            mergedSourceMap,
            List.copyOf(promptEvidence),
            Set.copyOf(allowed),
            !promptEvidence.isEmpty()
        );
    }

    private List<AnalysisToolEvidenceResponse> validateEvidence(
        List<AnalysisToolEvidenceResponse> candidates,
        Set<String> allowed,
        String expectedCapability
    ) {
        if (candidates == null) return List.of();
        List<AnalysisToolEvidenceResponse> result = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();
        for (AnalysisToolEvidenceResponse item : candidates) {
            if (item == null
                || item.id() == null
                || !item.id().startsWith("tool:")
                || !expectedCapability.equals(normalized(item.capability()))
                || !ids.add(item.id())) {
                continue;
            }
            List<String> refs = item.evidenceRefs() == null
                ? List.of()
                : item.evidenceRefs().stream().filter(allowed::contains).distinct().limit(20).toList();
            if (refs.isEmpty()) continue;
            result.add(new AnalysisToolEvidenceResponse(
                item.id(),
                normalized(item.capability()),
                "TOOL_RESULT",
                bounded(item.sourceType(), 80),
                bounded(redactor.redact(item.summary()), 1_000),
                refs
            ));
        }
        return List.copyOf(result);
    }

    private PromptEvidence sanitizePromptEvidence(PromptEvidence item) {
        return new PromptEvidence(
            item.id(),
            "TOOL_RESULT",
            bounded(item.sourceType(), 80),
            safeRelative(item.locator()),
            bounded(redactor.redact(item.summary()), 1_000),
            bounded(redactor.redact(item.boundedSample()), 16_000)
        );
    }

    private static EvidenceSourceMapResponse mergeSourceMap(
        EvidenceSourceMapResponse sourceMap,
        List<AnalysisToolEvidenceResponse> evidence
    ) {
        if (evidence.isEmpty()) return sourceMap;
        List<ProjectEvidenceSourceResponse> sources = new ArrayList<>(sourceMap.sources());
        evidence.forEach(item -> sources.add(new ProjectEvidenceSourceResponse(
            item.id(),
            "TOOL_RESULT",
            item.sourceType(),
            "",
            "EXECUTED_TOOL_EVIDENCE",
            "HIGH",
            "CURRENT",
            "HIGH",
            "TARGETED_DEEP_READ",
            item.summary(),
            item.evidenceRefs()
        )));
        Map<String, Long> categories = new LinkedHashMap<>(sourceMap.categoryCounts());
        categories.merge("TOOL_RESULT", (long) evidence.size(), Long::sum);
        return new EvidenceSourceMapResponse(
            sourceMap.discoveredEvidenceCount() + evidence.size(),
            sourceMap.candidateEvidenceCount(),
            sourceMap.scoutEvidenceCount(),
            evidence.size(),
            sourceMap.skippedCount(),
            Map.copyOf(categories),
            List.copyOf(sources),
            sourceMap.warnings(),
            sourceMap.diversityMetrics()
        );
    }

    private static ExecutionBudget budget(String capability, int remaining) {
        int total = Math.max(1_000, Math.min(remaining, switch (capability) {
            case "DOC_READER" -> 48_000;
            case "MANIFEST" -> 18_000;
            case "AGENT_RESULT" -> 16_000;
            default -> 20_000;
        }));
        return new ExecutionBudget(
            switch (capability) {
                case "DOC_READER" -> 8;
                case "MANIFEST" -> 12;
                case "AGENT_RESULT" -> 6;
                default -> 500;
            },
            "DOC_READER".equals(capability) ? 12_000 : 8_000,
            total,
            "GIT_HISTORY".equals(capability) ? 12_000 : 8_000
        );
    }

    private static String cacheKey(String revision, List<String> capabilities) {
        try {
            String input = revision + ":" + String.join(",", capabilities);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 Java 环境不支持 SHA-256", exception);
        }
    }

    private static String safeRelative(String value) {
        if (value == null) return "";
        String normalized = value.strip().replace('\\', '/');
        if (normalized.startsWith("/") || normalized.matches("^[A-Za-z]:/.*") || normalized.contains("../")) return "";
        return bounded(normalized, 260);
    }

    private static String normalized(String value) {
        return value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
    }

    private static String bounded(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static long elapsedMs(long started) {
        return Math.max(0, (System.nanoTime() - started) / 1_000_000);
    }

    public record ExecutionOutcome(
        AnalysisExecutionResponse response,
        EvidenceSourceMapResponse sourceMap,
        List<PromptEvidence> promptEvidence,
        Set<String> allowedEvidence,
        boolean highValueEvidenceProduced
    ) {
    }
}
