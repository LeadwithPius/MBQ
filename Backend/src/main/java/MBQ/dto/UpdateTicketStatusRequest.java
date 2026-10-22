package com.MBQ.dto;

import com.MBQ.model.TicketStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * JSON body for PATCH /api/queue/tickets/{id}/status.
 *
 * @param status      the new status, required
 * @param outcome     optional consultation outcome (doctor), max 255 characters
 * @param triageNotes optional triage notes (nurse), max 255 characters
 */
public record UpdateTicketStatusRequest(
        @NotNull(message = "status is required")
        TicketStatus status,

        @Size(max = 255, message = "outcome must be at most 255 characters")
        String outcome,

        @Size(max = 255, message = "triageNotes must be at most 255 characters")
        String triageNotes) {
}
