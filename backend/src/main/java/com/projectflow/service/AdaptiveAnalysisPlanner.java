package com.projectflow.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

@Service
public class AdaptiveAnalysisPlanner {
    private final AnalysisToolRegistry toolRegistry;

    public AdaptiveAnalysisPlanner(AnalysisToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
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
                source.importance(),
                source.currentness(),
                isDocumentCategory(source.category())
                    && ("HIGH".equals(source.importance()) || "UNKNOWN_DOCUMENT".equals(source.category())),
                false,
                "确定性 Discovery 候选，最终语义角色保持可替换",
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
            false
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

        List<String> defaultTools = toolRegistry.defaults(intake, index, sourceMap, history);
        List<String> requestedTools = new ArrayList<>(
            scout == null ? List.of() : scout.recommendedToolCalls()
        );
        if (scout != null && scout.evidenceSourceAssessments().stream()
            .anyMatch(EvidenceSourceAssessment::shouldDeepRead)) {
            requestedTools.add("DOC_READER");
        }
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
        List<String> dimensions = scout == null || scout.applicableDimensions().isEmpty()
            ? defaultDimensions(intake, history.historyAvailable())
            : boundedDistinct(scout.applicableDimensions(), 20);
        if (history.historyAvailable() && !dimensions.contains("evolution")) {
            dimensions = append(dimensions, "evolution");
        }
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
        List<String> shapes = scout == null ? List.of() : scout.projectShapeHypotheses().stream()
            .map(ProjectShapeHypothesis::shape)
            .distinct()
            .limit(8)
            .toList();
        List<String> priorities = sourceMap.sources().stream()
            .filter(source -> "HIGH".equals(source.importance()))
            .map(ProjectEvidenceSourceResponse::id)
            .limit(30)
            .toList();
        Set<String> requestedDeepReads = scout == null ? Set.of() : scout.evidenceSourceAssessments().stream()
            .filter(EvidenceSourceAssessment::shouldDeepRead)
            .map(EvidenceSourceAssessment::evidenceId)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (requestedDeepReads.isEmpty()) {
            requestedDeepReads = sourceMap.sources().stream()
                .filter(source -> isDocumentCategory(source.category()))
                .filter(source -> "HIGH".equals(source.importance()) || "UNKNOWN_DOCUMENT".equals(source.category()))
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
        return new AdaptiveAnalysisPlanResponse(
            deterministicCapabilities,
            index.indexerSource(),
            semanticMode,
            semanticEligible ? (secondStageEligible ? 2 : 1) : 0,
            semanticEligible ? 12_000 : 0,
            semanticEligible ? (secondStageEligible ? 28_000 : 20_000) : 0,
            semanticEligible ? 600_000L : 120_000L,
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
            scout != null && scout.modelUsed() ? "HIGH" : "MEDIUM"
        );
    }

    private static List<String> defaultDimensions(RepositoryIntakeResponse intake, boolean historyAvailable) {
        List<String> values = new ArrayList<>();
        if ("EMPTY".equals(intake.classification())) return List.of();
        values.add("identity");
        if (intake.sourceFileCount() == 0) {
            values.add("documentPurpose");
            values.add("topicsAndDecisions");
        } else if (intake.sourceFileCount() <= 2 && intake.estimatedLoc() <= 500) {
            values.add("purpose");
            values.add("inputOutput");
            values.add("dependencies");
            values.add("usage");
        } else {
            values.add("technology");
            values.add("currentStructure");
            values.add("engineeringState");
        }
        if (historyAvailable) values.add("evolution");
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
        if (!applicable.contains("backend")) skipped.add("backend：当前证据未证明适用");
        if (!applicable.contains("database")) skipped.add("database：当前证据未证明适用");
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

    private static List<String> boundedDistinct(List<String> values, int limit) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null) continue;
            String bounded = value.strip();
            if (bounded.isBlank()) continue;
            if (bounded.length() > 80) bounded = bounded.substring(0, 80);
            result.add(bounded);
            if (result.size() >= limit) break;
        }
        return List.copyOf(result);
    }

    private static List<String> append(List<String> values, String value) {
        List<String> result = new ArrayList<>(values);
        result.add(value);
        return List.copyOf(result);
    }
}
