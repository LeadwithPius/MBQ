package com.MBQ.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.MBQ.model.Role;

/** Admin staff account management. */
public final class StaffDtos {

    private StaffDtos() {}

    public record CreateStaffRequest(
            @NotBlank @Size(max = 255) String name,
            @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{3,50}",
                    message = "Username must be 3-50 letters, numbers, dots, dashes or underscores")
            String username,
            @NotNull Role role,
            Long departmentId,
            @NotBlank @Size(min = 8, max = 100) String temporaryPassword) {}

    public record SetActiveRequest(@NotNull Boolean active) {}

    public record ResetPasswordRequest(@NotBlank @Size(min = 8, max = 100) String newPassword) {}

    public record StaffResponse(
            Long id,
            String name,
            String username,
            Role role,
            Long departmentId,
            String departmentName,
            boolean active) {}
}
