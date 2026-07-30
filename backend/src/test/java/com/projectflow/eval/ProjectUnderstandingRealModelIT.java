package com.projectflow.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.dto.ProjectUnderstandingDtos.DynamicProfileSection;
import com.projectflow.dto.ProjectUnderstandingDtos.ProjectStructureIndexResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.ProjectUnderstandingSnapshotResponse;
import com.projectflow.entity.AiProvider;
import com.projectflow.entity.ProjectMemory;
import com.projectflow.entity.ProjectSpace;
import com.projectflow.entity.ProjectStatus;
import com.projectflow.entity.ProjectStructureIndex;
import com.projectflow.entity.ProjectUnderstandingSnapshot;
import com.projectflow.repository.AiProviderRepository;
import com.projectflow.repository.ProjectFactCommitRefRepository;
import com.projectflow.repository.ProjectMemoryRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.repository.ProjectStructureIndexRepository;
import com.projectflow.repository.ProjectUnderstandingSnapshotRepository;
import com.projectflow.service.AdaptiveAnalysisPlanner;
import com.projectflow.service.AiProviderUrlGuard;
import com.projectflow.service.AnalysisExecutionCoordinator;
import com.projectflow.service.AnalysisToolRegistry;
import com.projectflow.service.BoundedLocalAnalysisCapabilityProvider;
import com.projectflow.service.BudgetAwareContextPacker;
import com.projectflow.service.CompositeProjectStructureIndexer;
import com.projectflow.service.DynamicProjectProfileSynthesizer;
import com.projectflow.service.FinalProfileSynthesisService;
import com.projectflow.service.FixedCommandExecutor;
import com.projectflow.service.HistoricalCoverageService;
import com.projectflow.service.LocalProjectPathGuard;
import com.projectflow.service.ManifestFilesystemProjectStructureIndexer;
import com.projectflow.service.ModelGatewayService;
import com.projectflow.service.ModelOutputAdapter;
import com.projectflow.service.ProjectEvidenceDiscoveryService;
import com.projectflow.service.ProjectEvolutionBridgeService;
import com.projectflow.service.ProjectUnderstandingService;
import com.projectflow.service.RepositoryIntakeService;
import com.projectflow.service.SccCodeMetricsAdapter;
import com.projectflow.service.ScipProjectStructureIndexer;
import com.projectflow.service.SemanticScoutService;
import com.projectflow.service.SensitiveContentRedactor;

class ProjectUnderstandingRealModelIT {
    @TempDir
    Path tempRoot;

    @Test
    void evaluatesCoreCasesThroughRealProjectUnderstandingRefresh() throws Exception {
        var config = ProjectFlowRealModelEvalIT.providerConfig();
        Assumptions.assumeTrue(config != null, "未提供真实 Provider 配置，端到端验收跳过");

        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        Set<String> requestedCases = requestedCaseIds();
        List<CaseSpec> cases = fixtures().stream()
            .filter(testCase -> requestedCases.isEmpty() || requestedCases.contains(testCase.id()))
            .toList();
        List<CaseResult> results = new ArrayList<>();
        for (CaseSpec testCase : cases) {
            System.out.println("REAL_E2E_START case=" + testCase.id());
            long started = System.nanoTime();
            List<String> progress = new ArrayList<>();
            try {
                TestContext context = context(testCase, config, mapper);
                var outcome = context.service().refresh(
                    context.userId(),
                    context.projectId(),
                    (stage, message) -> progress.add(stage)
                );
                ProjectUnderstandingSnapshotResponse snapshot = outcome.snapshot();
                ProjectUnderstandingSnapshotResponse readback =
                    context.service().get(context.userId(), context.projectId());
                ProjectStructureIndexResponse structure =
                    context.service().getStructureIndex(context.userId(), context.projectId());
                CaseResult result = successfulResult(
                    testCase,
                    snapshot,
                    readback,
                    structure,
                    progress,
                    elapsedMs(started)
                );
                results.add(result);
                System.out.printf(
                    "REAL_E2E_DONE case=%s status=%s logicalRequests=%d physicalRequests=%d tools=%s latencyMs=%d%n",
                    testCase.id(),
                    result.status(),
                    result.logicalModelRequests(),
                    result.physicalModelRequests(),
                    result.executedCapabilities(),
                    result.latencyMs()
                );
            } catch (Exception failure) {
                CaseResult result = failedResult(testCase.id(), progress, elapsedMs(started), failure);
                results.add(result);
                System.out.printf(
                    "REAL_E2E_DONE case=%s status=%s failure=%s latencyMs=%d%n",
                    testCase.id(),
                    result.status(),
                    result.failureCategory(),
                    result.latencyMs()
                );
                Thread.interrupted();
            }
        }

        Path output = Path.of("target", "projectflow-eval", "e2e");
        Files.createDirectories(output);
        Map<String, Object> artifact = new LinkedHashMap<>();
        artifact.put("version", "projectflow-v3.7.4-real-e2e-v1");
        artifact.put("provider", config.name());
        artifact.put("protocol", config.protocol().name());
        artifact.put("model", config.model());
        artifact.put("promptVersions", List.of(
            SemanticScoutService.PROMPT_VERSION,
            FinalProfileSynthesisService.PROMPT_VERSION
        ));
        artifact.put("caseCount", results.size());
        artifact.put("passedCount", results.stream().filter(CaseResult::passed).count());
        artifact.put("results", results);
        mapper.writerWithDefaultPrettyPrinter().writeValue(
            output.resolve("project-understanding-real-e2e.json").toFile(),
            artifact
        );
        System.out.println(
            "REAL_E2E_AGGREGATE cases=" + results.size()
                + " passed=" + results.stream().filter(CaseResult::passed).count()
        );

        assertThat(results)
            .as("核心端到端 case 必须通过真实 refresh、Capability Provider 和持久化回读")
            .allMatch(CaseResult::passed);
    }

