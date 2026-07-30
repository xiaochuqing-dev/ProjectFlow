package com.projectflow.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.projectflow.entity.DevelopmentSegment;
import com.projectflow.entity.DevelopmentSegmentStatus;
import com.projectflow.entity.EvidenceConfidence;

final class StrongFactTestSupport {
    private StrongFactTestSupport() {
    }

    static DevelopmentSegment segment(String statement, List<String> evidence, boolean agentResult) {
        DevelopmentSegment segment = new DevelopmentSegment(UUID.randomUUID(), UUID.randomUUID());
        List<String> commits = evidence.stream().filter(value -> value.startsWith("commit:"))
            .map(value -> value.substring("commit:".length())).toList();
        segment.updateContent(
            "事实候选", statement, List.of(statement), "",
            commits, agentResult ? List.of("agent-result-1") : List.of(),
            List.of("src/Main.java"), evidence, EvidenceConfidence.HIGH,
            DevelopmentSegmentStatus.CONFIRMED
        );
        segment.updateAnalysis("MODEL", "test-provider", "", "PASS", "", List.of(), List.of());
        segment.recordOccurrence(Instant.parse("2026-07-29T00:00:00Z"), Instant.parse("2026-07-29T00:00:00Z"));
        return segment;
    }
}
