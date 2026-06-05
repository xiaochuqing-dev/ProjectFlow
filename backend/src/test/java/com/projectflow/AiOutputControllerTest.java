package com.projectflow;

import static org.hamcrest.Matchers.containsString;
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
class AiOutputControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void generatesAndListsAiOutputsForOwnedProject() throws Exception {
        String token = register("ai-owner", "ai-owner@example.com");
        String projectId = createProject(token, "AI Reflection Project");
        createTask(token, projectId, "Build AI output API");
        createDevLog(token, projectId, "完成 AI Mock 输出");

        MvcResult result = mockMvc.perform(post("/api/projects/" + projectId + "/ai-outputs")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "type": "WEEKLY_REPORT",
                      "fromDate": "2026-06-01",
                      "toDate": "2026-06-07"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.type").value("WEEKLY_REPORT"))
            .andExpect(jsonPath("$.data.provider").value("mock-provider"))
            .andExpect(jsonPath("$.data.content").value(containsString("完成 AI Mock 输出")))
            .andReturn();

        String outputId = result.getResponse().getContentAsString().split("\"id\":\"")[1].split("\"")[0];

        mockMvc.perform(get("/api/projects/" + projectId + "/ai-outputs")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(1)));

        mockMvc.perform(get("/api/ai-outputs/" + outputId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.title").value("AI Reflection Project 周报"));
    }

    @Test
    void rejectsAiOutputAccessForNonOwner() throws Exception {
        String ownerToken = register("ai-private-owner", "ai-private-owner@example.com");
        String otherToken = register("ai-private-other", "ai-private-other@example.com");
        String projectId = createProject(ownerToken, "Private AI Project");

        MvcResult result = mockMvc.perform(post("/api/projects/" + projectId + "/ai-outputs")
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "type": "PROJECT_SUMMARY",
                      "fromDate": null,
                      "toDate": null
                    }
                    """))
            .andExpect(status().isOk())
            .andReturn();

        String outputId = result.getResponse().getContentAsString().split("\"id\":\"")[1].split("\"")[0];

        mockMvc.perform(get("/api/ai-outputs/" + outputId)
                .header("Authorization", "Bearer " + otherToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("AI_OUTPUT_NOT_FOUND"));
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

    private void createTask(String token, String projectId, String title) throws Exception {
        mockMvc.perform(post("/api/projects/" + projectId + "/tasks")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "%s",
                      "description": "Prepare reflection outputs.",
                      "status": "DONE",
                      "priority": "HIGH",
                      "dueDate": "2026-06-10",
                      "tags": ["ai", "output"]
                    }
                    """.formatted(title)))
            .andExpect(status().isOk());
    }

    private void createDevLog(String token, String projectId, String title) throws Exception {
        mockMvc.perform(post("/api/projects/" + projectId + "/dev-logs")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "taskId": null,
                      "title": "%s",
                      "content": "完成 Mock Provider 和 Markdown 输出。",
                      "category": "FEATURE",
                      "logDate": "2026-06-05",
                      "minutesSpent": 90,
                      "blocked": false,
                      "tags": ["ai", "reflection"]
                    }
                    """.formatted(title)))
            .andExpect(status().isOk());
    }
}
