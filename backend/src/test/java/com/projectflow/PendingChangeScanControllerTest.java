package com.projectflow;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.entity.ProjectReviewCursor;
import com.projectflow.repository.ProjectReviewCursorRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PendingChangeScanControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProjectReviewCursorRepository cursorRepository;

    @Test
    void firstScanIsBoundedAndRepeatedRangeIsIdempotent() throws Exception {
        String token = register("first-scan");
        String projectId = createProject(token, "First Scan");
        Path projectPath = createGitProject("first-scan", 35);
        bindProject(token, projectId, projectPath);

        MvcResult first = scan(token, projectId)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.firstScan").value(true))
            .andExpect(jsonPath("$.data.batch.newCommitCount").value(30))
            .andExpect(jsonPath("$.data.batch.warnings[0]").value(containsString("首次扫描")))
            .andExpect(jsonPath("$.data.segments.length()").value(greaterThanOrEqualTo(2)))
            .andExpect(jsonPath("$.data.batch.segmentCount").value(greaterThanOrEqualTo(2)))
            .andReturn();

        String firstBatchId = json(first).at("/data/batch/id").asText();
        MvcResult second = scan(token, projectId)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.batch.id").value(firstBatchId))
            .andReturn();

        org.assertj.core.api.Assertions.assertThat(json(second).at("/data/sessions").isArray()).isTrue();
    }

    @Test
    void confirmedCursorScansOnlyNewCommits() throws Exception {
        String token = register("cursor-scan");
        String projectId = createProject(token, "Cursor Scan");
        Path projectPath = createGitProject("cursor-scan", 3);
        bindProject(token, projectId, projectPath);

        String baseSha = run(projectPath, "git", "rev-parse", "HEAD").trim();
        ProjectReviewCursor cursor = new ProjectReviewCursor(UUID.fromString(projectId));
        cursor.advance(baseSha, Instant.now(), "master", "", null);
        cursorRepository.saveAndFlush(cursor);

        commit(projectPath, 4);
        commit(projectPath, 5);

        scan(token, projectId)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.firstScan").value(false))
            .andExpect(jsonPath("$.data.batch.baseCommitSha").value(baseSha))
            .andExpect(jsonPath("$.data.batch.newCommitCount").value(2));
    }

    @Test
    void unreachableCursorFallsBackByTimeWithWarning() throws Exception {
        String token = register("fallback-scan");
        String projectId = createProject(token, "Fallback Scan");
        Path projectPath = createGitProject("fallback-scan", 3);
        bindProject(token, projectId, projectPath);

        ProjectReviewCursor cursor = new ProjectReviewCursor(UUID.fromString(projectId));
        cursor.advance("0000000000000000000000000000000000000000", Instant.now().minusSeconds(86_400), "master", "", null);
        cursorRepository.saveAndFlush(cursor);

        scan(token, projectId)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.firstScan").value(false))
            .andExpect(jsonPath("$.data.batch.warnings[0]").value(containsString("提交历史变化")))
            .andExpect(jsonPath("$.data.batch.newCommitCount").value(3));
    }

    private org.springframework.test.web.servlet.ResultActions scan(String token, String projectId) throws Exception {
        return mockMvc.perform(post("/api/projects/" + projectId + "/scan")
            .header("Authorization", "Bearer " + token));
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String register(String label) throws Exception {
        String unique = UUID.randomUUID().toString().replace("-", "");
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"%s-%s","email":"%s@example.com","password":"local-password-123"}
                    """.formatted(label, unique.substring(0, 8), unique)))
            .andExpect(status().isOk())
            .andReturn();
        return json(result).at("/data/accessToken").asText();
    }

    private String createProject(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name":"%s","description":"V3.3 scan test","status":"BUILDING",
                      "techStack":["Spring Boot"],"repoUrl":"","startDate":"2026-07-01","endDate":null
                    }
                    """.formatted(name)))
            .andExpect(status().isOk())
            .andReturn();
        return json(result).at("/data/id").asText();
    }

    private void bindProject(String token, String projectId, Path projectPath) throws Exception {
        mockMvc.perform(post("/api/projects/" + projectId + "/agent-bridge/protocol")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"projectPath":"%s","requirements":"V3.3 scan test"}
                    """.formatted(projectPath.toAbsolutePath().normalize().toString().replace("\\", "\\\\"))))
            .andExpect(status().isOk());
    }

    private Path createGitProject(String label, int commits) throws Exception {
        Path root = Files.createDirectories(Path.of("target", "v33-scan-tests", label + "-" + UUID.randomUUID()));
        run(root, "git", "init", "-b", "master");
        run(root, "git", "config", "user.email", "agent@example.com");
        run(root, "git", "config", "user.name", "Codex Agent");
        for (int index = 1; index <= commits; index++) {
            commit(root, index);
        }
        return root;
    }

    private void commit(Path root, int index) throws Exception {
        Path file = root.resolve("src/module/file-" + index + ".txt");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "change " + index + "\n", StandardCharsets.UTF_8);
        run(root, "git", "add", ".");
        run(root, "git", "commit", "-m", "feat(module): change " + index);
    }

    private String run(Path workingDirectory, String... command) throws Exception {
        Process process = new ProcessBuilder(command)
            .directory(workingDirectory.toFile())
            .redirectErrorStream(true)
            .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new AssertionError(String.join(" ", command) + " failed: " + output);
        }
        return output;
    }
}
