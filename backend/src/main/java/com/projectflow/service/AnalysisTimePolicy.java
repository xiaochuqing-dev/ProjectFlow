package com.projectflow.service;

import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.projectflow.dto.ProjectUnderstandingDtos.ProjectUnderstandingRefreshRequest;

/**
 * Long-running analysis time contract.
 *
 * Connection and Provider request timeouts stay finite even when the overall
 * analysis deadline is unlimited. Network retries remain owned and bounded by
 * ModelGatewayService.
 */
@Component
public class AnalysisTimePolicy {
    public static final long NO_OVERALL_DEADLINE_MS = Long.MAX_VALUE;
    public static final int MAX_TRANSPORT_RETRIES = 1;

    private final int connectionTimeoutSeconds;
    private final int defaultProviderRequestTimeoutSeconds;

    public AnalysisTimePolicy(
        @Value("${projectflow.model.connection-timeout-seconds:10}") int connectionTimeoutSeconds,
        @Value("${projectflow.model.request-timeout-seconds:240}") int defaultProviderRequestTimeoutSeconds
    ) {
        this.connectionTimeoutSeconds = bounded(connectionTimeoutSeconds, 1, 60);
        this.defaultProviderRequestTimeoutSeconds = Math.max(30, defaultProviderRequestTimeoutSeconds);
    }

    public RuntimePolicy resolve(ProjectUnderstandingRefreshRequest request) {
        DeadlineMode mode = deadlineMode(request == null ? null : request.deadlineMode());
        QualityMode quality = qualityMode(request == null ? null : request.qualityMode());
        Long requestedSeconds = request == null ? null : request.maxAnalysisDurationSeconds();
        long durationMs = switch (mode) {
            case AUTO, UNLIMITED -> NO_OVERALL_DEADLINE_MS;
            case FINITE -> Math.min(
                Math.max(1L, requestedSeconds == null ? 600L : requestedSeconds),
                Long.MAX_VALUE / 1_000L
            ) * 1_000L;
        };
        return new RuntimePolicy(
            mode,
            durationMs,
            connectionTimeoutSeconds,
            defaultProviderRequestTimeoutSeconds,
            MAX_TRANSPORT_RETRIES,
            quality
        );
    }

    public RuntimePolicy automatic() {
        return resolve(null);
    }

    public int connectionTimeoutSeconds() {
        return connectionTimeoutSeconds;
    }

    public int defaultProviderRequestTimeoutSeconds() {
        return defaultProviderRequestTimeoutSeconds;
    }

    public static boolean hasOverallDeadline(long maxDurationMs) {
        return maxDurationMs > 0 && maxDurationMs < NO_OVERALL_DEADLINE_MS;
    }

    private static DeadlineMode deadlineMode(String value) {
        if (value == null || value.isBlank()) return DeadlineMode.AUTO;
        try {
            return DeadlineMode.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return DeadlineMode.AUTO;
        }
    }

    private static QualityMode qualityMode(String value) {
        if (value == null || value.isBlank()) return QualityMode.QUALITY_FIRST;
        try {
            return QualityMode.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return QualityMode.QUALITY_FIRST;
        }
    }

    private static int bounded(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public enum DeadlineMode {
        AUTO,
        FINITE,
        UNLIMITED
    }

    public enum QualityMode {
        QUALITY_FIRST
    }

    public record RuntimePolicy(
        DeadlineMode deadlineMode,
        long maxAnalysisDurationMs,
        int connectionTimeoutSeconds,
        int providerRequestTimeoutSeconds,
        int maxTransportRetries,
        QualityMode qualityMode
    ) {
        public boolean unlimitedOverallDuration() {
            return !AnalysisTimePolicy.hasOverallDeadline(maxAnalysisDurationMs);
        }
    }
}
