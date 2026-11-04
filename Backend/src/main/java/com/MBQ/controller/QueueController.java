package com.MBQ.controller;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import com.MBQ.dto.QueueDtos.CreateTicketRequest;
import com.MBQ.dto.QueueDtos.TicketHistoryResponse;
import com.MBQ.dto.QueueDtos.TicketResponse;
import com.MBQ.dto.QueueDtos.UpdatePriorityRequest;
import com.MBQ.dto.QueueDtos.UpdateTicketStatusRequest;
import com.MBQ.service.QueueService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/queue")
public class QueueController {

    private final QueueService queueService;

    public QueueController(QueueService queueService) {
        this.queueService = queueService;
    }

    @PostMapping("/tickets")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST', 'NURSE')")
    public TicketResponse createTicket(@Valid @RequestBody CreateTicketRequest request) {
        return queueService.createTicket(request);
    }

    // The proposal (slide 9) has the triage nurse calling the next patient too.
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'NURSE')")
    @PostMapping("/departments/{departmentId}/call-next")
    public TicketResponse callNextPatient(@PathVariable Long departmentId) {
        return queueService.callNextPatient(departmentId);
    }

    @GetMapping("/departments/{departmentId}/waiting")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'NURSE', 'RECEPTIONIST')")
    public List<TicketResponse> getWaitingQueue(@PathVariable Long departmentId) {
        return queueService.getWaitingQueue(departmentId);
    }

    @GetMapping("/departments/{departmentId}/in-progress")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'NURSE', 'RECEPTIONIST')")
    public List<TicketResponse> getInProgressQueue(@PathVariable Long departmentId) {
        return queueService.getInProgressQueue(departmentId);
    }

    @PatchMapping("/tickets/{ticketId}/priority")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'NURSE')")
    public TicketResponse updateTicketPriority(
            @PathVariable Long ticketId,
            @Valid @RequestBody UpdatePriorityRequest request) {
        return queueService.updateTicketPriority(ticketId, request);
    }

    @PatchMapping("/tickets/{ticketId}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR')")
    public TicketResponse updateTicketStatus(
            @PathVariable Long ticketId,
            @Valid @RequestBody UpdateTicketStatusRequest request) {
        return queueService.updateTicketStatus(ticketId, request);
    }

    @GetMapping("/tickets/{ticketId}/history")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'NURSE')")
    public List<TicketHistoryResponse> getTicketHistory(@PathVariable Long ticketId) {
        return queueService.getTicketHistory(ticketId);
    }
}
