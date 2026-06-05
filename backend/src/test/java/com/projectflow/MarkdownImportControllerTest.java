package com.projectflow;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MarkdownImportControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void previewsMarkdownIntoStructuredDevLog() throws Exception {
        String token = register("import-preview", "import-preview@example.com");
        String projectId = createProject(token, "Import Project");

        mockMvc.perform(post("/api/imports/preview")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(previewJson(projectId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.title").value("导入日志测试"))
            .andExpect(jsonPath("$.data.category").value("FEATURE"))
            .andExpect(jsonPath("$.data.logDate").value("2026-06-05"))
            .andExpect(jsonPath("$.data.tags[0]").value("codex"))
            .andExpect(jsonPath("$.data.blocked").value(true))
            .andExpect(jsonPath("$.data.content").value(org.hamcrest.Matchers.containsString("## 已完成")));
    }

    @Test
    void confirmsMarkdownAsDevLogAndImportRecord() throws Exception {
        String token = register("import-confirm", "import-confirm@example.com");
        String projectId = createProject(token, "Confirm Project");

        mockMvc.perform(post("/api/imports/confirm")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(confirmJson(projectId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.title").value("导入日志测试"))
            .andExpect(jsonPath("$.data.tags[0]").value("codex"));

        mockMvc.perform(get("/api/projects/" + projectId + "/imports")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(1)))
            .andExpect(jsonPath("$.data[0].source").value("codex"));

        mockMvc.perform(get("/api/projects/" + projectId + "/dev-logs")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(1)))
            .andExpect(jsonPath("$.data[0].title").value("导入日志测试"));
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
                      "description": "A portfolio project.",
                      "status": "BUILDING",
                      "techStack": ["Spring Boot", "Next.js"],
                      "repoUrl": "https://github.com/xiaochuqing-dev/ProjectFlow",
                      "startDate": "2026-06-04",
                      "endDate": null
                    }
                    """.formatted(name)))
            .andExpect(status().isOk())
            .andReturn();

        return result.getResponse().getContentAsString().split("\"id\":\"")[1].split("\"")[0];
    }

    private String previewJson(String projectId) {
        return """
            {
              "projectId": "%s",
              "markdown": "%s"
            }
            """.formatted(projectId, escapedMarkdown());
    }

    private String confirmJson(String projectId) {
        return """
            {
              "projectId": "%s",
              "taskId": null,
              "markdown": "%s"
            }
            """.formatted(projectId, escapedMarkdown());
    }

    private String escapedMarkdown() {
        return """
            ---
            title: 导入日志测试
            date: 2026-06-05
            category: feature
            source: codex
            tags: codex,import
            minutes: 45
            ---
            # 导入日志测试
            ## 完成
            - 完成 Markdown 解析预览。
            ## 阻塞
            - 需要确认字段映射。
            ## 下一步
            - 保存为结构化日志。
            """
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n");
    }
}
