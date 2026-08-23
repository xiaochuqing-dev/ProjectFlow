package com.projectflow.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bounded, project-owned description of one explicit continuity refresh.
 * It is derived from the existing history-event upsert and never becomes a
 * second event ledger or a factual source.
 */
public record ProjectContinuityDelta(
    String revision,
    boolean noOp,
    String rewriteMode,
    String previousSourceFingerprint,
    String currentSourceFingerprint,
    String previousProjectRevision,
    String currentProjectRevision,
    String previousPresentationRevision,
    String currentPresentationRevision,
    Instant affectedFrom,
    List<UUID> addedEventIds,
    List<UUID> updatedEventIds,
    List<UUID> staleEventIds,
    List<UUID> invalidatedEventIds,
    List<String> changedPaths,
    List<String> changedDocumentIdentities,
    List<String> agentResultRefs,
    boolean truncated
) {
    private static final int MAX_IDS = 500;
    private static final int MAX_PATHS = 200;
    private static final int MAX_IDENTITIES = 100;

    public ProjectContinuityDelta {
        revision = safe(revision);
        rewriteMode = safe(rewriteMode);
        previousSourceFingerprint = safe(previousSourceFingerprint);
        currentSourceFingerprint = safe(currentSourceFingerprint);
        previousProjectRevision = safe(previousProjectRevision);
        currentProjectRevision = safe(currentProjectRevision);
        previousPresentationRevision = safe(previousPresentationRevision);
        currentPresentationRevision = safe(currentPresentationRevision);
        addedEventIds = immutable(addedEventIds);
        updatedEventIds = immutable(updatedEventIds);
        staleEventIds = immutable(staleEventIds);
        invalidatedEventIds = immutable(invalidatedEventIds);
        changedPaths = immutable(changedPaths);
        changedDocumentIdentities = immutable(changedDocumentIdentities);
        agentResultRefs = immutable(agentResultRefs);
    }

    public static ProjectContinuityDelta create(
        String rewriteMode,
        String previousSourceFingerprint,
        String currentSourceFingerprint,
        String previousProjectRevision,
        String currentProjectRevision,
        Instant affectedFrom,
        Collection<Mutation> mutations
    ) {
        return create(
            rewriteMode, previousSourceFingerprint, currentSourceFingerprint,
            previousProjectRevision, currentProjectRevision, "", "", affectedFrom, mutations
        );
    }

    public static ProjectContinuityDelta create(
        String rewriteMode,
        String previousSourceFingerprint,
        String currentSourceFingerprint,
        String previousProjectRevision,
        String currentProjectRevision,
        String previousPresentationRevision,
        String currentPresentationRevision,
        Instant affectedFrom,
        Collection<Mutation> mutations
    ) {
        List<Mutation> ordered = (mutations == null ? List.<Mutation>of() : mutations.stream()
            .sorted(java.util.Comparator.comparing(Mutation::kind)
                .thenComparing(value -> value.eventId().toString()))
            .toList());
        List<UUID> added = ids(ordered, "ADDED");
        List<UUID> updated = ids(ordered, "UPDATED");
        List<UUID> stale = ids(ordered, "STALE");
        List<UUID> invalidated = ids(ordered, "INVALIDATED");
        LinkedHashSet<String> paths = new LinkedHashSet<>();
        LinkedHashSet<String> documents = new LinkedHashSet<>();
        LinkedHashSet<String> agentResults = new LinkedHashSet<>();
        for (Mutation mutation : ordered) {
            mutation.changedPaths().stream().filter(ProjectContinuityDelta::safeRelative)
                .map(ProjectContinuityDelta::boundedPath).forEach(paths::add);
            if ("DOCUMENT".equals(mutation.sourceType())) {
                documents.add("document:" + shortHash(mutation.stableEventKey()));
            }
            if ("AGENT_RESULT".equals(mutation.sourceType())) {
                agentResults.add("agent-result:" + shortHash(mutation.stableEventKey()));
            }
            mutation.relationRefs().stream().filter(value -> value != null && value.startsWith("agent-result:"))
                .map(value -> "agent-result:" + shortHash(value)).forEach(agentResults::add);
        }
        boolean truncated = added.size() > MAX_IDS || updated.size() > MAX_IDS || stale.size() > MAX_IDS
            || invalidated.size() > MAX_IDS || paths.size() > MAX_PATHS || documents.size() > MAX_IDENTITIES
            || agentResults.size() > MAX_IDENTITIES;
        StringBuilder identity = new StringBuilder();
        boolean presentationChanged = !safe(previousPresentationRevision).equals(safe(currentPresentationRevision));
        token(identity, ordered.isEmpty() && !presentationChanged ? "NO_OP" : "DELTA");
        token(identity, currentSourceFingerprint);
        token(identity, currentProjectRevision);
        token(identity, previousPresentationRevision);
        token(identity, currentPresentationRevision);
        for (Mutation mutation : ordered) {
            token(identity, mutation.kind());
            token(identity, mutation.eventId().toString());
            token(identity, mutation.stableEventKey());
        }
        return new ProjectContinuityDelta(
            "continuity:" + sha256(identity.toString()), ordered.isEmpty() && !presentationChanged, rewriteMode,
            previousSourceFingerprint, currentSourceFingerprint, previousProjectRevision, currentProjectRevision,
            previousPresentationRevision, currentPresentationRevision, affectedFrom,
            limit(added, MAX_IDS), limit(updated, MAX_IDS), limit(stale, MAX_IDS),
            limit(invalidated, MAX_IDS), limit(paths, MAX_PATHS), limit(documents, MAX_IDENTITIES),
            limit(agentResults, MAX_IDENTITIES), truncated
        );
    }

    public Map<String, Object> diagnostics() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("continuityDeltaRevision", revision);
        result.put("continuityNoOp", noOp);
        result.put("continuityRewriteMode", rewriteMode);
        result.put("continuityPreviousSourceFingerprint", previousSourceFingerprint);
        result.put("continuityCurrentSourceFingerprint", currentSourceFingerprint);
        result.put("continuityPreviousProjectRevision", previousProjectRevision);
        result.put("continuityCurrentProjectRevision", currentProjectRevision);
        result.put("continuityPreviousPresentationRevision", previousPresentationRevision);
        result.put("continuityCurrentPresentationRevision", currentPresentationRevision);
        result.put("continuityPresentationChanged", !previousPresentationRevision.equals(currentPresentationRevision));
        result.put("continuityAffectedFrom", affectedFrom == null ? "" : affectedFrom.toString());
        result.put("continuityAddedEventIds", addedEventIds.stream().map(UUID::toString).toList());
        result.put("continuityUpdatedEventIds", updatedEventIds.stream().map(UUID::toString).toList());
        result.put("continuityStaleEventIds", staleEventIds.stream().map(UUID::toString).toList());
        result.put("continuityInvalidatedEventIds", invalidatedEventIds.stream().map(UUID::toString).toList());
        result.put("continuityChangedPaths", changedPaths);
        result.put("continuityChangedDocumentIdentities", changedDocumentIdentities);
        result.put("continuityAgentResultRefs", agentResultRefs);
        result.put("continuityDeltaSize", addedEventIds.size() + updatedEventIds.size()
            + staleEventIds.size() + invalidatedEventIds.size());
        result.put("continuityDeltaTruncated", truncated);
        return result;
    }

    private static List<UUID> ids(List<Mutation> mutations, String kind) {
        return mutations.stream().filter(value -> kind.equals(value.kind())).map(Mutation::eventId).distinct().toList();
    }

    private static boolean safeRelative(String value) {
        if (value == null || value.isBlank()) return false;
        String normalized = value.trim().replace('\\', '/');
        return !normalized.startsWith("/") && !normalized.matches("^[A-Za-z]:/.*")
            && java.util.Arrays.stream(normalized.split("/")).noneMatch(".."::equals);
    }

    private static String boundedPath(String value) {
        String safe = value.trim().replace('\\', '/');
        return safe.length() <= 300 ? safe : safe.substring(0, 300);
    }

    private static String shortHash(String value) {
        return sha256(safe(value)).substring(0, 20);
    }

    private static void token(StringBuilder target, String value) {
        String safe = safe(value);
        target.append(safe.length()).append(':').append(safe).append('|');
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(safe(value).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static String safe(String value) { return value == null ? "" : value.trim(); }

    private static <T> List<T> immutable(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private static <T> List<T> limit(Collection<T> values, int maximum) {
        if (values == null || values.isEmpty()) return List.of();
        return new ArrayList<>(values).stream().limit(maximum).toList();
    }

    public record Mutation(
        UUID eventId,
        String stableEventKey,
        String kind,
        String sourceType,
        List<String> changedPaths,
        List<String> relationRefs
    ) {
        public Mutation {
            if (eventId == null) throw new IllegalArgumentException("eventId is required");
            stableEventKey = safe(stableEventKey);
            kind = safe(kind).toUpperCase(java.util.Locale.ROOT);
            sourceType = safe(sourceType).toUpperCase(java.util.Locale.ROOT);
            changedPaths = immutable(changedPaths);
            relationRefs = immutable(relationRefs);
        }
    }
}
