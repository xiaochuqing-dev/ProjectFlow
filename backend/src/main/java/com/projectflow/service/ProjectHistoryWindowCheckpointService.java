package com.projectflow.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.projectflow.entity.ProjectHistoryWindowCheckpoint;
import com.projectflow.repository.ProjectHistoryWindowCheckpointRepository;

/** Short, independent transactions around one semantic-window attempt. */
@Service
public final class ProjectHistoryWindowCheckpointService {
    private static final Duration RUNNING_LEASE = Duration.ofMinutes(5);

    private final ProjectHistoryWindowCheckpointRepository repository;
    private final TransactionTemplate requiresNew;

    public ProjectHistoryWindowCheckpointService(
        ProjectHistoryWindowCheckpointRepository repository,
        PlatformTransactionManager transactionManager
    ) {
        this.repository = repository;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public Attempt begin(UUID projectId, String identity, String cacheKey, String sourceFingerprint,
        int storyCount, int eventCount) {
        try {
            return requiresNew.execute(status -> beginLocked(
                projectId, identity, cacheKey, sourceFingerprint, storyCount, eventCount
            ));
        } catch (DataIntegrityViolationException conflict) {
            // Concurrent first insert: the unique cache key identifies the row
            // that won. Re-read it in a fresh transaction and respect its lease.
            return requiresNew.execute(status -> beginLocked(
                projectId, identity, cacheKey, sourceFingerprint, storyCount, eventCount
            ));
        }
    }

    public boolean succeed(Attempt attempt, String resultJson, int requestCount, String diagnosticsJson) {
        return transition(attempt, checkpoint -> {
            checkpoint.storeValidatedResult(resultJson);
            checkpoint.succeed(requestCount, diagnosticsJson);
        });
    }

    public boolean fail(Attempt attempt, String summary, String diagnosticsJson) {
        return transition(attempt, checkpoint -> checkpoint.fail(summary, diagnosticsJson));
    }

    public boolean cancel(Attempt attempt, String summary, String diagnosticsJson) {
        return transition(attempt, checkpoint -> checkpoint.cancel(summary, diagnosticsJson));
    }

    public boolean skipOversize(Attempt attempt, String summary, String diagnosticsJson) {
        return transition(attempt, checkpoint -> checkpoint.skipOversize(summary, diagnosticsJson));
    }

    public StatusSummary summarize(UUID projectId, Collection<String> cacheKeys) {
        LinkedHashSet<String> activeKeys = new LinkedHashSet<>(cacheKeys == null ? java.util.List.of() : cacheKeys);
        activeKeys.removeIf(value -> value == null || value.isBlank());
        if (activeKeys.isEmpty()) return new StatusSummary(Map.of());
        Map<String, Long> counts = requiresNew.execute(status -> {
            Map<String, Long> fresh = new LinkedHashMap<>();
            repository.countStatuses(projectId, activeKeys)
                .forEach(value -> fresh.put(value.getStatus(), value.getCount()));
            return fresh;
        });
        return new StatusSummary(counts);
    }

    private Attempt beginLocked(UUID projectId, String identity, String cacheKey, String sourceFingerprint,
        int storyCount, int eventCount) {
        ProjectHistoryWindowCheckpoint checkpoint = repository
            .findLockedByProjectIdAndCacheKey(projectId, cacheKey).orElse(null);
        Instant now = Instant.now();
        if (checkpoint != null && "RUNNING".equals(checkpoint.getStatus()) && checkpoint.getUpdatedAt() != null
            && checkpoint.getUpdatedAt().isAfter(now.minus(RUNNING_LEASE))) {
            return new Attempt(projectId, checkpoint.getId(), checkpoint.getVersion(), false);
        }
        if (checkpoint == null) {
            checkpoint = new ProjectHistoryWindowCheckpoint(
                projectId, identity, cacheKey, sourceFingerprint, storyCount, eventCount
            );
        } else {
            checkpoint.beginAttempt();
        }
        checkpoint = repository.saveAndFlush(checkpoint);
        return new Attempt(projectId, checkpoint.getId(), checkpoint.getVersion(), true);
    }

    private boolean transition(Attempt attempt, java.util.function.Consumer<ProjectHistoryWindowCheckpoint> action) {
        if (attempt == null || !attempt.claimed()) return false;
        Boolean applied = requiresNew.execute(status -> {
            ProjectHistoryWindowCheckpoint checkpoint = repository
                .findLockedByIdAndProjectId(attempt.checkpointId(), attempt.projectId()).orElse(null);
            if (checkpoint == null || !"RUNNING".equals(checkpoint.getStatus())
                || !java.util.Objects.equals(checkpoint.getVersion(), attempt.version())) {
                return false;
            }
            action.accept(checkpoint);
            repository.saveAndFlush(checkpoint);
            return true;
        });
        return Boolean.TRUE.equals(applied);
    }

    public record Attempt(UUID projectId, UUID checkpointId, Long version, boolean claimed) {
    }

    public record StatusSummary(Map<String, Long> counts) {
        public StatusSummary {
            counts = Map.copyOf(counts == null ? Map.of() : counts);
        }

        public int count(String status) {
            return Math.toIntExact(counts.getOrDefault(status, 0L));
        }
    }
}
