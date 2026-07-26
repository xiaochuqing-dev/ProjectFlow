package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.projectflow.dto.ProjectUnderstandingDtos.AdaptiveAnalysisPlanResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.EvidenceSourceMapResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.ProjectEvidenceSourceResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.ProjectStructureIndexResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.RepositoryIntakeResponse;
import com.projectflow.service.AnalysisCapabilityProvider.CapabilityRequest;
import com.projectflow.service.AnalysisCapabilityProvider.ExecutionBudget;

class BoundedLocalAnalysisCapabilityProviderTest {
    @TempDir
    Path root;

    @Test
    void targetedDocReaderUsesEvidenceIdsAndRedactsDeepContent() throws Exception {
        String token = "gh" + "p_abcdefghijklmnopqrstuvwxyz1234567890AB";
        Files.createDirectories(root.resolve("docs"));
        Files.writeString(root.resolve("docs/odd-name.md"), "# 事故复盘\n决定保留兼容路径\n" + token);
        SensitiveContentRedactor redactor = new SensitiveContentRedactor();
        BoundedLocalAnalysisCapabilityProvider provider = new BoundedLocalAnalysisCapabilityProvider(
            (directory, command, timeout) -> new LocalCommandExecutor.CommandResult(1, "", false),
            redactor
        );
        AdaptiveAnalysisPlanResponse plan = mock(AdaptiveAnalysisPlanResponse.class);
        when(plan.deepReadTargets()).thenReturn(List.of("source:doc", "source:unknown"));

        var result = provider.execute(new CapabilityRequest(
            "DOC_READER",
            root,
            intake(),
            mock(ProjectStructureIndexResponse.class),
            sourceMap("source:doc", "docs/odd-name.md", "UNKNOWN_DOCUMENT"),
            plan,
            Set.of("source:doc", "intake:scan"),
            new ExecutionBudget(8, 12_000, 48_000, 8_000)
        ));

        assertThat(result.status()).isEqualTo("SUCCEEDED");
        assertThat(result.evidence()).hasSize(1);
        assertThat(result.promptEvidence()).hasSize(1);
        assertThat(result.promptEvidence().get(0).boundedSample())
            .contains("决定保留兼容路径", SensitiveContentRedactor.REDACTED)
            .doesNotContain(token);
        assertThat(result.evidence().get(0).evidenceRefs()).containsExactly("source:doc");
    }

    @Test
    void gitHistoryUsesFixedMetadataAndNeverReturnsPatch() {
        LocalCommandExecutor commands = (directory, command, timeout) -> {
            assertThat(command).contains("--max-count=240", "--format=@@COMMIT@@%ad|%h");
            assertThat(command).doesNotContain("-p", "--patch", "--name-only");
            return new LocalCommandExecutor.CommandResult(
                0,
                "@@COMMIT@@2026-07|a1b2c3\n@@COMMIT@@2026-07|b2c3d4\n"
                    + "@@COMMIT@@2026-06|c3d4e5\n2026-05\n",
                false
            );
        };
        BoundedLocalAnalysisCapabilityProvider provider = new BoundedLocalAnalysisCapabilityProvider(
            commands,
            new SensitiveContentRedactor()
        );

        var result = provider.execute(new CapabilityRequest(
            "GIT_HISTORY",
            root,
            intake(),
            mock(ProjectStructureIndexResponse.class),
            sourceMap("git:summary", ".git", "GIT"),
            mock(AdaptiveAnalysisPlanResponse.class),
            Set.of("git:summary", "intake:scan"),
            new ExecutionBudget(500, 8_000, 20_000, 8_000)
        ));

        assertThat(result.status()).isEqualTo("SUCCEEDED");
        assertThat(result.evidence().get(0).summary())
            .contains("最近 3 次提交元数据", "2026-07=2", "2026-06=1")
            .doesNotContain("diff --git");
    }

    private static RepositoryIntakeResponse intake() {
        RepositoryIntakeResponse intake = mock(RepositoryIntakeResponse.class);
        when(intake.sourceRevision()).thenReturn("git:abc");
        return intake;
    }

    private static EvidenceSourceMapResponse sourceMap(String id, String locator, String category) {
        return new EvidenceSourceMapResponse(
            1,
            1,
            1,
            0,
            0,
            Map.of(category, 1L),
            List.of(new ProjectEvidenceSourceResponse(
                id,
                category,
                category + "_CANDIDATE",
                locator,
                "EVIDENCE_CANDIDATE",
                "HIGH",
                "CURRENT",
                "HIGH",
                "SAMPLED_BOUNDED",
                "候选",
                List.of(id)
            )),
            List.of(),
            null
        );
    }
}
