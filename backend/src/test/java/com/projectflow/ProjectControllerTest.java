package com.projectflow;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class ProjectControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void createsAndListsOnlyCurrentUsersProjects() throws Exception {
        String ownerToken = register("owner", "owner@example.com");
        String otherToken = register("other", "other@example.com");

        createProject(ownerToken, "InsightWrite 2.0");
        createProject(otherToken, "Other Project");

        mockMvc.perform(get("/api/projects")
                .header("Authorization", "Bearer " + ownerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(1)))
            .andExpect(jsonPath("$.data[0].name").value("InsightWrite 2.0"))
            .andExpect(jsonPath("$.data[0].techStack[0]").value("Spring Boot"));
    }

    @Test
    void returnsProjectDetailForOwner() throws Exception {
        String token = register("detail", "detail@example.com");
        String projectId = createProject(token, "ProjectFlow");

        mockMvc.perform(get("/api/projects/" + projectId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(projectId))
            .andExpect(jsonPath("$.data.name").value("ProjectFlow"))
            .andExpect(jsonPath("$.data.status").value("BUILDING"));
    }

    @Test
    void rejectsProjectDetailForNonOwner() throws Exception {
        String ownerToken = register("private-owner", "private-owner@example.com");
        String otherToken = register("private-other", "private-other@example.com");
        String projectId = createProject(ownerToken, "Private Project");

        mockMvc.perform(get("/api/projects/" + projectId)
                .header("Authorization", "Bearer " + otherToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("PROJECT_NOT_FOUND"));
    }

    @Test
    void updatesOwnedProject() throws Exception {
        String token = register("update", "update@example.com");
        String projectId = createProject(token, "Draft Project");

        mockMvc.perform(put("/api/projects/" + projectId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Updated Project",
                      "description": "Updated description",
                      "status": "PAUSED",
                      "techStack": ["Next.js", "PostgreSQL"],
                      "repoUrl": "https://github.com/xiaochuqing-dev/ProjectFlow",
                      "startDate": "2026-06-04",
                      "endDate": null
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.name").value("Updated Project"))
            .andExpect(jsonPath("$.data.status").value("PAUSED"))
            .andExpect(jsonPath("$.data.techStack[1]").value("PostgreSQL"));
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
}
