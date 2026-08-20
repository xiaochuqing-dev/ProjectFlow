package com.projectflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.entity.AiProvider;
import com.projectflow.entity.AiProviderType;
import com.projectflow.entity.EvidenceConfidence;
import com.projectflow.entity.ProjectFact;
import com.projectflow.entity.ProjectFactEpistemicStatus;
import com.projectflow.entity.ProjectFactOrigin;
import com.projectflow.entity.ProjectFactRecordStatus;
import com.projectflow.entity.ProjectHistoryEvent.RewriteState;
import com.projectflow.entity.ProjectHistorySnapshot;
import com.projectflow.entity.ProjectMemory;
import com.projectflow.entity.ProjectSpace;
import com.projectflow.entity.ProjectStatus;
import com.projectflow.repository.AiProviderRepository;
import com.projectflow.repository.ProjectFactRepository;
import com.projectflow.repository.ProjectHistoryEventRepository;
import com.projectflow.repository.ProjectHistorySnapshotRepository;
import com.projectflow.repository.ProjectHistoryWindowCheckpointRepository;
import com.projectflow.repository.ProjectMemoryRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.service.ModelGatewayService;
import com.projectflow.service.ModelOutputAdapter;
import com.projectflow.service.ModelTaskType;
import com.projectflow.service.ProjectHistoryCorrectionService;
import com.projectflow.service.ProjectHistoryPromptBuilder;
import com.projectflow.service.ProjectHistoryReadService;
import com.projectflow.service.ProjectHistoryReconstructionService;
import com.projectflow.dto.ProjectHistoryDtos.HistoryCorrectionRequest;
import com.projectflow.support.AppException;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProjectHistoryReconstructionTest {
    @Autowired ProjectRepository projectRepository;
    @Autowired ProjectMemoryRepository memoryRepository;
    @Autowired ProjectHistoryEventRepository eventRepository;
    @Autowired ProjectHistorySnapshotRepository snapshotRepository;
    @Autowired ProjectHistoryWindowCheckpointRepository checkpointRepository;
    @Autowired ProjectFactRepository factRepository;
    @Autowired AiProviderRepository providerRepository;
    @Autowired ProjectHistoryReconstructionService reconstructionService;
    @Autowired ProjectHistoryCorrectionService correctionService;
    @Autowired ProjectHistoryReadService readService;
    @Autowired ModelOutputAdapter outputAdapter;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean ModelGatewayService modelGateway;

    @TempDir Path temporaryRoot;

    @Test
    void rebuildsSourceEventsStoriesAndCreatedModifiedRemovedRestoredThread() throws Exception {
        UUID userId = UUID.randomUUID();
        Path repository = temporaryRoot.resolve("chain");
        Files.createDirectories(repository);
        git(repository, "init", "-b", "master");
        git(repository, "config", "user.email", "history@example.com");
        git(repository, "config", "user.name", "History Fixture");
        Path auth = repository.resolve("src/AuthService.java");
        Files.createDirectories(auth.getParent());
        Files.writeString(auth, "class AuthService {}\n", StandardCharsets.UTF_8);
        commitAt(repository, "add authentication entry", Instant.parse("2024-01-01T00:00:00Z"));
        Files.writeString(auth, "class AuthService { boolean emailFallback; }\n", StandardCharsets.UTF_8);
        commitAt(repository, "support email fallback", Instant.parse("2024-01-02T00:00:00Z"));
        Files.delete(auth);
        commitAt(repository, "remove obsolete authentication entry", Instant.parse("2024-01-03T00:00:00Z"));
        Files.writeString(auth, "class AuthService { boolean restored; }\n", StandardCharsets.UTF_8);
        commitAt(repository, "restore authentication entry", Instant.parse("2024-01-04T00:00:00Z"));
        Files.move(auth, auth.resolveSibling("AuthGateway.java"));
        commitAt(repository, "rename authentication boundary", Instant.parse("2024-01-05T00:00:00Z"));

        ProjectSpace project = project(userId, "History Chain", repository);
        var result = reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);

        assertThat(result.cacheHit()).isFalse();
        ProjectHistorySnapshot snapshot = snapshotRepository.findByProjectId(project.getId()).orElseThrow();
        assertThat(snapshot.getStatus()).isEqualTo(ProjectHistorySnapshot.Status.READY);
        assertThat(snapshot.getSourceEventCount()).isGreaterThanOrEqualTo(10);
        assertThat(eventRepository.countByProjectIdAndRewriteState(project.getId(), RewriteState.CURRENT))
            .isEqualTo(snapshot.getSourceEventCount());

        var threads = readService.threads(userId, project.getId(), "auth", 0, 20);
        assertThat(threads.items()).hasSize(1);
        assertThat(threads.items().get(0).transitions())
            .containsSubsequence("CREATED", "MODIFIED", "REMOVED", "RESTORED", "RENAMED");
        assertThat(threads.items().get(0).storyRefs()).hasSizeGreaterThanOrEqualTo(3);

        var stories = readService.stories(userId, project.getId(), "auth", false, null, null, 0, 20);
        assertThat(stories.items()).allSatisfy(story -> {
            assertThat(story.humanTitle()).doesNotContain("优化了系统", "修改了相关文件");
            assertThat(story.reason()).isBlank();
            assertThat(story.reasonEvidenceRefs()).isEmpty();
            assertThat(story.eventRefs()).isNotEmpty();
            assertThat(story.evidenceRefs()).isNotEmpty();
        });
        assertThat(readService.events(
            userId, project.getId(), null, "FILE_CHANGE", "RESTORED", null, null, "CURRENT",
            "auth", false, null, null, 0, 20
        ).items()).hasSize(1);
        assertThat(readService.overview(userId, project.getId()).overview().recentChanges()).isNotEmpty();

        var cached = reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);
        assertThat(cached.cacheHit()).isTrue();
        assertThat(snapshotRepository.findByProjectId(project.getId()).orElseThrow().getSourceEventCount())
            .isEqualTo(snapshot.getSourceEventCount());
    }

    @Test
    void splitsOneGenericCommitAcrossIndependentSubjectsAndKeepsOwnershipIsolation() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        Path repository = temporaryRoot.resolve("split");
        Files.createDirectories(repository);
        git(repository, "init", "-b", "master");
        git(repository, "config", "user.email", "history@example.com");
        git(repository, "config", "user.name", "History Fixture");
        Files.createDirectories(repository.resolve("src"));
        Files.writeString(repository.resolve("src/AuthService.java"), "class AuthService {}\n");
        Files.writeString(repository.resolve("src/ExportService.java"), "class ExportService {}\n");
        Files.writeString(repository.resolve("src/CacheService.java"), "class CacheService {}\n");
        commit(repository, "update");
        ProjectSpace project = project(userId, "Three Changes", repository);

        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);
        var stories = readService.stories(userId, project.getId(), null, false, null, null, 0, 20);
        assertThat(stories.items().stream().map(item -> item.primarySubjectKey()).toList())
            .contains("auth", "export", "cache");
        assertThat(stories.items().stream().map(item -> item.primarySubjectKey()).distinct().count()).isGreaterThanOrEqualTo(3);
        var chapters = readService.chapters(userId, project.getId(), 0, 20).items();
        assertThat(chapters).hasSizeGreaterThanOrEqualTo(3);
        assertThat(chapters).allSatisfy(chapter -> assertThat(chapter.storyRefs().stream()
            .map(id -> stories.items().stream().filter(story -> story.id().equals(id)).findFirst().orElseThrow())
            .filter(story -> "PRIMARY".equals(story.role())).count()).isEqualTo(1));

        assertThatThrownBy(() -> readService.overview(otherUserId, project.getId()))
            .isInstanceOf(AppException.class)
            .hasMessageContaining("项目不存在");
    }

    @Test
    void reconstructsCurrentStateOnlyForDocumentProjectWithoutGitAndKeepsSensitiveContentUnread() throws Exception {
        UUID userId = UUID.randomUUID();
        Path projectRoot = temporaryRoot.resolve("documents");
        Files.createDirectories(projectRoot.resolve("materials"));
        Files.createDirectories(projectRoot.resolve("analysis"));
        Files.createDirectories(projectRoot.resolve("site"));
        Files.writeString(projectRoot.resolve("materials/chapter-one.md"), "公开章节内容\n", StandardCharsets.UTF_8);
        Files.write(projectRoot.resolve("materials/deck.pptx"), new byte[] {1, 2, 3, 4});
        Files.writeString(projectRoot.resolve("analysis/results.csv"), "metric,value\ncoverage,0.8\n", StandardCharsets.UTF_8);
        Files.writeString(projectRoot.resolve("site/index.html"), "<main>Project result</main>\n", StandardCharsets.UTF_8);
        Files.writeString(projectRoot.resolve(".env"), "API_TOKEN=should-never-be-read\n", StandardCharsets.UTF_8);
        ProjectSpace project = project(userId, "Document History", projectRoot);

        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);

        var overview = readService.overview(userId, project.getId());
        assertThat(overview.status()).isEqualTo("DEGRADED");
        assertThat(overview.coverage().currentness()).isEqualTo("CURRENT_STATE_ONLY");
        assertThat(overview.coverage().sourceCounts()).containsKeys("DOCUMENT", "FILESYSTEM");
        assertThat(readService.events(
            userId, project.getId(), "DOCUMENT", null, null, null, null, "CURRENT",
            null, false, null, null, 0, 20
        ).items()).hasSize(3);
        assertThat(readService.events(
            userId, project.getId(), "FILESYSTEM", null, null, null, null, "CURRENT",
            "site", false, null, null, 0, 20
        ).items()).hasSize(1);
        var sensitive = readService.events(
            userId, project.getId(), null, null, null, null, null, "CURRENT",
            "sensitive-material", false, null, null, 0, 20
        ).items();
        assertThat(sensitive).hasSize(1);
        assertThat(sensitive.get(0).safeSourceLabel()).isEqualTo("检测到敏感材料元数据");
        assertThat(sensitive.get(0).safeSourceLabel()).doesNotContain("should-never-be-read", projectRoot.toString());
        assertThat(readService.stories(userId, project.getId(), "sensitive-material", false, null, null, 0, 20).items())
            .isEmpty();
    }

    @Test
    void keepsDependencyAndSensitiveEventsRawOnlyAndOutOfModelContract() throws Exception {
        UUID userId = UUID.randomUUID();
        Path repository = temporaryRoot.resolve("raw-only");
        Files.createDirectories(repository);
        git(repository, "init", "-b", "master");
        git(repository, "config", "user.email", "history@example.com");
        git(repository, "config", "user.name", "History Fixture");
        ProjectSpace project = project(userId, "Raw Only History", repository);
        Files.writeString(repository.resolve("package-lock.json"), "{\"lockfileVersion\":3}\n", StandardCharsets.UTF_8);
        Files.writeString(repository.resolve(".env"), "API_TOKEN=never-read\n", StandardCharsets.UTF_8);
        Files.createDirectories(repository.resolve(".projectflow/context"));
        Files.writeString(repository.resolve(".projectflow/context/generated.md"), "managed projection metadata\n", StandardCharsets.UTF_8);
        Path managedProjection = repository.resolve("KnowledgeBase/ProjectFlowHistory");
        Files.createDirectories(managedProjection);
        Files.writeString(
            managedProjection.resolve(".projectflow-manifest.json"),
            objectMapper.writeValueAsString(Map.of("projectId", project.getId().toString(), "files", Map.of())) + "\n",
            StandardCharsets.UTF_8
        );
        Files.writeString(managedProjection.resolve("项目概览.md"), "generated Obsidian projection\n", StandardCharsets.UTF_8);
        commit(repository, "update");
        provider(userId);
        AtomicInteger calls = new AtomicInteger();
        when(modelGateway.callStructured(any(), any(), any())).thenAnswer(invocation -> {
            calls.incrementAndGet();
            return modelResponse(historyModelResponse(invocation.getArgument(1, String.class)));
        });

        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);

        var overview = readService.overview(userId, project.getId());
        var rawEvents = readService.events(
            userId, project.getId(), null, null, null, null, null, "CURRENT",
            null, false, null, null, 0, 20
        ).items();
        assertThat(overview.sourceEventCount()).isGreaterThanOrEqualTo(3);
        assertThat(((Number) overview.diagnostics().get("rawOnlyEventCount")).intValue())
            .as("raw events: %s", rawEvents)
            .isGreaterThanOrEqualTo(5);
        assertThat(readService.events(
            userId, project.getId(), null, null, null, null, null, "CURRENT",
            "projectflow-metadata", false, null, null, 0, 20
        ).items()).anySatisfy(item -> assertThat(item.affectedPaths())
            .anyMatch(path -> path.startsWith("KnowledgeBase/ProjectFlowHistory/")));
        assertThat(readService.stories(userId, project.getId(), null, false, null, null, 0, 20).items()).isEmpty();
        assertThat(calls.get()).isZero();
        assertThat(ModelTaskType.PROJECT_HISTORY_SYNTHESIS.minimalSchema())
            .doesNotContain("beforeState", "change\"", "afterState", "laterOutcome");
    }

    @Test
    void keepsRawOnlyTailChangesOutOfIncrementalStoriesAndModelInput() throws Exception {
        UUID userId = UUID.randomUUID();
        Path repository = temporaryRoot.resolve("incremental-raw-only");
        Files.createDirectories(repository.resolve("src"));
        git(repository, "init", "-b", "master");
        git(repository, "config", "user.email", "history@example.com");
        git(repository, "config", "user.name", "History Fixture");
        Files.writeString(repository.resolve("src/AuthService.java"), "class AuthService {}\n");
        commitAt(repository, "add authentication", Instant.parse("2024-01-01T00:00:00Z"));
        ProjectSpace project = project(userId, "Incremental Raw Only", repository);
        provider(userId);
        AtomicInteger calls = new AtomicInteger();
        when(modelGateway.callStructured(any(), any(), any())).thenAnswer(invocation -> {
            calls.incrementAndGet();
            return modelResponse(historyModelResponse(invocation.getArgument(1, String.class)));
        });

        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);
        List<String> originalStories = readService.stories(userId, project.getId(), null, false, null, null, 0, 100)
            .items().stream().map(item -> item.id()).toList();
        assertThat(calls.get()).isEqualTo(1);

        Files.writeString(repository.resolve("package-lock.json"), "{\"lockfileVersion\":3}\n");
        commitAt(repository, "update", Instant.parse("2025-01-01T00:00:00Z"));
        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);

        assertThat(readService.stories(userId, project.getId(), null, false, null, null, 0, 100)
            .items().stream().map(item -> item.id()).toList()).containsExactlyElementsOf(originalStories);
        assertThat(calls.get()).isEqualTo(1);
        assertThat(((Number) readService.overview(userId, project.getId()).diagnostics().get("rawOnlyEventCount")).intValue())
            .isGreaterThanOrEqualTo(2);
    }

    @Test
    void sanitizesAbsolutePathsFromProjectFactsBeforePersistencePromptAndReadApi() throws Exception {
        UUID userId = UUID.randomUUID();
        Path repository = temporaryRoot.resolve("absolute-paths");
        Files.createDirectories(repository.resolve("src"));
        git(repository, "init", "-b", "master");
        git(repository, "config", "user.email", "history@example.com");
        git(repository, "config", "user.name", "History Fixture");
        Files.writeString(repository.resolve("src/AuthService.java"), "class AuthService {}\n");
        commit(repository, "add authentication");
        ProjectSpace project = project(userId, "Absolute Path Safety", repository);
        String windowsPath = "C:\\Users\\private-user\\secret\\evidence.txt";
        String unixPath = "/home/private-user/secret/evidence.txt";
        ProjectFact fact = new ProjectFact(
            project.getId(), null, null, ProjectFactOrigin.INCREMENTAL_SCAN, "1".repeat(64)
        );
        fact.updateContent(
            "记录认证结果", "来源事实确认认证结果已形成。", List.of("形成认证结果"), "认证结果可追溯。",
            Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"),
            List.of(), List.of(), List.of(), List.of(windowsPath, unixPath),
            List.of("file:" + windowsPath, "document:" + unixPath), "LOCAL_RULE", "PASS",
            EvidenceConfidence.HIGH, ProjectFactRecordStatus.RECORDED, ""
        );
        fact.applyKnowledgeContract(
            ProjectFactEpistemicStatus.OBSERVED, List.of("FILESYSTEM"), "CURRENT", "fact-revision",
            Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"),
            List.of("原始材料位于 " + windowsPath, "历史材料位于 " + unixPath), List.of(),
            "ENGINEERING_VALIDATION", "", "", "VALIDATED"
        );
        factRepository.saveAndFlush(fact);
        provider(userId);
        AtomicReference<String> capturedPrompt = new AtomicReference<>();
        when(modelGateway.callStructured(any(), any(), any())).thenAnswer(invocation -> {
            String prompt = invocation.getArgument(1, String.class);
            capturedPrompt.set(prompt);
            return modelResponse(historyModelResponse(prompt));
        });

        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);

        assertThat(capturedPrompt.get()).doesNotContain(
            windowsPath, unixPath, "C:\\Users\\private-user", "/home/private-user", "ABSOLUTE_PATH_REDACTED"
        );
        String apiJson = objectMapper.writeValueAsString(readService.events(
            userId, project.getId(), "PROJECT_FACT", null, null, null, null, "CURRENT",
            null, false, null, null, 0, 20
        ));
        assertThat(apiJson).doesNotContain(windowsPath, unixPath, "C:\\\\Users", "/home/private-user")
            .contains("ABSOLUTE_PATH_REDACTED");
    }

    @Test
    void keepsUnchangedWorktreeEventIdentityStableWhenAnotherFileChanges() throws Exception {
        UUID userId = UUID.randomUUID();
        Path repository = temporaryRoot.resolve("worktree-revision");
        Files.createDirectories(repository.resolve("src"));
        git(repository, "init", "-b", "master");
        git(repository, "config", "user.email", "history@example.com");
        git(repository, "config", "user.name", "History Fixture");
        Path first = repository.resolve("src/First.java");
        Path second = repository.resolve("src/Second.java");
        Files.writeString(first, "class First {}\n");
        Files.writeString(second, "class Second {}\n");
        commit(repository, "add two project elements");
        ProjectSpace project = project(userId, "Worktree Identity", repository);

        Files.writeString(first, "class First { int value = 1; }\n");
        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);
        String firstKey = readService.events(
            userId, project.getId(), "FILESYSTEM", null, null, null, null, "CURRENT",
            "first", false, null, null, 0, 20
        ).items().stream().filter(item -> item.affectedPaths().contains("src/First.java")).findFirst().orElseThrow().stableEventKey();

        Files.writeString(second, "class Second { int value = 2; }\n");
        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);
        String unchangedFirstKey = readService.events(
            userId, project.getId(), "FILESYSTEM", null, null, null, null, "CURRENT",
            "first", false, null, null, 0, 20
        ).items().stream().filter(item -> item.affectedPaths().contains("src/First.java")).findFirst().orElseThrow().stableEventKey();

        assertThat(unchangedFirstKey).isEqualTo(firstKey);
    }

    @Test
    void keepsCommitAndBoundaryFileEventTogetherInsideOneChangeStory() throws Exception {
        UUID userId = UUID.randomUUID();
        Path repository = temporaryRoot.resolve("same-commit-grouping");
        Files.createDirectories(repository.resolve("src"));
        git(repository, "init", "-b", "master");
        git(repository, "config", "user.email", "history@example.com");
        git(repository, "config", "user.name", "History Fixture");
        Path auth = repository.resolve("src/AuthService.java");
        Files.writeString(auth, "class AuthService {}\n");
        commitAt(repository, "add authentication", Instant.parse("2024-01-01T00:00:00Z"));
        Files.delete(auth);
        commitAt(repository, "remove authentication", Instant.parse("2024-02-01T00:00:00Z"));
        ProjectSpace project = project(userId, "Same Commit Grouping", repository);

        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);

        var removedStories = readService.stories(userId, project.getId(), "auth", false, null, null, 0, 20).items().stream()
            .filter(item -> item.humanTitle().startsWith("移除"))
            .toList();
        assertThat(removedStories).hasSize(1);
        assertThat(removedStories.get(0).rawEventCount()).isGreaterThanOrEqualTo(2);
        assertThat(readService.story(userId, project.getId(), removedStories.get(0).id()).events().stream()
            .map(item -> item.category()).toList()).contains("COMMIT", "FILE_CHANGE");
    }

    @Test
    void groupsMultipleCommitsForOneSubjectIntoOneBoundedChangeStory() throws Exception {
        UUID userId = UUID.randomUUID();
        Path repository = temporaryRoot.resolve("multi-commit-story");
        Files.createDirectories(repository.resolve("src"));
        git(repository, "init", "-b", "master");
        git(repository, "config", "user.email", "history@example.com");
        git(repository, "config", "user.name", "History Fixture");
        Path export = repository.resolve("src/ExportService.java");
        Files.writeString(export, "class ExportService {}\n");
        commitAt(repository, "start export result", Instant.parse("2025-01-01T00:00:00Z"));
        Files.writeString(export, "class ExportService { boolean markdown; }\n");
        commitAt(repository, "add markdown output", Instant.parse("2025-01-03T00:00:00Z"));
        Files.writeString(export, "class ExportService { boolean markdown; boolean pdf; }\n");
        commitAt(repository, "finish pdf output", Instant.parse("2025-01-05T00:00:00Z"));
        ProjectSpace project = project(userId, "Multi Commit Story", repository);

        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);

        var stories = readService.stories(userId, project.getId(), "export", false, null, null, 0, 20).items();
        assertThat(stories).hasSize(1);
        assertThat(stories.get(0).rawEventCount()).isGreaterThanOrEqualTo(6);
        assertThat(stories.get(0).occurredFrom()).isEqualTo(Instant.parse("2025-01-01T00:00:00Z"));
        assertThat(stories.get(0).occurredTo()).isEqualTo(Instant.parse("2025-01-05T00:00:00Z"));
    }

    @Test
    void foldsLargeCrossAreaImportIntoReadableAreaStoriesInsteadOfFileNameStories() throws Exception {
        UUID userId = UUID.randomUUID();
        Path repository = temporaryRoot.resolve("large-cross-area-import");
        Files.createDirectories(repository);
        git(repository, "init", "-b", "master");
        git(repository, "config", "user.email", "history@example.com");
        git(repository, "config", "user.name", "History Fixture");
        git(repository, "config", "core.autocrlf", "false");
        for (int index = 1; index <= 18; index++) {
            Path backendFile = repository.resolve("backend/src/BackendModule%02d.java".formatted(index));
            Path frontendFile = repository.resolve("frontend/src/FeaturePage%02d.tsx".formatted(index));
            Path documentFile = repository.resolve("docs/guide-%02d.md".formatted(index));
            Files.createDirectories(backendFile.getParent());
            Files.createDirectories(frontendFile.getParent());
            Files.createDirectories(documentFile.getParent());
            Files.writeString(backendFile, "class BackendModule%02d {}\n".formatted(index));
            Files.writeString(
                frontendFile,
                "export default function FeaturePage%02d() { return null }\n".formatted(index)
            );
            Files.writeString(documentFile, "# Guide %02d\n".formatted(index));
        }
        commitAt(repository, "import project history workspace", Instant.parse("2025-03-01T00:00:00Z"));
        ProjectSpace project = project(userId, "Large Cross Area Import", repository);

        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);

        var stories = readService.stories(userId, project.getId(), null, false, null, null, 0, 100);
        assertThat(stories.totalElements())
            .as("stories=%s", stories.items().stream()
                .map(item -> item.primarySubjectKey() + ":" + item.humanTitle()).toList())
            .isLessThanOrEqualTo(12);
        assertThat(stories.items()).extracting(item -> item.primarySubjectKey())
            .contains("project-area-backend", "project-area-frontend", "project-area-docs");
        assertThat(stories.items()).extracting(item -> item.humanTitle())
            .noneMatch(title -> title.matches(".*(BackendModule|FeaturePage|guide-\\d+|后端区域|前端区域|Controller|Service).*"));
        assertThat(stories.items()).extracting(item -> item.humanTitle())
            .anyMatch(title -> title.contains("前端项目骨架"))
            .anyMatch(title -> title.contains("后端项目骨架"))
            .anyMatch(title -> title.contains("项目文档"));
    }

    @Test
    void keepsNonCodeArtifactsAsPrimaryResultsWithNeutralFirstLayerLanguage() throws Exception {
        UUID userId = UUID.randomUUID();
        Path repository = temporaryRoot.resolve("non-code-artifacts");
        Files.createDirectories(repository);
        git(repository, "init", "-b", "master");
        git(repository, "config", "user.email", "history@example.com");
        git(repository, "config", "user.name", "History Fixture");
        List<List<String>> artifacts = List.of(
            List.of("slides/quarterly-review.pptx", "quarterly presentation"),
            List.of("paper/research-conclusion.md", "research conclusion"),
            List.of("analysis/revenue-summary.csv", "revenue analysis"),
            List.of("media/final-cut.mp4", "final video"),
            List.of("design/brand-system.fig", "brand design"),
            List.of("site/index.html", "campaign page")
        );
        Instant first = Instant.parse("2025-04-01T00:00:00Z");
        for (int index = 0; index < artifacts.size(); index++) {
            Path file = repository.resolve(artifacts.get(index).get(0));
            Files.createDirectories(file.getParent());
            Files.writeString(file, "artifact-" + index, StandardCharsets.UTF_8);
            commitAt(repository, "add " + artifacts.get(index).get(1), first.plusSeconds(index * 86_400L));
        }
        ProjectSpace project = project(userId, "Non-code artifacts", repository);

        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);

        var visible = readService.stories(userId, project.getId(), null, false, null, null, 0, 100).items();
        String firstLayer = visible.stream().map(story -> String.join(" ", story.humanTitle(), story.oneSentenceSummary(),
            story.beforeState(), story.change(), story.afterState())).collect(java.util.stream.Collectors.joining(" "));
        assertThat(visible).allSatisfy(story -> assertThat(story.role()).isEqualTo("PRIMARY"));
        assertThat(firstLayer).contains("演示文稿", "文档", "数据分析结果", "视频", "设计稿", "页面")
            .doesNotContain("能力", "后端", "Controller", "Service", "Repository", "ENGINEERING_GROUPING");
    }

    @Test
    void genericCommitsLowerConfidenceAndFoldExplicitTestsAndConfigurationUnderThePrimaryResult() throws Exception {
        UUID userId = UUID.randomUUID();
        Path repository = temporaryRoot.resolve("generic-supporting-work");
        Files.createDirectories(repository);
        git(repository, "init", "-b", "master");
        git(repository, "config", "user.email", "history@example.com");
        git(repository, "config", "user.name", "History Fixture");
        Path page = repository.resolve("site/overview.html");
        Files.createDirectories(page.getParent());
        Files.writeString(page, "<main>overview</main>", StandardCharsets.UTF_8);
        commitAt(repository, "launch project overview", Instant.parse("2025-05-01T00:00:00Z"));
        Path test = repository.resolve("tests/overview-validation.json");
        Files.createDirectories(test.getParent());
        Files.writeString(test, "{\"valid\":true}", StandardCharsets.UTF_8);
        commitAt(repository, "fix", Instant.parse("2025-05-02T00:00:00Z"));
        Path config = repository.resolve("config/site.json");
        Files.createDirectories(config.getParent());
        Files.writeString(config, "{\"title\":\"Overview\"}", StandardCharsets.UTF_8);
        commitAt(repository, "update", Instant.parse("2025-05-03T00:00:00Z"));
        ProjectSpace project = project(userId, "Generic supporting work", repository);

        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);

        var snapshot = snapshotRepository.findByProjectId(project.getId()).orElseThrow();
        var corrected = correctionService.resolve(project.getId(), snapshot);
        var supporting = corrected.stories().stream().filter(story -> story.technicalDetails().stream().anyMatch(path ->
            path.startsWith("tests/") || path.startsWith("config/"))).toList();
        assertThat(supporting).isNotEmpty().allSatisfy(story -> {
            assertThat(story.role()).isEqualTo("SUPPORTING");
            assertThat(story.primaryStoryId()).isNotBlank();
        });
        assertThat(corrected.stories()).filteredOn(story -> story.role().equals("PRIMARY"))
            .anySatisfy(primary -> assertThat(primary.supportingChangeRefs()).containsAll(
                supporting.stream().map(item -> item.id()).toList()
            ));
        assertThat(corrected.stories()).extracting(item -> item.humanTitle())
            .noneMatch(title -> title.toLowerCase().contains("fix 相关变化")
                || title.toLowerCase().contains("update 相关变化"));
    }

    @Test
    void rejectsReversedTimeRangesAndSafelyBoundsExtremeSnapshotPages() throws Exception {
        UUID userId = UUID.randomUUID();
        Path repository = temporaryRoot.resolve("read-bounds");
        Files.createDirectories(repository);
        git(repository, "init", "-b", "master");
        git(repository, "config", "user.email", "history@example.com");
        git(repository, "config", "user.name", "History Fixture");
        Files.writeString(repository.resolve("README.md"), "history\n", StandardCharsets.UTF_8);
        commit(repository, "add readable history");
        ProjectSpace project = project(userId, "Read Bounds", repository);
        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);
        Instant from = Instant.parse("2025-02-01T00:00:00Z");
        Instant to = Instant.parse("2025-01-01T00:00:00Z");

        assertThatThrownBy(() -> readService.stories(userId, project.getId(), null, false, from, to, 0, 20))
            .isInstanceOf(AppException.class).hasMessageContaining("起始时间");
        assertThatThrownBy(() -> readService.events(
            userId, project.getId(), null, null, null, null, null, null,
            null, false, from, to, 0, 20
        )).isInstanceOf(AppException.class).hasMessageContaining("起始时间");
        assertThat(readService.chapters(userId, project.getId(), Integer.MAX_VALUE, 100).items()).isEmpty();
    }

    @Test
    void reusesUnaffectedStoriesWhenOnlyASeparatedTailWindowChanges() throws Exception {
        UUID userId = UUID.randomUUID();
        Path repository = temporaryRoot.resolve("incremental");
        Files.createDirectories(repository.resolve("src"));
        git(repository, "init", "-b", "master");
        git(repository, "config", "user.email", "history@example.com");
        git(repository, "config", "user.name", "History Fixture");
        Path auth = repository.resolve("src/AuthService.java");
        Files.writeString(auth, "class AuthService {}\n");
        commitAt(repository, "add authentication", Instant.parse("2024-01-01T00:00:00Z"));
        Files.writeString(auth, "class AuthService { boolean email; }\n");
        commitAt(repository, "add email fallback", Instant.parse("2024-03-01T00:00:00Z"));
        ProjectSpace project = project(userId, "Incremental History", repository);
        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);

        Files.writeString(auth, "class AuthService { boolean email; boolean oauth; }\n");
        commitAt(repository, "add oauth path", Instant.parse("2025-01-01T00:00:00Z"));
        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);

        var diagnostics = readService.overview(userId, project.getId()).diagnostics();
        assertThat(diagnostics.get("reconstructionMode")).isEqualTo("INCREMENTAL_OVERLAP_WINDOW");
        assertThat(((Number) diagnostics.get("reusedStoryCount")).intValue()).isGreaterThanOrEqualTo(2);
        assertThat(((Number) diagnostics.get("recomputedStoryCount")).intValue()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void invalidatesEventsRemovedByHistoryRewriteWithoutDeletingTheirEvidence() throws Exception {
        UUID userId = UUID.randomUUID();
        Path repository = temporaryRoot.resolve("rewrite");
        Files.createDirectories(repository.resolve("src"));
        git(repository, "init", "-b", "master");
        git(repository, "config", "user.email", "history@example.com");
        git(repository, "config", "user.name", "History Fixture");
        Path auth = repository.resolve("src/AuthService.java");
        Files.writeString(auth, "class AuthService {}\n");
        commitAt(repository, "add authentication", Instant.parse("2024-01-01T00:00:00Z"));
        Files.writeString(auth, "class AuthService { boolean legacy; }\n");
        commitAt(repository, "add legacy path", Instant.parse("2024-02-01T00:00:00Z"));
        ProjectSpace project = project(userId, "Rewrite History", repository);
        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);
        long before = eventRepository.countByProjectIdAndRewriteState(project.getId(), RewriteState.CURRENT);

        git(repository, "reset", "--hard", "HEAD~1");
        Files.writeString(auth, "class AuthService { boolean replacement; }\n");
        commitAt(repository, "replace legacy path", Instant.parse("2024-03-01T00:00:00Z"));
        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);

        var overview = readService.overview(userId, project.getId());
        assertThat(((Number) overview.diagnostics().get("invalidatedEventCount")).intValue()).isGreaterThan(0);
        assertThat(overview.diagnostics().get("rewriteMode")).isEqualTo("PARTIAL_REWRITE");
        assertThat(eventRepository.countByProjectIdAndRewriteState(project.getId(), RewriteState.INVALIDATED)).isGreaterThan(0);
        assertThat(eventRepository.findByProjectId(project.getId()).size()).isGreaterThan((int) before);

        int cumulativeInvalidated = ((Number) overview.diagnostics().get("invalidatedEventCount")).intValue();
        Files.writeString(auth, "class AuthService { boolean replacement; boolean stable; }\n");
        commitAt(repository, "continue replacement path", Instant.parse("2024-04-01T00:00:00Z"));
        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);
        var afterAppend = readService.overview(userId, project.getId()).diagnostics();
        assertThat(((Number) afterAppend.get("invalidatedEventCount")).intValue()).isEqualTo(cumulativeInvalidated);
        assertThat(((Number) afterAppend.get("newlyInvalidatedEventCount")).intValue()).isZero();
    }

    @Test
    void rebindingProjectPathInvalidatesOldSourceEventsAndKeepsNewHistoryIsolated() throws Exception {
        UUID userId = UUID.randomUUID();
        Path firstRepository = temporaryRoot.resolve("rebind-first");
        Path secondRepository = temporaryRoot.resolve("rebind-second");
        for (Path repository : List.of(firstRepository, secondRepository)) {
            Files.createDirectories(repository);
            git(repository, "init", "-b", "master");
            git(repository, "config", "user.email", "history@example.com");
            git(repository, "config", "user.name", "History Fixture");
        }
        Files.writeString(firstRepository.resolve("FirstHistory.md"), "first history\n");
        commitAt(firstRepository, "record first project history", Instant.parse("2024-01-01T00:00:00Z"));
        Files.writeString(secondRepository.resolve("SecondHistory.md"), "second history\n");
        commitAt(secondRepository, "record second project history", Instant.parse("2025-01-01T00:00:00Z"));
        ProjectSpace project = project(userId, "Rebound History", firstRepository);

        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);
        int firstCurrentCount = (int) eventRepository.countByProjectIdAndRewriteState(project.getId(), RewriteState.CURRENT);
        ProjectMemory memory = memoryRepository.findByProjectId(project.getId()).orElseThrow();
        memory.rememberLocalProjectPath(secondRepository.toAbsolutePath().normalize().toString());
        memoryRepository.saveAndFlush(memory);

        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);

        var current = readService.events(
            userId, project.getId(), "GIT", null, null, null, null, "CURRENT",
            null, false, null, null, 0, 100
        ).items();
        assertThat(current).anyMatch(item -> item.safeSourceLabel().contains("second project history"));
        assertThat(current).noneMatch(item -> item.safeSourceLabel().contains("first project history"));
        assertThat(eventRepository.countByProjectIdAndRewriteState(project.getId(), RewriteState.INVALIDATED))
            .isGreaterThanOrEqualTo(firstCurrentCount);
        assertThat(readService.overview(userId, project.getId()).diagnostics().get("rewriteMode"))
            .isEqualTo("PARTIAL_REWRITE");
    }

    @Test
    void recognizesLinkedGitWorktreeWhereDotGitIsAFile() throws Exception {
        UUID userId = UUID.randomUUID();
        Path repository = temporaryRoot.resolve("main-repository");
        Files.createDirectories(repository);
        git(repository, "init", "-b", "master");
        git(repository, "config", "user.email", "history@example.com");
        git(repository, "config", "user.name", "History Fixture");
        Files.writeString(repository.resolve("README.md"), "history\n");
        commit(repository, "add project history");
        Path worktree = temporaryRoot.resolve("linked-worktree");
        git(repository, "worktree", "add", "-b", "history-linked", worktree.toString());
        assertThat(Files.isRegularFile(worktree.resolve(".git"))).isTrue();
        ProjectSpace project = project(userId, "Linked Worktree", worktree);

        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);

        var coverage = readService.overview(userId, project.getId()).coverage();
        assertThat(coverage.complete()).isTrue();
        assertThat(coverage.sourceCounts().getOrDefault("GIT", 0)).isGreaterThan(0);
    }

    @Test
    void marksShallowGitHistoryAsIncompleteInsteadOfTreatingFetchedWindowAsFullHistory() throws Exception {
        UUID userId = UUID.randomUUID();
        Path source = temporaryRoot.resolve("shallow-source");
        Files.createDirectories(source);
        git(source, "init", "-b", "master");
        git(source, "config", "user.email", "history@example.com");
        git(source, "config", "user.name", "History Fixture");
        Files.writeString(source.resolve("first.md"), "first\n");
        commitAt(source, "add first history", Instant.parse("2024-01-01T00:00:00Z"));
        Files.writeString(source.resolve("second.md"), "second\n");
        commitAt(source, "add second history", Instant.parse("2024-02-01T00:00:00Z"));
        Path shallow = temporaryRoot.resolve("shallow-clone");
        git(temporaryRoot, "clone", "--depth=1", source.toUri().toString(), shallow.toString());
        ProjectSpace project = project(userId, "Shallow History", shallow);

        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);

        var coverage = readService.overview(userId, project.getId()).coverage();
        assertThat(coverage.complete()).isFalse();
        assertThat(coverage.gaps()).anyMatch(value -> value.contains("浅克隆"));
        assertThat(readService.events(
            userId, project.getId(), "GIT", "COMMIT", null, null, null, "CURRENT",
            null, false, null, null, 0, 20
        ).totalElements()).isEqualTo(1);
    }

    @Test
    void rejectsIllegalEvidenceUnknownIdsAndInventedConflictsWithoutPollutingSnapshot() throws Exception {
        UUID userId = UUID.randomUUID();
        Path repository = temporaryRoot.resolve("model-guard");
        Files.createDirectories(repository.resolve("src"));
        git(repository, "init", "-b", "master");
        git(repository, "config", "user.email", "history@example.com");
        git(repository, "config", "user.name", "History Fixture");
        Files.writeString(repository.resolve("src/AuthService.java"), "class AuthService {}\n");
        commit(repository, "introduce authentication entry");
        ProjectSpace project = project(userId, "Model Guard", repository);
        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);
        var story = readService.stories(userId, project.getId(), null, false, null, null, 0, 20).items().get(0);
        var chapter = readService.chapters(userId, project.getId(), 0, 20).items().get(0);
        provider(userId);

        when(modelGateway.callStructured(any(), any(), any())).thenReturn(modelResponse("""
            {"stories":[{"storyId":"%s","humanTitle":"新增认证入口并形成可用结果","oneSentenceSummary":"认证入口已经形成。","reason":"因为另一个项目要求复用","reasonEvidenceRefs":["fact:%s"],"unknowns":[]}],"chapters":[{"chapterId":"%s","title":"认证入口变化区间","summary":"这一时间区间记录了认证入口的形成。"}]}
            """.formatted(story.id(), UUID.randomUUID(), chapter.id())));
        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), true);
        var invalidEvidence = readService.overview(userId, project.getId()).diagnostics();
        assertThat(invalidEvidence.get("invalidEvidenceRefCount")).isEqualTo(0);
        assertThat(invalidEvidence.get("modelRejectedInvalidEvidenceRefCount")).isEqualTo(1);
        assertThat(readService.stories(userId, project.getId(), null, false, null, null, 0, 20).items().get(0).reason()).isBlank();

        when(modelGateway.callStructured(any(), any(), any())).thenReturn(modelResponse("""
            {"stories":[{"storyId":"story-from-another-project","humanTitle":"新增认证入口并形成可用结果","oneSentenceSummary":"认证入口已经形成。","reason":"","reasonEvidenceRefs":[],"unknowns":["原因未知"]}],"chapters":[]}
            """));
        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), true);
        var crossProject = readService.overview(userId, project.getId()).diagnostics();
        assertThat(crossProject.get("crossProjectRefCount")).isEqualTo(0);
        assertThat(crossProject.get("modelRejectedCrossProjectRefCount")).isEqualTo(1);

        when(modelGateway.callStructured(any(), any(), any())).thenReturn(modelResponse("""
            {"stories":[{"storyId":"%s","humanTitle":"新增认证入口并形成可用结果","oneSentenceSummary":"认证入口已经形成。","reason":"","reasonEvidenceRefs":[],"conflicts":["模型编造的来源冲突"],"unknowns":["原因未知"]}],"chapters":[{"chapterId":"%s","title":"认证入口变化区间","summary":"这一时间区间记录了认证入口的形成。"}]}
            """.formatted(story.id(), chapter.id())));
        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), true);
        var unsupported = readService.overview(userId, project.getId()).diagnostics();
        assertThat(unsupported.get("unsupportedStrongFactCount")).isEqualTo(0);
        assertThat(unsupported.get("modelRejectedUnsupportedClaimCount")).isEqualTo(1);
        assertThat(readService.stories(userId, project.getId(), null, false, null, null, 0, 20).items().get(0).conflicts()).isEmpty();

        when(modelGateway.callStructured(any(), any(), any())).thenReturn(modelResponse("""
            {"stories":[{"storyId":"%s","humanTitle":"新增认证入口并形成可用结果","oneSentenceSummary":"认证入口已经形成。","beforeState":"模型试图改写工程状态","reason":"","reasonEvidenceRefs":[],"unknowns":["原因未知"]}],"chapters":[{"chapterId":"%s","title":"认证入口变化区间","summary":"这一时间区间记录了认证入口的形成。"}]}
            """.formatted(story.id(), chapter.id())));
        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), true);
        assertThat(readService.overview(userId, project.getId()).diagnostics().get("modelRejectedUnsupportedClaimCount"))
            .isEqualTo(1);

        when(modelGateway.callStructured(any(), any(), any())).thenReturn(modelResponse("""
            {"stories":[{"storyId":"%s","humanTitle":"完成关键里程碑并成功交付项目","oneSentenceSummary":"项目已成功完成并达到成熟度要求。","reason":"","reasonEvidenceRefs":[],"unknowns":["原因未知"]}],"chapters":[{"chapterId":"%s","title":"认证入口变化区间","summary":"这一时间区间记录了认证入口的形成。"}]}
            """.formatted(story.id(), chapter.id())));
        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), true);
        assertThat(readService.overview(userId, project.getId()).diagnostics().get("modelRejectedUnsupportedClaimCount"))
            .isEqualTo(1);

        when(modelGateway.callStructured(any(), any(), any())).thenReturn(modelResponse("""
            {"stories":[{"storyId":"%s","humanTitle":"新增认证入口并形成可用结果","oneSentenceSummary":"认证入口已经形成。","reason":"","reasonEvidenceRefs":[],"unknowns":[]}],"chapters":[{"chapterId":"%s","title":"认证入口变化区间","summary":"这一时间区间记录了认证入口的形成。"}]}
            """.formatted(story.id(), chapter.id())));
        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), true);
        var normalizedUnknown = readService.stories(
            userId, project.getId(), null, false, null, null, 0, 20
        ).items().get(0);
        assertThat(normalizedUnknown.reason()).isBlank();
        assertThat(normalizedUnknown.unknowns()).containsExactly("目前没有足够信息确认为什么做这次调整。")
            .allSatisfy(value -> assertThat(value).doesNotContain("UNKNOWN", "Evidence", "reason eligibility"));
    }

    @Test
    void repairsOneSemanticValidationFailureWithoutAcceptingInvalidEvidence() throws Exception {
        UUID userId = UUID.randomUUID();
        Path repository = temporaryRoot.resolve("model-validation-repair");
        Files.createDirectories(repository.resolve("src"));
        git(repository, "init", "-b", "master");
        git(repository, "config", "user.email", "history@example.com");
        git(repository, "config", "user.name", "History Fixture");
        Files.writeString(repository.resolve("src/Result.java"), "class Result {}\n");
        commit(repository, "introduce reviewable result");
        ProjectSpace project = project(userId, "Model Validation Repair", repository);
        provider(userId);

        AtomicInteger calls = new AtomicInteger();
        when(modelGateway.callStructured(any(), any(), any())).thenAnswer(invocation -> {
            String prompt = invocation.getArgument(1, String.class);
            String valid = historyModelResponse(prompt);
            if (calls.incrementAndGet() != 1) return modelResponse(valid);
            JsonNode invalid = objectMapper.readTree(valid);
            com.fasterxml.jackson.databind.node.ObjectNode story =
                (com.fasterxml.jackson.databind.node.ObjectNode) invalid.path("stories").get(0);
            story.put("reason", "未经合格 Evidence 支持的原因");
            story.set("reasonEvidenceRefs", objectMapper.valueToTree(List.of("fact:" + UUID.randomUUID())));
            return modelResponse(objectMapper.writeValueAsString(invalid));
        });

        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);

        Map<String, Object> diagnostics = readService.overview(userId, project.getId()).diagnostics();
        assertThat(calls.get()).isEqualTo(2);
        assertThat(diagnostics).containsEntry("modelStatus", "MODEL_VALIDATED")
            .containsEntry("failedWindowCount", 0)
            .containsEntry("modelRejectedInvalidEvidenceRefCount", 0)
            .containsEntry("modelValidationRepairCount", 1)
            .containsEntry("modelValidationRepairFailureCount", 0);
        var story = readService.stories(userId, project.getId(), null, false, null, null, 0, 20).items().get(0);
        assertThat(story.reason()).isBlank();
        assertThat(story.reasonEvidenceRefs()).isEmpty();
    }

    @Test
    void retainsDeterministicTitlePairWhenProviderOmitsTheSupportedResult() throws Exception {
        UUID userId = UUID.randomUUID();
        Path repository = temporaryRoot.resolve("model-title-result-fallback");
        Files.createDirectories(repository.resolve("src"));
        git(repository, "init", "-b", "master");
        git(repository, "config", "user.email", "history@example.com");
        git(repository, "config", "user.name", "History Fixture");
        Files.writeString(repository.resolve("src/AuthFlow.java"), "class AuthFlow {}\n");
        commit(repository, "implement login flow");
        ProjectSpace project = project(userId, "Model Title Result Fallback", repository);

        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);
        var deterministic = readService.stories(
            userId, project.getId(), null, false, null, null, 0, 20
        ).items().get(0);

        provider(userId);
        when(modelGateway.callStructured(any(), any(), any())).thenAnswer(invocation -> {
            JsonNode valid = objectMapper.readTree(historyModelResponse(invocation.getArgument(1, String.class)));
            com.fasterxml.jackson.databind.node.ObjectNode story =
                (com.fasterxml.jackson.databind.node.ObjectNode) valid.path("stories").get(0);
            String generatedTitle = story.path("humanTitle").asText();
            int subjectStart = generatedTitle.indexOf('“');
            int subjectEnd = generatedTitle.lastIndexOf('”');
            assertThat(subjectStart).isGreaterThanOrEqualTo(0);
            assertThat(subjectEnd).isGreaterThan(subjectStart);
            String subject = generatedTitle.substring(subjectStart, subjectEnd + 1);
            story.put("humanTitle", "编写" + subject + "的代码");
            story.put("oneSentenceSummary", "涵盖" + subject + "的代码创建与修改。");
            return modelResponse(objectMapper.writeValueAsString(valid));
        });

        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), true);

        var finalStory = readService.stories(
            userId, project.getId(), null, false, null, null, 0, 20
        ).items().get(0);
        assertThat(finalStory.humanTitle()).isEqualTo(deterministic.humanTitle());
        assertThat(finalStory.oneSentenceSummary()).isEqualTo(deterministic.oneSentenceSummary());
        assertThat(finalStory.summaryStatus()).isEqualTo("MODEL_VALIDATED_WITH_DETERMINISTIC_TITLE");
        assertThat(readService.overview(userId, project.getId()).diagnostics())
            .containsEntry("modelStatus", "MODEL_VALIDATED")
            .containsEntry("modelDeterministicTitleFallbackCount", 1)
            .containsEntry("modelValidationRepairFailureCount", 0);
    }

    @Test
    void modelCannotRewriteEngineeringOwnedPrimarySupportingRoleGraph() throws Exception {
        UUID userId = UUID.randomUUID();
        Path repository = temporaryRoot.resolve("model-role-graph");
        Files.createDirectories(repository.resolve("product"));
        git(repository, "init", "-b", "master");
        git(repository, "config", "user.email", "history@example.com");
        git(repository, "config", "user.name", "History Fixture");
        Files.writeString(repository.resolve("product/intake.txt"), "intake result\n");
        commitAt(repository, "establish project intake result", Instant.parse("2024-01-01T00:00:00Z"));
        Files.writeString(repository.resolve("product/delivery.txt"), "delivery result\n");
        commitAt(repository, "establish project delivery result", Instant.parse("2024-02-01T00:00:00Z"));
        ProjectSpace project = project(userId, "Model Role Graph", repository);

        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);
        var deterministic = readService.stories(userId, project.getId(), null, false, null, null, 0, 100).items();
        assertThat(deterministic.stream().filter(item -> item.primary()).toList()).hasSizeGreaterThanOrEqualTo(2);
        Map<String, String> deterministicRoles = deterministic.stream().collect(java.util.stream.Collectors.toMap(
            com.projectflow.dto.ProjectHistoryDtos.ChangeStory::id,
            com.projectflow.dto.ProjectHistoryDtos.ChangeStory::role
        ));

        provider(userId);
        when(modelGateway.callStructured(any(), any(), any())).thenAnswer(invocation -> {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode valid = mapper.readTree(historyModelResponse(invocation.getArgument(1, String.class)));
            ((com.fasterxml.jackson.databind.node.ObjectNode) valid.path("stories").get(0))
                .put("role", "SUPPORTING");
            return modelResponse(mapper.writeValueAsString(valid));
        });
        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), true);
        Map<String, com.projectflow.dto.ProjectHistoryDtos.ChangeStory> afterAttempt = readService.stories(
            userId, project.getId(), null, false, null, null, 0, 100
        ).items().stream().collect(java.util.stream.Collectors.toMap(
            com.projectflow.dto.ProjectHistoryDtos.ChangeStory::id, item -> item
        ));
        deterministicRoles.forEach((id, role) -> assertThat(afterAttempt.get(id).role()).isEqualTo(role));
        assertThat(readService.overview(userId, project.getId()).diagnostics().get("modelRejectedUnsupportedClaimCount"))
            .isEqualTo(1);
    }

    @Test
    void reconstructsRenameMoveSplitMergeRevertReapplyMergeCommitsAndMixedMessages() throws Exception {
        UUID userId = UUID.randomUUID();
        Path repository = temporaryRoot.resolve("complex-history");
        Files.createDirectories(repository.resolve("src"));
        git(repository, "init", "-b", "master");
        git(repository, "config", "user.email", "history@example.com");
        git(repository, "config", "user.name", "History Fixture");

        Path report = repository.resolve("src/Report.java");
        Files.writeString(report, "class Report { String combined; }\n", StandardCharsets.UTF_8);
        commit(repository, "新增报告入口");

        Files.delete(report);
        Files.writeString(repository.resolve("src/ReportPartA.java"), "class ReportPartA { String sectionA; }\n", StandardCharsets.UTF_8);
        Files.writeString(repository.resolve("src/ReportPartB.java"), "class ReportPartB { String sectionB; }\n", StandardCharsets.UTF_8);
        commit(repository, "split report into independent sections");

        Files.delete(repository.resolve("src/ReportPartA.java"));
        Files.delete(repository.resolve("src/ReportPartB.java"));
        Files.writeString(report, "class Report { String mergedResult; }\n", StandardCharsets.UTF_8);
        commit(repository, "merge report sections into one result");

        Path renamed = repository.resolve("src/ReportRenamed.java");
        git(repository, "mv", "src/Report.java", "src/ReportRenamed.java");
        commit(repository, "rename report boundary");
        Files.createDirectories(repository.resolve("archive"));
        Path moved = repository.resolve("archive/ReportRenamed.java");
        git(repository, "mv", "src/ReportRenamed.java", "archive/ReportRenamed.java");
        commit(repository, "move report boundary");

        Files.writeString(moved, "class ReportRenamed { String temporary; }\n", StandardCharsets.UTF_8);
        commit(repository, "update");
        git(repository, "revert", "--no-edit", "HEAD");
        Files.writeString(moved, "class ReportRenamed { String reapplied; }\n", StandardCharsets.UTF_8);
        commit(repository, "reapply report after revert");

        git(repository, "checkout", "-b", "feature-guide");
        Files.createDirectories(repository.resolve("docs"));
        Files.writeString(repository.resolve("docs/Guide.md"), "# Guide\n", StandardCharsets.UTF_8);
        commit(repository, "document guide for issue #41");
        git(repository, "checkout", "master");
        Files.writeString(repository.resolve("src/CacheService.java"), "class CacheService {}\n", StandardCharsets.UTF_8);
        commit(repository, "fix");
        git(repository, "merge", "--no-ff", "feature-guide", "-m", "Merge pull request #42 for guide");

        git(repository, "checkout", "-b", "feature-export");
        Files.writeString(repository.resolve("src/ExportService.java"), "class ExportService {}\n", StandardCharsets.UTF_8);
        commit(repository, "增加导出结果");
        git(repository, "checkout", "master");
        Files.writeString(repository.resolve("src/CacheService.java"), "class CacheService { boolean enabled; }\n", StandardCharsets.UTF_8);
        commit(repository, "update");
        git(repository, "merge", "--no-ff", "feature-export", "-m", "Merge pull request #43 for export");
        git(repository, "commit", "--allow-empty", "--allow-empty-message", "-m", "");

        ProjectSpace project = project(userId, "Complex History", repository);
        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);

        var events = readService.events(
            userId, project.getId(), null, null, null, null, null, "CURRENT",
            null, false, null, null, 0, 100
        );
        assertThat(events.totalElements()).isEqualTo(readService.overview(userId, project.getId()).sourceEventCount());
        assertThat(events.items().stream().map(item -> item.transition()).toList())
            .contains("SPLIT", "MERGED", "RENAMED", "MOVED", "REVERTED", "REAPPLIED");
        assertThat(events.items().stream().filter(item -> item.category().equals("MERGE")).count()).isGreaterThanOrEqualTo(2);
        assertThat(events.items().stream().filter(item -> item.sourceType().equals("GITHUB")).count()).isGreaterThanOrEqualTo(3);
        var reportThreads = readService.threads(userId, project.getId(), "report", 0, 50).items();
        assertThat(reportThreads).hasSize(1);
        assertThat(reportThreads.get(0).transitions())
            .contains("SPLIT", "MERGED", "RENAMED", "MOVED", "REVERTED", "REAPPLIED");
        assertThat(reportThreads.get(0).storyRefs()).hasSizeGreaterThanOrEqualTo(5);
    }

    @Test
    void keepsMoreThanOneThousandEventsAcrossThreeHundredCommitsAndCapsModelAtOneBoundedBatch() throws Exception {
        UUID userId = UUID.randomUUID();
        Path repository = temporaryRoot.resolve("large-history");
        Files.createDirectories(repository);
        git(repository, "init", "-b", "master");
        fastImport(repository, 340);
        ProjectSpace project = project(userId, "Large History", repository);
        provider(userId);

        AtomicInteger storyCalls = new AtomicInteger();
        AtomicInteger chapterCalls = new AtomicInteger();
        when(modelGateway.callStructured(any(), any(), any())).thenAnswer(invocation -> {
            String prompt = invocation.getArgument(1, String.class);
            if (invocation.getArgument(2, ModelTaskType.class) == ModelTaskType.PROJECT_HISTORY_CHAPTER_SYNTHESIS) {
                chapterCalls.incrementAndGet();
                return modelResponse(historyChapterModelResponse(prompt));
            }
            storyCalls.incrementAndGet();
            return modelResponse(historyModelResponse(prompt));
        });

        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);

        var overview = readService.overview(userId, project.getId());
        assertThat(storyCalls.get()).as("history diagnostics: %s", overview.diagnostics()).isEqualTo(1);
        assertThat(chapterCalls.get()).isLessThanOrEqualTo(4);
        assertThat(overview.sourceEventCount()).isGreaterThan(1_000);
        assertThat(overview.coverage().complete()).isTrue();
        assertThat(((Number) overview.diagnostics().get("boundedDeterministicStoryCount")).intValue()).isGreaterThan(0);
        assertThat(overview.diagnostics().get("eventConservation")).isEqualTo(true);
        assertThat(readService.events(
            userId, project.getId(), null, null, null, null, null, "CURRENT",
            null, false, null, null, 0, 100
        ).totalElements()).isEqualTo(overview.sourceEventCount());
        assertThat(readService.chapters(userId, project.getId(), 0, 100).totalElements()).isGreaterThanOrEqualTo(3);
        assertThat(readService.stories(userId, project.getId(), null, false, null, null, 0, 100).totalElements())
            .isGreaterThanOrEqualTo(40);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void continuesFortyWindowsAcrossRefreshesAndOnlyCachesAfterTheTailCompletes() throws Exception {
        UUID userId = UUID.randomUUID();
        Path root = temporaryRoot.resolve("forty-window-history");
        Files.createDirectories(root);
        ProjectSpace project = project(userId, "Forty Window History", root);
        historicalFacts(project, 40 * 32, 1, 1, 0);
        provider(userId);

        AtomicInteger calls = new AtomicInteger();
        AtomicInteger chapterCalls = new AtomicInteger();
        when(modelGateway.callStructured(any(), any(), any())).thenAnswer(invocation -> {
            if (invocation.getArgument(2, ModelTaskType.class) == ModelTaskType.PROJECT_HISTORY_CHAPTER_SYNTHESIS) {
                chapterCalls.incrementAndGet();
                return modelResponse(historyChapterModelResponse(invocation.getArgument(1, String.class)));
            }
            calls.incrementAndGet();
            return modelResponse(historyModelResponse(invocation.getArgument(1, String.class)));
        });

        var first = reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);
        Map<String, Object> firstDiagnostics = readService.overview(userId, project.getId()).diagnostics();
        assertThat(first.cacheHit()).isFalse();
        assertThat(calls.get()).isEqualTo(16);
        assertThat(firstDiagnostics).containsEntry("totalWindowCount", 40)
            .containsEntry("succeededWindowCount", 16)
            .containsEntry("failedWindowCount", 0)
            .containsEntry("skippedWindowCount", 0)
            .containsEntry("pendingWindowCount", 24)
            .containsEntry("nextWindowOrdinal", 16);
        assertThat(checkpointRepository.findByProjectIdOrderByUpdatedAtAsc(project.getId()))
            .filteredOn(checkpoint -> "SUCCEEDED".equals(checkpoint.getStatus()))
            .hasSize(16);

        var second = reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);
        Map<String, Object> secondDiagnostics = readService.overview(userId, project.getId()).diagnostics();
        assertThat(second.cacheHit()).isFalse();
        assertThat(calls.get()).isEqualTo(32);
        assertThat(secondDiagnostics).containsEntry("succeededWindowCount", 32)
            .containsEntry("pendingWindowCount", 8)
            .containsEntry("nextWindowOrdinal", 32);

        var third = reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);
        Map<String, Object> completedDiagnostics = readService.overview(userId, project.getId()).diagnostics();
        assertThat(third.cacheHit()).isFalse();
        assertThat(calls.get()).isEqualTo(40);
        assertThat(completedDiagnostics).containsEntry("succeededWindowCount", 40)
            .containsEntry("pendingWindowCount", 0)
            .containsEntry("nextWindowOrdinal", -1)
            .containsEntry("unprocessedStoryCount", 0);
        assertThat(readService.stories(userId, project.getId(), null, false, null, null, 0, 1).totalElements())
            .isEqualTo(40L * 32L);

        int guard = 0;
        while (((Number) readService.overview(userId, project.getId()).diagnostics()
            .getOrDefault("chapterSynthesisPendingCount", 0)).intValue() > 0 && guard++ < 10) {
            reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);
        }
        Map<String, Object> chapterDiagnostics = readService.overview(userId, project.getId()).diagnostics();
        assertThat(chapterDiagnostics).containsEntry("chapterSynthesisPendingCount", 0);
        assertThat(chapterCalls.get()).isEqualTo(((Number) chapterDiagnostics.get("chapterSynthesisCount")).intValue());

        var cached = reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);
        assertThat(cached.cacheHit()).isTrue();
        assertThat(calls.get()).isEqualTo(40);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void synthesizesLargeChaptersAfterTheirStoriesCrossMultipleValidatedWindows() throws Exception {
        UUID userId = UUID.randomUUID();
        Path root = temporaryRoot.resolve("large-chapter-second-stage");
        Files.createDirectories(root);
        ProjectSpace project = project(userId, "Large Chapter Second Stage", root);
        historicalFacts(project, 100, 1, 1, 0);
        provider(userId);

        AtomicInteger storyCalls = new AtomicInteger();
        AtomicInteger chapterCalls = new AtomicInteger();
        List<String> chapterPrompts = new ArrayList<>();
        when(modelGateway.callStructured(any(), any(), any())).thenAnswer(invocation -> {
            String prompt = invocation.getArgument(1, String.class);
            ModelTaskType task = invocation.getArgument(2, ModelTaskType.class);
            if (task == ModelTaskType.PROJECT_HISTORY_CHAPTER_SYNTHESIS) {
                chapterCalls.incrementAndGet();
                chapterPrompts.add(prompt);
                return modelResponse(historyChapterModelResponse(prompt));
            }
            storyCalls.incrementAndGet();
            return modelResponse(historyModelResponse(prompt));
        });

        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);

        var chapters = readService.chapters(userId, project.getId(), 0, 100).items();
        List<com.projectflow.dto.ProjectHistoryDtos.HistoryChapter> large = chapters.stream()
            .filter(chapter -> chapter.storyRefs().size() == 40).toList();
        assertThat(storyCalls.get()).isEqualTo(4);
        assertThat(chapterCalls.get()).isEqualTo(chapters.size());
        assertThat(large).hasSize(2).allSatisfy(chapter -> {
            assertThat(chapter.title()).contains("项目成果记录", "可追溯结果")
                .doesNotContain("环境配置示例", "版本库忽略规则");
            assertThat(chapter.summary()).contains("项目成果记录", "可追溯结果");
            assertThat(chapter.authority()).isEqualTo("INFERRED_NON_AUTHORITATIVE");
            assertThat(chapter.storyRefs()).doesNotHaveDuplicates().hasSize(40);
        });
        assertThat(chapterPrompts).hasSize(chapters.size()).allSatisfy(prompt -> assertThat(prompt)
            .hasSizeLessThanOrEqualTo(48_000)
            .contains(
                "CHAPTER_SYNTHESIS_JSON", "primaryStoryCount", "supportingStoryCount",
                "representativeClusters", "requiredRepresentativeClusterIds", "dominantClusterIds"
            )
            .doesNotContain("evidenceRefs", "technicalDetails", "commitSummaries", "results/Outcome", "fact:"));
        assertThat(readService.overview(userId, project.getId()).diagnostics())
            .containsEntry("chapterSynthesisCount", chapters.size())
            .containsEntry("chapterSynthesisProcessedCount", chapters.size())
            .containsEntry("chapterSynthesisFailedCount", 0)
            .containsEntry("chapterSynthesisPendingCount", 0)
            .containsEntry("chaptersWithMinorClusterTitleRisk", 0)
            .containsEntry("chapterOverlapCount", 0)
            .containsEntry("orphanSupportingCount", 0)
            .containsEntry("userDeclaredChapterMutationCount", 0);
        assertThat(checkpointRepository.findByProjectIdOrderByUpdatedAtAsc(project.getId()))
            .filteredOn(checkpoint -> checkpoint.getWindowIdentity().startsWith("chapter-summary-"))
            .hasSize(chapters.size()).allSatisfy(checkpoint -> {
                assertThat(checkpoint.getStatus()).isEqualTo("SUCCEEDED");
                assertThat(checkpoint.getEventCount()).isZero();
            });

        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), true);

        assertThat(storyCalls.get()).isEqualTo(4);
        assertThat(chapterCalls.get()).isEqualTo(chapters.size());
        assertThat(readService.overview(userId, project.getId()).diagnostics())
            .containsEntry("chapterSynthesisCacheHitCount", chapters.size());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void routesAutomaticChapterWordingOnlyThroughTheRepresentationPlanStage() throws Exception {
        UUID userId = UUID.randomUUID();
        Path root = temporaryRoot.resolve("chapter-plan-only-stage");
        Files.createDirectories(root);
        ProjectSpace project = project(userId, "Chapter Plan Only Stage", root);
        historicalFacts(project, 3, 1, 1, 0);
        provider(userId);

        AtomicInteger storyCalls = new AtomicInteger();
        AtomicInteger chapterCalls = new AtomicInteger();
        AtomicReference<String> storyPrompt = new AtomicReference<>();
        AtomicReference<String> chapterPrompt = new AtomicReference<>();
        when(modelGateway.callStructured(any(), any(), any())).thenAnswer(invocation -> {
            String prompt = invocation.getArgument(1, String.class);
            ModelTaskType task = invocation.getArgument(2, ModelTaskType.class);
            if (task == ModelTaskType.PROJECT_HISTORY_CHAPTER_SYNTHESIS) {
                chapterCalls.incrementAndGet();
                chapterPrompt.set(prompt);
                return modelResponse(historyChapterModelResponse(prompt));
            }
            storyCalls.incrementAndGet();
            storyPrompt.set(prompt);
            return modelResponse(historyModelResponse(prompt));
        });

        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);

        assertThat(storyCalls.get()).isEqualTo(1);
        assertThat(chapterCalls.get()).isEqualTo(1);
        assertThat(storyPrompt.get()).contains("requiredChapterIds=[]", "\"chapters\":[]");
        assertThat(chapterPrompt.get()).contains(
            "CHAPTER_SYNTHESIS_JSON", "requiredRepresentativeClusterIds", "deterministicFallback"
        );
        assertThat(readService.overview(userId, project.getId()).diagnostics())
            .containsEntry("chapterSynthesisCount", 1)
            .containsEntry("chapterSynthesisProcessedCount", 1)
            .containsEntry("chapterSynthesisFailedCount", 0);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void chapterRepairFailureKeepsTheValidatedRepresentativeDeterministicResult() throws Exception {
        UUID userId = UUID.randomUUID();
        Path root = temporaryRoot.resolve("chapter-representation-repair-failure");
        Files.createDirectories(root);
        ProjectSpace project = project(userId, "Chapter Repair Failure", root);
        historicalFacts(project, 80, 1, 1, 0);
        provider(userId);

        AtomicInteger chapterCalls = new AtomicInteger();
        when(modelGateway.callStructured(any(), any(), any())).thenAnswer(invocation -> {
            if (invocation.getArgument(2, ModelTaskType.class) == ModelTaskType.PROJECT_HISTORY_CHAPTER_SYNTHESIS) {
                chapterCalls.incrementAndGet();
                return modelResponse("""
                    {"chapters":[{"chapterId":"unknown-chapter","representedClusterIds":["unknown-cluster"],
                    "title":"未经校验的模型标题","summary":"未经校验的模型摘要"}]}
                    """);
            }
            return modelResponse(historyModelResponse(invocation.getArgument(1, String.class)));
        });

        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);

        var chapters = readService.chapters(userId, project.getId(), 0, 20).items();
        var deterministic = chapters.stream()
            .filter(chapter -> "ENGINEERING_REPRESENTATION_PLAN".equals(chapter.authority())).toList();
        Map<String, Object> diagnostics = readService.overview(userId, project.getId()).diagnostics();
        assertThat(chapterCalls.get()).isGreaterThanOrEqualTo(2);
        assertThat(deterministic).isNotEmpty().allSatisfy(chapter -> {
            assertThat(chapter.title()).contains("项目成果记录").doesNotContain("未经校验");
            assertThat(chapter.summary()).contains("项目成果记录").doesNotContain("未经校验");
        });
        assertThat(((Number) diagnostics.get("chapterSynthesisFailedCount")).intValue()).isGreaterThan(0);
        assertThat(((Number) diagnostics.get("chaptersUsingDeterministicFallback")).intValue()).isGreaterThan(0);
        assertThat(diagnostics).containsEntry("chaptersWithMinorClusterTitleRisk", 0)
            .containsEntry("unsupportedClaimCount", 0);
    }

    @Test
    void continuesAfterOneWindowSchemaFailureAndRetriesOnlyThatWindow() throws Exception {
        UUID userId = UUID.randomUUID();
        Path root = temporaryRoot.resolve("window-local-failure");
        Files.createDirectories(root);
        ProjectSpace project = project(userId, "Window Local Failure", root);
        historicalFacts(project, 3 * 32, 1, 1, 0);
        provider(userId);

        AtomicInteger calls = new AtomicInteger();
        when(modelGateway.callStructured(any(), any(), any())).thenAnswer(invocation -> {
            if (invocation.getArgument(2, ModelTaskType.class) == ModelTaskType.PROJECT_HISTORY_CHAPTER_SYNTHESIS) {
                return modelResponse(historyChapterModelResponse(invocation.getArgument(1, String.class)));
            }
            int call = calls.incrementAndGet();
            if (call == 2 || call == 3) return modelResponse("{\"stories\":[],\"chapters\":[]}");
            return modelResponse(historyModelResponse(invocation.getArgument(1, String.class)));
        });

        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);
        Map<String, Object> failedDiagnostics = readService.overview(userId, project.getId()).diagnostics();
        assertThat(calls.get()).as("history diagnostics=%s stories=%s", failedDiagnostics,
            readService.stories(userId, project.getId(), null, false, null, null, 0, 100).items().stream()
                .limit(5).map(item -> item.primarySubjectKey() + ":" + item.humanTitle()).toList()).isEqualTo(4);
        assertThat(failedDiagnostics).containsEntry("totalWindowCount", 3)
            .containsEntry("succeededWindowCount", 2)
            .containsEntry("failedWindowCount", 1)
            .containsEntry("pendingWindowCount", 0)
            .containsEntry("nextWindowOrdinal", 1)
            .containsEntry("modelValidationRepairCount", 1)
            .containsEntry("modelValidationRepairFailureCount", 1);
        assertThat(checkpointRepository.findByProjectIdOrderByUpdatedAtAsc(project.getId()))
            .filteredOn(checkpoint -> "SUCCEEDED".equals(checkpoint.getStatus())).hasSize(2);
        assertThat(checkpointRepository.findByProjectIdOrderByUpdatedAtAsc(project.getId()))
            .filteredOn(checkpoint -> "FAILED".equals(checkpoint.getStatus()))
            .singleElement().satisfies(checkpoint -> assertThat(checkpoint.getDiagnosticsJson())
                .contains("\"failureClass\":\"HISTORY_VALIDATION\"")
                .contains("\"validationKind\":\"CONTRACT\""));

        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);
        Map<String, Object> recoveredDiagnostics = readService.overview(userId, project.getId()).diagnostics();
        assertThat(calls.get()).isEqualTo(5);
        assertThat(recoveredDiagnostics).containsEntry("succeededWindowCount", 3)
            .containsEntry("failedWindowCount", 0)
            .containsEntry("pendingWindowCount", 0)
            .containsEntry("nextWindowOrdinal", -1);
    }

    @Test
    void stopsLaterWindowsAfterSystemicProviderFailure() throws Exception {
        UUID userId = UUID.randomUUID();
        Path root = temporaryRoot.resolve("window-systemic-failure");
        Files.createDirectories(root);
        ProjectSpace project = project(userId, "Window Systemic Failure", root);
        historicalFacts(project, 3 * 32, 1, 1, 0);
        provider(userId);

        AtomicInteger calls = new AtomicInteger();
        when(modelGateway.callStructured(any(), any(), any())).thenAnswer(invocation -> {
            if (calls.incrementAndGet() == 2) throw new ModelGatewayService.ModelHttpException(401);
            return modelResponse(historyModelResponse(invocation.getArgument(1, String.class)));
        });

        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);

        Map<String, Object> diagnostics = readService.overview(userId, project.getId()).diagnostics();
        assertThat(calls.get()).isEqualTo(2);
        assertThat(diagnostics).containsEntry("succeededWindowCount", 1)
            .containsEntry("failedWindowCount", 1)
            .containsEntry("pendingWindowCount", 1)
            .containsEntry("nextWindowOrdinal", 1)
            .containsEntry("modelStatus", "MODEL_PARTIAL_FALLBACK_PROVIDER_STOPPED");
        assertThat(checkpointRepository.findByProjectIdOrderByUpdatedAtAsc(project.getId())).hasSize(2);
        assertThat(checkpointRepository.findByProjectIdOrderByUpdatedAtAsc(project.getId()))
            .filteredOn(checkpoint -> "FAILED".equals(checkpoint.getStatus()))
            .singleElement().satisfies(checkpoint -> assertThat(checkpoint.getDiagnosticsJson())
                .contains("\"failureClass\":\"MODEL_HTTP\"")
                .contains("\"failureCode\":\"HTTP_401\""));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void cancellationStopsTheTailAndRetryRunsOnlyCancelledAndUnstartedWindows() throws Exception {
        UUID userId = UUID.randomUUID();
        Path root = temporaryRoot.resolve("window-cancellation");
        Files.createDirectories(root);
        ProjectSpace project = project(userId, "Window Cancellation", root);
        historicalFacts(project, 3 * 32, 1, 1, 0);
        provider(userId);

        AtomicInteger calls = new AtomicInteger();
        when(modelGateway.callStructured(any(), any(), any())).thenAnswer(invocation -> {
            if (invocation.getArgument(2, ModelTaskType.class) == ModelTaskType.PROJECT_HISTORY_CHAPTER_SYNTHESIS) {
                return modelResponse(historyChapterModelResponse(invocation.getArgument(1, String.class)));
            }
            if (calls.incrementAndGet() == 2) throw new CancellationException("cancelled by user");
            return modelResponse(historyModelResponse(invocation.getArgument(1, String.class)));
        });

        assertThatThrownBy(() -> reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false))
            .isInstanceOf(CancellationException.class);
        assertThat(calls.get()).isEqualTo(2);
        var initialCheckpoints = checkpointRepository.findByProjectIdOrderByUpdatedAtAsc(project.getId());
        assertThat(initialCheckpoints)
            .extracting(value -> value.getStatus())
            .containsExactlyInAnyOrder("SUCCEEDED", "CANCELLED");
        UUID succeededCheckpointId = initialCheckpoints.stream()
            .filter(value -> "SUCCEEDED".equals(value.getStatus()))
            .findFirst().orElseThrow().getId();

        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);

        assertThat(calls.get()).isEqualTo(4);
        var retriedCheckpoints = checkpointRepository.findByProjectIdOrderByUpdatedAtAsc(project.getId());
        assertThat(retriedCheckpoints)
            .extracting(value -> value.getStatus())
            .containsOnly("SUCCEEDED");
        assertThat(retriedCheckpoints).anySatisfy(value -> assertThat(value.getId()).isEqualTo(succeededCheckpointId));
        assertThat(readService.overview(userId, project.getId()).diagnostics())
            .containsEntry("succeededWindowCount", 3)
            .containsEntry("failedWindowCount", 0)
            .containsEntry("pendingWindowCount", 0);
    }

    @Test
    void removesRawTechnicalPayloadBeforePromptBudgetingAndCachesOneStableWindow() throws Exception {
        UUID userId = UUID.randomUUID();
        Path root = temporaryRoot.resolve("window-prompt-overflow");
        Files.createDirectories(root);
        ProjectSpace project = project(userId, "Window Prompt Overflow", root);
        historicalFacts(project, 32, 8, 12, 120);
        provider(userId);

        AtomicInteger calls = new AtomicInteger();
        when(modelGateway.callStructured(any(), any(), any())).thenAnswer(invocation -> {
            if (invocation.getArgument(2, ModelTaskType.class) == ModelTaskType.PROJECT_HISTORY_CHAPTER_SYNTHESIS) {
                return modelResponse(historyChapterModelResponse(invocation.getArgument(1, String.class)));
            }
            calls.incrementAndGet();
            return modelResponse(historyModelResponse(invocation.getArgument(1, String.class)));
        });

        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);

        Map<String, Object> diagnostics = readService.overview(userId, project.getId()).diagnostics();
        int completedCalls = calls.get();
        assertThat(completedCalls).isEqualTo(1);
        assertThat(((Number) diagnostics.get("totalWindowCount")).intValue()).isEqualTo(completedCalls);
        assertThat(diagnostics).containsEntry("succeededWindowCount", completedCalls)
            .containsEntry("failedWindowCount", 0)
            .containsEntry("skippedWindowCount", 0)
            .containsEntry("pendingWindowCount", 0);
        assertThat(checkpointRepository.findByProjectIdOrderByUpdatedAtAsc(project.getId()))
            .filteredOn(checkpoint -> checkpoint.getWindowIdentity().startsWith("window-"))
            .singleElement()
            .satisfies(checkpoint -> {
                assertThat(checkpoint.getStatus()).isEqualTo("SUCCEEDED");
                assertThat(checkpoint.getWindowIdentity()).doesNotEndWith("-a").doesNotEndWith("-b");
            });

        var cached = reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);
        assertThat(cached.cacheHit()).isTrue();
        assertThat(calls.get()).isEqualTo(completedCalls);
    }

    @Test
    void boundsSingleStoryPayloadBeforeModelAndCachesTheValidatedWindow() throws Exception {
        UUID userId = UUID.randomUUID();
        Path root = temporaryRoot.resolve("single-story-overflow");
        Files.createDirectories(root);
        ProjectSpace project = project(userId, "Single Story Overflow", root);
        historicalFacts(project, 1, 40, 80, 200);
        provider(userId);

        AtomicInteger calls = new AtomicInteger();
        AtomicReference<String> capturedPrompt = new AtomicReference<>();
        when(modelGateway.callStructured(any(), any(), any())).thenAnswer(invocation -> {
            if (invocation.getArgument(2, ModelTaskType.class) == ModelTaskType.PROJECT_HISTORY_CHAPTER_SYNTHESIS) {
                return modelResponse(historyChapterModelResponse(invocation.getArgument(1, String.class)));
            }
            calls.incrementAndGet();
            capturedPrompt.set(invocation.getArgument(1, String.class));
            return modelResponse(historyModelResponse(capturedPrompt.get()));
        });

        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);
        Map<String, Object> diagnostics = readService.overview(userId, project.getId()).diagnostics();
        assertThat(calls.get()).isEqualTo(1);
        assertThat(capturedPrompt.get()).hasSizeLessThanOrEqualTo(60_000)
            .doesNotContain("technicalDetails", "results/项目结果主题", "p".repeat(200), "e".repeat(200));
        assertThat(diagnostics).containsEntry("totalWindowCount", 1)
            .containsEntry("succeededWindowCount", 1)
            .containsEntry("skippedWindowCount", 0)
            .containsEntry("skippedStoryCount", 0)
            .containsEntry("pendingWindowCount", 0)
            .containsEntry("unprocessedStoryCount", 0);
        assertThat(checkpointRepository.findByProjectIdOrderByUpdatedAtAsc(project.getId()))
            .singleElement().satisfies(checkpoint -> assertThat(checkpoint.getStatus()).isEqualTo("SUCCEEDED"));

        var cached = reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);
        assertThat(cached.cacheHit()).isTrue();
        assertThat(calls.get()).isEqualTo(1);
        assertThat(readService.overview(userId, project.getId()).diagnostics())
            .containsEntry("skippedWindowCount", 0)
            .containsEntry("skippedStoryCount", 0)
            .containsEntry("pendingWindowCount", 0)
            .containsEntry("previousModelStatus", "MODEL_VALIDATED");
    }

    @Test
    void sourceAppendReusesUnaffectedWindowCheckpointsAndCreatesOnlyTheTail() throws Exception {
        UUID userId = UUID.randomUUID();
        Path root = temporaryRoot.resolve("window-source-append");
        Files.createDirectories(root);
        ProjectSpace project = project(userId, "Window Source Append", root);
        historicalFacts(project, 64, 1, 1, 0);
        provider(userId);

        AtomicInteger calls = new AtomicInteger();
        when(modelGateway.callStructured(any(), any(), any())).thenAnswer(invocation -> {
            if (invocation.getArgument(2, ModelTaskType.class) == ModelTaskType.PROJECT_HISTORY_CHAPTER_SYNTHESIS) {
                return modelResponse(historyChapterModelResponse(invocation.getArgument(1, String.class)));
            }
            calls.incrementAndGet();
            return modelResponse(historyModelResponse(invocation.getArgument(1, String.class)));
        });

        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);
        var originalCheckpoints = checkpointRepository.findByProjectIdOrderByUpdatedAtAsc(project.getId()).stream()
            .filter(value -> value.getWindowIdentity().startsWith("window-")).toList();
        List<UUID> originalCheckpointIds = originalCheckpoints.stream().map(value -> value.getId()).toList();
        List<String> originalCacheKeys = originalCheckpoints.stream().map(value -> value.getCacheKey()).toList();
        assertThat(calls.get()).isEqualTo(2);
        assertThat(originalCheckpointIds).hasSize(2);

        var stalePresentationCache = originalCheckpoints.stream()
            .filter(value -> value.getWindowIdentity().startsWith("window-"))
            .findFirst().orElseThrow();
        JsonNode cachedPresentation = objectMapper.readTree(stalePresentationCache.getValidatedResultJson());
        ((com.fasterxml.jackson.databind.node.ObjectNode) cachedPresentation.path("stories").get(0))
            .put("occurredFrom", "2099-01-01T00:00:00Z")
            .put("occurredTo", "2099-01-02T00:00:00Z");
        stalePresentationCache.storeValidatedResult(objectMapper.writeValueAsString(cachedPresentation));
        checkpointRepository.saveAndFlush(stalePresentationCache);

        historicalFacts(project, 64, 1, 1, 1, 0);
        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);

        var appendedCheckpoints = checkpointRepository.findByProjectIdOrderByUpdatedAtAsc(project.getId());
        var appendedStoryCheckpoints = appendedCheckpoints.stream()
            .filter(value -> value.getWindowIdentity().startsWith("window-")).toList();
        List<UUID> afterAppend = appendedStoryCheckpoints.stream().map(value -> value.getId()).toList();
        List<String> appendedCacheKeys = appendedStoryCheckpoints.stream().map(value -> value.getCacheKey()).toList();
        assertThat(calls.get()).as("before cache keys=%s, after cache keys=%s, identities=%s",
            originalCacheKeys, appendedCacheKeys,
            appendedCheckpoints.stream().map(value -> value.getWindowIdentity()).toList()).isEqualTo(3);
        assertThat(afterAppend).hasSize(3).containsAll(originalCheckpointIds);
        assertThat(readService.overview(userId, project.getId()).diagnostics())
            .containsEntry("totalWindowCount", 3)
            .containsEntry("succeededWindowCount", 3)
            .containsEntry("pendingWindowCount", 0);
        assertThat(readService.stories(userId, project.getId(), null, false, null, null, 0, 100).items())
            .allSatisfy(story -> assertThat(story.occurredFrom()).isBefore(Instant.parse("2099-01-01T00:00:00Z")));
    }

    @Test
    void correctionRevisionInvalidatesOnlyTheTargetWindow() throws Exception {
        UUID userId = UUID.randomUUID();
        Path root = temporaryRoot.resolve("window-correction-revision");
        Files.createDirectories(root);
        ProjectSpace project = project(userId, "Window Correction Revision", root);
        historicalFacts(project, 64, 1, 1, 0);
        provider(userId);

        AtomicInteger calls = new AtomicInteger();
        when(modelGateway.callStructured(any(), any(), any())).thenAnswer(invocation -> {
            if (invocation.getArgument(2, ModelTaskType.class) == ModelTaskType.PROJECT_HISTORY_CHAPTER_SYNTHESIS) {
                return modelResponse(historyChapterModelResponse(invocation.getArgument(1, String.class)));
            }
            calls.incrementAndGet();
            return modelResponse(historyModelResponse(invocation.getArgument(1, String.class)));
        });

        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);
        List<UUID> originalCheckpointIds = checkpointRepository.findByProjectIdOrderByUpdatedAtAsc(project.getId())
            .stream().filter(value -> value.getWindowIdentity().startsWith("window-"))
            .map(value -> value.getId()).toList();
        var target = readService.stories(userId, project.getId(), null, false, null, null, 0, 1).items().get(0);
        ProjectHistorySnapshot snapshot = snapshotRepository.findByProjectId(project.getId()).orElseThrow();
        String revision = correctionService.list(userId, project.getId()).presentationRevision();
        correctionService.create(userId, project.getId(), new HistoryCorrectionRequest(
            "RENAME_STORY", "STORY", target.id(), List.of(), "用户确认的结果名称", "", "", "",
            revision, snapshot.getSourceEventFingerprint()
        ));

        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);

        List<UUID> afterCorrection = checkpointRepository.findByProjectIdOrderByUpdatedAtAsc(project.getId())
            .stream().filter(value -> value.getWindowIdentity().startsWith("window-"))
            .map(value -> value.getId()).toList();
        assertThat(calls.get()).isEqualTo(3);
        assertThat(afterCorrection).hasSize(2);
        assertThat(afterCorrection.stream().filter(originalCheckpointIds::contains).count()).isEqualTo(1);
        assertThat(readService.stories(userId, project.getId(), null, false, null, null, 0, 100).items())
            .filteredOn(story -> story.id().equals(target.id()))
            .singleElement().satisfies(story -> {
                assertThat(story.humanTitle()).isEqualTo("用户确认的结果名称");
                assertThat(story.oneSentenceSummary()).doesNotContain("主题000", "内容000");
            });
    }

    private ProjectSpace project(UUID userId, String name, Path root) {
        ProjectSpace project = new ProjectSpace(userId);
        project.update(name, "History fixture", ProjectStatus.BUILDING, List.of("Java"), "https://github.com/example/history", LocalDate.now(), null);
        project = projectRepository.saveAndFlush(project);
        ProjectMemory memory = new ProjectMemory(project.getId());
        memory.update("", "", "", "", "", "", "", "", "");
        memory.rememberLocalProjectPath(root.toAbsolutePath().normalize().toString());
        memoryRepository.saveAndFlush(memory);
        return project;
    }

    private void historicalFacts(ProjectSpace project, int count, int pathCount, int evidenceCount, int payloadWidth) {
        historicalFacts(project, 0, count, pathCount, evidenceCount, payloadWidth);
    }

    private void historicalFacts(ProjectSpace project, int startIndex, int count, int pathCount, int evidenceCount,
        int payloadWidth) {
        Instant first = Instant.parse("2024-01-01T00:00:00Z");
        List<ProjectFact> facts = new ArrayList<>();
        for (int offset = 0; offset < count; offset++) {
            int index = startIndex + offset;
            Instant occurredAt = first.plusSeconds(index * 3_600L);
            List<String> paths = new ArrayList<>();
            for (int pathIndex = 0; pathIndex < pathCount; pathIndex++) {
                paths.add("results/项目结果主题" + String.format("%05d", index) + "内容"
                    + String.format("%03d", pathIndex) + (payloadWidth <= 0 ? "" : "-" + "p".repeat(payloadWidth)) + ".java");
            }
            List<String> evidence = new ArrayList<>();
            for (int evidenceIndex = 0; evidenceIndex < evidenceCount; evidenceIndex++) {
                evidence.add("source:outcome-" + String.format("%05d", index) + "-"
                    + String.format("%03d", evidenceIndex) + (payloadWidth <= 0 ? "" : "-" + "e".repeat(payloadWidth)));
            }
            ProjectFact fact = new ProjectFact(
                project.getId(), null, null, ProjectFactOrigin.INCREMENTAL_SCAN,
                String.format("%064d", index + 1)
            );
            fact.updateContent(
                "记录项目结果 " + index, "来源确认项目结果 " + index + " 已发生。", List.of("形成可核对结果"),
                "结果可从来源继续核对。", occurredAt, occurredAt, List.of(), List.of(), List.of(), paths, evidence,
                "LOCAL_RULE", "PASS", EvidenceConfidence.HIGH, ProjectFactRecordStatus.RECORDED, ""
            );
            fact.applyKnowledgeContract(
                ProjectFactEpistemicStatus.OBSERVED, List.of("FILESYSTEM"), "CURRENT", "fact-revision-" + index,
                occurredAt, occurredAt, List.of(), List.of(), "ENGINEERING_VALIDATION", "", "", "VALIDATED"
            );
            facts.add(fact);
        }
        factRepository.saveAllAndFlush(facts);
    }

    private void provider(UUID userId) {
        AiProvider provider = new AiProvider(userId);
        provider.update(
            "history-fixed", "http://127.0.0.1", "test-key", "fixed", AiProviderType.OPENAI_COMPATIBLE,
            0.1, 8_000, true, List.of()
        );
        providerRepository.saveAndFlush(provider);
    }

    private ModelGatewayService.StructuredModelResponse modelResponse(String content) throws Exception {
        return new ModelGatewayService.StructuredModelResponse(content, outputAdapter.parse(content));
    }

    private String historyModelResponse(String prompt) throws Exception {
        String requiredTemplateMarker = "\nREQUIRED_OUTPUT_TEMPLATE_JSON=";
        int requiredTemplateStart = prompt.indexOf(requiredTemplateMarker);
        if (requiredTemplateStart >= 0) {
            JsonNode template = objectMapper.readTree(prompt.substring(
                requiredTemplateStart + requiredTemplateMarker.length()
            ));
            return objectMapper.writeValueAsString(template);
        }
        String storiesMarker = "\nSTORIES_JSON=";
        String chaptersMarker = "\nCHAPTERS_JSON=";
        int storiesStart = prompt.indexOf(storiesMarker);
        int chaptersStart = prompt.indexOf(chaptersMarker, storiesStart + storiesMarker.length());
        int repairStart = prompt.indexOf(ProjectHistoryPromptBuilder.VALIDATION_REPAIR_MARKER, chaptersStart);
        assertThat(storiesStart).isGreaterThanOrEqualTo(0);
        assertThat(chaptersStart).isGreaterThan(storiesStart);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode stories = mapper.readTree(prompt.substring(storiesStart + storiesMarker.length(), chaptersStart));
        JsonNode chapters = mapper.readTree(prompt.substring(
            chaptersStart + chaptersMarker.length(), repairStart < 0 ? prompt.length() : repairStart
        ));
        List<Map<String, Object>> storyOutput = new ArrayList<>();
        for (JsonNode story : stories) {
            String subject = story.path("subjectDisplayConcept").asText("项目内容");
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("storyId", story.path("storyId").asText());
            item.put("humanTitle", "调整“" + subject + "”并形成可追溯结果");
            item.put("oneSentenceSummary", "围绕“" + subject + "”的来源事件已按时间归纳为可追溯变化。");
            item.put("beforeWording", "此前已保留与“" + subject + "”有关的项目记录。");
            item.put("changeWording", "这一阶段对“" + subject + "”的已有内容进行了调整。");
            item.put("afterWording", "当前可以继续查看“" + subject + "”的更新记录。");
            item.put("reason", "");
            item.put("reasonEvidenceRefs", List.of());
            item.put("unknownWording", "目前没有足够信息确认为什么做这次调整。");
            storyOutput.add(item);
        }
        List<Map<String, Object>> chapterOutput = new ArrayList<>();
        for (JsonNode chapter : chapters) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("chapterId", chapter.path("chapterId").asText());
            item.put("title", representativeChapterTitle(chapter));
            item.put("summary", representativeChapterSummary(chapter));
            chapterOutput.add(item);
        }
        return mapper.writeValueAsString(Map.of("stories", storyOutput, "chapters", chapterOutput));
    }

    private String historyChapterModelResponse(String prompt) throws Exception {
        String requiredTemplateMarker = "\nREQUIRED_OUTPUT_TEMPLATE_JSON=";
        int requiredTemplateStart = prompt.indexOf(requiredTemplateMarker);
        if (requiredTemplateStart >= 0) {
            JsonNode template = objectMapper.readTree(prompt.substring(
                requiredTemplateStart + requiredTemplateMarker.length()
            ));
            return objectMapper.writeValueAsString(template);
        }
        String marker = "\nCHAPTER_SYNTHESIS_JSON=";
        int start = prompt.indexOf(marker);
        int repairStart = prompt.indexOf(ProjectHistoryPromptBuilder.VALIDATION_REPAIR_MARKER, start);
        assertThat(start).isGreaterThanOrEqualTo(0);
        JsonNode chapter = objectMapper.readTree(prompt.substring(
            start + marker.length(), repairStart < 0 ? prompt.length() : repairStart
        ));
        String chapterId = chapter.path("chapterId").asText();
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("chapterId", chapterId);
        output.put("representedClusterIds", objectMapper.convertValue(
            chapter.path("requiredRepresentativeClusterIds"), List.class
        ));
        output.put("title", representativeChapterTitle(chapter));
        output.put("summary", representativeChapterSummary(chapter));
        return objectMapper.writeValueAsString(Map.of("chapters", List.of(output)));
    }

    private static String representativeChapterTitle(JsonNode chapter) {
        JsonNode clusters = chapter.path("representativeClusters");
        for (JsonNode cluster : clusters) {
            if (!"MINOR".equals(cluster.path("role").asText())
                && cluster.path("representativeOutcomes").isArray()
                && !cluster.path("representativeOutcomes").isEmpty()) {
                return cluster.path("representativeOutcomes").get(0).asText();
            }
        }
        return "记录项目阶段成果并形成可查看结果";
    }

    private static String representativeChapterSummary(JsonNode chapter) {
        List<String> outcomes = new ArrayList<>();
        for (JsonNode cluster : chapter.path("representativeClusters")) {
            if (cluster.path("representativeOutcomes").isArray()
                && !cluster.path("representativeOutcomes").isEmpty()) {
                String value = cluster.path("representativeOutcomes").get(0).asText().trim()
                    .replaceAll("[。；;！!？?]+$", "");
                if (!value.isBlank()) outcomes.add(value);
            }
        }
        if (outcomes.isEmpty()) return "这一时期记录项目阶段成果并形成可查看结果。";
        return "这一时期" + outcomes.get(0)
            + outcomes.stream().skip(1).map(value -> "，并" + value).collect(java.util.stream.Collectors.joining())
            + "。";
    }

    private void fastImport(Path root, int commits) throws Exception {
        long firstEpochSecond = Instant.parse("2024-01-01T00:00:00Z").getEpochSecond();
        StringBuilder stream = new StringBuilder();
        for (int index = 1; index <= commits; index++) {
            long occurredAt = firstEpochSecond + index * 3_600L;
            String message = switch (index % 3) {
                case 0 -> "update";
                case 1 -> "调整认证与导出流程 " + index;
                default -> "refine cache and export flow " + index;
            };
            stream.append("commit refs/heads/master\n")
                .append("mark :").append(index).append('\n')
                .append("author History Fixture <history@example.com> ").append(occurredAt).append(" +0000\n")
                .append("committer History Fixture <history@example.com> ").append(occurredAt).append(" +0000\n")
                .append("data <<PFMSG\n").append(message).append("\nPFMSG\n");
            if (index > 1) stream.append("from :").append(index - 1).append('\n');
            for (String subject : List.of("AuthService", "ExportService", "CacheService")) {
                stream.append("M 100644 inline src/").append(subject).append(".java\n")
                    .append("data <<PFDATA\nclass ").append(subject).append(" { int version = ")
                    .append(index).append("; }\nPFDATA\n");
            }
            stream.append('\n');
        }
        stream.append("done\n");
        Process process = new ProcessBuilder("git", "fast-import", "--quiet")
            .directory(root.toFile()).redirectErrorStream(true).start();
        try (var output = process.getOutputStream()) {
            output.write(stream.toString().getBytes(StandardCharsets.UTF_8));
        }
        String commandOutput = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.waitFor() != 0) throw new AssertionError("git fast-import failed: " + commandOutput);
        git(root, "reset", "--hard", "master");
    }

    private void commit(Path root, String message) throws Exception {
        git(root, "add", "-A");
        git(root, "commit", "-m", message);
    }

    private void commitAt(Path root, String message, Instant occurredAt) throws Exception {
        git(root, "add", "-A");
        List<String> command = new ArrayList<>(List.of("git", "commit", "-m", message));
        ProcessBuilder builder = new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true);
        builder.environment().put("GIT_AUTHOR_DATE", occurredAt.toString());
        builder.environment().put("GIT_COMMITTER_DATE", occurredAt.toString());
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.waitFor() != 0) throw new AssertionError(String.join(" ", command) + " failed: " + output);
    }

    private String git(Path root, String... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.waitFor() != 0) throw new AssertionError(String.join(" ", command) + " failed: " + output);
        return output;
    }
}
