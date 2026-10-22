package com.MBQ.dto;

import com.MBQ.model.PriorityLevel;
import com.MBQ.model.QueueTicket;
import com.MBQ.model.TicketStatus;

import java.time.LocalDateTime;

/**
 * JSON returned for a queue ticket.
 *
 * @param position place in the waiting line (1 = next to be called);
 *                 null when the ticket is not WAITING
 */
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
        String triageNotes,
        String outcome,
        boolean requiresBlood,
        Integer position) {

    public static TicketResponse from(QueueTicket t, Integer position) {
        return new TicketResponse(
                t.getId(),
                t.getTicketNumber(),
                t.getPatient().getId(),
                t.getPatient().getName(),
                t.getDepartment().getId(),
                t.getDepartment().getName(),
                t.getPriorityLevel(),
                t.getStatus(),
                t.getTimeIssued(),
                t.getTriageNotes(),
                t.getOutcome(),
                t.isRequiresBlood(),
                position);
    }
}
