package com.MBQ.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.MBQ.model.BloodGroup;
import com.MBQ.model.BloodRequestStatus;
import com.MBQ.model.PriorityLevel;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class BloodRequestDtos {
    private BloodRequestDtos() {}

    public record CreateBloodRequest(
            @NotNull Long patientId,
            @Min(1) @Max(10) int units,
            PriorityLevel priorityLevel,
            @jakarta.validation.constraints.NotBlank @Size(max = 500) String reason) {}

    public record ReviewBloodRequest(
            @NotNull BloodRequestStatus status,
            @Size(max = 500) String notes) {}

    public record FulfilBloodRequest(@NotNull Long stockId) {}

    public record BloodRequestResponse(
            Long id, Long patientId, String patientName, BloodGroup bloodGroup,
            int units, PriorityLevel priorityLevel, String reason, BloodRequestStatus status,
            String requestedBy, OffsetDateTime requestedAt, String reviewedBy,
            OffsetDateTime reviewedAt, String reviewNotes, Long stockId,
            UUID inventoryOperationId, OffsetDateTime fulfilledAt) {}
}
