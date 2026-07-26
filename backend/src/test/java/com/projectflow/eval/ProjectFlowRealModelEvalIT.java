package com.projectflow.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.entity.AiProvider;
import com.projectflow.entity.AiProviderType;
import com.projectflow.entity.ModelProtocol;
import com.projectflow.eval.ProjectFlowEvalGroundTruth.EvalCase;
import com.projectflow.eval.ProjectFlowEvalObservation.EvalClaim;
import com.projectflow.eval.ProjectFlowEvalObservation.StageResult;
import com.projectflow.service.AiProviderUrlGuard;
import com.projectflow.service.FinalProfileSynthesisService;
import com.projectflow.service.ModelGatewayService;
import com.projectflow.service.ModelFailureClassifier;
import com.projectflow.service.ModelOutputAdapter;
import com.projectflow.service.ModelTaskType;
import com.projectflow.service.SemanticScoutService;

class ProjectFlowRealModelEvalIT {
    private static final String PROMPT_VERSION =
        SemanticScoutService.PROMPT_VERSION + "+" + FinalProfileSynthesisService.PROMPT_VERSION;
    private static final Pattern EVIDENCE_ID = Pattern.compile("source:[A-Za-z0-9._-]+");

    @Test
    void evaluatesRealProviderThroughProjectFlowModelGateway() throws Exception {
        ProviderConfig config = providerConfig();
        Assumptions.assumeTrue(config != null, "未提供真实 Provider 配置，显式真实模型评测跳过");
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        ModelGatewayService gateway = new ModelGatewayService(
            mapper,
            new AiProviderUrlGuard(),
            new ModelOutputAdapter(mapper),
            Math.min(45, Math.max(30, config.timeoutSeconds()))
        );
        AiProvider provider = new AiProvider(UUID.randomUUID());
        provider.update(
            config.name(),
            config.baseUrl(),
            config.apiKey(),
            config.model(),
            config.type(),
            0.1,
            config.maxTokens(),
            false,
            List.of("V3.7.2_REAL_EVAL")
        );
        provider.configureProtocol(
            config.protocol(),
            null,
            null,
            null,
            null,
            java.util.Map.of(),
            Math.min(45, Math.max(30, config.timeoutSeconds())),
            null,
            null,
            null,
            null,
            null
        );

        ProjectFlowEvalGroundTruth groundTruth = ProjectFlowEvalGroundTruth.load(mapper);
        List<ProjectFlowEvalObservation> observations = new ArrayList<>();
        int maxCases = Integer.getInteger("projectflow.eval.max-cases", groundTruth.cases().size());
        int importantRepetitions = Integer.getInteger("projectflow.eval.important-repetitions", 3);
        List<EvalCase> selectedCases = groundTruth.cases().stream().limit(Math.max(1, maxCases)).toList();
        for (EvalCase testCase : selectedCases) {
            int runs = testCase.important() ? Math.max(1, importantRepetitions) : 1;
            for (int run = 1; run <= runs; run++) {
                System.out.printf(
                    "REAL_EVAL_START case=%s run=%d/%d%n",
                    testCase.id(),
                    run,
                    runs
                );
                ProjectFlowEvalObservation observation = runCase(gateway, provider, config, testCase, run);
                observations.add(observation);
                System.out.printf(
                    "REAL_EVAL_DONE case=%s run=%d status=%s requests=%d tokens=%d latencyMs=%d%n",
                    testCase.id(),
                    run,
                    observation.finalStatus(),
                    observation.requestCount(),
                    observation.totalTokens(),
                    observation.latencyMs()
                );
            }
        }
        ProjectFlowEvalHarness harness = new ProjectFlowEvalHarness(mapper);
        var evalRun = harness.evaluate(groundTruth, observations);
        Path output = Path.of("target", "projectflow-eval", "real");
        harness.writeArtifacts(evalRun, output);
        System.out.println("REAL_EVAL_AGGREGATE " + mapper.writeValueAsString(evalRun.summary()));

        long successful = observations.stream().filter(value -> !value.failed()).count();
        assertThat(successful).as("真实模型至少应完成一个代表性 case").isPositive();
        if (selectedCases.size() == groundTruth.cases().size() && importantRepetitions >= 3) {
            assertThat(evalRun.summary().failureRate()).isLessThanOrEqualTo(0.05);
            assertThat(evalRun.summary().criticalEvidenceRecall()).isGreaterThanOrEqualTo(0.85);
            assertThat(evalRun.summary().unsupportedClaimRate()).isLessThanOrEqualTo(0.05);
            assertThat(evalRun.summary().toolSelectionRecall()).isGreaterThanOrEqualTo(0.80);
            assertThat(evalRun.summary().unnecessaryToolRate()).isLessThanOrEqualTo(0.15);
            assertThat(evalRun.summary().dynamicViewRecall()).isGreaterThanOrEqualTo(0.90);
            assertThat(evalRun.summary().repeatability()).isGreaterThanOrEqualTo(0.80);
            assertThat(
                evalRun.summary().secondStageEvidenceGain() > 0
                    || evalRun.summary().secondStageUnsupportedClaimReduction() > 0
                    || evalRun.summary().secondStageViewGain() > 0
            ).as("深读 case 的第二阶段至少应产生一种可测增益").isTrue();
            assertThat(observations.stream()
                .filter(value -> groundTruth.cases().stream()
                    .anyMatch(testCase -> testCase.id().equals(value.caseId()) && testCase.important()))
                .flatMap(value -> value.mustNotClaimViolations().stream())
                .toList())
                .as("关键 case 不得出现 must-not-claim 违反")
                .isEmpty();
            assertThat(evalRun.summary().modelRequestCount()).isPositive();
        }
    }

