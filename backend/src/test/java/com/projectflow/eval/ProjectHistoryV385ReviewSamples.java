package com.projectflow.eval;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.projectflow.dto.ProjectHistoryDtos.ChangeStory;
import com.projectflow.dto.ProjectHistoryDtos.HistoryChapter;

final class ProjectHistoryV385ReviewSamples {
    private ProjectHistoryV385ReviewSamples() {
    }

    static List<Map<String, Object>> stories(List<ChangeStory> stories, int limit) {
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
            value.put("presentationRevision", requiredRevision(story.presentationRevision()));
            value.put("displayStatus", safe(story.displayStatus()));
            value.put("hiddenByDefault", story.hiddenByDefault());
            value.put("pinned", story.pinned());
            return Map.copyOf(value);
        }).toList();
    }

    static List<Map<String, Object>> chapters(List<HistoryChapter> chapters, int limit) {
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
            value.put("presentationRevision", requiredRevision(chapter.presentationRevision()));
            value.put("hiddenByDefault", chapter.hiddenByDefault());
            value.put("pinned", chapter.pinned());
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
