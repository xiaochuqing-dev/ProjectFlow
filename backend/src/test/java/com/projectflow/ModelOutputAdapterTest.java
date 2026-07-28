package com.projectflow;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.service.ModelOutputAdapter;

class ModelOutputAdapterTest {
    private final ModelOutputAdapter adapter = new ModelOutputAdapter(new ObjectMapper());

    @Test
    void parsesStrictObjectAndDirectArray() throws Exception {
        var object = adapter.parse("{\"capabilities\":[{\"name\":\"能力一\"}]}");
        var array = adapter.parse("[{\"name\":\"能力一\"}]");

        assertThat(adapter.items(object.root(), "capabilities")).hasSize(1);
        assertThat(adapter.items(array.root(), "capabilities")).hasSize(1);
        assertThat(object.repaired()).isFalse();
    }

    @Test
    void removesMarkdownAndExplanationText() throws Exception {
        var result = adapter.parse("模型分析如下：\n```json\n{\"cards\":[{\"title\":\"证据归并\"}]}\n```\n请确认");

        assertThat(result.repaired()).isTrue();
        assertThat(adapter.items(result.root(), "capabilities", "cards")).singleElement()
            .satisfies(item -> assertThat(adapter.text(item, "", "name", "title")).isEqualTo("证据归并"));
    }

    @Test
    void repairsTrailingCommaAndUnwrapsNestedAlias() throws Exception {
        var result = adapter.parse("{\"data\":{\"capabilityCards\":[{\"name\":\"模型输出适配\",}],},}");

        assertThat(result.repaired()).isTrue();
        assertThat(adapter.items(result.root(), "capabilities", "capabilityCards", "cards")).hasSize(1);
    }

    @Test
    void acceptsSingleObjectInsteadOfArrayAndFieldAliases() throws Exception {
        var result = adapter.parse("{\"result\":{\"title\":\"能力分析\",\"sources\":\"S1，S3\"}}");
        var item = adapter.items(result.root(), "capabilities", "result").get(0);

        assertThat(adapter.text(item, "", "name", "title")).isEqualTo("能力分析");
        assertThat(adapter.strings(item, "sourceIndexes", "sources")).containsExactly("S1", "S3");
    }

    @Test
    void recoversCompleteItemsFromTruncatedRootArray() throws Exception {
        var result = adapter.parse("{\"capabilities\":[{\"name\":\"能力一\"},{\"name\":\"能力二\"},{\"name\":\"未完成");

        assertThat(result.partial()).isTrue();
        assertThat(result.recoveredItems()).isEqualTo(2);
        assertThat(adapter.items(result.root(), "capabilities")).hasSize(2);
    }

    @Test
    void distinguishesClosedJsonWithExplanationFromTruncatedJson() {
        assertThat(adapter.likelyTruncated("说明 {\"items\":[]} 后续解释")).isFalse();
        assertThat(adapter.likelyTruncated("{\"items\":[{\"name\":\"未完成\"}")).isTrue();
    }

    @Test
    void selectsTargetSchemaFromMultipleJsonCandidates() throws Exception {
        var result = adapter.parse(
            "示例：[1,2,3]\n正式结果：{\"capabilities\":[{\"name\":\"证据归并\",\"summary\":\"按来源整理\"}]}",
            com.projectflow.service.ModelTaskType.PROJECT_CAPABILITY_ANALYSIS
        );

        assertThat(adapter.items(result.root(), "capabilities")).singleElement()
            .satisfies(item -> assertThat(item.path("name").asText()).isEqualTo("证据归并"));
    }

    @Test
    void partialRecoveryIgnoresEarlierUnrelatedArray() throws Exception {
        var result = adapter.parse(
            "示例：[{\"foo\":\"bar\"}]\n正式：{\"segments\":[{\"title\":\"第一段\",\"summary\":\"已完成\"},{\"title\":\"第二段",
            com.projectflow.service.ModelTaskType.DEVELOPMENT_SEGMENT_MERGE
        );

        assertThat(result.partial()).isTrue();
        assertThat(adapter.items(result.root(), "segments")).singleElement()
            .satisfies(item -> assertThat(item.path("title").asText()).isEqualTo("第一段"));
    }

