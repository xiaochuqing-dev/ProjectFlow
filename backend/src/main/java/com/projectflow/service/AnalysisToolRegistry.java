package com.projectflow.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.projectflow.dto.ProjectUnderstandingDtos.EvidenceSourceMapResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.HistoricalCoverageResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.ProjectStructureIndexResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.RepositoryIntakeResponse;

@Component
public class AnalysisToolRegistry {
    private static final Set<String> REGISTERED_CAPABILITIES = Set.of(
        "FILESYSTEM",
        "MANIFEST",
        "SCIP",
        "GIT_HISTORY",
        "GIT_TAG",
        "WORKTREE",
        "DOC_READER",
        "AGENT_RESULT",
        "REMOTE_GITHUB",
        "REMOTE_GITLAB",
        "REMOTE_GITEA"
    );

    public List<String> defaults(
        RepositoryIntakeResponse intake,
        ProjectStructureIndexResponse index,
        EvidenceSourceMapResponse sourceMap,
        HistoricalCoverageResponse history
    ) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        result.add("FILESYSTEM");
        if (index.indexerSource().toUpperCase(Locale.ROOT).contains("SCIP")) result.add("SCIP");
        return List.copyOf(result);
    }

    public List<String> eligibleCapabilities(
        RepositoryIntakeResponse intake,
        ProjectStructureIndexResponse index,
        EvidenceSourceMapResponse sourceMap
    ) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String capability : List.of(
            "MANIFEST", "GIT_HISTORY", "GIT_TAG", "WORKTREE", "DOC_READER", "AGENT_RESULT"
        )) {
            if (isAvailable(capability, intake, index, sourceMap)) result.add(capability);
        }
        return List.copyOf(result);
    }

    public List<String> validateRequested(
        List<String> requested,
        List<String> defaults,
        RepositoryIntakeResponse intake,
        ProjectStructureIndexResponse index,
        EvidenceSourceMapResponse sourceMap
    ) {
        LinkedHashSet<String> result = new LinkedHashSet<>(defaults);
        Set<String> eligible = Set.copyOf(eligibleCapabilities(intake, index, sourceMap));
        if (requested == null) return List.copyOf(result);
        for (String raw : requested) {
            String capability = normalizeCapability(raw);
            if (!REGISTERED_CAPABILITIES.contains(capability)) continue;
            if (eligible.contains(capability)) result.add(capability);
        }
        return List.copyOf(result);
    }

    public static String normalizeCapability(String value) {
        if (value == null) return "";
        String normalized = value.strip().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
        return switch (normalized) {
            case "DOCREADER" -> "DOC_READER";
            case "AGENTRESULT" -> "AGENT_RESULT";
            case "GITHISTORY" -> "GIT_HISTORY";
            case "GITTAG" -> "GIT_TAG";
            case "FILE_SYSTEM" -> "FILESYSTEM";
            default -> normalized;
        };
    }

    public List<String> unavailableReasons(
        RepositoryIntakeResponse intake,
        ProjectStructureIndexResponse index
    ) {
        List<String> result = new ArrayList<>();
        if (!intake.git().available()) result.add("GIT_HISTORY：本地目录没有可用 Git 历史");
        if (index.symbols().isEmpty()) result.add("SCIP：没有可用且有效的 precise index");
        result.add("REMOTE_REPOSITORY：远程 PR、Issue、Release 仅是未来可选增强，不阻断本地分析");
        return List.copyOf(result);
    }

    private static boolean isAvailable(
        String capability,
        RepositoryIntakeResponse intake,
        ProjectStructureIndexResponse index,
        EvidenceSourceMapResponse sourceMap
    ) {
        return switch (capability) {
            case "FILESYSTEM" -> true;
            case "MANIFEST" -> !intake.manifestFiles().isEmpty();
            case "SCIP" -> !index.symbols().isEmpty();
            case "GIT_HISTORY", "GIT_TAG", "WORKTREE" -> intake.git().available();
            case "DOC_READER" -> sourceMap.categoryCounts().entrySet().stream()
                .anyMatch(entry -> isDocumentCategory(entry.getKey()) && entry.getValue() > 0);
            case "AGENT_RESULT" -> sourceMap.categoryCounts().getOrDefault("AGENT_RESULT", 0L) > 0;
            default -> false;
        };
    }

    private static boolean isDocumentCategory(String category) {
        return Set.of(
            "DOC", "README", "ADR", "PRODUCT_CONTEXT", "AGENT_CONTEXT",
            "AGENT_RESULT", "CHANGELOG", "UNKNOWN_DOCUMENT"
        ).contains(category);
    }

}
