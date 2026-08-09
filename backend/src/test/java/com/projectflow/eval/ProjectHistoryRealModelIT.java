package com.projectflow.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.entity.AiProvider;
import com.projectflow.service.AiProviderUrlGuard;
import com.projectflow.service.ModelGatewayService;
import com.projectflow.service.ModelOutputAdapter;
import com.projectflow.service.ModelTaskType;
import com.projectflow.service.ProjectHistoryPromptBuilder;
import com.projectflow.service.ProjectHistoryModelOutputContract;

class ProjectHistoryRealModelIT {
    private static final List<String> WEAK_OR_PROHIBITED = List.of(
        "优化了系统", "改进了功能", "进行了重构", "提升了体验", "修改了相关文件",
        "成熟度", "关键里程碑", "项目成功", "成功完成", "下一步", "路线图", "未来计划"
    );

    @Test
    void qualifiesRealProviderForBoundedProjectHistorySynthesis() throws Exception {
        ProjectFlowRealModelEvalIT.ProviderConfig config = ProjectFlowRealModelEvalIT.providerConfig();
        Assumptions.assumeTrue(config != null, "未提供真实 Provider 配置，项目历程真实模型验收跳过");

        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        ProjectHistoryPromptBuilder promptBuilder = new ProjectHistoryPromptBuilder(mapper);
        ProjectHistoryPromptBuilder.PromptInput input = fixture();
        var production = promptBuilder.buildProduction(input);
        var evaluation = promptBuilder.buildEvaluation(input);
        assertThat(evaluation).as("Production 与 Eval 必须共享同一 Prompt 合同").isEqualTo(production);

        ModelGatewayService gateway = new ModelGatewayService(
            mapper,
            new AiProviderUrlGuard(),
            new ModelOutputAdapter(mapper),
            config.timeoutSeconds()
        );
        AiProvider provider = provider(config);
        ModelGatewayService.StructuredModelResponse response = gateway.callStructured(
            provider, production.prompt(), ModelTaskType.PROJECT_HISTORY_SYNTHESIS
        );

        Qualification qualification = validate(response.parsed().root(), input, production);
        writeSafeArtifact(mapper, config, response.diagnostics(), production, qualification);

        assertThat(qualification.rootSchemaViolationCount()).isZero();
        assertThat(qualification.entitySchemaViolationCount()).isZero();
        assertThat(qualification.missingEntityCount()).isZero();
        assertThat(qualification.duplicateEntityCount()).isZero();
        assertThat(qualification.crossProjectReferenceCount()).isZero();
        assertThat(qualification.invalidEvidenceRefCount()).isZero();
        assertThat(qualification.reasonWithoutEvidenceCount()).isZero();
        assertThat(qualification.missingReasonUnknownCount()).isZero();
        assertThat(qualification.emptyReadableSummaryCount()).isZero();
        assertThat(qualification.unsupportedClaimCount()).isZero();
    }

    private static ProjectHistoryPromptBuilder.PromptInput fixture() {
        String reasonFact = "fact:11111111-1111-1111-1111-111111111111";
        List<ProjectHistoryPromptBuilder.StoryPromptInput> stories = List.of(
            new ProjectHistoryPromptBuilder.StoryPromptInput(
                "story-auth-entry", "认证入口", "2026-07-01T08:00:00Z", "2026-07-03T08:00:00Z",
                List.of("ADDED", "MODIFIED"), List.of("新增认证入口", "补充邮箱兜底"),
                List.of("backend/auth", "frontend/login"),
                List.of("commit:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", reasonFact),
                List.of(reasonFact),
                "项目没有统一认证入口。", "新增认证入口，并在后续修改中补充邮箱兜底。", "当前保留统一入口和邮箱兜底。"
            ),
            new ProjectHistoryPromptBuilder.StoryPromptInput(
                "story-export-restore", "成果导出", "2026-07-08T08:00:00Z", "2026-07-15T08:00:00Z",
                List.of("ADDED", "REMOVED", "RESTORED"), List.of("新增导出", "移除不稳定实现", "恢复双格式导出"),
                List.of("backend/export", "docs"),
                List.of(
                    "commit:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                    "commit:cccccccccccccccccccccccccccccccccccccccc",
                    "commit:dddddddddddddddddddddddddddddddddddddddd"
                ),
                List.of(),
                "项目只提供页面内查看。", "导出能力经历新增、移除和恢复。", "当前以 PDF 和 Markdown 双格式提供导出。"
            )
        );
        List<ProjectHistoryPromptBuilder.ChapterPromptInput> chapters = List.of(
            new ProjectHistoryPromptBuilder.ChapterPromptInput(
                "chapter-july", "2026-07-01T08:00:00Z", "2026-07-15T08:00:00Z",
                List.of("story-auth-entry", "story-export-restore"), List.of("RESTORED", "10_DAY_GAP")
            )
        );
        return new ProjectHistoryPromptBuilder.PromptInput(stories, chapters);
    }