    @Test
    void locatesTargetCollectionInsideUnknownNestedWrapperAndSnakeCaseAliases() throws Exception {
        var result = adapter.parse(
            "{\"analysis\":{\"payload\":{\"development_segments\":[{\"segment_title\":\"真实结果\",\"plain_summary\":\"已归并\",\"source_indexes\":[\"S1\"]}]}}}",
            com.projectflow.service.ModelTaskType.DEVELOPMENT_SEGMENT_MERGE
        );

        assertThat(result.root().isArray()).isTrue();
        assertThat(result.root()).singleElement()
            .satisfies(item -> assertThat(item.path("segment_title").asText()).isEqualTo("真实结果"));
    }

    @Test
    void understandingSchemasRequireCrossProviderGuardrailFieldsBeforeAcceptance() throws Exception {
        var incompleteScout = adapter.parse(
            "{\"semanticScout\":{\"projectShapeHypotheses\":[],\"evidenceSourceAssessments\":[],"
                + "\"applicableDimensions\":[],\"unknowns\":[]},"
                + "\"dynamicProfile\":{\"sections\":[]},\"unknowns\":[]}",
            com.projectflow.service.ModelTaskType.PROJECT_UNDERSTANDING_SNAPSHOT
        );
        var completeScout = adapter.parse(
            com.projectflow.service.ModelTaskType.PROJECT_UNDERSTANDING_SNAPSHOT.minimalSchema(),
            com.projectflow.service.ModelTaskType.PROJECT_UNDERSTANDING_SNAPSHOT
        );
        var incompleteFinal = adapter.parse(
            "{\"dynamicProfile\":{\"sections\":[]},\"unknowns\":[]}",
            com.projectflow.service.ModelTaskType.PROJECT_UNDERSTANDING_FINAL_SYNTHESIS
        );
        var completeFinal = adapter.parse(
            com.projectflow.service.ModelTaskType.PROJECT_UNDERSTANDING_FINAL_SYNTHESIS.minimalSchema(),
            com.projectflow.service.ModelTaskType.PROJECT_UNDERSTANDING_FINAL_SYNTHESIS
        );

        assertThat(com.projectflow.service.ModelTaskType.PROJECT_UNDERSTANDING_SNAPSHOT.schemaMatches(
            incompleteScout.root(),
            adapter
        )).isFalse();
        assertThat(com.projectflow.service.ModelTaskType.PROJECT_UNDERSTANDING_SNAPSHOT.schemaMatches(
            completeScout.root(),
            adapter
        )).isTrue();
        assertThat(com.projectflow.service.ModelTaskType.PROJECT_UNDERSTANDING_FINAL_SYNTHESIS.schemaMatches(
            incompleteFinal.root(),
            adapter
        )).isFalse();
        assertThat(com.projectflow.service.ModelTaskType.PROJECT_UNDERSTANDING_FINAL_SYNTHESIS.schemaMatches(
            completeFinal.root(),
            adapter
        )).isTrue();
    }

    @Test
    void normalizesNonAuthoritativeUnderstandingDiagnosticsWithoutSemanticRetry() throws Exception {
        var parsed = adapter.parse(
            """
                {
                  "semanticScout":{
                    "projectShapeHypotheses":[],
                    "evidenceSourceAssessments":[],
                    "applicableDimensions":[],
                    "toolRequests":[],
                    "unknowns":["history"]
                  },
                  "dynamicProfile":{"summary":"","sections":[]}
                }
                """,
            com.projectflow.service.ModelTaskType.PROJECT_UNDERSTANDING_SNAPSHOT
        );

        assertThat(parsed.root().path("selfCheck").isObject()).isTrue();
        assertThat(parsed.root().path("unknowns")).isEqualTo(
            parsed.root().path("semanticScout").path("unknowns")
        );
        assertThat(com.projectflow.service.ModelTaskType.PROJECT_UNDERSTANDING_SNAPSHOT.schemaMatches(
            parsed.root(),
            adapter
        )).isTrue();
        assertThat(parsed.repaired()).isTrue();
    }

