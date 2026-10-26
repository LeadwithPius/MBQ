package com.MBQ.controller;

import com.MBQ.dto.QueueDtos.CreateTicketRequest;
import com.MBQ.dto.QueueDtos.TicketHistoryResponse;
import com.MBQ.dto.QueueDtos.TicketResponse;
import com.MBQ.dto.QueueDtos.UpdateTicketStatusRequest;
import com.MBQ.service.QueueService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/queue")
public class QueueController {

    private final QueueService queueService;

    public QueueController(QueueService queueService) {
        this.queueService = queueService;
    }

    // STEP 2: Create a ticket.

    @PostMapping("/tickets")
    @ResponseStatus(HttpStatus.CREATED)
    public TicketResponse createTicket(
            @Valid @RequestBody CreateTicketRequest request
    ) {
        return queueService.createTicket(request);
    }

    // STEP 3: Retrieve the waiting queue for a department.

    @GetMapping("/departments/{departmentId}/waiting")
    public List<TicketResponse> getWaitingQueue(
            @PathVariable Long departmentId
    ) {
        return queueService.getWaitingQueue(departmentId);
    }

    // STEP 4: Call the next waiting patient.

    @PostMapping("/departments/{departmentId}/call-next")
    public TicketResponse callNextPatient(
            @PathVariable Long departmentId
    ) {
        return queueService.callNextPatient(departmentId);
    }

    // STEP 5: Update a ticket's status.

    @PatchMapping("/tickets/{ticketId}/status")
    public TicketResponse updateTicketStatus(
            @PathVariable Long ticketId,
            @Valid @RequestBody UpdateTicketStatusRequest request
    ) {
        return queueService.updateTicketStatus(ticketId, request);
    }

    // STEP 6: Retrieve a ticket's status history.

    @GetMapping("/tickets/{ticketId}/history")
    public List<TicketHistoryResponse> getTicketHistory(
            @PathVariable Long ticketId
    ) {
        return queueService.getTicketHistory(ticketId);
    }
}