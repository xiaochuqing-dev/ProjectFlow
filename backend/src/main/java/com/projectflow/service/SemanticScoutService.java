package com.projectflow.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.projectflow.dto.ProjectUnderstandingDtos.EvidenceSourceAssessment;
import com.projectflow.dto.ProjectUnderstandingDtos.ContextPackingDiagnostics;
import com.projectflow.dto.ProjectUnderstandingDtos.HistoricalCoverageResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.ProjectEvidenceSourceResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.ProjectShapeHypothesis;
import com.projectflow.dto.ProjectUnderstandingDtos.ProjectStructureIndexResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.RepositoryIntakeResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.SemanticScoutResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.StructureEvidence;
import com.projectflow.entity.AiProvider;
import com.projectflow.entity.ProjectSpace;
import com.projectflow.service.ProjectEvidenceDiscoveryService.DiscoveryResult;
import com.projectflow.service.ProjectEvidenceDiscoveryService.PromptEvidence;

@Service
public class SemanticScoutService {
    public static final String PROMPT_VERSION = "semantic-scout-v3";

    private final ModelGatewayService modelGateway;
    private final BudgetAwareContextPacker contextPacker;

    @Value("${projectflow.understanding.max-model-prompt-chars:48000}")
    private int maxModelPromptChars;

    public SemanticScoutService(
        ModelGatewayService modelGateway,
        BudgetAwareContextPacker contextPacker
    ) {
        this.modelGateway = modelGateway;
        this.contextPacker = contextPacker;
    }

    public ScoutResult scout(
        AiProvider provider,
        ProjectSpace project,
        RepositoryIntakeResponse intake,
        ProjectStructureIndexResponse index,
        DiscoveryResult discovery,
        HistoricalCoverageResponse history
    ) throws Exception {
        long started = System.nanoTime();
        Set<String> allowedEvidence = allowedEvidence(index, discovery);
        PromptBuild promptBuild = buildPrompt(project, intake, index, discovery, history);
        ModelGatewayService.StructuredModelResponse response = modelGateway.callStructured(
            provider,
            promptBuild.prompt(),
            ModelTaskType.PROJECT_UNDERSTANDING_SNAPSHOT
        );
        JsonNode root = response.parsed().root();
        SemanticScoutResponse scout = parseScout(root.path("semanticScout"), allowedEvidence);
        return new ScoutResult(
            scout,
            root,
            allowedEvidence,
            response.diagnostics(),
            elapsedMs(started),
            containsUnknownEvidence(root, allowedEvidence),
            promptBuild.diagnostics()
        );
    }