    private TestContext context(
        CaseSpec testCase,
        ProjectFlowRealModelEvalIT.ProviderConfig config,
        ObjectMapper mapper
    ) throws Exception {
        Path root = testCase.projectFlowItself()
            ? Path.of("..").toAbsolutePath().normalize()
            : tempRoot.resolve(testCase.id());
        if (!testCase.projectFlowItself()) {
            Files.createDirectories(root);
            testCase.fixture().create(root);
        }

        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        ProjectRepository projects = mock(ProjectRepository.class);
        ProjectMemoryRepository memories = mock(ProjectMemoryRepository.class);
        AiProviderRepository providers = mock(AiProviderRepository.class);
        ProjectStructureIndexRepository indexes = mock(ProjectStructureIndexRepository.class);
        ProjectUnderstandingSnapshotRepository snapshots = mock(ProjectUnderstandingSnapshotRepository.class);
        AtomicReference<ProjectStructureIndex> storedIndex = new AtomicReference<>();
        AtomicReference<ProjectUnderstandingSnapshot> storedSnapshot = new AtomicReference<>();

        ProjectSpace project = new ProjectSpace(userId);
        ReflectionTestUtils.setField(project, "id", projectId);
        project.update(
            testCase.id(),
            "V3.7.4 real end-to-end acceptance fixture",
            ProjectStatus.BUILDING,
            List.of(),
            "",
            LocalDate.now(),
            null
        );
        ProjectMemory memory = new ProjectMemory(projectId);
        memory.rememberLocalProjectPath(root.toString());
        AiProvider provider = provider(config, userId);

        when(projects.findByIdAndUserId(projectId, userId)).thenReturn(Optional.of(project));
        when(memories.findByProjectId(projectId)).thenReturn(Optional.of(memory));
        when(providers.findByUserIdOrderByDefaultEnabledDescUpdatedAtDesc(userId))
            .thenReturn(List.of(provider));
        when(indexes.findByProjectId(projectId)).thenAnswer(ignored -> Optional.ofNullable(storedIndex.get()));
        when(indexes.save(any(ProjectStructureIndex.class))).thenAnswer(invocation -> {
            ProjectStructureIndex value = invocation.getArgument(0);
            storedIndex.set(value);
            return value;
        });
        when(snapshots.findByProjectId(projectId)).thenAnswer(ignored -> Optional.ofNullable(storedSnapshot.get()));
        when(snapshots.save(any(ProjectUnderstandingSnapshot.class))).thenAnswer(invocation -> {
            ProjectUnderstandingSnapshot value = invocation.getArgument(0);
            storedSnapshot.set(value);
            return value;
        });

        FixedCommandExecutor commands = new FixedCommandExecutor();
        SccCodeMetricsAdapter scc = new SccCodeMetricsAdapter(commands, mapper);
        RepositoryIntakeService intake = new RepositoryIntakeService(commands, scc, mapper);
        ReflectionTestUtils.setField(intake, "maxFiles", 5000);
        ReflectionTestUtils.setField(intake, "maxFileDetails", 200);
        ReflectionTestUtils.setField(intake, "maxFileReadBytes", 8_388_608L);
        ReflectionTestUtils.setField(intake, "maxTotalReadBytes", 536_870_912L);
        ReflectionTestUtils.setField(intake, "smallLoc", 2000L);
        ReflectionTestUtils.setField(intake, "mediumLoc", 20_000L);
        ReflectionTestUtils.setField(intake, "largeLoc", 100_000L);

        SensitiveContentRedactor redactor = new SensitiveContentRedactor();
        ProjectEvidenceDiscoveryService discovery = new ProjectEvidenceDiscoveryService(redactor);
        ReflectionTestUtils.setField(discovery, "maxCandidates", 320);
        ReflectionTestUtils.setField(discovery, "maxScoutEvidence", 80);
        ReflectionTestUtils.setField(discovery, "maxSampleChars", 1600);
        ReflectionTestUtils.setField(discovery, "maxSampleBytes", 8192);
        AnalysisToolRegistry toolRegistry = new AnalysisToolRegistry();
        AdaptiveAnalysisPlanner planner = new AdaptiveAnalysisPlanner(toolRegistry);
        BudgetAwareContextPacker contextPacker = new BudgetAwareContextPacker(mapper, redactor);
        ReflectionTestUtils.setField(contextPacker, "maxChars", 48_000);

        ModelGatewayService gateway = new ModelGatewayService(
            mapper,
            new AiProviderUrlGuard(),
            new ModelOutputAdapter(mapper),
            config.timeoutSeconds()
        );
        SemanticScoutService semanticScout = new SemanticScoutService(gateway, contextPacker);
        ReflectionTestUtils.setField(semanticScout, "maxModelPromptChars", 48_000);
        BoundedLocalAnalysisCapabilityProvider localProvider =
            new BoundedLocalAnalysisCapabilityProvider(commands, redactor);
        AnalysisExecutionCoordinator coordinator =
            new AnalysisExecutionCoordinator(List.of(localProvider), redactor);
        FinalProfileSynthesisService finalSynthesis =
            new FinalProfileSynthesisService(gateway, contextPacker);
        ReflectionTestUtils.setField(finalSynthesis, "maxModelPromptChars", 48_000);

        ProjectUnderstandingService service = new ProjectUnderstandingService(
            projects,
            memories,
            providers,
            indexes,
            snapshots,
            new LocalProjectPathGuard(),
            intake,
            new CompositeProjectStructureIndexer(
                new ManifestFilesystemProjectStructureIndexer(),
                new ScipProjectStructureIndexer()
            ),
            mock(ProjectEvolutionBridgeService.class),
            discovery,
            new HistoricalCoverageService(commands, mock(ProjectFactCommitRefRepository.class)),
            planner,
            semanticScout,
            coordinator,
            finalSynthesis,
            new DynamicProjectProfileSynthesizer(),
            mapper
        );
        return new TestContext(userId, projectId, service);
    }

