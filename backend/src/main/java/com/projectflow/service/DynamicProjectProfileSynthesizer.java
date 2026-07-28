package com.projectflow.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.projectflow.dto.ProjectUnderstandingDtos.AdaptiveAnalysisPlanResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.DynamicProfileSection;
import com.projectflow.dto.ProjectUnderstandingDtos.DynamicProjectProfileResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.EvidenceSourceMapResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.HistoricalCoverageResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.ProjectEvidenceSourceResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.ProjectShapeHypothesis;
import com.projectflow.dto.ProjectUnderstandingDtos.ProjectStructureIndexResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.RepositoryIntakeResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.SemanticScoutResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.UnderstandingClaim;
import com.projectflow.entity.ProjectSpace;

@Service
public class DynamicProjectProfileSynthesizer {
    public DynamicProjectProfileResponse synthesize(
        ProjectSpace project,
        RepositoryIntakeResponse intake,
        ProjectStructureIndexResponse index,
        EvidenceSourceMapResponse sourceMap,
        HistoricalCoverageResponse history,
        SemanticScoutResponse scout,
        AdaptiveAnalysisPlanResponse plan,
        JsonNode modelRoot,
        Set<String> allowedEvidence
    ) {
        if ("EMPTY".equals(intake.classification())) {
            return new DynamicProjectProfileResponse(
                project.getName() + " 当前没有可分析内容。",
                List.of("EMPTY"),
                List.of(),
                List.of("architecture", "capabilities", "timeline", "evolution"),
                List.of(),
                1,
                "HIGH",
                List.of("目录为空；添加材料后再运行分析。")
            );
        }
        if (intake.sourceFileCount() == 0 && sourceMap.scoutEvidenceCount() == 0) {
            return new DynamicProjectProfileResponse(
                project.getName() + " 当前只有空白或不可语义读取的材料，没有实质可分析内容。",
                List.of("EMPTY_CONTENT"),
                List.of(),
                List.of("codeArchitecture", "capabilities", "timeline", "evolution"),
                List.of(),
                1,
                "HIGH",
                List.of("发现文件但没有非空文本或源码证据。")
            );
        }

        Map<String, DynamicProfileSection> sections = new LinkedHashMap<>();
        deterministicSections(project, intake, index, sourceMap, history).forEach(
            section -> sections.put(section.id(), section)
        );
        List<DynamicProfileSection> modelSections = parseModelSections(
            modelRoot == null ? null : modelRoot.path("dynamicProfile").path("sections"),
            allowedEvidence,
            intake,
            history,
            scout,
            plan.eligibleViews()
        );
        modelSections.forEach(section -> sections.put(section.id(), section));
        List<DynamicProfileSection> ordered = sections.values().stream()
            .sorted(Comparator.comparingInt(DynamicProfileSection::displayPriority))
            .limit(16)
            .toList();

        List<String> shapes = scout.projectShapeHypotheses().stream()
            .map(ProjectShapeHypothesis::shape)
            .distinct()
            .limit(8)
            .toList();
        List<String> applicableViews = ordered.stream().map(DynamicProfileSection::type).distinct().toList();
        String modelSummary = modelRoot == null
            ? ""
            : bounded(modelRoot.path("dynamicProfile").path("summary").asText("").strip(), 800);
        String summary = !modelSummary.isBlank() && !modelSections.isEmpty()
            ? modelSummary
            : deterministicSummary(project, intake, history);
        LinkedHashSet<String> unknowns = new LinkedHashSet<>();
        unknowns.addAll(scout.unknowns());
        unknowns.addAll(scout.potentialConflicts());
        unknowns.addAll(scout.currentnessWarnings());
        unknowns.addAll(plan.unavailableCapabilities());
        if (unknowns.size() > 30) {
            unknowns = new LinkedHashSet<>(unknowns.stream().limit(30).toList());
        }
        double coverage = round(Math.min(
            1,
            Math.max(intake.supportedStructureCoverage(), index.coverage().overall())
        ));
        return new DynamicProjectProfileResponse(
            summary,
            shapes,
            applicableViews,
            plan.skippedDimensions(),
            ordered,
            coverage,
            scout.modelUsed() ? "HIGH" : confidence(index),
            List.copyOf(unknowns)
        );
    }

