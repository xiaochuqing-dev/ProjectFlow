package com.projectflow.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.projectflow.dto.ProjectUnderstandingDtos.AdaptiveAnalysisPlanResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.EvidenceSourceAssessment;
import com.projectflow.dto.ProjectUnderstandingDtos.EvidenceSourceMapResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.HistoricalCoverageResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.ProjectEvidenceSourceResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.ProjectShapeHypothesis;
import com.projectflow.dto.ProjectUnderstandingDtos.ProjectStructureIndexResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.RepositoryIntakeResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.SemanticScoutResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.SemanticContractDiagnostics;
import com.projectflow.dto.ProjectUnderstandingDtos.SemanticToolRequest;
import com.projectflow.dto.ProjectUnderstandingDtos.ToolSelectionRationale;

@Service
public class AdaptiveAnalysisPlanner {
    private final AnalysisToolRegistry toolRegistry;
    private final AnalysisViewRegistry viewRegistry;

    @Autowired
    public AdaptiveAnalysisPlanner(AnalysisToolRegistry toolRegistry, AnalysisViewRegistry viewRegistry) {
        this.toolRegistry = toolRegistry;
        this.viewRegistry = viewRegistry;
    }

    /** Compatibility constructor for focused tests. */
    public AdaptiveAnalysisPlanner(AnalysisToolRegistry toolRegistry) {
        this(toolRegistry, new AnalysisViewRegistry());
    }

    public boolean shouldUseSemanticModel(
        RepositoryIntakeResponse intake,
        EvidenceSourceMapResponse sourceMap
    ) {
        if ("EMPTY".equals(intake.classification())) return false;
        return intake.sourceFileCount() > 0 || sourceMap.scoutEvidenceCount() > 0;
    }

    public SemanticScoutResponse deterministicScout(
        RepositoryIntakeResponse intake,
        EvidenceSourceMapResponse sourceMap
    ) {
        List<ProjectShapeHypothesis> shapes = new ArrayList<>();
        if ("EMPTY".equals(intake.classification())) {
            shapes.add(new ProjectShapeHypothesis("EMPTY", "HIGH", List.of("intake:scan"), "目录中没有文件"));
        } else if (intake.sourceFileCount() == 0 && sourceMap.scoutEvidenceCount() > 0) {
            shapes.add(new ProjectShapeHypothesis(
                "DOCUMENT_PROJECT",
                "MEDIUM",
                documentRefs(sourceMap),
                "发现有内容的文本或文档候选，但没有源码证据"
            ));
        } else if (intake.sourceFileCount() <= 2 && intake.estimatedLoc() <= 500) {
            shapes.add(new ProjectShapeHypothesis(
                "SCRIPT_OR_SMALL_CODE",
                "MEDIUM",
                List.of("intake:scan"),
                "源码规模很小，避免套用多层软件架构"
            ));
        } else if (intake.sourceFileCount() > 0) {
            shapes.add(new ProjectShapeHypothesis(
                "SOFTWARE_PROJECT",
                "MEDIUM",
                List.of("intake:scan"),
                "存在源码；更具体形态需语义证据支持"
            ));
        }
        if (intake.monorepo()) {
            shapes.add(new ProjectShapeHypothesis(
                "MONOREPO",
                "HIGH",
                List.of("intake:scan"),
                "workspace manifest 提供多工作区证据"
            ));
        }
        List<EvidenceSourceAssessment> assessments = sourceMap.sources().stream()
            .filter(source -> source.id().startsWith("source:"))
            .limit(80)
            .map(source -> new EvidenceSourceAssessment(
                source.id(),
                source.semanticRole(),
                "UNKNOWN",
                source.currentness(),
                false,
                false,
                "工程系统只建立候选和安全边界，不预判语义重要性",
                "",
                List.of(),
                source.confidence()
            ))
            .toList();
        return new SemanticScoutResponse(
            List.copyOf(shapes),
            assessments,
            defaultDimensions(intake, false),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            false,
            new SemanticContractDiagnostics("NOT_APPLICABLE", List.of(), List.of(), List.of(), List.of())
        );
    }