    private static AiProvider provider(
        ProjectFlowRealModelEvalIT.ProviderConfig config,
        UUID userId
    ) {
        AiProvider provider = new AiProvider(userId);
        provider.update(
            config.name(),
            config.baseUrl(),
            config.apiKey(),
            config.model(),
            config.type(),
            0.1,
            config.maxTokens(),
            true,
            List.of("V3.7.4_REAL_END_TO_END")
        );
        provider.configureProtocol(
            config.protocol(),
            null,
            null,
            null,
            null,
            Map.of(),
            config.timeoutSeconds(),
            null,
            null,
            null,
            config.supportsReasoning(),
            config.supportsReasoningControl()
        );
        return provider;
    }

    private static CaseResult successfulResult(
        CaseSpec testCase,
        ProjectUnderstandingSnapshotResponse snapshot,
        ProjectUnderstandingSnapshotResponse readback,
        ProjectStructureIndexResponse structure,
        List<String> progress,
        long latencyMs
    ) {
        List<String> failures = new ArrayList<>();
        Set<String> allowedEvidence = new LinkedHashSet<>();
        snapshot.sourceMap().sources().forEach(source -> allowedEvidence.add(source.id()));
        structure.evidence().forEach(evidence -> allowedEvidence.add(evidence.id()));
        if (snapshot.analysisExecution() != null) {
            snapshot.analysisExecution().evidence().forEach(evidence -> allowedEvidence.add(evidence.id()));
        }
        allowedEvidence.add("intake:scan");
        List<String> finalEvidenceRefs = snapshot.dynamicProfile().sections().stream()
            .flatMap(section -> section.claims().stream())
            .flatMap(claim -> claim.evidenceRefs().stream())
            .distinct()
            .toList();
        List<String> invalidRefs = finalEvidenceRefs.stream()
            .filter(ref -> !allowedEvidence.contains(ref))
            .toList();
        if (!invalidRefs.isEmpty()) failures.add("FINAL_PROFILE_UNKNOWN_EVIDENCE");
        if (readback == null || !snapshot.sourceRevision().equals(readback.sourceRevision())) {
            failures.add("SNAPSHOT_READBACK_MISMATCH");
        }
        int physicalRequests = snapshot.analysisMetrics().modelRequestCount();
        int logicalRequests = switch (snapshot.finalSynthesisStatus()) {
            case "SUCCEEDED", "FAILED_DEGRADED" -> 2;
            default -> 1;
        };
        if (logicalRequests < 1 || logicalRequests > 2) failures.add("LOGICAL_REQUESTS_OUT_OF_RANGE");
        if (snapshot.analysisExecution() == null) {
            failures.add("CAPABILITY_EXECUTION_MISSING");
        } else {
            if (!snapshot.analysisExecution().requestedCapabilities()
                .containsAll(snapshot.analysisExecution().executedCapabilities())) {
                failures.add("EXECUTION_NOT_IN_PLAN");
            }
            if (snapshot.analysisExecution().secondStageDecision().secondStageTriggered()
                && snapshot.analysisExecution().evidence().isEmpty()) {
                failures.add("STAGE_TWO_WITHOUT_PROVIDER_EVIDENCE");
            }
        }
        scenarioChecks(testCase.id(), snapshot, failures);

        List<String> sectionTypes = snapshot.dynamicProfile().sections().stream()
            .map(DynamicProfileSection::type)
            .distinct()
            .toList();
        return new CaseResult(
            testCase.id(),
            failures.isEmpty(),
            failures.isEmpty() ? "PASSED" : "FAILED",
            snapshot.classification(),
            snapshot.dynamicProfile().projectShapes(),
            snapshot.analysisPlan().toolsToInvoke(),
            snapshot.analysisExecution() == null
                ? List.of()
                : snapshot.analysisExecution().executedCapabilities(),
            snapshot.analysisExecution() == null
                ? 0
                : snapshot.analysisExecution().evidence().size(),
            snapshot.analysisExecution() != null
                && snapshot.analysisExecution().secondStageDecision().secondStageTriggered(),
            snapshot.analysisExecution() == null
                ? List.of()
                : snapshot.analysisExecution().secondStageDecision().triggerReasons(),
            snapshot.finalSynthesisStatus(),
            logicalRequests,
            physicalRequests,
            snapshot.analysisMetrics().totalTokens(),
            snapshot.historicalCoverage().historyAvailable(),
            sectionTypes,
            finalEvidenceRefs.size(),
            invalidRefs.size(),
            true,
            progress,
            failures,
            "",
            latencyMs
        );
    }

