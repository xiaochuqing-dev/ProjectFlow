package com.projectflow.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
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
import com.projectflow.dto.ProjectUnderstandingDtos.SemanticToolRequest;
import com.projectflow.dto.ProjectUnderstandingDtos.StructureEvidence;
import com.projectflow.entity.AiProvider;
import com.projectflow.entity.ProjectSpace;
import com.projectflow.service.ProjectEvidenceDiscoveryService.DiscoveryResult;
import com.projectflow.service.ProjectEvidenceDiscoveryService.PromptEvidence;

@Service
public class SemanticScoutService {
    public static final String PROMPT_VERSION = ProjectUnderstandingPromptBuilder.SCOUT_PROMPT_VERSION;
    private static final int MAX_DETAILED_DOCUMENT_SAMPLES = 8;
    private static final int MAX_STRUCTURE_EVIDENCE = 16;
    private static final List<String> DETAILED_SAMPLE_CATEGORY_ORDER = List.of(
        "PRODUCT_CONTEXT", "README", "AGENT_CONTEXT", "AGENT_RESULT",
        "UNKNOWN_DOCUMENT", "ADR", "MANIFEST", "CI_CD", "TEST",
        "MIGRATION", "INFRA", "CHANGELOG", "CONFIG", "BUILD", "LICENSE"
    );

    private final ModelGatewayService modelGateway;
    private final BudgetAwareContextPacker contextPacker;
    private final ProjectUnderstandingPromptBuilder promptBuilder;
    private final AnalysisToolRegistry toolRegistry;
    private final AnalysisViewRegistry viewRegistry;

    @Value("${projectflow.understanding.max-model-prompt-chars:48000}")
    private int maxModelPromptChars;

    @Autowired
    public SemanticScoutService(
        ModelGatewayService modelGateway,
        BudgetAwareContextPacker contextPacker,
        ProjectUnderstandingPromptBuilder promptBuilder,
        AnalysisToolRegistry toolRegistry,
        AnalysisViewRegistry viewRegistry
    ) {
        this.modelGateway = modelGateway;
        this.contextPacker = contextPacker;
        this.promptBuilder = promptBuilder;
        this.toolRegistry = toolRegistry;
        this.viewRegistry = viewRegistry;
    }

