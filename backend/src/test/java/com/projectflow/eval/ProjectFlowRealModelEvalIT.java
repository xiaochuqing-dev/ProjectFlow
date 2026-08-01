package com.projectflow.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
import com.projectflow.dto.ProjectUnderstandingDtos.AdaptiveAnalysisPlanResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.EvidenceSourceMapResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.GitEvidenceResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.ProjectEvidenceSourceResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.RepositoryIntakeResponse;
import com.projectflow.service.AnalysisCapabilityProvider.CapabilityRequest;
import com.projectflow.service.AnalysisCapabilityProvider.ExecutionBudget;
import com.projectflow.service.AiProviderUrlGuard;
import com.projectflow.service.AnalysisToolRegistry;
import com.projectflow.service.AnalysisViewRegistry;
import com.projectflow.service.BoundedLocalAnalysisCapabilityProvider;
import com.projectflow.service.HighValueEvidenceGate;
import com.projectflow.service.LocalCommandExecutor;
import com.projectflow.service.ModelGatewayService;
import com.projectflow.service.ModelFailureClassifier;
import com.projectflow.service.ModelOutputAdapter;
import com.projectflow.service.ModelTaskType;
import com.projectflow.service.ProjectEvidenceDiscoveryService.PromptEvidence;
import com.projectflow.service.ProjectUnderstandingPromptBuilder;
import com.projectflow.service.SemanticScoutService;
import com.projectflow.service.SensitiveContentRedactor;

class ProjectFlowRealModelEvalIT {
    private static final String PROMPT_VERSION =
        ProjectUnderstandingPromptBuilder.SCOUT_PROMPT_VERSION
            + "+"
            + ProjectUnderstandingPromptBuilder.FINAL_PROMPT_VERSION;
    private static final Pattern EVIDENCE_ID = Pattern.compile("source:[A-Za-z0-9._-]+");

    @Test
    void evaluatesRealProviderThroughProjectFlowModelGateway() throws Exception {
        ProviderConfig config = providerConfig();
        Assumptions.assumeTrue(config != null, "未提供真实 Provider 配置，显式真实模型评测跳过");
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        ProjectUnderstandingPromptBuilder promptBuilder = new ProjectUnderstandingPromptBuilder();
        ModelGatewayService gateway = new ModelGatewayService(
            mapper,
            new AiProviderUrlGuard(),
            new ModelOutputAdapter(mapper),
            config.timeoutSeconds()
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
            List.of("V3.7.5_REAL_EVAL")
        );
        provider.configureProtocol(
            config.protocol(),
            null,
            null,
            null,
            null,
            java.util.Map.of(),
            config.timeoutSeconds(),
            null,
            null,
            null,
            config.supportsReasoning(),
            config.supportsReasoningControl()
        );

        ProjectFlowEvalGroundTruth groundTruth = ProjectFlowEvalGroundTruth.load(mapper);
        List<ProjectFlowEvalObservation> observations = new ArrayList<>();
        int maxCases = Integer.getInteger("projectflow.eval.max-cases", groundTruth.cases().size());
        int importantRepetitions = Integer.getInteger("projectflow.eval.important-repetitions", 3);
        int minimumRepetitions = Math.max(1, Integer.getInteger("projectflow.eval.minimum-repetitions", 1));
        Set<String> requestedCaseIds = requestedCaseIds();
        List<EvalCase> selectedCases = groundTruth.cases().stream()
            .filter(value -> requestedCaseIds.isEmpty() || requestedCaseIds.contains(value.id()))
            .limit(Math.max(1, maxCases))
            .toList();
        assertThat(selectedCases).as("显式 case 过滤后至少应保留一个真实案例").isNotEmpty();
        for (EvalCase testCase : selectedCases) {
            int runs = Math.max(
                minimumRepetitions,
                testCase.important() ? Math.max(1, importantRepetitions) : 1
            );
            for (int run = 1; run <= runs; run++) {
                System.out.printf(
                    "REAL_EVAL_START case=%s run=%d/%d%n",
                    testCase.id(),
                    run,
                    runs
                );
                ProjectFlowEvalObservation observation = runCase(
                    gateway,
                    provider,
                    config,
                    testCase,
                    run,
                    mapper,
                    promptBuilder
                );
                observations.add(observation);
                System.out.printf(
                    "REAL_EVAL_DONE case=%s run=%d status=%s requests=%d tokens=%d latencyMs=%d finish=%s%n",
                    testCase.id(),
                    run,
                    observation.finalStatus(),
                    observation.requestCount(),
                    observation.totalTokens(),
                    observation.latencyMs(),
                    observation.finishReason()
                );
            }
        }
        ProjectFlowEvalHarness harness = new ProjectFlowEvalHarness(mapper);
        var evalRun = harness.evaluate(groundTruth, observations);
        String outputName = System.getProperty("projectflow.eval.output-name", "real")
            .replaceAll("[^A-Za-z0-9._-]", "_");
        Path output = Path.of("target", "projectflow-eval", outputName);
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
        if (Boolean.getBoolean("projectflow.eval.enforce-v374-contract")) {
            assertThat(evalRun.summary().failureRate()).isLessThanOrEqualTo(0.05);
            assertThat(evalRun.summary().unsupportedClaimRate()).isZero();
            assertThat(evalRun.summary().criticalEvidenceRecall()).isGreaterThanOrEqualTo(0.90);
            assertThat(evalRun.summary().deepReadSufficiency()).isGreaterThanOrEqualTo(0.80);
            if (selectedCases.stream().anyMatch(value -> !value.expectedConflicts().isEmpty())) {
                assertThat(evalRun.summary().conflictDetectionRate()).isGreaterThanOrEqualTo(0.80);
            }
            assertThat(invalidEvidenceRefs(observations, groundTruth))
                .as("V3.7.5 Evidence 引用必须来自当前案例 allow-list")
                .isEmpty();
            assertThat(observations.stream()
                .flatMap(value -> value.mustNotClaimViolations().stream())
                .toList()).as("V3.7.5 强事实边界不得出现禁止声明").isEmpty();
        }
    }

