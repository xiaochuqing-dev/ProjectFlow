package com.projectflow.service;

import java.util.Set;

/** Provider-neutral boundary: models word the read model but never own its graph or factual fields. */
public final class ProjectHistoryModelOutputContract {
    public static final Set<String> ROOT_FIELDS = Set.of("stories", "chapters");
    public static final Set<String> STORY_FIELDS = Set.of(
        "storyId", "humanTitle", "oneSentenceSummary", "reason", "reasonEvidenceRefs", "unknowns"
    );
    public static final Set<String> CHAPTER_FIELDS = Set.of("chapterId", "title", "summary");
    public static final Set<String> ENGINEERING_OWNED_FIELDS = Set.of(
        "role", "primaryStoryId", "supportingChangeRefs", "storyRefs", "eventRefs", "evidenceRefs",
        "occurredFrom", "occurredTo", "sourceRevision", "projectFact", "verified", "conflicts",
        "beforeState", "change", "afterState", "laterOutcome"
    );

    private ProjectHistoryModelOutputContract() {
    }
}
