package com.projectflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.entity.AiProvider;
import com.projectflow.entity.AiProviderType;
import com.projectflow.entity.ProjectMemory;
import com.projectflow.entity.ProjectSpace;
import com.projectflow.entity.ProjectStatus;
import com.projectflow.repository.AiProviderRepository;
import com.projectflow.repository.ProjectMemoryRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.service.LocalCommandExecutor;
import com.projectflow.service.ModelGatewayService;
import com.projectflow.service.ModelOutputAdapter;
import com.projectflow.service.ProjectHistoryReadService;
import com.projectflow.service.ProjectHistoryReconstructionService;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProjectHistoryGitHubEvidenceTest {
    private static final String COMMIT = "a".repeat(40);

    @Autowired ProjectRepository projectRepository;
    @Autowired ProjectMemoryRepository memoryRepository;
    @Autowired AiProviderRepository providerRepository;
    @Autowired ProjectHistoryReconstructionService reconstructionService;
    @Autowired ProjectHistoryReadService readService;
    @Autowired ModelOutputAdapter outputAdapter;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean LocalCommandExecutor commandExecutor;
    @MockitoBean ModelGatewayService modelGateway;

    @TempDir Path temporaryRoot;

    @Test
    void usesBoundedDeclaredPullRequestEvidenceForReasonWithoutPersistingFullBody() throws Exception {
        UUID userId = UUID.randomUUID();
        Path repository = temporaryRoot.resolve("github-evidence");
        Files.createDirectories(repository.resolve(".git"));
        ProjectSpace project = project(userId, repository);
        AtomicReference<String> capturedPrompt = new AtomicReference<>();
        mockCommands(false);

        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);
        provider(userId);
        when(modelGateway.callStructured(any(), any(), any())).thenAnswer(invocation -> {
            String prompt = invocation.getArgument(1, String.class);
            capturedPrompt.set(prompt);
            String response = historyResponse(prompt);
            return new ModelGatewayService.StructuredModelResponse(response, outputAdapter.parse(response));
        });

        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), true);

        assertThat(capturedPrompt.get())
            .as("the bounded model prompt")
            .contains("github-pr:7", "[REDACTED_SECRET]")
            .doesNotContain("supersecretvalue123", "C:\\Users\\private-user");
        var exportStories = readService.stories(userId, project.getId(), "export", false, null, null, 0, 20).items();
        var story = exportStories.stream().filter(item -> item.reasonEvidenceRefs().contains("github-pr:7"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No export story retained github-pr:7: " + exportStories));
        assertThat(story.reason()).contains("旧客户端", "CSV");
        assertThat(story.reasonEvidenceRefs()).containsExactly("github-pr:7");
        assertThat(story.authority()).isEqualTo("INFERRED_NON_AUTHORITATIVE");

        var githubEvents = readService.events(
            userId, project.getId(), "GITHUB", null, null, null, null, "CURRENT",
            null, false, null, null, 0, 20
        ).items();
        assertThat(githubEvents).hasSizeGreaterThanOrEqualTo(2);
        String eventJson = objectMapper.writeValueAsString(githubEvents);
        assertThat(eventJson)
            .contains("Pull Request #7", "github-pr:7", "[REDACTED_SECRET]")
            .doesNotContain("supersecretvalue123", "C:\\\\Users\\\\private-user");
    }

    @Test
    void marksUnknownGitCommitCountIncompleteWhileKeepingReadableLocalEvents() throws Exception {
        UUID userId = UUID.randomUUID();
        Path repository = temporaryRoot.resolve("incomplete-history");
        Files.createDirectories(repository.resolve(".git"));
        ProjectSpace project = project(userId, repository);
        mockCommands(true);

        reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);

        var overview = readService.overview(userId, project.getId());
        assertThat(overview.status()).isEqualTo("DEGRADED");
        assertThat(overview.coverage().complete()).isFalse();
        assertThat(overview.coverage().gaps()).anyMatch(value -> value.contains("提交总数无法确认"));
        assertThat(overview.sourceEventCount()).isGreaterThanOrEqualTo(2);
        assertThat(overview.diagnostics().get("eventConservation")).isEqualTo(true);
    }

    private void mockCommands(boolean failCommitCount) {
        when(commandExecutor.execute(any(Path.class), any(), any(Duration.class))).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<String> command = invocation.getArgument(1, List.class);
            if (command.equals(List.of("git", "rev-parse", "--verify", "HEAD"))) return ok(COMMIT + "\n");
            if (command.equals(List.of("git", "rev-list", "--all", "--count"))) {
                return failCommitCount ? failed() : ok("1\n");
            }
            if (command.size() >= 2 && command.get(0).equals("git") && command.get(1).equals("log")) {
                return ok("__PF_COMMIT__\t" + COMMIT
                    + "\t\t2025-01-01T00:00:00Z\tHistory Author\tadd export compatibility\t\n"
                    + "A\tsrc/ExportService.java\n");
            }
            if (command.size() >= 2 && command.get(0).equals("git") && command.get(1).equals("for-each-ref")) {
                return ok("");
            }
            if (command.equals(List.of("git", "status", "--porcelain=v1", "--untracked-files=all"))) return ok("");
            if (command.equals(List.of("gh", "auth", "status"))) return ok("authenticated\n");
            if (command.size() >= 3 && command.subList(0, 3).equals(List.of("gh", "pr", "list"))) {
                return ok("""
                    [{"number":7,"title":"Keep CSV export for legacy clients","body":"旧客户端仍需要 CSV 兼容路径。 api_key=supersecretvalue123 C:\\\\Users\\\\private-user\\\\note.txt\\n\\n完整正文后续内容不应持久化。","createdAt":"2025-01-01T00:00:00Z","updatedAt":"2025-01-02T00:00:00Z","closedAt":"2025-01-02T00:00:00Z","mergedAt":"2025-01-02T00:00:00Z","author":{"login":"history-author"},"url":"https://github.com/example/history/pull/7","mergeCommit":{"oid":"%s"}}]
                    """.formatted(COMMIT));
            }
            if (command.size() >= 3 && command.subList(0, 3).equals(List.of("gh", "issue", "list"))) {
                return ok("""
                    [{"number":9,"title":"Document export compatibility","body":"记录仍待确认的外部约束。","createdAt":"2025-01-01T00:00:00Z","updatedAt":"2025-01-03T00:00:00Z","closedAt":null,"author":{"login":"history-author"},"url":"https://github.com/example/history/issues/9","state":"OPEN"}]
                    """);
            }
            return failed();
        });
    }

    private String historyResponse(String prompt) throws Exception {
        String storiesMarker = "\nSTORIES_JSON=";
        String chaptersMarker = "\nCHAPTERS_JSON=";
        int storiesStart = prompt.indexOf(storiesMarker);
        int chaptersStart = prompt.indexOf(chaptersMarker, storiesStart + storiesMarker.length());
        JsonNode stories = objectMapper.readTree(prompt.substring(storiesStart + storiesMarker.length(), chaptersStart));
        JsonNode chapters = objectMapper.readTree(prompt.substring(chaptersStart + chaptersMarker.length()));
        List<Map<String, Object>> storyOutput = new ArrayList<>();
        for (JsonNode story : stories) {
            boolean reasonEligible = false;
            for (JsonNode evidence : story.path("reasonEligibleEvidenceRefs")) {
                reasonEligible |= evidence.asText().equals("github-pr:7");
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("storyId", story.path("storyId").asText());
            item.put("humanTitle", reasonEligible
                ? "增加导出兼容路径并保留旧客户端结果"
                : "记录项目约束并保留可追溯状态");
            item.put("oneSentenceSummary", reasonEligible
                ? "导出路径增加兼容结果，原因由 Pull Request 声明支持。"
                : "来源事件已按时间归纳，无法确认的原因继续保持未知。");
            item.put("reason", reasonEligible ? "旧客户端仍需要 CSV 兼容路径。" : "");
            item.put("reasonEvidenceRefs", reasonEligible ? List.of("github-pr:7") : List.of());
            item.put("unknowns", reasonEligible ? List.of() : List.of("原因未知"));
            storyOutput.add(item);
        }
        List<Map<String, Object>> chapterOutput = new ArrayList<>();
        for (JsonNode chapter : chapters) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("chapterId", chapter.path("chapterId").asText());
            item.put("title", "整理导出兼容变化并形成可读区间");
            item.put("summary", "该时间区间汇总导出兼容与协作声明，未把声明升级为实现事实。");
            chapterOutput.add(item);
        }
        return objectMapper.writeValueAsString(Map.of("stories", storyOutput, "chapters", chapterOutput));
    }

    private ProjectSpace project(UUID userId, Path repository) {
        ProjectSpace project = new ProjectSpace(userId);
        project.update(
            "GitHub Evidence", "Bounded collaboration evidence", ProjectStatus.BUILDING,
            List.of("Java"), "https://github.com/example/history", LocalDate.now(), null
        );
        project = projectRepository.saveAndFlush(project);
        ProjectMemory memory = new ProjectMemory(project.getId());
        memory.update("", "", "", "", "", "", "", "", "");
        memory.rememberLocalProjectPath(repository.toAbsolutePath().normalize().toString());
        memoryRepository.saveAndFlush(memory);
        return project;
    }

    private void provider(UUID userId) {
        AiProvider provider = new AiProvider(userId);
        provider.update(
            "history-fixed", "http://127.0.0.1", "test-key", "fixed", AiProviderType.OPENAI_COMPATIBLE,
            0.1, 8_000, true, List.of()
        );
        providerRepository.saveAndFlush(provider);
    }

    private static LocalCommandExecutor.CommandResult ok(String output) {
        return new LocalCommandExecutor.CommandResult(0, output, false);
    }

    private static LocalCommandExecutor.CommandResult failed() {
        return new LocalCommandExecutor.CommandResult(1, "", false);
    }
}