    private static AiProvider provider(ProjectFlowRealModelEvalIT.ProviderConfig config) {
        AiProvider provider = new AiProvider(UUID.randomUUID());
        provider.update(
            config.name(), config.baseUrl(), config.apiKey(), config.model(), config.type(),
            0.1, config.maxTokens(), false, List.of("V3.8.0_PROJECT_HISTORY_REAL_MODEL")
        );
        provider.configureProtocol(
            config.protocol(), null, null, null, null, Map.of(), config.timeoutSeconds(), null,
            config.supportsJsonMode(), null, config.supportsReasoning(), config.supportsReasoningControl()
        );
        return provider;
    }

    private static Qualification validate(
        JsonNode root,
        ProjectHistoryPromptBuilder.PromptInput input,
        ProjectHistoryPromptBuilder.PromptBuildResult prompt
    ) {
        int rootSchemaViolations = root != null && root.isObject()
            && fields(root).equals(ProjectHistoryModelOutputContract.ROOT_FIELDS) ? 0 : 1;
        Map<String, Set<String>> eligibleEvidence = new LinkedHashMap<>();
        input.stories().forEach(story -> eligibleEvidence.put(story.storyId(), Set.copyOf(story.reasonEligibleEvidenceRefs())));
        List<String> returnedStoryIds = new ArrayList<>();
        List<String> returnedChapterIds = new ArrayList<>();
        int entitySchemaViolations = 0;
        int invalidEvidenceRefs = 0;
        int reasonWithoutEvidence = 0;
        int missingReasonUnknown = 0;
        int emptyReadableSummaries = 0;
        int unsupportedClaims = 0;

        JsonNode stories = root == null ? null : root.path("stories");
        if (stories == null || !stories.isArray()) {
            rootSchemaViolations++;
        } else {
            for (JsonNode story : stories) {
                if (!story.isObject() || !fields(story).equals(ProjectHistoryModelOutputContract.STORY_FIELDS)) {
                    entitySchemaViolations++;
                }
                String storyId = text(story, "storyId");
                returnedStoryIds.add(storyId);
                Set<String> eligible = eligibleEvidence.getOrDefault(storyId, Set.of());
                List<String> refs = strings(story.path("reasonEvidenceRefs"));
                invalidEvidenceRefs += refs.stream().filter(ref -> !eligible.contains(ref)).count();
                String reason = text(story, "reason");
                if (!reason.isBlank() && refs.isEmpty()) reasonWithoutEvidence++;
                boolean reasonUnknownDisclosed = strings(story.path("unknowns")).stream()
                    .anyMatch(value -> value.contains("原因") && (value.contains("未知") || value.contains("没有") || value.contains("缺少")));
                if (eligible.isEmpty() && reason.isBlank() && !reasonUnknownDisclosed) {
                    missingReasonUnknown++;
                }
                String title = text(story, "humanTitle");
                String summary = text(story, "oneSentenceSummary");
                if (title.length() < 4 || summary.length() < 6) emptyReadableSummaries++;
                if (containsProhibited(title + "\n" + summary + "\n" + reason)) unsupportedClaims++;
            }
        }

        JsonNode chapters = root == null ? null : root.path("chapters");
        if (chapters == null || !chapters.isArray()) {
            rootSchemaViolations++;
        } else {
            for (JsonNode chapter : chapters) {
                if (!chapter.isObject() || !fields(chapter).equals(ProjectHistoryModelOutputContract.CHAPTER_FIELDS)) {
                    entitySchemaViolations++;
                }
                String chapterId = text(chapter, "chapterId");
                returnedChapterIds.add(chapterId);
                String title = text(chapter, "title");
                String summary = text(chapter, "summary");
                if (title.length() < 4 || summary.length() < 6) emptyReadableSummaries++;
                if (containsProhibited(title + "\n" + summary)) unsupportedClaims++;
            }
        }

        Set<String> uniqueStoryIds = new LinkedHashSet<>(returnedStoryIds);
        Set<String> uniqueChapterIds = new LinkedHashSet<>(returnedChapterIds);
        int duplicateEntities = returnedStoryIds.size() - uniqueStoryIds.size()
            + returnedChapterIds.size() - uniqueChapterIds.size();
        int missingEntities = difference(prompt.includedStoryIds(), uniqueStoryIds).size()
            + difference(prompt.includedChapterIds(), uniqueChapterIds).size();
        int unknownEntities = difference(uniqueStoryIds, prompt.includedStoryIds()).size()
            + difference(uniqueChapterIds, prompt.includedChapterIds()).size();
        return new Qualification(
            rootSchemaViolations, entitySchemaViolations, missingEntities, duplicateEntities, unknownEntities,
            invalidEvidenceRefs, reasonWithoutEvidence, missingReasonUnknown,
            emptyReadableSummaries, unsupportedClaims, returnedStoryIds.size(), returnedChapterIds.size()
        );
    }