    private PromptBuild buildPrompt(
        ProjectSpace project,
        RepositoryIntakeResponse intake,
        ProjectStructureIndexResponse index,
        DiscoveryResult discovery,
        HistoricalCoverageResponse history
    ) {
        Map<String, Object> sections = new LinkedHashMap<>();
        sections.put("projectIntake", Map.of(
            "projectName", bounded(project.getName(), 200),
            "classification", intake.classification(),
            "scale", intake.scale(),
            "fileCount", intake.fileCount(),
            "sourceFileCount", intake.sourceFileCount(),
            "estimatedLoc", intake.estimatedLoc(),
            "languages", intake.languageDistribution()
        ));
        sections.put("manifests", intake.manifestFiles().stream().limit(80).toList());
        sections.put("git", Map.of(
            "available", intake.git().available(),
            "commitCount", intake.git().commitCount(),
            "worktreeState", intake.git().worktreeState(),
            "tagCount", history.tagCount(),
            "historicalCoverage", history.overallCoverage()
        ));
        sections.put("documents", discovery.promptEvidence().stream()
            .limit(80)
            .map(this::promptEvidence)
            .toList());
        Map<String, Object> structure = new LinkedHashMap<>();
        structure.put("modules", index.modules().stream().limit(120).toList());
        structure.put("entryPoints", index.entryPoints().stream().limit(60).toList());
        structure.put("importantNodes", index.importantNodes().stream()
            .limit(40)
            .map(item -> Map.of(
                "id", item.id(),
                "type", item.nodeType(),
                "label", bounded(item.label(), 160),
                "path", item.path(),
                "score", item.score(),
                "evidenceRefs", item.evidenceRefs().stream().limit(5).toList()
            ))
            .toList());
        structure.put("functionalAreas", index.functionalAreas().stream()
            .limit(60)
            .map(item -> Map.of(
                "id", item.id(),
                "label", bounded(item.label(), 160),
                "confidence", item.confidence(),
                "memberPaths", item.memberPaths().stream().limit(12).toList(),
                "keySymbolIds", item.keySymbolIds().stream().limit(8).toList(),
                "relationCount", item.relationCount(),
                "evidenceRefs", item.evidenceRefs().stream().limit(10).toList()
            ))
            .toList());
        Set<String> prioritizedRefs = new LinkedHashSet<>();
        index.functionalAreas().forEach(area -> prioritizedRefs.addAll(area.evidenceRefs()));
        index.importantNodes().forEach(node -> prioritizedRefs.addAll(node.evidenceRefs()));
        structure.put("structureEvidence", index.evidence().stream()
            .sorted((left, right) -> Boolean.compare(
                !prioritizedRefs.contains(left.id()),
                !prioritizedRefs.contains(right.id())
            ))
            .limit(400)
            .toList());
        structure.put("engineeringSignals", index.engineeringSignals());
        structure.put("coverage", index.coverage());
        sections.put("structure", structure);
        sections.put("historicalCoverage", history);
        sections.put("unknownsAndConflicts", Map.of("unsupportedAreas", index.unsupportedAreas()));
        BudgetAwareContextPacker.PackedContext packed = contextPacker.pack(
            sections,
            Math.max(8_000, maxModelPromptChars - 4_000)
        );
        String prompt = """
            你是 ProjectFlow 的 Semantic Scout 与有界项目解释器。输入可能是空目录、文档、脚本、前端、后端、
            Desktop、Monorepo 或混合项目。不要预设固定项目形态，不要因为 package.json、目录名或 README 宣传
            就断言前端、后端、数据库或已实现能力。README 与源码冲突时必须报告 conflict/currentness warning。

            只允许引用上下文中真实存在的 evidence id。不得输出绝对路径、密钥、凭证、原始推理、下一步路线图或优先级。
            你负责：判断项目形态假设、材料语义角色、适用分析维度、已注册工具能力需求，以及动态 Profile 的有证据解释。
            工程系统负责扫描、Git、SCIP、PageRank 和工具执行；你不能编造工具结果。

            语义边界：
            1. Agent Result 只是外部过程证据，未经 ProjectFact 链路校验不能表述为已确认事实或稳定能力。
            2. token、耗时、request count、模型名等只属于 PROCESS_METADATA，不能证明业务能力、质量、成熟度或完成结果。
            3. 当前源码只能证明当前可观察状态；没有 Git/Fact/Tag/document history 时不得推断历史阶段、演进或发布日期。
            4. 历史文档、README 与当前源码冲突时保留双方证据，标记 POSSIBLY_STALE/UNKNOWN，不得替用户裁决。
            5. 缺少证据表示 unknown，不表示不存在；空目录、单脚本、纯文档不能扩张成多层架构。
            6. recommendedToolCalls 只能请求 capability 名称，不能输出命令、参数、绝对路径或任意文件读取。
            7. shape 使用原子、稳定标签，优先从 DOCUMENT、SCRIPT、FRONTEND、BACKEND、DESKTOP、MONOREPO、
               CODE_PROJECT、LARGE_REPOSITORY、AGENT_RESULT_MATERIAL、PROCESS_METADATA、OTHER_MATERIAL 选择；
               多形态分别输出，禁止拼成“FRONTEND+BACKEND”一类复合自由文本。
            8. 对上下文提供的每个 evidence id 恰好给出一次来源评估；有实质证据时不得把 shape、来源评估和适用
               维度全部留空。不确定时输出 UNKNOWN/unknown，不要用空数组逃避判断。
            9. 工具只在能补充当前上下文缺少的信息时请求：DOC_READER 用于样本不足的重要文档，MANIFEST 用于
               manifest 细节，AGENT_RESULT 用于 Agent result，GIT_HISTORY/GIT_TAG 用于真实历史，WORKTREE
               用于未提交变化；不得因为“可能有帮助”泛化请求。
            10. applicableDimensions 使用简短稳定的大写原子标签，避免 analysis、summary、general、hypothesis
                这类无信息标签。shouldDeepRead 只表示需要获取尚未提供的正文，不等同于 evidence 重要。

            只返回 JSON：
            {
              "semanticScout":{
                "projectShapeHypotheses":[{"shape":"","confidence":"HIGH|MEDIUM|LOW","evidenceRefs":["id"],"reason":""}],
                "evidenceSourceAssessments":[{"evidenceId":"source:id","semanticRole":"","importance":"HIGH|MEDIUM|LOW",
                  "currentness":"CURRENT|HISTORICAL|POSSIBLY_STALE|UNKNOWN","shouldDeepRead":true,"shouldSkip":false,
                  "reason":"","confidence":"HIGH|MEDIUM|LOW"}],
                "applicableDimensions":[],
                "recommendedToolCalls":[],
                "unknowns":[],
                "skipCandidates":[],
                "potentialConflicts":[{"text":"","evidenceRefs":["id"]}],
                "currentnessWarnings":[{"text":"","evidenceRefs":["id"]}]
              },
              "dynamicProfile":{
                "summary":"",
                "sections":[{"id":"","type":"","title":"","summary":"",
                  "claims":[{"text":"","confidence":"HIGH|MEDIUM|LOW","evidenceRefs":["id"]}],
                  "confidence":"HIGH|MEDIUM|LOW","epistemicStatus":"OBSERVED|INFERRED|UNKNOWN","displayPriority":50,
                  "applicabilityReason":""}]
              },
              "unknowns":[]
            }
            最多 4 个 shape、40 个来源评估、12 个维度、8 个动态 Section、每个 Section 最多 5 条 claim。
            没有源码时不能生成代码架构；没有历史时不能生成 Timeline/Evolution；小脚本不能伪装成多层架构。
            Prompt version: semantic-scout-v3。
            证据上下文：
            """ + packed.json();
        return new PromptBuild(prompt, packed.diagnostics());
    }

