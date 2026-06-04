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
class DevLogControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void createsAndListsDevLogsForOwnedProject() throws Exception {
        String token = register("log-owner", "log-owner@example.com");
        String projectId = createProject(token, "Log Project");
        String taskId = createTask(token, projectId, "Build log module");

        createDevLog(token, projectId, taskId, "完成开发日志基础 API");

        mockMvc.perform(get("/api/projects/" + projectId + "/dev-logs")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(1)))
            .andExpect(jsonPath("$.data[0].title").value("完成开发日志基础 API"))
            .andExpect(jsonPath("$.data[0].taskId").value(taskId))
            .andExpect(jsonPath("$.data[0].category").value("FEATURE"))
            .andExpect(jsonPath("$.data[0].minutesSpent").value(90))
            .andExpect(jsonPath("$.data[0].blocked").value(false))
            .andExpect(jsonPath("$.data[0].tags[0]").value("backend"));
    }

    @Test
    void rejectsDevLogTaskOutsideProject() throws Exception {
        String token = register("log-task-owner", "log-task-owner@example.com");
        String projectId = createProject(token, "Main Project");
        String otherProjectId = createProject(token, "Other Project");
        String otherTaskId = createTask(token, otherProjectId, "Task outside project");

        mockMvc.perform(post("/api/projects/" + projectId + "/dev-logs")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(devLogJson(otherTaskId, "错误关联其他项目任务")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("INVALID_DEV_LOG_TASK"));
    }

    @Test
    void rejectsDevLogAccessForNonOwner() throws Exception {
        String ownerToken = register("log-private-owner", "log-private-owner@example.com");
        String otherToken = register("log-private-other", "log-private-other@example.com");
        String projectId = createProject(ownerToken, "Private Log Project");
        String logId = createDevLog(ownerToken, projectId, null, "私有开发日志");

        mockMvc.perform(get("/api/dev-logs/" + logId)
                .header("Authorization", "Bearer " + otherToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("DEV_LOG_NOT_FOUND"));
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

    private String createTask(String token, String projectId, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects/" + projectId + "/tasks")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "%s",
                      "description": "Prepare the task board workflow.",
                      "status": "TODO",
                      "priority": "HIGH",
                      "dueDate": "2026-06-10",
                      "tags": ["frontend", "workflow"]
                    }
                    """.formatted(title)))
            .andExpect(status().isOk())
            .andReturn();

        return result.getResponse().getContentAsString().split("\"id\":\"")[1].split("\"")[0];
    }

    private String createDevLog(String token, String projectId, String taskId, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects/" + projectId + "/dev-logs")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(devLogJson(taskId, title)))
            .andExpect(status().isOk())
            .andReturn();

        return result.getResponse().getContentAsString().split("\"id\":\"")[1].split("\"")[0];
    }

    private String devLogJson(String taskId, String title) {
        String taskValue = taskId == null ? "null" : "\"" + taskId + "\"";
        return """
            {
              "taskId": %s,
              "title": "%s",
              "content": "完成接口、服务层和测试闭环，记录关键取舍。",
              "category": "FEATURE",
              "logDate": "2026-06-05",
              "minutesSpent": 90,
              "blocked": false,
              "tags": ["backend", "workflow"]
            }
            """.formatted(taskValue, title);
    }
}