    public AdaptiveAnalysisPlanResponse plan(
        RepositoryIntakeResponse intake,
        ProjectStructureIndexResponse index,
        EvidenceSourceMapResponse sourceMap,
        HistoricalCoverageResponse history,
        SemanticScoutResponse scout,
        boolean providerConfigured
    ) {
        boolean semanticEligible = providerConfigured && shouldUseSemanticModel(intake, sourceMap);
        boolean hierarchical = "LARGE".equals(intake.scale())
            || "HUGE".equals(intake.scale())
            || "MONOREPO".equals(intake.scale())
            || "HUGE_MONOREPO".equals(intake.classification());
        String semanticMode;
        if ("EMPTY".equals(intake.classification())) semanticMode = "SKIPPED_EMPTY";
        else if (!shouldUseSemanticModel(intake, sourceMap)) semanticMode = "SKIPPED_NO_SUBSTANTIVE_EVIDENCE";
        else if (!providerConfigured) semanticMode = "UNAVAILABLE";
        else semanticMode = "PENDING_EXECUTION_DECISION";

        List<String> eligibleCapabilities = toolRegistry.eligibleCapabilities(intake, index, sourceMap);
        List<String> eligibleViews = viewRegistry.eligible(intake, index, sourceMap, history);
        List<SemanticToolRequest> semanticRequests = scout == null || scout.toolRequests() == null
            ? List.of()
            : scout.toolRequests();
        List<String> shapes = scout == null ? List.of() : scout.projectShapeHypotheses().stream()
            .map(ProjectShapeHypothesis::shape)
            .distinct()
            .limit(8)
            .toList();
        List<String> defaultTools = toolRegistry.defaults(intake, index, sourceMap, history);
        List<String> requestedTools = semanticRequests.stream().map(SemanticToolRequest::capability).toList();
        List<String> tools = toolRegistry.validateRequested(
            requestedTools,
            defaultTools,
            intake,
            index,
            sourceMap
        );
        boolean secondStageEligible = semanticEligible && tools.stream().anyMatch(Set.of(
            "DOC_READER", "GIT_HISTORY", "GIT_TAG", "WORKTREE", "MANIFEST", "AGENT_RESULT"
        )::contains);
        if ("PENDING_EXECUTION_DECISION".equals(semanticMode)) {
            semanticMode = secondStageEligible ? "TWO_STAGE_CONDITIONAL" : "ONE_PASS_SCOUT_AND_SYNTHESIS";
        }
        List<String> selectedDimensions = scout == null || !scout.modelUsed()
            ? viewRegistry.validate(defaultDimensions(intake, history.historyAvailable()), eligibleViews)
            : viewRegistry.validate(scout.applicableDimensions(), eligibleViews);
        List<String> dimensions = List.copyOf(new LinkedHashSet<>(selectedDimensions));
        List<String> skippedDimensions = skippedDimensions(intake, index, history, dimensions);
        List<String> unavailable = new ArrayList<>(index.unsupportedAreas());
        unavailable.addAll(toolRegistry.unavailableReasons(intake, index));
        if (!providerConfigured && shouldUseSemanticModel(intake, sourceMap)) {
            unavailable.add("没有可用默认模型，项目形态和材料语义保持有限理解");
        }

        List<String> reasons = new ArrayList<>();
        reasons.add("Evidence Discovery 与结构索引先建立边界，Scout 只输出 capability intent，再由工程 Provider 执行固定参数工具");
        if (secondStageEligible) {
            reasons.add("只有工具产生新的高价值 Evidence 时才进行第二阶段 Final Synthesis；没有新增证据仍保持一次模型调用");
        }
        reasons.add(hierarchical ? "规模较大，只发送压缩模块、重要节点和少量来源样本" : "规模可控，仍执行固定上限的来源采样");
        if (!intake.git().available()) reasons.add("没有 Git 时只理解当前状态，不生成虚假 Timeline");
        if (intake.sourceFileCount() == 0 && sourceMap.scoutEvidenceCount() > 0) {
            reasons.add("非代码材料仍可进行一次有界语义理解，但不调用代码架构工具");
        }
        Map<String, Integer> budgets = new LinkedHashMap<>();
        budgets.put("scoutInputTokens", semanticEligible ? 8_000 : 0);
        budgets.put("plannerInputTokens", 0);
        budgets.put("deepReadChars", semanticEligible ? 32_000 : 0);
        budgets.put("synthesisInputTokens", secondStageEligible ? 10_000 : 0);
        budgets.put("synthesisOutputTokens", semanticEligible ? 5_000 : 0);
        budgets.put("evolutionCandidateWindows", history.historyAvailable() ? 15 : 0);

        List<String> deterministicCapabilities = index.symbols().isEmpty()
            ? List.of("有界目录盘点", "Evidence Source Map", "语言与 LOC 统计", "manifest/workspace 识别", "工程信号", "证据编号")
            : List.of(
                "有界目录盘点", "Evidence Source Map", "语言与 LOC 统计", "SCIP Symbol/Definition/Reference",
                "JGraphT 重要节点排序", "关系驱动 Functional Area", "证据编号"
            );
        List<String> priorities = (scout == null ? java.util.stream.Stream.<EvidenceSourceAssessment>empty()
            : scout.evidenceSourceAssessments().stream())
            .filter(source -> "HIGH".equals(source.importance()))
            .map(EvidenceSourceAssessment::evidenceId)
            .limit(30)
            .toList();
        Set<String> requestedDeepReads = new LinkedHashSet<>();
        semanticRequests.stream()
            .filter(request -> "DOC_READER".equals(request.capability()))
            .flatMap(request -> request.targetEvidenceIds().stream())
            .forEach(requestedDeepReads::add);
        if (scout != null) {
            scout.evidenceSourceAssessments().stream()
                .filter(EvidenceSourceAssessment::shouldDeepRead)
                .map(EvidenceSourceAssessment::evidenceId)
                .forEach(requestedDeepReads::add);
        }
        if (requestedDeepReads.isEmpty() && (
            (scout == null || !scout.modelUsed()) || tools.contains("DOC_READER")
        )) {
            requestedDeepReads = sourceMap.sources().stream()
                .filter(source -> isDocumentCategory(source.category()))
                .map(ProjectEvidenceSourceResponse::id)
                .limit(8)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        }
        Set<String> deepReadIds = Set.copyOf(requestedDeepReads);
        List<String> deepReadTargets = sourceMap.sources().stream()
            .filter(source -> deepReadIds.contains(source.id()))
            .map(ProjectEvidenceSourceResponse::id)
            .limit(10)
            .toList();
        List<String> expectedOutputs = new ArrayList<>();
        if (!"EMPTY".equals(intake.classification())) expectedOutputs.add("dynamicProjectProfile");
        expectedOutputs.add("evidenceSourceMap");
        expectedOutputs.add("analysisPlan");
        expectedOutputs.add("historicalCoverage");
        if (history.historyAvailable()) expectedOutputs.add("evolutionPreview");
        List<ToolSelectionRationale> toolRationales = new ArrayList<>(semanticRequests.stream()
            .map(request -> new ToolSelectionRationale(
                request.capability(),
                request.informationGap(),
                request.expectedEvidenceValue(),
                request.targetEvidenceIds(),
                request.whyExistingEvidenceIsInsufficient(),
                eligibleCapabilities.contains(request.capability())
            ))
            .toList());
        return new AdaptiveAnalysisPlanResponse(
            deterministicCapabilities,
            index.indexerSource(),
            semanticMode,
            semanticEligible ? (secondStageEligible ? 2 : 1) : 0,
            semanticEligible ? 12_000 : 0,
            semanticEligible ? (secondStageEligible ? 28_000 : 20_000) : 0,
            AnalysisTimePolicy.NO_OVERALL_DEADLINE_MS,
            hierarchical,
            history.availability(),
            Math.min(index.coverage().overall(), Math.max(intake.supportedStructureCoverage(), 0)),
            List.copyOf(new LinkedHashSet<>(unavailable)),
            List.copyOf(reasons),
            shapes,
            dimensions,
            skippedDimensions,
            priorities,
            tools,
            deepReadTargets,
            history.historyAvailable() ? "MILESTONE_CANDIDATES_ONLY" : "CURRENT_STATE_ONLY",
            index.symbols().isEmpty() ? "MANIFEST_FILESYSTEM_FALLBACK" : "PRECISE_SCIP_WITH_FALLBACK",
            Map.copyOf(budgets),
            List.copyOf(expectedOutputs),
            scout != null && scout.modelUsed() ? "HIGH" : "MEDIUM",
            eligibleCapabilities,
            eligibleViews,
            List.copyOf(toolRationales)
        );
    }

