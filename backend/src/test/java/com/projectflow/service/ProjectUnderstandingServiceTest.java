package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.entity.AiProvider;
import com.projectflow.entity.AiProviderType;
import com.projectflow.entity.ProjectMemory;
import com.projectflow.entity.ProjectSpace;
import com.projectflow.entity.ProjectStatus;
import com.projectflow.entity.ProjectStructureIndex;
import com.projectflow.entity.ProjectUnderstandingSnapshot;
import com.projectflow.repository.AiProviderRepository;
import com.projectflow.repository.ProjectMemoryRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.repository.ProjectFactCommitRefRepository;
import com.projectflow.repository.ProjectStructureIndexRepository;
import com.projectflow.repository.ProjectUnderstandingSnapshotRepository;
import com.projectflow.support.AppException;

class ProjectUnderstandingServiceTest {
    @TempDir
    Path root;

    private final UUID userId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();
    private final UUID otherUserId = UUID.randomUUID();
    private final AtomicReference<List<AiProvider>> providers = new AtomicReference<>(List.of());
    private final AtomicReference<ProjectStructureIndex> storedIndex = new AtomicReference<>();
    private final AtomicReference<ProjectUnderstandingSnapshot> storedSnapshot = new AtomicReference<>();

    private ProjectUnderstandingService service;
    private ModelGatewayService gateway;

    @BeforeEach
    void setUp() {
        ProjectRepository projects = mock(ProjectRepository.class);
        ProjectMemoryRepository memories = mock(ProjectMemoryRepository.class);
        AiProviderRepository providerRepository = mock(AiProviderRepository.class);
        ProjectStructureIndexRepository indexes = mock(ProjectStructureIndexRepository.class);
        ProjectUnderstandingSnapshotRepository snapshots = mock(ProjectUnderstandingSnapshotRepository.class);
        gateway = mock(ModelGatewayService.class);

        ProjectSpace project = new ProjectSpace(userId);
        ReflectionTestUtils.setField(project, "id", projectId);
        project.update("Demo", "demo", ProjectStatus.BUILDING, List.of("Java"), "", LocalDate.now(), null);
        ProjectMemory memory = new ProjectMemory(projectId);
        memory.rememberLocalProjectPath(root.toString());

        when(projects.findByIdAndUserId(projectId, userId)).thenReturn(Optional.of(project));
        when(projects.findByIdAndUserId(projectId, otherUserId)).thenReturn(Optional.empty());
        when(memories.findByProjectId(projectId)).thenReturn(Optional.of(memory));
        when(providerRepository.findByUserIdOrderByDefaultEnabledDescUpdatedAtDesc(userId))
            .thenAnswer(ignored -> providers.get());
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

        LocalCommandExecutor commands = (directory, command, timeout) ->
            new LocalCommandExecutor.CommandResult(1, "", false);
        SccCodeMetricsAdapter scc = mock(SccCodeMetricsAdapter.class);
        when(scc.inspect(root)).thenReturn(SccCodeMetricsAdapter.CodeMetrics.unavailable());
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        RepositoryIntakeService intake = new RepositoryIntakeService(commands, scc, mapper);
        ReflectionTestUtils.setField(intake, "maxFiles", 1000);
        ReflectionTestUtils.setField(intake, "maxFileDetails", 100);
        ReflectionTestUtils.setField(intake, "maxFileReadBytes", 8_388_608L);
        ReflectionTestUtils.setField(intake, "maxTotalReadBytes", 536_870_912L);
        ReflectionTestUtils.setField(intake, "smallLoc", 20L);
        ReflectionTestUtils.setField(intake, "mediumLoc", 50L);
        ReflectionTestUtils.setField(intake, "largeLoc", 100L);
        ProjectEvidenceDiscoveryService evidenceDiscovery = new ProjectEvidenceDiscoveryService();
        ReflectionTestUtils.setField(evidenceDiscovery, "maxCandidates", 100);
        ReflectionTestUtils.setField(evidenceDiscovery, "maxScoutEvidence", 40);
        ReflectionTestUtils.setField(evidenceDiscovery, "maxSampleChars", 1600);
        ReflectionTestUtils.setField(evidenceDiscovery, "maxSampleBytes", 8192);
        AnalysisToolRegistry toolRegistry = new AnalysisToolRegistry();
        AdaptiveAnalysisPlanner planner = new AdaptiveAnalysisPlanner(toolRegistry);
        SemanticScoutService semanticScout = new SemanticScoutService(gateway, mapper);
        ReflectionTestUtils.setField(semanticScout, "maxModelPromptChars", 48_000);
        service = new ProjectUnderstandingService(
            projects,
            memories,
            providerRepository,
            indexes,
            snapshots,
            new LocalProjectPathGuard(),
            intake,
            new CompositeProjectStructureIndexer(
                new ManifestFilesystemProjectStructureIndexer(),
                new ScipProjectStructureIndexer()
            ),
            mock(ProjectEvolutionBridgeService.class),
            evidenceDiscovery,
            new HistoricalCoverageService(commands, mock(ProjectFactCommitRefRepository.class)),
            planner,
            semanticScout,
            new DynamicProjectProfileSynthesizer(),
            mapper
        );
    }

