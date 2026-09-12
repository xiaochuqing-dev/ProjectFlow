package com.projectflow.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.entity.*;
import com.projectflow.repository.*;
import com.projectflow.service.model.*;

class ModelRequestTelemetryTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void boundedHttp2ResetRetryRetainsActualCountAndUnknownTotalUsage() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        var gateway = gateway(request -> {
            if (calls.incrementAndGet() == 1) throw reset();
            return response();
        });
        var telemetry = new ModelRequestTelemetryContext.Collector();
        try (var scope = ModelRequestTelemetryContext.bind(telemetry)) {
            gateway.callStructured(provider(), "synthetic private prompt", ModelTaskType.PROVIDER_CONNECTION_TEST);
        }
        var result = telemetry.snapshot();
        assertThat(result.requestCount()).isEqualTo(2);
        assertThat(result.completedRequestCount()).isEqualTo(1);
        assertThat(result.failedRequestCount()).isEqualTo(1);
        assertThat(result.totalTokens()).isNull();
        assertThat(result.reportedTotalTokens()).isEqualTo(18);
        assertThat(result.usageAvailability()).isEqualTo("PARTIAL");
        assertThat(result.attempts().get(0).transportFailureType()).isEqualTo("HTTP2_STREAM_RESET_CANCEL");
        assertThat(result.attempts().get(1).retryReason()).isEqualTo("TRANSPORT_RETRY");
        assertThat(mapper.writeValueAsString(result)).doesNotContain("private prompt", "test-key", "127.0.0.1", "Authorization");
    }

    @Test
    void wrappedUnderstandingFailurePersistsTwoRequestsThroughJobRunner() throws Exception {
        var gateway = gateway(request -> { throw reset(); });
        var job = new ProjectAnalysisJob(UUID.randomUUID(), UUID.randomUUID(), ProjectAnalysisJobType.PROJECT_UNDERSTANDING_REFRESH, null);
        var repository = mock(ProjectAnalysisJobRepository.class);
        when(repository.findById(job.getId())).thenReturn(Optional.of(job));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var understanding = mock(ProjectUnderstandingService.class);
        when(understanding.refresh(any(), any(), any())).thenAnswer(invocation -> {
            try { gateway.callStructured(provider(), "private", ModelTaskType.PROVIDER_CONNECTION_TEST); }
            catch (Exception failure) { throw new ProjectUnderstandingService.UnderstandingModelException("safe wrapper", failure); }
            return null;
        });
        var runner = new ProjectAnalysisJobRunner(repository, mock(ModelUsageRecordRepository.class),
            mock(ProjectAnalysisService.class), mock(ProjectAnalysisRecordService.class), mock(ProjectMemoryService.class),
            mock(WorkSessionScanService.class), mock(ProjectCapabilityService.class), mock(ProjectFactHistoryService.class),
            mock(ProjectTimelineSummaryService.class), mock(ProjectHistoryReconstructionService.class),
            mock(ProjectCapabilityMapService.class), understanding, mapper, mock(org.springframework.context.ApplicationEventPublisher.class));
        runner.execute(job.getId());
        assertThat(job.getStatus()).isEqualTo(ProjectAnalysisJobStatus.FAILED);
        assertThat(job.getRequestCount()).isEqualTo(2);
        var telemetry = mapper.readTree(job.getDiagnosticsJson()).path("transportTelemetry");
        assertThat(telemetry.path("failedRequestCount").asInt()).isEqualTo(2);
        assertThat(telemetry.path("usageAvailability").asText()).isEqualTo("UNKNOWN");
        assertThat(telemetry.path("totalTokens").isNull()).isTrue();
    }

    @Test
    void cancellationDoesNotRetryOrLoseAnAttemptAndEmptyScopeIsNotCalled() throws Exception {
        AtomicBoolean cancel = new AtomicBoolean();
        var gateway = gateway(request -> {
            cancel.set(true);
            try { Thread.sleep(10_000); }
            catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IOException("cancelled", failure); }
            return response();
        });
        var telemetry = new ModelRequestTelemetryContext.Collector();
        assertThat(telemetry.snapshot().usageAvailability()).isEqualTo("NOT_CALLED");
        assertThat(telemetry.snapshot().totalTokens()).isZero();
        try (var scope = ModelRequestTelemetryContext.bind(telemetry); var cancellation = ModelCancellationContext.bind(cancel::get)) {
            assertThatThrownBy(() -> gateway.callStructured(provider(), "private", ModelTaskType.PROVIDER_CONNECTION_TEST))
                .isInstanceOf(java.util.concurrent.CancellationException.class);
        }
        assertThat(telemetry.snapshot().requestCount()).isEqualTo(1);
        assertThat(telemetry.snapshot().failedRequestCount()).isEqualTo(1);
        assertThat(telemetry.snapshot().totalTokens()).isNull();
        assertThat(telemetry.snapshot().attempts().get(0).transportFailureType()).isEqualTo("CLIENT_CANCELLED");
    }

    @Test
    void executorTimeoutInterruptsOnlyTheActiveRequest() {
        AtomicBoolean interrupted = new AtomicBoolean();
        assertThatThrownBy(() -> CancellableModelRequestExecutor.execute(() -> {
            try { Thread.sleep(10_000); } catch (InterruptedException failure) { interrupted.set(true); throw failure; }
            return "unused";
        }, Duration.ofMillis(60))).isInstanceOf(IOException.class);
        assertThat(interrupted).isTrue();
    }

    @Test
    void wrappedFailuresKeepTheirNormalizedMeaning() {
        assertThat(ModelFailureClassifier.classifyException(new IllegalStateException("wrapper", reset()))).isEqualTo("NETWORK_ERROR");
        assertThat(ModelFailureClassifier.classifyException(new IllegalStateException("wrapper", new ModelGatewayService.ModelHttpException(401)))).isEqualTo("PROVIDER_AUTH_FAILED");
        assertThat(ModelFailureClassifier.classifyException(new IllegalStateException("wrapper", new java.net.SocketTimeoutException()))).isEqualTo("REQUEST_TIMEOUT");
    }

    @Test
    void rejectedRequestKeepsHttpStatusAndUnknownUsageWithoutPayloads() {
        var telemetry = new ModelRequestTelemetryContext.Collector();
        int attempt = telemetry.begin(ModelTaskType.PROJECT_UNDERSTANDING_FINAL_SYNTHESIS,
            "OPENAI_RESPONSES", "xhigh", "NONE");
        telemetry.fail(attempt, new com.projectflow.service.model.ModelProtocolHttpException(
            400, "invalid_request_error", "", "", null));
        assertThat(telemetry.snapshot().attempts().get(0).httpStatus()).isEqualTo(400);
        assertThat(telemetry.snapshot().attempts().get(0).failureCode()).isEqualTo("PROVIDER_REQUEST_REJECTED");
        assertThat(telemetry.snapshot().totalTokens()).isNull();
        assertThat(telemetry.snapshot().usageAvailability()).isEqualTo("UNKNOWN");
    }

    @Test
    void explicitUpstreamStreamReadErrorUsesExistingTransportRetryBudget() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        var telemetry = new ModelRequestTelemetryContext.Collector();
        var gateway = gateway(request -> {
            if (calls.incrementAndGet() == 1) throw new ModelProtocolHttpException(
                200, "upstream_error", "stream_read_error", "", null);
            return response();
        });
        try (var scope = ModelRequestTelemetryContext.bind(telemetry)) {
            gateway.callStructured(provider(), "private", ModelTaskType.PROVIDER_CONNECTION_TEST);
        }
        assertThat(calls).hasValue(2);
        assertThat(telemetry.snapshot().attempts().get(0).transportFailureType()).isEqualTo("UPSTREAM_STREAM_READ_ERROR");
        assertThat(telemetry.snapshot().usageAvailability()).isEqualTo("PARTIAL");
        AtomicInteger rejected = new AtomicInteger();
        var nonTransport = gateway(request -> {
            rejected.incrementAndGet();
            throw new ModelProtocolHttpException(200, "other_error", "other_code", "", null);
        });
        assertThatThrownBy(() -> nonTransport.callStructured(provider(), "private", ModelTaskType.PROVIDER_CONNECTION_TEST))
            .isInstanceOf(ModelGatewayService.ModelHttpException.class);
        assertThat(rejected).hasValue(1);
    }

    private IOException reset() {
        return new IOException("safe SDK wrapper", new okhttp3.internal.http2.StreamResetException(okhttp3.internal.http2.ErrorCode.CANCEL));
    }

    private CanonicalModelResponse response() {
        return new CanonicalModelResponse(ModelTaskType.PROVIDER_CONNECTION_TEST.minimalSchema(), "completed",
            NormalizedFinishReason.COMPLETE, new CanonicalModelUsage(11, 7, 18, 3, "ACTUAL"), "", true, 0);
    }

    private AiProvider provider() {
        AiProvider provider = new AiProvider(UUID.randomUUID());
        provider.update("synthetic", "http://127.0.0.1/v1", "test-key", "synthetic", AiProviderType.OPENAI, 0.0, 1024, true, List.of());
        provider.configureProtocol(ModelProtocol.OPENAI_RESPONSES, null, AiProviderAuthMode.BEARER, null, null, Map.of(),
            30, false, false, true, true, true);
        return provider;
    }

    private ModelGatewayService gateway(Transport transport) {
        var adapter = new ModelProtocolAdapter() {
            public ModelProtocol protocol() { return ModelProtocol.OPENAI_RESPONSES; }
            public CanonicalModelResponse execute(CanonicalModelRequest request) throws IOException { return transport.execute(request); }
        };
        return new ModelGatewayService(mapper, new AiProviderUrlGuard(), new ModelOutputAdapter(mapper),
            new ModelCapabilityRegistry(), new ModelRequestPolicy("xhigh"), new ModelProtocolAdapterRegistry(List.of(adapter)), 30);
    }

    private interface Transport { CanonicalModelResponse execute(CanonicalModelRequest request) throws IOException; }
}
