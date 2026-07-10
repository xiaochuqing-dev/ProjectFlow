package com.projectflow.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.projectflow.service.DevelopmentSegmentationService.ChangeAtom;
import com.projectflow.service.DevelopmentSegmentationService.SegmentDraft;

@Component
public class SegmentEvidenceValidator {
    public Optional<SegmentDraft> validate(SegmentDraft candidate, List<ChangeAtom> atoms) {
        Set<String> requestedIds = new LinkedHashSet<>(candidate.includedAtomIds());
        List<ChangeAtom> included = atoms.stream().filter(atom -> requestedIds.contains(atom.id())).toList();
        if (included.isEmpty()) {
            return Optional.empty();
        }

        LinkedHashSet<String> atomIds = new LinkedHashSet<>();
        LinkedHashSet<String> allowedFiles = new LinkedHashSet<>();
        LinkedHashSet<String> allowedEvidence = new LinkedHashSet<>();
        for (ChangeAtom atom : included) {
            atomIds.add(atom.id());
            allowedFiles.addAll(atom.files());
            allowedEvidence.addAll(atom.evidenceRefs());
        }

        List<String> files = candidate.affectedFiles().stream().filter(allowedFiles::contains).distinct().toList();
        LinkedHashSet<String> evidence = new LinkedHashSet<>(candidate.evidenceRefs().stream().filter(allowedEvidence::contains).toList());
        if (evidence.isEmpty()) {
            // 模型只负责选择 S 编号，真实证据由后端从对应原子变化恢复。
            evidence.addAll(allowedEvidence);
        }
        for (String file : files) {
            String ref = "file:" + file;
            if (allowedEvidence.contains(ref)) {
                evidence.add(ref);
            }
        }
        if (evidence.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new SegmentDraft(
            candidate.title(),
            candidate.plainSummary(),
            List.copyOf(atomIds),
            candidate.mainChanges(),
            candidate.userVisibleValue(),
            List.copyOf(evidence),
            files,
            candidate.confidence()
        ));
    }
}
