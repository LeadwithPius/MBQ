package com.MBQ.dto;

import jakarta.validation.constraints.NotBlank;

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
            Long facilityId) {
    }
}