package com.MBQ.dto;

import com.MBQ.model.BloodGroup;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * JSON body for POST /api/patients (FR1: name, age, sex, contact, blood group).
 *
 * @param name       full name, required
 * @param age        age in years, 0-130, required
 * @param sex        "MALE" or "FEMALE" (any letter case), required
 * @param contact    optional phone number, digits only with an optional leading +, e.g. 0971234567
 * @param bloodGroup optional, e.g. "O_POS"; may be unknown at registration
 * @param facilityId optional facility the patient is registered at
 */
public record RegisterPatientRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name must be at most 100 characters")
        String name,

        @NotNull(message = "Age is required")
        @Min(value = 0, message = "Age cannot be negative")
        @Max(value = 130, message = "Age must be 130 or less")
        Integer age,

        @NotBlank(message = "Sex is required")
        @Pattern(regexp = "(?i)MALE|FEMALE", message = "Sex must be MALE or FEMALE")
        String sex,

        @Pattern(regexp = "^\\+?[0-9]{9,15}$",
                message = "Contact must be 9-15 digits, optionally starting with +")
        String contact,

        BloodGroup bloodGroup,

        @Positive(message = "facilityId must be a positive number")
        Long facilityId) {
}
