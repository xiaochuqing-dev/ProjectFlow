package com.projectflow.dto;

import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record RegisterRequest(
        @NotBlank @Size(max = 80) String username,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 120) String password
    ) {
    }

    public record LoginRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 120) String password
    ) {
    }

    public record ResetPasswordRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 120) String newPassword,
        @NotBlank @Size(min = 6, max = 128) String recoveryCode
    ) {
    }

    public record AuthUser(UUID id, String username, String email) {
    }

    public record AuthResponse(String accessToken, AuthUser user) {
    }
}