    private static void writeSafeArtifact(
        ObjectMapper mapper,
        ProjectFlowRealModelEvalIT.ProviderConfig config,
        ModelGatewayService.ModelCallDiagnostics diagnostics,
        ProjectHistoryPromptBuilder.PromptBuildResult prompt,
        Qualification qualification
    ) throws Exception {
        String outputName = System.getProperty("projectflow.eval.output-name", "history-real")
            .replaceAll("[^A-Za-z0-9._-]", "_");
        String configured = System.getProperty("projectflow.history.real-model-output", "").trim();
        Path output = configured.isBlank()
            ? Path.of("target", "projectflow-eval", outputName)
            : Path.of(configured).resolve(outputName);
        Files.createDirectories(output);

        Map<String, Object> artifact = new LinkedHashMap<>();
        artifact.put("version", "projectflow-v3.8.5-history-real-model-v4");
        artifact.put("generatedAt", Instant.now().toString());
        artifact.put("promptVersion", ProjectHistoryPromptBuilder.PROMPT_VERSION);
        artifact.put("provider", Map.of(
            "name", config.name(), "model", config.model(), "protocol", config.protocol().name(),
            "reasoningEffort", config.reasoningEffort()
        ));
        artifact.put("input", Map.of(
            "storyCount", prompt.includedStoryIds().size(),
            "chapterCount", prompt.includedChapterIds().size(),
            "promptCharacterCount", prompt.promptCharacterCount()
        ));
        artifact.put("contract", qualification);
        artifact.put("diagnostics", Map.of(
            "requestCount", diagnostics.requestCount(),
            "totalTokens", diagnostics.totalTokens(),
            "latencyMs", diagnostics.latencyMs(),
            "finishReason", diagnostics.normalizedFinishReason(),
            "schemaMatched", diagnostics.schemaMatched(),
            "truncated", diagnostics.truncated(),
            "reasoningPresent", diagnostics.reasoningPresent()
        ));
        artifact.put("security", Map.of(
            "apiKeyPersisted", false,
            "promptPersisted", false,
            "rawResponsePersisted", false,
            "reasoningPersisted", false,
            "absolutePathPersisted", false
        ));
        mapper.writerWithDefaultPrettyPrinter().writeValue(
            output.resolve("project-history-real-model.json").toFile(), artifact
        );
    }

    private static Set<String> fields(JsonNode node) {
        Set<String> result = new LinkedHashSet<>();
        node.fieldNames().forEachRemaining(result::add);
        return result;
    }

    private static String text(JsonNode node, String field) {
        return node != null && node.path(field).isTextual() ? node.path(field).asText().trim() : "";
    }

    private static List<String> strings(JsonNode node) {
        if (node == null || !node.isArray()) return List.of();
        List<String> result = new ArrayList<>();
        node.forEach(value -> {
            if (value.isTextual()) result.add(value.asText().trim());
        });
        return List.copyOf(result);
    }

    private static boolean containsProhibited(String value) {
        return WEAK_OR_PROHIBITED.stream().anyMatch(value::contains);
    }

    private static <T> Set<T> difference(Set<T> left, Set<T> right) {
        Set<T> result = new LinkedHashSet<>(left);
        result.removeAll(right);
        return result;
    }

    private record Qualification(
        int rootSchemaViolationCount,
        int entitySchemaViolationCount,
        int missingEntityCount,
        int duplicateEntityCount,
        int crossProjectReferenceCount,
        int invalidEvidenceRefCount,
        int reasonWithoutEvidenceCount,
        int missingReasonUnknownCount,
        int emptyReadableSummaryCount,
        int unsupportedClaimCount,
        int returnedStoryCount,
        int returnedChapterCount
    ) {
    }

}
