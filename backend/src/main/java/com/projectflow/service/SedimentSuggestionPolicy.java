package com.projectflow.service;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.projectflow.entity.SedimentAction;

@Component
public class SedimentSuggestionPolicy {
    public Suggestion suggest(
        String title,
        String problemSolved,
        List<String> files,
        List<String> evidenceRefs,
        List<ExistingSediment> existing
    ) {
        if (evidenceRefs == null || evidenceRefs.isEmpty()) {
            return new Suggestion(SedimentAction.IGNORE, null, "缺少可验证证据，建议先人工复核。");
        }
        ExistingSediment best = null;
        double bestScore = 0;
        for (ExistingSediment sediment : existing == null ? List.<ExistingSediment>of() : existing) {
            double score = Math.max(similarity(title, sediment.title()), similarity(problemSolved, sediment.problemSolved()));
            if (score > bestScore) {
                bestScore = score;
                best = sediment;
            }
        }
        if (best != null && bestScore >= 0.35) {
            if (isEvidenceOnly(files)) {
                return new Suggestion(SedimentAction.EVIDENCE_ONLY, best.id(), "变化主要补充测试或文档，建议只补充到已有沉淀的证据链。");
            }
            return new Suggestion(SedimentAction.MERGE_EXISTING, best.id(), "与已有沉淀解决的问题相近，建议合并以避免重复卡片。");
        }
        return new Suggestion(SedimentAction.NEW_SEDIMENT, null, "变化形成独立开发结果，建议新建沉淀。");
    }

    private boolean isEvidenceOnly(List<String> files) {
        return files != null && !files.isEmpty() && files.stream().allMatch(file -> {
            String value = file.toLowerCase(Locale.ROOT);
            return value.endsWith(".md") || value.contains("test") || value.contains("spec") || value.contains("docs/");
        });
    }

    private double similarity(String left, String right) {
        Set<String> a = bigrams(normalize(left));
        Set<String> b = bigrams(normalize(right));
        if (a.isEmpty() || b.isEmpty()) return 0;
        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return (double) intersection.size() / union.size();
    }

    private Set<String> bigrams(String value) {
        Set<String> result = new HashSet<>();
        if (value.length() == 1) result.add(value);
        for (int index = 0; index + 1 < value.length(); index++) result.add(value.substring(index, index + 2));
        return result;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    public record ExistingSediment(UUID id, String title, String problemSolved) {
    }

    public record Suggestion(SedimentAction action, UUID targetSedimentId, String reason) {
    }
}
