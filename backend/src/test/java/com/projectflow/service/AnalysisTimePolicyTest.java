package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.projectflow.dto.ProjectUnderstandingDtos.ProjectUnderstandingRefreshRequest;

class AnalysisTimePolicyTest {
    @Test
    void separatesConnectionRequestAndOverallDeadlineSemantics() {
        AnalysisTimePolicy policy = new AnalysisTimePolicy(7, 900);

        var automatic = policy.resolve(null);
        var finite = policy.resolve(new ProjectUnderstandingRefreshRequest(
            "FINITE",
            90L,
            "QUALITY_FIRST"
        ));
        var unlimited = policy.resolve(new ProjectUnderstandingRefreshRequest(
            "UNLIMITED",
            null,
            "QUALITY_FIRST"
        ));

        assertThat(automatic.deadlineMode()).isEqualTo(AnalysisTimePolicy.DeadlineMode.AUTO);
        assertThat(automatic.unlimitedOverallDuration()).isTrue();
        assertThat(automatic.connectionTimeoutSeconds()).isEqualTo(7);
        assertThat(automatic.providerRequestTimeoutSeconds()).isEqualTo(900);
        assertThat(automatic.maxTransportRetries()).isEqualTo(1);
        assertThat(finite.maxAnalysisDurationMs()).isEqualTo(90_000L);
        assertThat(finite.unlimitedOverallDuration()).isFalse();
        assertThat(unlimited.maxAnalysisDurationMs()).isEqualTo(AnalysisTimePolicy.NO_OVERALL_DEADLINE_MS);
        assertThat(unlimited.qualityMode()).isEqualTo(AnalysisTimePolicy.QualityMode.QUALITY_FIRST);
    }

    @Test
    void finiteModeHonorsAnExplicitShortDiagnosticDeadline() {
        AnalysisTimePolicy policy = new AnalysisTimePolicy(10, 240);

        var finite = policy.resolve(new ProjectUnderstandingRefreshRequest(
            "FINITE",
            5L,
            "QUALITY_FIRST"
        ));

        assertThat(finite.maxAnalysisDurationMs()).isEqualTo(5_000L);
    }

    @Test
    void representationallyHugeFiniteDeadlineIsClampedWithoutOverflow() {
        AnalysisTimePolicy policy = new AnalysisTimePolicy(10, 240);

        var finite = policy.resolve(new ProjectUnderstandingRefreshRequest(
            "FINITE",
            Long.MAX_VALUE,
            "QUALITY_FIRST"
        ));

        assertThat(finite.maxAnalysisDurationMs()).isPositive();
        assertThat(finite.maxAnalysisDurationMs()).isLessThan(Long.MAX_VALUE);
        assertThat(finite.unlimitedOverallDuration()).isFalse();
    }

    @Test
    void unlimitedOverallStillKeepsFiniteRequestTimeout() throws Exception {
        try (AnalysisDeadlineContext.Scope ignored = AnalysisDeadlineContext.bind(
            Instant.now(),
            AnalysisTimePolicy.NO_OVERALL_DEADLINE_MS
        )) {
            String result = CancellableModelRequestExecutor.execute(
                () -> {
                    Thread.sleep(80);
                    return "completed";
                },
                Duration.ofSeconds(2)
            );

            assertThat(result).isEqualTo("completed");
        }
    }

    @Test
    void providerRequestTimeoutRemainsBoundedWithoutOverallDeadline() {
        assertThatThrownBy(() -> {
            try (AnalysisDeadlineContext.Scope ignored = AnalysisDeadlineContext.bind(
                Instant.now(),
                AnalysisTimePolicy.NO_OVERALL_DEADLINE_MS
            )) {
                CancellableModelRequestExecutor.execute(
                    () -> {
                        Thread.sleep(5_000);
                        return "late";
                    },
                    Duration.ofMillis(100)
                );
            }
        }).isInstanceOf(IOException.class)
            .hasMessageContaining("configured timeout");
    }

    @Test
    void cancellationAndHeartbeatStayActiveDuringLongProviderCall() throws Exception {
        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicInteger heartbeats = new AtomicInteger();
        var scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.schedule(() -> cancelled.set(true), 80, TimeUnit.MILLISECONDS);
        long started = System.nanoTime();
        try {
            assertThatThrownBy(() -> {
                try (ModelCancellationContext.Scope ignored = ModelCancellationContext.bind(
                    cancelled::get,
                    heartbeats::incrementAndGet
                )) {
                    CancellableModelRequestExecutor.execute(
                        () -> {
                            Thread.sleep(5_000);
                            return "late";
                        },
                        Duration.ofSeconds(5)
                    );
                }
            }).isInstanceOf(CancellationException.class);
        } finally {
            scheduler.shutdownNow();
        }

        assertThat(heartbeats).hasPositiveValue();
        assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)).isLessThan(2_000);
    }

    @Test
    void explicitOverallDeadlineStopsRequestWithoutChangingRequestPolicy() {
        assertThatThrownBy(() -> {
            try (AnalysisDeadlineContext.Scope ignored = AnalysisDeadlineContext.bind(
                Instant.now(),
                75
            )) {
                CancellableModelRequestExecutor.execute(
                    () -> {
                        Thread.sleep(5_000);
                        return "late";
                    },
                    Duration.ofSeconds(5)
                );
            }
        }).isInstanceOf(AnalysisDeadlineContext.DeadlineExceededException.class);
    }
}
