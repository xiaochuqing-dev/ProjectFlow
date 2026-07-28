package com.projectflow.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.projectflow.dto.ProjectUnderstandingDtos.EvidenceSourceMapResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.HistoricalCoverageResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.ProjectStructureIndexResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.RepositoryIntakeResponse;

/** Objective eligibility and alias validation. Semantic view selection remains model-owned. */
@Component
public class AnalysisViewRegistry {
    private static final List<String> CODE_VIEWS = List.of(
        "CURRENT_STATE", "TECHNOLOGY", "CURRENT_STRUCTURE", "ENGINEERING_STATE",
        "PURPOSE", "INPUT_OUTPUT", "DEPENDENCIES", "USAGE",
        "FRONTEND", "BACKEND", "ROUTES", "COMPONENTS", "API_DEPENDENCIES",
        "API", "SERVICES", "DATA", "AUTH", "INTEGRATIONS", "INTEGRATION_RELATIONS",
        "DESKTOP_RUNTIME", "ENTRY_POINTS", "WORKSPACES", "MODULE_BOUNDARIES", "ARCHITECTURE"
    );
    private static final List<String> MATERIAL_VIEWS = List.of(
        "CURRENT_STATE", "DOCUMENT_OVERVIEW", "PROCESS_EVIDENCE", "PROCESS_METADATA",
        "CURRENTNESS", "CONFLICTS", "LIMITATIONS", "UNKNOWN"
    );
    private static final List<String> HISTORY_VIEWS = List.of(
        "HISTORICAL_COVERAGE", "LIMITED_HISTORY", "MILESTONE_WINDOWS", "EVOLUTION"
    );

    public List<String> eligible(
        RepositoryIntakeResponse intake,
        ProjectStructureIndexResponse index,
        EvidenceSourceMapResponse sourceMap,
        HistoricalCoverageResponse history
    ) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        boolean hasCode = intake.sourceFileCount() > 0;
        boolean hasMaterial = sourceMap.scoutEvidenceCount() > 0;
        if (hasCode) result.addAll(CODE_VIEWS);
        if (hasMaterial) result.addAll(MATERIAL_VIEWS);
        if (history.historyAvailable()) result.addAll(HISTORY_VIEWS);
        if (hasCode && intake.sourceFileCount() <= 2 && intake.estimatedLoc() <= 500) {
            result.remove("ARCHITECTURE");
        }
        if (!index.engineeringSignals().isEmpty()) result.add("ENGINEERING_STATE");
        return List.copyOf(result);
    }

    public List<String> validate(List<String> requested, List<String> eligible) {
        Set<String> allowed = Set.copyOf(eligible == null ? List.of() : eligible);
        if (requested == null) return List.of();
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String value : requested) {
            String normalized = normalize(value);
            if (allowed.contains(normalized)) result.add(normalized);
        }
        return List.copyOf(result);
    }

    public static String normalize(String value) {
        if (value == null) return "";
        String normalized = value.strip().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
        return switch (normalized) {
            case "CURRENTSTATE" -> "CURRENT_STATE";
            case "CURRENTSTRUCTURE" -> "CURRENT_STRUCTURE";
            case "ENGINEERINGSTATE" -> "ENGINEERING_STATE";
            case "INPUTOUTPUT" -> "INPUT_OUTPUT";
            case "DOCUMENTPURPOSE", "DOCUMENTATION", "TOPICS_AND_DECISIONS" -> "DOCUMENT_OVERVIEW";
            case "APIDEPENDENCIES" -> "API_DEPENDENCIES";
            case "INTEGRATIONRELATIONS" -> "INTEGRATION_RELATIONS";
            case "DESKTOPRUNTIME" -> "DESKTOP_RUNTIME";
            case "ENTRYPOINTS" -> "ENTRY_POINTS";
            case "MODULEBOUNDARIES" -> "MODULE_BOUNDARIES";
            case "HISTORICALCOVERAGE", "TIMELINE" -> "HISTORICAL_COVERAGE";
            case "LIMITEDHISTORY" -> "LIMITED_HISTORY";
            case "MILESTONEWINDOWS" -> "MILESTONE_WINDOWS";
            case "DATABASE" -> "DATA";
            case "WEB_BACKEND" -> "BACKEND";
            default -> normalized;
        };
    }

}
