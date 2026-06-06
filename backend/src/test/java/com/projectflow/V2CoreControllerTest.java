package com.projectflow;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class V2CoreControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createsProviderWithoutLeakingApiKey() throws Exception {
        String token = register("provider-owner", "provider-owner@example.com");

        mockMvc.perform(post("/api/ai-providers")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "DeepSeek Local",
                      "baseUrl": "https://api.deepseek.com/v1/",
                      "apiKey": "sk-test-secret-value",
                      "modelName": "deepseek-chat",
                      "type": "DEEPSEEK",
                      "temperature": 0.2,
                      "maxTokens": 4096,
                      "defaultEnabled": true,
                      "purposeTags": ["项目分析", "材料解析"]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.apiKeyConfigured").value(true))
            .andExpect(jsonPath("$.data.baseUrl").value("https://api.deepseek.com/v1"))
            .andExpect(content().string(not(containsString("sk-test-secret-value"))));
    }

    @Test
    void normalizesDeepSeekChatCompletionsEndpointToBaseUrl() throws Exception {
        String token = register("deepseek-url-owner", "deepseek-url-owner@example.com");

        mockMvc.perform(post("/api/ai-providers")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "DeepSeek",
                      "baseUrl": "https://api.deepseek.com/chat/completions",
                      "apiKey": "sk-test-secret-value",
                      "modelName": "deepseek-v4-pro",
                      "type": "DEEPSEEK",
                      "temperature": 0.3,
                      "maxTokens": 8192,
                      "defaultEnabled": true,
                      "purposeTags": ["项目分析"]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.baseUrl").value("https://api.deepseek.com"))
            .andExpect(content().string(not(containsString("sk-test-secret-value"))));
    }

    @Test
    void acceptsLargeProviderMaxTokenConfiguration() throws Exception {
        String token = register("large-token-owner", "large-token-owner@example.com");

        mockMvc.perform(post("/api/ai-providers")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "DeepSeek Large Context",
                      "baseUrl": "https://api.deepseek.com",
                      "apiKey": "sk-test-secret-value",
                      "modelName": "deepseek-v4-pro",
                      "type": "DEEPSEEK",
                      "temperature": 0.3,
                      "maxTokens": 1000000,
                      "defaultEnabled": true,
                      "purposeTags": ["项目分析"]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.maxTokens").value(1000000));
    }

    @Test
    void analyzesMaterialAndAppliesSuggestionsAfterConfirmation() throws Exception {
        String token = register("v2-owner", "v2-owner@example.com");
        String projectId = createProject(token, "V2 Project");

        MvcResult materialResult = mockMvc.perform(post("/api/projects/" + projectId + "/materials/text")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "sourceType": "AGENT_SUMMARY",
                      "content": "本轮完成 V2 Core 的 Project Material、AI suggestion 和 Project Memory 设计。验证了后端测试。下一步是升级 Dashboard 驾驶舱，并跟踪 provider 风险。"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.sourceType").value("AGENT_SUMMARY"))
            .andReturn();
        String materialId = objectMapper.readTree(materialResult.getResponse().getContentAsString()).get("data").get("id").asText();

        MvcResult analyzeResult = mockMvc.perform(post("/api/project-materials/" + materialId + "/analyze")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.suggestions.length()", greaterThanOrEqualTo(3)))
            .andReturn();

        JsonNode suggestions = objectMapper.readTree(analyzeResult.getResponse().getContentAsString()).get("data").get("suggestions");
        List<String> ids = new ArrayList<>();
        suggestions.forEach(suggestion -> ids.add(suggestion.get("id").asText()));

        mockMvc.perform(get("/api/projects/" + projectId + "/tasks")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(0)));

        mockMvc.perform(post("/api/projects/" + projectId + "/suggestions/apply")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "suggestionIds": %s
                    }
                    """.formatted(objectMapper.writeValueAsString(ids))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.appliedCount", greaterThanOrEqualTo(3)))
            .andExpect(jsonPath("$.data.memory.currentStage").value("V2 Core 构建"))
            .andExpect(jsonPath("$.data.snapshot.currentStage").value("V2 Core 构建"))
            .andExpect(jsonPath("$.data.evolutionRecord.summary").value("确认并应用 " + ids.size() + " 条 AI 建议。"));

        mockMvc.perform(get("/api/projects/" + projectId + "/tasks")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()", greaterThanOrEqualTo(1)));
    }

    @Test
    void importsProjectZipWithoutManualProjectAndKeepsSuggestionsPending() throws Exception {
        String token = register("zip-owner", "zip-owner@example.com");
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "ProjectFlow.zip",
            "application/zip",
            projectZip()
        );

        MvcResult result = mockMvc.perform(multipart("/api/project-imports/zip")
                .file(file)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.project.name").value("projectflow-v2"))
            .andExpect(jsonPath("$.data.material.sourceType").value("PROJECT_ZIP"))
            .andExpect(jsonPath("$.data.projectProfile.inferredProjectName").value("projectflow-v2"))
            .andExpect(jsonPath("$.data.projectProfile.hasReadme").value(true))
            .andExpect(jsonPath("$.data.projectProfile.hasTests").value(true))
            .andExpect(jsonPath("$.data.projectProfile.hasStartScript").value(true))
            .andExpect(jsonPath("$.data.projectProfile.hasDeployConfig").value(true))
            .andExpect(jsonPath("$.data.projectProfile.looksEmptyShell").value(false))
            .andExpect(jsonPath("$.data.projectProfile.techStack[0]").value("Next.js"))
            .andExpect(jsonPath("$.data.suggestions.length()", greaterThanOrEqualTo(3)))
            .andExpect(content().string(not(containsString("DATABASE_PASSWORD=secret"))))
            .andReturn();

        String projectId = objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("project").get("id").asText();

        mockMvc.perform(get("/api/projects/" + projectId + "/tasks")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void writesProjectFlowProtocolFilesWithoutRequiringGeneratedTaskBrief() throws Exception {
        String token = register("bridge-owner", "bridge-owner@example.com");
        String projectId = createProject(token, "Bridge Project");
        Path projectPath = createTestProjectDir("bridge-project");

        mockMvc.perform(post("/api/projects/" + projectId + "/agent-bridge/protocol")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "projectPath": "%s",
                      "requirements": "Keep ProjectFlow practical for developers. Agent result write-back is required; generated task briefs are optional."
                    }
                    """.formatted(jsonEscapedPath(projectPath))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.projectFlowDir").value(projectPath.toAbsolutePath().normalize().resolve(".projectflow").toString()))
            .andExpect(jsonPath("$.data.alreadyLinked").value(false))
            .andExpect(jsonPath("$.data.writtenFiles", hasSize(greaterThanOrEqualTo(5))))
            .andExpect(jsonPath("$.data.globalRule").value(containsString(".projectflow/agent-protocol.md")));

        String protocol = Files.readString(projectPath.resolve(".projectflow/agent-protocol.md"));
        String requirements = Files.readString(projectPath.resolve(".projectflow/context/requirements.md"));

        org.assertj.core.api.Assertions.assertThat(protocol)
            .contains("ProjectFlow Agent Protocol")
            .contains(".projectflow/inbox/")
            .contains("Do not directly modify ProjectFlow task state");
        org.assertj.core.api.Assertions.assertThat(requirements)
            .contains("Agent result write-back is required")
            .contains("generated task briefs are optional");

        mockMvc.perform(get("/api/projects/" + projectId + "/memory")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.localProjectPath").value(projectPath.toAbsolutePath().normalize().toString()));
    }

    @Test
    void writingProjectFlowProtocolAgainReportsExistingLink() throws Exception {
        String token = register("bridge-repeat-owner", "bridge-repeat-owner@example.com");
        String projectId = createProject(token, "Repeated Bridge Project");
        Path projectPath = createTestProjectDir("bridge-repeat-project");

        String body = """
            {
              "projectPath": "%s",
              "requirements": "Keep ProjectFlow linked."
            }
            """.formatted(jsonEscapedPath(projectPath));

        mockMvc.perform(post("/api/projects/" + projectId + "/agent-bridge/protocol")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.alreadyLinked").value(false));

        mockMvc.perform(post("/api/projects/" + projectId + "/agent-bridge/protocol")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.alreadyLinked").value(true));
    }

    @Test
    void scansAgentResultIntoPendingSuggestionsWithoutChangingTaskState() throws Exception {
        String token = register("scan-owner", "scan-owner@example.com");
        String projectId = createProject(token, "Scan Project");
        Path projectPath = createTestProjectDir("scan-project");
        Path inbox = Files.createDirectories(projectPath.resolve(".projectflow/inbox"));
        Files.writeString(inbox.resolve("20260606-1200-agent-result.md"), """
            # ProjectFlow Agent Result

            ProjectId: scan-project
            TaskId: PF-101
            Status: ready_for_review

            ## Summary
            Agent completed the compact project management layout and added protocol write-back.

            ## Changed Files
            - frontend/src/app/dashboard/page.tsx
            - backend/src/main/java/com/projectflow/service/ProjectIntelligenceService.java

            ## Task Updates
            - PF-101: ready_for_review
            - New: Add processed marker for imported agent result files

            ## Decisions
            - ProjectFlow generated task briefs are optional.
            - Agent result write-back is the required integration point.

            ## Risks
            - Need to handle project folders without write permission.

            ## Dev Log
            Implemented the first local file bridge between ProjectFlow and agent work.
            """);

        mockMvc.perform(get("/api/projects/" + projectId + "/tasks")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(0)));

        mockMvc.perform(post("/api/projects/" + projectId + "/agent-bridge/scan")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "projectPath": "%s"
                    }
                    """.formatted(jsonEscapedPath(projectPath))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.importedResults").value(1))
            .andExpect(jsonPath("$.data.suggestions.length()", greaterThanOrEqualTo(4)))
            .andExpect(jsonPath("$.data.suggestions[0].status").value("PENDING"));

        mockMvc.perform(get("/api/projects/" + projectId + "/tasks")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void scansAgentResultWithRealTaskIdIntoLinkedPendingSuggestions() throws Exception {
        String token = register("linked-scan-owner", "linked-scan-owner@example.com");
        String projectId = createProject(token, "Linked Scan Project");
        String taskId = createTask(token, projectId, "Complete linked agent workflow");
        Path projectPath = createTestProjectDir("linked-scan-project");
        Path taskDir = Files.createDirectories(projectPath.resolve(".projectflow/tasks").resolve(taskId));
        Files.writeString(taskDir.resolve("result.md"), """
            # ProjectFlow Agent Result

            ProjectId: linked-scan-project
            TaskId: %s
            Status: ready_for_review

            ## Summary
            Agent finished the linked workflow task.

            ## Changed Files
            - frontend/src/app/dashboard/page.tsx

            ## Task Updates
            - %s: ready_for_review

            ## Decisions
            - Keep ProjectFlow confirmation as the final source of truth.

            ## Risks
            - None.

            ## Dev Log
            Finished linked agent workflow implementation.
            """.formatted(taskId, taskId));

        mockMvc.perform(post("/api/projects/" + projectId + "/agent-bridge/scan")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "projectPath": "%s"
                    }
                    """.formatted(jsonEscapedPath(projectPath))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.importedResults").value(1))
            .andExpect(jsonPath("$.data.warnings", hasSize(0)))
            .andExpect(jsonPath("$.data.suggestions[0].payload.taskId").value(taskId))
            .andExpect(jsonPath("$.data.suggestions[0].payload.taskTitle").value("Complete linked agent workflow"));
    }

    @Test
    void scanSkipsMalformedAgentResultAndReportsWarning() throws Exception {
        String token = register("bad-result-owner", "bad-result-owner@example.com");
        String projectId = createProject(token, "Bad Result Project");
        Path projectPath = createTestProjectDir("bad-result-project");
        Path inbox = Files.createDirectories(projectPath.resolve(".projectflow/inbox"));
        Files.writeString(inbox.resolve("20260606-1300-agent-result.md"), "this is not a ProjectFlow agent result");

        mockMvc.perform(post("/api/projects/" + projectId + "/agent-bridge/scan")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "projectPath": "%s"
                    }
                    """.formatted(jsonEscapedPath(projectPath))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.importedResults").value(0))
            .andExpect(jsonPath("$.data.suggestions", hasSize(0)))
            .andExpect(jsonPath("$.data.warnings[0]").value(containsString("invalid ProjectFlow Agent Result")));
    }

    @Test
    void bridgeRejectsDangerousSystemLevelProjectPath() throws Exception {
        String token = register("path-owner", "path-owner@example.com");
        String projectId = createProject(token, "Path Safety Project");
        Path driveRoot = Path.of("C:\\");

        mockMvc.perform(post("/api/projects/" + projectId + "/agent-bridge/protocol")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "projectPath": "%s",
                      "requirements": "should be rejected"
                    }
                    """.formatted(jsonEscapedPath(driveRoot))))
            .andExpect(status().isBadRequest())
            .andExpect(content().string(containsString("Project folder path is too broad")));
    }

    @Test
    void writesTaskBriefFilesForOptionalProjectFlowPlannedWork() throws Exception {
        String token = register("brief-owner", "brief-owner@example.com");
        String projectId = createProject(token, "Brief Project");
        String taskId = createTask(token, projectId, "Wire agent bridge into project management");
        Path projectPath = createTestProjectDir("brief-project");

        mockMvc.perform(post("/api/projects/" + projectId + "/agent-bridge/tasks/" + taskId + "/brief")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "projectPath": "%s",
                      "requirements": "Generate a task-level brief only when the developer chooses this optional ProjectFlow path."
                    }
                    """.formatted(jsonEscapedPath(projectPath))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.taskId").value(taskId))
            .andExpect(jsonPath("$.data.writtenFiles", hasSize(3)))
            .andExpect(jsonPath("$.data.briefPath").value(containsString(".projectflow/tasks/" + taskId + "/brief.md")))
            .andExpect(jsonPath("$.data.resultPath").value(containsString(".projectflow/tasks/" + taskId + "/result.md")));

        Path taskDir = projectPath.resolve(".projectflow/tasks").resolve(taskId);
        String brief = Files.readString(taskDir.resolve("brief.md"));
        String result = Files.readString(taskDir.resolve("result.md"));
        String status = Files.readString(taskDir.resolve("status.json"));

        org.assertj.core.api.Assertions.assertThat(brief)
            .contains("ProjectFlow Agent Brief")
            .contains("Wire agent bridge into project management")
            .contains("Generate a task-level brief only when the developer chooses this optional ProjectFlow path");
        org.assertj.core.api.Assertions.assertThat(result)
            .contains("# ProjectFlow Agent Result")
            .contains("TaskId: " + taskId);
        org.assertj.core.api.Assertions.assertThat(status)
            .contains("\"taskId\"")
            .contains("\"waiting_for_agent\"");
    }

    private String register(String username, String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "username": "%s",
                      "email": "%s",
                      "password": "local-password-123"
                    }
                    """.formatted(username, email)))
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
                      "description": "AI project process cockpit.",
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

    private String createTask(String token, String projectId, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects/" + projectId + "/tasks")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "%s",
                      "description": "Task created for agent bridge brief generation.",
                      "status": "TODO",
                      "priority": "MEDIUM",
                      "dueDate": null,
                      "tags": ["agent-bridge"]
                    }
                    """.formatted(title)))
            .andExpect(status().isOk())
            .andReturn();

        return result.getResponse().getContentAsString().split("\"id\":\"")[1].split("\"")[0];
    }

    private byte[] projectZip() throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(outputStream)) {
            addZipEntry(zip, "projectflow-v2/README.md", "# ProjectFlow V2\n\nAI project process management.");
            addZipEntry(zip, "projectflow-v2/package.json", """
                {
                  "name": "projectflow-v2",
                  "dependencies": {
                    "next": "16.2.2",
                    "react": "19.2.0"
                  }
                }
                """);
            addZipEntry(zip, "projectflow-v2/pom.xml", """
                <project>
                  <artifactId>projectflow</artifactId>
                  <dependencies>
                    <dependency>
                      <artifactId>spring-boot-starter-web</artifactId>
                    </dependency>
                  </dependencies>
                </project>
                """);
            addZipEntry(zip, "projectflow-v2/docker-compose.yml", "services:\n  db:\n    image: postgres:16\n");
            addZipEntry(zip, "projectflow-v2/start-projectflow.ps1", "npm.cmd run dev");
            addZipEntry(zip, "projectflow-v2/src/app/page.tsx", "export default function Page() { return null; }");
            addZipEntry(zip, "projectflow-v2/backend/src/test/java/AppTest.java", "class AppTest {}");
            addZipEntry(zip, "projectflow-v2/.env", "DATABASE_PASSWORD=secret");
        }
        return outputStream.toByteArray();
    }

    private void addZipEntry(ZipOutputStream zip, String name, String content) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes());
        zip.closeEntry();
    }

    private String jsonEscapedPath(Path path) {
        return path.toAbsolutePath().normalize().toString().replace("\\", "\\\\");
    }

    private Path createTestProjectDir(String label) throws Exception {
        Path root = Path.of("target", "projectflow-bridge-tests", label + "-" + UUID.randomUUID());
        return Files.createDirectories(root);
    }
}
