package com.projectflow;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class AuthControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void registersUserAndReturnsAccessToken() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "username": "xiaochuqing",
                      "email": "register@example.com",
                      "password": "local-password-123"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.accessToken", not(blankOrNullString())))
            .andExpect(jsonPath("$.data.user.email").value("register@example.com"))
            .andExpect(jsonPath("$.data.user.username").value("xiaochuqing"));
    }

    @Test
    void logsInExistingUserAndReturnsAccessToken() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "username": "login-user",
                      "email": "login@example.com",
                      "password": "local-password-123"
                    }
                    """))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "login@example.com",
                      "password": "local-password-123"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.accessToken", not(blankOrNullString())))
            .andExpect(jsonPath("$.data.user.email").value("login@example.com"));
    }

    @Test
    void rejectsDuplicateEmailRegistration() throws Exception {
        String request = """
            {
              "username": "duplicate",
              "email": "duplicate@example.com",
              "password": "local-password-123"
            }
            """;

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void resetsPasswordWithStartupRecoveryCode() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "username": "reset-user",
                      "email": "reset@example.com",
                      "password": "old-password-123"
                    }
                    """))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "reset@example.com",
                      "newPassword": "new-password-123",
                      "recoveryCode": "wrong-code"
                    }
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("INVALID_RECOVERY_CODE"));

        mockMvc.perform(post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "reset@example.com",
                      "newPassword": "new-password-123",
                      "recoveryCode": "test-recovery-code"
                    }
                    """))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "reset@example.com",
                      "password": "old-password-123"
                    }
                    """))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "reset@example.com",
                      "password": "new-password-123"
                    }
                    """))
            .andExpect(status().isOk());
    }

    @Test
    void limitsFrequentPasswordResetAttempts() throws Exception {
        String request = """
            {
              "email": "rate-limit@example.com",
              "newPassword": "new-password-123",
              "recoveryCode": "wrong-code"
            }
            """;

        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(post("/api/auth/reset-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
                .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.error.code").value("PASSWORD_RESET_RATE_LIMITED"));
    }

    @Test
    void returnsCurrentUserForBearerToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "username": "me-user",
                      "email": "me@example.com",
                      "password": "local-password-123"
                    }
                    """))
            .andExpect(status().isOk())
            .andReturn();

        String body = result.getResponse().getContentAsString();
        String token = body.split("\"accessToken\":\"")[1].split("\"")[0];

        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.email").value("me@example.com"))
            .andExpect(jsonPath("$.data.username").value("me-user"));
    }

    @Test
    void rejectsCurrentUserWithoutBearerToken() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }
}