    private static void scenarioChecks(
        String caseId,
        ProjectUnderstandingSnapshotResponse snapshot,
        List<String> failures
    ) {
        List<String> shapes = snapshot.dynamicProfile().projectShapes();
        List<String> views = snapshot.dynamicProfile().applicableViews();
        List<String> executed = snapshot.analysisExecution() == null
            ? List.of()
            : snapshot.analysisExecution().executedCapabilities();
        switch (caseId) {
            case "strange-important-document" -> {
                if (!shapes.contains("DOCUMENT")) failures.add("DOCUMENT_SHAPE_MISSING");
                if (!executed.contains("DOC_READER")) failures.add("DOC_READER_NOT_EXECUTED");
                if (snapshot.sourceMap().deepReadCount() == 0) failures.add("DEEP_READ_EVIDENCE_MISSING");
            }
            case "small-script" -> {
                if (!shapes.contains("SCRIPT")) failures.add("SCRIPT_SHAPE_MISSING");
                if (views.contains("ARCHITECTURE")) failures.add("SMALL_SCRIPT_ARCHITECTURE_EXPANSION");
            }
            case "frontend-only" -> {
                if (!shapes.contains("FRONTEND")) failures.add("FRONTEND_SHAPE_MISSING");
                if (shapes.contains("BACKEND") || views.contains("BACKEND") || views.contains("DATABASE")) {
                    failures.add("FRONTEND_FALSE_BACKEND_OR_DATABASE");
                }
            }
            case "backend-only" -> {
                if (!shapes.contains("BACKEND")) failures.add("BACKEND_SHAPE_MISSING");
                if (shapes.contains("FRONTEND") || views.contains("FRONTEND")) {
                    failures.add("BACKEND_FALSE_FRONTEND");
                }
            }
            case "fullstack" -> {
                if (!shapes.contains("FRONTEND") || !shapes.contains("BACKEND")) {
                    failures.add("FULLSTACK_SHAPES_INCOMPLETE");
                }
            }
            case "no-git" -> {
                if (snapshot.historicalCoverage().historyAvailable()) failures.add("NO_GIT_FALSE_HISTORY");
                if (views.contains("TIMELINE") || views.contains("EVOLUTION")) {
                    failures.add("NO_GIT_FALSE_TIMELINE");
                }
            }
            case "agent-result" -> {
                if (!shapes.contains("AGENT_RESULT_MATERIAL")) failures.add("AGENT_RESULT_SHAPE_MISSING");
                boolean promoted = snapshot.dynamicProfile().sections().stream()
                    .flatMap(section -> section.claims().stream())
                    .anyMatch(claim ->
                        ProjectFlowEvalTextRules.containsUnnegatedMarker(claim.text(), "ProjectFact")
                            || ProjectFlowEvalTextRules.containsUnnegatedMarker(claim.text(), "已确认事实")
                    );
                if (promoted) failures.add("AGENT_RESULT_PROMOTED_TO_FACT");
            }
            case "projectflow-itself" -> {
                if (!shapes.contains("FRONTEND") && !shapes.contains("BACKEND")) {
                    failures.add("PROJECTFLOW_PRIMARY_SHAPE_MISSING");
                }
            }
            case "normal-git-project" -> {
                if (!snapshot.historicalCoverage().historyAvailable()) failures.add("NORMAL_GIT_HISTORY_MISSING");
                if (snapshot.sourceMap().sources().stream().noneMatch(source -> "TEST".equals(source.category()))) {
                    failures.add("NORMAL_GIT_TEST_EVIDENCE_MISSING");
                }
            }
            case "stale-readme" -> {
                boolean staleAware = snapshot.sourceMap().sources().stream()
                    .anyMatch(source -> "README".equals(source.category()));
                if (!staleAware) failures.add("STALE_README_NOT_DISCOVERED");
            }
            case "agent-result-conflict" -> {
                boolean promoted = snapshot.dynamicProfile().sections().stream()
                    .flatMap(section -> section.claims().stream())
                    .anyMatch(claim -> "VERIFIED".equals(claim.epistemicStatus()));
                if (promoted) failures.add("UNVERIFIED_AGENT_CLAIM_PROMOTED");
            }
            case "weird-extensionless" -> {
                if (snapshot.sourceMap().sources().stream()
                    .noneMatch(source -> "UNKNOWN_DOCUMENT".equals(source.category()))) {
                    failures.add("EXTENSIONLESS_DOCUMENT_MISSING");
                }
            }
            case "large-middle", "large-tail-revision" -> {
                boolean mapped = snapshot.sourceMap().sources().stream()
                    .anyMatch(source -> source.summary().contains("CONTENT_MAP"));
                if (!mapped) failures.add("LARGE_FILE_CONTENT_MAP_MISSING");
            }
            case "conflicting-final-docs" -> {
                if (snapshot.sourceMap().sources().stream()
                    .filter(source -> "DOC".equals(source.category())).count() < 2) {
                    failures.add("CONFLICTING_DOCUMENTS_NOT_DISCOVERED");
                }
            }
            case "multi-language-monorepo" -> {
                if (!shapes.contains("MONOREPO")) failures.add("MONOREPO_SHAPE_MISSING");
            }
            case "documentation-project" -> {
                if (!shapes.contains("DOCUMENT") && !shapes.contains("DOCUMENT_PROJECT")) {
                    failures.add("DOCUMENT_PROJECT_SHAPE_MISSING");
                }
            }
            default -> {
            }
        }
    }

