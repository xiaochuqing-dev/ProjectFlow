package com.projectflow.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
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
    private final ModelGatewayService modelGateway;
    private final BudgetAwareContextPacker contextPacker;

    @Value("${projectflow.understanding.max-model-prompt-chars:48000}")
    private int maxModelPromptChars;

    public FinalProfileSynthesisService(
        ModelGatewayService modelGateway,
        BudgetAwareContextPacker contextPacker
    ) {
        this.modelGateway = modelGateway;
        this.contextPacker = contextPacker;
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
        String prompt = """
            你是 ProjectFlow 的 Final Synthesis 阶段。Semantic Scout 已经完成，工程系统也已按 capability
            allow-list 执行固定参数工具。现在只能使用给定 evidence id，把新增 Tool Evidence 与原有结构、
            历史覆盖和 Scout 判断合成最终 Dynamic Project Profile。

            不得发明未执行工具、源码关系、数据库、前后端、Release、Timeline 或成熟阶段。没有证据的 Section
            必须省略。README 与代码或工作树冲突时保持 unknown/currentness warning。不得输出绝对路径、凭证、
            原始推理、下一步计划或优先级。

            只返回 JSON：
            {
              "dynamicProfile":{
                "summary":"",
                "sections":[{"id":"","type":"","title":"","summary":"",
                  "claims":[{"text":"","confidence":"HIGH|MEDIUM|LOW","evidenceRefs":["id"]}],
                  "confidence":"HIGH|MEDIUM|LOW","epistemicStatus":"INFERRED","displayPriority":50,
                  "applicabilityReason":""}]
              },
              "unknowns":[]
            }
            最多 12 个 Section，每个 Section 最多 10 条 claim；每条 claim 至少一个真实 evidence id。
            完整合法的有界上下文：
            """ + packed.json();
        ModelGatewayService.StructuredModelResponse response = modelGateway.callStructured(
            provider,
            prompt,
            ModelTaskType.PROJECT_UNDERSTANDING_SNAPSHOT
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