    private static List<String> defaultDimensions(RepositoryIntakeResponse intake, boolean historyAvailable) {
        List<String> values = new ArrayList<>();
        if ("EMPTY".equals(intake.classification())) return List.of();
        values.add("CURRENT_STATE");
        if (intake.sourceFileCount() == 0) {
            values.add("DOCUMENT_OVERVIEW");
            values.add("CURRENTNESS");
            values.add("CONFLICTS");
        } else if (intake.sourceFileCount() <= 2 && intake.estimatedLoc() <= 500) {
            values.add("PURPOSE");
            values.add("INPUT_OUTPUT");
            values.add("DEPENDENCIES");
            values.add("USAGE");
        } else {
            values.add("TECHNOLOGY");
            values.add("CURRENT_STRUCTURE");
            values.add("ENGINEERING_STATE");
        }
        if (historyAvailable) {
            values.add("HISTORICAL_COVERAGE");
            values.add("EVOLUTION");
        }
        return List.copyOf(values);
    }

    private static List<String> skippedDimensions(
        RepositoryIntakeResponse intake,
        ProjectStructureIndexResponse index,
        HistoricalCoverageResponse history,
        List<String> applicable
    ) {
        List<String> skipped = new ArrayList<>();
        if (intake.sourceFileCount() == 0) {
            skipped.add("codeArchitecture：没有源码证据");
            skipped.add("symbolGraph：没有源码证据");
        } else if (index.symbols().isEmpty()) {
            skipped.add("preciseCallGraph：没有有效 SCIP，不能把目录邻近当代码关系");
        }
        if (!history.historyAvailable()) skipped.add("timeline：历史证据不足");
        if (!applicable.contains("BACKEND")) skipped.add("backend：当前证据未证明适用");
        if (!applicable.contains("DATA")) skipped.add("database：当前证据未证明适用");
        return List.copyOf(skipped);
    }

    private static List<String> documentRefs(EvidenceSourceMapResponse sourceMap) {
        List<String> refs = sourceMap.sources().stream()
            .filter(source -> Set.of(
                "DOC", "README", "ADR", "PRODUCT_CONTEXT", "AGENT_CONTEXT",
                "AGENT_RESULT", "CHANGELOG", "UNKNOWN_DOCUMENT"
            ).contains(source.category()))
            .map(ProjectEvidenceSourceResponse::id)
            .limit(8)
            .toList();
        return refs.isEmpty() ? List.of("intake:scan") : refs;
    }

    private static boolean isDocumentCategory(String category) {
        return Set.of(
            "DOC", "README", "ADR", "PRODUCT_CONTEXT", "AGENT_CONTEXT",
            "AGENT_RESULT", "CHANGELOG", "UNKNOWN_DOCUMENT"
        ).contains(category);
    }

}