    /** Compatibility constructor for focused tests. */
    public SemanticScoutService(
        ModelGatewayService modelGateway,
        BudgetAwareContextPacker contextPacker
    ) {
        this(
            modelGateway,
            contextPacker,
            new ProjectUnderstandingPromptBuilder(),
            new AnalysisToolRegistry(),
            new AnalysisViewRegistry()
        );
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
        SemanticScoutResponse scout = parseScout(
            root.path("semanticScout"),
            allowedEvidence,
            promptBuild.eligibleCapabilities(),
            promptBuild.eligibleViews()
        );
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
        sections.put("documents", compactPromptEvidence(discovery.promptEvidence()));
        Map<String, Object> structure = new LinkedHashMap<>();
        structure.put("modules", index.modules().stream()
            .limit(12)
            .map(item -> Map.of(
                "path", bounded(item.path(), 100),
                "fileCount", item.fileCount(),
                "sourceFileCount", item.sourceFileCount(),
                "estimatedLoc", item.estimatedLoc(),
                "languages", item.languages().entrySet().stream()
                    .limit(4)
                    .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (left, right) -> left,
                        LinkedHashMap::new
                    )),
                "evidenceRefs", item.evidenceRefs().stream().limit(2).toList()
            ))
            .toList());
        structure.put("entryPoints", index.entryPoints().stream()
            .limit(12)
            .map(item -> Map.of(
                "path", bounded(item.path(), 100),
                "kind", bounded(item.kind(), 40),
                "confidence", item.confidence(),
                "evidenceRef", item.evidenceRef()
            ))
            .toList());
        structure.put("importantNodes", index.importantNodes().stream()
            .limit(10)
            .map(item -> Map.of(
                "id", item.id(),
                "type", item.nodeType(),
                "label", bounded(item.label(), 160),
                "path", bounded(item.path(), 100),
                "score", item.score(),
                "evidenceRefs", item.evidenceRefs().stream().limit(3).toList()
            ))
            .toList());
        structure.put("functionalAreas", index.functionalAreas().stream()
            .limit(8)
            .map(item -> Map.of(
                "id", item.id(),
                "label", bounded(item.label(), 160),
                "confidence", item.confidence(),
                "memberPaths", item.memberPaths().stream().limit(4)
                    .map(path -> bounded(path, 100))
                    .toList(),
                "keySymbolIds", item.keySymbolIds().stream().limit(3).toList(),
                "relationCount", item.relationCount(),
                "evidenceRefs", item.evidenceRefs().stream().limit(4).toList()
            ))
            .toList());
        Set<String> prioritizedRefs = new LinkedHashSet<>();
        index.functionalAreas().forEach(area -> prioritizedRefs.addAll(area.evidenceRefs()));
        index.importantNodes().forEach(node -> prioritizedRefs.addAll(node.evidenceRefs()));
        structure.put(
            "structureEvidence",
            compactStructureEvidence(index.evidence(), prioritizedRefs)
        );
        structure.put("engineeringSignals", index.engineeringSignals().entrySet().stream()
            .limit(12)
            .collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().stream().limit(6)
                    .map(value -> bounded(value, 100))
                    .toList(),
                (left, right) -> left,
                LinkedHashMap::new
            )));
        structure.put("coverage", index.coverage());
        sections.put("structure", structure);
        sections.put("historicalCoverage", history);
        sections.put("unknownsAndConflicts", Map.of("unsupportedAreas", index.unsupportedAreas()));
        BudgetAwareContextPacker.PackedContext packed = contextPacker.pack(
            sections,
            Math.max(8_000, maxModelPromptChars - 4_000)
        );
        List<String> eligibleCapabilities = toolRegistry.eligibleCapabilities(
            intake,
            index,
            discovery.sourceMap()
        );
        List<String> eligibleViews = viewRegistry.eligible(
            intake,
            index,
            discovery.sourceMap(),
            history
        );
        List<String> evidenceIds = allowedEvidence(index, discovery).stream().sorted().toList();
        String prompt = promptBuilder.buildScoutPrompt(
            new ProjectUnderstandingPromptBuilder.ScoutPromptInput(
                packed.json(),
                evidenceIds,
                eligibleCapabilities,
                eligibleViews
            )
        );
        return new PromptBuild(prompt, packed.diagnostics(), eligibleCapabilities, eligibleViews);
    }

    static List<Map<String, Object>> compactPromptEvidence(List<PromptEvidence> evidence) {
        List<PromptEvidence> safeEvidence = evidence == null ? List.of() : evidence;
        Set<String> detailedIds = detailedSampleIds(safeEvidence);
        List<Map<String, Object>> result = new ArrayList<>();
        for (PromptEvidence item : safeEvidence) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("id", item.id());
            value.put("category", bounded(item.category(), 40));
            value.put("locator", bounded(item.locator(), 80));
            value.put("summary", bounded(item.summary(), 96));
            if (detailedIds.contains(item.id()) && !item.boundedSample().isBlank()) {
                value.put("boundedSample", bounded(item.boundedSample(), 240));
            }
            result.add(Map.copyOf(value));
        }
        return List.copyOf(result);
    }

    static List<Map<String, Object>> compactStructureEvidence(
        List<StructureEvidence> evidence,
        Set<String> prioritizedRefs
    ) {
        List<StructureEvidence> safeEvidence = evidence == null ? List.of() : evidence;
        Set<String> priority = prioritizedRefs == null ? Set.of() : prioritizedRefs;
        List<StructureEvidence> selected = new ArrayList<>();
        Set<String> selectedIds = new LinkedHashSet<>();
        selectDiverseStructureEvidence(safeEvidence, priority, selected, selectedIds, true, true);
        selectDiverseStructureEvidence(safeEvidence, priority, selected, selectedIds, true, false);
        selectDiverseStructureEvidence(safeEvidence, priority, selected, selectedIds, false, true);
        selectDiverseStructureEvidence(safeEvidence, priority, selected, selectedIds, false, false);
        return selected.stream()
            .map(item -> Map.<String, Object>of(
                "id", item.id(),
                "kind", bounded(item.kind(), 50),
                "path", bounded(item.path(), 100),
                "summary", bounded(item.summary(), 120)
            ))
            .toList();
    }

    private static Set<String> detailedSampleIds(List<PromptEvidence> evidence) {
        Set<String> result = new LinkedHashSet<>();
        for (String category : DETAILED_SAMPLE_CATEGORY_ORDER) {
            evidence.stream()
                .filter(item -> category.equals(item.category()))
                .filter(item -> !item.boundedSample().isBlank())
                .map(PromptEvidence::id)
                .filter(id -> !result.contains(id))
                .findFirst()
                .ifPresent(result::add);
            if (result.size() >= MAX_DETAILED_DOCUMENT_SAMPLES) return Set.copyOf(result);
        }
        Set<String> modules = new LinkedHashSet<>();
        for (PromptEvidence item : evidence) {
            if (result.contains(item.id()) || item.boundedSample().isBlank()) continue;
            String module = topLevelModule(item.locator());
            if (modules.add(module)) result.add(item.id());
            if (result.size() >= MAX_DETAILED_DOCUMENT_SAMPLES) break;
        }
        for (PromptEvidence item : evidence) {
            if (!item.boundedSample().isBlank()) result.add(item.id());
            if (result.size() >= MAX_DETAILED_DOCUMENT_SAMPLES) break;
        }
        return Set.copyOf(result);
    }

    private static void selectDiverseStructureEvidence(
        List<StructureEvidence> evidence,
        Set<String> prioritizedRefs,
        List<StructureEvidence> selected,
        Set<String> selectedIds,
        boolean prioritizedOnly,
        boolean diversityPass
    ) {
        if (selected.size() >= MAX_STRUCTURE_EVIDENCE) return;
        Set<String> representedKinds = new LinkedHashSet<>();
        Set<String> representedModules = new LinkedHashSet<>();
        selected.forEach(item -> {
            representedKinds.add(item.kind());
            representedModules.add(topLevelModule(item.path()));
        });
        for (StructureEvidence item : evidence) {
            if (selected.size() >= MAX_STRUCTURE_EVIDENCE) break;
            if (selectedIds.contains(item.id())) continue;
            if (prioritizedOnly && !prioritizedRefs.contains(item.id())) continue;
            if (diversityPass
                && representedKinds.contains(item.kind())
                && representedModules.contains(topLevelModule(item.path()))) {
                continue;
            }
            selected.add(item);
            selectedIds.add(item.id());
            representedKinds.add(item.kind());
            representedModules.add(topLevelModule(item.path()));
        }
    }

    private static String topLevelModule(String path) {
        if (path == null || path.isBlank()) return ".";
        String normalized = path.replace('\\', '/');
        int slash = normalized.indexOf('/');
        return slash < 0 ? "." : normalized.substring(0, slash);
    }

    private SemanticScoutResponse parseScout(
        JsonNode node,
        Set<String> allowedEvidence,
        List<String> eligibleCapabilities,
        List<String> eligibleViews
    ) {
        List<ProjectShapeHypothesis> shapes = new ArrayList<>();
        for (JsonNode item : array(node.path("projectShapeHypotheses"), 8)) {
            List<String> refs = validRefs(item.path("evidenceRefs"), allowedEvidence, 12);
            if (refs.isEmpty()) continue;
            for (String shape : normalizeShapes(item.path("shape").asText(""))) {
                if (shapes.stream().anyMatch(existing -> existing.shape().equals(shape))) continue;
                shapes.add(new ProjectShapeHypothesis(
                    shape,
                    confidence(item.path("confidence").asText("MEDIUM")),
                    refs,
                    bounded(item.path("reason").asText("").strip(), 300)
                ));
                if (shapes.size() >= 8) break;
            }
        }
        List<EvidenceSourceAssessment> assessments = new ArrayList<>();
        for (JsonNode item : array(node.path("evidenceSourceAssessments"), 80)) {
            String evidenceId = item.path("evidenceId").asText("").strip();
            if (!allowedEvidence.contains(evidenceId) || !evidenceId.startsWith("source:")) continue;
            assessments.add(new EvidenceSourceAssessment(
                evidenceId,
                bounded(item.path("semanticRole").asText("UNKNOWN").strip(), 80),
                importance(item.path("importance").asText("UNKNOWN")),
                bounded(item.path("currentness").asText("UNKNOWN").strip().toUpperCase(Locale.ROOT), 30),
                item.path("shouldDeepRead").asBoolean(false),
                item.path("shouldSkip").asBoolean(false),
                bounded(item.path("reason").asText("").strip(), 300),
                bounded(item.path("informationGap").asText("").strip(), 300),
                viewRegistry.validate(texts(item.path("affectedDimensions"), 12, 80), eligibleViews),
                confidence(item.path("confidence").asText("MEDIUM"))
            ));
        }
        List<SemanticToolRequest> toolRequests = new ArrayList<>();
        for (JsonNode item : normalizedToolRequestNodes(node)) {
            String capability = AnalysisToolRegistry.normalizeCapability(
                item.path("capability").asText("")
            );
            String gap = bounded(item.path("informationGap").asText("").strip(), 300);
            String expectedValue = bounded(item.path("expectedEvidenceValue").asText("").strip(), 300);
            String insufficient = bounded(
                item.path("whyExistingEvidenceIsInsufficient").asText("").strip(),
                300
            );
            List<String> targets = validRefs(item.path("targetEvidenceIds"), allowedEvidence, 12);
            if (eligibleCapabilities.contains(capability)
                && !gap.isBlank()
                && !expectedValue.isBlank()
                && !insufficient.isBlank()
                && !targets.isEmpty()) {
                toolRequests.add(new SemanticToolRequest(
                    capability,
                    gap,
                    expectedValue,
                    targets,
                    insufficient
                ));
            }
        }
        List<String> recommended = toolRequests.stream().map(SemanticToolRequest::capability).distinct().toList();
        return new SemanticScoutResponse(
            List.copyOf(shapes),
            List.copyOf(assessments),
            viewRegistry.validate(texts(node.path("applicableDimensions"), 20, 80), eligibleViews),
            recommended,
            texts(node.path("unknowns"), 20, 300),
            texts(node.path("skipCandidates"), 40, 100).stream().filter(allowedEvidence::contains).toList(),
            guardedTexts(node.path("potentialConflicts"), allowedEvidence, 20),
            guardedTexts(node.path("currentnessWarnings"), allowedEvidence, 20),
            List.copyOf(toolRequests),
            true
        );
    }

    /**
     * Normalizes only requests the model explicitly emitted. Capability
     * decisions do not create an engineering-owned semantic floor: SKIP is
     * ignored, while REQUEST is treated as an alternate encoding of the same
     * model decision and deduplicated by structural completeness.
     */
    public static List<JsonNode> normalizedToolRequestNodes(JsonNode scoutNode) {
        if (scoutNode == null || !scoutNode.isObject()) return List.of();
        LinkedHashMap<String, JsonNode> result = new LinkedHashMap<>();
        for (JsonNode request : array(scoutNode.path("toolRequests"), 12)) {
            mergeExplicitRequest(result, request);
        }
        for (JsonNode decision : array(scoutNode.path("capabilityDecisions"), 24)) {
            if ("REQUEST".equalsIgnoreCase(decision.path("decision").asText("").strip())) {
                mergeExplicitRequest(result, decision);
            }
        }
        return result.values().stream().limit(12).toList();
    }

    private static void mergeExplicitRequest(Map<String, JsonNode> result, JsonNode candidate) {
        if (candidate == null || !candidate.isObject()) return;
        String capability = AnalysisToolRegistry.normalizeCapability(
            candidate.path("capability").asText("")
        );
        if (capability.isBlank()) return;
        JsonNode existing = result.get(capability);
        if (existing == null || requestCompleteness(candidate) > requestCompleteness(existing)) {
            result.put(capability, candidate);
        }
    }

    private static int requestCompleteness(JsonNode request) {
        int score = 0;
        if (!request.path("informationGap").asText("").isBlank()) score++;
        if (!request.path("expectedEvidenceValue").asText("").isBlank()) score++;
        if (!request.path("whyExistingEvidenceIsInsufficient").asText("").isBlank()) score++;
        if (request.path("targetEvidenceIds").isArray()
            && !request.path("targetEvidenceIds").isEmpty()) score++;
        return score;
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

    private static String importance(String value) {
        String normalized = value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "HIGH", "MEDIUM", "LOW", "UNKNOWN" -> normalized;
            default -> "UNKNOWN";
        };
    }

    public static List<String> normalizeShapes(String value) {
        if (value == null || value.isBlank()) return List.of();
        String normalized = value.strip().toUpperCase(Locale.ROOT);
        if (Set.of(
            "FULLSTACK", "FULL_STACK", "FULL_STACK_APPLICATION",
            "FRONTEND_BACKEND", "FRONTEND+BACKEND"
        ).contains(normalized)) {
            return List.of("FRONTEND", "BACKEND");
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String part : normalized.split("\\s*(?:\\+|/|\\||,|，|、|&|\\bAND\\b)\\s*")) {
            String canonical = canonicalShape(part);
            if (!canonical.isBlank()) result.add(canonical);
        }
        return List.copyOf(result);
    }

    private static String canonicalShape(String value) {
        String normalized = value.strip().replaceAll("[^A-Z0-9]+", "_");
        return switch (normalized) {
            case "DOCUMENT", "SCRIPT", "FRONTEND", "BACKEND", "DESKTOP", "MONOREPO",
                "CODE_PROJECT", "LARGE_REPOSITORY", "AGENT_RESULT_MATERIAL",
                "PROCESS_METADATA", "OTHER_MATERIAL", "DEVELOPER_WORKBENCH" -> normalized;
            case "DOCUMENTATION", "TEXT_DOCUMENT" -> "DOCUMENT";
            case "SINGLE_SCRIPT", "CLI_SCRIPT" -> "SCRIPT";
            case "FRONT_END" -> "FRONTEND";
            case "BACK_END" -> "BACKEND";
            case "DESKTOP_APP", "DESKTOP_APPLICATION" -> "DESKTOP";
            case "MONO_REPO", "MONOREPOSITORY" -> "MONOREPO";
            case "CODEBASE", "SOURCE_PROJECT" -> "CODE_PROJECT";
            case "LARGE_CODEBASE" -> "LARGE_REPOSITORY";
            case "AGENT_RESULT" -> "AGENT_RESULT_MATERIAL";
            case "PROCESS_META", "REQUEST_METADATA" -> "PROCESS_METADATA";
            case "DEVELOPER_TOOL", "PROJECT_WORKBENCH" -> "DEVELOPER_WORKBENCH";
            default -> "";
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

    private record PromptBuild(
        String prompt,
        ContextPackingDiagnostics diagnostics,
        List<String> eligibleCapabilities,
        List<String> eligibleViews
    ) {
    }
}
