package com.MBQ.dto;

import com.MBQ.model.PriorityLevel;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * JSON body for PATCH /api/queue/tickets/{id}/priority (FR4: triage flags priority/emergency).
 *
 * @param priorityLevel the new priority, required
 * @param triageNotes   optional reason / assessment, max 255 characters
 * @param requiresBlood optional; set true to flag the emergency blood branch
 */
public record UpdatePriorityRequest(
        @NotNull(message = "priorityLevel is required")
        PriorityLevel priorityLevel,

        @Size(max = 255, message = "triageNotes must be at most 255 characters")
        String triageNotes,

        Boolean requiresBlood) {
}