    @Test
    void acceptsExplicitCapabilityDecisionEncodingWithoutSemanticFill() throws Exception {
        var parsed = adapter.parse(
            """
                {
                  "semanticScout":{
                    "projectShapeHypotheses":[],
                    "evidenceSourceAssessments":[],
                    "applicableDimensions":[],
                    "capabilityDecisions":[{
                      "capability":"MANIFEST",
                      "decision":"REQUEST",
                      "informationGap":"依赖未知",
                      "expectedEvidenceValue":"确认依赖",
                      "targetEvidenceIds":["source:manifest"],
                      "whyExistingEvidenceIsInsufficient":"只有压缩候选"
                    }],
                    "unknowns":[]
                  },
                  "dynamicProfile":{"summary":"","sections":[]},
                  "unknowns":[],
                  "selfCheck":{}
                }
                """,
            com.projectflow.service.ModelTaskType.PROJECT_UNDERSTANDING_SNAPSHOT
        );

        assertThat(parsed.root().path("semanticScout").path("toolRequests").isArray()).isTrue();
        assertThat(com.projectflow.service.SemanticScoutService.normalizedToolRequestNodes(
            parsed.root().path("semanticScout")
        )).singleElement().satisfies(request ->
            assertThat(request.path("capability").asText()).isEqualTo("MANIFEST")
        );
        assertThat(com.projectflow.service.ModelTaskType.PROJECT_UNDERSTANDING_SNAPSHOT.schemaMatches(
            parsed.root(),
            adapter
        )).isTrue();
        assertThat(parsed.repaired()).isTrue();
    }

    @Test
    void wrapsFlattenedScoutWithoutInventingProfileSemantics() throws Exception {
        var parsed = adapter.parse(
            """
                {
                  "projectShapeHypotheses":[{
                    "shape":"BACKEND",
                    "confidence":"HIGH",
                    "evidenceRefs":["source:manifest"],
                    "reason":"存在服务入口"
                  }],
                  "evidenceSourceAssessments":[],
                  "applicableDimensions":["SERVICES"],
                  "capabilityDecisions":[],
                  "toolRequests":[],
                  "unknowns":["数据库未知"],
                  "selfCheck":{}
                }
                """,
            com.projectflow.service.ModelTaskType.PROJECT_UNDERSTANDING_SNAPSHOT
        );

        assertThat(parsed.root().path("semanticScout").path("projectShapeHypotheses"))
            .singleElement()
            .satisfies(shape -> assertThat(shape.path("shape").asText()).isEqualTo("BACKEND"));
        assertThat(parsed.root().path("dynamicProfile").path("sections")).isEmpty();
        assertThat(parsed.root().path("unknowns").path(0).asText()).isEqualTo("数据库未知");
        assertThat(com.projectflow.service.ModelTaskType.PROJECT_UNDERSTANDING_SNAPSHOT.schemaMatches(
            parsed.root(),
            adapter
        )).isTrue();
        assertThat(parsed.repaired()).isTrue();
    }

    @Test
    void recoversClosedScoutFieldsFromTruncatedSnapshotWithoutInventingClaims() throws Exception {
        var parsed = adapter.parse(
            """
                {
                  "semanticScout":{
                    "projectShapeHypotheses":[{
                      "shape":"BACKEND","confidence":"HIGH",
                      "evidenceRefs":["source:manifest"],"reason":"存在服务入口"
                    }],
                    "evidenceSourceAssessments":[{
                      "evidenceId":"source:manifest","semanticRole":"规范来源",
                      "importance":"HIGH","currentness":"CURRENT",
                      "shouldDeepRead":false,"shouldSkip":false,"reason":"定义依赖",
                      "informationGap":"入口未知","affectedDimensions":["SERVICES"],
                      "confidence":"HIGH"
                    }],
                    "applicableDimensions":["SERVICES"],
                    "capabilityDecisions":[{
                      "capability":"MANIFEST","decision":"REQUEST",
                      "informationGap":"入口未知","expectedEvidenceValue":"确认入口",
                      "targetEvidenceIds":["source:manifest"],
                      "whyExistingEvidenceIsInsufficient":"只有压缩候选"
                    }],
                    "recommendedToolCalls":["MANIFEST"],
                    "unknowns":["数据库未知"]
                  },
                  "dynamicProfile":{"summary":"未闭合"
                """,
            com.projectflow.service.ModelTaskType.PROJECT_UNDERSTANDING_SNAPSHOT
        );

        assertThat(parsed.partial()).isTrue();
        assertThat(parsed.root().path("semanticScout").path("projectShapeHypotheses"))
            .singleElement()
            .satisfies(shape -> assertThat(shape.path("shape").asText()).isEqualTo("BACKEND"));
        assertThat(parsed.root().path("dynamicProfile").path("sections")).isEmpty();
        assertThat(com.projectflow.service.ModelTaskType.PROJECT_UNDERSTANDING_SNAPSHOT.schemaMatches(
            parsed.root(),
            adapter
        )).isTrue();
    }
}
