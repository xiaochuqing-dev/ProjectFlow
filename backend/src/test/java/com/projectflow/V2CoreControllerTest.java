package com.projectflow;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.sun.net.httpserver.HttpServer;
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
                      "maxTokens": 100000000,
                      "defaultEnabled": true,
                      "purposeTags": ["项目分析"]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.maxTokens").value(100000000));
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
            .andExpect(jsonPath("$.data.suggestions.length()", greaterThanOrEqualTo(3)))
            .andExpect(content().string(not(containsString("DATABASE_PASSWORD=secret"))))
            .andReturn();

        org.assertj.core.api.Assertions.assertThat(stringArray(objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/projectProfile/techStack")))
            .contains("Next.js", "React", "Java");

        String projectId = objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("project").get("id").asText();

        mockMvc.perform(get("/api/projects/" + projectId + "/tasks")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void importingSameProjectZipWithoutManualProjectReusesExistingProject() throws Exception {
        String token = register("zip-dedupe-owner", "zip-dedupe-owner@example.com");

        MvcResult first = mockMvc.perform(multipart("/api/project-imports/zip")
                .file(new MockMultipartFile("file", "ProjectFlow.zip", "application/zip", projectZip()))
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn();
        String firstProjectId = objectMapper.readTree(first.getResponse().getContentAsString()).at("/data/project/id").asText();

        MvcResult second = mockMvc.perform(multipart("/api/project-imports/zip")
                .file(new MockMultipartFile("file", "ProjectFlow.zip", "application/zip", projectZip()))
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn();
        String secondProjectId = objectMapper.readTree(second.getResponse().getContentAsString()).at("/data/project/id").asText();

        org.assertj.core.api.Assertions.assertThat(secondProjectId).isEqualTo(firstProjectId);
        mockMvc.perform(get("/api/projects")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(1)))
            .andExpect(jsonPath("$.data[0].id").value(firstProjectId));
    }

    @Test
    void importsProjectZipIgnoringCodexRunGitBackupsBeforeEntryLimit() throws Exception {
        String token = register("zip-codex-run-owner", "zip-codex-run@example.com");
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "insightwrite-2.0.zip",
            "application/zip",
            zipWithCodexRunBackupFirst()
        );

        MvcResult result = mockMvc.perform(multipart("/api/project-imports/zip")
                .file(file)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.projectProfile.inferredProjectName").value("insightwrite-2.0"))
            .andExpect(jsonPath("$.data.projectProfile.hasReadme").value(true))
            .andExpect(jsonPath("$.data.projectProfile.hasTests").value(true))
            .andExpect(jsonPath("$.data.projectProfile.hasStartScript").value(true))
            .andExpect(jsonPath("$.data.projectProfile.hasDeployConfig").value(true))
            .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        org.assertj.core.api.Assertions.assertThat(stringArray(response.at("/data/projectProfile/techStack"))).contains("Vue", "Vite", "Java");
        String materialContent = response.at("/data/material/content").asText();
        org.assertj.core.api.Assertions.assertThat(materialContent)
            .contains("README.md")
            .contains("frontend/package.json")
            .contains("backend/pom.xml")
            .doesNotContain(".codex-run");
    }

    @Test
    void importsPolyglotFullStackProjectWithoutAssumingFixedFrontendBackendFolders() throws Exception {
        String token = register("polyglot-zip-owner", "polyglot-zip@example.com");
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "polyglot-workspace.zip",
            "application/zip",
            polyglotFullStackZip()
        );

        MvcResult result = mockMvc.perform(multipart("/api/project-imports/zip")
                .file(file)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.projectProfile.inferredProjectName").value("polyglot-workspace"))
            .andExpect(jsonPath("$.data.projectProfile.hasReadme").value(true))
            .andExpect(jsonPath("$.data.projectProfile.hasTests").value(true))
            .andExpect(jsonPath("$.data.projectProfile.hasStartScript").value(true))
            .andExpect(jsonPath("$.data.projectProfile.hasDeployConfig").value(true))
            .andExpect(jsonPath("$.data.projectProfile.looksEmptyShell").value(false))
            .andReturn();

        JsonNode profile = objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/projectProfile");
        org.assertj.core.api.Assertions.assertThat(stringArray(profile.get("techStack")))
            .contains("React", "Vite", "Python", "FastAPI", "Java", "Gradle", "Docker Compose");
        org.assertj.core.api.Assertions.assertThat(stringArray(profile.get("moduleStructure")))
            .contains("apps/web/package.json", "services/api/pyproject.toml", "services/api/app/main.py", "services/worker/build.gradle");
    }

    @Test
    void importsGbkEncodedChineseProjectZip() throws Exception {
        String token = register("gbk-zip-owner", "gbk-zip@example.com");
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "综测系统.zip",
            "application/zip",
            gbkChineseProjectZip()
        );

        MvcResult result = mockMvc.perform(multipart("/api/project-imports/zip")
                .file(file)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.projectProfile.inferredProjectName").value("综测系统"))
            .andExpect(jsonPath("$.data.projectProfile.hasReadme").value(true))
            .andExpect(jsonPath("$.data.projectProfile.hasTests").value(true))
            .andExpect(jsonPath("$.data.projectProfile.looksEmptyShell").value(false))
            .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        org.assertj.core.api.Assertions.assertThat(response.at("/data/material/content").asText())
            .contains("README.md")
            .contains("scorecard_batch/ocr.py");
        org.assertj.core.api.Assertions.assertThat(stringArray(response.at("/data/projectProfile/techStack"))).contains("Python");
    }

    @Test
    void analysisIgnoresCodexRunNoiseAlreadyStoredInOlderZipMaterial() throws Exception {
        String token = register("legacy-noise-owner", "legacy-noise@example.com");
        String projectId = createProject(token, "Legacy Noise Project");
        String content = """
            # Project zip summary

            ## Directory tree
            - .codex-run/old-git-20260602132318/objects/aa/object-1
            - .codex-run/old-git-20260602132318/config
            - apps/web/package.json
            - apps/web/src/App.tsx
            - services/api/pyproject.toml
            - services/api/app/main.py
            - services/api/tests/test_main.py
            - README.md

            ## Key files

            ### apps/web/package.json
            {"dependencies":{"react":"19.0.0","vite":"6.0.0"}}

            ### services/api/pyproject.toml
            [project]
            dependencies = ["fastapi"]

            ### README.md
            # Legacy Noise Project
            """;

        mockMvc.perform(post("/api/projects/" + projectId + "/materials/text")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "sourceType": "PROJECT_ZIP",
                      "content": %s
                    }
                    """.formatted(objectMapper.writeValueAsString(content))))
            .andExpect(status().isOk());

        JsonNode completed = runProjectAnalysisAndAwait(token, projectId);
        org.assertj.core.api.Assertions.assertThat(stringArray(completed.at("/projectResult/modules")))
            .doesNotContain(".codex-run")
            .contains("frontend", "backend");
        org.assertj.core.api.Assertions.assertThat(completed.at("/projectResult/summary").asText()).doesNotContain(".codex-run");
        org.assertj.core.api.Assertions.assertThat(stringArray(completed.at("/projectResult/evidence")))
            .noneMatch(item -> item.contains(".codex-run"));
    }

    @Test
    void modelProjectAnalysisDoesNotReceiveCodexRunNoiseFromLegacyMaterial() throws Exception {
        AtomicReference<String> lastRequestBody = new AtomicReference<>("");
        HttpServer modelServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        modelServer.createContext("/v1/chat/completions", exchange -> {
            lastRequestBody.set(new String(exchange.getRequestBody().readAllBytes()));
            byte[] body = """
                {"choices":[{"message":{"content":"{\\"summary\\":\\"Legacy Noise Project 是一个前后端项目。\\",\\"architecture\\":\\"apps/web 提供前端入口，services/api 提供后端接口。\\",\\"modules\\":[\\"frontend\\",\\"backend\\"],\\"risks\\":[\\"未发现明确风险证据。\\"],\\"importantFiles\\":[\\"apps/web/package.json\\",\\"services/api/pyproject.toml\\"],\\"evidence\\":[\\"apps/web/package.json：声明 React 依赖\\",\\"services/api/pyproject.toml：声明 FastAPI 依赖\\",\\"README.md：说明项目名称\\"],\\"limitations\\":[\\"仅依据已导入材料分析。\\"],\\"confidence\\":\\"high\\"}"}}]}
                """.getBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        modelServer.start();

        try {
            String token = register("legacy-model-noise-owner", "legacy-model-noise@example.com");
            mockMvc.perform(post("/api/ai-providers")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "name": "Noise sanitizer provider",
                          "baseUrl": "http://127.0.0.1:%d/v1",
                          "apiKey": "test-key",
                          "modelName": "test-model",
                          "type": "OPENAI_COMPATIBLE",
                          "temperature": 0.1,
                          "maxTokens": 200000,
                          "defaultEnabled": true,
                          "purposeTags": ["项目分析"]
                        }
                        """.formatted(modelServer.getAddress().getPort())))
                .andExpect(status().isOk());
            String projectId = createProject(token, "Legacy Noise Project");
            String content = """
                # Project zip summary

                ## Directory tree
                - .codex-run/old-git-20260602132318/objects/aa/object-1
                - .git/objects/aa/object-2
                - apps/web/package.json
                - services/api/pyproject.toml
                - README.md

                ## Key files

                ### .codex-run/old-git-20260602132318/config
                [core]
                  repositoryformatversion = 0

                ### apps/web/package.json
                {"dependencies":{"react":"19.0.0","vite":"6.0.0"}}

                ### services/api/pyproject.toml
                [project]
                dependencies = ["fastapi"]

                ### README.md
                # Legacy Noise Project
                """;

            mockMvc.perform(post("/api/projects/" + projectId + "/materials/text")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "sourceType": "PROJECT_ZIP",
                          "content": %s
                        }
                        """.formatted(objectMapper.writeValueAsString(content))))
                .andExpect(status().isOk());

            JsonNode completed = runProjectAnalysisAndAwait(token, projectId);
            org.assertj.core.api.Assertions.assertThat(completed.at("/projectResult/modelUsed").asBoolean()).isTrue();
            org.assertj.core.api.Assertions.assertThat(lastRequestBody.get())
                .contains("apps/web/package.json")
                .doesNotContain(".codex-run")
                .doesNotContain("old-git-")
                .doesNotContain(".git/objects");
        } finally {
            modelServer.stop(0);
        }
    }

    @Test
    void runsProjectAnalysisWithLocalFallbackWhenProviderIsNotConfigured() throws Exception {
        String token = register("analysis-owner", "analysis-owner@example.com");
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
            .andReturn();
        String projectId = objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("project").get("id").asText();

        JsonNode completed = runProjectAnalysisAndAwait(token, projectId);
        org.assertj.core.api.Assertions.assertThat(completed.at("/projectResult/providerConfigured").asBoolean()).isFalse();
        org.assertj.core.api.Assertions.assertThat(completed.at("/projectResult/modelUsed").asBoolean()).isFalse();
        org.assertj.core.api.Assertions.assertThat(completed.at("/projectResult/analysisSource").asText()).isEqualTo("LOCAL_RULE");
        org.assertj.core.api.Assertions.assertThat(completed.at("/projectResult/summary").asText()).contains("ProjectFlow");
        org.assertj.core.api.Assertions.assertThat(completed.at("/projectResult/modules").size()).isGreaterThanOrEqualTo(3);
        org.assertj.core.api.Assertions.assertThat(completed.at("/projectResult/risks/0").asText()).contains("模型");

        mockMvc.perform(get("/api/projects/" + projectId + "/model-usage-records")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].operation").value("PROJECT_ANALYSIS"))
            .andExpect(jsonPath("$.data[0].usageEstimated").value(true))
            .andExpect(jsonPath("$.data[0].totalTokens", greaterThanOrEqualTo(1)));
    }

    @Test
    void persistsAsyncProjectAnalysisJobAndRestoresChineseEvidence() throws Exception {
        String token = register("async-analysis-owner", "async-analysis-owner@example.com");
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "ProjectFlow.zip",
            "application/zip",
            projectZip()
        );

        MvcResult importResult = mockMvc.perform(multipart("/api/project-imports/zip")
                .file(file)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn();
        String projectId = objectMapper.readTree(importResult.getResponse().getContentAsString()).get("data").get("project").get("id").asText();

        MvcResult startResult = mockMvc.perform(post("/api/projects/" + projectId + "/analysis/run")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").isNotEmpty())
            .andExpect(jsonPath("$.data.projectId").value(projectId))
            .andExpect(jsonPath("$.data.jobType").value("PROJECT"))
            .andReturn();
        String jobId = objectMapper.readTree(startResult.getResponse().getContentAsString()).get("data").get("id").asText();

        JsonNode completed = awaitAnalysisJob(token, jobId);
        org.assertj.core.api.Assertions.assertThat(completed.get("status").asText()).isEqualTo("SUCCEEDED");
        org.assertj.core.api.Assertions.assertThat(completed.at("/projectResult/summary").asText()).contains("已导入");
        org.assertj.core.api.Assertions.assertThat(completed.at("/projectResult/evidence").isArray()).isTrue();
        org.assertj.core.api.Assertions.assertThat(completed.at("/projectResult/evidence").size()).isGreaterThan(0);

        mockMvc.perform(get("/api/projects/" + projectId + "/analysis-jobs")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].id").value(jobId))
            .andExpect(jsonPath("$.data[0].status").value("SUCCEEDED"));
    }

    @Test
    void indexesSourceSnippetsAndSkipsGeneratedLogs() throws Exception {
        String token = register("zip-evidence-owner", "zip-evidence-owner@example.com");
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
            .andReturn();

        JsonNode material = objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/material");
        org.assertj.core.api.Assertions.assertThat(material.get("content").asText())
            .contains("## File snippets")
            .contains("src/app/page.tsx")
            .contains("export default function Page()")
            .doesNotContain(".next-dev.err.log");
    }

    @Test
    void retriesTransientModelFailureForFileAnalysis() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        AtomicReference<String> lastRequestBody = new AtomicReference<>("");
        HttpServer modelServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        modelServer.createContext("/v1/chat/completions", exchange -> {
            int attempt = requests.incrementAndGet();
            lastRequestBody.set(new String(exchange.getRequestBody().readAllBytes()));
            byte[] body;
            if (attempt == 1) {
                exchange.sendResponseHeaders(503, -1);
                exchange.close();
                return;
            }
            body = """
                {"choices":[{"message":{"content":"{\\"path\\":\\"src/app/page.tsx\\",\\"fileType\\":\\"source\\",\\"role\\":\\"页面入口\\",\\"summary\\":\\"该文件声明 Page 组件并作为页面入口。\\",\\"importance\\":\\"critical\\",\\"riskLevel\\":\\"none\\",\\"riskNotes\\":\\"未发现明确风险证据。\\",\\"evidence\\":[\\"Page：默认导出页面组件\\"],\\"relatedFiles\\":[],\\"limitations\\":\\"仅分析已索引片段。\\",\\"confidence\\":\\"high\\"}"}}]}
                """.getBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        modelServer.start();

        try {
            String token = register("model-retry-owner", "model-retry-owner@example.com");
            mockMvc.perform(post("/api/ai-providers")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "name": "Retry test provider",
                          "baseUrl": "http://127.0.0.1:%d/v1",
                          "apiKey": "test-key",
                          "modelName": "test-model",
                          "type": "OPENAI_COMPATIBLE",
                          "temperature": 0.1,
                          "maxTokens": 200000,
                          "defaultEnabled": true,
                          "purposeTags": ["项目分析"]
                        }
                        """.formatted(modelServer.getAddress().getPort())))
                .andExpect(status().isOk());

            MockMultipartFile file = new MockMultipartFile("file", "ProjectFlow.zip", "application/zip", projectZip());
            MvcResult importResult = mockMvc.perform(multipart("/api/project-imports/zip")
                    .file(file)
                    .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
            String projectId = objectMapper.readTree(importResult.getResponse().getContentAsString()).at("/data/project/id").asText();

            MvcResult startResult = mockMvc.perform(post("/api/projects/" + projectId + "/files/analyze")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"path\":\"src/app/page.tsx\"}"))
                .andExpect(status().isOk())
                .andReturn();
            String jobId = objectMapper.readTree(startResult.getResponse().getContentAsString()).at("/data/id").asText();
            JsonNode completed = awaitAnalysisJob(token, jobId);

            org.assertj.core.api.Assertions.assertThat(completed.at("/fileResult/modelUsed").asBoolean()).isTrue();
            org.assertj.core.api.Assertions.assertThat(completed.at("/fileResult/summary").asText()).contains("Page");
            org.assertj.core.api.Assertions.assertThat(requests.get()).isEqualTo(2);
            org.assertj.core.api.Assertions.assertThat(lastRequestBody.get()).contains("\"max_tokens\":100000");
        } finally {
            modelServer.stop(0);
        }
    }

    @Test
    void storesAndDeletesProjectAnalysisRecords() throws Exception {
        String token = register("analysis-record-owner", "analysis-record-owner@example.com");
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
            .andReturn();
        String projectId = objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("project").get("id").asText();

        runProjectAnalysisAndAwait(token, projectId);

        MvcResult recordsResult = mockMvc.perform(get("/api/projects/" + projectId + "/analysis-records")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(1)))
            .andExpect(jsonPath("$.data[0].recordType").value("PROJECT"))
            .andReturn();
        String recordId = objectMapper.readTree(recordsResult.getResponse().getContentAsString()).get("data").get(0).get("id").asText();

        mockMvc.perform(delete("/api/project-analysis-records/" + recordId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/projects/" + projectId + "/analysis-records")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void rejectsAnalysisRecordDeleteForNonOwner() throws Exception {
        String ownerToken = register("analysis-delete-owner", "analysis-delete-owner@example.com");
        String otherToken = register("analysis-delete-other", "analysis-delete-other@example.com");
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "ProjectFlow.zip",
            "application/zip",
            projectZip()
        );

        MvcResult result = mockMvc.perform(multipart("/api/project-imports/zip")
                .file(file)
                .header("Authorization", "Bearer " + ownerToken))
            .andExpect(status().isOk())
            .andReturn();
        String projectId = objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("project").get("id").asText();

        runProjectAnalysisAndAwait(ownerToken, projectId);

        MvcResult recordsResult = mockMvc.perform(get("/api/projects/" + projectId + "/analysis-records")
                .header("Authorization", "Bearer " + ownerToken))
            .andExpect(status().isOk())
            .andReturn();
        String recordId = objectMapper.readTree(recordsResult.getResponse().getContentAsString()).get("data").get(0).get("id").asText();

        mockMvc.perform(delete("/api/project-analysis-records/" + recordId)
                .header("Authorization", "Bearer " + otherToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("PROJECT_NOT_FOUND"));
    }

    @Test
    void getsProjectAnalysisRecordDetailForOwnerAndRejectsNonOwner() throws Exception {
        String ownerToken = register("analysis-detail-owner", "analysis-detail-owner@example.com");
        String otherToken = register("analysis-detail-other", "analysis-detail-other@example.com");
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "ProjectFlow.zip",
            "application/zip",
            projectZip()
        );

        MvcResult result = mockMvc.perform(multipart("/api/project-imports/zip")
                .file(file)
                .header("Authorization", "Bearer " + ownerToken))
            .andExpect(status().isOk())
            .andReturn();
        String projectId = objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("project").get("id").asText();

        runProjectAnalysisAndAwait(ownerToken, projectId);

        MvcResult recordsResult = mockMvc.perform(get("/api/projects/" + projectId + "/analysis-records")
                .header("Authorization", "Bearer " + ownerToken))
            .andExpect(status().isOk())
            .andReturn();
        String recordId = objectMapper.readTree(recordsResult.getResponse().getContentAsString()).get("data").get(0).get("id").asText();

        mockMvc.perform(get("/api/project-analysis-records/" + recordId)
                .header("Authorization", "Bearer " + ownerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(recordId))
            .andExpect(jsonPath("$.data.recordType").value("PROJECT"))
            .andExpect(jsonPath("$.data.details").value(containsString("架构判断")));

        mockMvc.perform(get("/api/project-analysis-records/" + recordId)
                .header("Authorization", "Bearer " + otherToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("PROJECT_NOT_FOUND"));
    }

    @Test
    void rejectsFileAnalysisForNonOwner() throws Exception {
        String ownerToken = register("file-analysis-owner", "file-analysis-owner@example.com");
        String otherToken = register("file-analysis-other", "file-analysis-other@example.com");
        String projectId = createProject(ownerToken, "Private File Analysis");

        mockMvc.perform(post("/api/projects/" + projectId + "/files/analyze")
                .header("Authorization", "Bearer " + otherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "path": "backend/src/main/java/com/projectflow/config/WebConfig.java"
                    }
                    """))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("PROJECT_NOT_FOUND"));
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
    void savesLocalProjectPathWithoutWritingBridgeProtocol() throws Exception {
        String token = register("local-path-owner", "local-path-owner@example.com");
        String projectId = createProject(token, "Path Only Project");
        Path projectPath = createTestProjectDir("path-only-project");

        mockMvc.perform(patch("/api/projects/" + projectId + "/memory/local-path")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "localProjectPath": "%s"
                    }
                    """.formatted(jsonEscapedPath(projectPath))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.localProjectPath").value(projectPath.toAbsolutePath().normalize().toString()));

        org.assertj.core.api.Assertions.assertThat(Files.exists(projectPath.resolve(".projectflow/agent-protocol.md"))).isFalse();
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
    void scansAgentResultIntoStructuredProjectChanges() throws Exception {
        String token = register("change-scan-owner", "change-scan-owner@example.com");
        String projectId = createProject(token, "Structured Change Project");
        Path projectPath = createTestProjectDir("structured-change-project");
        Path inbox = Files.createDirectories(projectPath.resolve(".projectflow/inbox"));
        Files.writeString(inbox.resolve("20260607-0900-agent-result.md"), """
            # ProjectFlow Agent Result

            ProjectId: structured-change-project
            TaskId: PF-201
            Status: ready_for_review

            ## Summary
            Agent implemented the V3 change review data model and wired source-aware project facts.

            ## Changed Files
            - backend/src/main/java/com/projectflow/entity/ProjectChange.java
            - backend/src/main/java/com/projectflow/entity/ProjectFactSource.java

            ## Task Updates
            - PF-201: ready_for_review

            ## Decisions
            - Keep AiSuggestion compatibility while ProjectChange becomes the review source.

            ## Risks
            - Need to avoid AI overwriting user-confirmed project facts.

            ## Dev Log
            Implemented structured change review foundations.
            """);

        mockMvc.perform(post("/api/projects/" + projectId + "/agent-bridge/scan")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "projectPath": "%s"
                    }
                    """.formatted(jsonEscapedPath(projectPath))))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/projects/" + projectId + "/changes")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))))
            .andExpect(jsonPath("$.data[0].sourceType").value("AGENT_RESULT"))
            .andExpect(jsonPath("$.data[0].changeKind").value("CAPABILITY"))
            .andExpect(jsonPath("$.data[0].impactLevel").value("MAJOR"))
            .andExpect(jsonPath("$.data[0].status").value("PENDING"))
            .andExpect(jsonPath("$.data[0].affectedFiles").value(containsString("ProjectChange.java")))
            .andExpect(jsonPath("$.data[0].riskNotes").value(containsString("AI overwriting user-confirmed")));
    }

    @Test
    void acceptingStructuredProjectChangeWritesMemoryAndFactSource() throws Exception {
        String token = register("change-accept-owner", "change-accept-owner@example.com");
        String projectId = createProject(token, "Accepted Change Project");
        Path projectPath = createTestProjectDir("accepted-change-project");
        Path inbox = Files.createDirectories(projectPath.resolve(".projectflow/inbox"));
        Files.writeString(inbox.resolve("20260607-1000-agent-result.md"), """
            # ProjectFlow Agent Result

            ProjectId: accepted-change-project
            Status: ready_for_review

            ## Summary
            Agent added a durable evidence review workflow for accepted project changes.

            ## Changed Files
            - frontend/src/app/tasks/page.tsx
            - backend/src/main/java/com/projectflow/service/ProjectIntelligenceService.java

            ## Task Updates
            - PF-301: ready_for_review

            ## Decisions
            - Accepted structured changes must become reusable project memory.

            ## Risks
            - Accepted changes should not silently overwrite manual profile edits.

            ## Dev Log
            Verified accepted change propagation into memory and sources.
            """);

        mockMvc.perform(post("/api/projects/" + projectId + "/agent-bridge/scan")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "projectPath": "%s"
                    }
                    """.formatted(jsonEscapedPath(projectPath))))
            .andExpect(status().isOk());

        MvcResult changesResult = mockMvc.perform(get("/api/projects/" + projectId + "/changes")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].status").value("PENDING"))
            .andReturn();
        String changeId = objectMapper.readTree(changesResult.getResponse().getContentAsString()).at("/data/0/id").asText();

        mockMvc.perform(post("/api/project-changes/" + changeId + "/accept")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("ACCEPTED"));

        mockMvc.perform(get("/api/projects/" + projectId + "/memory")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.completedCapabilities").value(containsString("durable evidence review workflow")))
            .andExpect(jsonPath("$.data.currentRisks").value(containsString("silently overwrite manual profile edits")))
            .andExpect(jsonPath("$.data.technicalDecisions").value(containsString("reusable project memory")));

        mockMvc.perform(get("/api/projects/" + projectId + "/fact-sources")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].sourceType").value("ACCEPTED_CHANGE"));
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

    @Test
    void updatesProjectMemoryForOwnedProject() throws Exception {
        String token = register("memory-editor", "memory-editor@example.com");
        String projectId = createProject(token, "Editable Memory Project");

        mockMvc.perform(patch("/api/projects/" + projectId + "/memory")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "positioning": "Developer cockpit for agent-driven project work.",
                      "currentStage": "V3_WORKSTATION",
                      "completedCapabilities": "Navigation, workstation, confirmed facts.",
                      "inProgressCapabilities": "Change review and daily review.",
                      "currentRisks": "Local file access still depends on browser limitations.",
                      "technicalDecisions": "Keep ProjectFlow as recorder; agents change the real project.",
                      "developerLearnings": "Confirmed assets must outrank raw AI guesses.",
                      "showcaseAssets": "Weekly report, README draft, resume bullets.",
                      "nextStepSuggestions": "Implement a lightweight Tauri helper."
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.positioning").value("Developer cockpit for agent-driven project work."))
            .andExpect(jsonPath("$.data.currentStage").value("V3_WORKSTATION"))
            .andExpect(jsonPath("$.data.version").value(greaterThanOrEqualTo(2)));

        mockMvc.perform(get("/api/projects/" + projectId + "/memory")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.technicalDecisions").value("Keep ProjectFlow as recorder; agents change the real project."));

        mockMvc.perform(get("/api/projects/" + projectId + "/fact-sources")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(9))))
            .andExpect(jsonPath("$.data[0].sourceType").value("USER_MANUAL"))
            .andExpect(jsonPath("$.data[0].confirmedByUser").value(true));
    }

    @Test
    void rejectsProjectMemoryUpdateForNonOwner() throws Exception {
        String ownerToken = register("memory-private-owner", "memory-private-owner@example.com");
        String otherToken = register("memory-private-other", "memory-private-other@example.com");
        String projectId = createProject(ownerToken, "Private Memory Project");

        mockMvc.perform(patch("/api/projects/" + projectId + "/memory")
                .header("Authorization", "Bearer " + otherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "positioning": "Should not be saved.",
                      "currentStage": "BLOCKED",
                      "completedCapabilities": "",
                      "inProgressCapabilities": "",
                      "currentRisks": "",
                      "technicalDecisions": "",
                      "developerLearnings": "",
                      "showcaseAssets": "",
                      "nextStepSuggestions": ""
                    }
                    """))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("PROJECT_NOT_FOUND"));
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
            addZipEntry(zip, "projectflow-v2/frontend/.next-dev.err.log", "generated development error output");
            addZipEntry(zip, "projectflow-v2/.env", "DATABASE_PASSWORD=secret");
        }
        return outputStream.toByteArray();
    }

    private byte[] zipWithCodexRunBackupFirst() throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(outputStream)) {
            for (int index = 0; index < 260; index++) {
                addZipEntry(zip, "insightwrite-2.0/.codex-run/old-git-20260602132318/objects/aa/file-" + index, "git object " + index);
            }
            addZipEntry(zip, "insightwrite-2.0/README.md", "# insightwrite-2.0\n\n英语写作分析平台。");
            addZipEntry(zip, "insightwrite-2.0/frontend/package.json", """
                {
                  "name": "insightwrite-frontend",
                  "dependencies": {
                    "vue": "3.5.0",
                    "vite": "6.0.0"
                  }
                }
                """);
            addZipEntry(zip, "insightwrite-2.0/backend/pom.xml", """
                <project>
                  <artifactId>insightwrite-backend</artifactId>
                  <dependencies>
                    <dependency>
                      <artifactId>spring-boot-starter-web</artifactId>
                    </dependency>
                  </dependencies>
                </project>
                """);
            addZipEntry(zip, "insightwrite-2.0/docker/docker-compose.yml", "services:\n  mysql:\n    image: mysql:8\n");
            addZipEntry(zip, "insightwrite-2.0/start.bat", "cd backend && mvn spring-boot:run");
            addZipEntry(zip, "insightwrite-2.0/frontend/src/views/AnalyzeResult.vue", "<template><main>Analyze</main></template>");
            addZipEntry(zip, "insightwrite-2.0/backend/src/test/java/com/insightwrite/service/AnalyzeServiceLimitsTest.java", "class AnalyzeServiceLimitsTest {}");
        }
        return outputStream.toByteArray();
    }

    private byte[] polyglotFullStackZip() throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(outputStream)) {
            addZipEntry(zip, "polyglot-workspace/README.md", "# Polyglot Workspace\n\nReact, FastAPI and Java worker workspace.");
            addZipEntry(zip, "polyglot-workspace/apps/web/package.json", """
                {
                  "name": "web-client",
                  "scripts": {
                    "dev": "vite --host 0.0.0.0"
                  },
                  "dependencies": {
                    "@vitejs/plugin-react": "latest",
                    "react": "19.0.0",
                    "vite": "6.0.0"
                  }
                }
                """);
            addZipEntry(zip, "polyglot-workspace/apps/web/src/App.tsx", "export function App() { return <main>Web</main>; }");
            addZipEntry(zip, "polyglot-workspace/services/api/pyproject.toml", """
                [project]
                name = "api"
                dependencies = ["fastapi", "uvicorn"]
                """);
            addZipEntry(zip, "polyglot-workspace/services/api/app/main.py", "from fastapi import FastAPI\napp = FastAPI()\n");
            addZipEntry(zip, "polyglot-workspace/services/api/tests/test_main.py", "def test_api():\n    assert True\n");
            addZipEntry(zip, "polyglot-workspace/services/worker/build.gradle", """
                plugins {
                    id 'java'
                    id 'org.springframework.boot' version '3.5.0'
                }
                """);
            addZipEntry(zip, "polyglot-workspace/services/worker/src/main/java/com/example/Worker.java", "class Worker {}\n");
            addZipEntry(zip, "polyglot-workspace/docker-compose.yml", "services:\n  api:\n    build: ./services/api\n");
        }
        return outputStream.toByteArray();
    }

    private byte[] gbkChineseProjectZip() throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(outputStream, Charset.forName("GBK"))) {
            addZipEntry(zip, "综测系统/README.md", "# 综测系统\n\n成绩卡上传和 OCR 处理工具。");
            addZipEntry(zip, "综测系统/requirements.txt", "fastapi\npytest\n");
            addZipEntry(zip, "综测系统/scorecard_batch/ocr.py", "def parse_scorecard():\n    return True\n");
            addZipEntry(zip, "综测系统/tests/test_ocr.py", "def test_ocr():\n    assert True\n");
        }
        return outputStream.toByteArray();
    }

    private JsonNode awaitAnalysisJob(String token, String jobId) throws Exception {
        JsonNode job = null;
        for (int attempt = 0; attempt < 40; attempt++) {
            MvcResult result = mockMvc.perform(get("/api/analysis-jobs/" + jobId)
                    .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
            job = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
            String status = job.get("status").asText();
            if (status.equals("SUCCEEDED") || status.equals("FAILED")) {
                return job;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Analysis job did not finish: " + jobId);
    }

    private List<String> stringArray(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(item -> values.add(item.asText()));
        }
        return values;
    }

    private JsonNode runProjectAnalysisAndAwait(String token, String projectId) throws Exception {
        MvcResult startResult = mockMvc.perform(post("/api/projects/" + projectId + "/analysis/run")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").isNotEmpty())
            .andReturn();
        String jobId = objectMapper.readTree(startResult.getResponse().getContentAsString()).at("/data/id").asText();
        JsonNode completed = awaitAnalysisJob(token, jobId);
        org.assertj.core.api.Assertions.assertThat(completed.get("status").asText()).isEqualTo("SUCCEEDED");
        return completed;
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
