package com.MBQ.dto;

import com.MBQ.model.PriorityLevel;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * JSON body for POST /api/queue/tickets (FR2).
 *
 * @param patientId     an already-registered patient, required
 * @param departmentId  the department whose queue the patient joins, required
 * @param priorityLevel optional, defaults to NORMAL
 * @param requiresBlood optional, defaults to false
 */
public record CreateTicketRequest(
        @NotNull(message = "patientId is required")
        @Positive(message = "patientId must be a positive number")
        Long patientId,

        @NotNull(message = "departmentId is required")
        @Positive(message = "departmentId must be a positive number")
        Long departmentId,

        PriorityLevel priorityLevel,

        Boolean requiresBlood) {
}
