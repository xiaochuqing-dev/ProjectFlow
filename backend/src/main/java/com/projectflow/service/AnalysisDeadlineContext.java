package com.projectflow.service;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;

/** Overall analysis deadline propagated through one durable-job execution. */
public final class AnalysisDeadlineContext {
    private static final ThreadLocal<Instant> DEADLINE = new ThreadLocal<>();

    private AnalysisDeadlineContext() {
    }

    public static Scope bind(Instant startedAt, long maxDurationMs) {
        Instant previous = DEADLINE.get();
        if (AnalysisTimePolicy.hasOverallDeadline(maxDurationMs)) {
            Instant base = startedAt == null ? Instant.now() : startedAt;
            try {
                DEADLINE.set(base.plusMillis(maxDurationMs));
            } catch (DateTimeException ignored) {
                DEADLINE.set(Instant.MAX);
            }
        } else {
            DEADLINE.remove();
        }
        return () -> {
            if (previous == null) DEADLINE.remove();
            else DEADLINE.set(previous);
        };
    }

    public static void throwIfExpired() {
        Instant deadline = DEADLINE.get();
        if (deadline != null && !Instant.now().isBefore(deadline)) {
            throw new DeadlineExceededException("项目分析已达到显式总体时长上限");
        }
    }

    public static Duration remainingOr(Duration fallback) {
        Instant deadline = DEADLINE.get();
        if (deadline == null) return fallback;
        Duration remaining = Duration.between(Instant.now(), deadline);
        if (remaining.isNegative() || remaining.isZero()) throwIfExpired();
        return remaining.compareTo(fallback) < 0 ? remaining : fallback;
    }

    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }

    public static final class DeadlineExceededException extends RuntimeException {
        public DeadlineExceededException(String message) {
            super(message);
        }
    }
}
