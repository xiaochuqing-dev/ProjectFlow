package com.projectflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.dto.ProjectHistoryDtos.ChangeStory;
import com.projectflow.dto.ProjectHistoryDtos.HistoryChapter;
import com.projectflow.eval.ProjectHistoryV385QualityEvaluator;
import com.projectflow.service.ProjectHistoryLanguageService;
import com.projectflow.entity.ProjectHistoryEvent.Transition;

class ProjectHistoryV385GroundTruthTest {
    private static final String RESOURCE = "/projectflow-v385/history-ground-truth.json";
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void schemaFreezesCalibrationHoldoutAndVerificationContract() throws Exception {
        String raw;
        JsonNode root;
        try (InputStream input = getClass().getResourceAsStream(RESOURCE)) {
            assertThat(input).isNotNull();
            byte[] bytes = input.readAllBytes();
            raw = new String(bytes, StandardCharsets.UTF_8);
            root = mapper.readTree(bytes);
        }
        assertThat(root.path("version").asText()).isEqualTo("projectflow-v3.8.5-history-ground-truth-v1");
        assertThat(root.path("frozenIdentity").path("labelsFrozenBeforeModelRuns").asBoolean()).isTrue();
        assertThat(root.path("frozenIdentity").path("fixtureHash").asText()).matches("sha256:[0-9a-f]{64}");
        assertThat(root.path("verification").path("verificationCode").asText()).isEqualTo("V385-HISTORY-GT-LOCK-20260806");
        assertThat(root.path("technicalWordRules").path("reasonRequiresEvidence").asBoolean()).isTrue();
        assertThat(root.path("cases").isArray()).isTrue();
        assertThat(root.path("cases")).hasSizeGreaterThanOrEqualTo(16);

        Set<String> ids = new HashSet<>();
        Set<String> calibration = new HashSet<>();
        root.path("frozenIdentity").path("calibrationCases").forEach(value -> calibration.add(value.asText()));
        Set<String> holdout = new HashSet<>();
        root.path("frozenIdentity").path("holdoutCases").forEach(value -> holdout.add(value.asText()));
        assertThat(calibration).doesNotContainAnyElementsOf(holdout);
        for (JsonNode value : root.path("cases")) {
            assertThat(ids.add(value.path("id").asText())).as("duplicate case").isTrue();
            assertThat(value.path("split").asText()).isIn("CALIBRATION", "HOLDOUT");
            assertThat(value.path("fixtureHash").asText()).startsWith("fixture:");
            assertThat(value.path("primaryStoryGroups").isArray()).isTrue();
            assertThat(value.path("supportingStoryGroups").isArray()).isTrue();
            assertThat(value.path("chapterBoundaries").isArray()).isTrue();
            assertThat(value.path("titlePositive")).isNotEmpty();
            assertThat(value.path("titleNegative")).isNotEmpty();
            assertThat(value.path("beforeChangeAfter").path("before").asText()).isNotBlank();
            assertThat(value.path("beforeChangeAfter").path("change").asText()).isNotBlank();
            assertThat(value.path("beforeChangeAfter").path("after").asText()).isNotBlank();
            assertThat(value.path("thread").path("transitions")).isNotEmpty();
            assertThat(value.path("verificationTests")).isNotEmpty();
            for (JsonNode reference : value.path("verificationTests")) verifyMethod(reference.asText());
        }
        assertThat(ids).containsAll(calibration).containsAll(holdout);
        assertThat(raw).doesNotContain("C:\\Users\\xiaochuqing\\", "Bearer ey", "sk-live-")
            .doesNotContain("raw response body", "reasoning原文内容", "apiKey=");
    }