    @Test
    void noModelProducesDeterministicSnapshotAndUnchangedRefreshUsesCache() throws Exception {
        Files.createDirectories(root.resolve("src"));
        Files.writeString(root.resolve("src/main.java"), "class Main {}\n");

        var first = service.refresh(userId, projectId);
        var second = service.refresh(userId, projectId);

        assertThat(first.modelUsed()).isFalse();
        assertThat(first.snapshot().classification()).isEqualTo("CODE_NO_GIT");
        assertThat(first.snapshot().quality().semanticStatus()).isEqualTo("MODEL_UNAVAILABLE");
        assertThat(first.snapshot().evidenceCoverage().observedClaims()).isPositive();
        assertThat(second.cacheHit()).isTrue();
        assertThat(second.snapshot().quality().cacheHit()).isTrue();
        verify(gateway, never()).callStructured(any(), any(), any(ModelTaskType.class));
    }

    @Test
    void emptyDirectorySkipsModelAndDoesNotInventProfileSections() throws Exception {
        providers.set(List.of(provider()));

        var outcome = service.refresh(userId, projectId);

        assertThat(outcome.modelUsed()).isFalse();
        assertThat(outcome.snapshot().classification()).isEqualTo("EMPTY");
        assertThat(outcome.snapshot().dynamicProfile().sections()).isEmpty();
        assertThat(outcome.snapshot().historicalCoverage().historyAvailable()).isFalse();
        assertThat(outcome.snapshot().analysisPlan().semanticMode()).isEqualTo("SKIPPED_EMPTY");
        verify(gateway, never()).callStructured(any(), any(), any(ModelTaskType.class));
    }

    @Test
    void nonEmptyTextUsesBoundedSemanticScoutButEmptyTextDoesNot() throws Exception {
        providers.set(List.of(provider()));
        Files.writeString(root.resolve("fuck-this-bug.md"), "# 事故复盘\n这里记录真实故障原因和决定。\n");
        String content = """
            {
              "semanticScout":{
                "projectShapeHypotheses":[],"evidenceSourceAssessments":[],
                "applicableDimensions":["documentPurpose"],"recommendedToolCalls":["DOC_READER","DROP_DATABASE"],
                "unknowns":[],"skipCandidates":[],"potentialConflicts":[],"currentnessWarnings":[]
              },
              "dynamicProfile":{"summary":"文档型材料","sections":[]},
              "unknowns":[]
            }
            """;
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        when(gateway.callStructured(any(), any(), eq(ModelTaskType.PROJECT_UNDERSTANDING_SNAPSHOT)))
            .thenReturn(new ModelGatewayService.StructuredModelResponse(
                content,
                new ModelOutputAdapter(mapper).parse(content, ModelTaskType.PROJECT_UNDERSTANDING_SNAPSHOT)
            ));

        var outcome = service.refresh(userId, projectId);

        assertThat(outcome.modelUsed()).isTrue();
        assertThat(outcome.snapshot().classification()).isEqualTo("UNKNOWN_NON_CODE");
        assertThat(outcome.snapshot().sourceMap().scoutEvidenceCount()).isEqualTo(1);
        assertThat(outcome.snapshot().analysisPlan().toolsToInvoke()).contains("DOC_READER").doesNotContain("DROP_DATABASE");
        assertThat(outcome.snapshot().dynamicProfile().sections())
            .extracting(section -> section.type())
            .contains("DOCUMENT_OVERVIEW")
            .doesNotContain("CURRENT_STRUCTURE");
    }

    @Test
    void emptyTextHasNoSubstantiveProfileAndUsesZeroModelRequests() throws Exception {
        providers.set(List.of(provider()));
        Files.writeString(root.resolve("empty.txt"), "");

        var outcome = service.refresh(userId, projectId);

        assertThat(outcome.modelUsed()).isFalse();
        assertThat(outcome.snapshot().analysisPlan().semanticMode()).isEqualTo("SKIPPED_NO_SUBSTANTIVE_EVIDENCE");
        assertThat(outcome.snapshot().dynamicProfile().projectShapes()).containsExactly("EMPTY_CONTENT");
        assertThat(outcome.snapshot().dynamicProfile().sections()).isEmpty();
        verify(gateway, never()).callStructured(any(), any(), any(ModelTaskType.class));
    }

