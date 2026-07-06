package com.projectflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.projectflow.entity.EvidenceConfidence;
import com.projectflow.service.DevelopmentSegmentationService;
import com.projectflow.service.DevelopmentSegmentationService.ChangeAtom;
import com.projectflow.service.DevelopmentSegmentationService.SegmentDraft;
import com.projectflow.service.SegmentEvidenceValidator;

class DevelopmentSegmentationServiceTest {
    private final DevelopmentSegmentationService service = new DevelopmentSegmentationService();
    private final SegmentEvidenceValidator validator = new SegmentEvidenceValidator();

    @Test
    void smallChangeSetProducesOneToThreeSegments() {
        List<SegmentDraft> segments = service.group(List.of(
            atom("a1", "feat(agent): add protocol", "backend/Protocol.java"),
            atom("a2", "test(agent): cover protocol", "backend/ProtocolTest.java"),
            atom("a3", "docs(agent): explain protocol", "README.md")
        ));

        assertThat(segments).hasSizeBetween(1, 3);
        assertThat(segments.get(0).includedAtomIds()).contains("a1", "a2", "a3");
    }

    @Test
    void mediumChangeSetProducesTwoToFiveSegments() {
        List<ChangeAtom> atoms = new ArrayList<>();
        for (int index = 0; index < 14; index++) {
            String scope = index % 2 == 0 ? "scan" : "sediment";
            atoms.add(atom("m" + index, "feat(" + scope + "): change " + index, scope + "/file-" + index + ".java"));
        }

        assertThat(service.group(atoms)).hasSizeBetween(2, 5);
    }

    @Test
    void largeChangeSetIsChunkedIntoThreeToEightSegments() {
        List<ChangeAtom> atoms = new ArrayList<>();
        for (int index = 0; index < 110; index++) {
            atoms.add(atom("l" + index, "feat(module" + (index % 12) + "): change " + index, "module" + (index % 12) + "/file-" + index + ".java"));
        }

        assertThat(service.group(atoms)).hasSizeBetween(3, 8);
    }

    @Test
    void documentationAndTestsStayWithTheirFeatureTopic() {
        List<SegmentDraft> segments = service.group(List.of(
            atom("f1", "feat(agent): add health check", "backend/AgentHealth.java"),
            atom("f2", "test(agent): verify health check", "backend/AgentHealthTest.java"),
            atom("f3", "docs(agent): document health check", "docs/agent-health.md")
        ));

        assertThat(segments).singleElement().satisfies(segment -> {
            assertThat(segment.includedAtomIds()).containsExactlyInAnyOrder("f1", "f2", "f3");
            assertThat(segment.affectedFiles()).contains("backend/AgentHealth.java", "backend/AgentHealthTest.java", "docs/agent-health.md");
        });
    }

    @Test
    void validatorDropsInventedEvidenceAndRejectsEvidenceFreeCandidates() {
        List<ChangeAtom> atoms = List.of(atom("real", "feat(scan): real change", "backend/Scan.java"));
        SegmentDraft candidate = new SegmentDraft(
            "Scan progress",
            "Summarize scan progress.",
            List.of("real", "invented"),
            List.of("Persist scan cursor"),
            "Users do not lose pending changes.",
            List.of("commit:real", "commit:invented", "file:missing.java"),
            List.of("backend/Scan.java", "missing.java"),
            EvidenceConfidence.HIGH
        );

        SegmentDraft validated = validator.validate(candidate, atoms).orElseThrow();
        assertThat(validated.includedAtomIds()).containsExactly("real");
        assertThat(validated.evidenceRefs()).containsExactly("commit:real", "file:backend/Scan.java");
        assertThat(validated.affectedFiles()).containsExactly("backend/Scan.java");
        assertThat(validator.validate(candidateWithNoRealEvidence(), atoms)).isEmpty();
    }

    private SegmentDraft candidateWithNoRealEvidence() {
        return new SegmentDraft(
            "Invented",
            "No factual basis.",
            List.of("missing"),
            List.of("Unknown change"),
            "Unknown value",
            List.of("commit:missing"),
            List.of("missing.java"),
            EvidenceConfidence.LOW
        );
    }

    private ChangeAtom atom(String id, String title, String file) {
        return new ChangeAtom(
            id,
            title,
            Instant.parse("2026-07-06T08:00:00Z"),
            List.of(file.contains("/") ? file.substring(0, file.indexOf('/')) : file),
            List.of(file),
            List.of("commit:" + id, "file:" + file)
        );
    }
}
