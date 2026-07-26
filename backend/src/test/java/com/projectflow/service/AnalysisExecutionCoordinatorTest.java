package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.projectflow.dto.ProjectUnderstandingDtos.AdaptiveAnalysisPlanResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.AnalysisToolEvidenceResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.EvidenceSourceMapResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.ProjectEvidenceSourceResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.ProjectStructureIndexResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.RepositoryIntakeResponse;
import com.projectflow.service.AnalysisCapabilityProvider.CapabilityResult;
import com.projectflow.service.ProjectEvidenceDiscoveryService.PromptEvidence;

class AnalysisExecutionCoordinatorTest {
    @TempDir
    Path root;

    @Test
    void validatesAndMergesProviderEvidence() {
        AnalysisCapabilityProvider provider = new AnalysisCapabilityProvider() {
            @Override
            public boolean supports(String capability) {
                return "DOC_READER".equals(capability);
            }

            @Override
            public CapabilityResult execute(CapabilityRequest request) {
                return new CapabilityResult(
                    "SUCCEEDED",
                    List.of(
                        new AnalysisToolEvidenceResponse(
                            "tool:doc_reader:valid",
                            "DOC_READER",
                            "TOOL_RESULT",
                            "TARGETED_DEEP_READ",
                            "深读摘要",
                            List.of("source:doc")
                        ),
                        new AnalysisToolEvidenceResponse(
                            "tool:doc_reader:invalid",
                            "DOC_READER",
                            "TOOL_RESULT",
                            "TARGETED_DEEP_READ",
                            "未知来源",
                            List.of("source:unknown")
                        )
                    ),
                    List.of(
                        new PromptEvidence(
                            "tool:doc_reader:valid", "TOOL_RESULT", "TARGETED_DEEP_READ",
                            "docs/note.md", "摘要", "深读正文".repeat(80)
                        )
                    ),
                    1,
                    4,
                    "完成"
                );
            }
        };
        AnalysisExecutionCoordinator coordinator = new AnalysisExecutionCoordinator(
            List.of(provider),
            new SensitiveContentRedactor()
        );

        var result = coordinator.execute(root, intake(), index(), sourceMap(), plan("DOC_READER", "FILESYSTEM"));

        assertThat(result.response().executedCapabilities()).containsExactly("DOC_READER");
        assertThat(result.response().reusedCapabilities()).containsExactly("FILESYSTEM");
        assertThat(result.response().evidence()).extracting(AnalysisToolEvidenceResponse::id)
            .containsExactly("tool:doc_reader:valid");
        assertThat(result.sourceMap().sources()).extracting(ProjectEvidenceSourceResponse::id)
            .contains("tool:doc_reader:valid")
            .doesNotContain("tool:doc_reader:invalid");
        assertThat(result.highValueEvidenceProduced()).isTrue();
        assertThat(result.response().secondStageDecision().triggerReasons())
            .anyMatch(reason -> reason.startsWith("NEW_DEEP_CONTENT"));
    }

    @Test
    void toolFailureFallsBackWithoutDestroyingSourceMap() {
        AnalysisCapabilityProvider provider = new AnalysisCapabilityProvider() {
            @Override
            public boolean supports(String capability) {
                return true;
            }

            @Override
            public CapabilityResult execute(CapabilityRequest request) {
                throw new IllegalStateException("boom");
            }
        };
        AnalysisExecutionCoordinator coordinator = new AnalysisExecutionCoordinator(
            List.of(provider),
            new SensitiveContentRedactor()
        );

        var result = coordinator.execute(root, intake(), index(), sourceMap(), plan("DOC_READER"));

        assertThat(result.response().diagnostics()).anyMatch(item -> "FAILED".equals(item.status()));
        assertThat(result.sourceMap().sources()).hasSize(sourceMap().sources().size());
        assertThat(result.highValueEvidenceProduced()).isFalse();
    }

    @Test
    void cancellationStopsBeforeProviderExecution() {
        AnalysisExecutionCoordinator coordinator = new AnalysisExecutionCoordinator(
            List.of(),
            new SensitiveContentRedactor()
        );

        try (ModelCancellationContext.Scope ignored = ModelCancellationContext.bind(() -> true)) {
            assertThatThrownBy(() -> coordinator.execute(root, intake(), index(), sourceMap(), plan("DOC_READER")))
                .isInstanceOf(CancellationException.class);
        }
    }