    private Map<String, Object> promptEvidence(PromptEvidence evidence) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", evidence.id());
        value.put("category", evidence.category());
        value.put("sourceType", evidence.sourceType());
        value.put("locator", evidence.locator());
        value.put("summary", evidence.summary());
        value.put("boundedSample", evidence.boundedSample());
        return value;
    }

    private SemanticScoutResponse parseScout(JsonNode node, Set<String> allowedEvidence) {
        List<ProjectShapeHypothesis> shapes = new ArrayList<>();
        for (JsonNode item : array(node.path("projectShapeHypotheses"), 8)) {
            List<String> refs = validRefs(item.path("evidenceRefs"), allowedEvidence, 12);
            String shape = bounded(item.path("shape").asText("").strip(), 80);
            if (!shape.isBlank() && !refs.isEmpty()) {
                shapes.add(new ProjectShapeHypothesis(
                    shape,
                    confidence(item.path("confidence").asText("MEDIUM")),
                    refs,
                    bounded(item.path("reason").asText("").strip(), 300)
                ));
            }
        }
        List<EvidenceSourceAssessment> assessments = new ArrayList<>();
        for (JsonNode item : array(node.path("evidenceSourceAssessments"), 80)) {
            String evidenceId = item.path("evidenceId").asText("").strip();
            if (!allowedEvidence.contains(evidenceId) || !evidenceId.startsWith("source:")) continue;
            assessments.add(new EvidenceSourceAssessment(
                evidenceId,
                bounded(item.path("semanticRole").asText("UNKNOWN").strip(), 80),
                confidence(item.path("importance").asText("MEDIUM")),
                bounded(item.path("currentness").asText("UNKNOWN").strip().toUpperCase(Locale.ROOT), 30),
                item.path("shouldDeepRead").asBoolean(false),
                item.path("shouldSkip").asBoolean(false),
                bounded(item.path("reason").asText("").strip(), 300),
                confidence(item.path("confidence").asText("MEDIUM"))
            ));
        }
        return new SemanticScoutResponse(
            List.copyOf(shapes),
            List.copyOf(assessments),
            texts(node.path("applicableDimensions"), 20, 80),
            texts(node.path("recommendedToolCalls"), 20, 80),
            texts(node.path("unknowns"), 20, 300),
            texts(node.path("skipCandidates"), 40, 100).stream().filter(allowedEvidence::contains).toList(),
            guardedTexts(node.path("potentialConflicts"), allowedEvidence, 20),
            guardedTexts(node.path("currentnessWarnings"), allowedEvidence, 20),
            true
        );
    }

    private static Set<String> allowedEvidence(
        ProjectStructureIndexResponse index,
        DiscoveryResult discovery
    ) {
        Set<String> allowed = new LinkedHashSet<>();
        allowed.add("intake:scan");
        discovery.sourceMap().sources().stream().map(ProjectEvidenceSourceResponse::id).forEach(allowed::add);
        index.evidence().stream().map(StructureEvidence::id).forEach(allowed::add);
        return Set.copyOf(allowed);
    }

    private static List<String> guardedTexts(JsonNode node, Set<String> allowed, int limit) {
        List<String> result = new ArrayList<>();
        for (JsonNode item : array(node, limit)) {
            if (!item.isObject()) continue;
            List<String> refs = validRefs(item.path("evidenceRefs"), allowed, 12);
            String text = bounded(item.path("text").asText("").strip(), 300);
            if (!text.isBlank() && !refs.isEmpty()) result.add(text);
        }
        return List.copyOf(result);
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

    private static List<JsonNode> array(JsonNode node, int limit) {
        if (!node.isArray()) return List.of();
        List<JsonNode> result = new ArrayList<>();
        for (JsonNode item : node) {
            result.add(item);
            if (result.size() >= limit) break;
        }
        return List.copyOf(result);
    }

    private static List<String> texts(JsonNode node, int limit, int maxLength) {
        if (!node.isArray()) return List.of();
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (JsonNode item : node) {
            String value = bounded(item.asText("").strip(), maxLength);
            if (!value.isBlank()) result.add(value);
            if (result.size() >= limit) break;
        }
        return List.copyOf(result);
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

    private static String confidence(String value) {
        String normalized = value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "HIGH", "MEDIUM", "LOW" -> normalized;
            default -> "MEDIUM";
        };
    }

    private static String bounded(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static long elapsedMs(long started) {
        return Math.max(0, (System.nanoTime() - started) / 1_000_000);
    }

    public record ScoutResult(
        SemanticScoutResponse scout,
        JsonNode root,
        Set<String> allowedEvidence,
        ModelGatewayService.ModelCallDiagnostics diagnostics,
        long durationMs,
        boolean invalidEvidenceFiltered,
        ContextPackingDiagnostics contextPacking
    ) {
    }

    private record PromptBuild(String prompt, ContextPackingDiagnostics diagnostics) {
    }
}