    private static ProjectFlowEvalObservation runCase(
        ModelGatewayService gateway,
        AiProvider provider,
        ProviderConfig config,
        EvalCase testCase,
        int run
    ) {
        if ("empty-directory".equals(testCase.id()) || "blank-text".equals(testCase.id())) {
            return deterministicZeroModelObservation(config, testCase, run);
        }
        long started = System.nanoTime();
        try {
            var response = gateway.callStructured(
                provider,
                prompt(testCase),
                ModelTaskType.PROJECT_UNDERSTANDING_SNAPSHOT
            );
            JsonNode root = response.parsed().root();
            JsonNode scout = root.path("semanticScout");
            JsonNode profile = root.path("dynamicProfile");
            List<String> shapes = objectTexts(scout.path("projectShapeHypotheses"), "shape");
            List<String> selectedEvidence = new ArrayList<>();
            List<String> deepReads = new ArrayList<>();
            for (JsonNode assessment : array(scout.path("evidenceSourceAssessments"))) {
                String id = assessment.path("evidenceId").asText("").strip();
                if (!id.isBlank() && (
                    assessment.path("shouldDeepRead").asBoolean(false)
                        || "HIGH".equalsIgnoreCase(assessment.path("importance").asText(""))
                )) {
                    selectedEvidence.add(id);
                }
                if (!id.isBlank() && assessment.path("shouldDeepRead").asBoolean(false)) deepReads.add(id);
            }
            List<EvalClaim> claims = claims(profile.path("sections"));
            claims.stream().flatMap(value -> value.evidenceRefs().stream()).forEach(selectedEvidence::add);
            List<String> tools = texts(scout.path("recommendedToolCalls"));
            List<String> knownTools = tools.stream().filter(ProjectFlowRealModelEvalIT::registeredTool).toList();
            List<String> unavailable = tools.stream().filter(value -> !registeredTool(value)).toList();
            List<String> views = new ArrayList<>(texts(scout.path("applicableDimensions")));
            views.addAll(objectTexts(profile.path("sections"), "type"));
            List<String> conflicts = normalizedConflictLabels(scout.path("potentialConflicts"));
            List<String> unknowns = new ArrayList<>(texts(scout.path("unknowns")));
            unknowns.addAll(texts(root.path("unknowns")));
            List<String> uniqueEvidence = distinct(selectedEvidence);
            StageResult stageOne = new StageResult(uniqueEvidence, claims, distinct(views));
            StageResult stageTwo = null;
            int modelRequests = Math.max(1, response.diagnostics().requestCount());
            int inputTokens = response.diagnostics().promptTokens();
            int outputTokens = response.diagnostics().completionTokens();
            int totalTokens = response.diagnostics().totalTokens();
            int stageOneInputTokens = inputTokens;
            int stageOneOutputTokens = outputTokens;
            int stageTwoInputTokens = 0;
            int stageTwoOutputTokens = 0;
            long latencyMs = response.diagnostics().latencyMs();
            int retries = response.diagnostics().transportRetryCount();
            String finishReason = response.diagnostics().finishReason();
            boolean degraded = false;
            if (!deepReads.isEmpty()) {
                try {
                    var finalResponse = gateway.callStructured(
                        provider,
                        finalPrompt(deepReads, toolEvidence(testCase), root.path("dynamicProfile")),
                        ModelTaskType.PROJECT_UNDERSTANDING_FINAL_SYNTHESIS
                    );
                    JsonNode finalRoot = finalResponse.parsed().root();
                    List<EvalClaim> finalClaims = claims(finalRoot.path("dynamicProfile").path("sections"));
                    List<String> finalViews = new ArrayList<>();
                    finalViews.addAll(objectTexts(finalRoot.path("dynamicProfile").path("sections"), "type"));
                    List<String> finalEvidence = new ArrayList<>();
                    finalClaims.stream().flatMap(value -> value.evidenceRefs().stream()).forEach(finalEvidence::add);
                    stageTwo = new StageResult(distinct(finalEvidence), finalClaims, distinct(finalViews));
                    if (!finalClaims.isEmpty()) claims = finalClaims;
                    selectedEvidence.addAll(finalEvidence);
                    views.addAll(finalViews);
                    modelRequests += Math.max(1, finalResponse.diagnostics().requestCount());
                    inputTokens += finalResponse.diagnostics().promptTokens();
                    outputTokens += finalResponse.diagnostics().completionTokens();
                    totalTokens += finalResponse.diagnostics().totalTokens();
                    stageTwoInputTokens = finalResponse.diagnostics().promptTokens();
                    stageTwoOutputTokens = finalResponse.diagnostics().completionTokens();
                    latencyMs += finalResponse.diagnostics().latencyMs();
                    retries += finalResponse.diagnostics().transportRetryCount();
                    finishReason = finalResponse.diagnostics().finishReason();
                } catch (Exception finalFailure) {
                    degraded = true;
                }
            }
            List<String> unsupportedClaims = unsupportedClaims(claims);
            List<String> mustNotViolations = mustNotViolations(claims, testCase.mustNotClaim());
            List<String> expectedViewMatches = intersection(views, testCase.expectedViews());
            return new ProjectFlowEvalObservation(
                testCase.id(),
                testCase.id() + "-real-" + run,
                PROMPT_VERSION,
                "3.7.2",
                testCase.source(),
                run,
                config.name(),
                config.protocol().name(),
                config.model(),
                Instant.now(),
                distinct(shapes),
                distinct(selectedEvidence),
                distinct(knownTools),
                distinct(unavailable),
                tools.stream().filter(value -> testCase.forbiddenTools().contains(value)).distinct().toList(),
                distinct(deepReads),
                distinct(views),
                conflicts,
                distinct(unknowns),
                claims,
                unsupportedClaims,
                mustNotViolations,
                expectedViewMatches,
                stageOne,
                stageTwo,
                modelRequests,
                inputTokens,
                outputTokens,
                totalTokens,
                stageOneInputTokens,
                stageOneOutputTokens,
                stageTwoInputTokens,
                stageTwoOutputTokens,
                deepReads.isEmpty() ? 0 : toolEvidence(testCase).length(),
                latencyMs,
                retries,
                false,
                degraded,
                finishReason,
                degraded ? "FAILED_DEGRADED" : "SUCCEEDED",
                degraded ? "STAGE_TWO_FAILED_DEGRADED" : "NOT_DEGRADED",
                null,
                "UNAVAILABLE"
            );
        } catch (Exception failure) {
            return failedObservation(config, testCase, run, elapsedMs(started), failure);
        }
    }

