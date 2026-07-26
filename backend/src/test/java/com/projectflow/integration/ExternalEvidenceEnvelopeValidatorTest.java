package com.projectflow.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.projectflow.service.SensitiveContentRedactor;

class ExternalEvidenceEnvelopeValidatorTest {
    private final ExternalEvidenceEnvelopeValidator validator =
        new ExternalEvidenceEnvelopeValidator(new SensitiveContentRedactor());

    @Test
    void acceptsBoundedEnvelopeAndRedactsSecrets() {
        var result = validator.validateAndNormalize(
            envelope("project-1", "agent/run-1/result.json", false, "api_key=secret-value-123456"),
            Set.of()
        );

        assertThat(result.status()).isEqualTo("ACCEPTED");
        assertThat(result.envelope().normalizedSummary()).contains(SensitiveContentRedactor.REDACTED);
        assertThat(result.envelope().sensitiveRedacted()).isTrue();
        assertThat(result.envelope().rawPayloadStored()).isFalse();
        assertThat(result.envelope().temporalRole()).isEqualTo("PROCESS_EVIDENCE");
    }

    @Test
    void rejectsMissingBindingUnsafePathAndRawPayload() {
        assertThat(validator.validateAndNormalize(
            envelope("", "agent/result.json", false, "摘要"),
            Set.of()
        ).reasons()).containsExactly("PROJECT_BINDING_REQUIRED");
        assertThat(validator.validateAndNormalize(
            envelope("project-1", "../../outside.json", false, "摘要"),
            Set.of()
        ).reasons()).containsExactly("UNSAFE_SOURCE_REF");
        assertThat(validator.validateAndNormalize(
            envelope("project-1", "agent/result.json", true, "摘要"),
            Set.of()
        ).reasons()).containsExactly("RAW_PAYLOAD_FORBIDDEN");
    }

    @Test
    void duplicateSourceRevisionIsIdempotent() {
        var first = validator.validateAndNormalize(
            envelope("project-1", "agent/result.json", false, "摘要"),
            Set.of()
        );
        var duplicate = validator.validateAndNormalize(
            envelope("project-1", "agent/result.json", false, "另一份摘要"),
            Set.of(first.fingerprint())
        );

        assertThat(first.status()).isEqualTo("ACCEPTED");
        assertThat(duplicate.status()).isEqualTo("DUPLICATE");
        assertThat(duplicate.envelope()).isNull();
    }

    private static ExternalEvidenceEnvelope envelope(
        String projectBinding,
        String sourceRef,
        boolean rawPayloadStored,
        String summary
    ) {
        return new ExternalEvidenceEnvelope(
            "CODEX",
            "AGENT_RESULT",
            sourceRef,
            projectBinding,
            summary,
            Instant.parse("2026-07-26T00:00:00Z"),
            Instant.parse("2026-07-26T00:01:00Z"),
            "MEDIUM",
            "CURRENT",
            "PROCESS_EVIDENCE",
            List.of("agent:run-1"),
            false,
            rawPayloadStored,
            "agent-result-contract",
            "v1",
            "revision-1",
            Map.of("tokenCount", "1234")
        );
    }
}
