package com.projectflow.eval;

import java.util.List;
import java.util.Locale;

final class ProjectFlowEvalTextRules {
    private static final List<String> NEGATION_MARKERS = List.of(
        " NOT ", " NO ", " WITHOUT ", "UNAVAILABLE", "UNKNOWN", "ABSENT", "MISSING",
        "NO EVIDENCE", "未", "不", "没有", "无法", "不可", "缺少", "无证据"
    );

    private ProjectFlowEvalTextRules() {
    }

    static boolean containsUnnegatedMarker(String text, String marker) {
        String normalizedText = normalized(text);
        String normalizedMarker = normalized(marker);
        int index = normalizedText.indexOf(normalizedMarker);
        if (index < 0 || normalizedMarker.isBlank()) return false;
        int start = Math.max(0, index - 24);
        int end = Math.min(normalizedText.length(), index + normalizedMarker.length() + 32);
        String window = " " + normalizedText.substring(start, end) + " ";
        return NEGATION_MARKERS.stream().noneMatch(window::contains);
    }

    private static String normalized(String value) {
        return value == null ? "" : value.strip().toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
