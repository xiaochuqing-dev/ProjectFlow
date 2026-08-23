package com.projectflow.eval;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.projectflow.dto.ProjectHistoryDtos.ChangeStory;
import com.projectflow.dto.ProjectHistoryDtos.HistoryChapter;
import com.projectflow.service.ProjectHistoryChapterRepresentationPlanner;
import com.projectflow.service.ProjectHistoryLanguageService;

final class ProjectHistoryV385ReviewSamples {
    private ProjectHistoryV385ReviewSamples() {
    }

    static List<Map<String, Object>> stories(List<ChangeStory> stories, int limit, String presentationRevision) {
        String revision = requiredRevision(presentationRevision);
        return stories.stream().limit(limit).map(story -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("id", safe(story.id()));
            value.put("title", safe(story.humanTitle()));
            value.put("summary", safe(story.oneSentenceSummary()));
            value.put("before", safe(story.beforeState()));
            value.put("change", safe(story.change()));
            value.put("after", safe(story.afterState()));
            value.put("reason", safe(story.reason()));
            value.put("role", safe(story.role()));
            value.put("primaryStoryId", safe(story.primaryStoryId()));
            value.put("supportingChangeRefs", story.supportingChangeRefs());
            value.put("reasonEvidenceRefs", story.reasonEvidenceRefs());
            value.put("evidenceRefs", story.evidenceRefs().stream().limit(5).toList());
            value.put("unknowns", story.unknowns());
            value.put("conflicts", story.conflicts());
            value.put("presentationAuthority", safe(story.presentationAuthority()));
            value.put("summaryStatus", safe(story.summaryStatus()));
            value.put("presentationRevision", revision);
            value.put("displayStatus", safe(story.displayStatus()));
            value.put("hiddenByDefault", story.hiddenByDefault());
            value.put("pinned", story.pinned());
            value.put("claimAttribution", story.claimAttribution());
            return Map.copyOf(value);
        }).toList();
    }

    static List<Map<String, Object>> chapters(List<HistoryChapter> chapters, int limit, String presentationRevision) {
        String revision = requiredRevision(presentationRevision);
        List<Map<String, Object>> samples = new ArrayList<>();
        for (HistoryChapter chapter : chapters.stream().limit(limit).toList()) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("id", safe(chapter.id()));
            value.put("title", safe(chapter.title()));
            value.put("summary", safe(chapter.summary()));
            value.put("from", chapter.from() == null ? "" : chapter.from().toString());
            value.put("to", chapter.to() == null ? "" : chapter.to().toString());
            value.put("storyRefs", chapter.storyRefs().stream().limit(20).toList());
            value.put("storyCount", chapter.storyCount());
            value.put("rawEventCount", chapter.rawEventCount());
            value.put("coverage", safe(chapter.coverage()));
            value.put("presentationAuthority", safe(chapter.presentationAuthority()));
            value.put("presentationRevision", revision);
            value.put("hiddenByDefault", chapter.hiddenByDefault());
            value.put("pinned", chapter.pinned());
            samples.add(Map.copyOf(value));
        }
        return List.copyOf(samples);
    }

    static List<Map<String, Object>> chapterRepresentativeness(
        List<HistoryChapter> chapters,
        List<ChangeStory> stories,
        int limit,
        String presentationRevision
    ) {
        String revision = requiredRevision(presentationRevision);
        Map<String, ChangeStory> storiesById = stories.stream().collect(Collectors.toMap(
            ChangeStory::id, Function.identity(), (left, right) -> left, LinkedHashMap::new
        ));
        ProjectHistoryChapterRepresentationPlanner planner = new ProjectHistoryChapterRepresentationPlanner(
            new ProjectHistoryLanguageService()
        );
        List<Map<String, Object>> samples = new ArrayList<>();
        for (HistoryChapter chapter : chapters.stream().limit(limit).toList()) {
            List<ChangeStory> members = chapter.storyRefs().stream().map(storiesById::get)
                .filter(java.util.Objects::nonNull).toList();
            ProjectHistoryChapterRepresentationPlanner.Plan plan = planner.plan(members);
            List<Map<String, Object>> clusters = plan.clusters().stream().map(cluster -> {
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("id", cluster.id());
                value.put("role", cluster.role());
                value.put("label", safe(cluster.humanLabel()));
                value.put("weight", Math.round(cluster.weight() * 1000.0) / 1000.0);
                value.put("primaryStoryCount", cluster.primaryStoryCount());
                value.put("supportingStoryCount", cluster.supportingStoryCount());
                value.put("activeDays", cluster.activeDays());
                value.put("representativeOutcomes", cluster.representativeOutcomes());
                value.put("claimCeiling", cluster.claimCeiling());
                value.put("unknowns", cluster.unknowns());
                value.put("conflicts", cluster.conflicts());
                value.put("selected", plan.requiredRepresentativeClusterIds().contains(cluster.id()));
                return Map.copyOf(value);
            }).toList();
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("id", safe(chapter.id()));
            value.put("title", safe(chapter.title()));
            value.put("summary", safe(chapter.summary()));
            value.put("from", chapter.from() == null ? "" : chapter.from().toString());
            value.put("to", chapter.to() == null ? "" : chapter.to().toString());
            value.put("storyCount", chapter.storyCount());
            value.put("primaryStoryCount", plan.primaryStoryCount());
            value.put("supportingStoryCount", plan.supportingStoryCount());
            value.put("representativeClusters", clusters);
            value.put("representativeClusterCount", plan.clusters().size());
            value.put("dominantClusterCount", plan.dominantClusterCount());
            value.put("selectedRepresentativeClusterIds", plan.requiredRepresentativeClusterIds());
            value.put("selectedRepresentativeOutcomes", plan.representativeOutcomes());
            value.put("representativePrimaryCoverage", plan.representativePrimaryCoverage());
            value.put("coherenceRisk", plan.coherenceRisk());
            value.put("needsSplit", plan.needsSplit());
            value.put("boundarySignals", chapter.boundarySignals());
            value.put("evidenceSafeStatus", "VALIDATED_WITHIN_STORY_CLAIM_CEILINGS");
            value.put("unknowns", plan.unknowns());
            value.put("conflicts", plan.conflicts());
            value.put("narrativeStatus", "INFERRED_NON_AUTHORITATIVE".equals(chapter.authority())
                ? "MODEL_VALIDATED_REPRESENTATION" : "DETERMINISTIC_REPRESENTATION");
            value.put("deterministicFallback", !"INFERRED_NON_AUTHORITATIVE".equals(chapter.authority()));
            value.put("presentationAuthority", safe(chapter.presentationAuthority()));
            value.put("presentationRevision", revision);
            samples.add(Map.copyOf(value));
        }
        return List.copyOf(samples);
    }

    private static String requiredRevision(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Human review candidate is missing presentationRevision");
        }
        return value.trim();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
