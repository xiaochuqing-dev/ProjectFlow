package com.projectflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
import com.projectflow.repository.ProjectReviewCursorRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProjectSedimentControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ProjectReviewCursorRepository cursorRepository;

    @Test
    void confirmsFourActionsAndAdvancesCursorOnlyAfterTheBatchIsResolved() throws Exception {
        String token = register();
        String projectId = createProject(token);
        Path root = createGitProject();
        bindProject(token, projectId, root);

        mockMvc.perform(post("/api/projects/" + projectId + "/scan").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.segments", hasSize(4)));

        JsonNode changes = body(mockMvc.perform(get("/api/projects/" + projectId + "/changes")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(4)))
            .andReturn()).path("data");

        String first = changes.get(0).path("id").asText();
        String second = changes.get(1).path("id").asText();
        String third = changes.get(2).path("id").asText();
        String fourth = changes.get(3).path("id").asText();

        MvcResult created = confirm(token, first, "NEW_SEDIMENT", null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.sediment.title").isNotEmpty())
            .andReturn();
        String sedimentId = body(created).at("/data/sediment/id").asText();
        assertThat(cursorRepository.findByProjectId(UUID.fromString(projectId))).isEmpty();

        confirm(token, second, "MERGE_EXISTING", UUID.randomUUID().toString())
            .andExpect(status().isNotFound());
        confirm(token, second, "MERGE_EXISTING", sedimentId)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.sediment.id").value(sedimentId));
        assertThat(cursorRepository.findByProjectId(UUID.fromString(projectId))).isEmpty();

        String summaryBeforeEvidenceOnly = body(mockMvc.perform(get("/api/project-sediments/" + sedimentId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn()).at("/data/summary").asText();
        confirm(token, third, "EVIDENCE_ONLY", sedimentId)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.sediment.summary").value(summaryBeforeEvidenceOnly));
        assertThat(cursorRepository.findByProjectId(UUID.fromString(projectId))).isEmpty();

        confirm(token, fourth, "IGNORE", null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.sediment").doesNotExist());

        mockMvc.perform(get("/api/projects/" + projectId + "/sediments").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(1)));
        assertThat(cursorRepository.findByProjectId(UUID.fromString(projectId))).get()
            .extracting(cursor -> cursor.getLastReviewedCommitSha()).isEqualTo(run(root, "git", "rev-parse", "HEAD").trim());

        JsonNode cards = body(mockMvc.perform(post("/api/projects/" + projectId + "/capabilities/analyze")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].status").value("CANDIDATE"))
            .andExpect(jsonPath("$.data[0].evidenceRefs[0]").isNotEmpty())
            .andReturn()).path("data");
        assertThat(cards.size()).isBetween(3, 8);

        String confirmedCardId = cards.get(0).path("id").asText();
        String untouchedCardId = cards.get(1).path("id").asText();
        mockMvc.perform(patch("/api/capability-cards/" + confirmedCardId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"CONFIRM\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("CONFIRMED"));

        mockMvc.perform(get("/api/projects/" + projectId + "/capability-cards")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[?(@.id == '" + confirmedCardId + "')].status").value("CONFIRMED"))
            .andExpect(jsonPath("$.data[?(@.id == '" + untouchedCardId + "')].status").value("CANDIDATE"));
    }

    @Test
    void legacyAcceptEndpointCreatesASedimentForV33Suggestions() throws Exception {
        String token = register();
        String projectId = createProject(token);
        Path root = createSingleChangeGitProject();
        bindProject(token, projectId, root);
        mockMvc.perform(post("/api/projects/" + projectId + "/scan").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
        String changeId = body(mockMvc.perform(get("/api/projects/" + projectId + "/changes")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn()).at("/data/0/id").asText();

        mockMvc.perform(post("/api/project-changes/" + changeId + "/accept").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/projects/" + projectId + "/sediments").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(1)));
    }

    private org.springframework.test.web.servlet.ResultActions confirm(String token, String changeId, String action, String targetId) throws Exception {
        String target = targetId == null ? "null" : "\"" + targetId + "\"";
        return mockMvc.perform(post("/api/project-changes/" + changeId + "/confirm")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"action\":\"" + action + "\",\"targetSedimentId\":" + target + "}"));
    }

    private String register() throws Exception {
        String unique = UUID.randomUUID().toString().replace("-", "");
        MvcResult result = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"sediment-" + unique.substring(0, 8) + "\",\"email\":\"" + unique + "@example.com\",\"password\":\"local-password-123\"}"))
            .andExpect(status().isOk()).andReturn();
        return body(result).at("/data/accessToken").asText();
    }

    private String createProject(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Sediment Project","description":"V3.3","status":"BUILDING","techStack":["Java"],"repoUrl":"","startDate":"2026-07-01","endDate":null}
                    """))
            .andExpect(status().isOk()).andReturn();
        return body(result).at("/data/id").asText();
    }

    private void bindProject(String token, String projectId, Path root) throws Exception {
        mockMvc.perform(post("/api/projects/" + projectId + "/agent-bridge/protocol")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"projectPath\":\"" + root.toAbsolutePath().normalize().toString().replace("\\", "\\\\") + "\",\"requirements\":\"V3.3\"}"))
            .andExpect(status().isOk());
    }

    private Path createGitProject() throws Exception {
        Path root = Files.createDirectories(Path.of("target", "v33-sediment-tests", UUID.randomUUID().toString()));
        run(root, "git", "init", "-b", "master");
        run(root, "git", "config", "user.email", "agent@example.com");
        run(root, "git", "config", "user.name", "Codex Agent");
        String[] scopes = {"scan", "protocol", "sediment", "output"};
        for (int index = 0; index < 12; index++) {
            String scope = scopes[index % scopes.length];
            Path file = root.resolve(scope + "/file-" + index + ".txt");
            Files.createDirectories(file.getParent());
            Files.writeString(file, "change " + index, StandardCharsets.UTF_8);
            run(root, "git", "add", ".");
            run(root, "git", "commit", "-m", "feat(" + scope + "): change " + index);
        }
        return root;
    }

    private Path createSingleChangeGitProject() throws Exception {
        Path root = Files.createDirectories(Path.of("target", "v33-sediment-tests", UUID.randomUUID().toString()));
        run(root, "git", "init", "-b", "master");
        run(root, "git", "config", "user.email", "agent@example.com");
        run(root, "git", "config", "user.name", "Codex Agent");
        Path file = root.resolve("scan/cursor.txt");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "cursor", StandardCharsets.UTF_8);
        run(root, "git", "add", ".");
        run(root, "git", "commit", "-m", "feat(scan): add cursor");
        return root;
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String run(Path root, String... command) throws Exception {
        Process process = new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();
        if (exitCode != 0) throw new AssertionError(String.join(" ", command) + " failed: " + output);
        return output;
    }
}
