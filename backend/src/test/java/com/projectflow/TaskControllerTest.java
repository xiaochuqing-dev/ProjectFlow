package com.projectflow;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
class TaskControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void createsAndListsTasksForOwnedProject() throws Exception {
        String token = register("task-owner", "task-owner@example.com");
        String projectId = createProject(token, "Task Project");

        createTask(token, projectId, "Design project board");

        mockMvc.perform(get("/api/projects/" + projectId + "/tasks")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(1)))
            .andExpect(jsonPath("$.data[0].title").value("Design project board"))
            .andExpect(jsonPath("$.data[0].status").value("TODO"))
            .andExpect(jsonPath("$.data[0].priority").value("HIGH"))
            .andExpect(jsonPath("$.data[0].tags[0]").value("frontend"));
    }

    @Test
    void movesTaskThroughAllowedStatusTransition() throws Exception {
        String token = register("task-move", "task-move@example.com");
        String projectId = createProject(token, "Move Project");
        String taskId = createTask(token, projectId, "Implement task state");

        mockMvc.perform(patch("/api/tasks/" + taskId + "/status")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "status": "IN_PROGRESS"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
    }

    @Test
    void rejectsInvalidStatusJump() throws Exception {
        String token = register("task-jump", "task-jump@example.com");
        String projectId = createProject(token, "Jump Project");
        String taskId = createTask(token, projectId, "Skip workflow");

        mockMvc.perform(patch("/api/tasks/" + taskId + "/status")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "status": "DONE"
                    }
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("INVALID_TASK_TRANSITION"));
    }

    @Test
    void rejectsTaskAccessForNonOwner() throws Exception {
        String ownerToken = register("task-private-owner", "task-private-owner@example.com");
        String otherToken = register("task-private-other", "task-private-other@example.com");
        String projectId = createProject(ownerToken, "Private Task Project");
        String taskId = createTask(ownerToken, projectId, "Private task");

        mockMvc.perform(get("/api/tasks/" + taskId)
                .header("Authorization", "Bearer " + otherToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("TASK_NOT_FOUND"));
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
}