    @Test
    void changedRefreshPersistsAnIncrementalDirtySet() throws Exception {
        Files.createDirectories(root.resolve("src"));
        Files.writeString(root.resolve("src/main.java"), "class Main {}\n");
        service.refresh(userId, projectId);

        Files.writeString(root.resolve("src/next.java"), "class Next {}\n");
        var refreshed = service.refresh(userId, projectId);
        var index = service.getStructureIndex(userId, projectId);

        assertThat(refreshed.cacheHit()).isFalse();
        assertThat(index.delta().mode()).isEqualTo("INCREMENTAL_DIRTY_SET");
        assertThat(index.delta().addedFileCount()).isEqualTo(1);
        assertThat(index.delta().unchangedFileCount()).isPositive();
    }

    @Test
    void failedSemanticRefreshKeepsPreviousSnapshotAndMarksItStale() throws Exception {
        Files.createDirectories(root.resolve("src"));
        Files.writeString(root.resolve("src/main.java"), "class Main {}\n");
        service.refresh(userId, projectId);

        AiProvider provider = new AiProvider(userId);
        provider.update(
            "provider",
            "https://example.invalid",
            "test-key",
            "test-model",
            AiProviderType.OPENAI,
            0.1,
            2048,
            true,
            List.of()
        );
        providers.set(List.of(provider));
        Files.writeString(root.resolve("src/next.java"), "class Next {}\n");
        when(gateway.callStructured(any(), any(), any(ModelTaskType.class))).thenThrow(new IOException("offline"));

        assertThatThrownBy(() -> service.refresh(userId, projectId))
            .isInstanceOf(ProjectUnderstandingService.UnderstandingModelException.class)
            .hasMessageContaining("结构索引已更新");
        assertThat(service.get(userId, projectId).currentStatus()).isEqualTo("STALE");
        assertThat(storedIndex.get().getContentHash()).isNotEqualTo(storedSnapshot.get().getStructureHash());
    }

    @Test
    void readChecksProjectOwnershipBeforeReturningPersistedData() {
        assertThatThrownBy(() -> service.get(otherUserId, projectId))
            .isInstanceOf(AppException.class)
            .hasMessageContaining("项目不存在");
    }

    @Test
    void unknownModelEvidenceIsFilteredAndCannotReplaceDeterministicSections() throws Exception {
        Files.createDirectories(root.resolve("src"));
        Files.writeString(root.resolve("src/main.java"), "class Main {}\n");
        AiProvider provider = new AiProvider(userId);
        provider.update(
            "provider",
            "https://example.invalid",
            "test-key",
            "test-model",
            AiProviderType.OPENAI,
            0.1,
            2048,
            true,
            List.of()
        );
        providers.set(List.of(provider));
        String content = """
            {
              "semanticScout":{
                "projectShapeHypotheses":[{"shape":"FAKE","confidence":"HIGH","evidenceRefs":["unknown"],"reason":"无证据"}],
                "evidenceSourceAssessments":[],"applicableDimensions":[],"recommendedToolCalls":[],
                "unknowns":[],"skipCandidates":[],
                "potentialConflicts":[],"currentnessWarnings":[]
              },
              "dynamicProfile":{"summary":"无证据身份","sections":[
                {"id":"fake","type":"IDENTITY","title":"伪造","summary":"伪造",
                 "claims":[{"text":"伪造判断","confidence":"HIGH","evidenceRefs":["unknown"]}],
                 "confidence":"HIGH","displayPriority":10,"applicabilityReason":""}
              ]},
              "unknowns":[]
            }
            """;
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        when(gateway.callStructured(any(), any(), eq(ModelTaskType.PROJECT_UNDERSTANDING_SNAPSHOT)))
            .thenReturn(new ModelGatewayService.StructuredModelResponse(
                content,
                new ModelOutputAdapter(mapper).parse(content, ModelTaskType.PROJECT_UNDERSTANDING_SNAPSHOT)
            ));

        var outcome = service.refresh(userId, projectId);

        assertThat(outcome.snapshot().identity().summary()).contains("Demo");
        assertThat(outcome.snapshot().identity().claims()).allMatch(claim -> !"伪造判断".equals(claim.text()));
        assertThat(outcome.snapshot().quality().limitations()).contains("模型返回的未知证据引用已过滤");
    }

    private AiProvider provider() {
        AiProvider provider = new AiProvider(userId);
        provider.update(
            "provider",
            "https://example.invalid",
            "test-key",
            "test-model",
            AiProviderType.OPENAI,
            0.1,
            2048,
            true,
            List.of()
        );
        return provider;
    }
}