    @Test
    void cacheIdentityChangesWithProviderAndDeepReadStrategy() {
        AnalysisCapabilityProvider providerV1 = emptyProvider("provider-v1");
        AnalysisCapabilityProvider providerV2 = emptyProvider("provider-v2");
        AnalysisExecutionCoordinator first = new AnalysisExecutionCoordinator(
            List.of(providerV1),
            new SensitiveContentRedactor()
        );
        AnalysisExecutionCoordinator second = new AnalysisExecutionCoordinator(
            List.of(providerV2),
            new SensitiveContentRedactor()
        );

        String firstKey = first.execute(root, intake(), index(), sourceMap(), plan("DOC_READER"))
            .response().cacheKey();
        String providerChanged = second.execute(root, intake(), index(), sourceMap(), plan("DOC_READER"))
            .response().cacheKey();
        AdaptiveAnalysisPlanResponse changedTarget = plan("DOC_READER");
        when(changedTarget.deepReadTargets()).thenReturn(List.of("source:other"));
        String targetChanged = first.execute(root, intake(), index(), sourceMap(), changedTarget)
            .response().cacheKey();
        AdaptiveAnalysisPlanResponse changedBudget = plan("DOC_READER");
        when(changedBudget.maxModelInputTokens()).thenReturn(8_000);
        String budgetChanged = first.execute(root, intake(), index(), sourceMap(), changedBudget)
            .response().cacheKey();

        assertThat(providerChanged).isNotEqualTo(firstKey);
        assertThat(targetChanged).isNotEqualTo(firstKey);
        assertThat(budgetChanged).isNotEqualTo(firstKey);
    }

    private static AnalysisCapabilityProvider emptyProvider(String version) {
        return new AnalysisCapabilityProvider() {
            @Override
            public boolean supports(String capability) {
                return "DOC_READER".equals(capability);
            }

            @Override
            public String providerVersion() {
                return version;
            }

            @Override
            public CapabilityResult execute(CapabilityRequest request) {
                return new CapabilityResult("SUCCEEDED", List.of(), List.of(), 0, 0, "无新增证据");
            }
        };
    }

    private static RepositoryIntakeResponse intake() {
        RepositoryIntakeResponse intake = mock(RepositoryIntakeResponse.class);
        when(intake.sourceRevision()).thenReturn("revision");
        when(intake.contentHash()).thenReturn("content-hash");
        return intake;
    }

    private static ProjectStructureIndexResponse index() {
        ProjectStructureIndexResponse index = mock(ProjectStructureIndexResponse.class);
        when(index.evidence()).thenReturn(List.of());
        when(index.indexVersion()).thenReturn("index-v1");
        when(index.contentHash()).thenReturn("structure-hash");
        return index;
    }

    private static EvidenceSourceMapResponse sourceMap() {
        return new EvidenceSourceMapResponse(
            1,
            1,
            1,
            0,
            0,
            Map.of("UNKNOWN_DOCUMENT", 1L),
            List.of(new ProjectEvidenceSourceResponse(
                "source:doc",
                "UNKNOWN_DOCUMENT",
                "UNCLASSIFIED_TEXT_CANDIDATE",
                "docs/note.md",
                "SEMANTIC_CANDIDATE",
                "HIGH",
                "UNKNOWN",
                "MEDIUM",
                "SAMPLED_BOUNDED",
                "候选",
                List.of("source:doc")
            )),
            List.of(),
            null
        );
    }

    private static AdaptiveAnalysisPlanResponse plan(String... tools) {
        AdaptiveAnalysisPlanResponse plan = mock(AdaptiveAnalysisPlanResponse.class);
        when(plan.toolsToInvoke()).thenReturn(List.of(tools));
        when(plan.deepReadTargets()).thenReturn(List.of("source:doc"));
        when(plan.maxModelRequests()).thenReturn(2);
        when(plan.semanticBudgets()).thenReturn(Map.of("SCOUT", 1000));
        return plan;
    }
}
