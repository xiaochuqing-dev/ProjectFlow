package com.projectflow.service;

import static com.projectflow.dto.ProjectHistoryDtos.*;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

@Component
public class ProjectHistoryPresentationInvariantValidator {

    public void validateCorrectedHistory(
        Map<String, ChangeStory> stories,
        Map<String, HistoryChapter> chapters,
        Map<String, EvolutionThread> threads
    ) {
        validateRoleGraph(stories.values().stream().filter(story -> !merged(story)).toList());
        Set<String> activeStoryIds = stories.values().stream()
            .filter(story -> !merged(story))
            .map(ChangeStory::id).collect(Collectors.toCollection(LinkedHashSet::new));
        LinkedHashSet<String> chapterMembers = new LinkedHashSet<>();
        for (HistoryChapter chapter : chapters.values()) {
            LinkedHashSet<String> refs = new LinkedHashSet<>(chapter.storyRefs());
            if (refs.size() != chapter.storyRefs().size() || !activeStoryIds.containsAll(refs)
                || refs.stream().anyMatch(ref -> !chapterMembers.add(ref))) {
                throw violation(ViolationKind.CROSS_PROJECT_REFERENCE,
                    "Corrected history chapter membership is invalid");
            }
            LinkedHashSet<UUID> events = refs.stream().map(stories::get)
                .flatMap(story -> story.eventRefs().stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));
            if (chapter.storyCount() != refs.size() || chapter.rawEventCount() != events.size()) {
                throw violation(ViolationKind.CONTRACT, "Corrected history chapter counts are invalid");
            }
        }
        if (!chapterMembers.equals(activeStoryIds)) {
            throw violation(ViolationKind.CONTRACT, "Corrected history chapter coverage is incomplete");
        }
        for (EvolutionThread thread : threads.values()) {
            LinkedHashSet<String> refs = new LinkedHashSet<>(thread.storyRefs());
            if (refs.size() != thread.storyRefs().size() || !activeStoryIds.containsAll(refs)) {
                throw violation(ViolationKind.CROSS_PROJECT_REFERENCE,
                    "Corrected history thread membership is invalid");
            }
            LinkedHashSet<String> evidence = refs.stream().map(stories::get)
                .flatMap(story -> story.evidenceRefs().stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));
            if (thread.evidenceCount() != evidence.size()) {
                throw violation(ViolationKind.CONTRACT,
                    "Corrected history thread evidence count is invalid");
            }
        }
        for (ChangeStory story : stories.values()) {
            if (merged(story)) continue;
            if (story.rawEventCount() != new LinkedHashSet<>(story.eventRefs()).size()
                || story.evidenceCount() != new LinkedHashSet<>(story.evidenceRefs()).size()) {
                throw violation(ViolationKind.CONTRACT, "Corrected history story counts are invalid");
            }
        }
    }

    public void validateRoleGraph(Collection<ChangeStory> values) {
        Map<String, ChangeStory> stories = new LinkedHashMap<>();
        for (ChangeStory story : values == null ? List.<ChangeStory>of() : values) {
            if (story == null || story.id() == null || story.id().isBlank()
                || stories.putIfAbsent(story.id(), story) != null) {
                throw violation(ViolationKind.CONTRACT, "History role graph contains a duplicate story");
            }
            if (!story.primary() && !story.supporting()) {
                throw violation(ViolationKind.UNSUPPORTED_CLAIM,
                    "History role graph contains an invalid role");
            }
        }

        for (ChangeStory story : stories.values()) {
            Set<String> path = new LinkedHashSet<>();
            ChangeStory cursor = story;
            while (cursor != null && !cursor.primaryStoryId().isBlank()) {
                if (!path.add(cursor.id())) {
                    throw violation(ViolationKind.UNSUPPORTED_CLAIM, "History role graph contains a cycle");
                }
                cursor = stories.get(cursor.primaryStoryId());
            }
        }

        Map<String, String> owners = new LinkedHashMap<>();
        for (ChangeStory primary : stories.values()) {
            if (!primary.primary()) continue;
            if (!primary.primaryStoryId().isBlank()) {
                throw violation(ViolationKind.UNSUPPORTED_CLAIM,
                    "Primary history story points to another primary");
            }
            LinkedHashSet<String> refs = new LinkedHashSet<>(primary.supportingChangeRefs());
            if (refs.size() != primary.supportingChangeRefs().size()) {
                throw violation(ViolationKind.CONTRACT,
                    "Primary history story contains duplicate supporting references");
            }
            for (String ref : refs) {
                ChangeStory support = stories.get(ref);
                if (support == null) {
                    throw violation(ViolationKind.CROSS_PROJECT_REFERENCE,
                        "Primary history story references an unknown supporting story");
                }
                if (!support.supporting() || !primary.id().equals(support.primaryStoryId())) {
                    throw violation(ViolationKind.UNSUPPORTED_CLAIM,
                        "Primary and supporting history references are inconsistent");
                }
                String previousOwner = owners.putIfAbsent(ref, primary.id());
                if (previousOwner != null && !previousOwner.equals(primary.id())) {
                    throw violation(ViolationKind.UNSUPPORTED_CLAIM,
                        "Supporting history story has more than one primary");
                }
            }
        }

        for (ChangeStory support : stories.values()) {
            if (!support.supporting()) continue;
            if (!support.supportingChangeRefs().isEmpty()) {
                throw violation(ViolationKind.UNSUPPORTED_CLAIM,
                    "Supporting history story cannot own supporting stories");
            }
            if (support.primaryStoryId().isBlank()) {
                throw violation(ViolationKind.UNSUPPORTED_CLAIM,
                    "Supporting history story is orphaned");
            }
            ChangeStory primary = stories.get(support.primaryStoryId());
            if (primary == null) {
                throw violation(ViolationKind.CROSS_PROJECT_REFERENCE,
                    "Supporting history story references an unknown primary");
            }
            if (!primary.primary() || merged(primary) || !primary.mergedIntoStoryId().isBlank()) {
                throw violation(ViolationKind.UNSUPPORTED_CLAIM,
                    "Supporting history story references an invalid primary");
            }
            if (!primary.supportingChangeRefs().contains(support.id())
                || !primary.id().equals(owners.get(support.id()))) {
                throw violation(ViolationKind.UNSUPPORTED_CLAIM,
                    "Supporting and primary history references are inconsistent");
            }
        }
    }

    private static boolean merged(ChangeStory story) {
        return "MERGED".equalsIgnoreCase(story.displayStatus());
    }

    private static Violation violation(ViolationKind kind, String message) {
        return new Violation(kind, message);
    }

    public enum ViolationKind {
        CROSS_PROJECT_REFERENCE,
        UNSUPPORTED_CLAIM,
        CONTRACT
    }

    public static final class Violation extends IllegalStateException {
        private final ViolationKind kind;

        private Violation(ViolationKind kind, String message) {
            super(message);
            this.kind = kind == null ? ViolationKind.CONTRACT : kind;
        }

        public ViolationKind kind() {
            return kind;
        }
    }
}
