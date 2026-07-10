package com.projectflow;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.projectflow.entity.AiProvider;
import com.projectflow.repository.AiProviderRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiProviderControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AiProviderRepository providerRepository;

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

    @Test
    void editingWithoutANewKeyPreservesExistingKey() throws Exception {
        String token = register("provider-edit-owner", "provider-edit-owner@example.com");
        String providerId = createDeepSeekProvider(token, "DeepSeek", "https://api.deepseek.com", "deepseek-chat", "saved-key");

        mockMvc.perform(patch("/api/ai-providers/" + providerId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"DeepSeek Updated","baseUrl":"https://api.deepseek.com","apiKey":"","modelName":"deepseek-chat",
                     "type":"DEEPSEEK","temperature":0.2,"maxTokens":8192,"defaultEnabled":true,
                     "purposeTags":["项目分析"],"clearApiKey":false}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.name").value("DeepSeek Updated"))
            .andExpect(jsonPath("$.data.apiKeyConfigured").value(true));
    }

    @Test
    void settingDefaultMakesOtherProvidersNonDefaultAndProtectsDefaultDeletion() throws Exception {
        String token = register("provider-default-owner", "provider-default-owner@example.com");
        String firstId = createDeepSeekProvider(token, "First", "https://api.deepseek.com", "first-model", "first-key");
        String secondId = createDeepSeekProvider(token, "Second", "https://api.deepseek.com", "second-model", "second-key");

        mockMvc.perform(get("/api/ai-providers").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].id").value(secondId))
            .andExpect(jsonPath("$.data[0].defaultEnabled").value(true))
            .andExpect(jsonPath("$.data[1].id").value(firstId))
            .andExpect(jsonPath("$.data[1].defaultEnabled").value(false));

        mockMvc.perform(delete("/api/ai-providers/" + secondId).header("Authorization", "Bearer " + token))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("DEFAULT_PROVIDER_REPLACEMENT_REQUIRED"));
    }

    @Test
    void rejectsUnrealisticMaxTokens() throws Exception {
        String token = register("provider-range-owner", "provider-range-owner@example.com");
        mockMvc.perform(post("/api/ai-providers")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Too Large","baseUrl":"https://api.deepseek.com","apiKey":"key","modelName":"deepseek-chat",
                     "type":"DEEPSEEK","temperature":0.2,"maxTokens":1000000000,"defaultEnabled":true,"purposeTags":[]}
                    """))
            .andExpect(status().isBadRequest());
    }

    @Test
    void historicalDuplicateProvidersRequireExplicitCleanupAndKeepHistoryIndependent() throws Exception {
        String token = register("provider-cleanup-owner", "provider-cleanup-owner@example.com");
        String keeperId = createDeepSeekProvider(token, "Keeper", "https://api.deepseek.com", "deepseek-chat", "keeper-key");
        AiProvider keeper = providerRepository.findById(UUID.fromString(keeperId)).orElseThrow();
        AiProvider duplicate = new AiProvider(keeper.getUserId());
        duplicate.update(
            "Historical duplicate", keeper.getBaseUrl(), "", keeper.getModelName(), keeper.getType(),
            keeper.getTemperature(), keeper.getMaxTokens(), false, List.of("项目分析")
        );
        duplicate = providerRepository.save(duplicate);

        mockMvc.perform(get("/api/ai-providers/duplicates").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(1)))
            .andExpect(jsonPath("$.data[0].recommendedKeeper.id").value(keeperId))
            .andExpect(jsonPath("$.data[0].duplicates[0].id").value(duplicate.getId().toString()));

        mockMvc.perform(post("/api/ai-providers/duplicates/cleanup")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"providerIds\":[\"" + duplicate.getId() + "\"]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.deletedCount").value(1));
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
