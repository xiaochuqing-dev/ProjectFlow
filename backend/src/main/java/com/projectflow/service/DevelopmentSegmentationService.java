package com.projectflow.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.projectflow.entity.EvidenceConfidence;

@Service
public class DevelopmentSegmentationService {
    private static final Pattern CONVENTIONAL_SCOPE = Pattern.compile("^[a-zA-Z]+\\(([^)]+)\\):");

    public List<SegmentDraft> group(List<ChangeAtom> atoms) {
        if (atoms == null || atoms.isEmpty()) {
            return List.of();
        }
        if (atoms.size() <= 3 || distinctFiles(atoms) <= 10) {
            return topicGroups(atoms, 1, 3);
        }
        if (atoms.size() <= 30 && distinctFiles(atoms) <= 80) {
            return topicGroups(atoms, 2, 5);
        }
        int target = clamp((int) Math.ceil(atoms.size() / 25.0), 3, 8);
        return partition(atoms, target);
    }

    private List<SegmentDraft> topicGroups(List<ChangeAtom> atoms, int minimum, int maximum) {
        Map<String, List<ChangeAtom>> grouped = new LinkedHashMap<>();
        for (ChangeAtom atom : atoms) {
            grouped.computeIfAbsent(topicKey(atom), ignored -> new ArrayList<>()).add(atom);
        }
        if (grouped.size() >= minimum && grouped.size() <= maximum) {
            return grouped.entrySet().stream().map(entry -> draft(entry.getKey(), entry.getValue())).toList();
        }
        int target = clamp(grouped.size(), minimum, maximum);
        return partition(atoms, target);
    }

    private List<SegmentDraft> partition(List<ChangeAtom> atoms, int target) {
        List<List<ChangeAtom>> buckets = new ArrayList<>();
        for (int index = 0; index < target; index++) {
            buckets.add(new ArrayList<>());
        }
        for (int index = 0; index < atoms.size(); index++) {
            buckets.get(Math.min(target - 1, index * target / atoms.size())).add(atoms.get(index));
        }
        List<SegmentDraft> result = new ArrayList<>();
        for (List<ChangeAtom> bucket : buckets) {
            if (!bucket.isEmpty()) {
                result.add(draft(topicKey(bucket.get(0)), bucket));
            }
        }
        return result;
    }

    private SegmentDraft draft(String topic, List<ChangeAtom> atoms) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        LinkedHashSet<String> files = new LinkedHashSet<>();
        LinkedHashSet<String> evidence = new LinkedHashSet<>();
        List<String> changes = new ArrayList<>();
        for (ChangeAtom atom : atoms) {
            ids.add(atom.id());
            files.addAll(atom.files());
            evidence.addAll(atom.evidenceRefs());
            if (changes.size() < 5) {
                changes.add(cleanTitle(atom.title()));
            }
        }
        String displayTopic = topic.isBlank() ? "项目" : topic;
        return new SegmentDraft(
            displayTopic + " 开发推进",
            "这一组变化围绕 " + displayTopic + " 展开，共包含 " + atoms.size() + " 条原子变化。",
            List.copyOf(ids),
            changes,
            "相关能力、修复与证据已归并，可继续确认是否形成项目沉淀。",
            List.copyOf(evidence),
            List.copyOf(files),
            atoms.size() <= 3 ? EvidenceConfidence.HIGH : EvidenceConfidence.MEDIUM
        );
    }

    private String topicKey(ChangeAtom atom) {
        Matcher matcher = CONVENTIONAL_SCOPE.matcher(atom.title() == null ? "" : atom.title());
        if (matcher.find()) {
            return matcher.group(1).trim().toLowerCase(Locale.ROOT);
        }
        return atom.modules().stream().filter(value -> value != null && !value.isBlank()).findFirst().orElse("项目");
    }

    private String cleanTitle(String title) {
        if (title == null || title.isBlank()) {
            return "未命名变化";
        }
        return title.replaceFirst("^[a-zA-Z]+(?:\\([^)]+\\))?:\\s*", "").trim();
    }

    private int distinctFiles(List<ChangeAtom> atoms) {
        return (int) atoms.stream().flatMap(atom -> atom.files().stream()).distinct().count();
    }

    private int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public record ChangeAtom(
        String id,
        String title,
        Instant occurredAt,
        List<String> modules,
        List<String> files,
        List<String> evidenceRefs
    ) {
        public ChangeAtom {
            modules = modules == null ? List.of() : List.copyOf(modules);
            files = files == null ? List.of() : List.copyOf(files);
            evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
        }
    }

    public record SegmentDraft(
        String title,
        String plainSummary,
        List<String> includedAtomIds,
        List<String> mainChanges,
        String userVisibleValue,
        List<String> evidenceRefs,
        List<String> affectedFiles,
        EvidenceConfidence confidence
    ) {
    }
}
