package com.projectflow;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class WorkSessionScanControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void scansBoundGitProjectIntoTodayWorkSessionCandidate() throws Exception {
        String token = register("scan-owner", "scan-owner@example.com");
        String projectId = createProject(token, "Scan Project");
        Path projectPath = createGitProject("scan-project");

        writeProtocol(token, projectId, projectPath);

        MvcResult scanResult = mockMvc.perform(post("/api/projects/" + projectId + "/scan")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.sessions.length()").value(greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.data.sessions[0].agentType").value("UNKNOWN"))
            .andExpect(jsonPath("$.data.sessions[0].attributionConfidence").value("MEDIUM"))
            .andExpect(jsonPath("$.data.sessions[0].detectionMethod").value("GIT_EVIDENCE"))
            .andExpect(jsonPath("$.data.sessions[0].changedFiles").value(greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.data.sessions[0].addedLines").value(greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.data.sessions[0].affectedModules[0]").value("src"))
            .andExpect(jsonPath("$.data.sessions[0].evidence[0]").value(containsString("Git commit")))
            .andExpect(jsonPath("$.data.warnings.length()").value(0))
            .andReturn();

        String sessionId = objectMapper.readTree(scanResult.getResponse().getContentAsString())
            .at("/data/sessions/0/sessionId")
            .asText();

        mockMvc.perform(get("/api/projects/" + projectId + "/work-sessions")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].sessionId").value(sessionId))
            .andExpect(jsonPath("$.data[0].attributionConfidence").value("MEDIUM"));

        mockMvc.perform(patch("/api/work-sessions/" + sessionId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "agentType": "CODEX",
                      "taskIntent": "实现今日变化概览"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.agentType").value("CODEX"))
            .andExpect(jsonPath("$.data.taskIntent").value("实现今日变化概览"))
            .andExpect(jsonPath("$.data.detectionMethod").value("USER_CORRECTED"));

        MvcResult bundleResult = mockMvc.perform(post("/api/work-sessions/" + sessionId + "/evidence-bundles")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.workSessionId").value(sessionId))
            .andExpect(jsonPath("$.data.agentType").value("CODEX"))
            .andExpect(jsonPath("$.data.changedFiles").value(greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.data.addedLines").value(greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.data.sources[0].sourceType").value("GIT_EVIDENCE"))
            .andExpect(jsonPath("$.data.objectiveEvidence[0]").value(containsString("Git commit")))
            .andExpect(jsonPath("$.data.agentClaims.length()").value(0))
            .andReturn();

        String bundleId = objectMapper.readTree(bundleResult.getResponse().getContentAsString())
            .at("/data/id")
            .asText();

        mockMvc.perform(get("/api/projects/" + projectId + "/evidence-bundles")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].id").value(bundleId))
            .andExpect(jsonPath("$.data[0].workSessionId").value(sessionId));

        MvcResult draftChangeResult = mockMvc.perform(post("/api/evidence-bundles/" + bundleId + "/draft-changes")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.sourceType").value("EVIDENCE_BUNDLE"))
            .andExpect(jsonPath("$.data.status").value("PENDING"))
            .andExpect(jsonPath("$.data.title").value(containsString("Evidence Bundle")))
            .andExpect(jsonPath("$.data.affectedFiles").value(containsString("src/app/page.tsx")))
            .andReturn();

        String changeId = objectMapper.readTree(draftChangeResult.getResponse().getContentAsString())
            .at("/data/id")
            .asText();

        mockMvc.perform(get("/api/projects/" + projectId + "/changes")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].id").value(changeId));

        mockMvc.perform(post("/api/project-changes/" + changeId + "/accept")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/projects/" + projectId + "/context/sync")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.writtenFiles[0]").value(".projectflow/context/projectflow-context.md"));

        String syncedContext = Files.readString(projectPath.resolve(".projectflow/context/projectflow-context.md"));
        org.assertj.core.api.Assertions.assertThat(syncedContext)
            .contains("Confirmed ProjectFlow Context")
            .contains("实现今日变化概览")
            .doesNotContain("PENDING");

        mockMvc.perform(get("/api/projects/" + projectId + "/agent-signature-feedback")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].agentName").value("Codex Agent"))
            .andExpect(jsonPath("$.data[0].originalAgentType").value("UNKNOWN"))
            .andExpect(jsonPath("$.data[0].correctedAgentType").value("CODEX"));

        Files.writeString(projectPath.resolve("src/app/feedback.tsx"), "export function Feedback() { return null; }\n");
        run(projectPath, "git", "add", ".");
        run(projectPath, "git", "commit", "-m", "feat: add feedback evidence");

        mockMvc.perform(post("/api/projects/" + projectId + "/scan")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.sessions[0].agentType").value("CODEX"))
            .andExpect(jsonPath("$.data.sessions[0].detectionMethod").value("USER_FEEDBACK"));

        Files.writeString(projectPath.resolve("src/app/page.tsx"), "export default function Page() { return <main>Conflict</main>; }\n");
        MvcResult conflictScan = mockMvc.perform(post("/api/projects/" + projectId + "/scan")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.sessions.length()").value(greaterThanOrEqualTo(2)))
            .andReturn();

        String uncommittedSessionId = objectMapper.readTree(conflictScan.getResponse().getContentAsString())
            .at("/data/sessions/0/sessionId")
            .asText();
        String committedSessionId = objectMapper.readTree(conflictScan.getResponse().getContentAsString())
            .at("/data/sessions/1/sessionId")
            .asText();

        mockMvc.perform(post("/api/work-sessions/" + uncommittedSessionId + "/evidence-bundles")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/work-sessions/" + committedSessionId + "/evidence-bundles")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/projects/" + projectId + "/change-conflicts")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].conflictType").value("FILE_OVERLAP"))
            .andExpect(jsonPath("$.data[0].filePath").value("src/app/page.tsx"))
            .andExpect(jsonPath("$.data[0].status").value("PENDING"));
    }

    @Test
    void scanRequiresBoundProjectPath() throws Exception {
        String token = register("scan-no-path-owner", "scan-no-path-owner@example.com");
        String projectId = createProject(token, "Unbound Scan Project");

        mockMvc.perform(post("/api/projects/" + projectId + "/scan")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isBadRequest())
            .andExpect(content().string(containsString("Bind a local project path before scanning")));
    }

    private String register(String username, String email) throws Exception {
        String unique = UUID.randomUUID().toString().replace("-", "");
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "username": "%s",
                      "email": "%s",
                      "password": "local-password-123"
                    }
                    """.formatted(username + "-" + unique.substring(0, 8), unique + "-" + email)))
            .andExpect(status().isOk())
            .andReturn();

        return result.getResponse().getContentAsString().split("\"accessToken\":\"")[1].split("\"")[0];
    }

    private String createProject(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "%s",
                      "description": "Agent evidence ledger.",
                      "status": "BUILDING",
                      "techStack": ["Spring Boot", "Next.js"],
                      "repoUrl": "https://github.com/example/projectflow",
                      "startDate": "2026-06-04",
                      "endDate": null
                    }
                    """.formatted(name)))
            .andExpect(status().isOk())
            .andReturn();

        return result.getResponse().getContentAsString().split("\"id\":\"")[1].split("\"")[0];
    }

    private void writeProtocol(String token, String projectId, Path projectPath) throws Exception {
        mockMvc.perform(post("/api/projects/" + projectId + "/agent-bridge/protocol")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "projectPath": "%s",
                      "requirements": "Bind project path for scan tests."
                    }
                    """.formatted(jsonEscapedPath(projectPath))))
            .andExpect(status().isOk());
    }

    private Path createGitProject(String label) throws Exception {
        Path root = Files.createDirectories(Path.of("target", "work-session-scan-tests", label + "-" + UUID.randomUUID()));
        run(root, "git", "init");
        run(root, "git", "config", "user.email", "agent@example.com");
        run(root, "git", "config", "user.name", "Codex Agent");
        Files.createDirectories(root.resolve("src/app"));
        Files.writeString(root.resolve("src/app/page.tsx"), "export default function Page() { return null; }\n");
        run(root, "git", "add", ".");
        run(root, "git", "commit", "-m", "feat: seed project");

        Files.writeString(root.resolve("src/app/page.tsx"), "export default function Page() { return <main>Today</main>; }\n");
        Files.writeString(root.resolve("src/app/today.tsx"), "export function Today() { return null; }\n");
        run(root, "git", "add", ".");
        run(root, "git", "commit", "-m", "feat: add today overview");
        return root;
    }

    private void run(Path workingDirectory, String... command) throws Exception {
        Process process = new ProcessBuilder(command)
            .directory(workingDirectory.toFile())
            .redirectErrorStream(true)
            .start();
        String output = new String(process.getInputStream().readAllBytes());
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new AssertionError(String.join(" ", command) + " failed: " + output);
        }
    }

    private String jsonEscapedPath(Path path) {
        return path.toAbsolutePath().normalize().toString().replace("\\", "\\\\");
    }
}
