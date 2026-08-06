package com.projectflow.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.projectflow.dto.ProjectHistoryDtos.ChangeStory;

/** Creates bounded, stable semantic windows without one request per Commit. */
public final class ProjectHistoryWindowPlanner {
    public static final int DEFAULT_STORY_LIMIT = 32;
    public static final int DEFAULT_EVENT_LIMIT = 360;
    public static final int MAX_WINDOWS = 16;

    public List<Window> plan(List<ChangeStory> stories, Set<String> eligibleStoryIds, String sourceFingerprint,
        String strategyVersion, String promptVersion, String correctionRevision) {
        List<Window> planned = planAll(stories, eligibleStoryIds, sourceFingerprint, strategyVersion, promptVersion, correctionRevision);
        if (planned.size() <= MAX_WINDOWS) return planned;
        // Keep the execution bound explicit. Callers that need to disclose the
        // omitted tail can use planAll and compare the two counts.
        return List.copyOf(planned.subList(0, MAX_WINDOWS));
    }

    /** Returns the complete bounded-by-input plan before the execution cap. */
    public List<Window> planAll(List<ChangeStory> stories, Set<String> eligibleStoryIds, String sourceFingerprint,
        String strategyVersion, String promptVersion, String correctionRevision) {
        List<Window> result = new ArrayList<>();
        Set<String> storyIds = new LinkedHashSet<>();
        int eventCount = 0;
        int windowIndex = 0;
        for (ChangeStory story : stories == null ? List.<ChangeStory>of() : stories) {
            if (story == null || eligibleStoryIds == null || !eligibleStoryIds.contains(story.id())) continue;
            int nextEvents = eventCount + story.eventRefs().size();
            if (!storyIds.isEmpty() && (storyIds.size() >= DEFAULT_STORY_LIMIT || nextEvents > DEFAULT_EVENT_LIMIT)) {
                result.add(window(windowIndex++, storyIds, eventCount, sourceFingerprint, strategyVersion, promptVersion, correctionRevision));
                storyIds = new LinkedHashSet<>();
                eventCount = 0;
            }
            storyIds.add(story.id());
            eventCount += story.eventRefs().size();
        }
        if (!storyIds.isEmpty()) result.add(window(windowIndex, storyIds, eventCount, sourceFingerprint, strategyVersion, promptVersion, correctionRevision));
        return List.copyOf(result);
    }

    private Window window(int index, Set<String> storyIds, int eventCount, String sourceFingerprint,
        String strategyVersion, String promptVersion, String correctionRevision) {
        String identity = "window-" + index + "-" + hash(String.join("|", storyIds));
        String cacheKey = hash(String.join("|", sourceFingerprint == null ? "" : sourceFingerprint,
            strategyVersion == null ? "" : strategyVersion, promptVersion == null ? "" : promptVersion,
            identity, correctionRevision == null ? "" : correctionRevision));
        return new Window(identity, cacheKey, ordered(storyIds), eventCount, index);
    }

    private static <T> Set<T> ordered(Set<T> values) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(values == null ? Set.of() : values));
    }

    private static String hash(String value) {
        try {
            return HexFormatHolder.HEX.formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))).substring(0, 32);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static final class HexFormatHolder {
        private static final java.util.HexFormat HEX = java.util.HexFormat.of();
    }

    public record Window(String identity, String cacheKey, Set<String> storyIds, int eventCount, int ordinal) {
    }
}
