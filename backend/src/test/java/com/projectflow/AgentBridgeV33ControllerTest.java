package com.projectflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AgentBridgeV33ControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void initializesV33ProtocolWithoutOverwritingAgentsAndScansStructuredResults() throws Exception {
        String token = register();
        String projectId = createProject(token);
        Path root = gitProject();
        Files.writeString(root.resolve("AGENTS.md"), "# User rules\n\nKeep this content.\n", StandardCharsets.UTF_8);

        writeProtocol(token, projectId, root);
        writeProtocol(token, projectId, root);

        assertThat(root.resolve(".projectflow/AGENT_PROTOCOL.md")).isRegularFile();
        assertThat(root.resolve(".projectflow/agent-results")).isDirectory();
        assertThat(root.resolve(".projectflow/templates")).isDirectory();
        String agents = Files.readString(root.resolve("AGENTS.md"), StandardCharsets.UTF_8);
        assertThat(agents).startsWith("<!-- PROJECTFLOW ENTRY START -->");
        assertThat(agents).contains("Keep this content.");
        assertThat(count(agents, "<!-- PROJECTFLOW ENTRY START -->")).isEqualTo(1);

        mockMvc.perform(get("/api/projects/" + projectId + "/agent-bridge/health")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.pathAccessible").value(true))
            .andExpect(jsonPath("$.data.protocolExists").value(true))
            .andExpect(jsonPath("$.data.resultsDirectoryExists").value(true))
            .andExpect(jsonPath("$.data.entryRulePresent").value(true))
            .andExpect(jsonPath("$.data.protocolVersion").value("3.3"));

        Path resultDir = Files.createDirectories(root.resolve(".projectflow/agent-results/2026-07-06-agent-health"));
        Files.writeString(resultDir.resolve("result.json"), """
            {
              "taskGoal":"Upgrade Agent protocol health",
              "actualChanges":["Added protocol health endpoint"],
              "keyFiles":["backend/src/main/java/com/projectflow/service/AgentBridgeHealthService.java"],
              "verification":{"build":"not_run","tests":"passed","manualCheck":"health endpoint returned OK"},
              "unfinished":[],
              "sedimentCandidates":["Agent protocol health"]
            }
            """, StandardCharsets.UTF_8);

        mockMvc.perform(post("/api/projects/" + projectId + "/agent-bridge/scan")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(request(root)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.importedResults").value(1))
            .andExpect(jsonPath("$.data.materials[0].content").value(org.hamcrest.Matchers.containsString("Upgrade Agent protocol health")));

        Files.delete(root.resolve(".projectflow/AGENT_PROTOCOL.md"));
        mockMvc.perform(get("/api/projects/" + projectId + "/agent-bridge/health")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.protocolExists").value(false))
            .andExpect(jsonPath("$.data.warnings[0]").value(org.hamcrest.Matchers.containsString("协议")));
    }

    private void writeProtocol(String token, String projectId, Path root) throws Exception {
        mockMvc.perform(post("/api/projects/" + projectId + "/agent-bridge/protocol")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(request(root)))
            .andExpect(status().isOk());
    }

    private String request(Path root) {
        return "{\"projectPath\":\"" + root.toAbsolutePath().normalize().toString().replace("\\", "\\\\") + "\",\"requirements\":\"V3.3\"}";
    }

    private String register() throws Exception {
        String unique = UUID.randomUUID().toString().replace("-", "");
        MvcResult result = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"bridge-" + unique.substring(0, 8) + "\",\"email\":\"" + unique + "@example.com\",\"password\":\"local-password-123\"}"))
            .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/accessToken").asText();
    }

    private String createProject(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Bridge V3.3","description":"Agent bridge","status":"BUILDING","techStack":["Java"],"repoUrl":"","startDate":"2026-07-01","endDate":null}
                    """))
            .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/id").asText();
    }

    private Path gitProject() throws Exception {
        Path root = Files.createDirectories(Path.of("target", "v33-agent-bridge-tests", UUID.randomUUID().toString()));
        run(root, "git", "init", "-b", "master");
        return root;
    }

    private void run(Path root, String... command) throws Exception {
        Process process = new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true).start();
        int exit = process.waitFor();
        if (exit != 0) throw new AssertionError(String.join(" ", command) + " failed");
    }

    private int count(String text, String needle) {
        return (text.length() - text.replace(needle, "").length()) / needle.length();
    }
}
