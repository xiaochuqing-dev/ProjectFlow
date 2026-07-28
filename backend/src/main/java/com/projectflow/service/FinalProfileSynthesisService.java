package com.projectflow.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.projectflow.dto.ProjectUnderstandingDtos.AdaptiveAnalysisPlanResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.ContextPackingDiagnostics;
import com.projectflow.dto.ProjectUnderstandingDtos.HistoricalCoverageResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.ProjectStructureIndexResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.RepositoryIntakeResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.SemanticScoutResponse;
import com.projectflow.entity.AiProvider;
import com.projectflow.entity.ProjectSpace;
import com.projectflow.service.AnalysisExecutionCoordinator.ExecutionOutcome;
import com.projectflow.service.ProjectEvidenceDiscoveryService.PromptEvidence;

@Service
public class FinalProfileSynthesisService {
    public static final String PROMPT_VERSION = ProjectUnderstandingPromptBuilder.FINAL_PROMPT_VERSION;

    private final ModelGatewayService modelGateway;
    private final BudgetAwareContextPacker contextPacker;
    private final ProjectUnderstandingPromptBuilder promptBuilder;

    @Value("${projectflow.understanding.max-model-prompt-chars:48000}")
    private int maxModelPromptChars;

    @Autowired
    public FinalProfileSynthesisService(
        ModelGatewayService modelGateway,
        BudgetAwareContextPacker contextPacker,
        ProjectUnderstandingPromptBuilder promptBuilder
    ) {
        this.modelGateway = modelGateway;
        this.contextPacker = contextPacker;
        this.promptBuilder = promptBuilder;
    }

    /** Compatibility constructor for focused tests. */
    public FinalProfileSynthesisService(
        ModelGatewayService modelGateway,
        BudgetAwareContextPacker contextPacker
    ) {
        this(modelGateway, contextPacker, new ProjectUnderstandingPromptBuilder());
    }

    public SynthesisResult synthesize(
        AiProvider provider,
        ProjectSpace project,
        RepositoryIntakeResponse intake,
        ProjectStructureIndexResponse index,
        HistoricalCoverageResponse history,
        SemanticScoutResponse scout,
        AdaptiveAnalysisPlanResponse plan,
        JsonNode stageOneRoot,
        ExecutionOutcome execution
    ) throws Exception {
        long started = System.nanoTime();
        Map<String, Object> sections = new LinkedHashMap<>();
        sections.put("projectIntake", Map.of(
            "projectName", bounded(project.getName(), 200),
            "classification", intake.classification(),
            "scale", intake.scale(),
            "sourceFileCount", intake.sourceFileCount(),
            "estimatedLoc", intake.estimatedLoc(),
            "languages", intake.languageDistribution()
        ));
        sections.put("manifests", intake.manifestFiles().stream().limit(80).toList());
        sections.put("structure", Map.of(
            "modules", index.modules().stream().limit(80).toList(),
            "entryPoints", index.entryPoints().stream().limit(40).toList(),
            "importantNodes", index.importantNodes().stream().limit(30).toList(),
            "functionalAreas", index.functionalAreas().stream().limit(40).toList(),
            "coverage", index.coverage()
        ));
        sections.put("git", Map.of(
            "available", intake.git().available(),
            "commitCount", intake.git().commitCount(),
            "tagCount", history.tagCount(),
            "worktreeState", intake.git().worktreeState()
        ));
        sections.put("historicalCoverage", history);
        sections.put("unknownsAndConflicts", Map.of(
            "scout", scout,
            "planDimensions", plan.applicableDimensions(),
            "stageOneDynamicProfile", stageOneRoot.path("dynamicProfile")
        ));
        sections.put("toolResults", execution.promptEvidence().stream()
            .limit(30)
            .map(FinalProfileSynthesisService::promptEvidence)
            .toList());
        BudgetAwareContextPacker.PackedContext packed = contextPacker.pack(
            sections,
            Math.max(8_000, maxModelPromptChars - 3_000)
        );
        String prompt = promptBuilder.buildFinalPrompt(
            new ProjectUnderstandingPromptBuilder.FinalPromptInput(
                packed.json(),
                execution.allowedEvidence().stream().sorted().toList(),
                plan.eligibleViews(),
                execution.response().secondStageDecision().evidenceIds()
            )
        );
        ModelGatewayService.StructuredModelResponse response = modelGateway.callStructured(
            provider,
            prompt,
            ModelTaskType.PROJECT_UNDERSTANDING_FINAL_SYNTHESIS
        );
        JsonNode root = response.parsed().root();
        return new SynthesisResult(
            root,
            response.diagnostics(),
            elapsedMs(started),
            packed.diagnostics(),
            containsUnknownEvidence(root, execution.allowedEvidence())
        );
    }

    private static Map<String, Object> promptEvidence(PromptEvidence evidence) {
        return Map.of(
            "id", evidence.id(),
            "category", evidence.category(),
            "sourceType", evidence.sourceType(),
            "locator", evidence.locator(),
            "summary", evidence.summary(),
            "boundedContent", evidence.boundedSample()
        );
    }

    private static boolean containsUnknownEvidence(JsonNode root, Set<String> allowed) {
        return root.findValues("evidenceRefs").stream()
            .filter(JsonNode::isArray)
            .flatMap(node -> {
                List<JsonNode> values = new ArrayList<>();
                node.forEach(values::add);
                return values.stream();
            })
            .map(JsonNode::asText)
            .anyMatch(ref -> !allowed.contains(ref));
    }

    private static String bounded(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static long elapsedMs(long started) {
        return Math.max(0, (System.nanoTime() - started) / 1_000_000);
    }

    public record SynthesisResult(
        JsonNode root,
        ModelGatewayService.ModelCallDiagnostics diagnostics,
        long durationMs,
        ContextPackingDiagnostics contextPacking,
        boolean invalidEvidenceFiltered
    ) {
    }
}
