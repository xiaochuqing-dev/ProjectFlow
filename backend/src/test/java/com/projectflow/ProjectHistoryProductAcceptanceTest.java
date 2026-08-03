package com.projectflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
import com.projectflow.service.ProjectHistoryReadService;
import com.projectflow.service.ProjectHistoryReconstructionService;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProjectHistoryProductAcceptanceTest {
    @Autowired ProjectRepository projectRepository;
    @Autowired ProjectMemoryRepository memoryRepository;
    @Autowired ProjectHistoryReconstructionService reconstructionService;
    @Autowired ProjectHistoryReadService readService;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean ModelGatewayService modelGateway;

    @TempDir Path temporaryRoot;

    @Test
    void exportsThreeChaptersTenStoriesAndTwoLifecycleThreads() throws Exception {
        UUID userId = UUID.randomUUID();
        Path repository = temporaryRoot.resolve("synthetic-product-acceptance");
        Files.createDirectories(repository.resolve("src"));
        git(repository, "init", "-b", "master");
        git(repository, "config", "user.email", "history@example.com");
        git(repository, "config", "user.name", "History Fixture");

        lifecycle(repository, "AuthService.java", "authentication", Instant.parse("2024-01-01T00:00:00Z"));
        lifecycle(repository, "ExportService.java", "export", Instant.parse("2024-03-01T00:00:00Z"));
        reportEvolution(repository);
        ProjectSpace project = project(userId, repository);

        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);

        var overview = readService.overview(userId, project.getId());
        List<HistoryChapter> chapters = readService.chapters(userId, project.getId(), 0, 100).items();
        List<ChangeStory> stories = readService.stories(
            userId, project.getId(), null, false, null, null, 0, 100
        ).items();
        List<EvolutionThread> threads = readService.threads(userId, project.getId(), null, 0, 100).items();

        assertThat(chapters).hasSizeGreaterThanOrEqualTo(3);
        assertThat(stories).hasSizeGreaterThanOrEqualTo(10);
        assertThat(overview.sourceEventCount()).isGreaterThan(stories.size());
        assertThat(stories.size()).isGreaterThan(chapters.size());
        assertLifecycle(threads, "auth");
        assertLifecycle(threads, "export");
        assertThat(overview.diagnostics().get("eventConservation")).isEqualTo(true);
        assertThat(overview.diagnostics().get("invalidEvidenceRefCount")).isEqualTo(0);
        assertThat(overview.diagnostics().get("crossProjectRefCount")).isEqualTo(0);
        assertThat(overview.diagnostics().get("unsupportedStrongFactCount")).isEqualTo(0);
        assertThat(stories).allSatisfy(story -> {
            assertThat(story.eventRefs()).isNotEmpty();
            assertThat(story.evidenceRefs()).isNotEmpty();
            assertThat(story.humanTitle()).doesNotContain("优化了系统", "修改了相关文件");
        });
        verifyNoInteractions(modelGateway);
        writeAcceptanceArtifact(userId, project.getId(), overview, chapters, stories, threads);
    }

    private void lifecycle(Path repository, String fileName, String subject, Instant first) throws Exception {
        Path file = repository.resolve("src").resolve(fileName);
        Files.writeString(file, "class " + fileName.replace(".java", "") + " {}\n");
        commitAt(repository, "create " + subject + " result", first);
        Files.writeString(file, "class " + fileName.replace(".java", "") + " { boolean revised; }\n");
        commitAt(repository, "modify " + subject + " result", first.plusSeconds(2 * 86_400L));
        Files.delete(file);
        commitAt(repository, "remove " + subject + " result", first.plusSeconds(14 * 86_400L));
        Files.writeString(file, "class " + fileName.replace(".java", "") + " { boolean restored; }\n");
        commitAt(repository, "restore " + subject + " result", first.plusSeconds(28 * 86_400L));
    }

    private void reportEvolution(Path repository) throws Exception {
        Path report = repository.resolve("src/Report.java");
        Files.writeString(report, "class Report { String combined; }\n");
        commitAt(repository, "create report result", Instant.parse("2024-06-01T00:00:00Z"));
        Files.delete(report);
        Files.writeString(repository.resolve("src/ReportPartA.java"), "class ReportPartA {}\n");
        Files.writeString(repository.resolve("src/ReportPartB.java"), "class ReportPartB {}\n");
        commitAt(repository, "split report result", Instant.parse("2024-06-03T00:00:00Z"));
        Files.delete(repository.resolve("src/ReportPartA.java"));
        Files.delete(repository.resolve("src/ReportPartB.java"));
        Files.writeString(report, "class Report { String merged; }\n");
        commitAt(repository, "merge report result", Instant.parse("2024-06-05T00:00:00Z"));
        git(repository, "mv", "src/Report.java", "src/ProjectReport.java");
        commitAt(repository, "rename report boundary", Instant.parse("2024-06-07T00:00:00Z"));
        Files.writeString(repository.resolve("src/ProjectReport.java"), "class ProjectReport { String temporary; }\n");
        commitAt(repository, "update", Instant.parse("2024-06-09T00:00:00Z"));
        gitAt(repository, Instant.parse("2024-06-11T00:00:00Z"), "revert", "--no-edit", "HEAD");
        Files.writeString(repository.resolve("src/ProjectReport.java"), "class ProjectReport { String reapplied; }\n");
        commitAt(repository, "reapply report result", Instant.parse("2024-06-13T00:00:00Z"));
    }

    private static void assertLifecycle(List<EvolutionThread> threads, String subject) {
        EvolutionThread thread = threads.stream().filter(item -> item.subjectKey().contains(subject)).findFirst().orElseThrow();
        assertThat(thread.transitions()).contains("CREATED", "MODIFIED", "REMOVED", "RESTORED");
        assertThat(thread.storyRefs())
            .withFailMessage("Expected at least three stories for %s, but thread was %s and all threads were %s", subject, thread, threads)
            .hasSizeGreaterThanOrEqualTo(3);
    }

    private void writeAcceptanceArtifact(
        UUID userId,
        UUID projectId,
        com.projectflow.dto.ProjectHistoryDtos.HistoryOverviewResponse overview,
        List<HistoryChapter> chapters,
        List<ChangeStory> stories,
        List<EvolutionThread> threads
    ) throws Exception {
        String configured = System.getProperty("projectflow.history.acceptance-output", "").trim();
        if (configured.isBlank()) return;
        Path output = Path.of(configured).toAbsolutePath().normalize();
        Files.createDirectories(output);

        List<Map<String, Object>> storyArtifacts = new ArrayList<>();
        for (ChangeStory story : stories) {
            var detail = readService.story(userId, projectId, story.id());
            List<Map<String, Object>> events = detail.events().stream().limit(6).map(event -> Map.<String, Object>of(
                "stableEventKey", event.stableEventKey(),
                "category", event.category(),
                "transition", event.transition(),
                "evidenceRefs", event.evidenceRefs()
            )).toList();
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("id", story.id());
            value.put("title", story.humanTitle());
            value.put("summary", story.oneSentenceSummary());
            value.put("before", story.beforeState());
            value.put("change", story.change());
            value.put("after", story.afterState());
            value.put("unknowns", story.unknowns());
            value.put("evidenceCount", story.evidenceCount());
            value.put("rawEventCount", story.rawEventCount());
            value.put("events", events);
            storyArtifacts.add(value);
        }
        Map<String, Object> artifact = new LinkedHashMap<>();
        artifact.put("version", "projectflow-v3.8.0-synthetic-acceptance-v1");
        artifact.put("source", "synthetic-fixture");
        artifact.put("safeToCommit", true);
        artifact.put("sourceEventCount", overview.sourceEventCount());
        artifact.put("chapterCount", chapters.size());
        artifact.put("storyCount", stories.size());
        artifact.put("threadCount", threads.size());
        artifact.put("compression", Map.of(
            "rawEvents", overview.sourceEventCount(), "stories", stories.size(), "chapters", chapters.size()
        ));
        artifact.put("chapters", chapters);
        artifact.put("stories", storyArtifacts);
        artifact.put("threads", threads);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(output.resolve("synthetic-project-history.json").toFile(), artifact);

        StringBuilder markdown = new StringBuilder("# ProjectFlow V3.8.0 安全项目历程验收示例\n\n")
            .append("来源：synthetic fixture。该产物不包含用户私有项目、绝对路径、凭证、完整 Prompt、raw response 或 reasoning。\n\n")
            .append("## 压缩结果\n\n")
            .append("原始事件 ").append(overview.sourceEventCount()).append(" → 变化故事 ")
            .append(stories.size()).append(" → 时间篇章 ").append(chapters.size()).append("。\n\n")
            .append("## 时间篇章\n\n");
        chapters.stream().limit(6).forEach(chapter -> markdown.append("### ").append(chapter.title()).append("\n\n")
            .append(chapter.summary()).append("\n\n"));
        markdown.append("## 变化故事\n\n");
        for (Map<String, Object> story : storyArtifacts.stream().limit(12).toList()) {
            markdown.append("### ").append(story.get("title")).append("\n\n")
                .append(story.get("summary")).append("\n\n")
                .append("此前：").append(story.get("before")).append("\n\n")
                .append("变化：").append(story.get("change")).append("\n\n")
                .append("之后：").append(story.get("after")).append("\n\n")
                .append("Evidence 下钻：").append(story.get("events")).append("\n\n");
        }
        markdown.append("## 演变链\n\n");
        threads.stream().filter(thread -> thread.subjectKey().contains("auth") || thread.subjectKey().contains("export"))
            .forEach(thread -> markdown.append("- ").append(thread.subjectLabel()).append("：")
                .append(String.join(" → ", thread.transitions())).append("\n"));
        Files.writeString(output.resolve("synthetic-project-history.md"), markdown.toString(), StandardCharsets.UTF_8);
    }

    private ProjectSpace project(UUID userId, Path repository) {
        ProjectSpace project = new ProjectSpace(userId);
        project.update(
            "Synthetic History", "Safe product acceptance fixture", ProjectStatus.BUILDING,
            List.of("Java"), "", LocalDate.now(), null
        );
        project = projectRepository.saveAndFlush(project);
        ProjectMemory memory = new ProjectMemory(project.getId());
        memory.update("", "", "", "", "", "", "", "", "");
        memory.rememberLocalProjectPath(repository.toAbsolutePath().normalize().toString());
        memoryRepository.saveAndFlush(memory);
        return project;
    }

    private void commitAt(Path root, String message, Instant occurredAt) throws Exception {
        git(root, "add", "-A");
        gitAt(root, occurredAt, "commit", "-m", message);
    }

    private void gitAt(Path root, Instant occurredAt, String... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));
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