    private List<DynamicProfileSection> deterministicSections(
        ProjectSpace project,
        RepositoryIntakeResponse intake,
        ProjectStructureIndexResponse index,
        EvidenceSourceMapResponse sourceMap,
        HistoricalCoverageResponse history
    ) {
        List<DynamicProfileSection> result = new ArrayList<>();
        if (intake.sourceFileCount() == 0) {
            List<String> refs = documentRefs(sourceMap);
            result.add(section(
                "document-current-state",
                "DOCUMENT_OVERVIEW",
                "材料概览",
                "发现 " + sourceMap.categoryCounts().entrySet().stream()
                    .filter(entry -> isDocumentCategory(entry.getKey()))
                    .mapToLong(Map.Entry::getValue)
                    .sum() + " 个文档或文本候选；不会把它们伪装成软件架构。",
                claim(
                    "document-observed",
                    "当前目录没有可确认的源代码，Profile 只展示材料本身适用的维度。",
                    refs.isEmpty() ? List.of("intake:scan") : refs
                ),
                10,
                "没有源码，但存在可读取材料"
            ));
            return result;
        }

        result.add(section(
            "current-state",
            "CURRENT_STATE",
            "当前状态",
            project.getName() + " 当前包含 " + intake.sourceFileCount() + " 个源码文件，估算 "
                + intake.estimatedLoc() + " 行代码。",
            claim(
                "current-state-observed",
                "目录盘点确认 " + intake.fileCount() + " 个文件，规模为 " + intake.scale() + "。",
                List.of("intake:scan")
            ),
            10,
            "所有有源码项目都需要当前状态"
        ));
        if (!intake.languageDistribution().isEmpty()) {
            String languages = intake.languageDistribution().entrySet().stream()
                .limit(6)
                .map(entry -> entry.getKey() + " " + entry.getValue() + " 行")
                .reduce((left, right) -> left + "，" + right)
                .orElse("");
            result.add(section(
                "technology",
                "TECHNOLOGY",
                "技术组成",
                languages + "。",
                claim("technology-observed", "语言分布来自有界代码指标，不等同于产品形态。", List.of("intake:scan")),
                20,
                "存在源码语言信号"
            ));
        }
        List<String> structureRefs = index.evidence().stream().map(item -> item.id()).limit(10).toList();
        result.add(section(
            "current-structure",
            "CURRENT_STRUCTURE",
            "当前结构",
            index.symbols().isEmpty()
                ? "当前只确认文件、模块、manifest 和入口候选；没有精确关系时不把目录邻近当调用关系。"
                : "SCIP 识别 " + index.symbols().size() + " 个符号、" + index.definitions().size()
                    + " 个定义和 " + index.references().size() + " 个引用。",
            claim(
                "structure-observed",
                index.symbols().isEmpty()
                    ? "结构来源为 MANIFEST_FILESYSTEM fallback。"
                    : "结构关系来自 SCIP definition/reference，并复用 JGraphT 排序与聚类。",
                structureRefs.isEmpty() ? List.of("intake:scan") : structureRefs
            ),
            30,
            "存在源码或结构证据"
        ));
        List<String> engineeringKinds = index.engineeringSignals().entrySet().stream()
            .filter(entry -> !entry.getValue().isEmpty())
            .map(Map.Entry::getKey)
            .toList();
        if (!engineeringKinds.isEmpty()) {
            result.add(section(
                "engineering-state",
                "ENGINEERING_STATE",
                "工程状态",
                "发现工程化信号：" + String.join("、", engineeringKinds) + "。",
                claim(
                    "engineering-observed",
                    "测试、构建、质量或部署信号来自真实文件盘点。",
                    structureRefs.isEmpty() ? List.of("intake:scan") : structureRefs
                ),
                50,
                "存在工程化材料"
            ));
        }
        if (history.historyAvailable() && history.gitCommitCount() > 0) {
            result.add(section(
                "evolution",
                "EVOLUTION",
                history.gitCommitCount() <= 5 ? "早期变化" : "演进证据",
                history.gitCommitCount() <= 5
                    ? "当前只有 " + history.gitCommitCount() + " 次提交，只展示短历史，不生成成熟阶段。"
                    : "本地 Git 提供 " + history.gitCommitCount() + " 次提交和 " + history.tagCount()
                        + " 个 Tag；完整程度仍以 Historical Coverage 为准。",
                claim(
                    "history-observed",
                    "历史存在性来自本地 Git，ProjectFact 覆盖 " + history.coveredCommitCount()
                        + "/" + history.gitCommitCount() + " 个提交引用。",
                    List.of("git:summary")
                ),
                80,
                "存在可验证历史证据"
            ));
        }
        return List.copyOf(result);
    }

    private List<DynamicProfileSection> parseModelSections(
        JsonNode node,
        Set<String> allowedEvidence,
        RepositoryIntakeResponse intake,
        HistoricalCoverageResponse history,
        SemanticScoutResponse scout,
        List<String> eligibleViews
    ) {
        if (node == null || !node.isArray()) return List.of();
        List<DynamicProfileSection> result = new ArrayList<>();
        int sequence = 0;
        for (JsonNode item : node) {
            if (result.size() >= 12) break;
            String type = normalizedType(item.path("type").asText(""));
            if (type.isBlank() || !isApplicable(type, intake, history, scout, eligibleViews)) continue;
            List<UnderstandingClaim> claims = new ArrayList<>();
            if (item.path("claims").isArray()) {
                for (JsonNode claim : item.path("claims")) {
                    if (claims.size() >= 10) break;
                    String text = bounded(claim.path("text").asText("").strip(), 600);
                    List<String> refs = validRefs(claim.path("evidenceRefs"), allowedEvidence, 12);
                    if (!text.isBlank() && !refs.isEmpty()) {
                        claims.add(new UnderstandingClaim(
                            "profile-model-" + (++sequence),
                            text,
                            epistemic(claim.path("epistemicStatus").asText(
                                item.path("epistemicStatus").asText("INFERRED")
                            )),
                            confidence(claim.path("confidence").asText("MEDIUM")),
                            refs
                        ));
                    }
                }
            }
            if (claims.isEmpty()) continue;
            String id = normalizedId(item.path("id").asText(""), type, result.size());
            result.add(new DynamicProfileSection(
                id,
                type,
                bounded(item.path("title").asText(type).strip(), 100),
                bounded(item.path("summary").asText("").strip(), 1000),
                List.copyOf(claims),
                confidence(item.path("confidence").asText("MEDIUM")),
                "INFERRED",
                Math.max(10, Math.min(100, item.path("displayPriority").asInt(60))),
                bounded(item.path("applicabilityReason").asText("").strip(), 300)
            ));
        }
        return List.copyOf(result);
    }

