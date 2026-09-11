package com.projectflow.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import com.projectflow.service.model.CanonicalModelResponse;

/** Per-Job transport accounting, independent of semantic exception wrapping. No payloads or endpoint values. */
public final class ModelRequestTelemetryContext {
    private static final ThreadLocal<Collector> CURRENT = new ThreadLocal<>();
    private ModelRequestTelemetryContext() {}

    public static ModelCancellationContext.Scope bind(Collector collector) {
        Collector previous = CURRENT.get();
        CURRENT.set(collector);
        return () -> { if (previous == null) CURRENT.remove(); else CURRENT.set(previous); };
    }

    static Collector current() { return CURRENT.get(); }

    public record Attempt(int sequence, String taskType, String protocol, String reasoningEffort,
        String retryReason, String status, long latencyMs, String failureCode, String transportFailureType,
        Integer httpStatus, Integer promptTokens, Integer completionTokens, Integer totalTokens) {}

    public record Snapshot(int requestCount, int completedRequestCount, int failedRequestCount,
        int inFlightRequestCount, String usageAvailability, Integer promptTokens, Integer completionTokens,
        Integer totalTokens, int reportedPromptTokens, int reportedCompletionTokens, int reportedTotalTokens,
        long latencyMs, List<Attempt> attempts) {}

    public static final class Collector {
        private final List<Pending> requests = new ArrayList<>();

        synchronized int begin(ModelTaskType task, String protocol, String effort, String retry) {
            requests.add(new Pending(task.name(), protocol, effort == null ? "" : effort, retry));
            return requests.size() - 1;
        }

        synchronized void complete(int index, CanonicalModelResponse response) {
            if (index < 0) return;
            Pending pending = requests.get(index);
            if (pending.status != null) return;
            pending.status = "RESPONSE_RECEIVED";
            pending.elapsed = elapsed(pending.started);
            if ("ACTUAL".equals(response.usage().source())) {
                pending.prompt = response.usage().inputTokens();
                pending.completion = response.usage().outputTokens();
                pending.total = response.usage().totalTokens();
            }
            if (response.finishReason() != com.projectflow.service.model.NormalizedFinishReason.COMPLETE) {
                pending.failureCode = response.finishReason().name();
            }
        }

        synchronized void fail(int index, Exception failure) {
            if (index < 0) return; // Cancelled while waiting for an executor/connection slot: no request sent.
            Pending pending = requests.get(index);
            if (pending.status != null) return;
            pending.status = "FAILED";
            pending.elapsed = elapsed(pending.started);
            pending.failureCode = ModelFailureClassifier.classifyException(failure);
            pending.transportFailureType = transportFailureType(failure);
            Throwable cause = failure;
            for (int depth = 0; cause != null && depth < 16; depth++, cause = cause.getCause()) {
                if (cause instanceof com.projectflow.service.model.ModelProtocolHttpException http) {
                    pending.httpStatus = http.statusCode(); break;
                }
                if (cause.getCause() == cause) break;
            }
        }

        public synchronized Snapshot snapshot() {
            int completed = 0, failed = 0, known = 0, prompt = 0, completion = 0, total = 0;
            long latency = 0;
            List<Attempt> attempts = new ArrayList<>();
            for (Pending value : requests) {
                if ("RESPONSE_RECEIVED".equals(value.status)) completed++;
                else if (value.status != null) failed++;
                if (value.total != null) { known++; prompt += value.prompt; completion += value.completion; total += value.total; }
                long duration = value.status == null ? elapsed(value.started) : value.elapsed;
                latency += duration;
                attempts.add(new Attempt(attempts.size() + 1, value.taskType, value.protocol, value.effort, value.retry,
                    value.status == null ? "IN_FLIGHT" : value.status, duration, value.failureCode, value.transportFailureType,
                    value.httpStatus, value.prompt, value.completion, value.total));
            }
            boolean exact = known == requests.size();
            String availability = requests.isEmpty() ? "NOT_CALLED" : exact ? "ACTUAL" : known > 0 ? "PARTIAL" : "UNKNOWN";
            return new Snapshot(requests.size(), completed, failed, requests.size() - completed - failed, availability,
                exact ? prompt : null, exact ? completion : null, exact ? total : null, prompt, completion, total,
                latency, List.copyOf(attempts));
        }
    }

    static String transportFailureType(Throwable failure) {
        for (int depth = 0; failure != null && depth < 16; depth++, failure = failure.getCause()) {
            String type = failure.getClass().getSimpleName();
            String message = failure.getMessage() == null ? "" : failure.getMessage().toLowerCase(Locale.ROOT);
            if (type.equals("StreamResetException")) return message.contains("cancel") ? "HTTP2_STREAM_RESET_CANCEL" : "HTTP2_STREAM_RESET";
            if (failure instanceof java.util.concurrent.CancellationException || failure instanceof InterruptedException) return "CLIENT_CANCELLED";
            if (failure instanceof java.net.SocketTimeoutException || failure instanceof java.net.http.HttpTimeoutException
                || message.contains("timeout") || message.contains("timed out")) return "REQUEST_TIMEOUT";
            if (message.contains("stream ended without a terminal")) return "STREAM_INCOMPLETE";
            if (failure instanceof com.projectflow.service.model.ModelProtocolHttpException http)
                return http.streamReadFailure() ? "UPSTREAM_STREAM_READ_ERROR" : "HTTP_ERROR";
        }
        return "IO_FAILURE";
    }

    private static long elapsed(long started) { return Math.max(0, (System.nanoTime() - started) / 1_000_000); }

    private static final class Pending {
        final String taskType, protocol, effort, retry;
        final long started = System.nanoTime();
        String status, failureCode = "", transportFailureType = "";
        long elapsed;
        Integer httpStatus, prompt, completion, total;
        Pending(String taskType, String protocol, String effort, String retry) {
            this.taskType = taskType; this.protocol = protocol; this.effort = effort; this.retry = retry;
        }
    }
}
