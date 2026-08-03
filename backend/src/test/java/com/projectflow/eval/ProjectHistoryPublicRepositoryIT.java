package com.projectflow.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
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
class ProjectHistoryPublicRepositoryIT {
    private static final List<RepositoryFixture> REPOSITORIES = List.of(
        new RepositoryFixture(
            "kubernetes/kubernetes", "大型代码仓库", "https://github.com/kubernetes/kubernetes.git",
            "0e5f0f9374ca822d0a5619088d4a00f335b8bafd", 120,
            UUID.fromString("38000000-0000-0000-0000-000000000101")
        ),
        new RepositoryFixture(
            "mdn/content", "文档与知识仓库", "https://github.com/mdn/content.git",
            "f35f247e16286c4e0b1c88fba3d8ce01683c189b", 100,
            UUID.fromString("38000000-0000-0000-0000-000000000102")
        ),
        new RepositoryFixture(
            "hakimel/reveal.js", "前端与创意展示仓库", "https://github.com/hakimel/reveal.js.git",
            "a3b940695648aa1c5b0680bc9a5b905cf43020e5", 100,
            UUID.fromString("38000000-0000-0000-0000-000000000103")
        )
    );

    @Autowired ProjectRepository projectRepository;
    @Autowired ProjectMemoryRepository memoryRepository;
    @Autowired ProjectHistoryReconstructionService reconstructionService;
    @Autowired ProjectHistoryReadService readService;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean ModelGatewayService modelGateway;

    @TempDir Path temporaryRoot;

    @Test
    void validatesThreeDifferentPublicProjectShapesWithBoundedReadOnlyGitWindows() throws Exception {
        Assumptions.assumeTrue(
            Boolean.getBoolean("projectflow.history.public-repos.enabled"),
            "公开仓库验证仅在显式启用时运行"
        );
        UUID userId = UUID.fromString("38000000-0000-0000-0000-000000000100");
        List<Map<String, Object>> results = new ArrayList<>();

        for (RepositoryFixture fixture : REPOSITORIES) {
            Path repository = fetchFixture(fixture);
            ProjectSpace project = project(userId, fixture, repository);
            reconstructionService.refresh(userId, project.getId(), UUID.randomUUID(), false);

            var overview = readService.overview(userId, project.getId());
            var stories = readService.stories(userId, project.getId(), null, false, null, null, 0, 100);
            var chapters = readService.chapters(userId, project.getId(), 0, 100);
            var threads = readService.threads(userId, project.getId(), null, 0, 100);
            int fetchedCommits = Integer.parseInt(git(repository, "rev-list", "--count", "--all").trim());
            int readGitCommits = number(overview.diagnostics().get("readGitCommitCount"));
            int gitCommitReadLimit = number(overview.diagnostics().get("gitCommitReadLimit"));
            int sourceEventLimit = number(overview.diagnostics().get("sourceEventLimit"));

            assertThat(overview.status()).isEqualTo("DEGRADED");
            assertThat(overview.projectRevision()).isEqualTo(fixture.commit());
            assertThat(overview.coverage().complete()).isFalse();
            assertThat(overview.coverage().currentness()).isEqualTo("PARTIAL");
            assertThat(overview.coverage().gaps()).anyMatch(value -> value.contains("浅克隆"));
            assertThat(number(overview.diagnostics().get("reachableGitCommitCount"))).isEqualTo(fetchedCommits);
            assertThat(readGitCommits).isBetween(1, gitCommitReadLimit);
            assertThat(overview.sourceEventCount()).isBetween(1, sourceEventLimit);
            if (fetchedCommits > readGitCommits) {
                assertThat(overview.coverage().gaps()).anyMatch(value -> value.contains("未完全读取"));
            }
            assertThat(stories.totalElements()).isGreaterThan(5);
            assertThat(chapters.totalElements()).isPositive();
            assertThat(threads.totalElements()).isPositive();
            assertThat(overview.diagnostics().get("eventConservation")).isEqualTo(true);
            assertThat(overview.diagnostics().get("invalidEvidenceRefCount")).isEqualTo(0);
            assertThat(overview.diagnostics().get("crossProjectRefCount")).isEqualTo(0);
            assertThat(overview.diagnostics().get("unsupportedStrongFactCount")).isEqualTo(0);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("repository", fixture.name());
            result.put("shape", fixture.shape());
            result.put("fixedCommit", fixture.commit());
            result.put("snapshotStatus", overview.status());
            result.put("requestedDepth", fixture.depth());
            result.put("reachableCommitCountInFetchedWindow", fetchedCommits);
            result.put("readGitCommitCount", readGitCommits);
            result.put("sourceEventCount", overview.sourceEventCount());
            result.put("storyCount", stories.totalElements());
            result.put("chapterCount", chapters.totalElements());
            result.put("threadCount", threads.totalElements());
            result.put("coverage", Map.of(
                "complete", overview.coverage().complete(),
                "currentness", overview.coverage().currentness(),
                "shallowHistoryGapReported", overview.coverage().gaps().stream()
                    .anyMatch(value -> value.contains("浅克隆")),
                "boundedCommitWindowReported", fetchedCommits <= readGitCommits
                    || overview.coverage().gaps().stream().anyMatch(value -> value.contains("未完全读取"))
            ));
            result.put("bounds", Map.of(
                "gitCommitReadLimit", gitCommitReadLimit,
                "sourceEventLimit", sourceEventLimit
            ));
            result.put("contract", Map.of(
                "eventConservation", overview.diagnostics().get("eventConservation"),
                "invalidEvidenceRefCount", overview.diagnostics().get("invalidEvidenceRefCount"),
                "crossProjectRefCount", overview.diagnostics().get("crossProjectRefCount"),
                "unsupportedStrongFactCount", overview.diagnostics().get("unsupportedStrongFactCount")
            ));
            result.put("sampleStoryTitles", stories.items().stream().limit(10)
                .map(item -> item.humanTitle()).toList());
            results.add(result);
        }

        writeArtifact(results);
        verifyNoInteractions(modelGateway);
    }

