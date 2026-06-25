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
class AiProviderControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void reusesExistingProviderWhenSameModelEndpointIsSavedAgain() throws Exception {
        String token = register("provider-dedup-owner", "provider-dedup-owner@example.com");

        String firstProviderId = createDeepSeekProvider(token, "DeepSeek", "https://api.deepseek.com", "deepseek-v4-pro", "first-key");
        String secondProviderId = createDeepSeekProvider(token, "DeepSeek", "https://api.deepseek.com/chat/completions", "deepseek-v4-pro", "second-key");

        mockMvc.perform(get("/api/ai-providers")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(1)))
            .andExpect(jsonPath("$.data[0].id").value(firstProviderId))
            .andExpect(jsonPath("$.data[0].id").value(secondProviderId))
            .andExpect(jsonPath("$.data[0].baseUrl").value("https://api.deepseek.com"))
            .andExpect(jsonPath("$.data[0].apiKeyConfigured").value(true));
    }

    @Test
    void allowsDifferentUsersToSaveTheSameProviderEndpoint() throws Exception {
        String ownerToken = register("provider-owner-a", "provider-owner-a@example.com");
        String otherToken = register("provider-owner-b", "provider-owner-b@example.com");

        createDeepSeekProvider(ownerToken, "DeepSeek", "https://api.deepseek.com", "deepseek-v4-pro", "owner-key");
        createDeepSeekProvider(otherToken, "DeepSeek", "https://api.deepseek.com", "deepseek-v4-pro", "other-key");

        mockMvc.perform(get("/api/ai-providers")
                .header("Authorization", "Bearer " + ownerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(1)));

        mockMvc.perform(get("/api/ai-providers")
                .header("Authorization", "Bearer " + otherToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(1)));
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

    private String createDeepSeekProvider(String token, String name, String baseUrl, String modelName, String apiKey) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/ai-providers")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "%s",
                      "baseUrl": "%s",
                      "apiKey": "%s",
                      "modelName": "%s",
                      "type": "DEEPSEEK",
                      "temperature": 0.2,
                      "maxTokens": 100000,
                      "defaultEnabled": true,
                      "purposeTags": ["项目分析", "材料解析", "成果生成"]
                    }
                    """.formatted(name, baseUrl, apiKey, modelName)))
            .andExpect(status().isOk())
            .andReturn();

        return result.getResponse().getContentAsString().split("\"id\":\"")[1].split("\"")[0];
    }
}