    private static ProjectFlowEvalObservation runCase(
        ModelGatewayService gateway,
        AiProvider provider,
        ProviderConfig config,
        EvalCase testCase,
        int run,
        ObjectMapper mapper,
        ProjectUnderstandingPromptBuilder promptBuilder
    ) {
        if ("empty-directory".equals(testCase.id()) || "blank-text".equals(testCase.id())) {
            return deterministicZeroModelObservation(config, testCase, run);
        }
        long started = System.nanoTime();
        try {
            ObjectiveEligibility eligibility = objectiveEligibility(testCase);
            Set<String> allowedSourceEvidence = Set.copyOf(evidenceIds(testCase.context()));
            var response = gateway.callStructured(
                provider,
                buildScoutPrompt(mapper, promptBuilder, testCase),
                ModelTaskType.PROJECT_UNDERSTANDING_SNAPSHOT
            );
            printSafeStageDiagnostics(testCase.id(), "SCOUT", response.diagnostics());
            JsonNode root = response.parsed().root();
            JsonNode scout = root.path("semanticScout");
            JsonNode profile = root.path("dynamicProfile");
            printSafeCapabilityDecisions(testCase.id(), scout);
            List<String> shapes = array(scout.path("projectShapeHypotheses")).stream()
                .flatMap(item -> SemanticScoutService.normalizeShapes(item.path("shape").asText("")).stream())
                .toList();
            List<String> selectedEvidence = new ArrayList<>();
            List<String> deepReads = new ArrayList<>();
            for (JsonNode assessment : array(scout.path("evidenceSourceAssessments"))) {
                String id = assessment.path("evidenceId").asText("").strip();
                if (allowedSourceEvidence.contains(id) && (
                    assessment.path("shouldDeepRead").asBoolean(false)
                        || "HIGH".equalsIgnoreCase(assessment.path("importance").asText(""))
                )) {
                    selectedEvidence.add(id);
                }
                if (allowedSourceEvidence.contains(id)
                    && assessment.path("shouldDeepRead").asBoolean(false)) {
                    deepReads.add(id);
                }
            }
            List<EvalClaim> claims = filterClaimEvidence(
                claims(profile.path("sections")),
                allowedSourceEvidence
            );
            claims.stream().flatMap(value -> value.evidenceRefs().stream()).forEach(selectedEvidence::add);
            List<JsonNode> toolRequestNodes = SemanticScoutService.normalizedToolRequestNodes(scout);
            List<String> tools = toolRequestNodes.stream()
                .map(request -> request.path("capability").asText("").strip())
                .filter(value -> !value.isBlank())
                .map(AnalysisToolRegistry::normalizeCapability)
                .toList();
            for (JsonNode request : toolRequestNodes) {
                String capability = AnalysisToolRegistry.normalizeCapability(
                    request.path("capability").asText("")
                );
                if (Set.of("DOC_READER", "AGENT_RESULT").contains(capability)
                    && validToolRequest(
                        request,
                        eligibility.capabilities(),
                        evidenceIds(testCase.context())
                    )) {
                    deepReads.addAll(texts(request.path("targetEvidenceIds")));
                }
            }
            List<String> views = new ArrayList<>(normalizedEligibleViews(
                texts(scout.path("applicableDimensions")),
                eligibility.views()
            ));
            views.addAll(normalizedEligibleViews(
                objectTexts(profile.path("sections"), "type"),
                eligibility.views()
            ));
            List<String> conflicts = normalizedConflictLabels(scout.path("potentialConflicts"));
            views = new ArrayList<>(distinct(views));
            CapabilityFixtureResult capabilityEvidence = executeFixtureCapability(
                mapper,
                testCase,
                toolRequestNodes,
                eligibility.capabilities()
            );
            List<String> knownTools = new ArrayList<>(validatedTools(
                toolRequestNodes,
                eligibility.capabilities(),
                evidenceIds(testCase.context())
            ));
            knownTools = distinct(knownTools);
            List<String> unavailable = tools.stream()
                .filter(value -> !registeredTool(value) || !eligibility.capabilities().contains(value))
                .toList();
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
            int retries = Math.max(0, response.diagnostics().requestCount() - 1);
            String finishReason = response.diagnostics().finishReason();
            boolean degraded = false;
            boolean validatedToolEvidenceCited = false;
            if (capabilityEvidence != null) {
                long finalStarted = System.nanoTime();
                try {
                    List<String> finalAllowedEvidence = new ArrayList<>(evidenceIds(testCase.context()));
                    finalAllowedEvidence.addAll(capabilityEvidence.toolEvidenceIds());
                    var finalResponse = gateway.callStructured(
                        provider,
                        promptBuilder.buildFinalPrompt(new ProjectUnderstandingPromptBuilder.FinalPromptInput(
                            boundedFinalContext(mapper, root.path("dynamicProfile"), capabilityEvidence),
                            distinct(finalAllowedEvidence),
                            eligibility.views(),
                            capabilityEvidence.toolEvidenceIds()
                        )),
                        ModelTaskType.PROJECT_UNDERSTANDING_FINAL_SYNTHESIS
                    );
                    printSafeStageDiagnostics(testCase.id(), "FINAL", finalResponse.diagnostics());
                    JsonNode finalRoot = finalResponse.parsed().root();
                    Set<String> allowedFinalEvidence = Set.copyOf(finalAllowedEvidence);
                    List<EvalClaim> finalClaims = capabilityEvidence.traceClaimsToSources(
                        filterClaimEvidence(
                            claims(finalRoot.path("dynamicProfile").path("sections")),
                            allowedFinalEvidence
                        )
                    );
                    // The production plan keeps its applicable-dimension
                    // decision across Final; sections are a rendering subset.
                    List<String> finalViews = new ArrayList<>(stageOne.applicableViews());
                    finalViews.addAll(normalizedEligibleViews(
                        objectTexts(finalRoot.path("dynamicProfile").path("sections"), "type"),
                        eligibility.views()
                    ));
                    List<String> finalEvidence = new ArrayList<>();
                    finalClaims.stream().flatMap(value -> value.evidenceRefs().stream()).forEach(finalEvidence::add);
                    finalEvidence.addAll(evidenceRefs(finalRoot.path("conflicts")).stream()
                        .filter(allowedFinalEvidence::contains)
                        .toList());
                    finalEvidence.addAll(evidenceRefs(finalRoot.path("stageTwoChanges")).stream()
                        .filter(allowedFinalEvidence::contains)
                        .toList());
                    Set<String> toolEvidenceIds = Set.copyOf(capabilityEvidence.toolEvidenceIds());
                    validatedToolEvidenceCited = finalEvidence.stream().anyMatch(toolEvidenceIds::contains);
                    List<String> tracedFinalEvidence = capabilityEvidence.traceToSources(finalEvidence);
                    stageTwo = new StageResult(distinct(tracedFinalEvidence), finalClaims, distinct(finalViews));
                    if (!finalClaims.isEmpty()) claims = finalClaims;
                    selectedEvidence.addAll(tracedFinalEvidence);
                    views.addAll(finalViews);
                    modelRequests += Math.max(1, finalResponse.diagnostics().requestCount());
                    inputTokens += finalResponse.diagnostics().promptTokens();
                    outputTokens += finalResponse.diagnostics().completionTokens();
                    totalTokens += finalResponse.diagnostics().totalTokens();
                    stageTwoInputTokens = finalResponse.diagnostics().promptTokens();
                    stageTwoOutputTokens = finalResponse.diagnostics().completionTokens();
                    latencyMs += finalResponse.diagnostics().latencyMs();
                    retries += Math.max(0, finalResponse.diagnostics().requestCount() - 1);
                    finishReason = finalResponse.diagnostics().finishReason();
                } catch (Exception finalFailure) {
                    FailureAccounting failedFinal = failureAccounting(
                        finalFailure,
                        elapsedMs(finalStarted)
                    );
                    modelRequests += failedFinal.requestCount();
                    inputTokens += failedFinal.inputTokens();
                    outputTokens += failedFinal.outputTokens();
                    totalTokens += failedFinal.totalTokens();
                    stageTwoInputTokens = failedFinal.inputTokens();
                    stageTwoOutputTokens = failedFinal.outputTokens();
                    latencyMs += failedFinal.latencyMs();
                    retries += failedFinal.retries();
                    finishReason = failedFinal.failureCategory();
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
                "3.7.5",
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
                capabilityEvidence == null ? 0 : capabilityEvidence.contentChars(),
                validatedToolEvidenceCited,
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
            "3.7.5",
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
            0, // requestCount
            0, // inputTokens
            0, // outputTokens
            0, // totalTokens
            0, // stageOneInputTokens
            0, // stageOneOutputTokens
            0, // stageTwoInputTokens
            0, // stageTwoOutputTokens
            0, // toolEvidenceChars
            false, // validatedToolEvidenceCited
            0, // latencyMs
            0, // retries
            false,
            false,
            "NOT_CALLED",
            "NOT_APPLICABLE",
            "NOT_DEGRADED",
            null,
            "UNAVAILABLE"
        );
    }

    private static String boundedEvalContext(ObjectMapper mapper, EvalCase value) throws Exception {
        var context = mapper.createObjectNode();
        context.put("source", value.source());
        var ledger = context.putObject("evidenceLedger");
        List<String> ids = evidenceIds(value.context());
        ledger.put("coverageMode", ids.size() <= 12 ? "COMPLETE_SMALL_SET" : "BOUNDED_DIVERSE");
        ledger.put("sourceCount", ids.size());
        var items = ledger.putArray("items");
        for (String id : ids) {
            var item = items.addObject();
            item.put("id", id);
            item.put("category", "EVAL_SOURCE");
            item.put("summary", evidenceSentence(value.context(), id));
        }
        context.put("boundedProjectContext", value.context());
        return mapper.writeValueAsString(context);
    }

    private static String evidenceSentence(String context, String evidenceId) {
        if (context == null || context.isBlank()) return "";
        for (String sentence : context.split("(?<=[。；;])\\s*")) {
            if (sentence.contains(evidenceId)) return sentence.strip();
        }
        return context.length() <= 500 ? context.strip() : context.substring(0, 500).strip();
    }

    static String buildScoutPrompt(
        ObjectMapper mapper,
        ProjectUnderstandingPromptBuilder promptBuilder,
        EvalCase value
    ) throws Exception {
        ObjectiveEligibility eligibility = objectiveEligibility(value);
        return promptBuilder.buildScoutPrompt(new ProjectUnderstandingPromptBuilder.ScoutPromptInput(
            boundedEvalContext(mapper, value),
            evidenceIds(value.context()),
            eligibility.capabilities(),
            eligibility.views()
        ));
    }

    private static String boundedFinalContext(
        ObjectMapper mapper,
        JsonNode stageOneProfile,
        CapabilityFixtureResult capabilityEvidence
    ) throws Exception {
        var context = mapper.createObjectNode();
        context.set("stageOneProfile", stageOneProfile);
        context.putPOJO("validatedToolEvidence", capabilityEvidence.promptEvidence());
        context.putPOJO("validatedSourceEvidenceIds", capabilityEvidence.sourceEvidenceIds());
        return mapper.writeValueAsString(context);
    }

    private static CapabilityFixtureResult executeFixtureCapability(
        ObjectMapper mapper,
        EvalCase testCase,
        List<JsonNode> toolRequests,
        List<String> eligibleCapabilities
    ) throws Exception {
        String content = capabilityFixtureContent(mapper, testCase.id());
        if (content.isBlank()) return null;
        List<String> knownEvidenceIds = evidenceIds(testCase.context());
        if (knownEvidenceIds.isEmpty()) return null;

        LinkedHashMap<String, List<String>> executable = new LinkedHashMap<>();
        for (JsonNode request : toolRequests) {
            String capability = AnalysisToolRegistry.normalizeCapability(
                request.path("capability").asText("")
            );
            if (Set.of("DOC_READER", "AGENT_RESULT").contains(capability)
                && validToolRequest(request, eligibleCapabilities, knownEvidenceIds)) {
                executable.putIfAbsent(
                    capability,
                    texts(request.path("targetEvidenceIds")).stream()
                        .filter(knownEvidenceIds::contains)
                        .distinct()
                        .toList()
                );
            }
        }
        for (Map.Entry<String, List<String>> request : executable.entrySet()) {
            String capability = request.getKey();
            List<String> targets = request.getValue();
            if (targets.isEmpty()) continue;

            Path fixtureRoot = Path.of(
                "target",
                "projectflow-eval",
                "capability-fixtures",
                safeFixtureName(testCase.id())
            );
            Files.createDirectories(fixtureRoot);
            Files.writeString(fixtureRoot.resolve("evidence.txt"), content, StandardCharsets.UTF_8);
            String sourceId = targets.get(0);
            String category = "AGENT_RESULT".equals(capability) ? "AGENT_RESULT" : "DOC";
            ProjectEvidenceSourceResponse source = new ProjectEvidenceSourceResponse(
                sourceId,
                category,
                category,
                "evidence.txt",
                category,
                "UNKNOWN",
                "CURRENT",
                "HIGH",
                "PLANNED",
                "独立 Capability fixture",
                List.of(sourceId)
            );
            EvidenceSourceMapResponse sourceMap = new EvidenceSourceMapResponse(
                1,
                1,
                1,
                1,
                0,
                Map.of(category, 1L),
                List.of(source),
                List.of(),
                null
            );
            RepositoryIntakeResponse intake = new RepositoryIntakeResponse(
                "MATERIAL",
                "SMALL",
                true,
                1,
                0,
                content.length(),
                0,
                Map.of(),
                List.of(),
                new GitEvidenceResponse(false, "", "", 0, "UNAVAILABLE", 0),
                0,
                false,
                0,
                0,
                0.5,
                false,
                "fixture",
                "fixture-" + testCase.id(),
                "fixture-" + testCase.id(),
                List.of()
            );
            AdaptiveAnalysisPlanResponse plan = new AdaptiveAnalysisPlanResponse(
                List.of(capability),
                "FIXTURE",
                "TWO_STAGE_CONDITIONAL",
                2,
                0,
                0,
                Long.MAX_VALUE,
                false,
                "UNAVAILABLE",
                0,
                List.of(),
                List.of("真实有界 Provider fixture"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(capability),
                targets,
                "CURRENT_STATE_ONLY",
                "FIXTURE",
                Map.of(),
                List.of(),
                "HIGH",
                eligibleCapabilities,
                List.of(),
                List.of()
            );
            BoundedLocalAnalysisCapabilityProvider provider =
                new BoundedLocalAnalysisCapabilityProvider(
                    (directory, command, timeout) ->
                        new LocalCommandExecutor.CommandResult(1, "", false),
                    new SensitiveContentRedactor()
                );
            var result = provider.execute(new CapabilityRequest(
                capability,
                fixtureRoot,
                intake,
                null,
                sourceMap,
                plan,
                Set.copyOf(knownEvidenceIds),
                new ExecutionBudget(4, 12_000, 32_000, 5_000)
            ));
            if (result.promptEvidence().isEmpty() || result.evidence().isEmpty()) continue;
            var gate = HighValueEvidenceGate.decide(
                plan,
                result.evidence(),
                result.promptEvidence(),
                sourceMap
            );
            if (!gate.secondStageTriggered()) continue;
            Set<String> highValueIds = Set.copyOf(gate.evidenceIds());
            Map<String, List<String>> sourceRefsByTool = new LinkedHashMap<>();
            result.evidence().stream()
                .filter(value -> highValueIds.contains(value.id()))
                .forEach(value ->
                    sourceRefsByTool.put(value.id(), List.copyOf(value.evidenceRefs()))
                );
            return new CapabilityFixtureResult(
                result.promptEvidence().stream()
                    .filter(value -> highValueIds.contains(value.id()))
                    .toList(),
                Map.copyOf(sourceRefsByTool),
                result.consumedChars()
            );
        }
        return null;
    }

    private static String capabilityFixtureContent(ObjectMapper mapper, String caseId) throws Exception {
        String resource = System.getProperty(
            "projectflow.eval.capability-evidence-resource",
            "/projectflow-eval/capability-evidence.json"
        ).strip();
        if (!resource.startsWith("/projectflow-eval/") || !resource.endsWith(".json")) return "";
        try (InputStream input = ProjectFlowRealModelEvalIT.class.getResourceAsStream(resource)) {
            if (input == null) return "";
            return mapper.readTree(input).path(caseId).asText("");
        }
    }

    private static String safeFixtureName(String value) {
        String normalized = value == null ? "" : value.replaceAll("[^A-Za-z0-9._-]", "_");
        return normalized.isBlank() ? "case" : normalized;
    }

    /**
     * Test-fixture equivalent of production eligibility. It uses only observed
     * context/source signals and never reads expected labels or thresholds.
     */
    private static ObjectiveEligibility objectiveEligibility(EvalCase value) {
        String facts = (value.source() + " " + value.context()).toLowerCase(Locale.ROOT);
        if (facts.contains("目录为空") || facts.contains("空白文本")) {
            return new ObjectiveEligibility(List.of(), List.of(), false);
        }
        LinkedHashSet<String> capabilities = new LinkedHashSet<>();
        if (containsAny(
            facts,
            "manifest",
            "pom",
            "package",
            "workspace",
            "依赖",
            "react",
            "java",
            "desktop",
            "前端",
            "后端",
            "migration",
            "infra",
            "repository://"
        )) {
            capabilities.add("MANIFEST");
        }
        if (containsAny(
            facts,
            ".md",
            "文档",
            "readme",
            "roadmap",
            "content map",
            "range ",
            "unread_ranges",
            "无扩展名",
            "文件",
            "portfolio",
            "项目上下文",
            "历史路线",
            "adaptive execution"
        )) {
            capabilities.add("DOC_READER");
        }
        if (containsAny(facts, "agent result", "agent-result")) capabilities.add("AGENT_RESULT");
        boolean gitUnavailable = containsAny(facts, "git unavailable", "没有 git", "无 git");
        boolean hasHistory = !gitUnavailable && containsAny(
            facts,
            "提交",
            "commit",
            "tag",
            "历史",
            "repository://"
        );
        if (hasHistory) capabilities.add("GIT_HISTORY");
        if (hasHistory && containsAny(facts, "tag", "repository://")) capabilities.add("GIT_TAG");
        if (facts.contains("worktree")) capabilities.add("WORKTREE");
        if (facts.contains("scip available")) capabilities.add("SCIP");

        LinkedHashSet<String> views = new LinkedHashSet<>();
        boolean processMetadata = containsAny(facts, "token=", "latency=", "request metadata");
        boolean agentResult = containsAny(facts, "agent result", "agent-result");
        boolean material = containsAny(
            facts,
            ".md",
            "文档",
            "readme",
            "roadmap",
            "content map",
            "range ",
            "unread_ranges",
            "无扩展名",
            "文件",
            "portfolio",
            "agent result",
            "token=",
            "process"
        );
        boolean code = hasHistory || containsAny(
            facts,
            "源码",
            "代码",
            "script",
            "python",
            "react",
            "java",
            "frontend",
            "backend",
            "前端",
            "后端",
            "desktop",
            "workspace",
            "repository://",
            "触发上限"
        );
        if (code) views.addAll(List.of(
            "CURRENT_STATE", "TECHNOLOGY", "CURRENT_STRUCTURE", "ENGINEERING_STATE",
            "PURPOSE", "INPUT_OUTPUT", "DEPENDENCIES", "USAGE", "FRONTEND", "BACKEND",
            "ROUTES", "COMPONENTS", "API_DEPENDENCIES", "API", "SERVICES", "DATA", "AUTH",
            "INTEGRATIONS", "INTEGRATION_RELATIONS", "DESKTOP_RUNTIME", "ENTRY_POINTS",
            "WORKSPACES", "MODULE_BOUNDARIES", "ARCHITECTURE"
        ));
        if (material) views.addAll(List.of(
            "CURRENT_STATE", "DOCUMENT_OVERVIEW", "PROCESS_EVIDENCE", "PROCESS_METADATA", "CURRENTNESS",
            "CONFLICTS", "LIMITATIONS", "UNKNOWN"
        ));
        if (hasHistory) views.addAll(List.of(
            "HISTORICAL_COVERAGE", "LIMITED_HISTORY", "MILESTONE_WINDOWS", "EVOLUTION"
        ));
        if (facts.contains("单文件")) views.remove("ARCHITECTURE");
        if (processMetadata) views.retainAll(List.of("PROCESS_METADATA", "LIMITATIONS", "UNKNOWN"));
        if (agentResult && !code) {
            views.retainAll(List.of(
                "PROCESS_EVIDENCE", "PROCESS_METADATA", "CURRENTNESS", "CONFLICTS",
                "LIMITATIONS", "UNKNOWN"
            ));
        }
        return new ObjectiveEligibility(List.copyOf(capabilities), List.copyOf(views), code);
    }

    private static boolean containsAny(String value, String... markers) {
        for (String marker : markers) {
            if (value.contains(marker)) return true;
        }
        return false;
    }

    private static Set<String> requestedCaseIds() {
        String configured = System.getProperty("projectflow.eval.case-ids", "");
        if (configured.isBlank()) return Set.of();
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String value : configured.split(",")) {
            if (!value.isBlank()) result.add(value.strip());
        }
        return Set.copyOf(result);
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
                    claim.path("epistemicStatus").asText(epistemic),
                    refs,
                    true,
                    false
                ));
            }
        }
        return List.copyOf(result);
    }

    static List<String> normalizedConflictLabels(JsonNode values) {
        List<String> result = new ArrayList<>();
        for (String value : objectTexts(values, "text")) {
            String normalized = value.toUpperCase(Locale.ROOT);
            if (normalized.contains("SQLITE") && normalized.contains("POSTGRESQL")) {
                result.add("DATABASE_DEFAULT_CONFLICT");
            } else if ((normalized.contains("AGENT") || normalized.contains("迁移"))
                && (normalized.contains("CI") || normalized.contains("VERIFICATION"))
                && (normalized.contains("失败") || normalized.contains("FAILED"))) {
                result.add("AGENT_VERIFICATION_CONFLICT");
            } else if (normalized.contains("README")
                && (normalized.contains("冲突")
                    || (normalized.contains("8080") && normalized.contains("9090")))) {
                result.add("README_SOURCE_CONFLICT");
            } else if (normalized.contains("README")
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

    private static void printSafeCapabilityDecisions(String caseId, JsonNode scout) {
        List<String> decisions = new ArrayList<>();
        for (JsonNode item : array(scout.path("capabilityDecisions"))) {
            String capability = AnalysisToolRegistry.normalizeCapability(
                item.path("capability").asText("")
            );
            String decision = item.path("decision").asText("").strip().toUpperCase(Locale.ROOT);
            if (!capability.isBlank() && Set.of("REQUEST", "SKIP").contains(decision)) {
                decisions.add(capability + "=" + decision);
            }
        }
        System.out.println(
            "REAL_EVAL_CAPABILITY_DECISIONS case=" + caseId + " values=" + distinct(decisions)
        );
    }

    private static List<String> evidenceRefs(JsonNode values) {
        List<String> result = new ArrayList<>();
        for (JsonNode value : array(values)) result.addAll(texts(value.path("evidenceRefs")));
        return distinct(result);
    }

    private static List<String> validatedTools(
        List<JsonNode> requests,
        List<String> eligibleCapabilities,
        List<String> allowedEvidenceIds
    ) {
        List<String> result = new ArrayList<>();
        for (JsonNode request : requests) {
            String capability = AnalysisToolRegistry.normalizeCapability(
                request.path("capability").asText("")
            );
            if (registeredTool(capability)
                && validToolRequest(request, eligibleCapabilities, allowedEvidenceIds)) {
                result.add(capability);
            }
        }
        return distinct(result);
    }

    private static boolean validToolRequest(
        JsonNode request,
        List<String> eligibleCapabilities,
        List<String> allowedEvidenceIds
    ) {
        String capability = AnalysisToolRegistry.normalizeCapability(
            request.path("capability").asText("")
        );
        return eligibleCapabilities.contains(capability)
            && !request.path("informationGap").asText("").isBlank()
            && !request.path("expectedEvidenceValue").asText("").isBlank()
            && !request.path("whyExistingEvidenceIsInsufficient").asText("").isBlank()
            && texts(request.path("targetEvidenceIds")).stream().anyMatch(allowedEvidenceIds::contains);
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

    private static List<String> normalizedEligibleViews(List<String> values, List<String> eligible) {
        return values.stream()
            .map(AnalysisViewRegistry::normalize)
            .filter(eligible::contains)
            .distinct()
            .toList();
    }

    private static boolean registeredTool(String value) {
        if (value == null) return false;
        return switch (normalized(value)) {
            case "DOC_READER", "MANIFEST", "AGENT_RESULT", "GIT_HISTORY", "GIT_TAG",
                "WORKTREE", "FILESYSTEM", "SCIP" -> true;
            default -> false;
        };
    }

    private static String normalized(String value) {
        return value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
    }

    private static ProjectFlowEvalObservation failedObservation(
        ProviderConfig config,
        EvalCase testCase,
        int run,
        long latencyMs,
        Exception failure
    ) {
        FailureAccounting accounting = failureAccounting(failure, latencyMs);
        return new ProjectFlowEvalObservation(
            testCase.id(),
            testCase.id() + "-real-" + run,
            PROMPT_VERSION,
            "3.7.5",
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
            accounting.requestCount(),
            accounting.inputTokens(),
            accounting.outputTokens(),
            accounting.totalTokens(),
            accounting.inputTokens(),
            accounting.outputTokens(),
            0,
            0,
            0,
            false,
            accounting.latencyMs(),
            accounting.retries(),
            true,
            true,
            accounting.failureCategory(),
            "FAILED",
            "DEGRADED_NO_SEMANTIC_RESULT:" + accounting.failureCategory(),
            null,
            "UNAVAILABLE"
        );
    }

    private static void printSafeStageDiagnostics(
        String caseId,
        String stage,
        ModelGatewayService.ModelCallDiagnostics diagnostics
    ) {
        System.out.printf(
            "REAL_EVAL_STAGE case=%s stage=%s requests=%d recovery=%s finish=%s schema=%s%n",
            caseId,
            stage,
            diagnostics.requestCount(),
            diagnostics.retryType(),
            diagnostics.finishReason(),
            diagnostics.schemaMatched()
        );
    }

    private static int failedRequestCount(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof ModelGatewayService.ModelTransportException transport) {
                return transport.requestCount();
            }
            if (current instanceof ModelGatewayService.ModelResponseFormatException format
                && format.diagnostics() != null) {
                return Math.max(1, format.diagnostics().requestCount());
            }
            current = current.getCause();
        }
        return 1;
    }

    private static FailureAccounting failureAccounting(Exception failure, long measuredLatencyMs) {
        ModelGatewayService.ModelCallDiagnostics diagnostics = failedDiagnostics(failure);
        int requestCount = failedRequestCount(failure);
        if (diagnostics == null) {
            return new FailureAccounting(
                requestCount,
                0,
                0,
                0,
                measuredLatencyMs,
                Math.max(0, requestCount - 1),
                ModelFailureClassifier.classifyException(failure) + ":" + safeExceptionTypes(failure)
            );
        }
        return new FailureAccounting(
            Math.max(requestCount, diagnostics.requestCount()),
            diagnostics.promptTokens(),
            diagnostics.completionTokens(),
            diagnostics.totalTokens(),
            Math.max(measuredLatencyMs, diagnostics.latencyMs()),
            Math.max(0, Math.max(requestCount, diagnostics.requestCount()) - 1),
            ModelFailureClassifier.classifyException(failure)
                + ":"
                + safeExceptionTypes(failure)
                + safeFailureCode(diagnostics)
        );
    }

    private static String safeFailureCode(ModelGatewayService.ModelCallDiagnostics diagnostics) {
        return diagnostics.failureCode() == null || diagnostics.failureCode().isBlank()
            ? ""
            : ":" + diagnostics.failureCode();
    }

    private static ModelGatewayService.ModelCallDiagnostics failedDiagnostics(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof ModelGatewayService.ModelResponseFormatException format
                && format.diagnostics() != null) {
                return format.diagnostics();
            }
            current = current.getCause();
        }
        return null;
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
            integerEnvironment("PROJECTFLOW_REAL_MODEL_MAX_TOKENS", 16_000),
            nullableBooleanEnvironment("PROJECTFLOW_REAL_MODEL_SUPPORTS_REASONING"),
            booleanEnvironment("PROJECTFLOW_REAL_MODEL_SUPPORTS_REASONING_CONTROL", false)
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

    private static boolean booleanEnvironment(String name, boolean fallback) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) return fallback;
        return Boolean.parseBoolean(value.strip());
    }

    private static Boolean nullableBooleanEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) return null;
        return Boolean.valueOf(value.strip());
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
                       coalesce(request_timeout_seconds, 120), max_tokens,
                       supports_reasoning,
                       coalesce(supports_reasoning_control, false)
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
                Math.max(4_000, result.getInt(8)),
                (Boolean) result.getObject(9),
                result.getBoolean(10)
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

    static List<EvalClaim> filterClaimEvidence(
        List<EvalClaim> claims,
        Set<String> allowedEvidence
    ) {
        return claims.stream().map(value -> new EvalClaim(
            value.text(),
            value.claimType(),
            value.epistemicStatus(),
            value.evidenceRefs().stream()
                .filter(allowedEvidence::contains)
                .distinct()
                .toList(),
            value.manualReviewRequired(),
            value.manuallyUnsupported()
        )).toList();
    }

    private static List<String> invalidEvidenceRefs(
        List<ProjectFlowEvalObservation> observations,
        ProjectFlowEvalGroundTruth groundTruth
    ) {
        Map<String, Set<String>> allowedByCase = groundTruth.cases().stream().collect(
            java.util.stream.Collectors.toMap(
                EvalCase::id,
                value -> Set.copyOf(evidenceIds(value.context()))
            )
        );
        List<String> invalid = new ArrayList<>();
        for (ProjectFlowEvalObservation observation : observations) {
            Set<String> allowed = allowedByCase.getOrDefault(observation.caseId(), Set.of());
            observation.evidenceUsed().stream()
                .filter(value -> !allowed.contains(value))
                .forEach(value -> invalid.add(observation.caseId() + ":" + value));
            observation.claims().stream()
                .flatMap(value -> value.evidenceRefs().stream())
                .filter(value -> !allowed.contains(value))
                .forEach(value -> invalid.add(observation.caseId() + ":" + value));
        }
        return distinct(invalid);
    }

    record ProviderConfig(
        String name,
        String baseUrl,
        String apiKey,
        String model,
        AiProviderType type,
        ModelProtocol protocol,
        int timeoutSeconds,
        int maxTokens,
        Boolean supportsReasoning,
        boolean supportsReasoningControl
    ) {
    }

    private record FailureAccounting(
        int requestCount,
        int inputTokens,
        int outputTokens,
        int totalTokens,
        long latencyMs,
        int retries,
        String failureCategory
    ) {
    }

    private record ObjectiveEligibility(
        List<String> capabilities,
        List<String> views,
        boolean codeEvidenceAvailable
    ) {
    }

    private record CapabilityFixtureResult(
        List<PromptEvidence> promptEvidence,
        Map<String, List<String>> sourceRefsByTool,
        int contentChars
    ) {
        private List<String> toolEvidenceIds() {
            return List.copyOf(sourceRefsByTool.keySet());
        }

        private List<String> sourceEvidenceIds() {
            return sourceRefsByTool.values().stream()
                .flatMap(List::stream)
                .distinct()
                .toList();
        }

        private List<String> traceToSources(List<String> evidenceRefs) {
            LinkedHashSet<String> traced = new LinkedHashSet<>();
            for (String evidenceRef : evidenceRefs) {
                List<String> sourceRefs = sourceRefsByTool.get(evidenceRef);
                if (sourceRefs == null || sourceRefs.isEmpty()) traced.add(evidenceRef);
                else traced.addAll(sourceRefs);
            }
            return List.copyOf(traced);
        }

        private List<EvalClaim> traceClaimsToSources(List<EvalClaim> claims) {
            return claims.stream().map(value -> new EvalClaim(
                value.text(),
                value.claimType(),
                value.epistemicStatus(),
                traceToSources(value.evidenceRefs()),
                value.manualReviewRequired(),
                value.manuallyUnsupported()
            )).toList();
        }
    }
}
