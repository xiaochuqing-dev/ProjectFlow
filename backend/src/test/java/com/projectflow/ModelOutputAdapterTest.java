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
}
