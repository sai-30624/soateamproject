package com.payvault.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Size(max = 100) String fullName,
            @NotBlank @Email @Size(max = 120) String email,
            @NotBlank @Pattern(regexp = "^[0-9]{10}$", message = "must be 10 digits") String phone,
            @NotBlank @Size(min = 6, max = 64) String password) {
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record AuthResponse(String accessToken, String refreshToken, Long userId,
                               String email, String role, long expiresInSeconds) {
    }

    // ---- payloads exchanged with other services
    public record CreateUserRequest(String fullName, String email, String phone) {
    }

    public record UserDto(Long id, String fullName, String email, String phone, String status) {
    }

    public record CreateWalletRequest(Long userId) {
    }
}