    private Path fetchFixture(RepositoryFixture fixture) throws Exception {
        Path root = temporaryRoot.resolve(fixture.name().replace('/', '-').replace('.', '-'));
        Files.createDirectories(root);
        git(root, "init", "-b", "validation");
        git(root, "remote", "add", "origin", fixture.cloneUrl());
        git(root, "fetch", "--no-tags", "--filter=blob:none", "--depth=" + fixture.depth(), "origin", fixture.commit());
        git(root, "update-ref", "refs/heads/validation", fixture.commit());
        git(root, "sparse-checkout", "init", "--no-cone");
        git(root, "sparse-checkout", "set", "--no-cone", "/__projectflow_validation_no_files__/");
        git(root, "-c", "advice.detachedHead=false", "checkout", "--detach", fixture.commit());
        return root;
    }

    private ProjectSpace project(UUID userId, RepositoryFixture fixture, Path repository) {
        ProjectSpace project = new ProjectSpace(userId);
        ReflectionTestUtils.setField(project, "id", fixture.projectId());
        project.update(
            fixture.name(), fixture.shape(), ProjectStatus.BUILDING, List.of(), "",
            LocalDate.of(2026, 8, 3), null
        );
        project = projectRepository.saveAndFlush(project);
        ProjectMemory memory = new ProjectMemory(project.getId());
        memory.update("", "", "", "", "", "", "", "", "");
        memory.rememberLocalProjectPath(repository.toAbsolutePath().normalize().toString());
        memoryRepository.saveAndFlush(memory);
        return project;
    }

    private void writeArtifact(List<Map<String, Object>> results) throws Exception {
        String configured = System.getProperty("projectflow.history.public-repo-output", "").trim();
        if (configured.isBlank()) return;
        Path output = Path.of(configured).toAbsolutePath().normalize();
        Files.createDirectories(output);
        Map<String, Object> artifact = new LinkedHashMap<>();
        artifact.put("version", "projectflow-v3.8.0-public-repository-validation-v1");
        artifact.put("validationDate", "2026-08-03");
        artifact.put(
            "method",
            "Fixed public commits, bounded shallow Git windows, blob-filtered sparse worktrees, deterministic model-free reconstruction"
        );
        artifact.put("repositories", results);
        artifact.put("security", Map.of(
            "credentialsPersisted", false,
            "absolutePathPersisted", false,
            "promptPersisted", false,
            "rawResponsePersisted", false
        ));
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(
            output.resolve("public-repository-validation.json").toFile(), artifact
        );
    }

    private String git(Path root, String... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.add("-c");
        command.add("safe.directory=" + root.toAbsolutePath().normalize().toString().replace('\\', '/'));
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true).start();
        boolean completed = process.waitFor(120, TimeUnit.SECONDS);
        if (!completed) {
            process.destroyForcibly();
            throw new AssertionError(String.join(" ", command) + " timed out after 120 seconds");
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.exitValue() != 0) throw new AssertionError(String.join(" ", command) + " failed: " + output);
        return output;
    }

    private int number(Object value) {
        return value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value));
    }

    private record RepositoryFixture(
        String name,
        String shape,
        String cloneUrl,
        String commit,
        int depth,
        UUID projectId
    ) {
    }
}
