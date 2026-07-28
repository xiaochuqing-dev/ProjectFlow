package com.projectflow.service;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Keeps a long Provider call observable and cancellable without changing the
 * protocol adapters. The adapter still owns its finite request timeout.
 */
final class CancellableModelRequestExecutor {
    private static final long POLL_MS = 250;
    private static final AtomicInteger THREAD_SEQUENCE = new AtomicInteger();
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "projectflow-model-request-" + THREAD_SEQUENCE.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    });

    private CancellableModelRequestExecutor() {
    }

    static <T> T execute(Callable<T> request, Duration requestTimeout)
        throws IOException, InterruptedException {
        Future<T> future = EXECUTOR.submit(request);
        long startedAt = System.nanoTime();
        long requestBudgetNanos;
        try {
            requestBudgetNanos = requestTimeout.toNanos();
        } catch (ArithmeticException ignored) {
            requestBudgetNanos = Long.MAX_VALUE;
        }
        try {
            while (true) {
                ModelCancellationContext.throwIfCancelled();
                AnalysisDeadlineContext.throwIfExpired();
                ModelCancellationContext.heartbeat();
                long elapsedNanos = System.nanoTime() - startedAt;
                long remainingNanos = requestBudgetNanos - elapsedNanos;
                if (remainingNanos <= 0) {
                    future.cancel(true);
                    throw new IOException("Provider request exceeded its configured timeout");
                }
                try {
                    return future.get(
                        Math.min(POLL_MS, Math.max(1, TimeUnit.NANOSECONDS.toMillis(remainingNanos))),
                        TimeUnit.MILLISECONDS
                    );
                } catch (TimeoutException ignored) {
                    // Poll cancellation, heartbeat and the optional overall deadline.
                } catch (ExecutionException exception) {
                    Throwable cause = exception.getCause();
                    if (cause instanceof IOException io) throw io;
                    if (cause instanceof InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw interrupted;
                    }
                    if (cause instanceof RuntimeException runtime) throw runtime;
                    throw new IOException("Provider request failed", cause);
                }
            }
        } catch (RuntimeException | IOException | InterruptedException exception) {
            future.cancel(true);
            throw exception;
        }
    }
}
