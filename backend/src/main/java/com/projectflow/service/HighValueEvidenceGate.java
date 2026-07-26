package com.projectflow.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.projectflow.dto.ProjectUnderstandingDtos.AdaptiveAnalysisPlanResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.AnalysisToolEvidenceResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.EvidenceSourceMapResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.SecondStageDecisionResponse;
import com.projectflow.service.ProjectEvidenceDiscoveryService.PromptEvidence;

final class HighValueEvidenceGate {
    private static final int MIN_SUBSTANTIVE_CHARS = 180;
    private static final Set<String> DEEP_CONTENT_CAPABILITIES = Set.of(
        "DOC_READER", "MANIFEST", "AGENT_RESULT"
    );
    private static final Set<String> HISTORY_CAPABILITIES = Set.of("GIT_HISTORY", "GIT_TAG");
    private static final List<String> CONFLICT_MARKERS = List.of(
        "冲突", "不一致", "过期", "取代", "废弃", "conflict", "stale", "superseded", "deprecated"
    );

    private HighValueEvidenceGate() {
    }

    static SecondStageDecisionResponse decide(
        AdaptiveAnalysisPlanResponse plan,
        List<AnalysisToolEvidenceResponse> evidence,
        List<PromptEvidence> promptEvidence
    ) {
        return decide(plan, evidence, promptEvidence, null);
    }

    static SecondStageDecisionResponse decide(
        AdaptiveAnalysisPlanResponse plan,
        List<AnalysisToolEvidenceResponse> evidence,
        List<PromptEvidence> promptEvidence,
        EvidenceSourceMapResponse stageOneSourceMap
    ) {
        List<String> triggerReasons = new ArrayList<>();
        List<String> skippedReasons = new ArrayList<>();
        LinkedHashSet<String> evidenceIds = new LinkedHashSet<>();
        if (plan.maxModelRequests() < 2) {
            skippedReasons.add("MODEL_REQUEST_BUDGET_LIMIT");
            return decision(false, triggerReasons, skippedReasons, evidenceIds);
        }
        if (evidence == null || evidence.isEmpty() || promptEvidence == null || promptEvidence.isEmpty()) {
            skippedReasons.add("NO_VALIDATED_TOOL_EVIDENCE");
            return decision(false, triggerReasons, skippedReasons, evidenceIds);
        }

        Map<String, String> capabilityById = new LinkedHashMap<>();
        evidence.forEach(item -> capabilityById.put(item.id(), normalized(item.capability())));
        Set<String> knownStageOneContent = new HashSet<>();
        if (stageOneSourceMap != null && stageOneSourceMap.sources() != null) {
            stageOneSourceMap.sources().stream()
                .map(source -> normalizedContent(source.summary()))
                .filter(value -> !value.isBlank())
                .forEach(knownStageOneContent::add);
        }
        Set<String> contentSignatures = new HashSet<>();
        for (PromptEvidence item : promptEvidence) {
            String capability = capabilityById.getOrDefault(item.id(), "");
            String sample = normalizedContent(item.boundedSample());
            if (sample.length() < MIN_SUBSTANTIVE_CHARS) {
                skippedReasons.add("INSUFFICIENT_CONTENT:" + item.id());
                continue;
            }
            if (!contentSignatures.add(sample)) {
                skippedReasons.add("DUPLICATE_TOOL_CONTENT:" + item.id());
                continue;
            }
            if (knownStageOneContent.contains(sample)) {
                skippedReasons.add("KNOWN_STAGE_ONE_CONTENT:" + item.id());
                continue;
            }
            if ("WORKTREE".equals(capability) && isCleanWorktree(sample)) {
                skippedReasons.add("METADATA_ONLY_WORKTREE:" + item.id());
                continue;
            }
            String reason = triggerReason(capability, sample);
            if (reason.isBlank()) {
                skippedReasons.add("NO_NEW_SEMANTIC_VALUE:" + item.id());
                continue;
            }
            evidenceIds.add(item.id());
            triggerReasons.add(reason + ":" + item.id());
        }
        if (evidenceIds.isEmpty() && skippedReasons.isEmpty()) {
            skippedReasons.add("NO_HIGH_VALUE_EVIDENCE");
        }
        return decision(!evidenceIds.isEmpty(), triggerReasons, skippedReasons, evidenceIds);
    }

    private static String triggerReason(String capability, String sample) {
        if (containsAny(sample, CONFLICT_MARKERS)) return "CONFLICT_OR_CURRENTNESS_EVIDENCE";
        if (DEEP_CONTENT_CAPABILITIES.contains(capability)) return "NEW_DEEP_CONTENT";
        if (HISTORY_CAPABILITIES.contains(capability)) return "NEW_HISTORY_ANCHOR";
        if ("WORKTREE".equals(capability)) return "NEW_WORKTREE_CHANGE_DETAIL";
        return "";
    }

    private static boolean isCleanWorktree(String sample) {
        return sample.equals("clean")
            || sample.contains("工作区干净")
            || sample.contains("nothing to commit")
            || sample.contains("working tree clean");
    }

    private static boolean containsAny(String value, List<String> markers) {
        return markers.stream().anyMatch(value::contains);
    }

    private static String normalizedContent(String value) {
        if (value == null) return "";
        return value.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static String normalized(String value) {
        return value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
    }

    private static SecondStageDecisionResponse decision(
        boolean triggered,
        List<String> triggerReasons,
        List<String> skippedReasons,
        Set<String> evidenceIds
    ) {
        return new SecondStageDecisionResponse(
            triggered,
            List.copyOf(new LinkedHashSet<>(triggerReasons)),
            List.copyOf(new LinkedHashSet<>(skippedReasons)),
            List.copyOf(evidenceIds)
        );
    }
}
