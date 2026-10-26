package com.MBQ.dto;

import com.MBQ.model.BloodGroup;
import com.MBQ.model.PriorityLevel;
import com.MBQ.model.TicketStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class QueueDtos {

    private QueueDtos() {
    }

    public record PatientRegistrationRequest(
            @NotBlank
            @Size(max = 255)
            String name,

            @Min(0)
            @Max(130)
            Integer age,

            @Size(max = 255)
            String sex,

            @Size(max = 255)
            String contact,

            BloodGroup bloodGroup,

            @NotNull
            Long facilityId
    ) {
    }

    public record PatientResponse(
            Long id,
            String name,
            Integer age,
            String sex,
            String contact,
            BloodGroup bloodGroup,
            Long facilityId
    ) {
    }

    public record CreateTicketRequest(
            @NotNull
            Long patientId,

            @NotNull
            Long departmentId,

            PriorityLevel priorityLevel,

            @Size(max = 255)
            String triageNotes,

            boolean requiresBlood
    ) {
    }

    public record UpdateTicketStatusRequest(
            @NotNull
            TicketStatus status,

            @Size(max = 255)
            String notes,

            @Size(max = 255)
            String outcome
    ) {
    }

    public record TicketResponse(
            Long id,
            String ticketNumber,
            Long patientId,
            String patientName,
            Long departmentId,
            String departmentName,
            PriorityLevel priorityLevel,
            TicketStatus status,
            LocalDateTime timeIssued,
            boolean requiresBlood,
            String triageNotes,
            String outcome
    ) {
    }

    public record TicketHistoryResponse(
            Long id,
            Long ticketId,
            TicketStatus previousStatus,
            TicketStatus newStatus,
            Long changedByStaffId,
            LocalDateTime changedAt,
            String notes
    ) {
    }
}