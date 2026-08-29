package com.projectflow.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Fail-closed policy for non-secret Provider headers, including legacy rows. */
public final class AiProviderHeaderPolicy {
    private static final Pattern HEADER_NAME = Pattern.compile("[A-Za-z0-9-]{1,120}");
    private static final Pattern CREDENTIAL_HEADER = Pattern.compile(
        "(?i)(^|[-_])(authorization|authentication|cookie|proxy-authorization|api[-_]?key|access[-_]?token|refresh[-_]?token|bearer|secret|secrets|credential|credentials|password|token|auth|key)([-_]|$)"
    );
    private static final Pattern CREDENTIAL_VALUE = Pattern.compile(
        "(?i)(?:\\bbearer\\s+[A-Za-z0-9._~+/=-]{12,}|(?:^|[^A-Za-z0-9])(?:sk|rk|pk|ark)-[A-Za-z0-9_-]{16,}|eyJ[A-Za-z0-9_-]{8,}\\.eyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}|(?:api[_-]?key|access[_-]?token|refresh[_-]?token|client[_-]?secret|password|authorization|credential|token)\\s*[:=]\\s*\\S+)"
    );
    private static final Pattern CREDENTIAL_NAME = Pattern.compile("[A-Za-z0-9-]{1,120}");
    private static final Pattern CREDENTIAL_QUERY_NAME = Pattern.compile("[A-Za-z0-9._~-]{1,120}");
    private static final Set<String> RESERVED_HEADERS = Set.of(
        "authorization", "x-api-key", "api-key", "content-type", "content-length", "host", "connection",
        "proxy-authorization", "proxy-authenticate", "forwarded", "anthropic-version"
    );

    private AiProviderHeaderPolicy() {}

    /** Validates a dedicated credential-bearing HTTP header name. */
    public static String requireCredentialHeaderName(String submitted) {
        if (submitted == null || submitted.isBlank()) return null;
        String normalized = submitted.trim();
        if (!CREDENTIAL_NAME.matcher(normalized).matches()) {
            throw new IllegalArgumentException("AI_PROVIDER_CREDENTIAL_HEADER_INVALID");
        }
        return normalized;
    }

    /** Validates a dedicated credential-bearing query parameter name. */
    public static String requireCredentialQueryName(String submitted) {
        if (submitted == null || submitted.isBlank()) return null;
        String normalized = submitted.trim();
        if (!CREDENTIAL_QUERY_NAME.matcher(normalized).matches()) {
            throw new IllegalArgumentException("AI_PROVIDER_CREDENTIAL_QUERY_INVALID");
        }
        return normalized;
    }

    public static Map<String, String> requireSafe(Map<String, String> submitted) {
        if (submitted == null || submitted.isEmpty()) return Map.of();
        Map<String, String> result = new LinkedHashMap<>();
        submitted.forEach((name, value) -> {
            String normalized = name == null ? "" : name.trim();
            String lower = normalized.toLowerCase(Locale.ROOT);
            if (!HEADER_NAME.matcher(normalized).matches() || RESERVED_HEADERS.contains(lower)
                || CREDENTIAL_HEADER.matcher(lower).find()
                || lower.startsWith("proxy-") || lower.startsWith("x-forwarded-")
                || value == null || value.contains("\r") || value.contains("\n")
                || CREDENTIAL_VALUE.matcher(value).find()) {
                throw new IllegalArgumentException("AI_PROVIDER_HEADER_BLOCKED");
            }
            result.put(normalized, value);
        });
        return Collections.unmodifiableMap(result);
    }
}
