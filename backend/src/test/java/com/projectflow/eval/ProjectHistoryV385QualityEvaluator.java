package com.projectflow.eval;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.projectflow.dto.ProjectHistoryDtos.ChangeStory;
import com.projectflow.dto.ProjectHistoryDtos.HistoryChapter;

/** Test-only evaluator for the V3.8.5 presentation contract. */
public final class ProjectHistoryV385QualityEvaluator {
    private static final List<String> GENERIC_TEMPLATES = List.of(
        "相关变化", "形成初始结果", "进入当前时间点可确认的新状态", "工程分组", "修改 n 个文件"
    );

    private ProjectHistoryV385QualityEvaluator() {
    }

    public static Metrics evaluate(JsonNode groundTruth, List<ChangeStory> stories, List<HistoryChapter> chapters) {
        List<ChangeStory> safeStories = stories == null ? List.of() : stories;
        List<HistoryChapter> safeChapters = chapters == null ? List.of() : chapters;
        List<String> forbidden = textList(groundTruth.path("technicalWordRules").path("forbiddenFirstLayer"));
        Set<String> allowed = new HashSet<>(textList(groundTruth.path("technicalWordRules").path("allowedProductTerms")));
        int primary = 0;
        int supporting = 0;
        int orphanSupporting = 0;
        int reasonWithoutEvidence = 0;
        int technicalLeak = 0;
        int generic = 0;
        int complete = 0;
        Set<String> referencedSupporting = new HashSet<>();
        for (ChangeStory story : safeStories) {
            if ("SUPPORTING".equalsIgnoreCase(story.role())) supporting++;
            else primary++;
            referencedSupporting.addAll(story.supportingChangeRefs());
            if (story.reason() != null && !story.reason().isBlank() && story.reasonEvidenceRefs().isEmpty()) {
                reasonWithoutEvidence++;
            }
            String firstLayer = String.join(" ", List.of(
                safe(story.humanTitle()), safe(story.oneSentenceSummary()), safe(story.beforeState()),
                safe(story.change()), safe(story.afterState())
            ));
            if (containsForbidden(firstLayer, forbidden, allowed)) technicalLeak++;
            if (containsAny(firstLayer, GENERIC_TEMPLATES)) generic++;
            if (!safe(story.beforeState()).isBlank() && !safe(story.change()).isBlank() && !safe(story.afterState()).isBlank()) complete++;
        }
        for (ChangeStory story : safeStories) {
            if ("SUPPORTING".equalsIgnoreCase(story.role())
                && story.primaryStoryId().isBlank() && !referencedSupporting.contains(story.id())) orphanSupporting++;
        }
        Set<String> chapterRefs = new HashSet<>();
        int overlap = 0;
        for (HistoryChapter chapter : safeChapters) {
            for (String ref : chapter.storyRefs()) if (!chapterRefs.add(ref)) overlap++;
        }
        double denominator = Math.max(1, safeStories.size());
        double genericRate = generic / denominator;
        double technicalLeakRate = technicalLeak / denominator;
        double completenessRate = complete / denominator;
        JsonNode gates = groundTruth.path("hardGates");
        boolean passes = orphanSupporting <= gates.path("primarySupportingOrphanCountMax").asInt(0)
            && overlap <= gates.path("chapterStoryOverlapCountMax").asInt(0)
            && reasonWithoutEvidence <= gates.path("reasonWithoutEvidenceCountMax").asInt(0)
            && genericRate <= gates.path("genericTemplateRateMax").asDouble(0.05)
            && technicalLeakRate <= gates.path("firstLayerTechnicalLeakRateMax").asDouble(0.05);
        return new Metrics(primary, supporting, orphanSupporting, overlap, reasonWithoutEvidence, technicalLeak,
            generic, genericRate, technicalLeakRate, completenessRate, passes);
    }

    private static boolean containsForbidden(String text, List<String> forbidden, Set<String> allowed) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (String term : forbidden) {
            if (term == null || term.isBlank()) continue;
            if (allowed.stream().anyMatch(value -> value.equalsIgnoreCase(term))) continue;
            if (lower.contains(term.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private static boolean containsAny(String text, List<String> values) {
        String lower = text.toLowerCase(Locale.ROOT);
        return values.stream().anyMatch(value -> lower.contains(value.toLowerCase(Locale.ROOT)));
    }

    private static List<String> textList(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node != null && node.isArray()) node.forEach(value -> values.add(value.asText()));
        return values;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public record Metrics(
        int primaryCount,
        int supportingCount,
        int orphanSupportingCount,
        int chapterStoryOverlapCount,
        int reasonWithoutEvidenceCount,
        int technicalLeakCount,
        int genericTemplateCount,
        double genericTemplateRate,
        double technicalLeakRate,
        double beforeChangeAfterCompleteness,
        boolean passes
    ) {
    }
}
