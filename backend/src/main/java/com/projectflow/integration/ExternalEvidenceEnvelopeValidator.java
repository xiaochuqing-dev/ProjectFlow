package com.projectflow.integration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.projectflow.service.SensitiveContentRedactor;

public final class ExternalEvidenceEnvelopeValidator {
    private static final Set<String> CONFIDENCE = Set.of("HIGH", "MEDIUM", "LOW", "UNKNOWN");
    private static final Set<String> CURRENTNESS = Set.of("CURRENT", "HISTORICAL", "POSSIBLY_STALE", "UNKNOWN");
    private static final Set<String> TEMPORAL_ROLES = Set.of(
        "CURRENT_STATE", "HISTORICAL_EVENT", "PROCESS_EVIDENCE", "PROCESS_METADATA", "UNKNOWN"
    );

    private final SensitiveContentRedactor redactor;

    public ExternalEvidenceEnvelopeValidator(SensitiveContentRedactor redactor) {
        this.redactor = redactor;
    }

    public ValidationResult validateAndNormalize(
        ExternalEvidenceEnvelope candidate,
        Set<String> existingFingerprints
    ) {
        if (candidate == null) return rejected("ENVELOPE_REQUIRED");
        if (blank(candidate.projectBinding())) return rejected("PROJECT_BINDING_REQUIRED");
        if (blank(candidate.sourceSystem()) || blank(candidate.sourceType()) || blank(candidate.sourceRef())) {
            return rejected("SOURCE_IDENTITY_REQUIRED");
        }
        if (candidate.rawPayloadStored()) return rejected("RAW_PAYLOAD_FORBIDDEN");
        String safeRef = safeRelative(candidate.sourceRef());
        if (safeRef.isBlank()) return rejected("UNSAFE_SOURCE_REF");
        String temporalRole = normalized(candidate.temporalRole());
        if (!TEMPORAL_ROLES.contains(temporalRole)) return rejected("INVALID_TEMPORAL_ROLE");
        String currentness = normalized(candidate.currentness());
        if (!CURRENTNESS.contains(currentness)) return rejected("INVALID_CURRENTNESS");
        String confidence = normalized(candidate.confidence());
        if (!CONFIDENCE.contains(confidence)) return rejected("INVALID_CONFIDENCE");

        String summary = bounded(redactor.redact(candidate.normalizedSummary()), 4_000);
        List<String> evidenceRefs = normalizeRefs(candidate.evidenceRefs());
        if (summary.isBlank() || evidenceRefs.isEmpty()) return rejected("SUMMARY_AND_EVIDENCE_REQUIRED");
        String fingerprint = fingerprint(candidate, safeRef, evidenceRefs);
        if (existingFingerprints != null && existingFingerprints.contains(fingerprint)) {
            return new ValidationResult("DUPLICATE", null, fingerprint, List.of("DUPLICATE_SOURCE_REVISION"));
        }
        ExternalEvidenceEnvelope normalized = new ExternalEvidenceEnvelope(
            bounded(candidate.sourceSystem(), 80),
            bounded(candidate.sourceType(), 80),
            safeRef,
            bounded(candidate.projectBinding(), 160),
            summary,
            candidate.occurredAt(),
            candidate.collectedAt() == null ? Instant.now() : candidate.collectedAt(),
            confidence,
            currentness,
            temporalRole,
            evidenceRefs,
            true,
            false,
            bounded(candidate.adapterId(), 100),
            bounded(candidate.adapterVersion(), 80),
            bounded(candidate.sourceRevision(), 160),
            sanitizeMetadata(candidate.processMetadata())
        );
        return new ValidationResult("ACCEPTED", normalized, fingerprint, List.of());
    }

    private static ValidationResult rejected(String reason) {
        return new ValidationResult("REJECTED", null, "", List.of(reason));
    }

    private static List<String> normalizeRefs(List<String> values) {
        if (values == null) return List.of();
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String value : values) {
            if (blank(value)) continue;
            String normalized = value.strip();
            if (normalized.length() <= 160 && !normalized.contains("..") && !isAbsolute(normalized)) {
                result.add(normalized);
            }
            if (result.size() >= 30) break;
        }
        return List.copyOf(result);
    }

    private Map<String, String> sanitizeMetadata(Map<String, String> values) {
        if (values == null || values.isEmpty()) return Map.of();
        java.util.LinkedHashMap<String, String> result = new java.util.LinkedHashMap<>();
        values.entrySet().stream().sorted(Map.Entry.comparingByKey()).limit(20).forEach(entry -> {
            String key = bounded(entry.getKey(), 80);
            String value = bounded(redactor.redact(entry.getValue()), 300);
            if (!key.isBlank() && !value.isBlank()) result.put(key, value);
        });
        return Map.copyOf(result);
    }

    private static String fingerprint(
        ExternalEvidenceEnvelope value,
        String safeRef,
        List<String> refs
    ) {
        String input = String.join("|",
            value.projectBinding().strip(),
            value.sourceSystem().strip(),
            value.sourceType().strip(),
            safeRef,
            value.sourceRevision() == null ? "" : value.sourceRevision().strip(),
            String.join(",", refs)
        );
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 Java 环境不支持 SHA-256", exception);
        }
    }

    private static String safeRelative(String value) {
        if (value == null) return "";
        String normalized = value.strip().replace('\\', '/');
        if (isAbsolute(normalized) || normalized.contains("../") || normalized.equals("..")) return "";
        return bounded(normalized, 300);
    }

    private static boolean isAbsolute(String value) {
        return value.startsWith("/")
            || value.matches("^[A-Za-z]:/.*")
            || value.startsWith("\\\\");
    }

    private static String normalized(String value) {
        return value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String bounded(String value, int max) {
        if (value == null) return "";
        String stripped = value.strip();
        return stripped.length() <= max ? stripped : stripped.substring(0, max);
    }

    public record ValidationResult(
        String status,
        ExternalEvidenceEnvelope envelope,
        String fingerprint,
        List<String> reasons
    ) {
    }
}