    private static Set<String> requestedCaseIds() {
        String configured = System.getProperty("projectflow.eval.e2e.case-ids", "");
        if (configured.isBlank()) return Set.of();
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String value : configured.split(",")) {
            if (!value.isBlank()) result.add(value.strip());
        }
        return Set.copyOf(result);
    }

    private static CaseResult failedResult(
        String caseId,
        List<String> progress,
        long latencyMs,
        Throwable failure
    ) {
        return new CaseResult(
            caseId,
            false,
            "FAILED",
            "",
            List.of(),
            List.of(),
            List.of(),
            0,
            false,
            List.of(),
            "",
            0,
            0,
            0,
            false,
            List.of(),
            0,
            0,
            false,
            List.copyOf(progress),
            List.of("REFRESH_FAILED"),
            safeExceptionTypes(failure),
            latencyMs
        );
    }

    private static String safeExceptionTypes(Throwable failure) {
        List<String> types = new ArrayList<>();
        Throwable current = failure;
        while (current != null && types.size() < 5) {
            String type = current.getClass().getSimpleName();
            if (current instanceof ModelGatewayService.ModelResponseFormatException format
                && format.diagnostics() != null
            ) {
                var diagnostics = format.diagnostics();
                if (!diagnostics.failureCode().isBlank()) {
                    type += "[" + diagnostics.failureCode() + "]";
                }
                type += "{promptChars=" + diagnostics.promptSize()
                    + ",effectiveMax=" + diagnostics.effectiveMaxTokens()
                    + ",completionTokens=" + diagnostics.completionTokens()
                    + ",reasoningChars=" + diagnostics.reasoningLength()
                    + ",finish=" + diagnostics.normalizedFinishReason()
                    + "}";
            }
            types.add(type);
            current = current.getCause();
        }
        return String.join(">", types);
    }

    private static List<CaseSpec> fixtures() {
        return List.of(
            new CaseSpec("strange-important-document", false, root -> Files.writeString(
                root.resolve("fuck-this-bug.md"),
                "# 事故复盘\n批处理重试导致重复写入。回滚只撤销该批派生结果并保留既有事实。"
                    .repeat(40)
            )),
            new CaseSpec("small-script", false, root -> {
                Files.writeString(root.resolve("requirements.txt"), "requests==2.32.4\n");
                Files.writeString(
                    root.resolve("convert.py"),
                    "import csv\nimport sys\nwith open(sys.argv[1]) as source:\n"
                        + "    print(list(csv.DictReader(source)))\n"
                );
            }),
            new CaseSpec("frontend-only", false, root -> {
                Files.createDirectories(root.resolve("src"));
                Files.writeString(
                    root.resolve("package.json"),
                    "{\"scripts\":{\"build\":\"vite build\"},\"dependencies\":{\"react\":\"19.2.0\"}}"
                );
                Files.writeString(
                    root.resolve("src/App.tsx"),
                    "export function App(){return <main>Remote API dashboard</main>}\n"
                );
            }),
            new CaseSpec("backend-only", false, root -> {
                Files.createDirectories(root.resolve("src/main/java/demo"));
                Files.writeString(
                    root.resolve("pom.xml"),
                    "<project><modelVersion>4.0.0</modelVersion><groupId>demo</groupId>"
                        + "<artifactId>api</artifactId><version>1</version></project>"
                );
                Files.writeString(
                    root.resolve("src/main/java/demo/OrderController.java"),
                    "package demo; class OrderController { String list(){ return \"orders\"; } }\n"
                );
                Files.writeString(
                    root.resolve("src/main/java/demo/OrderRepository.java"),
                    "package demo; interface OrderRepository { }\n"
                );
            }),
            new CaseSpec("fullstack", false, root -> {
                Files.createDirectories(root.resolve("frontend/src"));
                Files.createDirectories(root.resolve("backend/src/main/java/demo"));
                Files.writeString(
                    root.resolve("frontend/package.json"),
                    "{\"dependencies\":{\"react\":\"19.2.0\"},\"scripts\":{\"build\":\"vite build\"}}"
                );
                Files.writeString(
                    root.resolve("frontend/src/App.tsx"),
                    "export const App=()=> <main>Orders</main>;\n"
                );
                Files.writeString(
                    root.resolve("backend/pom.xml"),
                    "<project><modelVersion>4.0.0</modelVersion><groupId>demo</groupId>"
                        + "<artifactId>api</artifactId><version>1</version></project>"
                );
                Files.writeString(
                    root.resolve("backend/src/main/java/demo/Api.java"),
                    "package demo; class Api { String orders(){ return \"orders\"; } }\n"
                );
            }),
            new CaseSpec("no-git", false, root -> {
                Files.createDirectories(root.resolve("src"));
                Files.writeString(root.resolve("README.md"), "# 当前工具\n本目录没有 Git 历史。\n");
                Files.writeString(root.resolve("src/main.py"), "print('current state only')\n");
            }),
            new CaseSpec("agent-result", false, root -> {
                Files.createDirectories(root.resolve(".projectflow/agent-results/e2e"));
                Files.writeString(
                    root.resolve(".projectflow/agent-results/e2e/result.json"),
                    """
                    {
                      "taskGoal":"验证导入边界",
                      "actualChanges":[{"summary":"候选过程结果，尚未进入 ProjectFact 链路"}],
                      "keyFiles":[],
                      "verification":{"build":"not_run","tests":"not_run","manualCheck":"not_run"},
                      "unfinished":[],
                      "sedimentCandidates":[]
                    }
                    """
                );
            }),
            new CaseSpec("normal-git-project", false, root -> {
                Files.createDirectories(root.resolve("src/test/java/demo"));
                Files.createDirectories(root.resolve(".github/workflows"));
                Files.writeString(root.resolve("README.md"), "# Orders API\n当前 Java 服务。\n");
                Files.writeString(
                    root.resolve("pom.xml"),
                    "<project><modelVersion>4.0.0</modelVersion><groupId>demo</groupId>"
                        + "<artifactId>orders</artifactId><version>1</version></project>"
                );
                Files.writeString(root.resolve("src/test/java/demo/OrdersTest.java"), "class OrdersTest {}\n");
                Files.writeString(root.resolve(".github/workflows/ci.yml"), "steps:\n  - run: mvn test\n");
                initializeGit(root);
            }),
            new CaseSpec("stale-readme", false, root -> {
                Files.createDirectories(root.resolve("src"));
                Files.writeString(root.resolve("README.md"), "# Current API\n默认监听 8080。\n");
                Files.writeString(root.resolve("src/server.ts"), "export const port = 9090;\n");
                Files.writeString(root.resolve("package.json"), "{\"scripts\":{\"test\":\"node --test\"}}\n");
            }),
            new CaseSpec("agent-result-conflict", false, root -> {
                Files.createDirectories(root.resolve(".projectflow/agent-results/conflict"));
                Files.createDirectories(root.resolve("reports"));
                Files.writeString(
                    root.resolve(".projectflow/agent-results/conflict/result.json"),
                    "{\"taskGoal\":\"迁移\",\"actualChanges\":[{\"summary\":\"迁移完成\"}],"
                        + "\"verification\":{\"tests\":\"passed\"}}"
                );
                Files.writeString(root.resolve("reports/ci.txt"), "migration-test: FAILED\n");
            }),
            new CaseSpec("weird-extensionless", false, root -> Files.writeString(
                root.resolve("不知道有没有用"),
                "导入失败时必须保留原始 ProjectFact，只回滚可重建的派生索引。\n".repeat(80)
            )),
            new CaseSpec("large-middle", false, root -> writeLargeFixture(
                root.resolve("HugeService.java"),
                80_000,
                40_000,
                "// CURRENT FACT: writes use idempotency keys backed by commit and file evidence.",
                -1,
                ""
            )),
            new CaseSpec("large-tail-revision", false, root -> writeLargeFixture(
                root.resolve("huge-spec.md"),
                80_020,
                1,
                "# Old statement\nThe current transport uses polling.",
                80_015,
                "# 2026-07 explicit revision\nCURRENT: event push replaces the earlier polling statement."
            )),
            new CaseSpec("conflicting-final-docs", false, root -> {
                Files.createDirectories(root.resolve("docs"));
                Files.writeString(root.resolve("docs/final-v2.md"), "# Final\nDefault database: SQLite.\n");
                Files.writeString(root.resolve("docs/final-really.md"), "# Final\nDefault database: PostgreSQL.\n");
            }),
            new CaseSpec("multi-language-monorepo", false, root -> {
                Files.createDirectories(root.resolve("apps/web/src"));
                Files.createDirectories(root.resolve("services/api/src"));
                Files.writeString(root.resolve("pnpm-workspace.yaml"), "packages:\n  - apps/*\n  - services/*\n");
                Files.writeString(root.resolve("apps/web/package.json"), "{\"dependencies\":{\"react\":\"19.2.0\"}}\n");
                Files.writeString(root.resolve("apps/web/src/App.tsx"), "export const App=()=> <main />;\n");
                Files.writeString(root.resolve("services/api/go.mod"), "module example/api\n\ngo 1.24\n");
                Files.writeString(root.resolve("services/api/src/main.go"), "package main\nfunc main() {}\n");
            }),
            new CaseSpec("documentation-project", false, root -> {
                Files.createDirectories(root.resolve("research"));
                Files.writeString(root.resolve("research/evidence-notes.md"), "# Evidence study\nNo implementation is claimed.\n");
                Files.writeString(root.resolve("bibliography.txt"), "MCP Specification 2025-11-25\n");
            }),
            new CaseSpec("projectflow-itself", true, root -> {
            })
        );
    }

    private static void writeLargeFixture(
        Path path,
        int lines,
        int firstMarkerLine,
        String firstMarker,
        int secondMarkerLine,
        String secondMarker
    ) throws Exception {
        StringBuilder content = new StringBuilder(lines * 36);
        for (int line = 1; line <= lines; line++) {
            if (line == firstMarkerLine) content.append(firstMarker);
            else if (line == secondMarkerLine) content.append(secondMarker);
            else content.append("line ").append(line).append(" repeated neutral fixture content");
            content.append('\n');
        }
        Files.writeString(path, content);
    }

    private static void initializeGit(Path root) throws Exception {
        runGit(root, "init", "-q");
        runGit(root, "config", "user.email", "projectflow-eval@example.invalid");
        runGit(root, "config", "user.name", "ProjectFlow Eval");
        runGit(root, "add", ".");
        runGit(root, "commit", "-q", "-m", "initial verified project fixture");
    }

    private static void runGit(Path root, String... arguments) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command)
            .directory(root.toFile())
            .redirectErrorStream(true)
            .start();
        if (process.waitFor() != 0) {
            throw new IllegalStateException("git fixture command failed");
        }
    }

    private static long elapsedMs(long started) {
        return Math.max(0, (System.nanoTime() - started) / 1_000_000);
    }

    @FunctionalInterface
    private interface Fixture {
        void create(Path root) throws Exception;
    }

    private record CaseSpec(String id, boolean projectFlowItself, Fixture fixture) {
    }

    private record TestContext(UUID userId, UUID projectId, ProjectUnderstandingService service) {
    }

    private record CaseResult(
        String caseId,
        boolean passed,
        String status,
        String classification,
        List<String> projectShapes,
        List<String> plannedCapabilities,
        List<String> executedCapabilities,
        int providerEvidenceCount,
        boolean secondStageTriggered,
        List<String> secondStageReasons,
        String finalSynthesisStatus,
        int logicalModelRequests,
        int physicalModelRequests,
        int totalTokens,
        boolean historyAvailable,
        List<String> dynamicSectionTypes,
        int finalEvidenceRefCount,
        int invalidEvidenceRefCount,
        boolean snapshotReadback,
        List<String> progressStages,
        List<String> failedChecks,
        String failureCategory,
        long latencyMs
    ) {
    }
}
