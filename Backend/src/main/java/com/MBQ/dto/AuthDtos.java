package com.MBQ.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password) {
    }

    public record LoginResponse(
            String accessToken,
            String tokenType,
            long expiresInMs,
            StaffProfile staff) {
    }

    public record StaffProfile(
            Long id,
            String name,
            String username,
            String role,
            Long departmentId,
            String departmentName,
            Long facilityId) {
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8, max = 100) String newPassword) {
    }
}
