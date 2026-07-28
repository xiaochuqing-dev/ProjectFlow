package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class ProjectUnderstandingPromptBuilderTest {
    private static final String SCOUT_SNAPSHOT_SHA256 =
        "0fdfe61ee230814deb86e7523d6d0ea863711b42ea2fc082139aa2776fcfc9ef";
    private static final String FINAL_SNAPSHOT_SHA256 =
        "d062ccaecfebfc0297e9a48f6f175f74a1b961add7a7997ce7beeb74954d8675";
    private final ProjectUnderstandingPromptBuilder builder = new ProjectUnderstandingPromptBuilder();

    @Test
    void scoutPromptIsVersionedConstitutionalAndSnapshotControlled() throws Exception {
        String prompt = scoutPrompt();

        assertThat(prompt)
            .contains(
                ProjectUnderstandingPromptBuilder.CONTRACT_VERSION,
                ProjectUnderstandingPromptBuilder.SCOUT_PROMPT_VERSION,
                ProjectUnderstandingPromptBuilder.COMPATIBILITY_PROFILE.id(),
                "正确的空值优于没有证据的完整答案",
                "三步方法",
                "Evidence 重要性不由文件类型、文件名",
                "稳定映射选择全部适用的核心 view",
                "多个彼此独立的关键 gap",
                "\"capabilityDecisions\"",
                "\"decision\":\"REQUEST|SKIP\"",
                "informationGap",
                "whyExistingEvidenceIsInsufficient",
                "\"selfCheck\""
            )
            .doesNotContain("\"toolRequests\":[", "\"recommendedToolCalls\":[")
            .doesNotContain("mustFindEvidence", "expectedTools", "forbiddenTools", "expectedViews")
            .doesNotContain("UPDATE_SCOUT_SNAPSHOT", "dummy-secret-value", "C:\\Users\\");
        assertThat(sha256(prompt)).isEqualTo(SCOUT_SNAPSHOT_SHA256);
    }

    @Test
    void finalPromptUsesSameContractAndHasIndependentSnapshot() throws Exception {
        String context = """
            {"stageOneProfile":{"summary":"初始理解"},"toolResults":[
              {"id":"tool:doc","sourceEvidenceId":"source:doc","summary":"补充了回滚边界"}
            ]}
            """;
        String prompt = builder.buildFinalPrompt(new ProjectUnderstandingPromptBuilder.FinalPromptInput(
            context,
            List.of("intake:scan", "source:doc", "tool:doc"),
            List.of("DOCUMENT_OVERVIEW", "CURRENTNESS"),
            List.of("tool:doc")
        ));

        assertThat(prompt)
            .contains(
                ProjectUnderstandingPromptBuilder.CONTRACT_VERSION,
                ProjectUnderstandingPromptBuilder.FINAL_PROMPT_VERSION,
                "Final 不重新自由探索",
                "Stage 1 已选 Section type 是稳定基线",
                "stageTwoChanges",
                "tool:doc"
            )
            .doesNotContain("mustFindEvidence", "expectedTools", "forbiddenTools");
        assertThat(sha256(prompt)).isEqualTo(FINAL_SNAPSHOT_SHA256);
    }

    @Test
    void productionVersionAliasesPointToTheSharedPromptAsset() {
        assertThat(SemanticScoutService.PROMPT_VERSION)
            .isEqualTo(ProjectUnderstandingPromptBuilder.SCOUT_PROMPT_VERSION);
        assertThat(FinalProfileSynthesisService.PROMPT_VERSION)
            .isEqualTo(ProjectUnderstandingPromptBuilder.FINAL_PROMPT_VERSION);
        assertThat(SemanticScoutService.normalizeShapes("fullstack"))
            .containsExactly("FRONTEND", "BACKEND");
        assertThat(SemanticScoutService.normalizeShapes("front-end + back_end"))
            .containsExactly("FRONTEND", "BACKEND");
        assertThat(SemanticScoutService.normalizeShapes("invented universal platform")).isEmpty();
    }

    @Test
    void explicitCapabilityRequestsAreNormalizedWithoutEngineeringSemanticFill() throws Exception {
        JsonNode scout = new ObjectMapper().readTree("""
            {
              "toolRequests":[
                {"capability":"manifest","informationGap":"依赖未知",
                 "expectedEvidenceValue":"确认依赖","targetEvidenceIds":["source:manifest"],
                 "whyExistingEvidenceIsInsufficient":""},
                {"capability":"DOC_READER","informationGap":"正文未知",
                 "expectedEvidenceValue":"确认正文","targetEvidenceIds":["source:doc"],
                 "whyExistingEvidenceIsInsufficient":"只有摘要"}
              ],
              "capabilityDecisions":[
                {"capability":"MANIFEST","decision":"REQUEST","informationGap":"依赖和入口未知",
                 "expectedEvidenceValue":"确认依赖与入口","targetEvidenceIds":["source:manifest"],
                 "whyExistingEvidenceIsInsufficient":"发现阶段只有压缩候选"},
                {"capability":"GIT_HISTORY","decision":"SKIP","skipReason":"当前视图不需要历史"}
              ]
            }
            """);

        List<JsonNode> normalized = SemanticScoutService.normalizedToolRequestNodes(scout);

        assertThat(normalized)
            .extracting(node -> AnalysisToolRegistry.normalizeCapability(
                node.path("capability").asText("")
            ))
            .containsExactly("MANIFEST", "DOC_READER");
        assertThat(normalized.get(0).path("whyExistingEvidenceIsInsufficient").asText())
            .isEqualTo("发现阶段只有压缩候选");
    }

    private String scoutPrompt() throws Exception {
        return builder.buildScoutPrompt(new ProjectUnderstandingPromptBuilder.ScoutPromptInput(
            fixture(),
            List.of("intake:scan", "source:doc"),
            List.of("DOC_READER"),
            List.of("DOCUMENT_OVERVIEW", "CURRENTNESS")
        ));
    }

    private static String fixture() throws Exception {
        try (InputStream input = ProjectUnderstandingPromptBuilderTest.class.getResourceAsStream(
            "/projectflow-prompt/scout-fixture.json"
        )) {
            if (input == null) throw new IllegalStateException("prompt fixture missing");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