    @Test
    void evaluatorEnforcesReadableFirstLayerPrimarySupportingAndReasonEvidence() throws Exception {
        JsonNode root = load();
        UUID event = UUID.randomUUID();
        ChangeStory supporting = story("support", "补充登录测试", "SUPPORTING", "primary", event);
        ChangeStory primary = new ChangeStory(
            "primary", "login", "完善登录流程并统一失败提示", "登录入口更清楚，失败时有一致提示。",
            "用户只能从单一路径进入。", "增加邮箱兜底并统一失败提示。", "登录入口更清楚，失败时有一致提示。",
            List.of("登录体验"), "", List.of(), "", List.of(), List.of(), Instant.EPOCH, Instant.EPOCH,
            1, 1, "ENGINEERING_GROUPING", "DETERMINISTIC", "COMPLETE", List.of(), List.of(event), List.of("event:login"),
            "PRIMARY", "", List.of("support"), List.of(), List.of(), List.of(), "AUTOMATIC", "", "完善登录流程并统一失败提示",
            "登录入口更清楚，失败时有一致提示。", List.of(), false, false, "", "ACTIVE", List.of()
        );
        HistoryChapter chapter = new HistoryChapter(
            "chapter", "完善登录流程", "登录相关工作形成一个阶段。", Instant.EPOCH, Instant.EPOCH,
            List.of(), List.of("primary", "support"), 2, 2, "ENGINEERING_GROUPING", "COMPLETE", List.of()
        );
        var metrics = ProjectHistoryV385QualityEvaluator.evaluate(root, List.of(primary, supporting), List.of(chapter));
        assertThat(metrics.passes()).isTrue();
        assertThat(metrics.primaryCount()).isEqualTo(1);
        assertThat(metrics.supportingCount()).isEqualTo(1);
        assertThat(metrics.beforeChangeAfterCompleteness()).isEqualTo(1.0);
        assertThat(metrics.genericTemplateRate()).isZero();

        ChangeStory leaked = new ChangeStory(
            "leaked", "bad", "修改 ProjectHistoryReconstructionService", "工程分组：相关变化。", "", "", "",
            List.of(), "因为客户要求", List.of(), "", List.of(), List.of(), Instant.EPOCH, Instant.EPOCH, 0, 0,
            "ENGINEERING_GROUPING", "DETERMINISTIC", "PARTIAL", List.of(), List.of(), List.of(), "PRIMARY", "",
            List.of(), List.of(), List.of(), List.of(), "AUTOMATIC", "", "", "", List.of(), false, false, "", "ACTIVE", List.of()
        );
        var rejected = ProjectHistoryV385QualityEvaluator.evaluate(root, List.of(leaked), List.of());
        assertThat(rejected.passes()).isFalse();
        assertThat(rejected.technicalLeakCount()).isPositive();
        assertThat(rejected.reasonWithoutEvidenceCount()).isEqualTo(1);
    }

    @Test
    void deterministicFallbackUsesActionAndObjectWithoutForbiddenTemplates() {
        ProjectHistoryLanguageService language = new ProjectHistoryLanguageService();
        var result = language.fallback(Transition.CREATED, "backend/src/main/java/AuthController.java", List.of("backend/src/main/java/AuthController.java"), List.of(), List.of());
        assertThat(result.title()).contains("建立", "初始成果").doesNotContain("形成初始结果", "工程分组", "相关变化");
        assertThat(result.summary()).doesNotContain("来源记录显示", "进入当前时间点可确认的新状态");
        assertThat(language.supporting(
            List.of(com.projectflow.entity.ProjectHistoryEvent.Category.VALIDATION), List.of("tests/login.md"), List.of("test"), List.of(Transition.MODIFIED), false
        )).isTrue();
    }

    private JsonNode load() throws Exception {
        try (InputStream input = getClass().getResourceAsStream(RESOURCE)) {
            return mapper.readTree(input);
        }
    }

    private static ChangeStory story(String id, String title, String role, String primary, UUID event) {
        return new ChangeStory(
            id, "login", title, title + "，结果可核对。", "此前状态可确认。", "补充可核对变化。", "当前结果可用。",
            List.of("登录体验"), "", List.of(), "", List.of(), List.of(), Instant.EPOCH, Instant.EPOCH, 1, 1,
            "ENGINEERING_GROUPING", "DETERMINISTIC", "COMPLETE", List.of(), List.of(event), List.of("event:test"), role, primary,
            List.of(), List.of(), List.of(), List.of(), "AUTOMATIC", "", title, title + "，结果可核对。", List.of(), false, false, "", "ACTIVE", List.of()
        );
    }

    private static void verifyMethod(String reference) throws Exception {
        int separator = reference.lastIndexOf('#');
        assertThat(separator).as(reference).isGreaterThan(0);
        Class<?> type = Class.forName(reference.substring(0, separator));
        assertThat(type.getDeclaredMethods()).as(reference).anyMatch(method -> method.getName().equals(reference.substring(separator + 1)));
    }
}
