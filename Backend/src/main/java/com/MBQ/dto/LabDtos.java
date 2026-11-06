package com.MBQ.dto;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.MBQ.model.LabPriority;
import com.MBQ.model.LabRequestStatus;

public final class LabDtos {

    private LabDtos() {}

    public record CreateLabRequest(
            @NotNull Long consultationId,
            @NotBlank @Size(max = 120) String testName,
            @Size(max = 1000) String clinicalNotes,
            LabPriority priority) {}

    public record CompleteLabRequest(
            @NotBlank @Size(max = 4000) String resultText,
            @Size(max = 2000) String resultNotes,
            Boolean abnormal) {}

    public record CancelLabRequest(
            @NotBlank @Size(max = 500) String reason) {}

    public record LabRequestResponse(
            Long id,
            Long consultationId,
            Long patientId,
            String patientName,
            String ticketNumber,
            String departmentName,
            String testName,
            String clinicalNotes,
            LabPriority priority,
            LabRequestStatus status,
            String requestedByName,
            OffsetDateTime requestedAt,
            String startedByName,
            OffsetDateTime startedAt,
            String resultText,
            String resultNotes,
            Boolean abnormal,
            String completedByName,
            OffsetDateTime completedAt,
            String cancelledReason) {}
}