    private static boolean isApplicable(
        String type,
        RepositoryIntakeResponse intake,
        HistoricalCoverageResponse history,
        SemanticScoutResponse scout,
        List<String> eligibleViews
    ) {
        if (eligibleViews == null || !eligibleViews.contains(type)) return false;
        String lower = type.toLowerCase(Locale.ROOT);
        if (intake.sourceFileCount() == 0
            && (lower.contains("architecture") || lower.contains("symbol") || lower.contains("code"))) {
            return false;
        }
        if (!history.historyAvailable()
            && (lower.contains("timeline") || lower.contains("evolution") || lower.contains("history"))) {
            return false;
        }
        if (lower.contains("backend") || lower.contains("database")) {
            String expectedDimension = lower.contains("database") ? "database" : "backend";
            return scout.applicableDimensions().stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(value -> value.contains(expectedDimension));
        }
        return true;
    }

    private static DynamicProfileSection section(
        String id,
        String type,
        String title,
        String summary,
        UnderstandingClaim claim,
        int priority,
        String reason
    ) {
        return new DynamicProfileSection(
            id,
            type,
            title,
            summary,
            claim == null ? List.of() : List.of(claim),
            "HIGH",
            "OBSERVED",
            priority,
            reason
        );
    }

    private static UnderstandingClaim claim(String id, String text, List<String> refs) {
        return new UnderstandingClaim(id, text, "OBSERVED", "HIGH", refs);
    }

    private static String deterministicSummary(
        ProjectSpace project,
        RepositoryIntakeResponse intake,
        HistoricalCoverageResponse history
    ) {
        if (intake.sourceFileCount() == 0) {
            return project.getName() + " 当前被识别为文档或材料型输入，不生成代码架构。";
        }
        String historyText = history.historyAvailable()
            ? "历史可用，但只按覆盖程度展示。"
            : "缺少历史证据，仅展示当前状态。";
        return project.getName() + " 当前包含可分析源码；" + historyText;
    }

    private static List<String> documentRefs(EvidenceSourceMapResponse sourceMap) {
        return sourceMap.sources().stream()
            .filter(source -> isDocumentCategory(source.category()))
            .map(ProjectEvidenceSourceResponse::id)
            .limit(10)
            .toList();
    }

    private static boolean isDocumentCategory(String category) {
        return Set.of(
            "DOC", "README", "ADR", "PRODUCT_CONTEXT", "AGENT_CONTEXT",
            "AGENT_RESULT", "CHANGELOG", "UNKNOWN_DOCUMENT"
        ).contains(category);
    }

    private static List<String> validRefs(JsonNode node, Set<String> allowed, int limit) {
        if (!node.isArray()) return List.of();
        List<String> result = new ArrayList<>();
        for (JsonNode item : node) {
            String ref = item.asText("").strip();
            if (allowed.contains(ref) && !result.contains(ref)) result.add(ref);
            if (result.size() >= limit) break;
        }
        return List.copyOf(result);
    }

    private static String normalizedType(String value) {
        if (value == null) return "";
        String normalized = value.strip().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]", "_");
        return bounded(normalized, 60);
    }

    private static String normalizedId(String value, String type, int index) {
        String normalized = value == null ? "" : value.strip().toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9_-]", "-")
            .replaceAll("-+", "-");
        return normalized.isBlank() ? "model-" + type.toLowerCase(Locale.ROOT) + "-" + index : bounded(normalized, 80);
    }

    private static String confidence(ProjectStructureIndexResponse index) {
        if (index.coverage().overall() >= 0.8) return "HIGH";
        if (index.coverage().overall() >= 0.5) return "MEDIUM";
        return "LOW";
    }

    private static String confidence(String value) {
        String normalized = value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "HIGH", "MEDIUM", "LOW" -> normalized;
            default -> "MEDIUM";
        };
    }

    private static String epistemic(String value) {
        String normalized = value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "CURRENT_STATE", "HISTORICAL_EVENT", "POSSIBLY_STALE", "PROCESS_EVIDENCE",
                "PROCESS_METADATA", "USER_ASSERTION", "ENGINEERING_OBSERVATION", "INFERRED", "UNKNOWN" -> normalized;
            default -> "INFERRED";
        };
    }

    private static String bounded(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static double round(double value) {
        return Math.round(Math.max(0, Math.min(1, value)) * 1000.0) / 1000.0;
    }
}
