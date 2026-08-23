package com.projectflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.dto.ProjectHistoryDtos.ChangeStory;
import com.projectflow.dto.ProjectHistoryDtos.EvolutionThread;
import com.projectflow.dto.ProjectHistoryDtos.HistoryChapter;
import com.projectflow.entity.ProjectMemory;
import com.projectflow.entity.ProjectSpace;
import com.projectflow.entity.ProjectStatus;
import com.projectflow.repository.ProjectMemoryRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.service.ModelGatewayService;
import com.projectflow.service.ProjectHistoryChapterRepresentationPlanner;
import com.projectflow.service.ProjectHistoryLanguageService;
import com.projectflow.service.ProjectHistoryReadService;
import com.projectflow.service.ProjectHistoryReconstructionService;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProjectHistoryDogfoodAcceptanceTest {
    static final String V375_BASELINE = "fd5ce827245f4fc4a20ecda15c63fc03313505ab";
    static final UUID DOGFOOD_PROJECT_ID = UUID.fromString("38000000-0000-0000-0000-000000000001");

    @Autowired ProjectRepository projectRepository;
    @Autowired ProjectMemoryRepository memoryRepository;
    @Autowired ProjectHistoryReconstructionService reconstructionService;
    @Autowired ProjectHistoryReadService readService;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean ModelGatewayService modelGateway;

    @TempDir Path temporaryRoot;

    @Test
    void reconstructsProjectFlowThroughV375AsBoundedReadableHistory() throws Exception {
        Path sourceRepository = sourceRepository();
        assertThat(git(sourceRepository, "cat-file", "-e", V375_BASELINE + "^{commit}")).isEmpty();
        Path baseline = temporaryRoot.resolve("projectflow-v375");
        git(temporaryRoot, "init", baseline.toString());
        git(baseline, "fetch", "--no-tags", sourceRepository.toString(), V375_BASELINE);
        git(baseline, "checkout", "--detach", V375_BASELINE);
        Instant baselineTime = Instant.ofEpochSecond(Long.parseLong(
            git(baseline, "show", "-s", "--format=%ct", V375_BASELINE).trim()
        ));
        normalizeWorkingTreeTimestamps(baseline, baselineTime);
        int baselineCommits = Integer.parseInt(git(baseline, "rev-list", "--count", V375_BASELINE).trim());
        UUID userId = UUID.randomUUID();
        ProjectSpace project = project(userId, baseline);

        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);

        var overview = readService.overview(userId, project.getId());
        List<HistoryChapter> chapters = readService.chapters(userId, project.getId(), 0, 100).items();
        var storyPage = readService.stories(
            userId, project.getId(), null, false, null, null, 0, 100
        );
        List<ChangeStory> stories = storyPage.items();
        var threadPage = readService.threads(userId, project.getId(), null, 0, 100);
        List<EvolutionThread> threads = threadPage.items();
        Instant v37From = Instant.parse("2026-07-25T00:00:00Z");
        Instant v37To = Instant.parse("2026-08-02T00:00:00Z");
        var v37Stories = readService.stories(userId, project.getId(), null, false, v37From, v37To, 0, 100);
        var v37Commits = readService.events(
            userId, project.getId(), "GIT", "COMMIT", null, null, null, "CURRENT",
            null, false, v37From, v37To, 0, 100
        );
        var v37RawEvents = readService.events(
            userId, project.getId(), "GIT", null, null, null, null, "CURRENT",
            null, false, v37From, v37To, 0, 100
        );
        List<ChangeStory> v37StoryItems = storiesBetween(userId, project.getId(), v37From, v37To);

        assertThat(baselineCommits).isEqualTo(197);
        assertThat(overview.status()).isEqualTo("READY");
        assertThat(overview.projectRevision()).isEqualTo(V375_BASELINE);
        assertThat(overview.sourceEventCount()).isGreaterThan(baselineCommits);
        assertThat(chapters).hasSizeGreaterThanOrEqualTo(3);
        assertThat(stories).hasSizeGreaterThanOrEqualTo(10);
        assertThat(threads).isNotEmpty();
        assertThat(v37Commits.totalElements()).isGreaterThan(10);
        assertThat(v37StoryItems).hasSize((int) v37Stories.totalElements());
        assertThat(v37Stories.totalElements()).isPositive().isLessThan(v37RawEvents.totalElements());
        assertThat(v37StoryItems).anyMatch(story -> story.evidenceRefs().stream()
            .filter(reference -> reference.startsWith("commit:"))
            .distinct().count() > 1);
        assertThat(v37Commits.items().stream().map(item -> item.safeSourceLabel()).toList())
            .anyMatch(value -> value.contains("V3.7.4"))
            .anyMatch(value -> value.contains("V3.7.5"));
        assertThat(overview.diagnostics().get("eventConservation")).isEqualTo(true);
        assertThat(overview.diagnostics().get("invalidEvidenceRefCount")).isEqualTo(0);
        assertThat(overview.diagnostics().get("crossProjectRefCount")).isEqualTo(0);
        assertThat(overview.diagnostics().get("unsupportedStrongFactCount")).isEqualTo(0);
        assertThat(objectMapper.writeValueAsString(overview)).doesNotContain(sourceRepository.toString());
        assertThat(readService.story(userId, project.getId(), stories.get(0).id()).events()).isNotEmpty();
        verifyNoInteractions(modelGateway);
        writeDogfoodArtifact(
            userId, project.getId(), baselineCommits, overview, chapters, stories, threads,
            storyPage.totalElements(), threadPage.totalElements(), v37Commits.totalElements(),
            v37RawEvents.totalElements(), v37Stories.totalElements()
        );
    }

    @Test
    void reconstructsCurrentProjectFlowWithoutProviderAndMeasuresChapterRepresentativeness() throws Exception {
        Path sourceRepository = sourceRepository();
        int commitCount = Integer.parseInt(git(sourceRepository, "rev-list", "--count", "HEAD").trim());
        assertThat(commitCount).isGreaterThan(197);
        UUID userId = UUID.randomUUID();
        ProjectSpace project = project(userId, sourceRepository);

        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);
        var overview = readService.overview(userId, project.getId());
        List<ChangeStory> stories = allStories(userId, project.getId());
        List<HistoryChapter> chapters = allChapters(userId, project.getId());
        List<EvolutionThread> threads = allThreads(userId, project.getId());
        var cached = reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);
        writeCurrentDogfoodArtifact(commitCount, overview, stories, chapters, threads, cached.cacheHit());

        assertThat(overview.status()).isEqualTo("READY");
        assertThat(overview.sourceEventCount()).isPositive();
        assertThat(stories).isNotEmpty();
        assertThat(chapters).isNotEmpty();
        assertThat(threads).isNotEmpty();
        assertThat(overview.diagnostics())
            .containsEntry("eventConservation", true)
            .containsEntry("invalidEvidenceRefCount", 0)
            .containsEntry("crossProjectRefCount", 0)
            .containsEntry("unsupportedStrongFactCount", 0)
            .containsEntry("chaptersWithMinorClusterTitleRisk", 0)
            .containsEntry("technicalLeakCount", 0)
            .containsEntry("unsupportedClaimCount", 0)
            .containsEntry("chapterOverlapCount", 0)
            .containsEntry("orphanSupportingCount", 0)
            .containsEntry("userDeclaredChapterMutationCount", 0);
        assertThat(((Number) overview.diagnostics().get("representativePrimaryCoverage")).doubleValue())
            .isGreaterThanOrEqualTo(0.60);
        Map<String, ChangeStory> storiesById = stories.stream().collect(
            LinkedHashMap::new, (map, story) -> map.put(story.id(), story), Map::putAll
        );
        var firstChapterPlan = new ProjectHistoryChapterRepresentationPlanner(
            new ProjectHistoryLanguageService()
        ).plan(chapters.get(0).storyRefs().stream().map(storiesById::get)
            .filter(java.util.Objects::nonNull).toList());
        assertThat(chapters.get(0).title()).contains("前后端项目骨架")
            .doesNotStartWith("补充环境配置示例")
            .doesNotStartWith("建立项目使用说明");
        assertThat(firstChapterPlan.selectedClusters().get(0).humanLabel()).contains("前后端项目骨架");
        assertThat(cached.cacheHit()).isTrue();
        verifyNoInteractions(modelGateway);
    }

    private List<ChangeStory> allStories(UUID userId, UUID projectId) {
        List<ChangeStory> result = new ArrayList<>();
        int page = 0;
        while (true) {
            var slice = readService.stories(userId, projectId, null, false, true, null, null, page, 100);
            result.addAll(slice.items());
            if (++page >= slice.totalPages()) return List.copyOf(result);
        }
    }

    private List<HistoryChapter> allChapters(UUID userId, UUID projectId) {
        List<HistoryChapter> result = new ArrayList<>();
        int page = 0;
        while (true) {
            var slice = readService.chapters(userId, projectId, page, 100);
            result.addAll(slice.items());
            if (++page >= slice.totalPages()) return List.copyOf(result);
        }
    }

    private List<EvolutionThread> allThreads(UUID userId, UUID projectId) {
        List<EvolutionThread> result = new ArrayList<>();
        int page = 0;
        while (true) {
            var slice = readService.threads(userId, projectId, null, page, 100);
            result.addAll(slice.items());
            if (++page >= slice.totalPages()) return List.copyOf(result);
        }
    }

    private void writeCurrentDogfoodArtifact(
        int commitCount,
        com.projectflow.dto.ProjectHistoryDtos.HistoryOverviewResponse overview,
        List<ChangeStory> stories,
        List<HistoryChapter> chapters,
        List<EvolutionThread> threads,
        boolean cacheHit
    ) throws Exception {
        String configured = System.getProperty("projectflow.history.final-dogfood-output", "").trim();
        if (configured.isBlank()) return;
        Path output = Path.of(configured).toAbsolutePath().normalize();
        Files.createDirectories(output);
        Map<String, Object> artifact = new LinkedHashMap<>();
        artifact.put("version", "projectflow-v3.8.5-final-chapter-deterministic-dogfood-v1");
        artifact.put("source", "current ProjectFlow Git history");
        artifact.put("commitCount", commitCount);
        artifact.put("sourceEventCount", overview.sourceEventCount());
        artifact.put("storyCount", stories.size());
        artifact.put("visiblePrimaryCount", stories.stream()
            .filter(ChangeStory::primary).filter(story -> !story.hiddenByDefault()).count());
        artifact.put("supportingCount", stories.stream().filter(ChangeStory::supporting).count());
        artifact.put("chapterCount", chapters.size());
        artifact.put("threadCount", threads.size());
        artifact.put("diagnostics", overview.diagnostics());
        artifact.put("cacheHit", cacheHit);
        artifact.put("providerModelCalls", 0);
        Map<String, ChangeStory> storiesById = stories.stream().collect(
            LinkedHashMap::new, (map, story) -> map.put(story.id(), story), Map::putAll
        );
        ProjectHistoryChapterRepresentationPlanner planner = new ProjectHistoryChapterRepresentationPlanner(
            new ProjectHistoryLanguageService()
        );
        List<Map<String, Object>> chapterDetails = new ArrayList<>();
        for (HistoryChapter chapter : chapters.stream().limit(12).toList()) {
            var plan = planner.plan(chapter.storyRefs().stream().map(storiesById::get)
                .filter(java.util.Objects::nonNull).toList());
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("chapter", chapter);
            detail.put("primaryStoryCount", plan.primaryStoryCount());
            detail.put("supportingStoryCount", plan.supportingStoryCount());
            detail.put("representativePrimaryCoverage", plan.representativePrimaryCoverage());
            detail.put("dominantClusterIds", plan.dominantClusterIds());
            detail.put("selectedClusterIds", plan.requiredRepresentativeClusterIds());
            detail.put("clusters", plan.clusters().stream().map(cluster -> Map.of(
                "id", cluster.id(),
                "role", cluster.role(),
                "label", cluster.humanLabel(),
                "family", cluster.family(),
                "primaryStoryCount", cluster.primaryStoryCount(),
                "supportingStoryCount", cluster.supportingStoryCount(),
                "claimCeiling", cluster.claimCeiling(),
                "outcomes", cluster.representativeOutcomes(),
                "areas", cluster.areas(),
                "topics", cluster.topics()
            )).toList());
            chapterDetails.add(detail);
        }
        artifact.put("chapters", chapterDetails);
        artifact.put("security", Map.of(
            "apiKeyStored", false,
            "promptStored", false,
            "rawResponseStored", false,
            "reasoningStored", false,
            "absolutePathStored", false
        ));
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(
            output.resolve("projectflow-final-chapter-deterministic-dogfood.json").toFile(), artifact
        );
    }

    private List<ChangeStory> storiesBetween(UUID userId, UUID projectId, Instant from, Instant to) {
        List<ChangeStory> result = new ArrayList<>();
        int page = 0;
        while (true) {
            var slice = readService.stories(userId, projectId, null, false, from, to, page, 100);
            result.addAll(slice.items());
            if (page + 1 >= slice.totalPages()) return List.copyOf(result);
            page++;
        }
    }

    private static Path sourceRepository() {
        Path candidate = Path.of("").toAbsolutePath().normalize();
        while (candidate != null) {
            if (Files.exists(candidate.resolve(".git"))) return candidate;
            candidate = candidate.getParent();
        }
        throw new AssertionError("Unable to locate the ProjectFlow checkout from the test working directory");
    }

    private void writeDogfoodArtifact(
        UUID userId,
        UUID projectId,
        int baselineCommits,
        com.projectflow.dto.ProjectHistoryDtos.HistoryOverviewResponse overview,
        List<HistoryChapter> chapters,
        List<ChangeStory> stories,
        List<EvolutionThread> threads,
        long totalStoryCount,
        long totalThreadCount,
        long v37CommitEvents,
        long v37RawEvents,
        long v37StoryCount
    ) throws Exception {
        String configured = System.getProperty("projectflow.history.dogfood-output", "").trim();
        if (configured.isBlank()) return;
        Path output = Path.of(configured).toAbsolutePath().normalize();
        Files.createDirectories(output);
        List<Map<String, Object>> storyArtifacts = new ArrayList<>();
        for (ChangeStory story : stories.stream().limit(20).toList()) {
            var detail = readService.story(userId, projectId, story.id());
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("id", story.id());
            value.put("title", story.humanTitle());
            value.put("summary", story.oneSentenceSummary());
            value.put("occurredFrom", story.occurredFrom());
            value.put("occurredTo", story.occurredTo());
            value.put("rawEventCount", story.rawEventCount());
            value.put("evidenceCount", story.evidenceCount());
            value.put("unknowns", story.unknowns());
            value.put("drillDown", detail.events().stream().limit(5)
                .map(ProjectHistoryDogfoodAcceptanceTest::drillDown).toList());
            storyArtifacts.add(value);
        }
        Map<String, Object> artifact = new LinkedHashMap<>();
        artifact.put("version", "projectflow-v3.8.0-dogfood-v1");
        artifact.put("source", "ProjectFlow public Git history through V3.7.5");
        artifact.put("baselineCommit", V375_BASELINE);
        artifact.put("baselineCommitCount", baselineCommits);
        artifact.put("sourceEventCount", overview.sourceEventCount());
        artifact.put("chapterCount", chapters.size());
        artifact.put("storyCount", totalStoryCount);
        artifact.put("sampledStoryCount", stories.size());
        artifact.put("threadCount", totalThreadCount);
        artifact.put("sampledThreadCount", threads.size());
        artifact.put("v37CommitEvents", v37CommitEvents);
        artifact.put("v37RawEvents", v37RawEvents);
        artifact.put("v37Stories", v37StoryCount);
        Map<String, Object> v37Window = new LinkedHashMap<>();
        v37Window.put("fromInclusive", "2026-07-25T00:00:00Z");
        v37Window.put("toExclusive", "2026-08-02T00:00:00Z");
        v37Window.put("gitEventScope", "Only GIT source events in the frozen V3.7.x window");
        v37Window.put(
            "storyScope",
            "All reconstructed stories overlapping the same window, including frozen current-material metadata"
        );
        v37Window.put(
            "comparisonLimit",
            "The Git-event and story counts have different source scopes and are not a direct compression ratio"
        );
        artifact.put("v37Window", v37Window);
        Map<String, Object> compression = new LinkedHashMap<>();
        compression.put("rawEvents", overview.sourceEventCount());
        compression.put("stories", totalStoryCount);
        compression.put("chapters", chapters.size());
        artifact.put("compression", compression);
        Map<String, Object> diagnostics = new LinkedHashMap<>();
        diagnostics.put("eventConservation", overview.diagnostics().get("eventConservation"));
        diagnostics.put("invalidEvidenceRefCount", overview.diagnostics().get("invalidEvidenceRefCount"));
        diagnostics.put("crossProjectRefCount", overview.diagnostics().get("crossProjectRefCount"));
        diagnostics.put("unsupportedStrongFactCount", overview.diagnostics().get("unsupportedStrongFactCount"));
        artifact.put("diagnostics", diagnostics);
        artifact.put("chapters", chapters.stream().limit(12).toList());
        artifact.put("stories", storyArtifacts);
        artifact.put("threads", threads.stream().limit(20).toList());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(output.resolve("projectflow-v375-dogfood.json").toFile(), artifact);

        StringBuilder markdown = new StringBuilder("# ProjectFlow V3.8.0 自身历史 Dogfood\n\n")
            .append("公开来源：ProjectFlow Git 历史，固定到 V3.7.5 基线 ").append(V375_BASELINE).append("。\n\n")
            .append("原始提交 ").append(baselineCommits).append("，归一化来源事件 ")
            .append(overview.sourceEventCount()).append("，折叠为 ").append(totalStoryCount)
            .append(" 个变化故事和 ").append(chapters.size()).append(" 个动态时间篇章。\n\n")
            .append("V3.7.x 固定窗口内读取到 ").append(v37CommitEvents).append(" 个 Commit 和 ")
            .append(v37RawEvents).append(" 个 Git 原始事件；同一窗口内，跨 Git 与冻结的当前材料元数据共形成 ")
            .append(v37StoryCount).append(" 个故事。两组数字的来源范围不同，不能直接解释为 Git 事件到故事的压缩比。")
            .append("同一 Commit 可拆分独立变化，多次 Commit 也可合并为一个故事。\n\n")
            .append("## 时间篇章\n\n");
        chapters.stream().limit(8).forEach(chapter -> markdown.append("### ").append(chapter.title()).append("\n\n")
            .append(chapter.summary()).append("\n\n"));
        markdown.append("## 代表性变化故事与 Evidence 下钻\n\n");
        for (Map<String, Object> story : storyArtifacts.stream().limit(12).toList()) {
            markdown.append("### ").append(story.get("title")).append("\n\n")
                .append(story.get("summary")).append("\n\n")
                .append("Evidence 下钻：").append(story.get("drillDown")).append("\n\n");
        }
        markdown.append("该产物不包含绝对路径、凭证、完整 Prompt、raw response 或 reasoning。\n");
        Files.writeString(output.resolve("projectflow-v375-dogfood.md"), markdown.toString(), StandardCharsets.UTF_8);
    }

    private static Map<String, Object> drillDown(
        com.projectflow.dto.ProjectHistoryDtos.HistoryEventResponse event
    ) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("stableEventKey", event.stableEventKey());
        value.put("category", event.category());
        value.put("transition", event.transition());
        value.put("evidenceRefs", event.evidenceRefs());
        return value;
    }

    private ProjectSpace project(UUID userId, Path repository) {
        ProjectSpace project = new ProjectSpace(userId);
        ReflectionTestUtils.setField(project, "id", DOGFOOD_PROJECT_ID);
        project.update(
            "ProjectFlow V3.7.5 Dogfood", "Public baseline history", ProjectStatus.BUILDING,
            List.of("Java", "TypeScript", "Python"), "",
            LocalDate.now(), null
        );
        project = projectRepository.saveAndFlush(project);
        ProjectMemory memory = new ProjectMemory(project.getId());
        memory.update("", "", "", "", "", "", "", "", "");
        memory.rememberLocalProjectPath(repository.toAbsolutePath().normalize().toString());
        memoryRepository.saveAndFlush(memory);
        return project;
    }

    private static void normalizeWorkingTreeTimestamps(Path repository, Instant baselineTime) throws Exception {
        FileTime fixed = FileTime.from(baselineTime);
        try (var paths = Files.walk(repository)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                Path relative = repository.relativize(path);
                if (relative.getNameCount() > 0 && relative.getName(0).toString().equals(".git")) continue;
                Files.setLastModifiedTime(path, fixed);
            }
        }
    }

    private String git(Path root, String... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.add("-c");
        command.add("safe.directory=" + root.toAbsolutePath().normalize().toString().replace('\\', '/'));
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.waitFor() != 0) throw new AssertionError(String.join(" ", command) + " failed: " + output);
        return output;
    }
}
