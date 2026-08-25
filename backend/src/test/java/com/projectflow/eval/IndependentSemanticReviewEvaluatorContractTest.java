package com.projectflow.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.projectflow.service.SensitiveContentRedactor;

class IndependentSemanticReviewEvaluatorContractTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void acceptsExactlyOneOrderedBoundedJudgementPerBlindScenario() throws Exception {
        List<String> ids = scenarioIds();
        var evaluator = evaluator();

        var result = evaluator.parseJudgements(validResponse(ids), ids);

        assertThat(result.keySet()).containsExactlyElementsOf(ids);
        assertThat(result.values()).allSatisfy(judgement -> {
            assertThat(judgement.attachmentSemanticallySupported()).isEqualTo("uncertain");
            assertThat(judgement.rationale()).isEqualTo("仅依据给定证据保留不确定性");
        });
    }

    @Test
    void normalizesOnlyBooleanYesNoJudgements() throws Exception {
        List<String> ids = scenarioIds();
        var evaluator = evaluator();
        ObjectNode response = validResponse(ids);
        ObjectNode first = (ObjectNode) response.withArray("reviews").get(0);
        first.put("attachmentSemanticallySupported", true);
        first.put("shouldRemainIndependent", false);
        first.put("oldHistoryUnexpectedlyChanged", true);
        first.put("truthfulnessConcern", false);

        var judgement = evaluator.parseJudgements(response, ids).get(ids.get(0));

        assertThat(judgement.attachmentSemanticallySupported()).isEqualTo("yes");
        assertThat(judgement.shouldRemainIndependent()).isEqualTo("no");
        assertThat(judgement.oldHistoryUnexpectedlyChanged()).isEqualTo("yes");
        assertThat(judgement.truthfulnessConcern()).isEqualTo("no");

        ObjectNode invalidConfidence = validResponse(ids);
        ((ObjectNode) invalidConfidence.withArray("reviews").get(0)).put("confidence", true);
        assertThatThrownBy(() -> evaluator.parseJudgements(invalidConfidence, ids))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("REVIEW_FIELD_INVALID_confidence");

        ObjectNode invalidNumber = validResponse(ids);
        ((ObjectNode) invalidNumber.withArray("reviews").get(0))
            .put("attachmentSemanticallySupported", 1);
        assertThatThrownBy(() -> evaluator.parseJudgements(invalidNumber, ids))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("REVIEW_FIELD_INVALID_attachmentSemanticallySupported");
    }

    @Test
    void normalizesCanonicalTextButRejectsOtherScalarAndContainerShapes() throws Exception {
        List<String> ids = scenarioIds();
        var evaluator = evaluator();
        ObjectNode canonical = validResponse(ids);
        ObjectNode first = (ObjectNode) canonical.withArray("reviews").get(0);
        first.put("attachmentSemanticallySupported", " YES ");
        first.put("shouldRemainIndependent", " No ");
        first.put("oldHistoryUnexpectedlyChanged", " NO ");
        first.put("truthfulnessConcern", " yes ");
        first.put("confidence", " HIGH ");

        var judgement = evaluator.parseJudgements(canonical, ids).get(ids.get(0));
        assertThat(judgement.attachmentSemanticallySupported()).isEqualTo("yes");
        assertThat(judgement.shouldRemainIndependent()).isEqualTo("no");
        assertThat(judgement.truthfulnessConcern()).isEqualTo("yes");
        assertThat(judgement.confidence()).isEqualTo("high");

        assertInvalidAttachment(evaluator, ids, node -> node.putNull("attachmentSemanticallySupported"));
        assertInvalidAttachment(evaluator, ids, node -> node.put("attachmentSemanticallySupported", 1));
        assertInvalidAttachment(evaluator, ids, node -> node.putArray("attachmentSemanticallySupported"));
        assertInvalidAttachment(evaluator, ids, node -> node.putObject("attachmentSemanticallySupported"));
        assertInvalidAttachment(evaluator, ids,
            node -> node.put("attachmentSemanticallySupported", "supported"));

        ObjectNode binaryUncertain = validResponse(ids);
        ((ObjectNode) binaryUncertain.withArray("reviews").get(0))
            .put("oldHistoryUnexpectedlyChanged", "uncertain");
        assertThatThrownBy(() -> evaluator.parseJudgements(binaryUncertain, ids))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("REVIEW_FIELD_INVALID_oldHistoryUnexpectedlyChanged");

        ObjectNode numericRationale = validResponse(ids);
        ((ObjectNode) numericRationale.withArray("reviews").get(0)).put("rationale", 123);
        assertThatThrownBy(() -> evaluator.parseJudgements(numericRationale, ids))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("REVIEW_RATIONALE_INVALID");
    }

    @Test
    void promptRequiresQuotedCanonicalJudgementStrings() throws Exception {
        JsonNode packageRoot = IndependentSemanticReviewPackageTest.loadAndValidatePackage(mapper);

        String prompt = evaluator().blindPrompt(packageRoot.path("scenarios"));

        assertThat(prompt)
            .contains("attachmentSemanticallySupported")
            .contains("JSON 字符串 \"yes\"、\"no\"、\"uncertain\"")
            .contains("不要返回 true/false 或数字");
    }

    @Test
    void rejectsMissingReorderedDuplicateAndInvalidJudgements() throws Exception {
        List<String> ids = scenarioIds();
        var evaluator = evaluator();

        ObjectNode missing = validResponse(ids);
        missing.withArray("reviews").remove(11);
        assertThatThrownBy(() -> evaluator.parseJudgements(missing, ids))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("REVIEW_SCENARIO_COUNT_INVALID");

        ObjectNode reordered = validResponse(ids);
        JsonNode first = reordered.withArray("reviews").remove(0);
        reordered.withArray("reviews").add(first);
        assertThatThrownBy(() -> evaluator.parseJudgements(reordered, ids))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("REVIEW_SCENARIO_ORDER_INVALID");

        ObjectNode duplicate = validResponse(ids);
        ((ObjectNode) duplicate.withArray("reviews").get(1)).put("scenarioId", ids.get(0));
        assertThatThrownBy(() -> evaluator.parseJudgements(duplicate, ids))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("REVIEW_SCENARIO_ID_INVALID");

        ObjectNode invalid = validResponse(ids);
        ((ObjectNode) invalid.withArray("reviews").get(0))
            .put("attachmentSemanticallySupported", "pass");
        assertThatThrownBy(() -> evaluator.parseJudgements(invalid, ids))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("REVIEW_FIELD_INVALID_attachmentSemanticallySupported");
    }

    private ProjectHistoryIndependentSemanticReviewEvaluatorTest evaluator() {
        var evaluator = new ProjectHistoryIndependentSemanticReviewEvaluatorTest();
        evaluator.objectMapper = mapper;
        evaluator.redactor = new SensitiveContentRedactor();
        return evaluator;
    }

    private void assertInvalidAttachment(
        ProjectHistoryIndependentSemanticReviewEvaluatorTest evaluator,
        List<String> ids,
        java.util.function.Consumer<ObjectNode> mutation
    ) {
        ObjectNode invalid = validResponse(ids);
        mutation.accept((ObjectNode) invalid.withArray("reviews").get(0));
        assertThatThrownBy(() -> evaluator.parseJudgements(invalid, ids))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("REVIEW_FIELD_INVALID_attachmentSemanticallySupported");
    }

    private List<String> scenarioIds() throws Exception {
        JsonNode packageRoot = IndependentSemanticReviewPackageTest.loadAndValidatePackage(mapper);
        List<String> ids = new ArrayList<>();
        packageRoot.path("scenarios").forEach(node -> ids.add(node.path("id").asText("")));
        return List.copyOf(ids);
    }

    private ObjectNode validResponse(List<String> ids) {
        ObjectNode root = mapper.createObjectNode();
        var reviews = root.putArray("reviews");
        for (String id : ids) {
            reviews.addObject()
                .put("scenarioId", id)
                .put("attachmentSemanticallySupported", "uncertain")
                .put("shouldRemainIndependent", "uncertain")
                .put("oldHistoryUnexpectedlyChanged", "no")
                .put("truthfulnessConcern", "no")
                .put("rationale", "仅依据给定证据保留不确定性")
                .put("confidence", "low");
        }
        return root;
    }
}
