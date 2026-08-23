package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ProjectContinuityDeltaTest {
    @Test
    void noOpRevisionIsStableAndContainsNoMutation() {
        ProjectContinuityDelta first = ProjectContinuityDelta.create(
            "UNCHANGED", "source-a", "source-a", "revision-a", "revision-a", null, List.of()
        );
        ProjectContinuityDelta second = ProjectContinuityDelta.create(
            "UNCHANGED", "source-a", "source-a", "revision-a", "revision-a", null, List.of()
        );
        assertThat(first.noOp()).isTrue();
        assertThat(first.revision()).isEqualTo(second.revision()).startsWith("continuity:");
        assertThat(first.diagnostics()).containsEntry("continuityDeltaSize", 0)
            .containsEntry("continuityNoOp", true);
    }

    @Test
    void deltaIsBoundedUsesSafePathsAndHashesDocumentAndAgentIdentities() {
        UUID document = UUID.randomUUID();
        UUID agent = UUID.randomUUID();
        ProjectContinuityDelta delta = ProjectContinuityDelta.create(
            "APPEND_ONLY", "source-a", "source-b", "revision-a", "revision-b",
            Instant.parse("2026-08-24T00:00:00Z"),
            List.of(
                new ProjectContinuityDelta.Mutation(
                    document, "private-document-stable-key", "ADDED", "DOCUMENT",
                    List.of("docs/report.md", "C:/Users/private/report.md", "../escape.md"), List.of()
                ),
                new ProjectContinuityDelta.Mutation(
                    agent, "private-agent-result-key", "UPDATED", "AGENT_RESULT",
                    List.of(".projectflow/agent-results/result.json"), List.of("agent-result:private-ref")
                )
            )
        );
        assertThat(delta.noOp()).isFalse();
        assertThat(delta.addedEventIds()).containsExactly(document);
        assertThat(delta.updatedEventIds()).containsExactly(agent);
        assertThat(delta.changedPaths()).contains("docs/report.md", ".projectflow/agent-results/result.json")
            .noneMatch(value -> value.contains("Users") || value.contains(".."));
        assertThat(delta.changedDocumentIdentities()).singleElement().asString()
            .startsWith("document:").doesNotContain("private-document");
        assertThat(delta.agentResultRefs()).allMatch(value -> value.startsWith("agent-result:"))
            .noneMatch(value -> value.contains("private"));
    }
}