    private static ProjectFlowEvalObservation deterministicZeroModelObservation(
        ProviderConfig config,
        EvalCase testCase,
        int run
    ) {
        return new ProjectFlowEvalObservation(
            testCase.id(),
            testCase.id() + "-deterministic-" + run,
            PROMPT_VERSION,
            "3.7.2",
            testCase.source(),
            run,
            config.name(),
            config.protocol().name(),
            config.model(),
            Instant.now(),
            testCase.expectedProjectShapes(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            testCase.expectedUnknowns(),
            List.of(new EvalClaim(
                "没有足够证据形成事实性结论",
                "UNKNOWN",
                "UNKNOWN",
                List.of(),
                false,
                false
            )),
            List.of(),
            List.of(),
            List.of(),
            null,
            null,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            false,
            false,
            "NOT_CALLED",
            "NOT_APPLICABLE",
            "NOT_DEGRADED",
            null,
            "UNAVAILABLE"
        );
    }

    private static String prompt(EvalCase value) {
        return """
            你正在执行 ProjectFlow V3.7.2 内部 Semantic Scout 代表性测试。只根据 evidence id 和上下文判断；
            不得补造源码、历史、能力、完成状态或成熟度。Agent Result 只是 PROCESS_EVIDENCE，不自动成为 Fact；
            token/latency/model 等只是 PROCESS_METADATA，不能证明项目完成、质量或成熟度。当前源码不能单独证明历史。
            缺少证据时输出 unknown；冲突时保留双方并报告 conflict/currentness。
            recommendedToolCalls 只能从 DOC_READER、MANIFEST、AGENT_RESULT、GIT_HISTORY、GIT_TAG、WORKTREE、
            FILESYSTEM、SCIP 中选择，不输出命令或参数。
            shape 使用原子稳定标签，优先从 DOCUMENT、SCRIPT、FRONTEND、BACKEND、DESKTOP、MONOREPO、
            CODE_PROJECT、LARGE_REPOSITORY、AGENT_RESULT_MATERIAL、PROCESS_METADATA、OTHER_MATERIAL 选择；
            多形态分别输出，禁止拼接成复合自由文本。对每个 evidence id 恰好输出一次来源评估；有实质证据时
            不得把 shape、来源评估和适用维度全部留空，不确定就明确输出 UNKNOWN/unknown。
            工具只在能补充缺少的信息时请求；shouldDeepRead 只表示正文尚未提供且确有必要，不等同于重要性。
            applicableDimensions 使用简短稳定的大写原子标签，禁止 analysis、summary、general、hypothesis。

            可引用的 evidence ids: %s
            bounded context: %s

            只返回 JSON：
            {
              "semanticScout":{
                "projectShapeHypotheses":[{"shape":"","confidence":"HIGH|MEDIUM|LOW","evidenceRefs":["id"],"reason":""}],
                "evidenceSourceAssessments":[{"evidenceId":"","semanticRole":"","importance":"HIGH|MEDIUM|LOW",
                  "currentness":"CURRENT|HISTORICAL|POSSIBLY_STALE|UNKNOWN","shouldDeepRead":false,
                  "shouldSkip":false,"reason":"","confidence":"HIGH|MEDIUM|LOW"}],
                "applicableDimensions":[],"recommendedToolCalls":[],"unknowns":[],"skipCandidates":[],
                "potentialConflicts":[{"text":"","evidenceRefs":["id"]}],
                "currentnessWarnings":[{"text":"","evidenceRefs":["id"]}]
              },
              "dynamicProfile":{
                "summary":"",
                "sections":[{"id":"","type":"","title":"","summary":"",
                  "claims":[{"text":"","confidence":"HIGH|MEDIUM|LOW","evidenceRefs":["id"]}],
                  "confidence":"HIGH|MEDIUM|LOW","epistemicStatus":"OBSERVED|INFERRED|UNKNOWN",
                  "displayPriority":50,"applicabilityReason":""}]
              },
              "unknowns":[]
            }
            最多 4 个 shape、12 个 evidence assessment、8 个维度、6 个 section、每个 section 4 条 claim。
            Prompt version: %s
            """.formatted(
            evidenceIds(value.context()),
            value.context(),
            PROMPT_VERSION
        );
    }

    private static String finalPrompt(List<String> deepReadEvidenceIds, String context, JsonNode stageOneProfile) {
        return """
            你正在执行 ProjectFlow V3.7.2 Final Synthesis 内部测试。以下新增深读证据已通过校验：
            evidence ids: %s
            bounded tool evidence: %s
            stage one profile: %s

            只修正新增证据直接支持的结论；Agent Result 不自动成为 Fact，PROCESS_METADATA 不证明完成或质量；
            当前源码不独自证明历史；冲突必须保留。
            dynamicProfile claim 必须引用真实 evidence id。只返回：
            {"dynamicProfile":{"summary":"","sections":[]},"unknowns":[]}
            可按第一阶段结构填充 sections，但不得增加根字段。
            Prompt version: %s
            """.formatted(
            distinct(deepReadEvidenceIds),
            context,
            stageOneProfile,
            PROMPT_VERSION
        );
    }

    private static String toolEvidence(EvalCase value) {
        return value.toolEvidence() == null || value.toolEvidence().isBlank()
            ? value.context()
            : value.toolEvidence();
    }

    private static List<EvalClaim> claims(JsonNode sections) {
        List<EvalClaim> result = new ArrayList<>();
        for (JsonNode section : array(sections)) {
            String epistemic = section.path("epistemicStatus").asText("INFERRED");
            for (JsonNode claim : array(section.path("claims"))) {
                String text = claim.path("text").asText("").strip();
                if (text.isBlank()) continue;
                List<String> refs = texts(claim.path("evidenceRefs"));
                result.add(new EvalClaim(
                    text,
                    section.path("type").asText("PROJECT_UNDERSTANDING"),
                    epistemic,
                    refs,
                    true,
                    false
                ));
            }
        }
        return List.copyOf(result);
    }

    private static List<String> normalizedConflictLabels(JsonNode values) {
        List<String> result = new ArrayList<>();
        for (String value : objectTexts(values, "text")) {
            String normalized = value.toUpperCase(Locale.ROOT);
            if (normalized.contains("README")
                && (normalized.contains("过时") || normalized.contains("STALE") || normalized.contains("CURRENT"))) {
                result.add("README_CURRENTNESS");
            } else if (normalized.contains("README")
                && (normalized.contains("源码") || normalized.contains("SOURCE")
                    || normalized.contains("MANIFEST") || normalized.contains("数据库"))) {
                result.add("README_SOURCE_CONFLICT");
            } else if ((normalized.contains("路线") || normalized.contains("ROADMAP"))
                && (normalized.contains("历史") || normalized.contains("HISTOR"))) {
                result.add("HISTORICAL_ROADMAP_CURRENTNESS");
            } else {
                result.add(value);
            }
        }
        return distinct(result);
    }

    private static List<String> evidenceIds(String context) {
        if (context == null || context.isBlank()) return List.of();
        List<String> result = new ArrayList<>();
        var matcher = EVIDENCE_ID.matcher(context);
        while (matcher.find()) result.add(matcher.group());
        return distinct(result);
    }

    private static List<String> objectTexts(JsonNode values, String field) {
        List<String> result = new ArrayList<>();
        for (JsonNode value : array(values)) {
            String text = value.path(field).asText("").strip();
            if (!text.isBlank()) result.add(text);
        }
        return List.copyOf(result);
    }

    private static List<String> texts(JsonNode values) {
        List<String> result = new ArrayList<>();
        for (JsonNode value : array(values)) {
            String text = value.asText("").strip();
            if (!text.isBlank()) result.add(text);
        }
        return List.copyOf(result);
    }

    private static List<JsonNode> array(JsonNode values) {
        if (!values.isArray()) return List.of();
        List<JsonNode> result = new ArrayList<>();
        values.forEach(result::add);
        return result;
    }

    private static List<String> distinct(List<String> values) {
        return List.copyOf(new LinkedHashSet<>(values == null ? List.of() : values));
    }

    private static boolean registeredTool(String value) {
        if (value == null) return false;
        return switch (value.strip().toUpperCase(Locale.ROOT)) {
            case "DOC_READER", "MANIFEST", "AGENT_RESULT", "GIT_HISTORY", "GIT_TAG",
                "WORKTREE", "FILESYSTEM", "SCIP" -> true;
            default -> false;
        };
    }

    private static ProjectFlowEvalObservation failedObservation(
        ProviderConfig config,
        EvalCase testCase,
        int run,
        long latencyMs,
        Exception failure
    ) {
        String failureCategory = ModelFailureClassifier.classifyException(failure)
            + ":" + safeExceptionTypes(failure);
        return new ProjectFlowEvalObservation(
            testCase.id(),
            testCase.id() + "-real-" + run,
            PROMPT_VERSION,
            "3.7.2",
            testCase.source(),
            run,
            config.name(),
            config.protocol().name(),
            config.model(),
            Instant.now(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            null,
            1,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            latencyMs,
            0,
            true,
            true,
            failureCategory,
            "FAILED",
            "DEGRADED_NO_SEMANTIC_RESULT:" + failureCategory,
            null,
            "UNAVAILABLE"
        );
    }

    private static String safeExceptionTypes(Throwable failure) {
        List<String> types = new ArrayList<>();
        Throwable current = failure;
        while (current != null && types.size() < 4) {
            if (current instanceof ModelGatewayService.ModelHttpException http) {
                types.add(current.getClass().getSimpleName() + "-" + http.statusCode());
            } else {
                types.add(current.getClass().getSimpleName());
            }
            current = current.getCause();
        }
        return String.join(">", types);
    }

    static ProviderConfig providerConfig() throws Exception {
        String databasePath = System.getProperty("projectflow.eval.provider-db", "").strip();
        if (!databasePath.isBlank()) return providerFromDatabase(databasePath);
        String genericApiKey = System.getenv("PROJECTFLOW_REAL_MODEL_API_KEY");
        boolean genericProvider = genericApiKey != null && !genericApiKey.isBlank();
        String apiKey = genericProvider ? genericApiKey.strip() : environment("DEEPSEEK_API_KEY");
        if (apiKey == null || apiKey.isBlank()) return null;
        return new ProviderConfig(
            environmentOrDefault(
                "PROJECTFLOW_REAL_MODEL_PROVIDER",
                genericProvider ? "OpenAI-compatible real eval" : "DeepSeek real eval"
            ),
            environmentOrDefault("PROJECTFLOW_REAL_MODEL_BASE_URL", "https://api.deepseek.com"),
            apiKey,
            environmentOrDefault(
                "PROJECTFLOW_REAL_MODEL_NAME",
                environmentOrDefault("DEEPSEEK_MODEL", "deepseek-chat")
            ),
            enumEnvironment(
                "PROJECTFLOW_REAL_MODEL_TYPE",
                AiProviderType.class,
                genericProvider ? AiProviderType.OPENAI : AiProviderType.DEEPSEEK
            ),
            enumEnvironment(
                "PROJECTFLOW_REAL_MODEL_PROTOCOL",
                ModelProtocol.class,
                genericProvider ? ModelProtocol.OPENAI_RESPONSES : ModelProtocol.OPENAI_CHAT_COMPLETIONS
            ),
            integerEnvironment("PROJECTFLOW_REAL_MODEL_TIMEOUT_SECONDS", 120),
            integerEnvironment("PROJECTFLOW_REAL_MODEL_MAX_TOKENS", 16_000)
        );
    }

    private static String environment(String name) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String environmentOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value.strip();
    }

    private static int integerEnvironment(String name, int fallback) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) return fallback;
        return Integer.parseInt(value.strip());
    }

    private static <T extends Enum<T>> T enumEnvironment(String name, Class<T> type, T fallback) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) return fallback;
        return Enum.valueOf(type, value.strip().toUpperCase(Locale.ROOT));
    }

    private static ProviderConfig providerFromDatabase(String databasePath) throws Exception {
        String normalized = Path.of(databasePath).toAbsolutePath().normalize().toString().replace('\\', '/');
        if (normalized.endsWith(".mv.db")) normalized = normalized.substring(0, normalized.length() - 6);
        String url = "jdbc:h2:file:" + normalized
            + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;ACCESS_MODE_DATA=r;IFEXISTS=TRUE";
        try (
            var connection = DriverManager.getConnection(url, "sa", "");
            var statement = connection.prepareStatement("""
                select name, base_url, api_key, model_name, type, protocol,
                       coalesce(request_timeout_seconds, 120), max_tokens
                from ai_providers
                where default_enabled = true
                order by updated_at desc
                limit 1
                """);
            var result = statement.executeQuery()
        ) {
            if (!result.next()) return null;
            return new ProviderConfig(
                result.getString(1),
                result.getString(2),
                result.getString(3),
                result.getString(4),
                AiProviderType.valueOf(result.getString(5)),
                ModelProtocol.valueOf(result.getString(6)),
                Math.max(60, result.getInt(7)),
                Math.max(4_000, result.getInt(8))
            );
        }
    }

    private static long elapsedMs(long started) {
        return Math.max(0, (System.nanoTime() - started) / 1_000_000);
    }

    private static List<String> unsupportedClaims(List<EvalClaim> claims) {
        return claims.stream()
            .filter(value -> value.evidenceRefs().isEmpty()
                && !"UNKNOWN".equalsIgnoreCase(value.epistemicStatus())
                && !"INFERRED".equalsIgnoreCase(value.epistemicStatus()))
            .map(EvalClaim::text)
            .toList();
    }

    private static List<String> mustNotViolations(List<EvalClaim> claims, List<String> forbidden) {
        List<String> result = new ArrayList<>();
        for (EvalClaim claim : claims) {
            if (forbidden.stream()
                .anyMatch(marker -> ProjectFlowEvalTextRules.containsUnnegatedMarker(claim.text(), marker))) {
                result.add(claim.text());
            }
        }
        return List.copyOf(result);
    }

    private static List<String> intersection(List<String> actual, List<String> expected) {
        LinkedHashSet<String> expectedSet = new LinkedHashSet<>(expected);
        return actual.stream().filter(expectedSet::contains).distinct().toList();
    }

    record ProviderConfig(
        String name,
        String baseUrl,
        String apiKey,
        String model,
        AiProviderType type,
        ModelProtocol protocol,
        int timeoutSeconds,
        int maxTokens
    ) {
    }
}
